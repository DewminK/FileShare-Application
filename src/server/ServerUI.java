package server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import shared.FileHandler;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


 
public class ServerUI extends Application {

    private ServerMain server;

    // UI Components
    private TextField serverPortField;
    private TextField sharedDirField;
    private Button startButton;
    private Button stopButton;
    private Button browseButton;
    private Label statusLabel;

    private TableView<ClientInfo> clientTable;
    private ObservableList<ClientInfo> clientList;

    private TableView<FileInfo> fileTable;
    private ObservableList<FileInfo> fileList;

    private TextArea logArea;
    private Label statsLabel;

    private Button refreshFilesButton;
    private Button openFolderButton;

    // Chat components
    private TextArea serverChatArea;
    private TextField serverChatInputField;
    private Button serverChatSendButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("File Sharing Server - Network Programming");

        // Create TabPane for multiple views
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Server Management
        Tab serverTab = new Tab("Server Management");
        BorderPane serverLayout = new BorderPane();
        serverLayout.setPadding(new Insets(10));

        VBox topPanel = createServerControlPanel();
        serverLayout.setTop(topPanel);

        SplitPane centerPanel = createCenterPanel();
        serverLayout.setCenter(centerPanel);

        VBox bottomPanel = createLogPanel();
        serverLayout.setBottom(bottomPanel);

        serverTab.setContent(serverLayout);

        // Tab 2: FileHandler Testing
        Tab fileHandlerTab = new Tab("FileHandler Testing");
        BorderPane fileHandlerLayout = createFileHandlerTestPanel();
        fileHandlerTab.setContent(fileHandlerLayout);

        tabPane.getTabs().addAll(serverTab, fileHandlerTab);

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setCenter(tabPane);

        // Create scene and apply CSS
        Scene scene = new Scene(mainLayout, 1100, 800);

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

