package grup6.client;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class LoginDialogController {
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    private boolean isRegistering = false;
    private String username = null;
    private String password = null;
    private Stage dialogStage;
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
    
    @FXML
    private void handleLogin() {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        isRegistering = false;
        dialogStage.close();
    }
    
    @FXML
    private void handleRegister() {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        isRegistering = true;
        dialogStage.close();
    }
    
    @FXML
    private void handleCancel() {
        username = null;
        password = null;
        dialogStage.close();
    }
    
    @FXML
    private void handleExit() {
        System.exit(0);
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public boolean isRegistering() {
        return isRegistering;
    }
} 