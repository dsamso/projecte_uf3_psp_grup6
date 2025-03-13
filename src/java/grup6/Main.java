package java.grup6;

import java.grup6.server.Server;
import java.grup6.client.Client;

public class Main {
    public static void main(String[] args) {
        new Thread(Server::startServer).start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        new Thread(Client::new).start();
    }
}
