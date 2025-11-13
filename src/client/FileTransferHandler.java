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

        // Convert to absolute path
        File dir = new File(localDirectory);
        this.localDirectory = dir.getAbsolutePath();

        // Ensure download directory exists
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("[FileTransferHandler] Downloads directory " +
                             (created ? "created" : "already exists") +
                             " at: " + this.localDirectory);
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
     * Upload a file to the server using NIO (FileHandler)
     * Uses Multithreading for simultaneous file upload/download operations
     */
    public void uploadFileNIO(File file, FileTransferListener listener) {
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

                    // Get socket channel for NIO transfer
                    Socket socket = client.getSocket();
                    java.nio.channels.SocketChannel socketChannel = socket.getChannel();

                    // If socket doesn't have a channel, create one from the socket
                    if (socketChannel == null) {
                        // For regular socket, we need to use Channels.newChannel()
                        java.nio.channels.WritableByteChannel channel =
                            java.nio.channels.Channels.newChannel(socket.getOutputStream());

                        // Use FileHandler to send file using NIO
                        shared.FileHandler fileHandler = new shared.FileHandler();
                        java.nio.file.Path filePath = file.toPath();

                        long totalBytesSent = fileHandler.sendFile(filePath, channel);

                        System.out.println("File uploaded successfully using NIO: " + file.getName() +
                                         " (" + totalBytesSent + " bytes)");

                        // Notify completion
                        if (listener != null) {
                            listener.onTransferCompleted(file.getName(), true);
                        }
                    } else {
                        // Use SocketChannel directly
                        shared.FileHandler fileHandler = new shared.FileHandler();
                        java.nio.file.Path filePath = file.toPath();

                        long totalBytesSent = fileHandler.sendFile(filePath, socketChannel);

                        System.out.println("File uploaded successfully using NIO: " + file.getName() +
                                         " (" + totalBytesSent + " bytes)");

                        // Notify completion
                        if (listener != null) {
                            listener.onTransferCompleted(file.getName(), true);
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Error uploading file with NIO: " + e.getMessage());
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
     * Download a file from the server using efficient I/O with NIO-inspired approach
     * Uses Multithreading for simultaneous file upload/download operations
     *
     * NOTE: Due to BufferedReader conflict in ClientMain's listener thread, we use
     * traditional stream I/O but with large buffers (64KB) and NIO FileChannel for writing.
     * This provides good performance while avoiding the protocol conflict.
     */
    public void downloadFileNIO(String filename, long fileSize, FileTransferListener listener) {
        // Create a new thread for download operation to allow simultaneous operations
        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Notify start
                    if (listener != null) {
                        listener.onTransferStarted(filename, fileSize);
                    }

                    // CRITICAL: Pause listener BEFORE sending command to prevent it from reading file data
                    client.beginFileTransfer();
                    System.out.println("[FileTransferHandler] Listener paused, sending download request");

                    // Send download request to server
                    client.requestDownload(filename);

                    // Wait for server to send FILE_SIZE response and file data
                    // During this time, the listener is paused and won't consume anything
                    Thread.sleep(200);

                    // Get socket input stream - now safe to read without listener interference
                    Socket socket = client.getSocket();
                    InputStream inputStream = socket.getInputStream();

                    // Prepare output file with NIO
                    File outputFile = new File(localDirectory, filename);
                    java.nio.file.Path outputPath = outputFile.toPath();

                    // Create parent directories if they don't exist
                    if (outputFile.getParentFile() != null) {
                        outputFile.getParentFile().mkdirs();
                    }

                    System.out.println("[Client] Starting download of " + filename + " (" + fileSize + " bytes)");
                    System.out.println("[Client] Output path: " + outputPath.toAbsolutePath());

                    // Use NIO FileChannel for efficient file writing
                    try (java.nio.channels.FileChannel fileChannel = java.nio.channels.FileChannel.open(
                            outputPath,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                            java.nio.file.StandardOpenOption.WRITE)) {

                        // Use large buffer (64KB) for efficient reading
                        byte[] buffer = new byte[64 * 1024];
                        int bytesRead;
                        long totalBytesReceived = 0;
                        int lastProgress = 0;

                        System.out.println("[Client] Starting to read from InputStream...");

                        // Read file data from input stream
                        while (totalBytesReceived < fileSize &&
                               (bytesRead = inputStream.read(buffer, 0,
                                   (int) Math.min(buffer.length, fileSize - totalBytesReceived))) != -1) {

                            System.out.println("[Client] Read " + bytesRead + " bytes from stream");

                            // Write to file using NIO
                            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, bytesRead);
                            while (byteBuffer.hasRemaining()) {
                                fileChannel.write(byteBuffer);
                            }

                            totalBytesReceived += bytesRead;

                            // Notify progress
                            if (listener != null && fileSize > 0) {
                                int progress = (int) ((totalBytesReceived * 100) / fileSize);
                                if (progress != lastProgress) {
                                    listener.onProgressUpdate(filename, progress);
                                    lastProgress = progress;
                                }
                            }
                        }

                        // Force write to disk
                        fileChannel.force(true);

                        System.out.println("File downloaded successfully: " + filename +
                                         " (" + totalBytesReceived + " bytes)");
                        System.out.println("File saved to: " + outputFile.getAbsolutePath());

                        // Verify file size
                        if (totalBytesReceived != fileSize) {
                            System.err.println("WARNING: Downloaded size (" + totalBytesReceived +
                                             ") doesn't match expected size (" + fileSize + ")");
                        }
                    }

                    // Verify file exists and has correct size
                    if (outputFile.exists()) {
                        long actualSize = outputFile.length();
                        System.out.println("✅ VERIFIED: File exists on disk, size: " + actualSize + " bytes");
                        if (actualSize != fileSize) {
                            System.err.println("⚠️ WARNING: File size on disk (" + actualSize +
                                             ") doesn't match expected (" + fileSize + ")");
                        }
                    } else {
                        System.err.println("❌ ERROR: File does NOT exist at: " + outputFile.getAbsolutePath());
                    }

                    // Notify completion
                    if (listener != null) {
                        listener.onTransferCompleted(filename, false);
                    }

                    // CRITICAL: Resume the listener thread
                    client.endFileTransfer();

                } catch (IOException e) {
                    System.err.println("Error downloading file: " + e.getMessage());
                    e.printStackTrace();

                    // CRITICAL: Resume the listener thread even on error
                    client.endFileTransfer();

                    if (listener != null) {
                        listener.onTransferFailed(filename, e.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.err.println("Download interrupted: " + e.getMessage());

                    // CRITICAL: Resume the listener thread even on interruption
                    client.endFileTransfer();

                    if (listener != null) {
                        listener.onTransferFailed(filename, "Download interrupted");
                    }
                }
            }
        });

        downloadThread.start();
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