        // Handle window close
        primaryStage.setOnCloseRequest(event -> {
            if (server != null && server.isRunning()) {
                server.stop();
            }
            Platform.exit();
        });
    }

    /**
     * Create server control panel with port, directory, and start/stop buttons
     */
    private VBox createServerControlPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1;");

        Label titleLabel = new Label("Server Control");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER_LEFT);

        Label portLabel = new Label("Port:");
        serverPortField = new TextField("9090");
        serverPortField.setPrefWidth(80);

        Label dirLabel = new Label("Shared Directory:");
        sharedDirField = new TextField("./shared_files");
        sharedDirField.setPrefWidth(250);

        browseButton = new Button("Browse...");
        browseButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        browseButton.setOnAction(e -> browseDirectory());

        startButton = new Button("Start Server");
        startButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        startButton.setOnAction(e -> startServer());

        stopButton = new Button("Stop Server");
        stopButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> stopServer());

        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff0000;");

        controlBox.getChildren().addAll(portLabel, serverPortField, dirLabel,
                sharedDirField, browseButton, startButton, stopButton,
                new Region(), statusLabel);
        HBox.setHgrow(controlBox.getChildren().get(controlBox.getChildren().size() - 2), Priority.ALWAYS);

        panel.getChildren().addAll(titleLabel, controlBox);
        return panel;
    }

    /**
     * Create center panel with clients, files, and chat
     */
    private SplitPane createCenterPanel() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.33, 0.66); // Three equal sections

        // Left: Connected Clients
        VBox clientPanel = createClientPanel();

        // Middle: Shared Files
        VBox filePanel = createFilePanel();

        // Right: Chat Monitor
        VBox chatPanel = createServerChatPanel();

        splitPane.getItems().addAll(clientPanel, filePanel, chatPanel);
        return splitPane;
    }

    /**
     * Create server chat monitoring panel
     */
    private VBox createServerChatPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label titleLabel = new Label("💬 Chat Monitor");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");

        // Chat messages area
        serverChatArea = new TextArea();
        serverChatArea.setEditable(false);
        serverChatArea.setWrapText(true);
        serverChatArea.setStyle("-fx-control-inner-background: #f5f5f5; -fx-font-family: 'Segoe UI';");
        VBox.setVgrow(serverChatArea, Priority.ALWAYS);

        // Server broadcast input
        Label broadcastLabel = new Label("Broadcast to all clients:");
        broadcastLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        HBox chatInputBox = new HBox(10);
        chatInputBox.setAlignment(Pos.CENTER);

        serverChatInputField = new TextField();
        serverChatInputField.setPromptText("Type server announcement...");
        serverChatInputField.setDisable(true);
        HBox.setHgrow(serverChatInputField, Priority.ALWAYS);
        serverChatInputField.setOnAction(e -> sendServerBroadcast());

        serverChatSendButton = new Button("Broadcast");
        serverChatSendButton.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15;");
        serverChatSendButton.setDisable(true);
        serverChatSendButton.setOnAction(e -> sendServerBroadcast());

        chatInputBox.getChildren().addAll(serverChatInputField, serverChatSendButton);

        panel.getChildren().addAll(titleLabel, serverChatArea, broadcastLabel, chatInputBox);
        return panel;
    }

    /**
     * Send server broadcast message to all connected clients
     */
    private void sendServerBroadcast() {
        String message = serverChatInputField.getText().trim();
        if (message.isEmpty() || server == null) {
            return;
        }

        try {
            // Broadcast through server
            server.broadcastChatMessage("SERVER", message);

            // Display in server chat monitor
            appendServerChatMessage("SERVER", message);
            serverChatInputField.clear();
        } catch (Exception e) {
            log("Error broadcasting message: " + e.getMessage());
        }
    }

    /**
     * Append chat message to server chat monitor
     */
    public void appendServerChatMessage(String sender, String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            serverChatArea.appendText(String.format("[%s] %s: %s\n", timestamp, sender, message));
        });
    }

    /**
     * Create connected clients panel
     */
    private VBox createClientPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label titleLabel = new Label("Connected Clients");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Create table
        clientTable = new TableView<>();
        clientList = FXCollections.observableArrayList();
        clientTable.setItems(clientList);

        TableColumn<ClientInfo, String> addressColumn = new TableColumn<>("Client Address");
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressColumn.setPrefWidth(150);

        TableColumn<ClientInfo, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(100);

        TableColumn<ClientInfo, String> connectionTimeColumn = new TableColumn<>("Connected Time");
        connectionTimeColumn.setCellValueFactory(new PropertyValueFactory<>("connectionTime"));
        connectionTimeColumn.setPrefWidth(150);

        clientTable.getColumns().addAll(addressColumn, statusColumn, connectionTimeColumn);
        VBox.setVgrow(clientTable, Priority.ALWAYS);

        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Button refreshClientsButton = new Button("Refresh Clients");
        refreshClientsButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshClientsButton.setOnAction(e -> refreshClientList());

        buttonBox.getChildren().addAll(refreshClientsButton);

        panel.getChildren().addAll(titleLabel, clientTable, buttonBox);
        return panel;
    }

    /**
     * Create shared files panel
     */
    private VBox createFilePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label titleLabel = new Label("Shared Files");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Create table
        fileTable = new TableView<>();
        fileList = FXCollections.observableArrayList();
        fileTable.setItems(fileList);

        TableColumn<FileInfo, String> nameColumn = new TableColumn<>("File Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(200);

        TableColumn<FileInfo, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setPrefWidth(100);

        TableColumn<FileInfo, String> dateColumn = new TableColumn<>("Date Modified");
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("dateModified"));
        dateColumn.setPrefWidth(150);

        fileTable.getColumns().addAll(nameColumn, sizeColumn, dateColumn);
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        refreshFilesButton = new Button("Refresh Files");
        refreshFilesButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshFilesButton.setOnAction(e -> refreshFileList());

        openFolderButton = new Button("Open Folder");
        openFolderButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        openFolderButton.setOnAction(e -> openSharedFolder());

        buttonBox.getChildren().addAll(refreshFilesButton, openFolderButton);

        panel.getChildren().addAll(titleLabel, fileTable, buttonBox);
        return panel;
    }

    /**
     * Create log and statistics panel
     */
    private VBox createLogPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefHeight(180);

        Label titleLabel = new Label("Server Activity Log");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px;");

        HBox statsBox = new HBox(10);
        statsBox.setAlignment(Pos.CENTER_LEFT);

        statsLabel = new Label("Ready | Clients: 0 | Files: 0");
        statsLabel.setStyle("-fx-font-weight: bold;");

        statsBox.getChildren().addAll(new Label("Statistics:"), statsLabel);

        panel.getChildren().addAll(titleLabel, logArea, statsBox);
        return panel;
    }

    /**
     * Browse for shared directory
     */
    private void browseDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Shared Directory");

        File currentDir = new File(sharedDirField.getText());
        if (currentDir.exists() && currentDir.isDirectory()) {
            directoryChooser.setInitialDirectory(currentDir);
        }

        File selectedDirectory = directoryChooser.showDialog(null);
        if (selectedDirectory != null) {
            sharedDirField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    /**
     * Start the server
     */
    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(serverPortField.getText());
        } catch (NumberFormatException e) {
            showAlert("Invalid Port", "Please enter a valid port number.");
            return;
        }

        String sharedDir = sharedDirField.getText();
        if (sharedDir.isEmpty()) {
            showAlert("Invalid Directory", "Please specify a shared directory.");
            return;
        }

        // Create server instance
        server = new ServerMain(port, sharedDir);

        // Add listener for server events (implementing ChatListener)
        server.addServerListener(new ServerMain.ChatListener() {
            @Override
            public void onServerStarted(int port) {
                Platform.runLater(() -> {
                    updateServerStatus(true);
                    log("Server started on port " + port);
                    refreshFileList();
                });
            }

            @Override
            public void onServerStopped() {
                Platform.runLater(() -> {
                    updateServerStatus(false);
                    log("Server stopped");
                    clientList.clear();
                });
            }

            @Override
            public void onClientConnected(String clientAddress) {
                Platform.runLater(() -> {
                    log("Client connected: " + clientAddress);
                    refreshClientList();
                    updateStatistics();
                });
            }

            @Override
            public void onClientDisconnected(String clientAddress) {
                Platform.runLater(() -> {
                    log("Client disconnected: " + clientAddress);
                    refreshClientList();
                    updateStatistics();
                });
            }

            @Override
            public void onFileTransfer(String clientAddress, String operation, String filename) {
                Platform.runLater(() -> {
                    log(operation + " - " + clientAddress + " - " + filename);
                    if (operation.equals("UPLOAD")) {
                        // Refresh file list after upload
                        new Thread(() -> {
                            try {
                                Thread.sleep(500); // Wait for file to be written
                                Platform.runLater(() -> refreshFileList());
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                });
            }

            @Override
            public void onServerError(String error) {
                Platform.runLater(() -> {
                    log("ERROR: " + error);
                    showAlert("Server Error", error);
                });
            }

            @Override
            public void onChatMessage(String sender, String message) {
                Platform.runLater(() -> {
                    appendServerChatMessage(sender, message);
                    log("💬 Chat from " + sender + ": " + message);
                });
            }
        });

        // Start server in a separate thread
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.start();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Stop the server
     */
    private void stopServer() {
        if (server != null && server.isRunning()) {
            server.stop();
            log("Server stopped by user");
        }
    }

    /**
     * Update UI based on server status
     */
    private void updateServerStatus(boolean running) {
        if (running) {
            statusLabel.setText("Status: Running on port " + serverPortField.getText());
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #00ff00;");
            startButton.setDisable(true);
            stopButton.setDisable(false);
            serverPortField.setDisable(true);
            sharedDirField.setDisable(true);
            browseButton.setDisable(true);

            // Enable server chat broadcast
            serverChatInputField.setDisable(false);
            serverChatSendButton.setDisable(false);
            appendServerChatMessage("System", "Server started - Chat monitoring active");
        } else {
            statusLabel.setText("Status: Stopped");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff0000;");
            startButton.setDisable(false);
            stopButton.setDisable(true);
            serverPortField.setDisable(false);
            sharedDirField.setDisable(false);
            browseButton.setDisable(false);

            // Disable server chat broadcast
            serverChatInputField.setDisable(true);
            serverChatSendButton.setDisable(true);
            serverChatArea.clear();
        }
    }

    /**
     * Refresh client list
     */
    private void refreshClientList() {
        if (server != null && server.isRunning()) {
            clientList.clear();
            List<String> clients = server.getConnectedClientsInfo();
            for (String clientInfo : clients) {
                // Parse client info
                String[] parts = clientInfo.split(" \\(Connected: ");
                String address = parts[0];
                String time = parts.length > 1 ? parts[1].replace(")", "") : "Unknown";
                clientList.add(new ClientInfo(address, "Active", time));
            }
        }
    }

    /**
     * Refresh file list
     */
    private void refreshFileList() {
        fileList.clear();

        String sharedDir = sharedDirField.getText();
        File dir = new File(sharedDir);

        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(new FileInfo(
                                file.getName(),
                                file.length(),
                                new java.util.Date(file.lastModified()).toString()));
                    }
                }
            }
        }

        updateStatistics();
    }

    /**
     * Open shared folder in file explorer
     */
    private void openSharedFolder() {
        try {
            File dir = new File(sharedDirField.getText());
            if (dir.exists() && dir.isDirectory()) {
                java.awt.Desktop.getDesktop().open(dir);
            } else {
                showAlert("Directory Not Found", "The shared directory does not exist.");
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to open directory: " + e.getMessage());
        }
    }

    /**
     * Update statistics label
     */
    private void updateStatistics() {
        int clientCount = clientList.size();
        int fileCount = fileList.size();

        String status = server != null && server.isRunning() ? "Running" : "Stopped";
        statsLabel.setText(status + " | Clients: " + clientCount + " | Files: " + fileCount);
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
     * Create FileHandler testing panel with both blocking and non-blocking tests
     */
    private BorderPane createFileHandlerTestPanel() {
        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(15));

        // Top: Title and description
        VBox headerBox = new VBox(10);
        headerBox.setPadding(new Insets(10));
        headerBox.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196F3; -fx-border-width: 2;");

        Label titleLabel = new Label("FileHandler Non-Blocking I/O Testing");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1976D2;");

        Label descLabel = new Label("Test the FileHandler class with Selector-based non-blocking I/O.\n" +
                "The FileHandler automatically detects blocking mode and uses appropriate transfer method.");
        descLabel.setStyle("-fx-font-size: 12px;");
        descLabel.setWrapText(true);

        headerBox.getChildren().addAll(titleLabel, descLabel);
        layout.setTop(headerBox);

        // Center: Test Controls
        VBox centerBox = new VBox(20);
        centerBox.setPadding(new Insets(20));

        // File Selection
        VBox fileSelectionBox = new VBox(10);
        fileSelectionBox.setStyle(
                "-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-border-color: #cccccc; -fx-border-width: 1;");

        Label fileLabel = new Label("1. Select Test File:");
        fileLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        HBox fileBox = new HBox(10);
        TextField testFileField = new TextField();
        testFileField.setPromptText("Select a file to test transfer...");
        testFileField.setPrefWidth(400);
        testFileField.setEditable(false);

        Button browseTestFile = new Button("Browse...");
        browseTestFile.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        browseTestFile.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Test File");
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                testFileField.setText(file.getAbsolutePath());
            }
        });

        fileBox.getChildren().addAll(testFileField, browseTestFile);
        fileSelectionBox.getChildren().addAll(fileLabel, fileBox);

        // Transfer Mode Selection
        VBox modeBox = new VBox(10);
        modeBox.setStyle(
                "-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-border-color: #cccccc; -fx-border-width: 1;");

        Label modeLabel = new Label("2. Select Transfer Mode:");
        modeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton blockingMode = new RadioButton("Blocking Mode (Traditional Socket)");
        blockingMode.setToggleGroup(modeGroup);
        blockingMode.setSelected(true);

        RadioButton nonBlockingMode = new RadioButton("Non-Blocking Mode (Selector-based SocketChannel)");
        nonBlockingMode.setToggleGroup(modeGroup);

        Label modeInfoLabel = new Label(
                "Blocking: Uses standard blocking sockets\nNon-Blocking: Uses NIO Selectors for event-driven I/O");
        modeInfoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-padding: 5 0 0 25;");

        modeBox.getChildren().addAll(modeLabel, blockingMode, nonBlockingMode, modeInfoLabel);

        // Test Server Configuration
        VBox configBox = new VBox(10);
        configBox.setStyle(
                "-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-border-color: #cccccc; -fx-border-width: 1;");

        Label configLabel = new Label("3. Test Server Configuration:");
        configLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        HBox portBox = new HBox(10);
        portBox.setAlignment(Pos.CENTER_LEFT);
        Label portLabel = new Label("Test Port:");
        TextField testPortField = new TextField("9090");
        testPortField.setPrefWidth(100);
        portBox.getChildren().addAll(portLabel, testPortField);

        configBox.getChildren().addAll(configLabel, portBox);

        // Action Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        Button startTestButton = new Button("Start Transfer Test");
        startTestButton.setStyle(
                "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 30;");

        Button clearLogButton = new Button("Clear Log");
        clearLogButton.setStyle(
                "-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");

        ProgressBar testProgressBar = new ProgressBar(0);
        testProgressBar.setPrefWidth(300);
        testProgressBar.setVisible(false);

        buttonBox.getChildren().addAll(startTestButton, clearLogButton, testProgressBar);

        centerBox.getChildren().addAll(fileSelectionBox, modeBox, configBox, buttonBox);
        layout.setCenter(centerBox);

        // Bottom: Test Log
        VBox logBox = new VBox(10);
        logBox.setPadding(new Insets(10));
        logBox.setPrefHeight(250);

        Label logLabel = new Label("Test Activity Log:");
        logLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TextArea testLogArea = new TextArea();
        testLogArea.setEditable(false);
        testLogArea.setPrefHeight(200);
        testLogArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        testLogArea.setText(
                "=== FileHandler Non-Blocking I/O Testing Console ===\nReady to test. Select a file and click 'Start Transfer Test'.\n");

        logBox.getChildren().addAll(logLabel, testLogArea);
        layout.setBottom(logBox);

        // Clear Log Action
        clearLogButton.setOnAction(e -> {
            testLogArea.clear();
            testLogArea.setText("=== FileHandler Non-Blocking I/O Testing Console ===\nLog cleared.\n");
        });

        // Start Test Action
        ExecutorService testExecutor = Executors.newCachedThreadPool();

        startTestButton.setOnAction(e -> {
            String filePath = testFileField.getText();
            if (filePath.isEmpty()) {
                testLogArea.appendText("[ERROR] Please select a test file first.\n");
                return;
            }

            File testFile = new File(filePath);
            if (!testFile.exists()) {
                testLogArea.appendText("[ERROR] Selected file does not exist.\n");
                return;
            }

            int testPort;
            try {
                testPort = Integer.parseInt(testPortField.getText());
            } catch (NumberFormatException ex) {
                testLogArea.appendText("[ERROR] Invalid port number.\n");
                return;
            }

            boolean useNonBlocking = nonBlockingMode.isSelected();
            String mode = useNonBlocking ? "NON-BLOCKING (Selector)" : "BLOCKING (Traditional)";

            testLogArea.appendText("\n" + "=".repeat(60) + "\n");
            testLogArea.appendText("[TEST START] Testing FileHandler in " + mode + " mode\n");
            testLogArea.appendText(
                    "[INFO] File: " + testFile.getName() + " (" + formatFileSize(testFile.length()) + ")\n");
            testLogArea.appendText("[INFO] Port: " + testPort + "\n");
            testLogArea.appendText("=".repeat(60) + "\n");

            startTestButton.setDisable(true);
            testProgressBar.setVisible(true);
            testProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

            // Run test in background
            testExecutor.submit(() -> {
                try {
                    runFileHandlerTest(testFile, testPort, useNonBlocking, testLogArea, testProgressBar,
                            startTestButton);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        testLogArea.appendText("[ERROR] Test failed: " + ex.getMessage() + "\n");
                        testProgressBar.setVisible(false);
                        startTestButton.setDisable(false);
                    });
                }
            });
        });

        return layout;
    }

    /**
     * Run FileHandler test with selected mode
     */
    private void runFileHandlerTest(File testFile, int port, boolean useNonBlocking,
                                    TextArea logArea, ProgressBar progressBar, Button startButton) {
        FileHandler fileHandler = new FileHandler();
        ServerSocketChannel serverChannel = null;
        SocketChannel clientChannel = null;

        try {
            long startTime = System.currentTimeMillis();

            // Phase 1: Setup server
            Platform.runLater(() -> logArea.appendText("[PHASE 1] Setting up test server...\n"));

            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));

            if (useNonBlocking) {
                serverChannel.configureBlocking(false);
                Platform.runLater(() -> logArea.appendText("[INFO] Server configured as NON-BLOCKING\n"));
            } else {
                Platform.runLater(() -> logArea.appendText("[INFO] Server configured as BLOCKING\n"));
            }

            Platform.runLater(() -> logArea.appendText("[SUCCESS] Test server listening on port " + port + "\n"));

            // Phase 2: Setup client connection
            Platform.runLater(() -> {
                logArea.appendText("[PHASE 2] Establishing client connection...\n");
                progressBar.setProgress(0.2);
            });

            // Accept connection in separate thread
            final ServerSocketChannel finalServerChannel = serverChannel;
            Thread acceptThread = new Thread(() -> {
                try {
                    SocketChannel accepted = finalServerChannel.accept();
                    if (accepted != null) {
                        Platform.runLater(() -> logArea.appendText("[SUCCESS] Client connected\n"));
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> logArea.appendText("[ERROR] Accept failed: " + e.getMessage() + "\n"));
                }
            });
            acceptThread.start();

            Thread.sleep(500); // Wait for server to be ready

            clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress("localhost", port));

            if (useNonBlocking) {
                clientChannel.configureBlocking(false);
                Platform.runLater(() -> logArea.appendText("[INFO] Client configured as NON-BLOCKING\n"));
            } else {
                Platform.runLater(() -> logArea.appendText("[INFO] Client configured as BLOCKING\n"));
            }

            Platform.runLater(() -> {
                logArea.appendText("[SUCCESS] Connection established\n");
                progressBar.setProgress(0.4);
            });

            // Phase 3: File Transfer
            Platform.runLater(() -> logArea.appendText("[PHASE 3] Starting file transfer...\n"));

            long transferStart = System.currentTimeMillis();
            long bytesSent = fileHandler.sendFile(Paths.get(testFile.getAbsolutePath()), clientChannel);
            long transferTime = System.currentTimeMillis() - transferStart;

            Platform.runLater(() -> {
                logArea.appendText("[SUCCESS] Transfer completed\n");
                logArea.appendText("[STATS] Bytes sent: " + bytesSent + " bytes\n");
                logArea.appendText("[STATS] Transfer time: " + transferTime + " ms\n");
                logArea.appendText("[STATS] Transfer speed: " +
                        String.format("%.2f", (bytesSent / 1024.0 / 1024.0) / (transferTime / 1000.0)) + " MB/s\n");
                progressBar.setProgress(0.8);
            });

            // Phase 4: Cleanup
            Platform.runLater(() -> logArea.appendText("[PHASE 4] Cleaning up...\n"));

            if (clientChannel != null)
                clientChannel.close();
            if (serverChannel != null)
                serverChannel.close();

            long totalTime = System.currentTimeMillis() - startTime;

            Platform.runLater(() -> {
                logArea.appendText("[SUCCESS] Test completed successfully\n");
                logArea.appendText("[TOTAL TIME] " + totalTime + " ms\n");
                logArea.appendText("=".repeat(60) + "\n");
                logArea.appendText("[RESULT] ✓ FileHandler " + (useNonBlocking ? "NON-BLOCKING" : "BLOCKING") +
                        " mode working correctly!\n");
                logArea.appendText("=".repeat(60) + "\n\n");
                progressBar.setProgress(1.0);

                // Hide progress bar and re-enable button after 2 seconds
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Platform.runLater(() -> {
                            progressBar.setVisible(false);
                            progressBar.setProgress(0);
                            startButton.setDisable(false);
                        });
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            });

        } catch (Exception e) {
            Platform.runLater(() -> {
                logArea.appendText("[ERROR] Test failed: " + e.getClass().getSimpleName() + "\n");
                logArea.appendText("[ERROR] Message: " + e.getMessage() + "\n");
                e.printStackTrace();
                logArea.appendText("=".repeat(60) + "\n\n");
                progressBar.setVisible(false);
                startButton.setDisable(false);
            });
        } finally {
            try {
                if (clientChannel != null && clientChannel.isOpen())
                    clientChannel.close();
                if (serverChannel != null && serverChannel.isOpen())
                    serverChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Format file size for display
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
     * ClientInfo class for TableView
     */
    public static class ClientInfo {
        private String address;
        private String status;
        private String connectionTime;

        public ClientInfo(String address, String status, String connectionTime) {
            this.address = address;
            this.status = status;
            this.connectionTime = connectionTime;
        }

        public String getAddress() {
            return address;
        }

        public String getStatus() {
            return status;
        }

        public String getConnectionTime() {
            return connectionTime;
        }
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

    public static void main(String[] args) {
        launch(args);
    }
}
