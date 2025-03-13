package grup6.chat;

import grup6.chat.client.Client;
import grup6.chat.server.Server;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class Main {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Aplicació de Xat");
        System.out.println("1. Iniciar Servidor");
        System.out.println("2. Iniciar Client");
        System.out.println("3. Iniciar Client (connexió remota)");
        System.out.print("Tria una opció (1-3): ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        try {
            switch (choice) {
                case 1:
                    startServer();
                    break;
                case 2:
                    startClient(SERVER_HOST);
                    break;
                case 3:
                    System.out.print("Introdueix l'adreça IP del servidor: ");
                    String serverIP = scanner.nextLine();
                    startClient(serverIP);
                    break;
                default:
                    System.out.println("Opció no vàlida");
                    break;
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void startServer() throws IOException {
        // Comprovar si el servidor ja està en execució
        if (isServerRunning()) {
            System.out.println("El servidor ja està en execució!");
            return;
        }

        Server server = new Server();
        System.out.println("Servidor iniciat al port " + Server.PORT);
        System.out.println("Adreça IP del servidor: " + java.net.InetAddress.getLocalHost().getHostAddress());
        server.start();
    }

    private static void startClient(String serverHost) throws IOException {
        // Comprovar si el servidor està disponible
        if (!isServerRunning()) {
            System.out.println("El servidor no està en execució. Vols iniciar-lo? (s/n)");
            Scanner scanner = new Scanner(System.in);
            if (scanner.nextLine().toLowerCase().startsWith("s")) {
                startServer();
            } else {
                System.out.println("No es pot connectar sense un servidor en execució.");
                return;
            }
        }

        Client client = new Client(serverHost);
        client.start();
    }

    private static boolean isServerRunning() {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}