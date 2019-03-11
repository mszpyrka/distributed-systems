#ifndef __CHAT_PROTOCOL_H__
#define __CHAT_PROTOCOL_H__

#include <netinet/in.h> 

// logger settings
#define LOGGER_IP   "224.0.0.1"
#define LOGGER_PORT 9090

// protocol message types
#define MSG_DATA    1   // data message
#define MSG_CONREQ  2   // connection request
#define MSG_CONFWD  3   // forwarding connection request

#define TRANSPORT_TCP   1
#define TRANSPORT_UDP   2

#define TOKEN_SLEEP_TIME 1000000   // sleep time in miliseconds after receiving the token

#define MAX_TCP_REQUESTS 5

#define MAX_MSG_SIZE 127

struct data_message {
    char type;
    char token_is_free;
    char buffer[MAX_MSG_SIZE];
    unsigned char sender_index;
    unsigned char receiver_index;
    unsigned char data_index;
    unsigned char total_length;
};

struct connection_message {
    char type;
    char with_token;
    sockaddr_in sender_address;
    sockaddr_in client_address;
    sockaddr_in neighbour_address;
};

void deserialize_data_msg(const char* buffer, int len, struct data_message* msg);

void deserialize_connection_msg(const char* buffer, struct connection_message* msg);
int serialize_connection_msg(const struct connection_message* msg, char* buffer);

void set_address(const char* ip_string, uint16_t port, sockaddr_in* address);

// provides abstraction level over communication between clients
class Transmission {

    char _transport_protocol;
    sockaddr_in _self_address;

    int _udp_socket;
    int _tcp_receive_socket;

    bool _debug;

    public:
        Transmission(const char* ip_string, uint16_t port, char protocol, bool debug = false);

        int receive_bytes(char* buffer, int buffer_len, struct sockaddr_in* sender_address);
        int send_bytes(const char* buffer, int size, const struct sockaddr_in* address);
        void log(const char* message, int len);
};

#endif