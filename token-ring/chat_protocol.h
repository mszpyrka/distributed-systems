#ifndef __CHAT_PROTOCOL_H__
#define __CHAT_PROTOCOL_H__

#include <netinet/in.h> 

// protocol message types
#define MSG_TOKEN_FREE  1   // free token
#define MSG_TOKEN_TAKEN 2   // token + chat message
#define MSG_CONREQ      3   // connection request from new client
#define MSG_CONACC      4   // connection request acceptance
#define MSG_CONEST      5   // indication that new connection has been successfully established

#define TRANSPORT_TCP   1
#define TRANSPORT_UDP   2

#define MAX_TCP_REQUESTS 5

#define MAX_MSG_SIZE 127

struct token_message {
    char buffer[MAX_MSG_SIZE];
    char type;
    unsigned char sender_index;
    unsigned char receiver_index;
    unsigned char message_index;
    unsigned char total_length;
};

struct connection_message {
    char type;
    char is_first_receiver;
    sockaddr_in client_address;
    sockaddr_in neighbour_address;
};

void deserialize_token_msg(const char* buffer, struct token_message* token);
void deserialize_connection_msg(const char* buffer, struct connection_message* token);
int serialize_token_msg(const struct token_message* msg, char* buffer);
int serialize_connection_msg(const struct connection_message* msg, char* buffer);

void set_address(const char* ip_string, uint16_t port, sockaddr_in* address);

// provides abstraction level over communication between clients
class Transmission {

    char _transport_protocol;
    sockaddr_in _self_address;

    int _udp_socket;
    int _tcp_receive_socket, _tcp_send_socket;

    public:
        Transmission(const char* ip_string, uint16_t port, char protocol);

        int receive_bytes(char* buffer, int buffer_len, struct sockaddr_in* sender_address);
        int send_bytes(const char* buffer, int size, const struct sockaddr_in* address);
};

#endif