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

// initial setup parameters
char* username;
sockaddr_in self_address;
bool is_connected;
char transport_protocol;



// next client pointer
sockaddr_in neighbour_address;
bool connection_established;
std::mutex mt_neighbour_address;

void set_neighbour_address(const struct sockaddr_in &address) {
    std::lock_guard<std::mutex> lock(mt_neighbour_address);
    neighbour_address = address;
    connection_established = true;
}

struct sockaddr_in get_neighbour_address() {
    std::lock_guard<std::mutex> lock(mt_neighbour_address);
    return neighbour_address;
}



// text messages queue
std::queue<std::string> message_queue;
std::mutex mt_message_queue;



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



// // token parameters
bool token_is_present;
// bool token_is_free;
// std::mutex mt_token;
// std::condition_variable cv_token_wait;

// ==========================================================================================
// Thread methods implementation
// ==========================================================================================

void token_process_thread(Transmission* ts) {
    sleep(1);
    for (auto it : pending_requests) {
        std::cout << it.first << ' ' << it.second << std::endl;
    }
}

void receive_thread(Transmission* ts) {
    char buffer[MAX_MSG_SIZE];
    struct sockaddr_in sender_address;

    while (true) {
        ts->receive_bytes(buffer, MAX_MSG_SIZE, &sender_address);
        char type = buffer[0];

        if (type == MSG_CONREQ) {
            add_connection_request(sender_address);
            std::thread th(&token_process_thread, ts);
            th.detach();
        }

    }
}

int main(int argc, char const *argv[])
{
    std::cout << "usage: ./main login self_ip self_port next_ip next_port [token]" << std::endl;

    int self_port = atoi(argv[3]);
    const char* self_address = argv[2];

    std::cout << self_address << ' ' << self_port << std::endl;

    int next_port;
    const char* next_address;

    next_port = atoi(argv[5]);
    next_address = argv[4];

    Transmission ts(self_address, self_port, TRANSPORT_UDP);
    
    if(next_port > 0) {

        struct sockaddr_in addr;
        set_address(next_address, next_port, &addr);
        set_neighbour_address(addr);

        struct connection_message msg;
        msg.type = MSG_CONREQ;
        set_address(self_address, self_port, &msg.client_address);
        set_address(next_address, next_port, &msg.neighbour_address);

        char buffer[MAX_MSG_SIZE];
        int size = serialize_connection_msg(&msg, buffer);

        ts.send_bytes(buffer, size, &neighbour_address);
    }

    else {
        
        connection_established = false;
    }

    if(argc > 6)
        token_is_present = true;

    else
        token_is_present = false;
    
    
    std::thread receiver(&receive_thread, &ts);

    receiver.join();
    

    // uint16_t port1, port2;
    // port1 = 9998;
    // port2 = 9999;
    // char address[] = "127.0.0.1";

    // if(argc >= 2) {

    //     std::cout << "wysylam..." << std::endl;
        
    //     Transmission ts(address, port1, TRANSPORT_UDP);

    //     sockaddr_in addr;
    //     set_address(address, port2, &addr);

    //     exit(0);

    //     char buffer[] = "twoja stara kretynie bez szkoly";

    //     struct token_message msg;
    //     deserialize_token_msg(buffer, strlen(buffer), &msg);

    //     ts.send_bytes(msg.buffer, msg.total_length, &addr);
    // }  

    // else {

    //     std::cout << "odbieram..." << std::endl;
        
    //     Transmission ts(address, port2, TRANSPORT_UDP);

    //     sockaddr_in addr;

    //     char buffer[128];
    //     int len = 128;

    //     ts.receive_bytes(buffer, len, &addr);

    //     struct token_message msg;
    //     deserialize_token_msg(buffer, strlen(buffer), &msg);

    //     std::cout << &msg.buffer[msg.sender_index] << std::endl;
    // }  

    sleep(10);
}
