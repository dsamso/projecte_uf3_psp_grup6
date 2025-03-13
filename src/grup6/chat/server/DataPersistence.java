package grup6.chat.server;

import grup6.chat.common.Message;
import grup6.chat.common.User;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DataPersistence {
    private static final String USERS_FILE = "usuaris.dat";
    private static final String MESSAGES_FILE = "missatges.dat";
    private final ConcurrentHashMap<String, User> users;

    public DataPersistence() {
        this.users = new ConcurrentHashMap<>();
        loadData();
    }

    private void loadData() {
        loadUsers();
        loadMessages();
    }

    private void loadUsers() {
        File file = new File(USERS_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                @SuppressWarnings("unchecked")
                List<User> loadedUsers = (List<User>) ois.readObject();
                loadedUsers.forEach(user -> users.put(user.getUsername(), user));
            } catch (Exception e) {
                System.err.println("Error en carregar els usuaris: " + e.getMessage());
            }
        }
    }

    private void loadMessages() {
        File file = new File(MESSAGES_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) ois.readObject();
                // Els missatges es carreguen però no es guarden a la memòria per evitar problemes de memòria
            } catch (Exception e) {
                System.err.println("Error en carregar els missatges: " + e.getMessage());
            }
        }
    }

    public void saveUser(User user) {
        users.put(user.getUsername(), user);
        saveUsers();
    }

    public void saveMessage(Message message) {
        List<Message> messages = new ArrayList<>();
        File file = new File(MESSAGES_FILE);
        
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                @SuppressWarnings("unchecked")
                List<Message> existingMessages = (List<Message>) ois.readObject();
                messages.addAll(existingMessages);
            } catch (Exception e) {
                System.err.println("Error en llegir els missatges existents: " + e.getMessage());
            }
        }

        messages.add(message);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(messages);
        } catch (Exception e) {
            System.err.println("Error en desar el missatge: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(new ArrayList<>(users.values()));
        } catch (Exception e) {
            System.err.println("Error en desar els usuaris: " + e.getMessage());
        }
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public boolean userExists(String username) {
        return users.containsKey(username);
    }
} 