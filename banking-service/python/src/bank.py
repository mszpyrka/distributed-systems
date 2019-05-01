import logging
import threading
import grpc
import json
import Ice
import sys
import random
import string
import inspect
from threading import Lock
sys.path.append('../idl_out/ice')
sys.path.append('../idl_out/grpc')

import Accounts
import exchange_rate_pb2_grpc
import exchange_rate_pb2
from bankingUtils import string2currency, currency2string


def random_password(length):
    """
    Used to generate passwords for new users.
    Creates random string of given length containing only lowercase letters.
    """
    password = ''.join(random.choices(string.ascii_lowercase, k=length))
    return password


def authenticate(func):
    """
    Performs password check on the account before the operation
    is realized.
        - func: callable, should be account's instance method
            in order to extract access password from 'self' argument,
            should accept 'context' argument
    """
    def decorator(*args, **kwargs):

        binding = inspect.signature(func).bind(*args, **kwargs).arguments

        try:
            obj = binding['self']
            context = binding['current'].ctx

            if obj._password != context['password']:
                raise Accounts.AuthenticationFailedException(
                    'incorrect PESEL or password')

        except KeyError:
            raise Accounts.AuthenticationFailedException(
                'no password found')

        return func(*args, **kwargs)

    return decorator


class AccountManagerI(Accounts.AccountManager):
    """
    Provides functionality for standard account type.
    """

    def __init__(self, full_name, PESEL, password,
                 monthly_income, account_type,
                 home_currency, foreign_currencies,
                 exchange_rates):
        """
        Initializes proper manager instance.
        Non-obvious parameters:
            - home_currency: currency that the account relates to when
                performing any currency conversions
            - foreign_currencies: other currencies supported by the account.
                Every currency has it's own balance stored
                (initially set to 0.0).
            - exchange_rates: (callable, must return <Currency, double> dict)
                when called, provides dictionary with current exchange rates
                for all supported currencies (relative to bank's home currency).
        """
        self._full_name = full_name
        self._PESEL = PESEL
        self._password = password
        self._monthly_income = monthly_income
        self._account_type = account_type
        self._home_currency = home_currency
        self._foreign_currencies = foreign_currencies
        self._exchange_rates = exchange_rates

        curs = foreign_currencies + [home_currency]
        self._balance = {c.upper(): 0.0 for c in curs}

    # ==========================================================================
    # slice interface methods implementation:
    # ==========================================================================

    @authenticate
    def getAccountDetails(self, current=None):
        balance = {string2currency(key, protocol='ice') : value
                   for key, value in self._balance.items()}

        return Accounts.AccountDetails(
            declaredMonthlyIncome=self._monthly_income,
            balance=balance)

    @authenticate
    def transferToAccount(self, currency, amount, current=None):
        try:
            cur_str = currency2string(currency, protocol='ice')
            self._balance[cur_str] += amount
        except KeyError:
            raise Accounts.IllegalCurrencyException(
                'currency {} is not supported by this bank'.
                    format(str(currency)))

    @authenticate
    def withdrawFromAccount(self, currency, amount, current=None):
        try:
            cur_str = currency2string(currency, protocol='ice')
            if self._balance[cur_str] >= amount:
                self._balance[cur_str] -= amount
            else:
                raise Accounts.InsufficientBalanceException(
                    'account balance insufficient to complete the operation')

        except KeyError:
            raise Accounts.IllegalCurrencyException(
                'currency {} is not supported by this bank'.
                    format(str(currency)))


class PremiumAccountManagerI(AccountManagerI, Accounts.PremiumAccountManager):
    """
    Provides additional functionality of calculating credit costs
    for premium account users.
    """

    @authenticate
    def getCreditCosts(self, currency, amount, monthsDuration, current=None):
        try:
            cur_str = currency2string(currency, protocol='ice')
            costs = amount + (monthsDuration * amount * 0.05)

            if cur_str == self._home_currency:
                return Accounts.CreditCosts(homeCurrency=costs)

            rates = self._exchange_rates()

            home_costs = rates[cur_str] * (costs + 0.01) # conversion fee
            return Accounts.CreditCosts(homeCurrency=home_costs,
                                        foreignCurrency=costs)
        except KeyError:
            raise Accounts.IllegalCurrencyException(
                'currency {} is not supported by this bank'.
                    format(cur_str))


