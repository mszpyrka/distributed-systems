import pika
import sys
from termcolor import colored
from random import randint
from time import sleep

from utils import Consumer, HospitalWorker


class Specialist(HospitalWorker):
    """
    Receives and processes specialized requests.
    Every specialist must be able to process exactly two
    out of three injury types (hip / knee / elbow).
    """

    def __init__(self, exchange_name, connection_address, specializations):
        """
        Initializes connection structures, binds to each queue
        corresponding to specializations.
        """
        super().__init__(exchange_name, connection_address)

        self._requests_consumer = Consumer(exchange_name, connection_address)

        for spec in specializations:
            self._requests_consumer.add_queue(
                queue_name=spec,
                routing_key='hosp.' + spec,
                callback=self.process_request)

        self._requests_consumer.start(new_thread=True)

    def process_request(self, ch, method, properties, body):
        """
        Simulates processing injury examination by sleeping random
        number of seconds (between 1 and 5) and sending back 'results' message.
        """
        body = body.decode()
        request_id = properties.correlation_id
        target = properties.reply_to

        log = colored('processing request: ' + body +
                      ' (request id: ' + request_id[:8] + ')', 'green')
        print(log, end='', flush=True)

        time_to_sleep = randint(1, 5)
        for _ in range(time_to_sleep):
            print(colored('.', 'green'), end='', flush=True)
            sleep(1)

        print('')

        message_opts = {
            'properties': pika.BasicProperties(
                correlation_id=request_id
            )
        }
        message = body + ' done'

        self._producer.send_message(
            routing_key=target,
            message=message,
            **message_opts
        )
        self.send_log(message)

        ch.basic_ack(delivery_tag=method.delivery_tag)


if __name__ == '__main__':

    specializations = sys.argv[1:]

    if len(specializations) != 2:
        print('example usage: python3 specialist.py knee hip')

    else:
        spec = Specialist('hospital', 'localhost', specializations)
