package server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.File;
import java.util.List;

/**
 * JavaFX-based User Interface for File Sharing Server
 * Provides a modern GUI for managing the server, viewing connected clients and activity
 * Uses the same design structure as ClientUI
 */
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

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("File Sharing Server - Network Programming");

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // Top: Server Control Panel
        VBox topPanel = createServerControlPanel();
        mainLayout.setTop(topPanel);

        // Center: Split view with clients and files
        SplitPane centerPanel = createCenterPanel();
        mainLayout.setCenter(centerPanel);

        // Bottom: Log and Statistics
        VBox bottomPanel = createLogPanel();
        mainLayout.setBottom(bottomPanel);

        // Create scene and apply CSS
        Scene scene = new Scene(mainLayout, 1000, 750);

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
        serverPortField = new TextField("8080");
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
     * Create center panel with clients and files tables
     */
    private SplitPane createCenterPanel() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.5);

        // Left: Connected Clients
        VBox clientPanel = createClientPanel();

        // Right: Shared Files
        VBox filePanel = createFilePanel();

        splitPane.getItems().addAll(clientPanel, filePanel);
        return splitPane;
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

        // Add listener for server events
        server.addServerListener(new ServerMain.ServerListener() {
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
        } else {
            statusLabel.setText("Status: Stopped");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff0000;");
            startButton.setDisable(false);
            stopButton.setDisable(true);
            serverPortField.setDisable(false);
            sharedDirField.setDisable(false);
            browseButton.setDisable(false);
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
                            new java.util.Date(file.lastModified()).toString()
                        ));
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

        public String getAddress() { return address; }
        public String getStatus() { return status; }
        public String getConnectionTime() { return connectionTime; }
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
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }

        public String getName() { return name; }
        public String getSize() { return size; }
        public long getSizeBytes() { return sizeBytes; }
        public String getDateModified() { return dateModified; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