class BankManagerI(Accounts.BankManager):

    def __init__(self, config, communicator, adapter):
        self._config = config
        self._home_currency = config['homeCurrency']
        self._foreign_currencies = config['foreignCurrencies']
        self._premium_account_threshold = config['premiumAccountThreshold']
        self._communicator = communicator
        self._adapter = adapter

        self._exchange_rates = {}
        self._accounts = {}
        self._rates_lock = Lock()

        self._connect_to_exchange_rates_provider()

    def _connect_to_exchange_rates_provider(self):

        def update():
            rates_server_address = '{}:{}'.format(
                self._config['ratesProviderAddress'],
                self._config['ratesProviderPort'])

            with grpc.insecure_channel(rates_server_address) as channel:
                stub = exchange_rate_pb2_grpc.ExchangeRatesProviderStub(channel)
                msg = exchange_rate_pb2.Subscription()
                msg.homeCurrency = string2currency(
                    self._home_currency, protocol='grpc')
                f_cur = [string2currency(c, protocol='grpc')
                         for c in self._foreign_currencies]
                msg.foreignCurrencies[:] = f_cur
                updates = stub.subscribe(msg)

                try:
                    for u in updates:
                        self._update_exchange_rates(u)
                except grpc._channel._Rendezvous as err:
                    print(err)

        update_thread = threading.Thread(target=update)
        update_thread.start()



    def _update_exchange_rates(self, update):
        with self._rates_lock:
            print('currency updates: {}'.format(update))
            for r in update.rates:
                print(r)
                cur = currency2string(r.currency, protocol='grpc')
                self._exchange_rates[cur] = r.value

            print(self._exchange_rates)

    def _get_exchange_rates(self):
        with self._rates_lock:
            return self._exchange_rates.copy()

    def _get_account_proxy(self, PESEL, current):
        try:
            manager = self._accounts[PESEL]
            ac_type = manager._account_type
            identifier = PESEL + str(ac_type)
            base = current.adapter.createProxy(
                Ice.stringToIdentity(identifier))

            if ac_type == Accounts.AccountType.STANDARD:
                return Accounts.AccountManagerPrx.uncheckedCast(base)

            else:
                return Accounts.PremiumAccountManagerPrx.uncheckedCast(base)

        except KeyError:
            raise Accounts.IllegalPeselException(
                'given PESEL does not match any account')

    # ==========================================================================
    # slice interface methods implementation:
    # ==========================================================================

    def registerNewAccount(self, fullName, PESEL,
                           declaredMonthlyIncome, current=None):
        if PESEL in self._accounts.keys():
            raise Accounts.PeselRegisteredException(
                'given PESEL is already registered')
        new_password = random_password(2)

        if declaredMonthlyIncome < self._premium_account_threshold:
            ac_type = Accounts.AccountType.STANDARD
            account_manager = AccountManagerI(fullName, PESEL, new_password,
                                              declaredMonthlyIncome, ac_type,
                                              self._home_currency,
                                              self._foreign_currencies,
                                              self._get_exchange_rates)

        else:
            ac_type = Accounts.AccountType.PREMIUM
            account_manager = PremiumAccountManagerI(fullName, PESEL,
                                                     new_password,
                                                     declaredMonthlyIncome,
                                                     ac_type,
                                                     self._home_currency,
                                                     self._foreign_currencies,
                                                     self._get_exchange_rates)

        identifier = PESEL + str(ac_type)
        self._adapter.add(account_manager,
                          communicator.stringToIdentity(identifier))
        self._accounts[PESEL] = account_manager

        print('new account registered (PESEL = {}, type = {}, password = {})'.
            format(PESEL, str(ac_type), new_password))

        return Accounts.RegistrationStatus(ac_type, new_password,
            self._get_account_proxy(PESEL, current))

    def recoverAccountAccess(self, PESEL, current=None):
        try:
            return Accounts.AccountAccess(
                self._accounts[PESEL]._account_type,
                self._get_account_proxy(PESEL, current))
        except KeyError:
            raise Accounts.AuthenticationFailedException(
                'incorrect PESEL or password')



if __name__ == '__main__':

    if not sys.argv or sys.argv[1] in ['help', '--help']:
        print(
            '''
            bank app
            usage:
                python3 bank.py path_to_json json_object_name
            '''
        )

    else:

        json_path = sys.argv[1]
        json_object = sys.argv[2]
        global_config = json.load(open(json_path))
        local_config = global_config[json_object]

        with Ice.initialize(sys.argv) as communicator:

            address = local_config['address']
            port = local_config['port']

            adapter = communicator.createObjectAdapterWithEndpoints(
                "BankAdapter", "default -p {} -h {}".format(port, address))

            bank_manager = BankManagerI(
                local_config,
                communicator,
                adapter
            )

            adapter.add(bank_manager, communicator.stringToIdentity("BankManager"))
            adapter.activate()
            communicator.waitForShutdown()