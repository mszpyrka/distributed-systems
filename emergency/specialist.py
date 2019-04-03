import pika
import sys
from utils import Consumer, Producer


class Specialist:
    """
    Receives and processes specialized requests.
    Every specialist must be able to process exactly two
    out of three injury types (hip / knee / elbow).
    """

    def __init__(self, exchange_name, connection_address, specializations):

        self._requests_consumer = Consumer(exchange_name, connection_address)

        for spec in specializations:
            self._requests_consumer.add_queue(
                queue_name=spec,
                routing_key='specialist.' + spec,
                callback=self.process_request)

        self._requests_consumer.start(new_thread=True)

        self._results_producer = Producer(exchange_name, connection_address)


    def process_request(self, ch, method, properties, body):

        request_id = properties.correlation_id
        target = properties.reply_to

        print('received request: ', body, ' ( request id: ', request_id, ')')

        message_opts = {
            'properties': pika.BasicProperties(
                correlation_id=request_id
            )
        }

        self._results_producer.send_message(
            routing_key=target,
            message='done',
            **message_opts
        )

        ch.basic_ack(delivery_tag=method.delivery_tag)


if __name__ == '__main__':

    specializations = sys.argv[1:]

    spec = Specialist('hospital', 'localhost', specializations)