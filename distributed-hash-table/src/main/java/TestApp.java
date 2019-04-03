import org.jgroups.JChannel;
import org.jgroups.protocols.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class TestApp {

    public static void main(String[] args) {


        System.setProperty("java.net.preferIPv4Stack","true");

        try {
            new UDP().setValue("mcast_group_addr", InetAddress.getByName("230.225.226.228"));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }


        Scanner scanner = new Scanner(System.in);

        try (JChannel channel = new JChannel()){

            channel.connect("test");
            DistributedMap map = new DistributedMap(channel);


            String command;
            String key;
            int value;

            boolean quit = false;

            while (!quit) {
                command = scanner.next();

                switch (command) {

                    case "put":
                        key = scanner.next();
                        value = scanner.nextInt();
                        map.put(key, value);
                        break;

                    case "test":
                        key = scanner.next();
                        System.out.println(map.containsKey(key));
                        break;

                    case "get":
                        key = scanner.next();
                        System.out.println(map.get(key));
                        break;

                    case "remove":
                        key = scanner.next();
                        System.out.println(String.format("removing value %d", map.remove(key)));
                        break;

                    case "quit":
                        quit = true;
                        break;

                    case "dump":
                        map.dump();
                        break;

                    default:
                        System.out.println("invalid command: " + command);
                        break;

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
