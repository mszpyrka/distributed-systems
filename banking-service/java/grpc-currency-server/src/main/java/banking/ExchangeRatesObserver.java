package banking;

import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.locks.ReadWriteLock;

public class ExchangeRatesObserver implements Observer {

    private final ReadWriteLock ratesLock;
    private final ExchangeRate.Currency homeCurrency;
    private final List<ExchangeRate.Currency> foreignCurrencies;
    private final StreamObserver<ExchangeRate.RatesUpdate> responseObserver;

    public ExchangeRatesObserver(
            ReadWriteLock ratesLock,
            ExchangeRate.Currency homeCurrency,
            List<ExchangeRate.Currency> subscribedCurrencies,
            StreamObserver<ExchangeRate.RatesUpdate> responseObserver
    ) {
        this.ratesLock = ratesLock;
        this.homeCurrency = homeCurrency;
        this.foreignCurrencies = subscribedCurrencies;
        this.responseObserver = responseObserver;
    }

    public void update(Observable obj, Object arg) {

        ratesLock.readLock().lock();

        ExchangeRate.RatesUpdate.Builder update = ExchangeRate.RatesUpdate
                .newBuilder();

        Map<ExchangeRate.Currency, Double> rates = ((ExchangeRates) obj).getRates();
        Map<ExchangeRate.Currency, Double> updated = ((ExchangeRates) obj).getUpdates();

        boolean sendUpdate = false;
        boolean homeUpdated = updated.containsKey(homeCurrency);

        for (ExchangeRate.Currency c : foreignCurrencies) {
            if (homeUpdated || updated.containsKey(c)) {
                sendUpdate = true;

                ExchangeRate.CurrencyValue currencyValue = ExchangeRate.CurrencyValue
                        .newBuilder()
                        .setCurrency(c)
                        .setValue((float) convertToHomeCurrency(c, rates))
                        .build();

                update.addRates(currencyValue);
            }
        }

        if (sendUpdate) {
            responseObserver.onNext(update.build());
        }

        ratesLock.readLock().unlock();
    }

    public double convertToHomeCurrency(
            ExchangeRate.Currency currency,
            Map<ExchangeRate.Currency, Double> rates
    ) {
        return rates.get(currency) / rates.get(homeCurrency);
    }
}
