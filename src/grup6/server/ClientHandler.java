package grup6.server;

import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private String username;
    private final AtomicBoolean isRunning;
    private final ChatServer server;

    public ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.isRunning = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        try {
            handleClient();
        } catch (IOException e) {
            Logger.error("Error en la conexión con el cliente", e);
        } finally {
            disconnect();
        }
    }

    private void handleClient() throws IOException {
        // Esperar login
        String loginMessage = in.readLine();
        if (loginMessage == null || !loginMessage.startsWith("LOGIN:")) {
            sendMessage("ERROR: Formato de login inválido");
            return;
        }

        username = loginMessage.substring(6);
        if (!server.addClient(username, this)) {
            sendMessage("ERROR: Usuario ya conectado");
            return;
        }

        sendMessage("OK: Conectado como " + username);
        Logger.log("Cliente conectado: " + username);

        // Manejar mensajes
        String message;
        while (isRunning.get() && (message = in.readLine()) != null) {
            handleMessage(message);
        }
    }

    private void handleMessage(String message) {
        try {
            if (message.startsWith("MESSAGE:")) {
                handleGeneralMessage(message.substring(8));
            } else if (message.startsWith("PRIVATE:")) {
                handlePrivateMessage(message.substring(8));
            } else if (message.startsWith("FILE:")) {
                handleFileMessage(message.substring(5));
            } else if (message.startsWith("PRIVATE_FILE:")) {
                handlePrivateFileMessage(message.substring(13));
            } else if (message.equals("LOGOUT")) {
                disconnect();
            } else {
                sendMessage("ERROR: Comando no reconocido");
            }
        } catch (Exception e) {
            Logger.error("Error procesando mensaje", e);
            sendMessage("ERROR: Error procesando mensaje");
        }
    }

    private void handleGeneralMessage(String message) {
        String encryptedMessage = CryptoUtils.encrypt(message);
        MessageManager.saveMessage(username, null, encryptedMessage);
        server.broadcast(username + ": " + message, username);
    }

    private void handlePrivateMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            sendMessage("ERROR: Formato de mensaje privado inválido");
            return;
        }

        String recipient = parts[0];
        String content = parts[1];
        String encryptedMessage = CryptoUtils.encrypt(content);
        MessageManager.saveMessage(username, recipient, encryptedMessage);
        server.sendPrivateMessage(username, recipient, content);
    }

    private void handleFileMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            sendMessage("ERROR: Formato de archivo inválido");
            return;
        }

        String fileName = parts[0];
        byte[] fileData = Base64.getDecoder().decode(parts[1]);
        server.broadcastFile(username, null, fileName, fileData);
    }

    private void handlePrivateFileMessage(String message) {
        String[] parts = message.split(":", 3);
        if (parts.length != 3) {
            sendMessage("ERROR: Formato de archivo privado inválido");
            return;
        }

        String recipient = parts[0];
        String fileName = parts[1];
        byte[] fileData = Base64.getDecoder().decode(parts[2]);
        server.sendFile(username, recipient, fileName, fileData);
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public void disconnect() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                if (username != null) {
                    server.removeClient(username);
                    Logger.log("Cliente desconectado: " + username);
                }
                socket.close();
            } catch (IOException e) {
                Logger.error("Error cerrando conexión", e);
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public String getClientAddress() {
        return socket.getInetAddress().getHostAddress();
    }
}

