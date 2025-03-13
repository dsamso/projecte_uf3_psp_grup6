package grup6.server;

import grup6.common.Message;
import grup6.common.MessageType;
import grup6.security.EncryptionUtil;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Server server;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private String username;
    private final AtomicBoolean running;

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.clientSocket = socket;
        this.server = server;
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.running = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                Message message = (Message) inputStream.readObject();
                handleMessage(message);
            }
        } catch (EOFException e) {
            System.out.println("Client disconnected: " + username);
        } catch (Exception e) {
            System.err.println("Error handling client message: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case CONNECT:
                handleConnect(message);
                break;
            case CHAT:
                handleChat(message);
                break;
            case DISCONNECT:
                handleDisconnect();
                break;
        }
    }

    private void handleConnect(Message message) {
        String requestedUsername = message.getSender();
        if (!server.isUsernameTaken(requestedUsername)) {
            this.username = requestedUsername;
            server.addClient(username, this);
            sendMessage(new Message(MessageType.CONNECT_ACCEPTED, "Server", username));
        } else {
            sendMessage(new Message(MessageType.CONNECT_REJECTED, "Server", "Username already taken"));
            cleanup();
        }
    }

    private void handleChat(Message message) {
        server.broadcastMessage(message, username);
    }

    private void handleDisconnect() {
        if (username != null) {
            server.removeClient(username);
        }
        cleanup();
    }

    public void sendMessage(Message message) {
        try {
            outputStream.writeObject(message);
        } catch (IOException e) {
            System.err.println("Error sending message to client: " + e.getMessage());
            cleanup();
        }
    }

    private void cleanup() {
        running.set(false);
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }
} 