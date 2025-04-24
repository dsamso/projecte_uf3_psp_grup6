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

public class ChatServer {
    private static final int PORT = 5000;
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Set<String> connectedUsers = ConcurrentHashMap.newKeySet();
    private static boolean isRunning = true;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        System.out.println("Iniciando servidor de chat...");
        
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Servidor iniciado en el puerto " + PORT);
            System.out.println("Esperando conexiones...");
            
            startServerCommandThread();
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("\nNueva conexión desde: " + clientSocket.getInetAddress().getHostAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    pool.execute(clientHandler);
                    
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error aceptando conexión: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error iniciando el servidor: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private static void startServerCommandThread() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRunning) {
                String command = scanner.nextLine().trim();
                if (command.equalsIgnoreCase("exit")) {
                    shutdown();
                    break;
                } else if (command.equalsIgnoreCase("list")) {
                    listConnectedClients();
                } else if (command.equalsIgnoreCase("help")) {
                    showHelp();
                }
            }
            scanner.close();
        }).start();
    }

    private static void listConnectedClients() {
        System.out.println("\nClientes conectados:");
        clients.forEach((username, handler) -> 
            System.out.println("- " + username + " (" + handler.getClientAddress() + ")"));
    }

    private static void showHelp() {
        System.out.println("\nComandos disponibles:");
        System.out.println("exit  - Cerrar el servidor");
        System.out.println("list  - Mostrar clientes conectados");
        System.out.println("help  - Mostrar esta ayuda");
    }

    private static void shutdown() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            System.err.println("Error cerrando el servidor: " + e.getMessage());
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
        System.out.println("Servidor cerrado.");
    }

    public static void broadcast(String message, String sender) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String formattedMessage = "[" + timestamp + "] " + sender + ": " + message;
        
        // Enviar el mensaje solo a los usuarios que no son el remitente
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String username = entry.getKey();
            ClientHandler handler = entry.getValue();
            if (!username.equals(sender)) {
                handler.sendMessage(formattedMessage);
            }
        }
    }

    public static void addClient(String username, ClientHandler handler) {
        if (connectedUsers.contains(username)) {
            handler.sendMessage("El nombre de usuario ya está en uso. Por favor, elige otro.");
            handler.close();
            return;
        }

        clients.put(username, handler);
        connectedUsers.add(username);
        broadcast(username + " ha entrado al chat", "SERVER");
        
        // Crear la lista de usuarios
        StringBuilder userList = new StringBuilder("USER_LIST");
        for (String user : clients.keySet()) {
            userList.append("|").append(user);
        }
        String finalUserList = userList.toString();
        
        // Enviar lista de usuarios al nuevo cliente
        handler.sendMessage(finalUserList);
        
        // Notificar a todos los clientes sobre la actualización de la lista de usuarios
        clients.forEach((user, clientHandler) -> clientHandler.sendMessage(finalUserList));
    }

    public static void removeClient(String username) {
        clients.remove(username);
        connectedUsers.remove(username);
        broadcast(username + " ha salido del chat", "SERVER");
        
        // Crear la lista de usuarios
        StringBuilder userList = new StringBuilder("USER_LIST");
        for (String user : clients.keySet()) {
            userList.append("|").append(user);
        }
        String finalUserList = userList.toString();
        
        // Actualizar la lista de usuarios para todos los clientes
        clients.forEach((user, clientHandler) -> clientHandler.sendMessage(finalUserList));
    }

    public static void sendPrivateMessage(String sender, String recipient, String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        
        // Enviar el mensaje al destinatario
        ClientHandler recipientHandler = clients.get(recipient);
        if (recipientHandler != null) {
            String recipientMessage = "PRIVATE|[" + timestamp + "] " + sender + " (privado): " + message;
            recipientHandler.sendMessage(recipientMessage);
        }
        
        // Enviar una copia al remitente
        ClientHandler senderHandler = clients.get(sender);
        if (senderHandler != null) {
            String senderMessage = "PRIVATE|[" + timestamp + "] Tú -> " + recipient + " (privado): " + message;
            senderHandler.sendMessage(senderMessage);
        }
    }

    public static void sendFile(String sender, String recipient, String fileName, byte[] fileData) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        
        // Enviar el archivo al destinatario
        ClientHandler recipientHandler = clients.get(recipient);
        if (recipientHandler != null) {
            try {
                // Enviar el archivo como un mensaje especial
                String encodedData = Base64.getEncoder().encodeToString(fileData);
                String recipientMessage = "FILE_TRANSFER|" + sender + "|" + fileName + "|" + encodedData;
                recipientHandler.sendMessage(recipientMessage);
                recipientHandler.out.flush(); // Asegurar que el mensaje se envía
                
                // Notificar al destinatario
                recipientHandler.sendMessage("GENERAL|[" + timestamp + "] " + sender + " te ha enviado un archivo: " + fileName);
            } catch (Exception e) {
                System.err.println("Error enviando archivo a " + recipient + ": " + e.getMessage());
                recipientHandler.sendMessage("ERROR|Error al enviar el archivo");
            }
        }
        
        // Notificar al remitente
        ClientHandler senderHandler = clients.get(sender);
        if (senderHandler != null) {
            senderHandler.sendMessage("GENERAL|[" + timestamp + "] Has enviado un archivo a " + recipient + ": " + fileName);
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private boolean isRunning = true;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
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
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // Primero obtener el username
                String loginMessage = in.readLine();
                if (loginMessage != null && loginMessage.startsWith("LOGIN|")) {
                    username = loginMessage.split("\\|")[1];
                    addClient(username, this);
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
                                System.out.println("Mensaje desconocido: " + message);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error en el manejo del cliente " + username + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handlePrivateMessage(String[] parts) {
            if (parts.length >= 3) {
                String recipient = parts[1];
                String message = parts[2];
                
                // Guardar el mensaje en la base de datos
                DatabaseConfig.saveMessage(username, recipient, message);
                System.out.println("Mensaje guardado en la base de datos: " + username + " -> " + recipient + ": " + message);
                
                // Si el destinatario está conectado, enviar el mensaje
                ClientHandler recipientHandler = clients.get(recipient);
                if (recipientHandler != null) {
                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    String recipientMessage = "PRIVATE|[" + timestamp + "] " + username + " (privado): " + message;
                    recipientHandler.sendMessage(recipientMessage);
                    System.out.println("Mensaje enviado al destinatario conectado: " + recipient);
                } else {
                    System.out.println("Destinatario no conectado: " + recipient);
                }
                
                // Enviar confirmación al remitente
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                String senderMessage = "PRIVATE|[" + timestamp + "] Tú -> " + recipient + " (privado): " + message;
                sendMessage(senderMessage);
            }
        }

        private void handleGeneralMessage(String[] parts) {
            if (parts.length >= 2) {
                String content = parts[1];
                // Guardar el mensaje general en la base de datos
                DatabaseConfig.saveGeneralMessage(username, content);
                broadcast(content, username);
            }
        }

        private void handleFileTransfer(String[] parts) {
            if (parts.length >= 4) {
                String recipient = parts[1];
                String fileName = parts[2];
                String fileData = parts[3];
                
                try {
                    // Decodificar los datos del archivo
                    byte[] decodedData = Base64.getDecoder().decode(fileData);
                    
                    if (recipient.equals("GENERAL")) {
                        // Guardar el envío de archivo en el historial general
                        String message = "Archivo enviado al chat general: " + fileName;
                        DatabaseConfig.saveGeneralMessage(username, message);
                        
                        // Enviar el archivo a todos los clientes conectados
                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        String encodedData = Base64.getEncoder().encodeToString(decodedData);
                        String fileMessage = "FILE_TRANSFER|" + username + "|" + fileName + "|" + encodedData;
                        
                        // Enviar a todos los clientes excepto al remitente
                        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                            String user = entry.getKey();
                            ClientHandler handler = entry.getValue();
                            if (!user.equals(username)) {
                                handler.sendMessage(fileMessage);
                                handler.sendMessage("GENERAL|[" + timestamp + "] " + username + " ha enviado un archivo al chat general: " + fileName);
                            }
                        }
                        
                        // Enviar confirmación al remitente
                        sendMessage("GENERAL|[" + timestamp + "] Has enviado un archivo al chat general: " + fileName);
                    } else {
                        // Guardar el envío de archivo en el historial privado
                        String message = "Archivo enviado: " + fileName;
                        DatabaseConfig.saveMessage(username, recipient, message);
                        
                        // Enviar el archivo usando el método sendFile
                        sendFile(username, recipient, fileName, decodedData);
                    }
                    
                } catch (IllegalArgumentException e) {
                    System.err.println("Error decodificando el archivo: " + e.getMessage());
                    sendMessage("ERROR|Error al procesar el archivo");
                }
            }
        }

        private void disconnect() {
            if (username != null) {
                removeClient(username);
            }
            close();
        }
    }
} 