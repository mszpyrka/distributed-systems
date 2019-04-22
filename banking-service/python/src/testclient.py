from concurrent import futures
import time
import logging
import grpc
import json

import sys
sys.path.append('../proto_out/')

import exchange_rate_pb2_grpc
import exchange_rate_pb2

def run(config):

    with grpc.insecure_channel(config['address'] + ':' + str(config['port'])) as channel:
        stub = exchange_rate_pb2_grpc.ExchangeRatesProviderStub(channel)

        response = stub.hello(exchange_rate_pb2.Hello(hello='hi'))
        print(response)

        cur = [exchange_rate_pb2.CHF, exchange_rate_pb2.PLN]

        msg = exchange_rate_pb2.Subscription()
        msg.currencies[:] = cur
        updates = stub.subscribe(msg)

        try:
            for u in updates:
                print('update: ', u)
        except grpc._channel._Rendezvous as err:
            print(err)


if __name__ == '__main__':
    global_config = json.load(open('config.json'))
    logging.basicConfig()
    run(global_config['currencyServer'])