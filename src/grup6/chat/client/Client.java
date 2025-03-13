package grup6.chat.client;

import grup6.chat.common.Message;
import grup6.chat.common.MessageType;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private static final int SERVER_PORT = 5000;
    private final String serverHost;
    private final Socket socket;
    private final ObjectOutputStream outputStream;
    private final ObjectInputStream inputStream;
    private final ExecutorService executorService;
    private final Scanner scanner;
    private String username;
    private volatile boolean running;

    public Client(String serverHost) throws IOException {
        this.serverHost = serverHost;
        this.socket = new Socket(serverHost, SERVER_PORT);
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.executorService = Executors.newSingleThreadExecutor();
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    // Constructor per compatibilitat
    public Client() throws IOException {
        this("localhost");
    }

    public void start() {
        System.out.println("Benvingut a l'Aplicació de Xat!");
        System.out.print("Introdueix el teu nom d'usuari: ");
        username = scanner.nextLine();

        // Enviar sol·licitud de connexió
        sendMessage(new Message(MessageType.CONNECT, username, ""));
        
        // Iniciar fil de recepció de missatges
        executorService.execute(this::receiveMessages);

        // Gestionar entrada de l'usuari
        while (running) {
            String messageText = scanner.nextLine();
            if (messageText.equalsIgnoreCase("/sortir")) {
                sendMessage(new Message(MessageType.DISCONNECT, username, ""));
                break;
            }
            sendMessage(new Message(MessageType.CHAT, username, messageText));
        }

        cleanup();
    }

    private void receiveMessages() {
        try {
            while (running) {
                Message message = (Message) inputStream.readObject();
                handleMessage(message);
            }
        } catch (EOFException e) {
            System.out.println("Desconnectat del servidor");
        } catch (Exception e) {
            System.err.println("Error en rebre el missatge: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case CONNECT_ACCEPTED:
                System.out.println("Connectat amb èxit!");
                break;
            case CONNECT_REJECTED:
                System.out.println("Connexió rebutjada: " + message.getContent());
                running = false;
                break;
            case CHAT:
                System.out.println(message.toString());
                break;
        }
    }

    private void sendMessage(Message message) {
        try {
            outputStream.writeObject(message);
        } catch (IOException e) {
            System.err.println("Error en enviar el missatge: " + e.getMessage());
            cleanup();
        }
    }

    private void cleanup() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error en tancar el socket: " + e.getMessage());
        }
        executorService.shutdown();
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            client.start();
        } catch (IOException e) {
            System.err.println("No s'ha pogut connectar al servidor: " + e.getMessage());
        }
    }
} 