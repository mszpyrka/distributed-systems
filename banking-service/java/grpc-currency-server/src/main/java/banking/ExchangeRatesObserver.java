package banking;

import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.locks.ReadWriteLock;

public class ExchangeRatesObserver implements Observer {

    private final ReadWriteLock ratesLock;
    private final List<ExchangeRate.Currency> subscribedCurrencies;
    private final StreamObserver<ExchangeRate.RatesUpdate> responseObserver;

    public ExchangeRatesObserver(
            ReadWriteLock ratesLock,
            List<ExchangeRate.Currency> subscribedCurrencies,
            StreamObserver<ExchangeRate.RatesUpdate> responseObserver
    ) {
        this.ratesLock = ratesLock;
        this.subscribedCurrencies = subscribedCurrencies;
        this.responseObserver = responseObserver;
    }

    public void update(Observable obj, Object arg) {

        ratesLock.readLock().lock();

        ExchangeRate.RatesUpdate.Builder update = ExchangeRate.RatesUpdate
                .newBuilder();

        Map<ExchangeRate.Currency, Double> updated = ((ExchangeRates) obj).getUpdates();

        boolean sendUpdate = false;

        for (ExchangeRate.Currency c : subscribedCurrencies) {
            if (updated.containsKey(c)) {
                sendUpdate = true;

                ExchangeRate.CurrencyValue currencyValue = ExchangeRate.CurrencyValue
                        .newBuilder()
                        .setCurrency(c)
                        .setValue(updated.get(c).floatValue())
                        .build();

                update.addRates(currencyValue);
            }
        }

        if (sendUpdate) {
            responseObserver.onNext(update.build());
        }

        ratesLock.readLock().unlock();
    }
}
