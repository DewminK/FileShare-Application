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

/**
 * JavaFX-based User Interface for File Sharing Client
 * Provides a modern GUI for connecting to server, viewing files, uploading and downloading
 */
public class ClientUI extends Application {

    private ClientMain client;
    private FileTransferHandler fileHandler;

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

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("File Sharing Client - Network Programming");

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // Top: Connection Panel
        VBox topPanel = createConnectionPanel();
        mainLayout.setTop(topPanel);

        // Center: File List and Operations
        VBox centerPanel = createFilePanel();
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

        // Handle window close
        primaryStage.setOnCloseRequest(event -> {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            Platform.exit();
        });
    }

    /**
     * Create connection panel with server address, port, and connect/disconnect buttons
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
        serverPortField = new TextField("8080");
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

        buttonBox.getChildren().addAll(uploadButton, downloadButton, refreshButton);

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
                    log("Server: " + message);
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

            fileHandler.uploadFile(file, new FileTransferHandler.FileTransferListener() {
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

        fileHandler.downloadFile(selectedFile.getName(), selectedFile.getSizeBytes(),
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
        }
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
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
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
