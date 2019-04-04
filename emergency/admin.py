from termcolor import colored
from utils import Producer, Consumer


class Administrator:
    """
    Receives and logs every message sent within the whole system.
    Is allowed to broadcast messages to all other workers.
    """

    def __init__(self):
        self._log_consumer = Consumer('hospital', 'topic', 'localhost')
        self._log_queue = self._log_consumer.add_queue(
            routing_key='#',
            callback=self.process_log
        )
        self._log_consumer.start(new_thread=True)
        self._info_producer = Producer('info', 'fanout', 'localhost')

    def send_info(self, message):
        print('sending info: ', message)
        self._info_producer.send_message(message=message)

    def process_log(self, ch, method, properties, body):
        body = body.decode()
        log = colored('LOG: ' + body, 'yellow')
        print(log)
        ch.basic_ack(delivery_tag=method.delivery_tag)


if __name__ == '__main__':

    admin = Administrator()

    while True:
        line = input()
        admin.send_info(line)
