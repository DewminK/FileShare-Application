package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import server.Notifier;

/**
 * Member 1 - Server Developer
 * TCP Server for File Sharing Application
 * Handles multiple client connections concurrently using multithreading
 *
 * Networking Concepts Used:
 * - ServerSocket for TCP server
 * - Socket for client communication
 * - Multithreading - spawns thread for each client
 * - I/O Streams for data transfer
 */
public class ServerMain {
    private ServerSocket serverSocket;
    private boolean running;
    private int port;
    private List<ClientHandler> connectedClients;
    private List<ServerListener> listeners;
    private String sharedDirectory;
    private Notifier notifier; // Member 5: Broadcaster/Notifier

    public ServerMain(int port, String sharedDirectory) {
        this.port = port;
        this.sharedDirectory = sharedDirectory;
        this.running = false;
        this.connectedClients = new CopyOnWriteArrayList<>();
        this.listeners = new ArrayList<>();

        // Create shared directory if it doesn't exist
        File dir = new File(sharedDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("[Server] Created shared directory: " + sharedDirectory);
        }

        // Initialize Member 5: Broadcaster/Notifier
        // Using UDP broadcasting on port 9876
        this.notifier = new Notifier(true, 9876);
        System.out.println("[Server] Notifier initialized (UDP port 9876)");
    }

    /**
     * Start the server and listen for client connections
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;

            // Start Member 5: Notifier
            notifier.start();
            System.out.println("[Server] Notifier service started");

            notifyServerStarted();
            System.out.println("[Server] Started on port " + port);
            System.out.println("[Server] Shared directory: " + sharedDirectory);

            // Broadcast server started message
            notifier.notifyServerMessage("File Sharing Server started on port " + port);

            // Accept client connections in a separate thread
            Thread acceptThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptConnections();
                }
            });
            acceptThread.start();

        } catch (IOException e) {
            System.err.println("[Server] Failed to start: " + e.getMessage());
            notifyServerError("Failed to start server: " + e.getMessage());
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

                // Create a handler for this client
                ClientHandler handler = new ClientHandler(clientSocket, this);
                connectedClients.add(handler);

                // Start handler thread
                Thread handlerThread = new Thread(handler);
                handlerThread.start();

                notifyClientConnected(clientAddress);

                // Broadcast client connection via Member 5: Notifier
                notifier.notifyClientConnected(clientAddress);

                // Also broadcast to all connected clients via TCP
                broadcastToClients("CLIENT_CONNECTED", "New client connected", clientAddress);

                // Update user list for all clients
                broadcastUserList();

                // Notify new client they joined chat
                handler.sendMessage("USER_JOINED:" + clientAddress);

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

        // Stop Member 5: Notifier
        if (notifier != null) {
            notifier.stop();
            System.out.println("[Server] Notifier service stopped");
        }

        notifyServerStopped();
        System.out.println("[Server] Stopped");
    }

    /**
     * Remove a client handler from the list
     */
    public void removeClient(ClientHandler handler) {
        String clientAddr = handler.getClientAddress();
        connectedClients.remove(handler);
        notifyClientDisconnected(clientAddr);

        // Broadcast client disconnection via Member 5: Notifier
        if (notifier != null) {
            notifier.notifyClientDisconnected(clientAddr);
        }

        // Also broadcast to all connected clients via TCP
        broadcastToClients("CLIENT_DISCONNECTED", "Client disconnected", clientAddr);

        // Update user list for remaining clients
        broadcastUserList();

        // Notify all clients that user left chat
        for (ClientHandler h : connectedClients) {
            h.sendMessage("USER_LEFT:" + clientAddr);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String getSharedDirectory() {
        return sharedDirectory;
    }

    public int getConnectedClientsCount() {
        return connectedClients.size();
    }

    public List<String> getConnectedClientsInfo() {
        List<String> clientsInfo = new ArrayList<>();
        for (ClientHandler handler : connectedClients) {
            clientsInfo.add(handler.getClientAddress() + " (Connected: " + handler.getConnectionTime() + "s)");
        }
        return clientsInfo;
    }

    // Listener pattern for UI updates
    public void addServerListener(ServerListener listener) {
        listeners.add(listener);
    }

    private void notifyServerStarted() {
        for (ServerListener listener : listeners) {
            listener.onServerStarted(port);
        }
    }

    private void notifyServerStopped() {
        for (ServerListener listener : listeners) {
            listener.onServerStopped();
        }
    }

    private void notifyClientConnected(String clientAddress) {
        for (ServerListener listener : listeners) {
            listener.onClientConnected(clientAddress);
        }
    }

    private void notifyClientDisconnected(String clientAddress) {
        for (ServerListener listener : listeners) {
            listener.onClientDisconnected(clientAddress);
        }
    }

    private void notifyServerError(String error) {
        for (ServerListener listener : listeners) {
            listener.onServerError(error);
        }
    }

    public void notifyFileTransfer(String clientAddress, String operation, String filename) {
        for (ServerListener listener : listeners) {
            listener.onFileTransfer(clientAddress, operation, filename);
        }
    }

    public void notifyChatMessage(String sender, String message) {
        for (ServerListener listener : listeners) {
            if (listener instanceof ChatListener) {
                ((ChatListener) listener).onChatMessage(sender, message);
            }
        }
    }

    /**
     * Broadcast notification to all clients (TCP method)
     */
    public void broadcastToClients(String type, String message, String details) {
        String notification = String.format("NOTIFICATION:[%s]%s|%s", type, message, details);
        for (ClientHandler handler : connectedClients) {
            handler.sendNotification(notification);
        }
        System.out.println("[Server] Broadcasted to " + connectedClients.size() + " clients: " + notification);
    }

    /**
     * Broadcast chat message to all connected clients
     */
    public void broadcastChatMessage(String sender, String message) {
        String chatMessage = "CHAT:" + sender + ":" + message;
        for (ClientHandler handler : connectedClients) {
            handler.sendMessage(chatMessage);
        }
        System.out.println("[Server] Chat broadcast from " + sender + ": " + message);
    }

    /**
     * Send user list to all clients
     */
    private void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USER_LIST:");
        for (ClientHandler handler : connectedClients) {
            userList.append(handler.getClientAddress()).append("|");
        }
        String message = userList.toString();
        for (ClientHandler handler : connectedClients) {
            handler.sendMessage(message);
        }
    }

    /**
     * Listener interface for server events
     */
    public interface ServerListener {
        void onServerStarted(int port);

        void onServerStopped();

        void onClientConnected(String clientAddress);

        void onClientDisconnected(String clientAddress);

        void onFileTransfer(String clientAddress, String operation, String filename);

        void onServerError(String error);
    }

    /**
     * Listener interface for chat messages
     */
    public interface ChatListener extends ServerListener {
        void onChatMessage(String sender, String message);
    }

    /**
     * Client Handler - handles individual client connection
     */
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private ServerMain server;
        private PrintWriter out;
        private BufferedReader in;
        private String clientAddress;
        private long connectionStartTime;

        public ClientHandler(Socket socket, ServerMain server) {
            this.socket = socket;
            this.server = server;
            this.clientAddress = socket.getInetAddress().getHostAddress();
            this.connectionStartTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send welcome message
                out.println("CONNECTED:Welcome to File Sharing Server");

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
            } else if (command.startsWith("CHAT:")) {
                handleChatMessage(command);
            } else {
                out.println("ERROR:Unknown command");
            }
        }

