package grup6.servidor;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private SSLSocket socket;
    private BufferedReader input;
    private PrintWriter output;

    public ClientHandler(SSLSocket socket) {
        this.socket = socket;
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            output.println("🔹 Connectat al servidor segur!");
            String message;
            while ((message = input.readLine()) != null) {
                System.out.println("📩 Missatge rebut: " + message);
                output.println("Servidor: Missatge rebut.");
            }
        } catch (IOException e) {
            System.out.println("❌ Client desconnectat.");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
