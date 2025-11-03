package client;

import java.io.*;
import java.net.Socket;

/**
 * Handles file upload and download operations using Stream Communication
 * Uses Multithreading for simultaneous file upload/download operations
 */
public class FileTransferHandler {
    private ClientMain client;
    private String localDirectory;

    public FileTransferHandler(ClientMain client, String localDirectory) {
        this.client = client;
        this.localDirectory = localDirectory;

        // Create local directory if it doesn't exist
        File dir = new File(localDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Upload a file to the server using multithreading
     * Uses Anonymous Class for thread creation (as per lecture slides)
     */
    public void uploadFile(File file, FileTransferListener listener) {
        // Create a new thread for upload operation to allow simultaneous operations
        Thread uploadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Notify start
                    if (listener != null) {
                        listener.onTransferStarted(file.getName(), file.length());
                    }

                    // First, send upload request to server
                    client.requestUpload(file.getName(), file.length());

                    // Wait a bit for server to prepare
                    Thread.sleep(100);

                    // Get socket output stream for file transfer
                    Socket socket = client.getSocket();
                    OutputStream outputStream = socket.getOutputStream();

                    // Use BufferedInputStream for efficient file reading
                    FileInputStream fileInputStream = new FileInputStream(file);
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

                    // Buffer for reading chunks
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalBytesSent = 0;

                    // Stream Communication - Send file data through streams
                    while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush();
                        totalBytesSent += bytesRead;

                        // Notify progress
                        if (listener != null) {
                            int progress = (int) ((totalBytesSent * 100) / file.length());
                            listener.onProgressUpdate(file.getName(), progress);
                        }
                    }

                    // Close streams
                    bufferedInputStream.close();
                    fileInputStream.close();

                    System.out.println("File uploaded successfully: " + file.getName());

                    // Notify completion
                    if (listener != null) {
                        listener.onTransferCompleted(file.getName(), true);
                    }

                } catch (IOException e) {
                    System.err.println("Error uploading file: " + e.getMessage());
                    if (listener != null) {
                        listener.onTransferFailed(file.getName(), e.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.err.println("Upload interrupted: " + e.getMessage());
                    if (listener != null) {
                        listener.onTransferFailed(file.getName(), "Upload interrupted");
                    }
                }
            }
        });

        uploadThread.start();
    }

    /**
     * Download a file from the server using multithreading
     * Uses Anonymous Class for thread creation (as per lecture slides)
     */
    public void downloadFile(String filename, long fileSize, FileTransferListener listener) {
        // Create a new thread for download operation to allow simultaneous operations
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Notify start
                    if (listener != null) {
                        listener.onTransferStarted(filename, fileSize);
                    }

                    // Send download request to server
                    client.requestDownload(filename);

                    // Wait a bit for server to prepare
                    Thread.sleep(100);

                    // Get socket input stream for file transfer
                    Socket socket = client.getSocket();
                    InputStream inputStream = socket.getInputStream();

                    // Use BufferedOutputStream for efficient file writing
                    File outputFile = new File(localDirectory, filename);
                    FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

                    // Buffer for reading chunks
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalBytesReceived = 0;

                    // Stream Communication - Receive file data through streams
                    while (totalBytesReceived < fileSize &&
                           (bytesRead = inputStream.read(buffer, 0,
                               (int) Math.min(buffer.length, fileSize - totalBytesReceived))) != -1) {
                        bufferedOutputStream.write(buffer, 0, bytesRead);
                        totalBytesReceived += bytesRead;

                        // Notify progress
                        if (listener != null) {
                            int progress = (int) ((totalBytesReceived * 100) / fileSize);
                            listener.onProgressUpdate(filename, progress);
                        }
                    }

                    // Close streams
                    bufferedOutputStream.close();
                    fileOutputStream.close();

                    System.out.println("File downloaded successfully: " + filename);

                    // Notify completion
                    if (listener != null) {
                        listener.onTransferCompleted(filename, false);
                    }

                } catch (IOException e) {
                    System.err.println("Error downloading file: " + e.getMessage());
                    if (listener != null) {
                        listener.onTransferFailed(filename, e.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.err.println("Download interrupted: " + e.getMessage());
                    if (listener != null) {
                        listener.onTransferFailed(filename, "Download interrupted");
                    }
                }
            }
        });

        downloadThread.start();
    }

    /**
     * Upload multiple files simultaneously using multithreading
     */
    public void uploadMultipleFiles(File[] files, FileTransferListener listener) {
        for (File file : files) {
            if (file.exists() && file.isFile()) {
                uploadFile(file, listener);
            }
        }
    }

    /**
     * Listener interface for file transfer events
     */
    public interface FileTransferListener {
        void onTransferStarted(String filename, long fileSize);
        void onProgressUpdate(String filename, int progress);
        void onTransferCompleted(String filename, boolean isUpload);
        void onTransferFailed(String filename, String error);
    }

    /**
     * Test main method demonstrating simultaneous upload/download
     */
    public static void main(String[] args) {
        // Example usage
        ClientMain client = new ClientMain("localhost", 8080);

        if (client.connect()) {
            FileTransferHandler handler = new FileTransferHandler(client, "./downloads");

            // Create a listener for transfer events
            FileTransferListener listener = new FileTransferListener() {
                @Override
                public void onTransferStarted(String filename, long fileSize) {
                    System.out.println("Transfer started: " + filename + " (" + fileSize + " bytes)");
                }

                @Override
                public void onProgressUpdate(String filename, int progress) {
                    System.out.println("Progress [" + filename + "]: " + progress + "%");
                }

                @Override
                public void onTransferCompleted(String filename, boolean isUpload) {
                    System.out.println("Transfer completed: " + filename +
                                     (isUpload ? " (Upload)" : " (Download)"));
                }

                @Override
                public void onTransferFailed(String filename, String error) {
                    System.err.println("Transfer failed: " + filename + " - " + error);
                }
            };

            // Example: Upload a file
            File fileToUpload = new File("test.txt");
            if (fileToUpload.exists()) {
                handler.uploadFile(fileToUpload, listener);
            }

            // Keep running
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            client.disconnect();
        }
    }
}
