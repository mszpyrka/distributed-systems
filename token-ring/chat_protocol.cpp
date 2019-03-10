#include <cstring> 
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <iostream>

#include "chat_protocol.h"

// converts char buffer to proper token message structure
void get_token_msg(const char* buffer, struct token_message* msg) {

    msg->type = buffer[0];
    strcpy(msg->message, &buffer[1]);
}

// converts char buffer to proper connection message structure
void get_connection_msg(const char* buffer, struct connection_message* msg) {

    msg->type = buffer[0];
    msg->first_receiver = buffer[1];
    std::memcpy((void*)&msg->client_address, (void*)&buffer[2], sizeof(sockaddr_in));
    std::memcpy((void*)&msg->neighbour_address, (void*)&buffer[2 + sizeof(sockaddr_in)], sizeof(sockaddr_in));
}

// saves token message into char array and returns the size of the serialized messages
int serialize_token_msg(const struct token_message* msg, char* buffer) {

    buffer[0] = msg->type;
    strcpy(&buffer[1], msg->message);

    int message_length = strlen(msg->message);
    return message_length + 1;
}

// saves connection message into char array and returns the size of the serialized messages
int serialize_connection_msg(const struct connection_message* msg, char* buffer) {

    buffer[0] = msg->type;
    buffer[1] = msg->first_receiver;

    std::memcpy((void*)&buffer[2], (void*)&msg->client_address, sizeof(sockaddr_in));
    std::memcpy((void*)&buffer[2 + sizeof(sockaddr_in)], (void*)&msg->neighbour_address, sizeof(sockaddr_in));

    return sizeof(connection_message);
}

// reads a message from given socket and stores sender's address
//      - if transport_protocol is set to TRANSPORT_TCP:
//          accepts new connection comming from given socket, reads data from
//          newly created client socket and instantly closes the connection
//      - if transport_protocol is set to TRANSPORT_UDP:
//          simply reads incomming data
int receive_message(char* buffer, int buffer_len, int socket_fd, struct sockaddr_in* sender_address, char transport_protocol) {

    int read_socket_fd;

    if (transport_protocol == TRANSPORT_TCP) {
        read_socket_fd = accept(socket_fd, NULL, NULL);

        if (read_socket_fd < 0) {
            perror("ERROR on accept");
            exit(1);
        }
    }

    else {
        read_socket_fd = socket_fd;
    }

    socklen_t addr_len = sizeof(*sender_address);
    int bytes_read = recvfrom(socket_fd, buffer, buffer_len, MSG_WAITALL,
        (struct sockaddr*) &sender_address, &addr_len);
    if (bytes_read < 0) perror("ERROR reading from socket");

    return bytes_read;
}

// properly sets up sockaddr_in structure based on human readable values
void set_address(const char* ip_string, uint16_t port, sockaddr_in* address) {

    memset(address, 0, sizeof(sockaddr_in));
    address->sin_family = AF_INET;
    address->sin_port = htons(port);
    address->sin_addr.s_addr = inet_addr(ip_string);
}



Transmission::Transmission(const char* ip_string, uint16_t port, char protocol) {

    set_address(ip_string, port, &_self_address);

    _transport_protocol = protocol;

    int socket_fd;

    if(protocol == TRANSPORT_TCP) {

        if ((socket_fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            perror("ERROR on creating TCP socket");
            exit(1);
        }
        _tcp_receive_socket = socket_fd;
    }

    else if(protocol == TRANSPORT_UDP) {

        if ((socket_fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
            perror("ERROR on creating UDP socket");
            exit(1);
        }
        _udp_socket = socket_fd;
    }

    if (bind(socket_fd, (const struct sockaddr*) &_self_address, sizeof(_self_address)) < 0) {
        perror("ERROR on binding socket");
        exit(1);
    }

    if (protocol == TRANSPORT_TCP) {

        if (listen(socket_fd, MAX_TCP_REQUESTS) < 0) {
            perror("ERROR when setting listen on socketS");
            exit(1);
        }
    }
}

int Transmission::receive_bytes(char* buffer, int buffer_len, struct sockaddr_in* sender_address) {

    int read_socket;

    if (_transport_protocol == TRANSPORT_TCP) {

        if ((read_socket = accept(_tcp_receive_socket, NULL, NULL)) < 0) {
            perror("ERROR when accepting on TCP socket");
            exit(1);
        }
    }

    else if (_transport_protocol == TRANSPORT_UDP) {

        read_socket = _udp_socket;
    }

    socklen_t addr_len = sizeof(sockaddr_in);
    int bytes_read = recvfrom(read_socket, buffer, buffer_len, 0,
        (struct sockaddr*) &sender_address, &addr_len);

    if (bytes_read < 0) {
        perror("ERROR reading from socket");
        exit(1);
    }

    if (_transport_protocol == TRANSPORT_TCP) {

        if (close(read_socket) < 0) {
            perror("ERROR when closing TCP socket");
            exit(1);
        }
    }

    return bytes_read;
}


int Transmission::send_bytes(const char* buffer, int size, const struct sockaddr_in* address) {

    int write_socket;

    if (_transport_protocol == TRANSPORT_TCP) {

        if ((write_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            perror("ERROR on creating TCP socket");
            exit(1);
        }
        if (connect(write_socket, (const sockaddr*) address, sizeof(sockaddr_in)) < 0) {
            perror("ERROR when connecting to TCP socket");
            exit(1);
        }
    }

    else if (_transport_protocol == TRANSPORT_UDP) {
        write_socket = _udp_socket;
    }

    int bytes_sent = sendto(write_socket, buffer, size, 0,
        (const struct sockaddr*) address, sizeof(sockaddr_in));

    if (bytes_sent < 0) {
        perror("ERROR sending to socket");
        exit(1);
    }

    if (_transport_protocol == TRANSPORT_TCP) {

        if (close(write_socket) < 0) {
            perror("ERROR when closing TCP socket");
            exit(1);
        }
    }

    return bytes_sent;
}
