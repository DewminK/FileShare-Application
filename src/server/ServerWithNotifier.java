package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enhanced Server with Notifier Integration
 * Demonstrates how to integrate all 5 team member components
 *
 * This is an enhanced version of ServerMain that includes:
 * - Member 1: TCP Server with multithreading
 * - Member 3: FileHandler for NIO operations
 * - Member 4: SynchronizedFileAccess for thread-safe operations
 * - Member 5: Notifier for real-time client notifications
 *
 * @author Integration Example - All Members
 */
public class ServerWithNotifier {

    private ServerSocket serverSocket;
    private boolean running;
    private int port;
    private CopyOnWriteArrayList<ClientHandler> connectedClients;
    private String sharedDirectory;

    // Member 5: Notifier for broadcasting updates
    private Notifier notifier;

    // Member 4 & 3: File operations coordinator
    private shared.FileTransferCoordinator fileCoordinator;

    public ServerWithNotifier(int port, String sharedDirectory) {
        this.port = port;
        this.sharedDirectory = sharedDirectory;
        this.running = false;
        this.connectedClients = new CopyOnWriteArrayList<>();

        // Initialize shared directory
        File dir = new File(sharedDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("[Server] Created shared directory: " + sharedDirectory);
        }

        // Initialize file coordinator (Member 3 + Member 4)
        this.fileCoordinator = new shared.FileTransferCoordinator(sharedDirectory);
        System.out.println("[Server] File coordinator initialized");

        // Initialize notifier (Member 5)
        this.notifier = new Notifier(true, 9876); // UDP enabled on port 9876
        try {
            this.notifier.start();
            System.out.println("[Server] Notifier service started");
        } catch (IOException e) {
            System.err.println("[Server] Failed to start notifier: " + e.getMessage());
        }
    }

    /**
     * Start the server
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("[Server] Started on port " + port);
            System.out.println("[Server] Shared directory: " + sharedDirectory);

            // Notify about server start
            notifier.notifyServerMessage("Server started on port " + port);

            // Accept connections
            Thread acceptThread = new Thread(this::acceptConnections);
            acceptThread.start();

        } catch (IOException e) {
            System.err.println("[Server] Failed to start: " + e.getMessage());
        }
    }

    /**
     * Accept incoming client connections
     */
    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                System.out.println("[Server] New client connected: " + clientAddress);

                // Create handler for this client
                ClientHandler handler = new ClientHandler(clientSocket, this);
                connectedClients.add(handler);

                // Register with notifier (Member 5)
                SocketChannel channel = clientSocket.getChannel();
                if (channel != null) {
                    notifier.registerClient(channel, clientAddress);
                } else {
                    // If channel is null (blocking socket), we can still notify via UDP
                    System.out.println("[Server] Client using blocking socket, UDP notifications available");
                }

                // Start handler thread
                Thread handlerThread = new Thread(handler);
                handlerThread.start();

