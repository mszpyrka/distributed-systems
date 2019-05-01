package banking;

import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class ExchangeRatesProviderImpl extends ExchangeRatesProviderGrpc.ExchangeRatesProviderImplBase {

    private static final Logger logger = Logger.getLogger(ExchangeRatesProviderImpl.class.getName());

    private final ExchangeRates exchangeRates;
    private final ReadWriteLock exchangeRatesLock;

    private final Lock simulationLock;

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
        this.simulationLock = new ReentrantLock();

        Thread simulation = new Thread() {
            public void run() {
                simulateCurrencyStock();
            }
        };
        simulation.start();

        Thread control = new Thread() {
            public void run() {
                simulationControl();
            }
        };
        control.start();
    }

    private void simulationControl() {
        Scanner scanner = new Scanner(System.in);
        boolean paused = false;
        while(true) {
            String command = scanner.next();
            if(command.equals("p")) {
                if (paused) {
                    paused = false;
                    this.simulationLock.unlock();
                    System.out.println("simulation resumed");
                }
                else {
                    paused = true;
                    this.simulationLock.lock();
                    System.out.println("simulation paused");
                }
            }
            else {
                System.out.println("type 'p' to pause / play");
            }
        }
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

            simulationLock.lock();
            exchangeRatesLock.writeLock().lock();
            exchangeRates.update(CURRENCY_UPDATE_CHANCE, MIN_UPDATE_VALUE, MAX_UPDATE_VALUE);
            exchangeRatesLock.writeLock().unlock();
            simulationLock.unlock();
        }
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

        StringBuilder log = new StringBuilder().append(String.format(
                "new subscriber: home=%s, foreign=(",
                subscription.getHomeCurrency().toString())
        );

        for (ExchangeRate.Currency c : subscription.getForeignCurrenciesList()) {
            log.append(String.format("%s ", c.toString()));
        }
        log.append(")\n");
        logger.info(log.toString());

        ExchangeRate.RatesUpdate.Builder initialResponse = ExchangeRate.RatesUpdate
                .newBuilder();

        exchangeRatesLock.readLock().lock();

        Map<ExchangeRate.Currency, Double> rates = exchangeRates.getRates();

        ExchangeRatesObserver observer = new ExchangeRatesObserver(
                exchangeRatesLock,
                subscription.getHomeCurrency(),
                subscription.getForeignCurrenciesList(),
                responseObserver
        );

        for (ExchangeRate.Currency c : subscription.getForeignCurrenciesList()) {
            ExchangeRate.CurrencyValue currencyValue = ExchangeRate.CurrencyValue
                    .newBuilder()
                    .setCurrency(c)
                    .setValue((float) observer.convertToHomeCurrency(c, rates))
                    .build();

            initialResponse.addRates(currencyValue);
        }

        responseObserver.onNext(initialResponse.build());

        exchangeRates.addObserver(observer);
        exchangeRatesLock.readLock().unlock();
    }
}
