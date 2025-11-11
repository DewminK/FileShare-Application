package chat;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * ChatClient - Client application for connecting to the chat server
 *
 * Networking Concepts:
 * - Socket: Establishes TCP connection to the server
 * - Multithreading: Uses two threads for concurrent send/receive operations
 *   1. Main thread: Reads user input and sends to server
 *   2. MessageReceiver thread: Continuously listens for incoming messages
 *
 * How it works:
 * 1. Client creates a Socket connection to server (localhost:5000)
 * 2. Spawns a separate thread to listen for incoming messages
 * 3. Main thread reads console input and sends messages to server
 * 4. Both threads run concurrently for full-duplex communication
 */
public class ChatClient {

    // Server connection details
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner scanner;

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.start();
    }

    /**
     * Start the chat client
     */
    public void start() {
        try {
            // Establish connection to the server
            System.out.println("Connecting to chat server at " + SERVER_ADDRESS + ":" + SERVER_PORT + "...");
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("✓ Connected to server!\n");

            // Setup I/O streams for communication

            // PrintWriter: For sending messages TO the server
            out = new PrintWriter(socket.getOutputStream(), true);

            // BufferedReader: For receiving messages FROM the server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Scanner: For reading user input from console
            scanner = new Scanner(System.in);

            // Start a separate thread to receive messages from server
            // This allows us to receive messages while also sending them
            MessageReceiver receiver = new MessageReceiver(in);
            Thread receiverThread = new Thread(receiver);
            receiverThread.setDaemon(true); // Daemon thread exits when main thread exits
            receiverThread.start();

            // Main thread handles sending messages
            sendMessages();

        } catch (UnknownHostException e) {
            System.err.println("Error: Could not find server at " + SERVER_ADDRESS);
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error: Could not connect to server");
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * Main loop for sending messages to the server
     * Runs in the main thread
     */
    private void sendMessages() {
        System.out.println("\n=== Chat Commands ===");
        System.out.println("/name <new_name> - Change your display name");
        System.out.println("/quit - Exit the chat");
        System.out.println("=====================\n");

        try {
            // Continuously read user input and send to server
            while (true) {
                String message = scanner.nextLine();

                // Check if user wants to quit
                if (message.trim().equalsIgnoreCase("/quit")) {
                    System.out.println("Disconnecting from chat...");
                    out.println("/quit");
                    break;
                }

                // Send message to server
                // The server will broadcast it to all connected clients
                out.println(message);
            }
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    /**
     * Clean up resources when disconnecting
     */
    private void cleanup() {
        try {
            if (scanner != null) scanner.close();
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();

            System.out.println("Disconnected from server. Goodbye!");

        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}


/**
 * MessageReceiver - Continuously listens for incoming messages from the server
 *
 * This class implements Runnable so it can run in a separate thread.
 * While the main thread handles user input and sending messages,
 * this thread continuously listens for messages from the server.
 *
 * This is an example of full-duplex communication - sending and receiving
 * happen simultaneously without blocking each other.
 */
class MessageReceiver implements Runnable {

    private BufferedReader in;

    /**
     * Constructor
     *
     * @param in BufferedReader for reading messages from server
     */
    public MessageReceiver(BufferedReader in) {
        this.in = in;
    }

    @Override
    public void run() {
        try {
            // Continuously read messages from the server
            String message;
            while ((message = in.readLine()) != null) {
                // Display the received message
                // Optional: Print notification for new messages
                System.out.println("[New message received]");
                System.out.println(message);
                System.out.println(); // Empty line for better readability
            }
        } catch (IOException e) {
            // Connection lost or server disconnected
            System.err.println("\n✗ Connection to server lost");
        }
    }
}
