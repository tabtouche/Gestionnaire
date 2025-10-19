package ca.polymtl.inf3405.client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Adresse IP du serveur : ");
        String ip = sc.nextLine();
        System.out.print("Port du serveur (5000-5050): ");
        int port = sc.nextInt();

        try (Socket socket = new Socket(ip, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            sc.nextLine(); 
            String message;
            System.out.println("Connecté. Tapez 'exit' pour quitter.");
            while (true) {
                System.out.print("> ");
                message = sc.nextLine();
                out.println(message);
                if (message.equals("exit")) break;
                System.out.println("Réponse : " + in.readLine());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
