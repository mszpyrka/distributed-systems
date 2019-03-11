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
const char* username;
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

char log_message[128];



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

/** 
 * Removes and stores one request from pending_requests set inside given buffer.
 * If the set is empty, returns -1.
 */
int get_pending_request(struct sockaddr_in* request) {
    std::lock_guard<std::mutex> lock(mt_pending_requests);
    auto request_info = pending_requests.begin();

    if (request_info == pending_requests.end())
        return -1;

    pending_requests.erase(pending_requests.begin());
    request->sin_family = AF_INET;
    request->sin_port = request_info->first;
    request->sin_addr.s_addr = request_info->second;
    return 0;
}



// token parameters
bool token_is_present;
bool token_is_free;



// ==========================================================================================
// Thread methods implementation
// ==========================================================================================

void token_process_thread(Transmission* ts) {
    sprintf(log_message, "%s received token", username);
    ts->log(log_message, strlen(log_message));
    usleep(TOKEN_SLEEP_TIME);

    if (token_is_free) {

        struct sockaddr_in request;
        int res = get_pending_request(&request);

        // if there is any pending request, the process creates proper connection message
        // and stores it inside forward_buffer
        if (res == 0) {
            struct connection_message msg;
            msg.client_address = request;
            msg.neighbour_address = self_address;
            msg.type = MSG_CONFWD;
            msg.with_token = 1;

            forward_data_size = serialize_connection_msg(&msg, forward_buffer);
        }

        // if no request is pending a data message with a token is created and optionally
        // filled with a queued message
        else {
            // todo: proper structure initialization

            forward_buffer[0] = MSG_DATA;
            forward_buffer[1] = 1;
            forward_data_size = 2;
        }
    }

    struct sockaddr_in dest = get_neighbour_address();
    ts->send_bytes(forward_buffer, forward_data_size, &dest);
    token_is_present = false;
}

void receive_thread(Transmission* ts) {
    char buffer[MAX_MSG_SIZE];
    struct sockaddr_in sender_address;

    while (true) {
        int msg_size = ts->receive_bytes(buffer, MAX_MSG_SIZE, &sender_address);
        char type = buffer[0];
        char token = buffer[1];

        std::cout << "received message with type " << (int) type << ", token: " << (int) token << std::endl; 
        std::cout << "from " << sender_address.sin_addr.s_addr << " " << ntohs(sender_address.sin_port) << std::endl;

        if (token == 1) {
            // in case the process still has expired request from the sender
            // (if the sender gave the process the token, then it means that the sender
            // is already connected to the ring)
            
        }

        if (type == MSG_DATA) {
            struct data_message msg;
            deserialize_data_msg(buffer, msg_size, &msg);

            token_is_present = true;
            token_is_free = (msg.token_is_free == 1) ? true : false;

            if (!token_is_free) {

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
        }

        else if (type == MSG_CONREQ || type == MSG_CONFWD) {
            struct connection_message msg;
            deserialize_connection_msg(buffer, &msg);

            if (msg.with_token) {
                remove_connection_request(msg.sender_address);
                token_is_present = true;

                // when the process receives connection message with the token and either
                // it is not connected to any client or its neighbour's address is the same
                // as neighbour's address from the message, then the process must set
                // it's neighbour to be the original process that created the connection message
                // (in any case, the token may be freed)
                if ((connection_established && (msg.neighbour_address == get_neighbour_address())) ||
                        (!connection_established)) {

                    set_neighbour_address(msg.client_address);
                    connection_established = true;
                    token_is_free = true;
                }

                // in any other case, the message needs to be forwarded so it reaches the root
                // of the network or the client preceeding the original message creator
                else {
                    msg.type = MSG_CONFWD;
                    forward_data_size = serialize_connection_msg(&msg, forward_buffer);
                    token_is_free = false;
                }
            }

            // if the message doesn't contain the token, then it can only be a connection request
            // to this process that needs to be queued
            else {
                add_connection_request(msg.client_address);
            }
        }

        // after the message is processed, if the token was received,
        // the process needs to run the token processing thread
        if (token) {
            std::cout << "running token processing..." << std::endl;
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
    username = argv[1];

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
