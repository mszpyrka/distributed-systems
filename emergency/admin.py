from termcolor import colored
from utils import Producer, Consumer


class Administrator:
    """
    Receives and logs every message sent within the whole system.
    Is allowed to broadcast messages to all other workers.
    """

    def __init__(self, exchange_name, connection_address):
        self._log_consumer = Consumer(exchange_name, connection_address)
        self._log_queue = self._log_consumer.add_queue(
            routing_key='hosp.log',
            callback=self.process_log
        )
        self._log_consumer.start(new_thread=True)
        self._info_producer = Producer(exchange_name, connection_address)

    def send_info(self, message):
        print('sending info: ', message)
        self._info_producer.send_message('hosp.info', message)

    def process_log(self, ch, method, properties, body):
        body = body.decode()
        log = colored('LOG: ' + body, 'yellow')
        print(log)
        ch.basic_ack(delivery_tag=method.delivery_tag)


if __name__ == '__main__':

    admin = Administrator('hospital', 'localhost')

    while True:
        line = input()
        admin.send_info(line)