        private void handleChatMessage(String command) {
            // Parse: CHAT:message
            String message = command.substring(5);
            System.out.println("[Server] Chat from " + clientAddress + ": " + message);

            // Notify UI about chat message
            server.notifyChatMessage(clientAddress, message);

            // Broadcast to all clients
            server.broadcastChatMessage(clientAddress, message);
        }

        private void handleListFiles() {
            File dir = new File(server.getSharedDirectory());
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
            server.notifyFileTransfer(clientAddress, "UPLOAD", filename);

            // Send ready signal
            out.println("READY:Ready to receive file");

            // Receive the file data
            try {
                File outputFile = new File(server.getSharedDirectory(), filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos);

                InputStream inputStream = socket.getInputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesReceived = 0;

                // Read file data directly from socket
                while (totalBytesReceived < fileSize &&
                        (bytesRead = inputStream.read(buffer, 0,
                                (int) Math.min(buffer.length, fileSize - totalBytesReceived))) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    totalBytesReceived += bytesRead;
                }

                bos.close();
                fos.close();

                System.out.println("[Server] Upload completed: " + filename + " (" + totalBytesReceived + " bytes)");
                out.println("UPLOAD_SUCCESS:File uploaded successfully");

                // Broadcast new file notification via Member 5: Notifier
                server.notifier.notifyNewFile(filename, clientAddress);

                // Also broadcast to all connected clients via TCP
                server.broadcastToClients("NEW_FILE", clientAddress + " uploaded", filename);

            } catch (IOException e) {
                System.err.println("[Server] Error receiving file: " + e.getMessage());
                out.println("UPLOAD_FAILED:Error receiving file");
            }
        }

        private void handleDownload(String command) {
            String filename = command.substring(9);
            File file = new File(server.getSharedDirectory(), filename);

            if (!file.exists()) {
                out.println("ERROR:File not found");
                return;
            }

            System.out.println("[Server] Sending file: " + filename);
            server.notifyFileTransfer(clientAddress, "DOWNLOAD", filename);

            // TODO: Member 4 integration point for synchronized file read
            // For now, basic implementation
            out.println("FILE_SIZE:" + file.length());
        }

        public void disconnect() {
            try {
                if (out != null)
                    out.close();
                if (in != null)
                    in.close();
                if (socket != null && !socket.isClosed())
                    socket.close();
            } catch (IOException e) {
                System.err.println("[Server] Error closing client connection: " + e.getMessage());
            }
            server.removeClient(this);
            System.out.println("[Server] Client disconnected: " + clientAddress);
        }

        public String getClientAddress() {
            return clientAddress;
        }

        public long getConnectionTime() {
            return (System.currentTimeMillis() - connectionStartTime) / 1000;
        }

        /**
         * Send notification to this client
         */
        public void sendNotification(String notification) {
            if (out != null) {
                out.println(notification);
            }
        }

        /**
         * Send message to this client
         */
        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }
    }

    /**
     * Simple test main method
     */
    public static void main(String[] args) {
        ServerMain server = new ServerMain(9090, "./shared_files");
        server.start();

        // Keep running
        System.out.println("Server is running. Press Ctrl+C to stop.");
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
