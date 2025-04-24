package grup6.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {
    public static void saveMessage(String sender, String recipient, String message) {
        String sql;
        if (recipient == null) {
            sql = "INSERT INTO mensajes_generales (sender, message) VALUES (?, ?)";
        } else {
            sql = "INSERT INTO mensajes (sender, recipient, message) VALUES (?, ?, ?)";
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sender);
            if (recipient == null) {
                stmt.setString(2, message);
            } else {
                stmt.setString(2, recipient);
                stmt.setString(3, message);
            }
            stmt.executeUpdate();
            Logger.log("Mensaje guardado: " + sender + " -> " + (recipient != null ? recipient : "todos"));
        } catch (SQLException e) {
            Logger.error("Error guardando mensaje", e);
        }
    }

    public static List<String> getMessageHistory(String username) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT * FROM (" +
                    "SELECT sender, message, timestamp FROM mensajes_generales " +
                    "UNION ALL " +
                    "SELECT sender, message, timestamp FROM mensajes WHERE recipient = ? OR sender = ?" +
                    ") ORDER BY timestamp DESC LIMIT 100";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String message = CryptoUtils.decrypt(rs.getString("message"));
                String timestamp = rs.getString("timestamp");
                messages.add("[" + timestamp + "] " + sender + ": " + message);
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo historial de mensajes", e);
        }

        return messages;
    }

    public static List<String> getUnreadMessages(String username) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM mensajes " +
                    "WHERE recipient = ? AND is_read = 0 ORDER BY timestamp";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String message = CryptoUtils.decrypt(rs.getString("message"));
                String timestamp = rs.getString("timestamp");
                messages.add("[" + timestamp + "] " + sender + " (privado): " + message);
            }

            // Marcar mensajes como leídos
            if (!messages.isEmpty()) {
                markMessagesAsRead(username);
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo mensajes no leídos", e);
        }

        return messages;
    }

    private static void markMessagesAsRead(String username) {
        String sql = "UPDATE mensajes SET is_read = 1 WHERE recipient = ? AND is_read = 0";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.error("Error marcando mensajes como leídos", e);
        }
    }

    public static void clearMessageHistory() {
        String[] tables = {"mensajes", "mensajes_generales"};
        for (String table : tables) {
            String sql = "DELETE FROM " + table;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
                Logger.log("Historial de mensajes borrado: " + table);
            } catch (SQLException e) {
                Logger.error("Error borrando historial de mensajes", e);
            }
        }
    }
}