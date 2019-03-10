#include <cstring> 
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <iostream>

#include "chat_protocol.h"


void error_exit(const char* message) {
    perror(message);
    exit(1);
}


// converts char buffer to proper token message structure
void deserialize_token_msg(const char* buffer, int len, struct token_message* msg) {
    msg->type = buffer[0];
    memcpy(&msg->buffer, buffer, len);

    // sender's name starts at the first index
    msg->sender_index = 1;

    // receiver's name is separated from sender's name with single 0
    msg->receiver_index = msg->sender_index + 1 + strlen(&msg->buffer[msg->sender_index]);

    // actual data is separated from receiver's name with single 0
    msg->message_index = msg->receiver_index + 1 + strlen(&msg->buffer[msg->receiver_index]);
}


// converts char buffer to proper connection message structure
void deserialize_connection_msg(const char* buffer, struct connection_message* msg) {
    msg->type = buffer[0];
    msg->is_first_receiver = buffer[1];
    std::memcpy((void*)&msg->client_address, (void*)&buffer[2], sizeof(sockaddr_in));
    std::memcpy((void*)&msg->neighbour_address, (void*)&buffer[2 + sizeof(sockaddr_in)], sizeof(sockaddr_in));
}


// saves connection message into char array and returns the size of the serialized messages
int serialize_connection_msg(const struct connection_message* msg, char* buffer) {
    buffer[0] = msg->type;
    buffer[1] = msg->is_first_receiver;

    std::memcpy((void*)&buffer[2], (void*)&msg->client_address, sizeof(sockaddr_in));
    std::memcpy((void*)&buffer[2 + sizeof(sockaddr_in)], (void*)&msg->neighbour_address, sizeof(sockaddr_in));

    return sizeof(connection_message);
}


// properly sets up sockaddr_in structure based on human readable values
void set_address(const char* ip_string, uint16_t port, sockaddr_in* address) {
    memset(address, 0, sizeof(sockaddr_in));
    address->sin_family = AF_INET;
    address->sin_port = htons(port);

    if(inet_pton(AF_INET, ip_string, &address->sin_addr) < 0)
        error_exit("ERROR when converting string IP address to numerical format");
}


// ==========================================================================================
// Transmission class implementation
// ==========================================================================================

// sets up all address and socket related structures for given transport protocol
Transmission::Transmission(const char* ip_string, uint16_t port, char protocol) {
    set_address(ip_string, port, &_self_address);
    _transport_protocol = protocol;

    int socket_fd;

    if(protocol == TRANSPORT_TCP) {
        if ((socket_fd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
            error_exit("ERROR on creating TCP socket");

        _tcp_receive_socket = socket_fd;
    }

    else if(protocol == TRANSPORT_UDP) {
        if ((socket_fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
            error_exit("ERROR on creating UDP socket");
 
        _udp_socket = socket_fd;
    }

    if (bind(socket_fd, (const struct sockaddr*) &_self_address, sizeof(_self_address)) < 0)
        error_exit("ERROR on binding socket");

    if (protocol == TRANSPORT_TCP) {
        if (listen(socket_fd, MAX_TCP_REQUESTS) < 0)
            error_exit("ERROR when setting listen on socketS");
    }
}


/**
 * If protocol is set to TRANSPORT_UDP, simply reads given number of bytes from UDP socket;
 * 
 * If protocol is set to TRANSPORT_TCP, first accepts new connection, then reads bytes
 * from newly created client socket, finally terminates connection by closing client socket descriptor. 
 */
int Transmission::receive_bytes(char* buffer, int buffer_len, struct sockaddr_in* sender_address) {

    // socket that the data will be read from
    int read_socket;

    if (_transport_protocol == TRANSPORT_TCP) {
        if ((read_socket = accept(_tcp_receive_socket, NULL, NULL)) < 0)
            error_exit("ERROR when accepting on TCP socket");
    }

    else if (_transport_protocol == TRANSPORT_UDP) {
        read_socket = _udp_socket;
    }

    socklen_t addr_len = sizeof(sockaddr_in);
    int bytes_read = recvfrom(read_socket, buffer, buffer_len, 0, (struct sockaddr*) &sender_address, &addr_len);

    if (bytes_read < 0)
        error_exit("ERROR when reading from socket");

    if (_transport_protocol == TRANSPORT_TCP) {
        if (close(read_socket) < 0)
            error_exit("ERROR when closing TCP socket");
    }

    return bytes_read;
}


/**
 * If protocol is set to TRANSPORT_UDP, simply sends given number of bytes from UDP socket;
 * 
 * If protocol is set to TRANSPORT_TCP, first creates new socket, then connects to given address,
 * sends the data through newly created socket and terminates connection by closing the socket descriptor. 
 */
int Transmission::send_bytes(const char* buffer, int size, const struct sockaddr_in* address) {

    // socket that the data will be written to
    int write_socket;

    if (_transport_protocol == TRANSPORT_TCP) {
        if ((write_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0)
            error_exit("ERROR on creating TCP socket");

        if (connect(write_socket, (const sockaddr*) address, sizeof(sockaddr_in)) < 0)
            error_exit("ERROR when connecting to TCP socket");
    }

    else if (_transport_protocol == TRANSPORT_UDP) {
        write_socket = _udp_socket;
    }

    int bytes_sent = sendto(write_socket, buffer, size, 0, (const struct sockaddr*) address, sizeof(sockaddr_in));

    if (bytes_sent < 0)
        error_exit("ERROR sending to socket");

    if (_transport_protocol == TRANSPORT_TCP) {
        if (close(write_socket) < 0)
            error_exit("ERROR when closing TCP socket");
    }

    return bytes_sent;
}
