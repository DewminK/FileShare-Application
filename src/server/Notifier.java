package server;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Member 5 - Broadcaster / Notifier
 *
 * Implements a notification system that informs all connected clients in real-time
 * about new files or file updates. Ensures that every client stays updated with the
 * latest shared files.
 *
 * Networking Concepts Used:
 * - Java NIO (Channels, Buffers, Selectors) – Non-blocking I/O for multiple client notifications
 * - UDP Broadcasting (optional) – Sends quick update packets to all clients on the network
 * - Multithreading – Runs notification broadcasts on a separate thread
 * - HTTP Client Communication (optional) – Uses HttpURLConnection to send update requests
 *
 * @author Member 5 - Broadcaster / Notifier
 */
public class Notifier {

    // NIO Selector for managing multiple notification channels
    private Selector selector;

    // List of connected client channels for notifications
    private final List<SocketChannel> clientChannels;
    private final Map<SocketChannel, ClientNotificationInfo> clientInfoMap;

    // UDP broadcast socket (optional feature)
    private DatagramSocket udpSocket;
    private InetAddress broadcastAddress;
    private int udpPort;
    private boolean udpEnabled;

    // Thread management
    private Thread notificationThread;
    private volatile boolean running;

    // Message queue for notifications
    private final BlockingQueue<NotificationMessage> messageQueue;

    // Statistics
    private long totalNotificationsSent;
    private long totalBroadcastsSent;

    // Configuration
    private static final int MESSAGE_QUEUE_SIZE = 1000;
    private static final int NOTIFICATION_BUFFER_SIZE = 1024;
    private static final long SELECTOR_TIMEOUT = 1000; // 1 second

    /**
     * Notification message types
     */
    public enum NotificationType {
        NEW_FILE,           // New file uploaded
        FILE_UPDATED,       // Existing file modified
        FILE_DELETED,       // File deleted
        SERVER_MESSAGE,     // General server message
        CLIENT_CONNECTED,   // New client connected
        CLIENT_DISCONNECTED // Client disconnected
    }

    /**
     * Constructor - Initializes the Notifier with NIO components
     *
     * @param udpEnabled Whether to enable UDP broadcasting
     * @param udpPort Port for UDP broadcasts
     */
    public Notifier(boolean udpEnabled, int udpPort) {
        this.clientChannels = new CopyOnWriteArrayList<>();
        this.clientInfoMap = new ConcurrentHashMap<>();
        this.messageQueue = new LinkedBlockingQueue<>(MESSAGE_QUEUE_SIZE);
        this.udpEnabled = udpEnabled;
        this.udpPort = udpPort;
        this.running = false;
        this.totalNotificationsSent = 0;
        this.totalBroadcastsSent = 0;

        System.out.println("[Notifier] Broadcaster/Notifier initialized");
        System.out.println("[Notifier] UDP Broadcasting: " + (udpEnabled ? "ENABLED" : "DISABLED"));
    }

    /**
     * Start the notifier service
     * Initializes NIO Selector and starts notification thread
     */
    public void start() throws IOException {
        if (running) {
            System.out.println("[Notifier] Already running");
            return;
        }

        // Initialize NIO Selector for non-blocking I/O
        selector = Selector.open();
        System.out.println("[Notifier] Selector opened for non-blocking I/O");

        // Initialize UDP broadcasting if enabled
        if (udpEnabled) {
            initializeUDPBroadcast();
        }

        running = true;

        // Start notification thread using Multithreading
        notificationThread = new Thread(new NotificationRunner());
        notificationThread.setName("Notifier-Thread");
        notificationThread.setDaemon(true);
        notificationThread.start();

        System.out.println("[Notifier] Notification service started");
    }

    /**
     * Initialize UDP broadcast socket
     * Uses DatagramSocket for UDP communication
     */
    private void initializeUDPBroadcast() {
        try {
            udpSocket = new DatagramSocket();
            udpSocket.setBroadcast(true);

            // Get broadcast address for the network
            try {
                broadcastAddress = InetAddress.getByName("255.255.255.255");
            } catch (UnknownHostException e) {
                System.err.println("[Notifier] Failed to resolve broadcast address, using localhost");
                broadcastAddress = InetAddress.getLocalHost();
            }

            System.out.println("[Notifier] UDP broadcast initialized on port " + udpPort);
            System.out.println("[Notifier] Broadcast address: " + broadcastAddress.getHostAddress());
        } catch (IOException e) {
            System.err.println("[Notifier] Failed to initialize UDP broadcast: " + e.getMessage());
            udpEnabled = false;
        }
    }

