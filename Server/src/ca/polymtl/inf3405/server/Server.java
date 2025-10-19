package ca.polymtl.inf3405.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Entrez le port (5000-5050): ");
        int port = sc.nextInt();

        if (port < 5000 || port > 5050) {
            System.out.println("Port invalide !");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur en écoute sur le port " + port + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
