package grup6.server;

import grup6.common.Message;
import grup6.common.User;
import grup6.security.EncryptionUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 5000;
    private final ConcurrentHashMap<String, ClientHandler> connectedClients;
    private final ExecutorService executorService;
    private final ServerSocket serverSocket;
    private final DataPersistence dataPersistence;

    public Server() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
        this.connectedClients = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.dataPersistence = new DataPersistence();
    }

    public void start() {
        System.out.println("Server started on port " + PORT);
        
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                executorService.execute(clientHandler);
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    public void broadcastMessage(Message message, String senderUsername) {
        connectedClients.forEach((username, handler) -> {
            if (!username.equals(senderUsername)) {
                handler.sendMessage(message);
            }
        });
    }

    public void addClient(String username, ClientHandler handler) {
        connectedClients.put(username, handler);
        System.out.println("Client connected: " + username);
    }

    public void removeClient(String username) {
        connectedClients.remove(username);
        System.out.println("Client disconnected: " + username);
    }

    public boolean isUsernameTaken(String username) {
        return connectedClients.containsKey(username);
    }

    public DataPersistence getDataPersistence() {
        return dataPersistence;
    }

    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.start();
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }
} 