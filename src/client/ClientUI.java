package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * JavaFX-based User Interface for File Sharing Client
 * Provides a modern GUI for connecting to server, viewing files, uploading and
 * downloading
 */
public class ClientUI extends Application {

    private ClientMain client;
    private FileTransferHandler fileHandler;

    // Member 5: UDP Broadcast Listener
    private Thread udpListenerThread;
    private volatile boolean listeningForBroadcasts = false;
    private static final int UDP_PORT = 9876;

    // UI Components
    private TextField serverAddressField;
    private TextField serverPortField;
    private Button connectButton;
    private Button disconnectButton;
    private Label statusLabel;

    private TableView<FileInfo> fileTable;
    private ObservableList<FileInfo> fileList;

    private TextArea logArea;
    private ProgressBar transferProgressBar;
    private Label progressLabel;

    private Button uploadButton;
    private Button downloadButton;
    private Button refreshButton;

    // Chat components
    private TextArea chatArea;
    private TextField chatInputField;
    private Button chatSendButton;
    private ListView<String> onlineUsersList;
    private ObservableList<String> onlineUsers;

    // User info fields
    private String userEmail;
    private String userName;
    private Socket authenticatedSocket;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("File Sharing Client - Network Programming");

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // Top: Connection Panel
        VBox topPanel = createConnectionPanel();
        mainLayout.setTop(topPanel);

        // Center: Split view - Files on left, Chat on right
        SplitPane centerPanel = createMainSplitPanel();
        mainLayout.setCenter(centerPanel);

        // Bottom: Log and Progress
        VBox bottomPanel = createLogPanel();
        mainLayout.setBottom(bottomPanel);

        // Create scene and apply CSS
        Scene scene = new Scene(mainLayout, 900, 700);

        // Try to load CSS, but don't fail if it's not found
        try {
            var cssUrl = getClass().getResource("/resources/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.out.println("CSS file not found, using default styles");
        }

        primaryStage.setScene(scene);
        primaryStage.show();

        // Start UDP broadcast listener for Member 5 notifications
        startUDPListener();

        // If we have an authenticated socket, use it to connect
        if (authenticatedSocket != null) {
            connectWithAuthenticatedSocket();
        }

        // Handle window close
        primaryStage.setOnCloseRequest(event -> {
            stopUDPListener();
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            Platform.exit();
        });
    }

