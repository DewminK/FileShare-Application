package client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientMain {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String serverAddress;
    private int serverPort;
    private boolean connected;
    private List<ConnectionListener> listeners;
    private volatile boolean fileTransferInProgress = false;
    private final Object transferLock = new Object();

    public ClientMain(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.connected = false;
        this.listeners = new ArrayList<>();
    }

    public ClientMain(Socket authenticatedSocket) {
        try {
            this.socket = authenticatedSocket;
            this.serverAddress = authenticatedSocket.getInetAddress().getHostAddress();
            this.serverPort = authenticatedSocket.getPort();
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.connected = true;
            this.listeners = new ArrayList<>();
            System.out.println("Using authenticated connection to server");
            startListening();
            notifyConnectionStatus(true);
        } catch (IOException e) {
            System.err.println("Failed to initialize authenticated connection: " + e.getMessage());
            this.connected = false;
        }
    }

    public boolean connect() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            System.out.println("Connected to server at " + serverAddress + ":" + serverPort);
            startListening();
            notifyConnectionStatus(true);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            connected = false;
            notifyConnectionStatus(false);
            return false;
        }
    }

    private void startListening() {
        Thread listenerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response;
                    while (connected && (response = in.readLine()) != null) {
                        // Wait if file transfer is in progress
                        synchronized (transferLock) {
                            while (fileTransferInProgress) {
                                try {
                                    transferLock.wait();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                        }

                        System.out.println("Server says: " + response);
                        notifyMessageReceived(response);
                    }
                } catch (IOException e) {
                    if (connected) {
                        System.err.println("Connection lost: " + e.getMessage());
                        disconnect();
                    }
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendCommand(String command) {
        if (connected && out != null) {
            out.println(command);
        } else {
            System.err.println("Not connected to server");
        }
    }

    public void requestFileList() {
        sendCommand("LIST_FILES");
    }

    public void requestDownload(String filename) {
        sendCommand("DOWNLOAD:" + filename);
    }

    public void requestUpload(String filename, long fileSize) {
        sendCommand("UPLOAD:" + filename + ":" + fileSize);
    }

    public void sendChatMessage(String message) {
        sendCommand("CHAT:" + message);
    }

    public void disconnect() {
        try {
            connected = false;
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (socket != null)
                socket.close();
            System.out.println("Disconnected from server");
            notifyConnectionStatus(false);
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public Socket getSocket() {
        return socket;
    }

    /**
     * Get the raw InputStream from the socket for file transfers.
     * WARNING: This bypasses the BufferedReader used by the listener thread.
     * Use with caution - only for binary file transfers where you know exactly
     * how many bytes to read.
     */
    public InputStream getRawInputStream() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return socket.getInputStream();
        }
        throw new IOException("Socket is not connected");
    }

    /**
     * Signal that a file transfer is starting - pauses the listener thread
     * to prevent it from consuming binary file data
     */
    public void beginFileTransfer() {
        synchronized (transferLock) {
            fileTransferInProgress = true;
        }
        System.out.println("[ClientMain] File transfer mode activated - listener paused");
    }

    /**
     * Signal that a file transfer has ended - resumes the listener thread
     */
    public void endFileTransfer() {
        synchronized (transferLock) {
            fileTransferInProgress = false;
            transferLock.notifyAll();
        }
        System.out.println("[ClientMain] File transfer mode deactivated - listener resumed");
    }

    public void addConnectionListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    private void notifyConnectionStatus(boolean status) {
        for (ConnectionListener listener : listeners) {
            listener.onConnectionStatusChanged(status);
        }
    }

    private void notifyMessageReceived(String message) {
        for (ConnectionListener listener : listeners) {
            listener.onMessageReceived(message);
        }
    }

    public interface ConnectionListener {
        void onConnectionStatusChanged(boolean connected);

        void onMessageReceived(String message);
    }

    public static void main(String[] args) {
        ClientMain client = new ClientMain("localhost", 9090);

        if (client.connect()) {
            client.sendCommand("Hello, Server!");

            client.requestFileList();

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            client.disconnect();
        }
    }
}
