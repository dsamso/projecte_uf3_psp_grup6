package java.grup6.server;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Missatge rebut: " + message);
                Server.broadcast(message, this);
            }
        } catch (IOException e) {
            System.out.println("Client desconnectat.");
        } finally {
            closeConnection();
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    private void closeConnection() {
        try {
            Server.removeClient(this);
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
