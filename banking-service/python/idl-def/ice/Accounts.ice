module Accounts
{
    // Available currencies
    enum Currency {
        GBP = 1,
        EUR = 2,
        CHF = 3,
        USD = 4,
        PLN = 5
    };

    // Available account types
    enum AccountType {
        STANDARD = 1,
        PREMIUM = 2
    };



    exception GenericException {
        string message;
    };

    exception AuthenticationFailedException extends GenericException {};
    exception IllegalCurrencyException extends GenericException {};
    exception PeselRegisteredException extends GenericException {};
    exception InsufficientBalanceException extends GenericException {};
    exception InvalidPeselException extends GenericException {};


    dictionary<Currency, double> AccountBalance;

    struct AccountDetails {
        double declaredMonthlyIncome;
        AccountBalance balance;
    };

    // Provides costs of obtaining desired credit.
    // foreignCurrencyCosts field is not used if the credit is obtained in home currency.
    class CreditCosts {
        double homeCurrency;
        optional(1) double foreignCurrency;
    };



    interface AccountManager {
        AccountDetails getAccountDetails() throws AuthenticationFailedException;
        void transferToAccount(Currency currency, double amount) throws AuthenticationFailedException, IllegalCurrencyException;
        void withdrawFromAccount(Currency currency, double amount) throws AuthenticationFailedException, IllegalCurrencyException, InsufficientBalanceException;
    };

    interface PremiumAccountManager extends AccountManager {
        CreditCosts getCreditCosts(Currency currency, double amount, int monthsDuration) throws AuthenticationFailedException, IllegalCurrencyException;
    };



    // Provides newly created account's type, access password and manager proxy
    struct RegistrationStatus {
        AccountType type;
        string password;
        AccountManager* manager;
    };

    struct AccountAccess {
        AccountType type;
        AccountManager* manager;
    }

    interface BankManager {
        RegistrationStatus registerNewAccount(string fullName, string PESEL, double declaredMonthlyIncome) throws PeselRegisteredException;
        AccountAccess recoverAccountAccess(string PESEL) throws AuthenticationFailedException, InvalidPeselException;
    };
};