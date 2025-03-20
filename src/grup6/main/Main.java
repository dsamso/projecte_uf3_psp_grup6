package grup6.main;

import grup6.servidor.SecureServer;

public class Main {
    public static void main(String[] args) {
        System.out.println("📡 Iniciant servidor...");
        new Thread(SecureServer::startServer).start();
    }
}