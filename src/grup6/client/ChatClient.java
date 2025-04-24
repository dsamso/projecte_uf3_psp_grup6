package grup6.client;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ChatClient extends Application {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final String ENCRYPTION_KEY = "MySecretKey12345";
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private ExecutorService executorService;
    private ChatWindowController chatController;
    private ObservableList<String> userList = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Cargar y mostrar el diálogo de login
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/grup6/resources/fxml/LoginDialog.fxml"));
        Parent loginRoot = loginLoader.load();
        LoginDialogController loginController = loginLoader.getController();
        
        Stage loginStage = new Stage();
        loginStage.setTitle("Iniciar Sesión");
        loginStage.setScene(new Scene(loginRoot));
        loginStage.initModality(Modality.APPLICATION_MODAL);
        loginController.setDialogStage(loginStage);
        loginStage.showAndWait();
        
        // Verificar si el usuario inició sesión
        if (loginController.getUsername() != null) {
            // Inicializar la conexión con el servidor
            initializeConnection();
            
            // Cargar la ventana principal del chat
            FXMLLoader chatLoader = new FXMLLoader(getClass().getResource("/grup6/resources/fxml/ChatWindow.fxml"));
            Parent chatRoot = chatLoader.load();
            chatController = chatLoader.getController();
            
            // Configurar el controlador
            chatController.setChatClient(this);
            chatController.setUsername(loginController.getUsername());
            
            primaryStage.setTitle("Chat App - " + loginController.getUsername());
            primaryStage.setScene(new Scene(chatRoot));
            primaryStage.setOnCloseRequest(event -> {
                disconnect();
                Platform.exit();
            });
            
            primaryStage.show();
            
            // Iniciar el hilo de escucha de mensajes
            startMessageListener();
        }
    }
    
    private void initializeConnection() throws IOException {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        executorService = Executors.newFixedThreadPool(2);
    }
    
    private void startMessageListener() {
        executorService.execute(() -> {
            try {
                while (!socket.isClosed()) {
                    String message = in.readLine();
                    if (message != null) {
                        handleServerMessage(message);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    Platform.runLater(() -> chatController.showError("Error de conexión: " + e.getMessage()));
                }
            }
        });
    }
    
    private void handleServerMessage(String message) {
        Platform.runLater(() -> {
            if (message.startsWith("USERLIST:")) {
                String[] users = message.substring(9).split(",");
                userList.clear();
                userList.addAll(users);
                chatController.updateUserList(userList);
            } else if (message.startsWith("FILE:")) {
                handleFileMessage(message);
            } else {
                try {
                    String decryptedMessage = decrypt(message);
                    chatController.appendMessage(decryptedMessage);
                } catch (Exception e) {
                    chatController.appendMessage(message);
                }
            }
        });
    }
    
    private void handleFileMessage(String message) {
        // Implementar la lógica para manejar archivos
        // Similar a la implementación anterior pero usando JavaFX
    }
    
    public void sendMessage(String message, String recipient) {
        try {
            String formattedMessage = recipient.isEmpty() ? 
                "GENERAL:" + message : 
                "PRIVATE:" + recipient + ":" + message;
            String encryptedMessage = encrypt(formattedMessage);
            out.println(encryptedMessage);
        } catch (Exception e) {
            Platform.runLater(() -> chatController.showError("Error al enviar mensaje: " + e.getMessage()));
        }
    }
    
    public void sendFile(File file, String recipient) {
        try {
            new Thread(() -> {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    
                    String header = recipient.isEmpty() ? 
                        "FILE:GENERAL:" + file.getName() + ":" + file.length() :
                        "FILE:PRIVATE:" + recipient + ":" + file.getName() + ":" + file.length();
                    out.println(header);
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        String chunk = Base64.getEncoder().encodeToString(buffer);
                        out.println(chunk);
                    }
                    
                    out.println("END_FILE");
                } catch (IOException e) {
                    Platform.runLater(() -> chatController.showError("Error al enviar archivo: " + e.getMessage()));
                }
            }).start();
        } catch (Exception e) {
            Platform.runLater(() -> chatController.showError("Error al preparar transferencia: " + e.getMessage()));
        }
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
    
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
        } catch (IOException e) {
            Platform.runLater(() -> chatController.showError("Error al cerrar conexión: " + e.getMessage()));
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
} 