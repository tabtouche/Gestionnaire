package client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Client {
    private static final int MIN_PORT = 5000;
    private static final int MAX_PORT = 5050;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader userInput;

    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }

    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            
            String serverAddress = "";
            boolean validIP = false;
            while (!validIP) {
                System.out.print("Entrez l'adresse IP du serveur : ");
                serverAddress = br.readLine().trim();
                
                if (isValidIP(serverAddress)) {
                    validIP = true;
                } else {
                    System.err.println("Adresse IP invalide. Format attendu: xxx.xxx.xxx.xxx");
                }
            }
            
            int port = -1;
            boolean validPort = false;
            while (!validPort) {
                System.out.print("Entrez le port du serveur (" + MIN_PORT + "-" + MAX_PORT + ") : ");
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
            
            socket = new Socket(serverAddress, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            userInput = new BufferedReader(new InputStreamReader(System.in));
            
            String response = in.readUTF();
            System.out.println(response);
            
            commandLoop();
            
        } catch (IOException e) {
            System.err.println("Erreur: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture: " + e.getMessage());
            }
        }
    }

    private void commandLoop() throws IOException {
        while (true) {
            System.out.print("> ");
            String input = userInput.readLine();
            if (input == null || input.trim().isEmpty()) {
                continue;
            }
            
            String[] parts = input.trim().split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String arg = (parts.length > 1) ? parts[1].trim() : "";
            
            try {
                switch (cmd) {
                    case "ls":
                        handleLs(input);
                        break;
                    case "cd":
                        handleCd(input, arg);
                        break;
                    case "mkdir":
                        handleMkdir(input, arg);
                        break;
                    case "delete":
                        handleDelete(input, arg);
                        break;
                    case "upload":
                        handleUpload(input, arg);
                        break;
                    case "download":
                        handleDownload(input, arg);
                        break;
                    case "exit":
                        handleExit(input);
                        return;
                    default:
                        System.out.println("Commande inconnue: " + cmd);
                }
            } catch (IOException e) {
                System.err.println("Erreur: " + e.getMessage());
                return;
            }
        }
    }

    private void handleLs(String input) throws IOException {
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        System.out.println(response);
    }

    private void handleCd(String input, String arg) throws IOException {
        if (arg.isEmpty()) {
            System.out.println("Usage: cd <path>");
            return;
        }
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        if (response.startsWith("OK")) {
            System.out.println(response.substring(3));
        } else {
            System.out.println(response);
        }
    }

    private void handleMkdir(String input, String arg) throws IOException {
        if (arg.isEmpty()) {
            System.out.println("Usage: mkdir <name>");
            return;
        }
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        if (response.startsWith("OK")) {
            System.out.println(response.substring(3));
        } else {
            System.out.println(response);
        }
    }

    private void handleDelete(String input, String arg) throws IOException {
        if (arg.isEmpty()) {
            System.out.println("Usage: delete <file|dir>");
            return;
        }
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        if (response.startsWith("OK")) {
            System.out.println(response.substring(3));
        } else {
            System.out.println(response);
        }
    }

    private void handleUpload(String input, String filename) throws IOException {
        if (filename.isEmpty()) {
            System.out.println("Usage: upload <filename>");
            return;
        }
        
        File file = new File(filename);
        if (!file.exists() || file.isDirectory()) {
            System.out.println("Fichier non trouvé ou n'est pas un fichier");
            return;
        }
        
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        
        if (response.startsWith("OK")) {
            long fileSize = file.length();
            out.writeLong(fileSize);
            out.flush();
            
            try (var fis = Files.newInputStream(file.toPath())) {
                fis.transferTo(out);
            }
            out.flush();
            
            String confirmResponse = in.readUTF();
            if (confirmResponse.startsWith("OK")) {
                System.out.println(confirmResponse.substring(3));
            } else {
                System.out.println(confirmResponse);
            }
        } else {
            System.out.println(response);
        }
    }

    private void handleDownload(String input, String filename) throws IOException {
        if (filename.isEmpty()) {
            System.out.println("Usage: download <filename>");
            return;
        }
        
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        
        if (response.startsWith("OK")) {
            long fileSize = in.readLong();
            Path dest = Paths.get(filename);
            
            try (var fos = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                long remaining = fileSize;
                byte[] buf = new byte[64 * 1024];
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int r = in.read(buf, 0, toRead);
                    if (r == -1) throw new IOException("Stream ended prematurely");
                    fos.write(buf, 0, r);
                    remaining -= r;
                }
            }
            System.out.println("Le fichier " + filename + " a bien été téléchargé");
        } else {
            System.out.println(response);
        }
    }

    private void handleExit(String input) throws IOException {
        out.writeUTF(input);
        out.flush();
        String response = in.readUTF();
        System.out.println("Vous avez été déconnecté avec succès.");
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