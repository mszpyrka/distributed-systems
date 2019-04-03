import threading
import pika
from utils import Consumer, Producer
from uuid import uuid4


class Medic:
    """
    Creates and sends specialized examination requests and receives
    the results. Every request must be of one of following types:
        - hip examination
        - knee examination
        - elbow examination
    """

    def __init__(self, exchange_name, connection_address):
        """
        Initializes all data structures that will be used
        for receiving processed examination requests.
        """

        self._results_consumer = Consumer(exchange_name, connection_address)
        self._results_queue = self._results_consumer.add_queue(
            callback=self.process_results)
        self._results_consumer.start(new_thread=True)

        self._requests_producer = Producer(exchange_name, connection_address)

        # Set of unique ids for every sent request
        # that hasn't been processed yet
        self._pending_requests = set()
        self._requests_lock = threading.Lock()

    def send_request(self, patient_name, injury_type):
        request_id = str(uuid4())
        routing_key = 'specialist.' + injury_type
        message = patient_name + '$' + injury_type

        message_opts = {
            'properties': pika.BasicProperties(
                reply_to = self._results_queue,
                correlation_id = request_id
            )
        }

        with self._requests_lock:
            self._pending_requests.add(request_id)
            self._requests_producer.send_message(
                routing_key,
                message,
                **message_opts
            )

        print('sending request: ', injury_type, 'examination for ',
              patient_name, '( request id: ', request_id, ')')

    def process_results(self, ch, method, properties, body):
        ignore = False
        request_id = properties.correlation_id

        with self._requests_lock:

            if request_id in self._pending_requests:
                self._pending_requests.remove(request_id)

            else:
                ignore = True

        if not ignore:
            print('received results: ', body, ' (request id: ', request_id, ')')

        ch.basic_ack(delivery_tag=method.delivery_tag)


if __name__ == '__main__':

    medic = Medic('hospital', 'localhost')

    while True:
        line = input('>')
        tokens = line.split()
        name = tokens[0]
        injury = tokens[1]
        medic.send_request(name, injury)