    /**
     * Register a client channel for notifications
     * Uses Java NIO SocketChannel for non-blocking communication
     *
     * @param channel The client's SocketChannel
     * @param clientAddress Client's address for identification
     */
    public void registerClient(SocketChannel channel, String clientAddress) {
        try {
            // Configure channel for non-blocking mode
            channel.configureBlocking(false);

            // Register channel with selector for write operations
            channel.register(selector, SelectionKey.OP_WRITE);

            // Add to client list
            clientChannels.add(channel);

            // Store client info
            ClientNotificationInfo info = new ClientNotificationInfo(clientAddress);
            clientInfoMap.put(channel, info);

            System.out.println("[Notifier] Registered client for notifications: " + clientAddress);
            System.out.println("[Notifier] Total registered clients: " + clientChannels.size());

            // Notify other clients about new connection
            broadcastNotification(NotificationType.CLIENT_CONNECTED,
                    "New client connected: " + clientAddress,
                    clientAddress);

        } catch (IOException e) {
            System.err.println("[Notifier] Failed to register client: " + e.getMessage());
        }
    }

    /**
     * Unregister a client channel
     *
     * @param channel The client's SocketChannel to remove
     */
    public void unregisterClient(SocketChannel channel) {
        ClientNotificationInfo info = clientInfoMap.get(channel);
        String clientAddress = (info != null) ? info.getClientAddress() : "Unknown";

        clientChannels.remove(channel);
        clientInfoMap.remove(channel);

        try {
            // Cancel selector key
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                key.cancel();
            }

            // Close channel
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            System.err.println("[Notifier] Error closing client channel: " + e.getMessage());
        }

        System.out.println("[Notifier] Unregistered client: " + clientAddress);
        System.out.println("[Notifier] Total registered clients: " + clientChannels.size());

