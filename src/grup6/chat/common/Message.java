package grup6.chat.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private final MessageType type;
    private final String sender;
    private final String content;
    private final LocalDateTime timestamp;
    private String encryptedContent;

    public Message(MessageType type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getEncryptedContent() {
        return encryptedContent;
    }

    public void setEncryptedContent(String encryptedContent) {
        this.encryptedContent = encryptedContent;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", 
            timestamp.toString(), 
            sender, 
            content);
    }
} 