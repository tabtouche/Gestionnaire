package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server {
    private static final int MIN_PORT = 5000;
    private static final int MAX_PORT = 5050;

    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            
            String serverAddress = "";
            boolean validIP = false;
            while (!validIP) {
                System.out.print("Entrez l'adresse IP du serveur (ex: 127.0.0.1 ou 0.0.0.0) : ");
                serverAddress = br.readLine().trim();
                
                if (isValidIP(serverAddress)) {
                    validIP = true;
                } else {
                    System.err.println("Adresse IP invalide. Format attendu: xxx.xxx.xxx.xxx (0-255 pour chaque octet)");
                }
            }
            
            int port = -1;
            boolean validPort = false;
            while (!validPort) {
                System.out.print("Entrez le port (" + MIN_PORT + "-" + MAX_PORT + ") : ");
                try {
                    port = Integer.parseInt(br.readLine().trim());
                    if (port < MIN_PORT || port > MAX_PORT) {
                        System.err.println("Port invalide. Utilisez un port entre " + MIN_PORT + " et " + MAX_PORT + ".");
                    } else {
                        validPort = true;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Veuillez entrer un nombre pour le port.");
                }
            }
            
            Path baseDir = Paths.get("server_storage").toAbsolutePath().normalize();
            Files.createDirectories(baseDir);
            System.out.println("Répertoire racine: " + baseDir);
            
            InetAddress address = InetAddress.getByName(serverAddress);
            try (ServerSocket serverSocket = new ServerSocket(port, 50, address)) {
                System.out.println("Serveur démarré sur " + serverAddress + ":" + port + " ...");
                
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, baseDir);
                    Thread t = new Thread(handler);
                    t.setDaemon(true);
                    t.start();
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur E/S: " + e.getMessage());
        }
    }

    private static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        
        if (parts.length != 4) {
            return false;
        }
        
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return true;
    }
}