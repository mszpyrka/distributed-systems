package banking;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.HashMap;

/**
 * Hello world!
 *
 */
public class App
{
    public static void main( String[] args ) throws Exception
    {

        HashMap<ExchangeRate.Currency, Double> initRates = new HashMap<ExchangeRate.Currency, Double>();
        initRates.put(ExchangeRate.Currency.PLN, 1.);
        initRates.put(ExchangeRate.Currency.USD, 3.81);
        initRates.put(ExchangeRate.Currency.CHF, 3.75);
        initRates.put(ExchangeRate.Currency.EUR, 4.27);
        initRates.put(ExchangeRate.Currency.GBP, 4.94);

        // Create a new server to listen on port 8080
        Server server = ServerBuilder.forPort(9990)
                .addService(new ExchangeRatesProviderImpl(initRates, 5))
                .build();

        // Start the server
        server.start();

        // Server threads are running in the background.
        System.out.println("Server started");
        // Don't exit the main thread. Wait until server is terminated.
        server.awaitTermination();
    }
}