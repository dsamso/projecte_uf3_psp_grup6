package grup6.client;

import grup6.server.DatabaseConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatWindow extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ExecutorService executorService;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private JTextArea chatArea;
    private JTextField messageField;
    private JTextField recipientField;
    private JButton sendButton;
    private JButton fileButton;
    private JButton backButton;
    private JButton deselectButton;
    private JButton clearChatButton;
    private JLabel statusLabel;
    private JLabel recipientLabel;
    private boolean isRunning = true;
    private File currentDirectory;
    private File photosDirectory;
    private final AtomicBoolean isRunningAtomic = new AtomicBoolean(true);
    private Map<String, byte[]> receivedFiles = new HashMap<>(); // Almacena los archivos recibidos

    public ChatWindow(String username) {
        super("Chat App - " + username);
        this.username = username;
        this.executorService = Executors.newFixedThreadPool(2);
        
        // Crear la carpeta files en el directorio del proyecto
        currentDirectory = new File("files");
        if (!currentDirectory.exists()) {
            currentDirectory.mkdirs();
            System.out.println("Carpeta de archivos creada en: " + currentDirectory.getAbsolutePath());
        }

        // Crear la carpeta fotos dentro de files
        photosDirectory = new File(currentDirectory, "fotos");
        if (!photosDirectory.exists()) {
            photosDirectory.mkdirs();
            System.out.println("Carpeta de fotos creada en: " + photosDirectory.getAbsolutePath());
        }

        // Inicializar componentes
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        statusLabel = new JLabel("Conectando...");
        chatArea = new JTextArea();
        messageField = new JTextField();
        recipientField = new JTextField(15);
        sendButton = new JButton("Enviar");
        fileButton = new JButton("Archivo");
        backButton = new JButton("Volver");
        deselectButton = new JButton("Deseleccionar Usuario");
        clearChatButton = new JButton("Limpiar Chat");
        recipientLabel = new JLabel("Para:");

        setupWindow();
        setupComponents();
        setupEvents();
        connectToServer();
    }

    private void setupWindow() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(600, 400));
    }

    private void setupComponents() {
        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        // Panel izquierdo - Lista de usuarios
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(150, 0));
        leftPanel.setBorder(new TitledBorder("Usuarios"));
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedUser = userList.getSelectedValue();
                if (selectedUser != null) {
                    recipientField.setText(selectedUser);
                    recipientField.setEnabled(false);
                    deselectButton.setEnabled(true);
                    recipientLabel.setText("Chat con: " + selectedUser);
                    showPrivateHistory(selectedUser);
                } else {
                    recipientLabel.setText("Chat general");
                    showGeneralHistory();
                }
            }
        });
        JScrollPane userScroll = new JScrollPane(userList);
        leftPanel.add(userScroll, BorderLayout.CENTER);

        // Panel de botones izquierdo
        JPanel leftButtonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        deselectButton.setEnabled(false);
        styleButton(deselectButton);
        styleButton(clearChatButton);
        leftButtonPanel.add(deselectButton);
        leftButtonPanel.add(clearChatButton);
        leftPanel.add(leftButtonPanel, BorderLayout.SOUTH);

        // Panel central - Chat
        JPanel centerPanel = new JPanel(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    int offset = chatArea.viewToModel(e.getPoint());
                    String text = chatArea.getText();
                    int start = text.lastIndexOf("[CLICK PARA ABRIR] ", offset);
                    if (start != -1) {
                        int end = text.indexOf("\n", start);
                        if (end == -1) end = text.length();
                        String fileLine = text.substring(start, end);
                        if (fileLine.contains("[CLICK PARA ABRIR] ")) {
                            String fileName = fileLine.substring(fileLine.indexOf("[CLICK PARA ABRIR] ") + 19); // Remover "[CLICK PARA ABRIR] "
                            if (receivedFiles.containsKey(fileName)) {
                                openFile(fileName);
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(new TitledBorder("Chat"));
        centerPanel.add(chatScroll, BorderLayout.CENTER);

        // Panel inferior - Campo de mensaje y botones
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        recipientField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        bottomPanel.add(recipientField, BorderLayout.NORTH);
        bottomPanel.add(messageField, BorderLayout.CENTER);

        // Panel de botones
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        styleButton(backButton);
        styleButton(sendButton);
        styleButton(fileButton);
        
        buttonPanel.add(backButton);
        buttonPanel.add(fileButton);
        buttonPanel.add(sendButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        // Añadir etiqueta para mostrar el destinatario seleccionado
        recipientLabel.setBorder(new EmptyBorder(0, 5, 0, 5));
        bottomPanel.add(recipientLabel, BorderLayout.NORTH);

        centerPanel.add(bottomPanel, BorderLayout.SOUTH);
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Barra de estado
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Deshabilitar componentes hasta que la conexión esté lista
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        fileButton.setEnabled(false);
        backButton.setEnabled(true);
    }

    private void setupEvents() {
        messageField.addActionListener(e -> sendMessage());
        sendButton.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> sendFile());
        backButton.addActionListener(e -> goBack());
        deselectButton.addActionListener(e -> {
            userList.clearSelection();
            recipientField.setText("");
            recipientField.setEnabled(true);
            deselectButton.setEnabled(false);
            recipientLabel.setText("Chat general");
        });
        clearChatButton.addActionListener(e -> clearChat());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    private void styleButton(JButton button) {
        button.setBackground(new Color(70, 130, 180));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBorder(new EmptyBorder(8, 15, 8, 15));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(100, 149, 237));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(70, 130, 180));
            }
        });
    }

    private void goBack() {
        // Cerrar la conexión antes de cerrar la ventana
        disconnect();
        
        // Cerrar la ventana actual
        this.dispose();
        
        // Crear y mostrar la ventana de inicio de sesión
        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);
            
            // Manejar el resultado del login
            String newUsername = loginDialog.getUsername();
            String newPassword = loginDialog.getPassword();
            
            if (newUsername != null && newPassword != null) {
                if (DatabaseConfig.validateUser(newUsername, newPassword)) {
                    new ChatWindow(newUsername).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(null,
                        "Credenciales incorrectas",
                        "Error de inicio de sesión",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Enviar nombre de usuario al servidor
            out.println("LOGIN|" + username);

            final boolean[] running = {true};
            executorService.execute(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        final String finalMessage = message;
                        SwingUtilities.invokeLater(() -> {
                            String[] parts = finalMessage.split("\\|", 2);
                            if (parts.length < 2) {
                                chatArea.append(finalMessage + "\n");
                                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                                return;
                            }
                            
                            String messageType = parts[0];
                            String content = parts[1];
                            
                            switch (messageType) {
                                case "USER_LIST":
                                    // Actualizar lista de usuarios
                                    userListModel.clear();
                                    String[] users = content.split("\\|");
                                    for (String user : users) {
                                        if (!user.equals(username)) {
                                            userListModel.addElement(user);
                                        }
                                    }
                                    break;
                                case "PRIVATE":
                                    // Mostrar mensaje privado
                                    chatArea.append(content + "\n");
                                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                                    break;
                                case "GENERAL":
                                    // Mostrar mensaje general
                                    chatArea.append(content + "\n");
                                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                                    break;
                                case "FILE":
                                case "FILE_TRANSFER":
                                    // Manejar archivo recibido
                                    String[] fileParts = content.split("\\|");
                                    if (fileParts.length == 4) {
                                        String sender = fileParts[1];
                                        String fileName = fileParts[2];
                                        String fileData = fileParts[3];
                                        
                                        try {
                                            System.out.println("Recibiendo archivo: " + fileName);
                                            byte[] decodedData = Base64.getDecoder().decode(fileData);
                                            System.out.println("Archivo decodificado, tamaño: " + decodedData.length + " bytes");
                                            
                                            // Guardar el archivo en la carpeta fotos
                                            File file = new File(photosDirectory, fileName);
                                            System.out.println("Intentando guardar archivo en: " + file.getAbsolutePath());
                                            if (!file.getParentFile().exists()) {
                                                file.getParentFile().mkdirs();
                                                System.out.println("Carpeta padre creada");
                                            }
                                            
                                            // Guardar el archivo
                                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                                fos.write(decodedData);
                                                fos.flush();
                                                System.out.println("Archivo guardado correctamente");
                                            } catch (IOException e) {
                                                System.err.println("Error al guardar el archivo: " + e.getMessage());
                                                throw new RuntimeException(e);
                                            }

                                            // Mostrar mensaje en el chat con enlace clickeable
                                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                            chatArea.append("[" + timestamp + "] " + sender + " ha enviado una imagen: [CLICK PARA ABRIR] " + fileName + "\n");
                                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                                            
                                            // Guardar en memoria para acceso rápido
                                            receivedFiles.put(fileName, decodedData);
                                            System.out.println("Archivo guardado en memoria");
                                            
                                        } catch (IllegalArgumentException e) {
                                            System.err.println("Error al procesar la imagen: " + e.getMessage());
                                            JOptionPane.showMessageDialog(this,
                                                "Error al procesar la imagen: " + e.getMessage(),
                                                "Error",
                                                JOptionPane.ERROR_MESSAGE);
                                        }
                                    }
                                    break;
                                default:
                                    // Mensajes del sistema
                                    chatArea.append(content + "\n");
                                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                            }
                        });
                    }
                } catch (IOException e) {
                    if (running[0] && !e.getMessage().equals("Socket closed")) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, 
                                "Error de conexión: " + e.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                            disconnect();
                        });
                    }
                }
            });

            isRunning = true;
            statusLabel.setText("Conectado como: " + username);
            
            // Habilitar componentes cuando la conexión está lista
            SwingUtilities.invokeLater(() -> {
                messageField.setEnabled(true);
                sendButton.setEnabled(true);
                fileButton.setEnabled(true);
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "Error al conectar: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                disconnect();
            });
        }
    }

    private void disconnect() {
        try {
            isRunning = false;  // Marcar que estamos cerrando intencionalmente
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            statusLabel.setText("Desconectado");
            
            // Deshabilitar componentes cuando se desconecta
            SwingUtilities.invokeLater(() -> {
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                fileButton.setEnabled(false);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        if (!isRunning || out == null) {
            JOptionPane.showMessageDialog(this,
                "No hay conexión con el servidor",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String selectedUser = userList.getSelectedValue();
            String messageToSend;
            
            if (selectedUser != null && !selectedUser.equals(username)) {
                // Mensaje privado
                messageToSend = "PRIVATE|" + selectedUser + "|" + message;
            } else {
                // Mensaje general
                messageToSend = "GENERAL|" + message;
            }
            
            out.println(messageToSend);
            messageField.setText("");
        }
    }

    private void sendFile() {
        if (!isRunning || out == null) {
            JOptionPane.showMessageDialog(this,
                "No hay conexión con el servidor",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selectedUser = userList.getSelectedValue();
        String messageToSend;
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Seleccionar imagen para enviar");
        
        // Filtrar solo archivos de imagen
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
            }
            public String getDescription() {
                return "Imágenes (*.jpg, *.jpeg, *.png)";
            }
        });
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            System.out.println("Archivo seleccionado: " + file.getAbsolutePath());
            
            // Verificar la extensión del archivo
            String fileName = file.getName().toLowerCase();
            if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg") && !fileName.endsWith(".png")) {
                JOptionPane.showMessageDialog(this,
                    "Solo se permiten archivos de imagen en formato JPG, JPEG o PNG.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (file.length() > MAX_FILE_SIZE) {
                JOptionPane.showMessageDialog(this, 
                    "La imagen excede el tamaño máximo permitido (" + MAX_FILE_SIZE + " bytes).",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            try {
                // Leer el archivo
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                System.out.println("Archivo leído, tamaño: " + fileBytes.length + " bytes");
                
                // Guardar una copia en la carpeta fotos
                File savedFile = new File(photosDirectory, fileName);
                try (FileOutputStream fos = new FileOutputStream(savedFile)) {
                    fos.write(fileBytes);
                    fos.flush();
                    System.out.println("Archivo guardado en: " + savedFile.getAbsolutePath());
                }
                
                // Codificar el archivo para enviar
                String encodedData = Base64.getEncoder().encodeToString(fileBytes);
                System.out.println("Archivo codificado en Base64");
                
                if (selectedUser != null && !selectedUser.equals(username)) {
                    // Enviar imagen privada
                    messageToSend = "FILE|" + selectedUser + "|" + fileName + "|" + encodedData;
                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    chatArea.append("[" + timestamp + "] Enviando imagen a " + selectedUser + ": [CLICK PARA ABRIR] " + fileName + "\n");
                    System.out.println("Enviando imagen privada a: " + selectedUser);
                } else {
                    // Enviar imagen al chat general
                    messageToSend = "FILE|GENERAL|" + fileName + "|" + encodedData;
                    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    chatArea.append("[" + timestamp + "] Enviando imagen al chat general: [CLICK PARA ABRIR] " + fileName + "\n");
                    System.out.println("Enviando imagen al chat general");
                }
                
                out.println(messageToSend);
                out.flush();
                System.out.println("Mensaje enviado al servidor");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            } catch (IOException e) {
                System.err.println("Error al leer la imagen: " + e.getMessage());
                JOptionPane.showMessageDialog(this, 
                    "Error al leer la imagen: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile(String fileName, byte[] fileData) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar archivo");
        fileChooser.setSelectedFile(new File(fileName));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                fos.write(fileData);
                fos.flush(); // Asegurar que los datos se escriben
                JOptionPane.showMessageDialog(this,
                    "Archivo guardado correctamente en: " + file.getAbsolutePath(),
                    "Éxito",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error al guardar el archivo: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        System.err.println("Error al cerrar el archivo: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void showPrivateHistory(String otherUser) {
        // Limpiar el área de chat
        chatArea.setText("");
        
        // Obtener el historial de mensajes privados
        List<String> messages = DatabaseConfig.getMessageHistory(username);
        
        // Filtrar solo los mensajes entre estos dos usuarios
        for (String message : messages) {
            if (message.contains(" -> " + otherUser) || message.contains(otherUser + " -> ")) {
                chatArea.append(message + "\n");
            }
        }
        
        // Mover el cursor al final
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void showGeneralHistory() {
        // Limpiar el área de chat
        chatArea.setText("");
        
        // Obtener el historial de mensajes generales
        List<String> messages = DatabaseConfig.getMessageHistory(username);
        
        // Mostrar solo los mensajes generales
        for (String message : messages) {
            if (!message.contains(" -> ")) {
                chatArea.append(message + "\n");
            }
        }
        
        // Mover el cursor al final
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void openFile(String fileName) {
        try {
            System.out.println("Intentando abrir archivo: " + fileName);
            
            // Verificar si el archivo ya existe en la carpeta fotos
            File file = new File(photosDirectory, fileName);
            System.out.println("Ruta del archivo: " + file.getAbsolutePath());
            
            if (!file.exists()) {
                System.out.println("Archivo no encontrado en la carpeta fotos");
                byte[] fileData = receivedFiles.get(fileName);
                if (fileData != null) {
                    System.out.println("Archivo encontrado en memoria, guardando...");
                    try {
                        if (!file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                            System.out.println("Carpeta padre creada");
                        }
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(fileData);
                            fos.flush();
                            System.out.println("Archivo guardado correctamente");
                        }
                    } catch (FileNotFoundException e) {
                        System.err.println("Error al guardar el archivo: " + e.getMessage());
                        JOptionPane.showMessageDialog(this,
                            "Error al guardar el archivo: No se pudo crear el archivo",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    System.out.println("Archivo no encontrado en memoria");
                    JOptionPane.showMessageDialog(this,
                        "No se encontró el archivo",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                System.out.println("Archivo encontrado en la carpeta fotos");
            }

            // Mostrar la imagen en una nueva ventana
            showImageInNewWindow(file);
        } catch (IOException e) {
            System.err.println("Error al abrir el archivo: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error al abrir el archivo: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showImageInNewWindow(File imageFile) {
        try {
            // Crear una nueva ventana
            JFrame imageFrame = new JFrame("Imagen");
            imageFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            
            // Cargar la imagen
            ImageIcon imageIcon = new ImageIcon(imageFile.getAbsolutePath());
            Image image = imageIcon.getImage();
            
            // Obtener las dimensiones de la pantalla
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int maxWidth = (int) (screenSize.width * 0.8); // 80% del ancho de la pantalla
            int maxHeight = (int) (screenSize.height * 0.8); // 80% del alto de la pantalla
            
            // Calcular las dimensiones manteniendo la proporción
            int imageWidth = image.getWidth(null);
            int imageHeight = image.getHeight(null);
            
            double scale = Math.min(
                (double) maxWidth / imageWidth,
                (double) maxHeight / imageHeight
            );
            
            int scaledWidth = (int) (imageWidth * scale);
            int scaledHeight = (int) (imageHeight * scale);
            
            // Redimensionar la imagen
            Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            ImageIcon scaledIcon = new ImageIcon(scaledImage);
            
            // Crear el label con la imagen
            JLabel imageLabel = new JLabel(scaledIcon);
            
            // Añadir el label a la ventana
            imageFrame.add(imageLabel);
            
            // Ajustar el tamaño de la ventana
            imageFrame.pack();
            
            // Centrar la ventana en la pantalla
            imageFrame.setLocationRelativeTo(null);
            
            // Mostrar la ventana
            imageFrame.setVisible(true);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al mostrar la imagen: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearChat() {
        String selectedUser = userList.getSelectedValue();
        String message;
        String title;

        if (selectedUser != null) {
            message = "¿Estás seguro de que quieres limpiar el historial del chat con " + selectedUser + "?\nEsta acción eliminará todos los mensajes y las imágenes compartidas, y no se pueden recuperar.";
            title = "Limpiar Chat con " + selectedUser;
        } else {
            message = "¿Estás seguro de que quieres limpiar el historial del chat general?\nEsta acción eliminará todos los mensajes generales y las imágenes compartidas, y no se pueden recuperar.";
            title = "Limpiar Chat General";
        }

        int option = JOptionPane.showConfirmDialog(
            this,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (option == JOptionPane.YES_OPTION) {
            try {
                // Obtener el texto actual del chat
                String chatText = chatArea.getText();
                
                // Encontrar todas las imágenes mencionadas en el chat
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[CLICK PARA ABRIR\\] ([^\\n]+)");
                java.util.regex.Matcher matcher = pattern.matcher(chatText);
                
                // Eliminar cada imagen encontrada
                while (matcher.find()) {
                    String fileName = matcher.group(1).trim();
                    System.out.println("Intentando eliminar imagen: " + fileName);
                    File imageFile = new File(photosDirectory, fileName);
                    System.out.println("Ruta completa: " + imageFile.getAbsolutePath());
                    if (imageFile.exists()) {
                        if (imageFile.delete()) {
                            System.out.println("Imagen eliminada correctamente: " + fileName);
                        } else {
                            System.err.println("No se pudo eliminar la imagen: " + fileName);
                        }
                    } else {
                        System.out.println("La imagen no existe en la carpeta: " + fileName);
                    }
                }

                if (selectedUser != null) {
                    // Limpiar mensajes del usuario seleccionado
                    DatabaseConfig.clearUserMessages(selectedUser);
                } else {
                    // Limpiar mensajes generales
                    DatabaseConfig.clearGeneralMessages();
                }
                
                // Limpiar el área de chat
                chatArea.setText("");
                
                // Limpiar los archivos recibidos en memoria
                receivedFiles.clear();
                
                // Mostrar mensaje de confirmación
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                if (selectedUser != null) {
                    chatArea.append("[" + timestamp + "] Historial del chat con " + selectedUser + " limpiado completamente.\n");
                } else {
                    chatArea.append("[" + timestamp + "] Historial del chat general limpiado completamente.\n");
                }
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                
                // Mostrar mensaje de éxito
                JOptionPane.showMessageDialog(this,
                    "El historial del chat y las imágenes compartidas han sido eliminados completamente.",
                    "Éxito",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                System.err.println("Error al limpiar el chat: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Error al limpiar el historial: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);
            
            String username = loginDialog.getUsername();
            String password = loginDialog.getPassword();
            
            if (username != null && password != null) {
                // Intento de login
                if (DatabaseConfig.validateUser(username, password)) {
                    new ChatWindow(username).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(null,
                        "Credenciales incorrectas",
                        "Error de inicio de sesión",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
} 