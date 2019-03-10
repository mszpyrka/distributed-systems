#include <cstdio>
#include <cstdlib>
#include <iostream>

#include <cstring>
#include <queue>
#include <mutex>
#include <condition_variable>

#include <unistd.h>

#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>

#include "chat_protocol.h"

#define PORT 8080

// initial setup parameters
char* username;
sockaddr_in self_address;
bool is_connected;
char transport_protocol;

// next client pointer
sockaddr_in neighbour_address;
bool connection_established;
std::mutex mt_neighbour_address;

// text messages queue
std::queue<std::string> message_queue;
std::mutex mt_message_queue;

// token parameters
bool token_is_present;
bool token_is_free;
std::mutex mt_token;
std::condition_variable cv_token_wait;



int main(int argc, char const *argv[])
{
    uint16_t port1, port2;
    port1 = 9998;
    port2 = 9999;
    char address[] = "127.0.0.1";

    if(argc >= 2) {

        std::cout << "wysylam..." << std::endl;
        
        Transmission ts(address, port1, TRANSPORT_TCP);

        sockaddr_in addr;
        
        set_address(address, port2, &addr);

        char buffer[] = "twoja stara kretynie bez szkoly";
        int len = 20;

        ts.send_bytes(buffer, 20, &addr);
        
    }  

    else {

        std::cout << "odbieram..." << std::endl;
        
        Transmission ts(address, port2, TRANSPORT_TCP);

        sockaddr_in addr;

        char buffer[128];
        int len = 128;

        ts.receive_bytes(buffer, len, &addr);
        std::cout << buffer[0] << std::endl;
    }  

}
