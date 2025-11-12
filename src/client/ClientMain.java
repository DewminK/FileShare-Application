package client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP Client for File Sharing Application
 * Handles connection to server and basic communication
 * Uses Socket for TCP connection and Stream Communication
 */
public class ClientMain {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String serverAddress;
    private int serverPort;
    private boolean connected;
    private List<ConnectionListener> listeners;

    public ClientMain(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.connected = false;
        this.listeners = new ArrayList<>();
    }

    /**
     * Establish connection with the server using TCP Socket
     */
    public boolean connect() {
        try {
            // Step 2: Create a Socket - TCP Client Socket
            socket = new Socket(serverAddress, serverPort);

            // Step 3: Set up output stream to send data to server
            out = new PrintWriter(socket.getOutputStream(), true);

            // Step 4: Set up input stream to receive data from server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            connected = true;
            System.out.println("Connected to server at " + serverAddress + ":" + serverPort);

            // Start listening for server messages in a separate thread
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

    /**
     * Start listening for server responses in a separate thread
     * Uses Anonymous Class for thread creation (as per lecture slides)
     */
    private void startListening() {
        Thread listenerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response;
                    while (connected && (response = in.readLine()) != null) {
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

    /**
     * Send a command to the server
     */
    public void sendCommand(String command) {
        if (connected && out != null) {
            out.println(command);
        } else {
            System.err.println("Not connected to server");
        }
    }

    /**
     * Request list of files from server
     */
    public void requestFileList() {
        sendCommand("LIST_FILES");
    }

    /**
     * Request to download a file
     */
    public void requestDownload(String filename) {
        sendCommand("DOWNLOAD:" + filename);
    }

    /**
     * Request to upload a file
     */
    public void requestUpload(String filename, long fileSize) {
        sendCommand("UPLOAD:" + filename + ":" + fileSize);
    }

    /**
     * Send chat message to server
     */
    public void sendChatMessage(String message) {
        sendCommand("CHAT:" + message);
    }

    /**
     * Step 5: Close the connection and free up system resources
     */
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

    // Listener pattern for UI updates
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

    /**
     * Simple test main method
     */
    public static void main(String[] args) {
        // Example usage
        ClientMain client = new ClientMain("localhost", 9090);

        if (client.connect()) {
            // Send a test message
            client.sendCommand("Hello, Server!");

            // Request file list
            client.requestFileList();

            // Keep running for a bit
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Disconnect
            client.disconnect();
        }
    }
}