        // Notify other clients about disconnection
        broadcastNotification(NotificationType.CLIENT_DISCONNECTED,
                "Client disconnected: " + clientAddress,
                clientAddress);
    }

    /**
     * Broadcast a notification to all connected clients
     * This is the main method for sending notifications
     *
     * @param type Type of notification
     * @param message Notification message
     * @param details Additional details
     */
    public void broadcastNotification(NotificationType type, String message, String details) {
        NotificationMessage notification = new NotificationMessage(type, message, details);

        try {
            // Add to message queue
            boolean added = messageQueue.offer(notification, 1, TimeUnit.SECONDS);

            if (added) {
                System.out.println("[Notifier] Queued notification: [" + type + "] " + message);

                // Wake up selector
                if (selector != null) {
                    selector.wakeup();
                }

                // Also send UDP broadcast if enabled
                if (udpEnabled) {
                    sendUDPBroadcast(notification);
                }
            } else {
                System.err.println("[Notifier] Message queue full, notification dropped");
            }

        } catch (InterruptedException e) {
            System.err.println("[Notifier] Interrupted while queueing notification");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Send UDP broadcast packet to all clients on network
     * Uses DatagramSocket and DatagramPacket for UDP communication
     *
     * @param notification The notification to broadcast
     */
    private void sendUDPBroadcast(NotificationMessage notification) {
        if (!udpEnabled || udpSocket == null) {
            return;
        }

        try {
            // Serialize notification to bytes
            String message = String.format("[%s] %s | %s",
                    notification.getType(),
                    notification.getMessage(),
                    notification.getDetails());
            byte[] data = message.getBytes();

            // Create UDP packet
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    broadcastAddress,
                    udpPort
            );

            // Send broadcast
            udpSocket.send(packet);
            totalBroadcastsSent++;

            System.out.println("[Notifier] UDP broadcast sent: " + message);

        } catch (IOException e) {
            System.err.println("[Notifier] Failed to send UDP broadcast: " + e.getMessage());
        }
    }

    /**
     * Notification runner - runs in separate thread
     * Uses Java NIO Selector for efficient event-driven I/O
     */
    private class NotificationRunner implements Runnable {
        @Override
        public void run() {
            System.out.println("[Notifier] Notification thread started");

            while (running) {
                try {
                    // Check for pending notifications
                    NotificationMessage notification = messageQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (notification != null) {
                        // Send notification to all registered clients
                        sendNotificationToClients(notification);
                    }

                    // Use Selector to handle non-blocking I/O
                    // Check which channels are ready for writing
                    if (selector.isOpen()) {
                        int readyChannels = selector.select(SELECTOR_TIMEOUT);

                        if (readyChannels > 0) {
                            // Process ready channels
                            Set<SelectionKey> selectedKeys = selector.selectedKeys();
                            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                            while (keyIterator.hasNext()) {
                                SelectionKey key = keyIterator.next();
                                keyIterator.remove();

                                if (key.isValid() && key.isWritable()) {
                                    // Channel is ready for writing
                                    // This is where we could send pending data if needed
                                }
                            }
                        }
                    }

                } catch (InterruptedException e) {
                    if (running) {
                        System.err.println("[Notifier] Notification thread interrupted");
                    }
                    break;
                } catch (IOException e) {
                    System.err.println("[Notifier] I/O error in notification thread: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("[Notifier] Unexpected error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("[Notifier] Notification thread stopped");
        }
    }

    /**
     * Send notification to all registered clients using NIO Channels and Buffers
     *
     * @param notification The notification to send
     */
    private void sendNotificationToClients(NotificationMessage notification) {
        if (clientChannels.isEmpty()) {
            System.out.println("[Notifier] No clients registered, skipping notification");
            return;
        }

        // Format notification message
        String message = String.format("NOTIFICATION:[%s]%s|%s\n",
                notification.getType(),
                notification.getMessage(),
                notification.getDetails());

        // Create ByteBuffer for efficient data transfer
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

        // Send to all registered clients
        int successCount = 0;
        int failureCount = 0;

        for (SocketChannel channel : clientChannels) {
            try {
                if (channel.isOpen() && channel.isConnected()) {
                    // Reset buffer position for each client
                    buffer.rewind();

                    // Write to channel using non-blocking I/O
                    int bytesWritten = 0;
                    while (buffer.hasRemaining()) {
                        bytesWritten += channel.write(buffer);
                    }

                    if (bytesWritten > 0) {
                        successCount++;

                        // Update client statistics
                        ClientNotificationInfo info = clientInfoMap.get(channel);
                        if (info != null) {
                            info.incrementNotificationsReceived();
                        }
                    }
                } else {
                    failureCount++;
                    // Channel is closed, remove it
                    unregisterClient(channel);
                }

            } catch (IOException e) {
                failureCount++;
                System.err.println("[Notifier] Failed to send notification to client: " + e.getMessage());
                // Remove failed channel
                unregisterClient(channel);
            }
        }

        totalNotificationsSent += successCount;

        System.out.println("[Notifier] Notification sent: [" + notification.getType() + "] "
                + notification.getMessage());
        System.out.println("[Notifier] Success: " + successCount + ", Failed: " + failureCount);
    }

    /**
     * Stop the notifier service
     */
    public void stop() {
        System.out.println("[Notifier] Stopping notification service...");

        running = false;

        // Interrupt notification thread
        if (notificationThread != null) {
            notificationThread.interrupt();
            try {
                notificationThread.join(5000);
            } catch (InterruptedException e) {
                System.err.println("[Notifier] Error waiting for thread to stop");
            }
        }

        // Close all client channels
        for (SocketChannel channel : clientChannels) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                System.err.println("[Notifier] Error closing client channel: " + e.getMessage());
            }
        }
        clientChannels.clear();
        clientInfoMap.clear();

        // Close selector
        if (selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException e) {
                System.err.println("[Notifier] Error closing selector: " + e.getMessage());
            }
        }

        // Close UDP socket
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        System.out.println("[Notifier] Notification service stopped");
        System.out.println("[Notifier] Total notifications sent: " + totalNotificationsSent);
        System.out.println("[Notifier] Total UDP broadcasts sent: " + totalBroadcastsSent);
    }

    /**
     * Get notifier statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("running", running);
        stats.put("registeredClients", clientChannels.size());
        stats.put("totalNotificationsSent", totalNotificationsSent);
        stats.put("totalBroadcastsSent", totalBroadcastsSent);
        stats.put("queuedMessages", messageQueue.size());
        stats.put("udpEnabled", udpEnabled);
        return stats;
    }

    /**
     * Get list of registered clients
     */
    public List<String> getRegisteredClients() {
        List<String> clients = new ArrayList<>();
        for (Map.Entry<SocketChannel, ClientNotificationInfo> entry : clientInfoMap.entrySet()) {
            ClientNotificationInfo info = entry.getValue();
            clients.add(info.getClientAddress() + " (Notifications: " + info.getNotificationsReceived() + ")");
        }
        return clients;
    }

    // ==================== Convenience Methods ====================

    public void notifyNewFile(String filename, String uploader) {
        broadcastNotification(NotificationType.NEW_FILE,
                "New file available: " + filename,
                "Uploaded by: " + uploader);
    }

    public void notifyFileUpdated(String filename, String updater) {
        broadcastNotification(NotificationType.FILE_UPDATED,
                "File updated: " + filename,
                "Updated by: " + updater);
    }

    public void notifyFileDeleted(String filename, String deleter) {
        broadcastNotification(NotificationType.FILE_DELETED,
                "File deleted: " + filename,
                "Deleted by: " + deleter);
    }

    public void notifyServerMessage(String message) {
        broadcastNotification(NotificationType.SERVER_MESSAGE, message, "");
    }

    // ==================== Helper Classes ====================

    /**
     * Notification message class
     */
    private static class NotificationMessage {
        private final NotificationType type;
        private final String message;
        private final String details;
        private final long timestamp;

        public NotificationMessage(NotificationType type, String message, String details) {
            this.type = type;
            this.message = message;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }

        public NotificationType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public String getDetails() {
            return details;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Client notification information
     */
    private static class ClientNotificationInfo {
        private final String clientAddress;
        private final long registeredTime;
        private long notificationsReceived;

        public ClientNotificationInfo(String clientAddress) {
            this.clientAddress = clientAddress;
            this.registeredTime = System.currentTimeMillis();
            this.notificationsReceived = 0;
        }

        public String getClientAddress() {
            return clientAddress;
        }

        public long getRegisteredTime() {
            return registeredTime;
        }

        public long getNotificationsReceived() {
            return notificationsReceived;
        }

        public void incrementNotificationsReceived() {
            notificationsReceived++;
        }
    }

    // ==================== Test / Demo Main Method ====================

    /**
     * Demo/Test method for the Notifier
     */
    public static void main(String[] args) {
        System.out.println("=== Broadcaster/Notifier Demo ===\n");

        try {
            // Create notifier with UDP broadcasting enabled
            Notifier notifier = new Notifier(true, 9876);

            // Start the notifier service
            notifier.start();

            System.out.println("\n--- Simulating client connections ---");

            // Simulate registering some clients (in real scenario, these would be actual socket channels)
            // For demo purposes, we'll just show the broadcasting capability

            Thread.sleep(1000);

            // Send various notifications
            System.out.println("\n--- Broadcasting notifications ---");

            notifier.notifyNewFile("document.pdf", "Alice");
            Thread.sleep(500);

            notifier.notifyFileUpdated("spreadsheet.xlsx", "Bob");
            Thread.sleep(500);

            notifier.notifyServerMessage("Server maintenance scheduled for tonight");
            Thread.sleep(500);

            notifier.notifyFileDeleted("old_file.txt", "Charlie");
            Thread.sleep(500);

            // Show statistics
            System.out.println("\n--- Notifier Statistics ---");
            Map<String, Object> stats = notifier.getStatistics();
            stats.forEach((key, value) -> System.out.println(key + ": " + value));

            // Wait a bit
            System.out.println("\n--- Running for 5 seconds ---");
            Thread.sleep(5000);

            // Stop notifier
            System.out.println("\n--- Stopping notifier ---");
            notifier.stop();

            System.out.println("\n=== Demo Complete ===");

        } catch (Exception e) {
            System.err.println("Error in demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
