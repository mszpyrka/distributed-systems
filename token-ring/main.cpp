#include <cstdio>
#include <cstdlib>
#include <iostream>

#include <cstring>
#include <queue>
#include <set>
#include <mutex>
#include <condition_variable>
#include <thread>

#include <unistd.h>

#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>

#include "chat_protocol.h"


bool operator==(const struct sockaddr_in &a, const struct sockaddr_in &b) {
    return ((a.sin_port == b.sin_port) &&
        (a.sin_addr.s_addr == b.sin_addr.s_addr) &&
        (a.sin_family == b.sin_family));
}


// debugging parameters
bool logging = true;



// initial setup parameters
char* username;
sockaddr_in self_address;
char transport_protocol;



// stores the data that is to be forwarded
char forward_buffer[MAX_MSG_SIZE];
int forward_data_size;



// next client pointer
sockaddr_in neighbour_address;
bool connection_established;
std::mutex mt_neighbour_address;

void set_neighbour_address(const struct sockaddr_in &address) {
    std::lock_guard<std::mutex> lock(mt_neighbour_address);
    neighbour_address = address;
}

struct sockaddr_in get_neighbour_address() {
    std::lock_guard<std::mutex> lock(mt_neighbour_address);
    return neighbour_address;
}



// text messages queue
std::queue<struct data_message> message_queue;
std::mutex mt_message_queue;

void parse_data_message(const char* raw) {

}



// connection requests queue
std::set<std::pair<in_port_t, in_addr_t> > pending_requests;
std::mutex mt_pending_requests;

bool add_connection_request(const struct sockaddr_in &address) {
    std::lock_guard<std::mutex> lock(mt_pending_requests);
    pending_requests.insert(std::make_pair(address.sin_port, address.sin_addr.s_addr));
}

bool remove_connection_request(const struct sockaddr_in &address) {
    std::lock_guard<std::mutex> lock(mt_pending_requests);
    pending_requests.erase(std::make_pair(address.sin_port, address.sin_addr.s_addr));
}



// token parameters
bool token_is_present;
bool token_is_free;
// std::mutex mt_token;


// ==========================================================================================
// Thread methods implementation
// ==========================================================================================

void token_process_thread(Transmission* ts) {
    sleep(1);
    std::cout << "pending requests: " << pending_requests.size() << std::endl;
    for (auto it : pending_requests) {
        std::cout << it.first << ' ' << it.second << std::endl;
    }
}

void receive_thread(Transmission* ts) {
    char buffer[MAX_MSG_SIZE];
    struct sockaddr_in sender_address;

    while (true) {
        int msg_size = ts->receive_bytes(buffer, MAX_MSG_SIZE, &sender_address);
        char type = buffer[0];
        char token = buffer[1];

        std::cout << "received message with type " << (int) type << ", token: " << (int) token << std::endl; 

        if (token == 1) {
            // in case the process still has expired request from the sender
            // (if the sender gave the process the token, then it means that the sender
            // is already connected to the ring)
            remove_connection_request(sender_address);
        }

        if (type == MSG_DATA) {
            struct data_message msg;
            deserialize_data_msg(buffer, msg_size, &msg);

            token_is_present = true;
            token_is_free = (bool) msg.token_is_free;

            // if the message is addressed to this process
            if (strcmp(&msg.buffer[msg.receiver_index], username) == 0) {
                std::cout << "message from " << &msg.buffer[msg.sender_index] << ": "
                    << &msg.buffer[msg.data_index] << std::endl;

                // frees the token since the data has beed successfully delivered
                token_is_free = true; 
            }
            
            // if the message was sent by this process
            else if (strcmp(&msg.buffer[msg.sender_index], username) == 0) {
                std::cout << "message to " << &msg.buffer[msg.receiver_index] << ": \""
                    << &msg.buffer[msg.data_index] << "\" was not delivered" <<  std::endl;

                // frees the token since the receiver was not found in the network
                token_is_free = true; 
            }

            // in any other case the process needs to simply forward the message
            else {
                memcpy(forward_buffer, &msg.buffer, msg_size);
                forward_data_size = msg_size;
            }
        }

        else if (type == MSG_CONREQ || type == MSG_CONFWD) {
            struct connection_message msg;
            deserialize_connection_msg(buffer, &msg);

            if (msg.with_token) {
                token_is_present = true;

                // when the process receives connection message with the token and either
                // it is not connected to any client or its neighbour's address is the same
                // as neighbour's address from the message, then the process must set
                // it's neighbour to be the original process that created the connection message
                // (in any case, the token may be freed)
                if ((connection_established && (msg.neighbour_address == get_neighbour_address())) ||
                        (!connection_established)) {

                    set_neighbour_address(msg.client_address);
                    token_is_free = true;
                }

                // in any other case, the message needs to be forwarded so it reaches the root
                // of the network or the client preceeding the original message creator
                else {
                    msg.type = MSG_CONFWD;
                    forward_data_size = serialize_connection_msg(&msg, forward_buffer);
                }
            }

            // if the message doesn't contain the token, then it can only be connection request
            // to this process that needs to be queued
            else {
                add_connection_request(sender_address);
            }
        }

        // after the message is processed, if the token was received,
        // the process needs to run the token processing thread
        if (token_is_present) {
            std::thread th(&token_process_thread, ts);
            th.detach();
        }
    }
}

int main(int argc, char const *argv[]) {
    
    if (argc < 7) {
        std::cout << "usage: ./main login self_ip self_port next_ip next_port ( tcp | udp ) [token]" << std::endl;
        exit(0);
    }

    // initial parameters setup
    const char* self_ip = argv[2];
    int self_port = atoi(argv[3]);
    set_address(self_ip, self_port, &self_address);

    const char* next_ip = argv[4];
    int next_port = atoi(argv[5]);

    transport_protocol = (strcmp(argv[6], "tcp") == 0) ? TRANSPORT_TCP : TRANSPORT_UDP;
    Transmission ts(self_ip, self_port, transport_protocol);

    token_is_present = (argc > 7) ? true : false;

    if(next_port > 0) {

        struct sockaddr_in addr;
        set_address(next_ip, next_port, &addr);
        set_neighbour_address(addr);

        struct connection_message msg;
        msg.type = MSG_CONREQ;

        msg.with_token = token_is_present;
        token_is_present = 0;

        msg.client_address = self_address;
        msg.neighbour_address = get_neighbour_address();

        char buffer[MAX_MSG_SIZE];
        int size = serialize_connection_msg(&msg, buffer);

        ts.send_bytes(buffer, size, &neighbour_address);
        connection_established = true;
    }

    else {
        connection_established = false;
    }
    
    
    std::thread receiver(&receive_thread, &ts);

    receiver.join();

    sleep(10);
}
