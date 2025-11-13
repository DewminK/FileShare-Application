package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import server.Notifier;

public class ServerMain {
    private ServerSocket serverSocket;
    private boolean running;
    private int port;
    private List<ClientHandler> connectedClients;
    private List<ServerListener> listeners;
    private String sharedDirectory;
    private Notifier notifier;
    private AuthenticationHandler authHandler;

    public ServerMain(int port, String sharedDirectory) {
        this.port = port;
        this.sharedDirectory = sharedDirectory;
        this.running = false;
        this.connectedClients = new CopyOnWriteArrayList<>();
        this.listeners = new ArrayList<>();
        this.authHandler = new AuthenticationHandler();

        File dir = new File(sharedDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("[Server] Created shared directory: " + sharedDirectory);
        }

        this.notifier = new Notifier(true, 9876);
        System.out.println("[Server] Notifier initialized (UDP port 9876)");
        System.out.println("[Server] Authentication handler initialized");
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;

            notifier.start();
            System.out.println("[Server] Notifier service started");

            notifyServerStarted();
            System.out.println("[Server] Started on port " + port);
            System.out.println("[Server] Shared directory: " + sharedDirectory);

            notifier.notifyServerMessage("File Sharing Server started on port " + port);

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

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                System.out.println("[Server] New connection attempt from: " + clientAddress);

                ClientHandler handler = new ClientHandler(clientSocket, this);
                Thread handlerThread = new Thread(handler);
                handlerThread.start();

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Server] Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;

