package chat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer - Multi-client Chat Server using TCP Sockets
 *
 * Networking Concepts:
 * - ServerSocket: Listens for incoming TCP connections on a specific port
 * - Socket: Represents individual client connections
 * - Multithreading: Each client runs on a separate thread for concurrent handling
 * - Broadcast Pattern: Messages from one client are sent to all connected clients
 *
 * How it works:
 * 1. Server creates a ServerSocket and listens on port 5000
 * 2. When a client connects, server spawns a new ClientHandler thread
 * 3. All active client writers are stored in a shared list
 * 4. When any client sends a message, it's broadcast to all other clients
 */
public class ChatServer {

    // Port number for server to listen on
    private static final int PORT = 5000;

    // Thread-safe list to store all connected client output streams
    // CopyOnWriteArrayList allows concurrent reads/writes without explicit synchronization
    private static final List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();

    // Counter to assign unique IDs to clients
    private static int clientCounter = 0;

    public static void main(String[] args) {
        System.out.println("=== Chat Server Starting ===");
        System.out.println("Listening on port " + PORT + "...\n");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Infinite loop to continuously accept new client connections
            while (true) {
                // accept() blocks until a client connects
                // Returns a new Socket object for communication with that client
                Socket clientSocket = serverSocket.accept();

                // Increment client counter and assign ID
                clientCounter++;
                int clientId = clientCounter;

                System.out.println("✓ New client connected: Client-" + clientId);
                System.out.println("  Address: " + clientSocket.getInetAddress().getHostAddress());
                System.out.println("  Total clients: " + clientCounter + "\n");

                // Create a new thread to handle this client
                // This allows the server to handle multiple clients concurrently
                ClientHandler handler = new ClientHandler(clientSocket, clientId);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Broadcast a message to all connected clients
     *
     * @param message The message to broadcast
     * @param senderId The ID of the client who sent the message (to exclude them if needed)
     */
    public static void broadcast(String message, int senderId) {
        System.out.println("Broadcasting: " + message);

        // Iterate through all connected client writers
        for (PrintWriter writer : clientWriters) {
            // Send message to each client
            writer.println(message);
        }
    }

    /**
     * Add a new client writer to the broadcast list
     */
    public static void addClient(PrintWriter writer) {
        clientWriters.add(writer);
    }

    /**
     * Remove a client writer from the broadcast list
     */
    public static void removeClient(PrintWriter writer) {
        clientWriters.remove(writer);
    }
}


/**
 * ClientHandler - Handles communication with a single client
 *
 * This class implements Runnable so it can be executed in a separate thread.
 * Each connected client has its own ClientHandler instance running concurrently.
 *
 * Responsibilities:
 * - Read messages from the client
 * - Broadcast received messages to all other clients
 * - Handle client disconnection
 */
class ClientHandler implements Runnable {

    private Socket socket;
    private int clientId;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;

    /**
     * Constructor
     *
     * @param socket The socket connection to the client
     * @param clientId Unique identifier for this client
     */
    public ClientHandler(Socket socket, int clientId) {
        this.socket = socket;
        this.clientId = clientId;
        this.clientName = "Client-" + clientId;
    }

    @Override
    public void run() {
        try {
            // Setup I/O streams for communication with this client

            // PrintWriter: For sending messages TO the client
            // auto-flush=true means messages are sent immediately
            out = new PrintWriter(socket.getOutputStream(), true);

            // BufferedReader: For receiving messages FROM the client
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Add this client to the broadcast list
            ChatServer.addClient(out);

            // Send welcome message to the newly connected client
            out.println("=== Welcome to the Chat Server ===");
            out.println("You are: " + clientName);
            out.println("Type your messages and press Enter to send.");
            out.println("===================================\n");

            // Notify all other clients about the new connection
            ChatServer.broadcast(">>> " + clientName + " joined the chat", clientId);

            // Main message receiving loop
            String message;
            while ((message = in.readLine()) != null) {
                // Check if client wants to quit
                if (message.trim().equalsIgnoreCase("/quit")) {
                    break;
                }

                // Check if client wants to change their name
                if (message.trim().startsWith("/name ")) {
                    String oldName = clientName;
                    clientName = message.trim().substring(6).trim();
                    ChatServer.broadcast(">>> " + oldName + " changed name to " + clientName, clientId);
                    out.println("Your name is now: " + clientName);
                    continue;
                }

                // Broadcast the message to all clients
                String formattedMessage = "[" + clientName + "]: " + message;
                ChatServer.broadcast(formattedMessage, clientId);
            }

        } catch (IOException e) {
            System.err.println("Error handling client " + clientName + ": " + e.getMessage());
        } finally {
            // Cleanup when client disconnects
            disconnect();
        }
    }

    /**
     * Clean up resources when client disconnects
     */
    private void disconnect() {
        try {
            // Remove this client from the broadcast list
            if (out != null) {
                ChatServer.removeClient(out);
            }

            // Notify other clients about the disconnection
            ChatServer.broadcast("<<< " + clientName + " left the chat", clientId);

            // Close all resources
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

            System.out.println("✗ Client disconnected: " + clientName + "\n");

        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }
}