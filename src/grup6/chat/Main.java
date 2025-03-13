package grup6.chat;

import grup6.chat.server.Server;
import grup6.chat.client.Client;

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
