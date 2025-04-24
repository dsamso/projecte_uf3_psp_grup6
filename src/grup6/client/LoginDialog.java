package grup6.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class LoginDialog extends JDialog {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private boolean isRegistering = false;
    private String username = null;
    private String password = null;

    public LoginDialog(Frame parent) {
        super(parent, "Iniciar Sesión", true);
        setupUI();
    }

    private void setupUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints cs = new GridBagConstraints();
        cs.fill = GridBagConstraints.HORIZONTAL;
        cs.insets = new Insets(5, 5, 5, 5);

        // Username
        cs.gridx = 0;
        cs.gridy = 0;
        panel.add(new JLabel("Usuario: "), cs);

        cs.gridx = 1;
        cs.gridwidth = 2;
        usernameField = new JTextField(20);
        panel.add(usernameField, cs);

        // Password
        cs.gridx = 0;
        cs.gridy = 1;
        cs.gridwidth = 1;
        panel.add(new JLabel("Contraseña: "), cs);

        cs.gridx = 1;
        cs.gridwidth = 2;
        passwordField = new JPasswordField(20);
        panel.add(passwordField, cs);

        // Botones
        JPanel buttonPanel = new JPanel();
        JButton loginButton = new JButton("Iniciar Sesión");
        JButton registerButton = new JButton("Registrarse");
        JButton cancelButton = new JButton("Cancelar");
        JButton exitButton = new JButton("Salir");

        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> register());
        cancelButton.addActionListener(e -> cancel());
        exitButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(exitButton);

        // Panel principal
        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.PAGE_END);

        pack();
        setResizable(false);
        setLocationRelativeTo(getParent());

        // Cerrar con Escape
        getRootPane().registerKeyboardAction(
            e -> cancel(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void login() {
        username = usernameField.getText().trim();
        password = new String(passwordField.getPassword());
        isRegistering = false;
        dispose();
    }

    private void register() {
        username = usernameField.getText().trim();
        password = new String(passwordField.getPassword());
        isRegistering = true;
        dispose();
    }

    private void cancel() {
        username = null;
        password = null;
        dispose();
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