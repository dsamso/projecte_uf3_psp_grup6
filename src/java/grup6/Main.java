package java.grup6;

import grup6.client.Client;
import grup6.server.Server;

import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Chat Application");
        System.out.println("1. Start Server");
        System.out.println("2. Start Client");
        System.out.print("Choose an option (1-2): ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        try {
            switch (choice) {
                case 1:
                    Server server = new Server();
                    server.start();
                    break;
                case 2:
                    Client client = new Client();
                    client.start();
                    break;
                default:
                    System.out.println("Invalid option");
                    break;
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}