        for (ClientHandler handler : connectedClients) {
            handler.disconnect();
        }
        connectedClients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }

        if (notifier != null) {
            notifier.stop();
            System.out.println("[Server] Notifier service stopped");
        }

        notifyServerStopped();
        System.out.println("[Server] Stopped");
    }

    public void addAuthenticatedClient(ClientHandler handler) {
        connectedClients.add(handler);
        String userName = handler.getUserName();
        String clientAddr = handler.getClientAddress();
        notifyClientConnected(userName + " (" + clientAddr + ")");
        notifier.notifyClientConnected(userName);
        broadcastToClients("CLIENT_CONNECTED", "New user joined", userName);
        broadcastUserList();
    }

    public void removeClient(ClientHandler handler) {
        String userName = handler.getUserName();
        String clientAddr = handler.getClientAddress();
        connectedClients.remove(handler);
        notifyClientDisconnected(userName + " (" + clientAddr + ")");

        if (notifier != null) {
            notifier.notifyClientDisconnected(userName);
        }

        broadcastToClients("CLIENT_DISCONNECTED", "User disconnected", userName);
        broadcastUserList();

        for (ClientHandler h : connectedClients) {
            h.sendMessage("USER_LEFT:" + userName);
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

    public void broadcastToClients(String type, String message, String details) {
        String notification = String.format("NOTIFICATION:[%s]%s|%s", type, message, details);
        for (ClientHandler handler : connectedClients) {
            handler.sendNotification(notification);
        }
        System.out.println("[Server] Broadcasted to " + connectedClients.size() + " clients: " + notification);
    }

    public void broadcastChatMessage(String sender, String message) {
        String chatMessage = "CHAT:" + sender + ":" + message;
        for (ClientHandler handler : connectedClients) {
            handler.sendMessage(chatMessage);
        }
        System.out.println("[Server] Chat broadcast from " + sender + ": " + message);
    }

    private void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USER_LIST:");
        for (ClientHandler handler : connectedClients) {
            userList.append(handler.getUserName()).append("|");
        }
        String message = userList.toString();
        for (ClientHandler handler : connectedClients) {
            handler.sendMessage(message);
        }
    }

    public interface ServerListener {
        void onServerStarted(int port);
        void onServerStopped();
        void onClientConnected(String clientAddress);
        void onClientDisconnected(String clientAddress);
        void onFileTransfer(String clientAddress, String operation, String filename);
        void onServerError(String error);
    }

    public interface ChatListener extends ServerListener {
        void onChatMessage(String sender, String message);
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private ServerMain server;
        private PrintWriter out;
        private BufferedReader in;
        private String clientAddress;
        private long connectionStartTime;
        private boolean authenticated;
        private String userName;

        public ClientHandler(Socket socket, ServerMain server) {
            this.socket = socket;
            this.server = server;
            this.clientAddress = socket.getInetAddress().getHostAddress();
            this.connectionStartTime = System.currentTimeMillis();
            this.authenticated = false;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String command;
                while ((command = in.readLine()) != null) {
                    System.out.println("[Server] Command from " + clientAddress + ": " + command);

                    if (!authenticated) {
                        if (command.startsWith("LOGIN:") || command.startsWith("SIGNUP:")) {
                            handleAuthentication(command);
                        } else {
                            out.println("ERROR:Not authenticated");
                        }
                    } else {
                        handleCommand(command);
                    }
                }

            } catch (IOException e) {
                System.err.println("[Server] Client handler error: " + e.getMessage());
            } finally {
                if (authenticated) {
                    disconnect();
                }
            }
        }

        private void handleAuthentication(String command) {
            if (command.startsWith("LOGIN:")) {
                String[] parts = command.split(":");
                if (parts.length >= 3) {
                    String email = parts[1];
                    String password = parts[2];

                    String name = server.authHandler.authenticate(email, password);
                    if (name != null) {
                        authenticated = true;
                        userName = name;
                        out.println("LOGIN_SUCCESS:" + name);
                        server.addAuthenticatedClient(this);
                        out.println("CONNECTED:Welcome " + name);
                        System.out.println("[Server] Client authenticated: " + userName + " (" + clientAddress + ")");
                    } else {
                        out.println("LOGIN_FAILED:Invalid credentials");
                    }
                } else {
                    out.println("LOGIN_FAILED:Invalid command format");
                }
            } else if (command.startsWith("SIGNUP:")) {
                String[] parts = command.split(":");
                if (parts.length >= 4) {
                    String name = parts[1];
                    String email = parts[2];
                    String password = parts[3];

                    if (server.authHandler.signup(name, email, password)) {
                        out.println("SIGNUP_SUCCESS");
                    } else {
                        out.println("SIGNUP_FAILED:EMAIL_EXISTS");
                    }
                } else {
                    out.println("SIGNUP_FAILED:Invalid command format");
                }
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
            String message = command.substring(5);
            System.out.println("[Server] Chat from " + userName + ": " + message);
            server.notifyChatMessage(userName, message);
            server.broadcastChatMessage(userName, message);
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
            String[] parts = command.split(":");
            if (parts.length < 3) {
                out.println("ERROR:Invalid upload command");
                return;
            }

            String filename = parts[1];
            long fileSize = Long.parseLong(parts[2]);

            System.out.println("[Server] Receiving upload: " + filename + " (" + fileSize + " bytes)");
            server.notifyFileTransfer(clientAddress, "UPLOAD", filename);

            out.println("READY:Ready to receive file");

            try {
                File outputFile = new File(server.getSharedDirectory(), filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos);

                InputStream inputStream = socket.getInputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesReceived = 0;

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

                server.notifier.notifyNewFile(filename, clientAddress);
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
            out.println("FILE_SIZE:" + file.length());
        }

        public void disconnect() {
            cleanupConnection();
        }

        public String getClientAddress() {
            return userName != null ? userName : clientAddress;
        }

        public String getUserName() {
            return userName;
        }

        public long getConnectionTime() {
            return (System.currentTimeMillis() - connectionStartTime) / 1000;
        }

        public void sendNotification(String notification) {
            if (out != null) {
                out.println(notification);
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        private void cleanupConnection() {
            try {
                if (out != null)
                    out.close();
                if (in != null)
                    in.close();
                if (socket != null && !socket.isClosed())
                    socket.close();
            } catch (IOException e) {
            }

            if (authenticated) {
                server.removeClient(this);
                System.out.println("[Server] Client disconnected: " + userName);
            } else {
                System.out.println("[Server] Unauthenticated connection closed from: " + clientAddress);
            }
        }
    }

    public static void main(String[] args) {
        ServerMain server = new ServerMain(9090, "./shared_files");
        server.start();

        System.out.println("Server is running. Press Ctrl+C to stop.");
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