    /**
     * Create main split panel with files on left and chat on right
     */
    private SplitPane createMainSplitPanel() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.6); // 60% for files, 40% for chat

        // Left: File Panel
        VBox filePanel = createFilePanel();

        // Right: Chat Panel
        VBox chatPanel = createChatPanel();

        splitPane.getItems().addAll(filePanel, chatPanel);
        return splitPane;
    }

    /**
     * Create chat panel with messages and online users
     */
    private VBox createChatPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label titleLabel = new Label("💬 Chat Room");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");

        // Split for chat area and online users
        SplitPane chatSplit = new SplitPane();
        chatSplit.setDividerPositions(0.7);
        VBox.setVgrow(chatSplit, Priority.ALWAYS);

        // Chat messages area
        VBox chatMessagesBox = new VBox(5);
        Label chatLabel = new Label("Messages");
        chatLabel.setStyle("-fx-font-weight: bold;");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-control-inner-background: #f5f5f5; -fx-font-family: 'Segoe UI';");
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        chatMessagesBox.getChildren().addAll(chatLabel, chatArea);

        // Online users panel
        VBox usersBox = new VBox(5);
        Label usersLabel = new Label("👥 Online");
        usersLabel.setStyle("-fx-font-weight: bold;");

        onlineUsers = FXCollections.observableArrayList();
        onlineUsersList = new ListView<>(onlineUsers);
        onlineUsersList.setStyle("-fx-control-inner-background: #e3f2fd;");
        VBox.setVgrow(onlineUsersList, Priority.ALWAYS);

        usersBox.getChildren().addAll(usersLabel, onlineUsersList);

        chatSplit.getItems().addAll(chatMessagesBox, usersBox);

        // Chat input area
        HBox chatInputBox = new HBox(10);
        chatInputBox.setAlignment(Pos.CENTER);

        chatInputField = new TextField();
        chatInputField.setPromptText("Type your message...");
        chatInputField.setDisable(true);
        HBox.setHgrow(chatInputField, Priority.ALWAYS);
        chatInputField.setOnAction(e -> sendChatMessage());

        chatSendButton = new Button("Send");
        chatSendButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        chatSendButton.setDisable(true);
        chatSendButton.setOnAction(e -> sendChatMessage());

        chatInputBox.getChildren().addAll(chatInputField, chatSendButton);

        panel.getChildren().addAll(titleLabel, chatSplit, chatInputBox);
        return panel;
    }

    /**
     * Send chat message to server
     */
    private void sendChatMessage() {
        String message = chatInputField.getText().trim();
        if (message.isEmpty() || client == null || !client.isConnected()) {
            return;
        }

        try {
            // Send chat message to server
            client.sendChatMessage(message);

            // Display in local chat
            appendChatMessage("You", message);
            chatInputField.clear();
        } catch (Exception e) {
            log("Error sending chat message: " + e.getMessage());
        }
    }

    /**
     * Append chat message to chat area
     */
    private void appendChatMessage(String sender, String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            chatArea.appendText(String.format("[%s] %s: %s\n", timestamp, sender, message));
        });
    }

    /**
     * Connection panel with server address, port, and connect/disconnect
     * buttons
     */
    private VBox createConnectionPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1;");

        Label titleLabel = new Label("Server Connection");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox connectionBox = new HBox(10);
        connectionBox.setAlignment(Pos.CENTER_LEFT);

        Label addressLabel = new Label("Server Address:");
        serverAddressField = new TextField("localhost");
        serverAddressField.setPrefWidth(150);

        Label portLabel = new Label("Port:");
        serverPortField = new TextField("9090");
        serverPortField.setPrefWidth(80);

        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> connectToServer());

        disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        disconnectButton.setDisable(true);
        disconnectButton.setOnAction(e -> disconnectFromServer());

        statusLabel = new Label("Status: Not Connected");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff0000;");

        connectionBox.getChildren().addAll(addressLabel, serverAddressField, portLabel,
                serverPortField, connectButton, disconnectButton,
                new Region(), statusLabel);
        HBox.setHgrow(connectionBox.getChildren().get(connectionBox.getChildren().size() - 2), Priority.ALWAYS);

        panel.getChildren().addAll(titleLabel, connectionBox);
        return panel;
    }

    /**
     * Create file panel with table view and action buttons
     */
    private VBox createFilePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label titleLabel = new Label("Available Files on Server");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Create table
        fileTable = new TableView<>();
        fileList = FXCollections.observableArrayList();
        fileTable.setItems(fileList);

        TableColumn<FileInfo, String> nameColumn = new TableColumn<>("File Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(400);

        TableColumn<FileInfo, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setPrefWidth(150);

        TableColumn<FileInfo, String> dateColumn = new TableColumn<>("Date Modified");
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("dateModified"));
        dateColumn.setPrefWidth(200);

        fileTable.getColumns().addAll(nameColumn, sizeColumn, dateColumn);
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        uploadButton = new Button("Upload File");
        uploadButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        uploadButton.setDisable(true);
        uploadButton.setOnAction(e -> uploadFile());

        downloadButton = new Button("Download Selected");
        downloadButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        downloadButton.setDisable(true);
        downloadButton.setOnAction(e -> downloadFile());

        refreshButton = new Button("Refresh List");
        refreshButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshButton.setDisable(true);
        refreshButton.setOnAction(e -> refreshFileList());

        // Test notification button (for debugging)
        Button testNotifButton = new Button("🔔 Test Notification");
        testNotifButton.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-weight: bold;");
        testNotifButton.setOnAction(e -> {
            showNotificationAlert("Test Notification 🔔",
                    "This is a test notification to verify the system is working!\nTimestamp: " +
                            java.time.LocalTime.now(),
                    Alert.AlertType.INFORMATION);
        });

        buttonBox.getChildren().addAll(uploadButton, downloadButton, refreshButton, testNotifButton);

        panel.getChildren().addAll(titleLabel, fileTable, buttonBox);
        return panel;
    }

    /**
     * Create log and progress panel
     */
    private VBox createLogPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefHeight(200);

        Label titleLabel = new Label("Activity Log");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px;");

        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER_LEFT);

        progressLabel = new Label("Ready");
        transferProgressBar = new ProgressBar(0);
        transferProgressBar.setPrefWidth(300);

        progressBox.getChildren().addAll(new Label("Progress:"), transferProgressBar, progressLabel);

        panel.getChildren().addAll(titleLabel, logArea, progressBox);
        return panel;
    }

    /**
     * Connect to server using TCP Socket
     */
    private void connectToServer() {
        String address = serverAddressField.getText();
        int port;

        try {
            port = Integer.parseInt(serverPortField.getText());
        } catch (NumberFormatException e) {
            showAlert("Invalid Port", "Please enter a valid port number.");
            return;
        }

        // Create client and file handler
        client = new ClientMain(address, port);
        fileHandler = new FileTransferHandler(client, "./downloads");

        // Add connection listener
        client.addConnectionListener(new ClientMain.ConnectionListener() {
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                Platform.runLater(() -> updateConnectionStatus(connected));
            }

            @Override
            public void onMessageReceived(String message) {
                Platform.runLater(() -> {
                    // Filter out binary data before logging to UI
                    // Only log if it's a legitimate protocol message
                    if (shouldLogMessage(message)) {
                        log("Server: " + message);
                    }
                    parseServerMessage(message);
                });
            }
        });

        // Connect in a separate thread using Anonymous Class
        Thread connectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = client.connect();
                if (success) {
                    Platform.runLater(() -> {
                        log("Connected to " + address + ":" + port);
                        refreshFileList();
                    });
                } else {
                    Platform.runLater(() -> {
                        showAlert("Connection Failed", "Could not connect to server at " + address + ":" + port);
                        log("Connection failed!");
                    });
                }
            }
        });
        connectThread.start();
    }

    /**
     * Disconnect from server
     */
    private void disconnectFromServer() {
        if (client != null) {
            client.disconnect();
            log("Disconnected from server");
        }
    }

    /**
     * Update UI based on connection status
     */
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("Status: Connected");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #00ff00;");
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            serverAddressField.setDisable(true);
            serverPortField.setDisable(true);
            uploadButton.setDisable(false);
            downloadButton.setDisable(false);
            refreshButton.setDisable(false);

            // Enable chat components
            chatInputField.setDisable(false);
            chatSendButton.setDisable(false);
            appendChatMessage("System", "Connected to chat room!");

        } else {
            statusLabel.setText("Status: Not Connected");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff0000;");
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            serverAddressField.setDisable(false);
            serverPortField.setDisable(false);
            uploadButton.setDisable(true);
            downloadButton.setDisable(true);
            refreshButton.setDisable(true);

            // Disable chat components
            chatInputField.setDisable(true);
            chatSendButton.setDisable(true);
            chatArea.clear();
            onlineUsers.clear();
        }
    }

    /**
     * Upload file using FileTransferHandler with multithreading
     */
    private void uploadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            log("Uploading: " + file.getName() + " (" + formatFileSize(file.length()) + ")");

            fileHandler.uploadFileNIO(file, new FileTransferHandler.FileTransferListener() {
                @Override
                public void onTransferStarted(String filename, long fileSize) {
                    Platform.runLater(() -> {
                        progressLabel.setText("Uploading: " + filename);
                        transferProgressBar.setProgress(0);
                    });
                }

                @Override
                public void onProgressUpdate(String filename, int progress) {
                    Platform.runLater(() -> {
                        transferProgressBar.setProgress(progress / 100.0);
                        progressLabel.setText("Uploading: " + filename + " - " + progress + "%");
                    });
                }

                @Override
                public void onTransferCompleted(String filename, boolean isUpload) {
                    Platform.runLater(() -> {
                        log("Upload completed: " + filename);
                        progressLabel.setText("Upload completed!");
                        transferProgressBar.setProgress(1);
                        refreshFileList();
                    });
                }

                @Override
                public void onTransferFailed(String filename, String error) {
                    Platform.runLater(() -> {
                        log("Upload failed: " + filename + " - " + error);
                        progressLabel.setText("Upload failed!");
                        showAlert("Upload Failed", "Failed to upload " + filename + ": " + error);
                    });
                }
            });
        }
    }

    /**
     * Download selected file using FileTransferHandler with multithreading
     */
    private void downloadFile() {
        FileInfo selectedFile = fileTable.getSelectionModel().getSelectedItem();

        if (selectedFile == null) {
            showAlert("No File Selected", "Please select a file to download.");
            return;
        }

        log("Downloading: " + selectedFile.getName());

        fileHandler.downloadFileNIO(selectedFile.getName(), selectedFile.getSizeBytes(),
                new FileTransferHandler.FileTransferListener() {
                    @Override
                    public void onTransferStarted(String filename, long fileSize) {
                        Platform.runLater(() -> {
                            progressLabel.setText("Downloading: " + filename);
                            transferProgressBar.setProgress(0);
                        });
                    }

                    @Override
                    public void onProgressUpdate(String filename, int progress) {
                        Platform.runLater(() -> {
                            transferProgressBar.setProgress(progress / 100.0);
                            progressLabel.setText("Downloading: " + filename + " - " + progress + "%");
                        });
                    }

                    @Override
                    public void onTransferCompleted(String filename, boolean isUpload) {
                        Platform.runLater(() -> {
                            log("Download completed: " + filename);
                            progressLabel.setText("Download completed!");
                            transferProgressBar.setProgress(1);
                            showAlert("Download Complete", filename + " has been downloaded to ./downloads/");
                        });
                    }

                    @Override
                    public void onTransferFailed(String filename, String error) {
                        Platform.runLater(() -> {
                            log("Download failed: " + filename + " - " + error);
                            progressLabel.setText("Download failed!");
                            showAlert("Download Failed", "Failed to download " + filename + ": " + error);
                        });
                    }
                });
    }

    /**
     * Refresh file list from server
     */
    private void refreshFileList() {
        if (client != null && client.isConnected()) {
            client.requestFileList();
            log("Requesting file list from server...");
        }
    }

    /**
     * Parse server messages and update UI accordingly
     */
    private void parseServerMessage(String message) {
        if (message.startsWith("FILE_LIST:")) {
            // Parse file list: FILE_LIST:file1.txt:1024:date1|file2.pdf:2048:date2
            String[] files = message.substring(10).split("\\|");
            fileList.clear();

            for (String fileInfo : files) {
                if (!fileInfo.isEmpty()) {
                    String[] parts = fileInfo.split(":");
                    if (parts.length >= 3) {
                        long size = Long.parseLong(parts[1]);
                        fileList.add(new FileInfo(parts[0], size, parts[2]));
                    }
                }
            }
            log("File list updated: " + fileList.size() + " files available");
        } else if (message.startsWith("NOTIFICATION:")) {
            // Handle notifications from server (Member 5 - Notifier)
            // Format: NOTIFICATION:[TYPE]MESSAGE|DETAILS
            System.out.println("DEBUG: Received notification: " + message);
            log("📢 Notification received from server");
            handleNotification(message.substring(13)); // Remove "NOTIFICATION:" prefix
        } else if (message.startsWith("CHAT:")) {
            // Handle chat messages: CHAT:username:message
            String chatContent = message.substring(5);
            int separatorIndex = chatContent.indexOf(":");
            if (separatorIndex > 0) {
                String username = chatContent.substring(0, separatorIndex);
                String chatMsg = chatContent.substring(separatorIndex + 1);
                appendChatMessage(username, chatMsg);
            }
        } else if (message.startsWith("USER_LIST:")) {
            // Handle online users list: USER_LIST:user1|user2|user3
            String userListStr = message.substring(10);
            onlineUsers.clear();
            if (!userListStr.isEmpty()) {
                String[] users = userListStr.split("\\|");
                onlineUsers.addAll(users);
            }
        } else if (message.startsWith("USER_JOINED:")) {
            // User joined: USER_JOINED:username
            String username = message.substring(12);
            if (!onlineUsers.contains(username)) {
                onlineUsers.add(username);
            }
            appendChatMessage("System", username + " joined the chat");
        } else if (message.startsWith("USER_LEFT:")) {
            // User left: USER_LEFT:username
            String username = message.substring(10);
            onlineUsers.remove(username);
            appendChatMessage("System", username + " left the chat");
        } else if (message.startsWith("FILE_SIZE:")) {
            // File size notification for download - don't log as unhandled
            // The download handler will process the actual file data
            log("Server preparing file for download...");
        } else {
            // Filter out binary data (file content being misread as text)
            // Only log legitimate unhandled protocol messages
            if (message.length() > 5 && isPrintableText(message) &&
                !message.trim().isEmpty() &&
                message.length() < 200) { // Reasonable message length
                // Log only if it looks like actual text protocol messages
                System.out.println("DEBUG: Unhandled message: " + message);
            }
            // Silently ignore binary/corrupted data (file transfer in progress)
        }
    }

    /**
     * Determine if a server message should be logged to the UI
     * Filters out binary data, PDF content, and file transfer noise
     */
    private boolean shouldLogMessage(String message) {
        if (message == null || message.isEmpty() || message.trim().isEmpty()) {
            return false;
        }

        // Always log known protocol messages
        if (message.startsWith("FILE_LIST:") ||
            message.startsWith("NOTIFICATION:") ||
            message.startsWith("CHAT:") ||
            message.startsWith("USER_LIST:") ||
            message.startsWith("USER_JOINED:") ||
            message.startsWith("USER_LEFT:") ||
            message.startsWith("FILE_SIZE:") ||
            message.startsWith("READY:") ||
            message.startsWith("UPLOAD_SUCCESS:") ||
            message.startsWith("DOWNLOAD_SUCCESS:") ||
            message.startsWith("ERROR:")) {
            return true;
        }

        // Filter out known binary/PDF content
        if (message.startsWith("<?xpacket") ||
            message.contains("<rdf:RDF") ||
            message.contains("xmlns:") ||
            message.startsWith("<") && message.endsWith(">") ||
            message.startsWith("stream") ||
            message.startsWith("endstream") ||
            message.startsWith("xref") ||
            message.startsWith("trailer") ||
            message.startsWith("startxref") ||
            message.startsWith("%%EOF") ||
            message.matches("^\\d{10} \\d{5} [nf]$") ||
            message.matches("^\\d+ \\d+ obj$") ||
            message.matches("^\\d+ \\d+$") ||
            message.startsWith("0000") ||
            message.contains("<<") && message.contains(">>") ||
            message.contains("obj") && message.length() < 30) {
            return false;
        }

        // Filter out messages with too many non-printable characters or whitespace-only
        if (!isPrintableText(message) || message.length() > 500) {
            return false;
        }

        return true;
    }

    /**
     * Check if a message contains mostly printable text (not binary file data)
     * Returns false for binary data, PDF content, etc.
     */
    private boolean isPrintableText(String message) {
        if (message == null || message.isEmpty()) return false;

        // Known binary/PDF markers - immediately reject
        if (message.startsWith("stream") || message.startsWith("endstream") ||
            message.startsWith("xref") || message.startsWith("trailer") ||
            message.startsWith("startxref") || message.startsWith("%%EOF") ||
            message.contains("obj") && message.contains("endobj") ||
            message.matches("^\\d{10} \\d{5} [nf]$") || // PDF xref entries
            message.matches("^\\d+ \\d+ obj$") || // PDF object headers
            message.startsWith("0000") || // PDF xref numbers
            message.contains("<<") && message.contains(">>")) { // PDF dictionaries
            return false;
        }

        // Check for high percentage of non-ASCII or control characters
        int nonPrintable = 0;
        int total = Math.min(message.length(), 100); // Check more chars

        for (int i = 0; i < total; i++) {
            char c = message.charAt(i);
            // Reject if contains null bytes or many control characters
            if (c == 0 || (c < 32 && c != '\t' && c != '\n' && c != '\r')) {
                nonPrintable++;
            } else if (c > 126 && c < 160) { // Extended ASCII control chars
                nonPrintable++;
            } else if (c > 255) { // Non-Latin characters (likely binary)
                nonPrintable++;
            }
        }

        // If more than 10% is non-printable, it's probably binary data
        // More strict threshold to catch more binary content
        return (nonPrintable * 100 / total) < 10;
    }

    /**
     * Handle and display notifications from server
     * Shows popup notifications for various server events
     */
    private void handleNotification(String notificationData) {
        try {
            System.out.println("DEBUG: Parsing notification: " + notificationData);
            // Parse notification: [TYPE]MESSAGE|DETAILS
            int typeEnd = notificationData.indexOf(']');
            if (typeEnd > 0) {
                String type = notificationData.substring(1, typeEnd); // Remove [ and ]
                String[] parts = notificationData.substring(typeEnd + 1).split("\\|");
                String message = parts.length > 0 ? parts[0] : "";
                String details = parts.length > 1 ? parts[1] : "";

                System.out.println("DEBUG: Type=" + type + ", Message=" + message + ", Details=" + details);

                // Log the notification
                log("📢 Notification [" + type + "]: " + message);

                // Show popup notification based on type
                switch (type) {
                    case "NEW_FILE":
                        showNotificationAlert("New File Available! 📁",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        // Auto-refresh file list
                        refreshFileList();
                        break;

                    case "FILE_UPDATED":
                        showNotificationAlert("File Updated 📝",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        refreshFileList();
                        break;

                    case "FILE_DELETED":
                        showNotificationAlert("File Deleted 🗑️",
                                message + "\n" + details,
                                Alert.AlertType.WARNING);
                        refreshFileList();
                        break;

                    case "CLIENT_CONNECTED":
                        showNotificationAlert("New Client Connected 👤",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        break;

                    case "CLIENT_DISCONNECTED":
                        showNotificationAlert("Client Disconnected 👋",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        break;

                    case "SERVER_MESSAGE":
                        showNotificationAlert("Server Message 📢",
                                message,
                                Alert.AlertType.INFORMATION);
                        break;

                    default:
                        showNotificationAlert("Notification",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                }
            }
        } catch (Exception e) {
            log("Error parsing notification: " + e.getMessage());
        }
    }

    /**
     * Show notification alert popup
     */
    private void showNotificationAlert(String title, String content, Alert.AlertType type) {
        System.out.println("DEBUG: Showing alert - Title: " + title + ", Content: " + content);
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(type);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(content);

                // Make alert more visible
                alert.setResizable(true);
                alert.getDialogPane().setMinWidth(400);

                System.out.println("DEBUG: Alert created, showing now...");

                // Auto-close after 15 seconds (increased from 5)
                // Comment this out if you want notifications to stay until user clicks OK
                Thread autoCloseThread = new Thread(() -> {
                    try {
                        Thread.sleep(15000); // 15 seconds (increased for visibility)
                        Platform.runLater(() -> {
                            if (alert.isShowing()) {
                                System.out.println("DEBUG: Auto-closing notification alert");
                                alert.close();
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                autoCloseThread.setDaemon(true);
                autoCloseThread.start();

                alert.show(); // Use show() instead of showAndWait() to not block
                System.out.println("DEBUG: Alert.show() called!");

                // Also log to activity log as backup
                log("📢 NOTIFICATION: " + title + " - " + content);
            } catch (Exception e) {
                System.err.println("ERROR showing alert: " + e.getMessage());
                e.printStackTrace();
                // Fallback: at least log it
                log("⚠️ Notification (popup failed): " + title + " - " + content);
            }
        });
    }

    /**
     * Log message to activity log
     */
    private void log(String message) {
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    /**
     * Show alert dialog
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Format file size to human-readable format
     */
    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    /**
     * FileInfo class for TableView
     */
    public static class FileInfo {
        private String name;
        private long sizeBytes;
        private String size;
        private String dateModified;

        public FileInfo(String name, long sizeBytes, String dateModified) {
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.size = formatSize(sizeBytes);
            this.dateModified = dateModified;
        }

        private String formatSize(long size) {
            if (size < 1024)
                return size + " B";
            if (size < 1024 * 1024)
                return String.format("%.2f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024)
                return String.format("%.2f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }

        public String getName() {
            return name;
        }

        public String getSize() {
            return size;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getDateModified() {
            return dateModified;
        }
    }

    // ==================== Member 5: UDP Broadcast Listener ====================

    /**
     * Start UDP listener to receive broadcast notifications from Member 5
     * (Notifier)
     * Demonstrates UDP broadcasting concept
     */
    private void startUDPListener() {
        listeningForBroadcasts = true;

        udpListenerThread = new Thread(() -> {
            DatagramSocket udpSocket = null;

            try {
                // Create UDP socket to listen on port 9876
                udpSocket = new DatagramSocket(UDP_PORT);
                udpSocket.setSoTimeout(1000); // 1 second timeout

                log("📻 UDP Broadcast listener started on port " + UDP_PORT);
                System.out.println("[ClientUI] UDP broadcast listener started on port " + UDP_PORT);

                byte[] buffer = new byte[1024];

                while (listeningForBroadcasts) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);

                        String message = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("[ClientUI] UDP broadcast received: " + message);

                        // Parse broadcast: [TYPE] Message | Details
                        handleUDPBroadcast(message);

                    } catch (SocketTimeoutException e) {
                        // Timeout is normal, continue listening
                    }
                }

            } catch (SocketException e) {
                if (listeningForBroadcasts) {
                    System.err.println("[ClientUI] UDP socket error: " + e.getMessage());
                    Platform.runLater(() -> log("⚠️ UDP listener error: " + e.getMessage()));
                }
            } catch (IOException e) {
                if (listeningForBroadcasts) {
                    System.err.println("[ClientUI] UDP I/O error: " + e.getMessage());
                }
            } finally {
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.close();
                }
                System.out.println("[ClientUI] UDP broadcast listener stopped");
            }
        });

        udpListenerThread.setName("UDP-Broadcast-Listener");
        udpListenerThread.setDaemon(true);
        udpListenerThread.start();
    }

    /**
     * Stop UDP broadcast listener
     */
    private void stopUDPListener() {
        listeningForBroadcasts = false;

        if (udpListenerThread != null) {
            try {
                udpListenerThread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                System.err.println("[ClientUI] Error stopping UDP listener: " + e.getMessage());
            }
        }

        log("📻 UDP Broadcast listener stopped");
    }

    /**
     * Handle UDP broadcast message from Member 5 (Notifier)
     * Format: [TYPE] Message | Details
     */
    private void handleUDPBroadcast(String broadcast) {
        try {
            // Parse format: [TYPE] Message | Details
            if (!broadcast.startsWith("[")) {
                return;
            }

            int typeEnd = broadcast.indexOf("]");
            if (typeEnd == -1) {
                return;
            }

            String type = broadcast.substring(1, typeEnd).trim();
            String rest = broadcast.substring(typeEnd + 1).trim();

            String[] parts = rest.split("\\|");
            String message = parts.length > 0 ? parts[0].trim() : "";
            String details = parts.length > 1 ? parts[1].trim() : "";

            System.out.println(
                    "[ClientUI] Parsed UDP - Type: " + type + ", Message: " + message + ", Details: " + details);

            // Show notification popup based on type
            Platform.runLater(() -> {
                switch (type) {
                    case "NEW_FILE":
                        showNotificationAlert("📁 New File Available",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        refreshFileList();
                        break;

                    case "FILE_UPDATED":
                        showNotificationAlert("📝 File Updated",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        refreshFileList();
                        break;

                    case "FILE_DELETED":
                        showNotificationAlert("🗑️ File Deleted",
                                message + "\n" + details,
                                Alert.AlertType.WARNING);
                        refreshFileList();
                        break;

                    case "CLIENT_CONNECTED":
                        showNotificationAlert("👤 Client Connected",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        break;

                    case "CLIENT_DISCONNECTED":
                        showNotificationAlert("👋 Client Disconnected",
                                message + "\n" + details,
                                Alert.AlertType.INFORMATION);
                        break;

                    case "SERVER_MESSAGE":
                        showNotificationAlert("📢 Server Message",
                                message + (details.isEmpty() ? "" : "\n" + details),
                                Alert.AlertType.INFORMATION);
                        break;

                    default:
                        log("📻 Broadcast: " + message);
                }
            });

        } catch (Exception e) {
            System.err.println("[ClientUI] Error parsing UDP broadcast: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setUserInfo(String email, String name) {
        this.userEmail = email;
        this.userName = name;
    }

    public void setAuthenticatedSocket(Socket socket) {
        this.authenticatedSocket = socket;
    }

    private void connectWithAuthenticatedSocket() {
        Platform.runLater(() -> {
            client = new ClientMain(authenticatedSocket);
            fileHandler = new FileTransferHandler(client, "./downloads");

            client.addConnectionListener(new ClientMain.ConnectionListener() {
                @Override
                public void onConnectionStatusChanged(boolean connected) {
                    Platform.runLater(() -> updateConnectionStatus(connected));
                }

                @Override
                public void onMessageReceived(String message) {
                    Platform.runLater(() -> {
                        log("Server: " + message);
                        parseServerMessage(message);
                    });
                }
            });

            log("Using authenticated connection");
            serverAddressField.setText("localhost");
            serverPortField.setText("9090");
            serverAddressField.setDisable(true);
            serverPortField.setDisable(true);
            connectButton.setDisable(true);

            updateConnectionStatus(true);
            refreshFileList();
        });
    }

    public void autoConnect(String serverAddress, int serverPort) {
        Platform.runLater(() -> {
            serverAddressField.setText(serverAddress);
            serverPortField.setText(String.valueOf(serverPort));
            connectToServer();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
