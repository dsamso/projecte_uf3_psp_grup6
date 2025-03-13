package grup6.chat.client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public Client() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connectat al servidor. Escriu un missatge:");

            Thread readerThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println("Missatge rebut: " + message);
                    }
                } catch (IOException e) {
                    System.out.println("Connexió tancada.");
                }
            });
            readerThread.start();

            while (true) {
                String message = scanner.nextLine();
                if (message.equalsIgnoreCase("exit")) break;
                out.println(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
