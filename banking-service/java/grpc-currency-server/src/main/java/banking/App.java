package banking;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;

import java.net.InetSocketAddress;
import java.util.HashMap;

public class App
{
    public static void main( String[] args ) throws Exception
    {
        String address = args[0];
        String port = args[1];

        HashMap<ExchangeRate.Currency, Double> initRates = new HashMap<ExchangeRate.Currency, Double>();
        initRates.put(ExchangeRate.Currency.PLN, 1.);
        initRates.put(ExchangeRate.Currency.USD, 3.81);
        initRates.put(ExchangeRate.Currency.CHF, 3.75);
        initRates.put(ExchangeRate.Currency.EUR, 4.27);
        initRates.put(ExchangeRate.Currency.GBP, 4.94);

        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(address, Integer.parseInt(port)))
                .addService(new ExchangeRatesProviderImpl(initRates, 5))
                .build();

        server.start();

        System.out.println(String.format("Server started at %s:%s", address, port));
        server.awaitTermination();
    }
}