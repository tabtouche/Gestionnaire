package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Path baseDir;
    private Path currentDir;
    private DataInputStream in;
    private DataOutputStream out;

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd@HH:mm:ss");

    public ClientHandler(Socket socket, Path baseDir) {
        this.socket = socket;
        this.baseDir = baseDir;
        this.currentDir = baseDir;
    }

    @Override
    public void run() {
        String who = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        try (socket) {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            log(who, "CONNECT");
            out.writeUTF("OK CONNECTED");
            out.flush();

            while (true) {
                String line;
                try {
                    line = in.readUTF();
                } catch (IOException eof) {
                    log(who, "DISCONNECT");
                    break;
                }
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) { 
                    out.writeUTF("ERR Empty command");
                    out.flush();
                    continue; 
                }

                String[] parts = line.split("\\s+", 2);
                String cmd = parts[0].toLowerCase();
                String arg = (parts.length > 1) ? parts[1].trim() : "";

                log(who, line);

                switch (cmd) {
                    case "ls":       handleLs(); break;
                    case "cd":       handleCd(arg); break;
                    case "mkdir":    handleMkdir(arg); break;
                    case "delete":   handleDelete(arg); break;
                    case "download": handleDownload(arg); break;
                    case "upload":   handleUpload(arg); break;
                    case "exit":
                        out.writeUTF("OK BYE");
                        out.flush();
                        log(who, "EXIT");
                        return;
                    default:
                        out.writeUTF("ERR Unknown command");
                        out.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("[" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "] erreur: " + e.getMessage());
        }
    }

    private void handleLs() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    sb.append("[Folder] ");
                } else {
                    sb.append("[File] ");
                }
                sb.append(p.getFileName().toString()).append('\n');
            }
        }
        out.writeUTF("OK\n" + sb.toString());
        out.flush();
    }

    private void handleCd(String arg) throws IOException {
        if (arg.isEmpty()) { 
            out.writeUTF("ERR Usage: cd <path>");
            out.flush();
            return; 
        }
        Path target = secureResolve(arg);
        if (Files.exists(target) && Files.isDirectory(target)) {
            currentDir = target.normalize();
            out.writeUTF("OK Vous êtes dans le dossier " + currentDir.getFileName().toString());
            out.flush();
        } else {
            out.writeUTF("ERR Not a directory");
            out.flush();
        }
    }

    private void handleMkdir(String arg) throws IOException {
        if (arg.isEmpty()) { 
            out.writeUTF("ERR Usage: mkdir <name>");
            out.flush();
            return; 
        }
        Path target = secureResolve(arg);
        try {
            Files.createDirectories(target);
            out.writeUTF("OK Le dossier " + arg + " a été créé.");
            out.flush();
        } catch (IOException e) {
            out.writeUTF("ERR " + e.getMessage());
            out.flush();
        }
    }

    private void handleDelete(String arg) throws IOException {
        if (arg.isEmpty()) { 
            out.writeUTF("ERR Usage: delete <file|dir>");
            out.flush();
            return; 
        }
        Path target = secureResolve(arg);
        if (!Files.exists(target)) { 
            out.writeUTF("ERR Not found");
            out.flush();
            return; 
        }

        try {
            if (Files.isDirectory(target)) {
                Files.walk(target)
                     .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                     .forEach(p -> { 
                         try { 
                             Files.deleteIfExists(p); 
                         } catch (IOException ignored) {} 
                     });
                out.writeUTF("OK Le dossier " + arg + " a bien été supprimé.");
            } else {
                Files.delete(target);
                out.writeUTF("OK Le fichier " + arg + " a bien été supprimé.");
            }
            out.flush();
        } catch (IOException e) {
            out.writeUTF("ERR " + e.getMessage());
            out.flush();
        }
    }

    private void handleDownload(String arg) throws IOException {
        if (arg.isEmpty()) { 
            out.writeUTF("ERR Usage: download <file>");
            out.flush();
            return; 
        }
        Path file = secureResolve(arg);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            out.writeUTF("ERR Not a file");
            out.flush();
            return;
        }
        long size = Files.size(file);
        out.writeUTF("OK");
        out.flush();
        out.writeLong(size);
        out.flush();
        try (var fis = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        out.flush();
    }

    private void handleUpload(String arg) throws IOException {
        if (arg.isEmpty()) { 
            out.writeUTF("ERR Usage: upload <destinationName>");
            out.flush();
            return; 
        }
        Path dest = secureResolve(arg);
        Files.createDirectories(dest.getParent());

        out.writeUTF("OK");
        out.flush();
        
        long size = in.readLong();
        try (var fos = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long remaining = size;
            byte[] buf = new byte[64 * 1024];
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int r = in.read(buf, 0, toRead);
                if (r == -1) throw new IOException("Stream ended prematurely");
                fos.write(buf, 0, r);
                remaining -= r;
            }
        }
        out.writeUTF("OK Le fichier " + arg + " a bien été téléversé.");
        out.flush();
    }

    private Path secureResolve(String userPath) throws IOException {
        Path p = currentDir.resolve(userPath).normalize();
        if (!p.startsWith(baseDir)) {
            throw new IOException("Path traversal interdit");
        }
        return p;
    }

    private void log(String who, String what) {
        String ts = LocalDateTime.now().format(TS);
        System.out.println("[" + who + " - " + ts + "] : " + what);
    }
}