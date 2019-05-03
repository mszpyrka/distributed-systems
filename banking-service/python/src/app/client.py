import Ice
import IcePy
import sys
sys.path.append('../idl/ice')

import Accounts

from bankingUtils import string2currency

def authenticate(func):
    def wrapped(*args, **kwargs):
        password = input('password: ')
        ctx = {'password': password}
        return func(*args, **kwargs, context=ctx)

    return wrapped

class UserInterface:

    def __init__(self, bank_address, communicator):
        """
        Initializes connection to the bank server.
        """
        self._bank_address = bank_address
        self._communicator = communicator

        bank_base = communicator.stringToProxy('BankManager:' + bank_address)
        bank_proxy = Accounts.BankManagerPrx.checkedCast(bank_base)

        if bank_proxy:
            self._bank_proxy = bank_proxy
        else:
            raise RuntimeError("Invalid bank proxy")

    def _cast_proxy(self, proxy):
        """
        Casts given proxy to specific account type.
        """
        result = Accounts.PremiumAccountManagerPrx.checkedCast(proxy)
        if result is not None:
            return result

        result = Accounts.AccountManagerPrx.checkedCast(proxy)
        if result is not None:
            return result

        return proxy

    def _get_proxy(self, pesel):
        """
        Recovers account proxy from bank server.
        """
        access = self._bank_proxy.recoverAccountAccess(pesel)
        return self._cast_proxy(access.manager), access.type

    def _register(self):
        """
        Performs new account registration procedure.
        """
        name = input('name: ')
        pesel = input('PESEL: ')
        income = float(input('monthly income: '))

        try:
            status = self._bank_proxy.registerNewAccount(name, pesel, income)
            print('account registered: type = {}, password = \'{}\''.
                  format(status.type,status.password))
            context = {'password': status.password}
            manager = self._cast_proxy(status.manager)
            self._user_panel(manager, pesel, status.type, context)
            return True

        except Exception as e:
            print('error: ', str(e))

        return False

    def _get_credit(self, manager, context):
        """
        Obtains credit costs.
        """
        try:
            cur = string2currency(input('currency: '), protocol='ice')
            amount = float(input('amount: '))
            duration = int(input('months duration: '))
            result = manager.getCreditCosts(cur, amount, duration, context)

            print('home currency costs: {}'.format(result.homeCurrency))

            if result.foreignCurrency:
                print('foreign currency costs: {}'.
                      format(result.foreignCurrency))

            return True

        except AttributeError as e:
            print('error: ', str(e))
            print('your account probably does not support this functionality')

        except Exception as e:
            print('error: ', str(e))

        return False

    def _get_info(self, manager, context):
        """
        Obtaining account's details.
        """
        try:
            info = manager.getAccountDetails(context)
            print('balance: ', info.balance)
            return True

        except Exception as e:
            print('error: ', str(e))

        return False

    def _put_money(self, manager, context):
        """
        Transferring money into account.
        """
        try:
            cur = string2currency(input('currency: '), protocol='ice')
            amount = float(input('amount: '))
            manager.transferToAccount(cur, amount, context)
            return True

        except Exception as e:
            print('error: ', str(e))

        return False

    def _take_money(self, manager, context):
        """
        Withdrawing money from account.
        """
        try:
            cur = string2currency(input('currency: '), protocol='ice')
            amount = float(input('amount: '))
            manager.withdrawFromAccount(cur, amount, context)
            return True

        except Exception as e:
            print('error: ', str(e))

        return False

    def _log_in(self):
        """
        App's log in panel
        """
        try:
            pesel = input('pesel: ')
            password = input('password: ')
            context = {'password': password}
            manager, ac_type = self._get_proxy(pesel)
            self._user_panel(manager, pesel, ac_type, context)

        except Exception as e:
            print('error: ', str(e))

    def _user_panel(self, manager, pesel, account_type, context):
        """
        App's user panel.
        """
        if not self._get_info(manager, context):
            return

        commands = ['get-credit', 'get-info', 'put-money', 'take-money', 'log-out']

        while True:
            c = input('{} {}> '.format(pesel, account_type))

            if c in ['c', 'credit', 'get-credit']:
                self._get_credit(manager, context)
            elif c in ['i', 'info', 'get-info']:
                self._get_info(manager, context)
            elif c in ['p', 'put', 'put-money']:
                self._put_money(manager, context)
            elif c in ['t', 'take', 'take-money']:
                self._take_money(manager, context)
            elif c in ['o', 'out', 'log-out']:
                return
            else:
                print('available commands: {}'.format(', '.join(commands)))


    def start(self):
        """
        App's main menu.
        """
        commands = ['register', 'log-in', 'quit']

        while True:
            c = input('> ')

            if c in ['r', 'register']:
                self._register()
            elif c in ['l', 'log', 'log-in']:
                self._log_in()
            elif c in ['q', 'quit']:
                return
            else:
                print('available commands: {}'.format(', '.join(commands)))


if __name__ == '__main__':

    if len(sys.argv) != 3:
        print(
            """
            client app
            usage: python3 client.py bank_address bank_port
            """
        )

    else:
        address = sys.argv[1]
        port = sys.argv[2]

        with Ice.initialize(sys.argv) as communicator:
            bank_address = 'default -p {} -h {}'.format(port, address)
            interface = UserInterface(bank_address, communicator)
            interface.start()