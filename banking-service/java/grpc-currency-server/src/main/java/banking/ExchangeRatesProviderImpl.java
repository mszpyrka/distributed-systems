package banking;

import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class ExchangeRatesProviderImpl extends ExchangeRatesProviderGrpc.ExchangeRatesProviderImplBase {

    private static final Logger logger = Logger.getLogger(CurrencyServer.class.getName());

    private final ExchangeRates exchangeRates;
    private final ReadWriteLock exchangeRatesLock;

    private final int currencyUpdateInterval;

    private final double CURRENCY_UPDATE_CHANCE = 0.3;
    private final double MIN_UPDATE_VALUE = 0.02;
    private final double MAX_UPDATE_VALUE = 0.05;

    /**
     * Initializes exchange rates map with given values.
     * Starts new thread that simulates currencies exchange rates
     * fluctuations by randomly changing the values every given interval.
     */
    public ExchangeRatesProviderImpl(
            Map<ExchangeRate.Currency, Double> initialRates,
            int currencyUpdateInterval
    ) {
        this.currencyUpdateInterval = currencyUpdateInterval;
        this.exchangeRates = new ExchangeRates(initialRates);
        this.exchangeRatesLock = new ReentrantReadWriteLock();

        Thread t = new Thread() {
            public void run() {
                simulateCurrencyStock();
            }
        };
        t.start();
    }

    /**
     * Makes random updates to exchangeRates map every few seconds.
     */
    private void simulateCurrencyStock() {

        while(true) {
            try {
                Thread.sleep(currencyUpdateInterval * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            exchangeRatesLock.writeLock().lock();
            exchangeRates.update(CURRENCY_UPDATE_CHANCE, MIN_UPDATE_VALUE, MAX_UPDATE_VALUE);
            exchangeRatesLock.writeLock().unlock();
        }
    }

    @Override
    public void hello(ExchangeRate.Hello hello, StreamObserver<ExchangeRate.Hello> responseObserver) {

        ExchangeRate.Hello response = ExchangeRate.Hello
                .newBuilder()
                .setHello("hello")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * This service provides full exchange rates data at the moment is is called,
     * and after that sends updates every time the rates are changed.
     */
    @Override
    public void subscribe(
            ExchangeRate.Subscription subscription,
            StreamObserver<ExchangeRate.RatesUpdate> responseObserver
    ) {

        StringBuilder log = new StringBuilder().append("new subscriber: ");

        for (ExchangeRate.Currency c : subscription.getCurrenciesList()) {
            log.append(String.format("%s ", c.toString()));
        }
        log.append('\n');
        logger.info(log.toString());

        ExchangeRate.RatesUpdate.Builder initialResponse = ExchangeRate.RatesUpdate
                .newBuilder();

        exchangeRatesLock.readLock().lock();

        Map<ExchangeRate.Currency, Double> rates = exchangeRates.getRates();

        for (ExchangeRate.Currency c : subscription.getCurrenciesList()) {
            ExchangeRate.CurrencyValue currencyValue = ExchangeRate.CurrencyValue
                    .newBuilder()
                    .setCurrency(c)
                    .setValue(rates.get(c).floatValue())
                    .build();

            initialResponse.addRates(currencyValue);
        }

        responseObserver.onNext(initialResponse.build());

        ExchangeRatesObserver observer = new ExchangeRatesObserver(
                exchangeRatesLock,
                subscription.getCurrenciesList(),
                responseObserver
        );

        exchangeRates.addObserver(observer);
        exchangeRatesLock.readLock().unlock();
    }
}
