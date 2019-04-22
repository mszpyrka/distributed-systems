package banking;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Random;
import java.util.logging.Logger;

public class ExchangeRates extends Observable {

    private static final Logger logger = Logger.getLogger(CurrencyServer.class.getName());

    private final Map<ExchangeRate.Currency, Double> exchangeRates;
    private final Map<ExchangeRate.Currency, Boolean> wasUpdated;
    private final Map<ExchangeRate.Currency, Double> updateValue;
    private final Random rand;

    public ExchangeRates(Map<ExchangeRate.Currency, Double> initialRates) {

        exchangeRates = new HashMap<ExchangeRate.Currency, Double>();
        updateValue = new HashMap<ExchangeRate.Currency, Double>();
        exchangeRates.putAll(initialRates);

        wasUpdated = new HashMap<ExchangeRate.Currency, Boolean>();
        for (ExchangeRate.Currency c : initialRates.keySet()) {
            wasUpdated.put(c, false);
        }

        rand = new Random();
    }

    public Map<ExchangeRate.Currency, Double> getRates() {
        return exchangeRates;
    }

    /**
     * Returns map of all currencies with their values
     * that were changed during previous update.
     */
    public Map<ExchangeRate.Currency, Double> getUpdates() {
        Map<ExchangeRate.Currency, Double> updates = new HashMap<ExchangeRate.Currency, Double>();

        for (ExchangeRate.Currency c : exchangeRates.keySet()) {
            if (wasUpdated.get(c)) {
                updates.put(c, exchangeRates.get(c));
            }
        }

        return updates;
    }

    public void update(double updateChance, double minChange, double maxChange) {

        boolean changed = false;
        for (ExchangeRate.Currency c : wasUpdated.keySet()) {
            wasUpdated.put(c, false);
        }

        for (ExchangeRate.Currency c : exchangeRates.keySet()) {
            double draw = rand.nextDouble();

            if (draw < updateChance) {
                changed = true;
                double update = rand.nextDouble() * (maxChange - minChange) + minChange;

                // randomly draws update's sign
                if (rand.nextDouble() < 0.5)
                    update *= -1;

                exchangeRates.put(c, exchangeRates.get(c) + update);
                wasUpdated.put(c, true);
                updateValue.put(c, update);
            }
        }

        if (changed) {

            StringBuilder log = new StringBuilder().append("exchange rates updated:\n");
            for (ExchangeRate.Currency c : exchangeRates.keySet()) {
                log.append(String.format("%s: %.5f", c.toString(), exchangeRates.get(c)));

                if (wasUpdated.get(c)) {
                    log.append(String.format(" (%+.5f)", updateValue.get(c)));
                }

                log.append('\n');
            }

            logger.info(log.toString());
            setChanged();
            notifyObservers();
        }
    }
}
