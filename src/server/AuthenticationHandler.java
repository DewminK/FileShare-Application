package server;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationHandler {

    private static final String USERS_FILE = "users.txt";
    private Map<String, UserCredential> userDatabase;

    public AuthenticationHandler() {
        this.userDatabase = new HashMap<>();
        loadUsers();
    }

    private void loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
                System.out.println("[Auth] Created new users.txt file");
            } catch (IOException e) {
                System.err.println("[Auth] Error creating users.txt: " + e.getMessage());
            }
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    String name = parts[0];
                    String email = parts[1];
                    String password = parts[2];
                    userDatabase.put(email, new UserCredential(name, email, password));
                    count++;
                }
            }
            System.out.println("[Auth] Loaded " + count + " users from database");
        } catch (IOException e) {
            System.err.println("[Auth] Error loading users: " + e.getMessage());
        }
    }

    public synchronized String authenticate(String email, String password) {
        UserCredential user = userDatabase.get(email);
        if (user != null && user.password.equals(password)) {
            System.out.println("[Auth] Login successful: " + email);
            return user.name;
        }
        System.out.println("[Auth] Login failed: " + email);
        return null;
    }

    public synchronized boolean signup(String name, String email, String password) {
        if (userDatabase.containsKey(email)) {
            System.out.println("[Auth] Signup failed - email exists: " + email);
            return false;
        }

        UserCredential newUser = new UserCredential(name, email, password);
        userDatabase.put(email, newUser);

        try (PrintWriter writer = new PrintWriter(new FileWriter(USERS_FILE, true))) {
            writer.println(name + "|" + email + "|" + password);
            System.out.println("[Auth] Signup successful: " + email);
            return true;
        } catch (IOException e) {
            System.err.println("[Auth] Error saving user: " + e.getMessage());
            userDatabase.remove(email);
            return false;
        }
    }

    public boolean userExists(String email) {
        return userDatabase.containsKey(email);
    }

    public int getUserCount() {
        return userDatabase.size();
    }

    private static class UserCredential {
        String name;
        String email;
        String password;

        UserCredential(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }
}

