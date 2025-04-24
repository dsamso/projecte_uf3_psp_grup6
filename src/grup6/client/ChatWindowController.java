package grup6.client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import java.io.File;
import java.util.Optional;

public class ChatWindowController {
    @FXML private ListView<String> userListView;
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private TextField recipientField;
    @FXML private Button sendButton;
    @FXML private Button fileButton;
    @FXML private Button clearButton;
    @FXML private Label statusLabel;
    @FXML private VBox mainContainer;

    private ChatClient chatClient;
    private String username;
    private Stage stage;
    private ObservableList<String> userList = FXCollections.observableArrayList();

    public void initialize() {
        // Configurar la lista de usuarios
        userListView.setItems(userList);
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                recipientField.setText(newVal);
                recipientField.setDisable(true);
            }
        });

        // Configurar el área de chat
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        // Configurar los botones
        sendButton.setOnAction(e -> sendMessage());
        fileButton.setOnAction(e -> sendFile());
        clearButton.setOnAction(e -> clearChat());

        // Configurar el campo de mensaje
        messageField.setOnAction(e -> sendMessage());
    }

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void updateUserList(ObservableList<String> users) {
        Platform.runLater(() -> {
            userList.clear();
            userList.addAll(users);
            userList.remove(username); // No mostrar el usuario actual en la lista
        });
    }

    public void appendMessage(String message) {
        Platform.runLater(() -> {
            chatArea.appendText(message + "\n");
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public void showError(String error) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(error);
            alert.showAndWait();
        });
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String recipient = recipientField.getText().trim();
            chatClient.sendMessage(message, recipient);
            messageField.clear();
        }
    }

    private void sendFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo");
        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null) {
            if (file.length() > 10 * 1024 * 1024) { // 10MB
                showError("El archivo es demasiado grande. Tamaño máximo: 10MB");
                return;
            }
            
            String recipient = recipientField.getText().trim();
            chatClient.sendFile(file, recipient);
        }
    }

    private void clearChat() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar");
        alert.setHeaderText(null);
        alert.setContentText("¿Estás seguro de que quieres limpiar el chat?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            chatArea.clear();
        }
    }
} 