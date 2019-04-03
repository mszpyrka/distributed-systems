from termcolor import colored
from utils import Producer, Consumer


class Administrator:
    """
    Receives and logs every message sent within the whole system.
    Is allowed to broadcast messages to all other workers.
    """

    def __init__(self, exchange_name, connection_address):
        self._sniffer = Consumer(exchange_name, connection_address)
        self._sniffer_queue = self._sniffer.add_queue(
            routing_key='#',
            callback=self.process_message
        )
        self._sniffer.start(new_thread=True)

        self._info_producer = Producer(exchange_name, connection_address)

    def send_info(self, message):
        print('sending info: ', message)
        self._info_producer.send_message('adm.info', message)

    def process_message(self, ch, method, properties, body):
        body = body.decode()
        id_tag = ' (id: ' + properties.correlation_id[:8] + ')'\
            if properties.correlation_id is not None \
            else ''

        log = colored('sniffed message: ' + body + id_tag, 'green')
        print(log)
        ch.basic_ack(delivery_tag=method.delivery_tag)


if __name__ == '__main__':

    admin = Administrator('hospital', 'localhost')

    while True:
        line = input()
        admin.send_info(line)
