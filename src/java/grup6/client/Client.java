package grup6.client;

import grup6.common.Message;
import grup6.common.MessageType;
import grup6.security.EncryptionUtil;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private final Socket socket;
    private final ObjectOutputStream outputStream;
    private final ObjectInputStream inputStream;
    private final ExecutorService executorService;
    private final Scanner scanner;
    private String username;
    private volatile boolean running;

    public Client() throws IOException {
        this.socket = new Socket(SERVER_HOST, SERVER_PORT);
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.executorService = Executors.newSingleThreadExecutor();
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    public void start() {
        System.out.println("Welcome to the Chat Application!");
        System.out.print("Enter your username: ");
        username = scanner.nextLine();

        // Send connection request
        sendMessage(new Message(MessageType.CONNECT, username, ""));
        
        // Start message receiver thread
        executorService.execute(this::receiveMessages);

        // Handle user input
        while (running) {
            String messageText = scanner.nextLine();
            if (messageText.equalsIgnoreCase("/quit")) {
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
            System.out.println("Disconnected from server");
        } catch (Exception e) {
            System.err.println("Error receiving message: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case CONNECT_ACCEPTED:
                System.out.println("Connected successfully!");
                break;
            case CONNECT_REJECTED:
                System.out.println("Connection rejected: " + message.getContent());
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
            System.err.println("Error sending message: " + e.getMessage());
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
            System.err.println("Error closing socket: " + e.getMessage());
        }
        executorService.shutdown();
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            client.start();
        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }
} 