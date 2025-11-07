package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
    }

    /**
     * Start the server and listen for client connections
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            notifyServerStarted();
            System.out.println("[Server] Started on port " + port);
            System.out.println("[Server] Shared directory: " + sharedDirectory);
            
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
        
        notifyServerStopped();
        System.out.println("[Server] Stopped");
    }

    /**
     * Remove a client handler from the list
     */
    public void removeClient(ClientHandler handler) {
        connectedClients.remove(handler);
        notifyClientDisconnected(handler.getClientAddress());
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
            } else {
                out.println("ERROR:Unknown command");
            }
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
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
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
    }

    /**
     * Simple test main method
     */
    public static void main(String[] args) {
        ServerMain server = new ServerMain(8080, "./shared_files");
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
