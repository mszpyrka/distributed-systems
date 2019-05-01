package banking;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

public class CurrencyServer {
    private static final Logger logger = Logger.getLogger(CurrencyServer.class.getName());

    private final int port;
    private final Server server;

    /**
     * Create a RouteGuide server using serverBuilder as a base and features as data.
     */
    public CurrencyServer(int port) {
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);
        this.port = port;

        HashMap<ExchangeRate.Currency, Double> initRates = new HashMap<ExchangeRate.Currency, Double>();
        initRates.put(ExchangeRate.Currency.PLN, 1.);
        initRates.put(ExchangeRate.Currency.USD, 3.81);
        initRates.put(ExchangeRate.Currency.CHF, 3.75);
        initRates.put(ExchangeRate.Currency.EUR, 4.27);
        initRates.put(ExchangeRate.Currency.GBP, 4.94);

        server = serverBuilder.addService(new ExchangeRatesProviderImpl(initRates, 3))
                .build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                CurrencyServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main method.  This comment makes the linter happy.

    public static void main(String[] args) throws Exception {
        CurrencyServer server = new CurrencyServer(9990);
        server.start();
        server.blockUntilShutdown();
    }
     */
}