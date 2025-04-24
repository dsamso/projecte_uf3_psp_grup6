package grup6.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseConfig {
    // Configuración para SQLite
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static final String USER = "";
    private static final String PASSWORD = "";

    static {
        try {
            // Cargar el driver de SQLite
            Class.forName("org.sqlite.JDBC");
            System.out.println("Driver SQLite cargado correctamente");
            initDatabase();
        } catch (SQLException e) {
            System.err.println("Error inicializando la base de datos: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Error: No se encontró el driver de SQLite");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void initDatabase() throws SQLException {
        try (Connection conn = getConnection()) {
            System.out.println("Conexión a la base de datos establecida correctamente");
            
            // Crear tabla de usuarios si no existe
            String createUsersTable = 
                "CREATE TABLE IF NOT EXISTS usuarios (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    username TEXT UNIQUE NOT NULL," +
                "    password TEXT NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    is_admin BOOLEAN DEFAULT 0" +
                ")";

            // Crear tabla de mensajes si no existe
            String createMessagesTable = 
                "CREATE TABLE IF NOT EXISTS mensajes (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    sender TEXT NOT NULL," +
                "    recipient TEXT NOT NULL," +
                "    message TEXT NOT NULL," +
                "    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "    is_read BOOLEAN DEFAULT 0," +
                "    FOREIGN KEY (sender) REFERENCES usuarios(username)," +
                "    FOREIGN KEY (recipient) REFERENCES usuarios(username)" +
                ")";

            // Crear tabla de mensajes generales si no existe
            String createGeneralMessagesTable = 
                "CREATE TABLE IF NOT EXISTS mensajes_generales (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    sender TEXT NOT NULL," +
                "    message TEXT NOT NULL," +
                "    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (sender) REFERENCES usuarios(username)" +
                ")";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createUsersTable);
                stmt.execute(createMessagesTable);
                stmt.execute(createGeneralMessagesTable);
                System.out.println("Tablas 'usuarios', 'mensajes' y 'mensajes_generales' creadas o verificadas correctamente");
            }

            // Verificar si existe el usuario admin
            String checkAdmin = "SELECT COUNT(*) FROM usuarios WHERE username = 'admin'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkAdmin)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // Crear usuario admin si no existe
                    String createAdmin = "INSERT INTO usuarios (username, password, is_admin) VALUES ('admin', 'admin123', 1)";
                    stmt.execute(createAdmin);
                    System.out.println("Usuario admin creado con contraseña: admin123");
                }
            }
        }
    }

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO usuarios (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            System.out.println("Usuario registrado correctamente: " + username);
            return true;
        } catch (SQLException e) {
            System.err.println("Error registrando usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean validateUser(String username, String password) {
        String sql = "SELECT password FROM usuarios WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    boolean isValid = storedPassword.equals(password);
                    System.out.println("Validación de usuario " + username + ": " + (isValid ? "correcta" : "incorrecta"));
                    return isValid;
                }
                System.out.println("Usuario no encontrado: " + username);
            }
        } catch (SQLException e) {
            System.err.println("Error validando usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                boolean exists = rs.getInt(1) > 0;
                System.out.println("Verificación de existencia de usuario " + username + ": " + (exists ? "existe" : "no existe"));
                return exists;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando usuario: " + e.getMessage());
            return false;
        }
    }

    public static void saveMessage(String sender, String recipient, String message) {
        String sql = "INSERT INTO mensajes (sender, recipient, message, is_read) VALUES (?, ?, ?, 0)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
            System.out.println("Mensaje guardado en la base de datos: " + sender + " -> " + recipient + ": " + message);
        } catch (SQLException e) {
            System.err.println("Error guardando mensaje: " + e.getMessage());
        }
    }

    public static List<String> getUnreadMessages(String recipient) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM mensajes WHERE recipient = ? AND is_read = 0 ORDER BY timestamp";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipient);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String message = rs.getString("message");
                    String timestamp = rs.getString("timestamp");
                    messages.add("[" + timestamp + "] " + sender + " (privado): " + message);
                }
            }
            
            // Marcar mensajes como leídos
            String updateSql = "UPDATE mensajes SET is_read = 1 WHERE recipient = ? AND is_read = 0";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, recipient);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo mensajes: " + e.getMessage());
        }
        return messages;
    }

    public static List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM usuarios WHERE username != 'admin' ORDER BY username";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo usuarios: " + e.getMessage());
        }
        return users;
    }

    public static void saveGeneralMessage(String sender, String message) {
        String sql = "INSERT INTO mensajes_generales (sender, message) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
            System.out.println("Mensaje general guardado en la base de datos: " + sender + ": " + message);
        } catch (SQLException e) {
            System.err.println("Error guardando mensaje general: " + e.getMessage());
        }
    }

    public static List<String> getMessageHistory(String username) {
        List<String> messages = new ArrayList<>();
        
        // Obtener mensajes privados
        String privateSql = 
            "SELECT sender, recipient, message, timestamp " +
            "FROM mensajes " +
            "WHERE sender = ? OR recipient = ? " +
            "ORDER BY timestamp DESC " +
            "LIMIT 50";
        
        // Obtener mensajes generales
        String generalSql = 
            "SELECT sender, message, timestamp " +
            "FROM mensajes_generales " +
            "ORDER BY timestamp DESC " +
            "LIMIT 50";
        
        try (Connection conn = getConnection()) {
            // Obtener mensajes privados
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String sender = rs.getString("sender");
                        String recipient = rs.getString("recipient");
                        String message = rs.getString("message");
                        String timestamp = rs.getString("timestamp");
                        
                        String messageType;
                        if (sender.equals(username)) {
                            messageType = "Tú -> " + recipient;
                        } else if (recipient.equals(username)) {
                            messageType = sender + " -> Tú";
                        } else {
                            messageType = sender + " -> " + recipient;
                        }
                        
                        String formattedMessage = "[" + timestamp + "] " + messageType + " (privado): " + message;
                        messages.add(formattedMessage);
                    }
                }
            }
            
            // Obtener mensajes generales
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(generalSql)) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String message = rs.getString("message");
                    String timestamp = rs.getString("timestamp");
                    
                    String formattedMessage = "[" + timestamp + "] " + sender + " (general): " + message;
                    messages.add(formattedMessage);
                }
            }
            
            // Ordenar todos los mensajes por timestamp
            messages.sort((a, b) -> {
                String timestampA = a.substring(1, a.indexOf("]"));
                String timestampB = b.substring(1, b.indexOf("]"));
                return timestampB.compareTo(timestampA);
            });
            
            // Limitar a 50 mensajes
            if (messages.size() > 50) {
                messages = messages.subList(0, 50);
            }
            
            System.out.println("Total de mensajes encontrados: " + messages.size());
        } catch (SQLException e) {
            System.err.println("Error obteniendo historial de mensajes: " + e.getMessage());
        }
        return messages;
    }

    public static void saveMessage(String message) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "INSERT INTO messages (message) VALUES (?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, message);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void clearMessageHistory() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // Borrar mensajes privados
            String privateSql = "DELETE FROM mensajes";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.executeUpdate();
            }

            // Borrar mensajes generales
            String generalSql = "DELETE FROM mensajes_generales";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.executeUpdate();
            }

            System.out.println("Historial de mensajes limpiado correctamente");
        } catch (SQLException e) {
            System.err.println("Error al limpiar el historial de mensajes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void clearUserMessages(String username) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // Borrar mensajes privados del usuario
            String privateSql = "DELETE FROM mensajes WHERE sender = ? OR recipient = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            }

            // Borrar mensajes generales del usuario
            String generalSql = "DELETE FROM mensajes_generales WHERE sender = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
            }

            System.out.println("Mensajes del usuario " + username + " limpiados correctamente");
        } catch (SQLException e) {
            System.err.println("Error al limpiar los mensajes del usuario: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void clearGeneralMessages() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // Borrar solo mensajes generales
            String generalSql = "DELETE FROM mensajes_generales";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.executeUpdate();
            }

            System.out.println("Mensajes generales limpiados correctamente");
        } catch (SQLException e) {
            System.err.println("Error al limpiar los mensajes generales: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 