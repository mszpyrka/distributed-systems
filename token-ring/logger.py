import socket
import time

ip = "224.0.0.1"
port = 9090

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind((ip, port))

while True:
    data, addr = sock.recvfrom(1024)
    print(time.time(), ": \t", data.decode("utf-8"))