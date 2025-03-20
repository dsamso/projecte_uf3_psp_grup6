package grup6.servidor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MessageManager {
    public static void saveMessage(String remitent, String destinatari, String text) {
        String encryptedText = CryptoUtils.encrypt(text);
        String sql = "INSERT INTO missatges (remitent, destinatari, text) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, remitent);
            stmt.setString(2, destinatari);
            stmt.setString(3, encryptedText);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void showMessages(String user) {
        String sql = "SELECT * FROM missatges WHERE destinatari IS NULL OR destinatari = ? ORDER BY timestamp";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String decryptedText = CryptoUtils.decrypt(rs.getString("text"));
                System.out.println(rs.getString("remitent") + ": " + decryptedText);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}