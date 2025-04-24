package grup6.server;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SecureServer {
    private static final int PORT = 12345;

    public static void startServer() {
        try {
            SSLServerSocketFactory factory = getSSLContext().getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);

            System.out.println("🔐 Servidor segur iniciat al port " + PORT);
            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("✅ Nou client connectat!");
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SSLContext getSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("resources/serverkeystore.jks"), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        return sslContext;
    }
}
