package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class LoginUI extends Application {

    private Stage primaryStage;
    private TextField emailField;
    private PasswordField passwordField;
    private TextField nameField;
    private TextField signupEmailField;
    private PasswordField signupPasswordField;
    private Label messageLabel;
    private Label signupMessageLabel;
    private String serverAddress = "localhost";
    private int serverPort = 9090;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("File Sharing Application - Login");

        showLoginScreen();

        primaryStage.show();
    }

    private void showLoginScreen() {
        VBox mainLayout = new VBox(20);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(40));
        mainLayout.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("File Sharing Application");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");

        Label subtitleLabel = new Label("Login to Continue");
        subtitleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");

        VBox formBox = new VBox(15);
        formBox.setAlignment(Pos.CENTER);
        formBox.setPadding(new Insets(30));
        formBox.setStyle("-fx-background-color: white; -fx-border-radius: 10; -fx-background-radius: 10; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        formBox.setMaxWidth(400);

        emailField = new TextField();
        emailField.setPromptText("Email");
        emailField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        emailField.setPrefWidth(350);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        passwordField.setPrefWidth(350);
        passwordField.setOnAction(e -> handleLogin());

        Button loginButton = new Button("Login");
        loginButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; " +
                            "-fx-font-weight: bold; -fx-padding: 12 40; -fx-border-radius: 5; -fx-background-radius: 5;");
        loginButton.setPrefWidth(350);
        loginButton.setOnAction(e -> handleLogin());

        messageLabel = new Label("");
        messageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");

        HBox signupLinkBox = new HBox(5);
        signupLinkBox.setAlignment(Pos.CENTER);
        Label noAccountLabel = new Label("No Account?");
        noAccountLabel.setStyle("-fx-text-fill: #666;");
        Hyperlink signupLink = new Hyperlink("Sign Up here");
        signupLink.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
        signupLink.setOnAction(e -> showSignupScreen());
        signupLinkBox.getChildren().addAll(noAccountLabel, signupLink);

        formBox.getChildren().addAll(emailField, passwordField, loginButton, messageLabel, signupLinkBox);

        mainLayout.getChildren().addAll(titleLabel, subtitleLabel, formBox);

        Scene scene = new Scene(mainLayout, 600, 500);
        primaryStage.setScene(scene);
    }

    private void showSignupScreen() {
        VBox mainLayout = new VBox(20);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(40));
        mainLayout.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("Create Account");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");

        Label subtitleLabel = new Label("Sign up to get started");
        subtitleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");

        VBox formBox = new VBox(15);
        formBox.setAlignment(Pos.CENTER);
        formBox.setPadding(new Insets(30));
        formBox.setStyle("-fx-background-color: white; -fx-border-radius: 10; -fx-background-radius: 10; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        formBox.setMaxWidth(400);

        nameField = new TextField();
        nameField.setPromptText("Full Name");
        nameField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        nameField.setPrefWidth(350);

        signupEmailField = new TextField();
        signupEmailField.setPromptText("Email");
        signupEmailField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        signupEmailField.setPrefWidth(350);

        signupPasswordField = new PasswordField();
        signupPasswordField.setPromptText("Password");
        signupPasswordField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        signupPasswordField.setPrefWidth(350);
        signupPasswordField.setOnAction(e -> handleSignup());

        Button signupButton = new Button("Sign Up");
        signupButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 16px; " +
                             "-fx-font-weight: bold; -fx-padding: 12 40; -fx-border-radius: 5; -fx-background-radius: 5;");
        signupButton.setPrefWidth(350);
        signupButton.setOnAction(e -> handleSignup());

        signupMessageLabel = new Label("");
        signupMessageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");

        HBox loginLinkBox = new HBox(5);
        loginLinkBox.setAlignment(Pos.CENTER);
        Label haveAccountLabel = new Label("Already have an account?");
        haveAccountLabel.setStyle("-fx-text-fill: #666;");
        Hyperlink loginLink = new Hyperlink("Login here");
        loginLink.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
        loginLink.setOnAction(e -> showLoginScreen());
        loginLinkBox.getChildren().addAll(haveAccountLabel, loginLink);

        formBox.getChildren().addAll(nameField, signupEmailField, signupPasswordField, signupButton,
                                     signupMessageLabel, loginLinkBox);

        mainLayout.getChildren().addAll(titleLabel, subtitleLabel, formBox);

        Scene scene = new Scene(mainLayout, 600, 550);
        primaryStage.setScene(scene);
    }

    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please fill in all fields");
            return;
        }

        messageLabel.setText("Connecting...");
        messageLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-size: 12px;");

        new Thread(() -> {
            try {
                Socket socket = new Socket(serverAddress, serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("LOGIN:" + email + ":" + password);

                String response = in.readLine();

                if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                    String[] parts = response.split(":");
                    String userName = parts.length > 1 ? parts[1] : email;

                    Platform.runLater(() -> {
                        openClientUI(email, userName, socket);
                    });
                } else if (response != null && response.startsWith("LOGIN_FAILED")) {
                    Platform.runLater(() -> {
                        messageLabel.setText("Invalid email or password");
                        messageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                    });
                    socket.close();
                } else {
                    Platform.runLater(() -> {
                        messageLabel.setText("Server error. Please try again.");
                        messageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                    });
                    socket.close();
                }

            } catch (IOException e) {
                Platform.runLater(() -> {
                    messageLabel.setText("Cannot connect to server: " + e.getMessage());
                    messageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                });
            }
        }).start();
    }

    private void handleSignup() {
        String name = nameField.getText().trim();
        String email = signupEmailField.getText().trim();
        String password = signupPasswordField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            signupMessageLabel.setText("Please fill in all fields");
            return;
        }

        if (!email.contains("@")) {
            signupMessageLabel.setText("Please enter a valid email");
            return;
        }

        signupMessageLabel.setText("Creating account...");
        signupMessageLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-size: 12px;");

        new Thread(() -> {
            try {
                Socket socket = new Socket(serverAddress, serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("SIGNUP:" + name + ":" + email + ":" + password);

                String response = in.readLine();

                if (response != null && response.equals("SIGNUP_SUCCESS")) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText("Account Created Successfully");
                        alert.setContentText("You can now login with your credentials.");
                        alert.showAndWait();
                        showLoginScreen();
                    });
                } else if (response != null && response.equals("SIGNUP_FAILED:EMAIL_EXISTS")) {
                    Platform.runLater(() -> {
                        signupMessageLabel.setText("Email already registered");
                        signupMessageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                    });
                } else {
                    Platform.runLater(() -> {
                        signupMessageLabel.setText("Signup failed. Please try again.");
                        signupMessageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                    });
                }

                socket.close();

            } catch (IOException e) {
                Platform.runLater(() -> {
                    signupMessageLabel.setText("Cannot connect to server: " + e.getMessage());
                    signupMessageLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12px;");
                });
            }
        }).start();
    }

    private void openClientUI(String email, String userName, Socket authenticatedSocket) {
        try {
            ClientUI clientUI = new ClientUI();
            clientUI.setUserInfo(email, userName);
            clientUI.setAuthenticatedSocket(authenticatedSocket);
            Stage clientStage = new Stage();
            clientUI.start(clientStage);
            primaryStage.close();
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("Error opening client: " + e.getMessage());
            try {
                authenticatedSocket.close();
            } catch (IOException ex) {
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
