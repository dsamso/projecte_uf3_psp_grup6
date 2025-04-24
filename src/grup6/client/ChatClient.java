package grup6.client;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.util.Base64;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final String ENCRYPTION_KEY = "MySecretKey12345";
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ChatClient() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Error al conectar con el servidor: " + e.getMessage());
        }
    }

    public void login(String username) throws IOException {
        this.username = username;
        out.println(username);
    }

    public void sendMessage(String message) {
        try {
            String encryptedMessage = encrypt(message);
            out.println(encryptedMessage);
        } catch (Exception e) {
            System.err.println("Error al enviar mensaje: " + e.getMessage());
        }
    }

    public void sendFile(File file) {
        try {
            new Thread(() -> {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    
                    out.println("FILE:" + file.getName() + ":" + file.length());
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        String chunk = Base64.getEncoder().encodeToString(buffer);
                        out.println(chunk);
                    }
                    
                    out.println("END_FILE");
                } catch (IOException e) {
                    System.err.println("Error al enviar archivo: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            System.err.println("Error al preparar transferencia: " + e.getMessage());
        }
    }

    public String receiveMessage() throws IOException {
        String message = in.readLine();
        if (message != null) {
            try {
                return decrypt(message);
            } catch (Exception e) {
                return message;
            }
        }
        return null;
    }

    private String encrypt(String message) throws Exception {
        SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decrypt(String encryptedMessage) throws Exception {
        SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedMessage));
        return new String(decrypted);
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar conexión: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
} 