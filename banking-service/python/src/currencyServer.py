from concurrent import futures
import time
import logging
import grpc
import json

import sys
sys.path.append('../proto_out/')

import exchange_rate_pb2_grpc
import exchange_rate_pb2


class Provider(exchange_rate_pb2_grpc.ExchangeRatesProviderServicer):

    def getExchangeRates(self, request, context):
        print('jajebie')
        currencies = request.currencyList
        print(currencies)

    def hello(self, request, context):
        print(request.hello)
        return exchange_rate_pb2.Hello(hello='Hello')


def serve(config):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    exchange_rate_pb2_grpc.add_ExchangeRatesProviderServicer_to_server(Provider(), server)
    server.add_insecure_port(config['address'] + ':' + str(config['port']))
    server.start()
    try:
        while True:
            time.sleep(60 * 60 * 24)
    except KeyboardInterrupt:
        server.stop(0)


if __name__ == '__main__':

    global_config = json.load(open('config.json'))
    logging.basicConfig()
    serve(global_config['currencyServer'])