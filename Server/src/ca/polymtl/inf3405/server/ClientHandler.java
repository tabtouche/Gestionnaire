package ca.polymtl.inf3405.server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientHandler extends Thread {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String time = new SimpleDateFormat("yyyy-MM-dd@HH:mm:ss").format(new Date());
                System.out.printf("[%s:%d - %s] : %s%n",
                        socket.getInetAddress().getHostAddress(),
                        socket.getPort(),
                        time,
                        inputLine);
                if (inputLine.equals("exit")) break;
                out.println("OK " + inputLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