                // Notify all clients about new connection
                notifier.broadcastNotification(
                        Notifier.NotificationType.CLIENT_CONNECTED,
                        "New client joined the network",
                        clientAddress
                );

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Server] Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop the server
     */
    public void stop() {
        running = false;

        // Notify clients about shutdown
        notifier.notifyServerMessage("Server shutting down...");

        // Close all client connections
        for (ClientHandler handler : connectedClients) {
            handler.disconnect();
        }
        connectedClients.clear();

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }

        // Shutdown notifier
        notifier.stop();

        // Shutdown file coordinator
        fileCoordinator.shutdown();

        System.out.println("[Server] Stopped");
    }

    /**
     * Remove a client handler
     */
    public void removeClient(ClientHandler handler) {
        connectedClients.remove(handler);

        // Unregister from notifier
        if (handler.getSocketChannel() != null) {
            notifier.unregisterClient(handler.getSocketChannel());
        }
    }

    /**
     * Get statistics
     */
    public void printStatistics() {
        System.out.println("\n=== Server Statistics ===");
        System.out.println("Connected clients: " + connectedClients.size());
        System.out.println("\nFile Coordinator Stats:");
        System.out.println(fileCoordinator.getStatistics());
        System.out.println("\nNotifier Stats:");
        System.out.println(notifier.getStatistics());
        System.out.println("========================\n");
    }

    /**
     * Client Handler - handles individual client connection
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private SocketChannel socketChannel;
        private ServerWithNotifier server;
        private PrintWriter out;
        private BufferedReader in;
        private String clientAddress;

        public ClientHandler(Socket socket, ServerWithNotifier server) {
            this.socket = socket;
            this.server = server;
            this.clientAddress = socket.getInetAddress().getHostAddress();
            this.socketChannel = socket.getChannel();
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send welcome message
                out.println("CONNECTED:Welcome to Enhanced File Sharing Server");

                String command;
                while ((command = in.readLine()) != null) {
                    System.out.println("[Server] Command from " + clientAddress + ": " + command);
                    handleCommand(command);
                }

            } catch (IOException e) {
                System.err.println("[Server] Client handler error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handleCommand(String command) {
            if (command.startsWith("LIST_FILES")) {
                handleListFiles();
            } else if (command.startsWith("UPLOAD:")) {
                handleUpload(command);
            } else if (command.startsWith("DOWNLOAD:")) {
                handleDownload(command);
            } else {
                out.println("ERROR:Unknown command");
            }
        }

        private void handleListFiles() {
            File dir = new File(server.sharedDirectory);
            File[] files = dir.listFiles();

            StringBuilder fileList = new StringBuilder("FILE_LIST:");
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.append(file.getName()).append(":")
                                .append(file.length()).append(":")
                                .append(new java.util.Date(file.lastModified())).append("|");
                    }
                }
            }

            out.println(fileList.toString());
            System.out.println("[Server] Sent file list to " + clientAddress);
        }

        private void handleUpload(String command) {
            // Parse: UPLOAD:filename:filesize
            String[] parts = command.split(":");
            if (parts.length < 3) {
                out.println("ERROR:Invalid upload command");
                return;
            }

            String filename = parts[1];
            long fileSize = Long.parseLong(parts[2]);

            System.out.println("[Server] Receiving upload: " + filename + " (" + fileSize + " bytes)");

            // Send ready signal
            out.println("READY:Ready to receive file");

            // Use Member 4's synchronized file access through coordinator
            try {
                InputStream inputStream = socket.getInputStream();

                // Coordinated upload with thread-safe access
                shared.FileTransferCoordinator.TransferResult result =
                        fileCoordinator.handleUpload(filename, inputStream, fileSize);

                if (result.isSuccess()) {
                    System.out.println("[Server] Upload completed: " + filename);
                    out.println("UPLOAD_SUCCESS:File uploaded successfully");

                    // Notify all clients about new file (Member 5)
                    server.notifier.notifyNewFile(filename, clientAddress);

                } else {
                    System.err.println("[Server] Upload failed: " + result.getMessage());
                    out.println("UPLOAD_FAILED:" + result.getMessage());
                }

            } catch (Exception e) {
                System.err.println("[Server] Error during upload: " + e.getMessage());
                out.println("UPLOAD_FAILED:Error during upload");
            }
        }

        private void handleDownload(String command) {
            String filename = command.substring(9);
            File file = new File(server.sharedDirectory, filename);

            if (!file.exists()) {
                out.println("ERROR:File not found");
                return;
            }

            System.out.println("[Server] Sending file: " + filename);

            try {
                // Send file size first
                out.println("FILE_SIZE:" + file.length());

                // Use Member 4's synchronized file access through coordinator
                OutputStream outputStream = socket.getOutputStream();

                // Coordinated download with thread-safe access
                shared.FileTransferCoordinator.TransferResult result =
                        fileCoordinator.handleDownload(filename, outputStream);

                if (result.isSuccess()) {
                    System.out.println("[Server] Download completed: " + filename);

                    // Log download (don't notify for downloads, only uploads/updates)
                    System.out.println("[Server] File downloaded by: " + clientAddress);
                } else {
                    System.err.println("[Server] Download failed: " + result.getMessage());
                }

            } catch (IOException e) {
                System.err.println("[Server] Error during download: " + e.getMessage());
            }
        }

        public void disconnect() {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("[Server] Error closing client connection: " + e.getMessage());
            }

            // Notify about disconnection
            server.notifier.broadcastNotification(
                    Notifier.NotificationType.CLIENT_DISCONNECTED,
                    "Client disconnected",
                    clientAddress
            );

            server.removeClient(this);
            System.out.println("[Server] Client disconnected: " + clientAddress);
        }

        public SocketChannel getSocketChannel() {
            return socketChannel;
        }
    }

    /**
     * Main method - demonstrates the enhanced server
     */
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║  Enhanced File Sharing Server                 ║");
        System.out.println("║  Integrating All 5 Team Member Components     ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        // Create enhanced server
        ServerWithNotifier server = new ServerWithNotifier(8080, "./shared_files");

        // Start server
        server.start();

        System.out.println("\nServer is running...");
        System.out.println("Commands:");
        System.out.println("  'stats' - Show statistics");
        System.out.println("  'quit'  - Stop server");
        System.out.println();

        // Simple command interface
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            String input;
            while ((input = consoleReader.readLine()) != null) {
                if (input.equalsIgnoreCase("quit")) {
                    System.out.println("Shutting down server...");
                    server.stop();
                    break;
                } else if (input.equalsIgnoreCase("stats")) {
                    server.printStatistics();
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading console input: " + e.getMessage());
        }

        System.out.println("Server stopped.");
    }
}
