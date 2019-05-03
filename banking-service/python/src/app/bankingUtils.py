import sys

sys.path.append('../idl/ice')
sys.path.append('../idl/grpc')

import  Accounts
import exchange_rate_pb2

class UserInputException(Exception):
    pass

_ICE_CURRENCIES = [Accounts.Currency.valueOf(i)
                   for i in Accounts.Currency._enumerators]
_ICE_CURRENCY_DICT = {str(c): c for c in _ICE_CURRENCIES}

def string2currency(s, protocol):

    try:
        if protocol == 'ice':
            return _ICE_CURRENCY_DICT[s.upper()]
        if protocol == 'grpc':
            return exchange_rate_pb2.Currency.Value(s)

        return None

    except (KeyError, ValueError):
        raise UserInputException('unknown currency: {}'.format(s))

def currency2string(c, protocol):

    if protocol == 'ice':
        return str(c)

    if protocol == 'grpc':
        return exchange_rate_pb2.Currency.Name(c)