package grup6.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Base64;

public class ChatServer {
    private static final int PORT = 5000;
    private final ExecutorService pool;
    private final Map<String, ClientHandler> clients;
    private final Set<String> connectedUsers;
    private final AtomicBoolean isRunning;
    private ServerSocket serverSocket;

    public ChatServer() {
        this.pool = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
        this.connectedUsers = ConcurrentHashMap.newKeySet();
        this.isRunning = new AtomicBoolean(true);
    }

    public void start() {
        Logger.log("Iniciando servidor de chat...");
        
        try {
            serverSocket = new ServerSocket(PORT);
            Logger.log("Servidor iniciado en el puerto " + PORT);
            Logger.log("Esperando conexiones...");
            
            startServerCommandThread();
            
            while (isRunning.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Logger.log("Nueva conexión desde: " + clientSocket.getInetAddress().getHostAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    pool.execute(clientHandler);
                    
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Logger.error("Error aceptando conexión", e);
                    }
                }
            }
        } catch (IOException e) {
            Logger.error("Error iniciando el servidor", e);
        } finally {
            shutdown();
        }
    }

    private void startServerCommandThread() {
        new Thread(() -> {
            while (isRunning.get()) {
                try {
                    int command = System.in.read();
                    if (command == 'q' || command == 'Q') {
                        shutdown();
                        break;
                    } else if (command == 'l' || command == 'L') {
                        listConnectedClients();
                    }
                } catch (IOException e) {
                    Logger.error("Error leyendo comando", e);
                }
            }
        }).start();
    }

    private void listConnectedClients() {
        Logger.log("Clientes conectados:");
        clients.forEach((username, handler) -> 
            Logger.log("- " + username + " (" + handler.getClientAddress() + ")"));
    }

    public void shutdown() {
        isRunning.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Logger.error("Error cerrando el servidor", e);
        }
        
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Logger.log("Servidor detenido");
    }

    public boolean addClient(String username, ClientHandler handler) {
        if (connectedUsers.contains(username)) {
            return false;
        }
        connectedUsers.add(username);
        clients.put(username, handler);
        broadcastUserList();
        return true;
    }

    public void removeClient(String username) {
        connectedUsers.remove(username);
        clients.remove(username);
        broadcastUserList();
    }

    public void broadcast(String message, String sender) {
        clients.forEach((username, handler) -> {
            if (!username.equals(sender)) {
                handler.sendMessage(message);
            }
        });
    }

    public void broadcastFile(String sender, String recipient, String fileName, byte[] fileData) {
        String message = "FILE:" + sender + ":" + fileName + ":" + Base64.getEncoder().encodeToString(fileData);
        if (recipient == null) {
            clients.forEach((username, handler) -> {
                if (!username.equals(sender)) {
                    handler.sendMessage(message);
                }
            });
        } else {
            ClientHandler handler = clients.get(recipient);
            if (handler != null) {
                handler.sendMessage(message);
            }
        }
    }

    public void sendPrivateMessage(String sender, String recipient, String message) {
        ClientHandler handler = clients.get(recipient);
        if (handler != null) {
            handler.sendMessage(sender + " (privado): " + message);
        }
    }

    public void sendFile(String sender, String recipient, String fileName, byte[] fileData) {
        String message = "FILE:" + sender + ":" + fileName + ":" + Base64.getEncoder().encodeToString(fileData);
        ClientHandler handler = clients.get(recipient);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }

    private void broadcastUserList() {
        String userList = "USERLIST:" + String.join(",", connectedUsers);
        clients.forEach((username, handler) -> handler.sendMessage(userList));
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private boolean isRunning = true;
        private final ChatServer server;

        public ClientHandler(Socket socket, ChatServer server) {
            this.clientSocket = socket;
            this.server = server;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                Logger.error("Error inicializando cliente", e);
            }
        }

        public String getClientAddress() {
            return clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        }

        public void sendMessage(String message) {
            if (out != null && !out.checkError()) {
                out.println(message);
                out.flush();
            }
        }

        public void close() {
            isRunning = false;
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                Logger.error("Error cerrando conexión", e);
            }
        }

        @Override
        public void run() {
            try {
                // Primero obtener el username
                String loginMessage = in.readLine();
                if (loginMessage != null && loginMessage.startsWith("LOGIN|")) {
                    username = loginMessage.split("\\|")[1];
                    if (!server.addClient(username, this)) {
                        sendMessage("ERROR|Usuario ya conectado");
                        close();
                        return;
                    }
                }
                
                // Enviar lista de usuarios al cliente
                List<String> allUsers = DatabaseConfig.getAllUsers();
                String userListMessage = "USER_LIST|" + String.join("|", allUsers);
                sendMessage(userListMessage);
                
                // Enviar mensajes no leídos
                List<String> unreadMessages = DatabaseConfig.getUnreadMessages(username);
                for (String message : unreadMessages) {
                    sendMessage("PRIVATE|" + message);
                }
                
                String message;
                while ((message = in.readLine()) != null) {
                    String[] parts = message.split("\\|");
                    if (parts.length > 0) {
                        switch (parts[0]) {
                            case "PRIVATE":
                                handlePrivateMessage(parts);
                                break;
                            case "GENERAL":
                                handleGeneralMessage(parts);
                                break;
                            case "FILE":
                                handleFileTransfer(parts);
                                break;
                            default:
                                Logger.log("Mensaje no reconocido: " + message);
                        }
                    }
                }
            } catch (IOException e) {
                Logger.error("Error en la conexión del cliente", e);
            } finally {
                if (username != null) {
                    server.removeClient(username);
                }
                close();
            }
        }

        private void handlePrivateMessage(String[] parts) {
            if (parts.length >= 3) {
                String recipient = parts[1];
                String message = parts[2];
                server.sendPrivateMessage(username, recipient, message);
                DatabaseConfig.saveMessage(username, recipient, message);
            }
        }

        private void handleGeneralMessage(String[] parts) {
            if (parts.length >= 2) {
                String message = parts[1];
                server.broadcast(username + ": " + message, username);
                DatabaseConfig.saveMessage(username, null, message);
            }
        }

        private void handleFileTransfer(String[] parts) {
            if (parts.length >= 5) {
                String type = parts[1]; // GENERAL o PRIVATE
                String recipient = parts[2];
                String fileName = parts[3];
                String fileData = parts[4];
                
                byte[] fileBytes = Base64.getDecoder().decode(fileData);
                
                if (type.equals("GENERAL")) {
                    server.broadcastFile(username, null, fileName, fileBytes);
                } else {
                    server.sendFile(username, recipient, fileName, fileBytes);
                }
            }
        }
    }
} 