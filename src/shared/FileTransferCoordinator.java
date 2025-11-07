package shared;

import java.io.*;
import java.util.concurrent.*;

/**
 
 * High-level wrapper for coordinating file transfers with synchronization.
 * This class bridges the gap between the Client/Server components and the
 * SynchronizedFileAccess handler.
 * 
 *
 * @author Synchronization Handler
 */
public class FileTransferCoordinator {
    
    private final SynchronizedFileAccess syncHandler;
    private final String sharedDirectory;
    
    /**
     * Constructor
     * @param sharedDirectory Directory where shared files are stored
     */
    public FileTransferCoordinator(String sharedDirectory) {
        this.syncHandler = new SynchronizedFileAccess();
        this.sharedDirectory = sharedDirectory;
        
        // Ensure shared directory exists
        File dir = new File(sharedDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("[Coordinator] Created shared directory: " + sharedDirectory);
        }
        
        System.out.println("[Coordinator] File Transfer Coordinator initialized");
    }
    
    /**
     * Handle file upload from client (Server-side usage)
     * This method ensures thread-safe writing when multiple clients upload
     * 
     * @param filename Name of the file being uploaded
     * @param inputStream Stream containing file data from client
     * @param fileSize Size of the file in bytes
     * @return TransferResult containing success status and details
     */
    public TransferResult handleUpload(String filename, InputStream inputStream, long fileSize) {
        System.out.println("[Coordinator] Handling upload request for: " + filename 
                          + " (" + fileSize + " bytes)");
        
        String filePath = getFilePath(filename);
        
        // Check if file is already being written by another client
        if (syncHandler.isFileInUse(filePath)) {
            System.out.println("[Coordinator] File is currently in use: " + filename 
                              + " (Active operations: " + syncHandler.getActiveOperations(filePath) + ")");
            // Still proceed, but log the concurrent access
        }
        
        try {
            // Use synchronized write operation
            Future<Boolean> future = syncHandler.synchronizedFileWrite(filePath, inputStream, fileSize);
            
            // Wait for the operation to complete
            Boolean success = future.get(30, TimeUnit.SECONDS); // 30 second timeout
            
            if (success) {
                System.out.println("[Coordinator] Upload successful: " + filename);
                return new TransferResult(true, "File uploaded successfully", filePath);
            } else {
                System.err.println("[Coordinator] Upload failed: " + filename);
                return new TransferResult(false, "File upload failed", filePath);
            }
            
        } catch (TimeoutException e) {
            System.err.println("[Coordinator] Upload timeout for: " + filename);
            return new TransferResult(false, "Upload timeout", filePath);
        } catch (InterruptedException e) {
            System.err.println("[Coordinator] Upload interrupted for: " + filename);
            Thread.currentThread().interrupt();
            return new TransferResult(false, "Upload interrupted", filePath);
        } catch (ExecutionException e) {
            System.err.println("[Coordinator] Upload execution error for: " + filename 
                              + " - " + e.getMessage());
            return new TransferResult(false, "Execution error: " + e.getMessage(), filePath);
        }
    }
    
    /**
     * Handle file download request (Server-side usage)
     * This method ensures thread-safe reading when multiple clients download the same file
     * 
     * @param filename Name of the file being downloaded
     * @param outputStream Stream to send file data to client
     * @return TransferResult containing success status and details
     */
    public TransferResult handleDownload(String filename, OutputStream outputStream) {
        System.out.println("[Coordinator] Handling download request for: " + filename);
        
        String filePath = getFilePath(filename);
        
        // Check if file exists
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("[Coordinator] File not found: " + filename);
            return new TransferResult(false, "File not found", filePath);
        }
        
        // Log concurrent access
        if (syncHandler.isFileInUse(filePath)) {
            System.out.println("[Coordinator] File is being accessed concurrently: " + filename 
                              + " (Active operations: " + syncHandler.getActiveOperations(filePath) + ")");
        }
        
        try {
            // Use synchronized read operation (allows multiple concurrent reads)
            Future<Boolean> future = syncHandler.synchronizedFileRead(filePath, outputStream);
            
            // Wait for the operation to complete
            Boolean success = future.get(30, TimeUnit.SECONDS); // 30 second timeout
            
            if (success) {
                System.out.println("[Coordinator] Download successful: " + filename);
                return new TransferResult(true, "File downloaded successfully", filePath);
            } else {
                System.err.println("[Coordinator] Download failed: " + filename);
                return new TransferResult(false, "File download failed", filePath);
            }
            
        } catch (TimeoutException e) {
            System.err.println("[Coordinator] Download timeout for: " + filename);
            return new TransferResult(false, "Download timeout", filePath);
        } catch (InterruptedException e) {
            System.err.println("[Coordinator] Download interrupted for: " + filename);
            Thread.currentThread().interrupt();
            return new TransferResult(false, "Download interrupted", filePath);
        } catch (ExecutionException e) {
            System.err.println("[Coordinator] Download execution error for: " + filename 
                              + " - " + e.getMessage());
            return new TransferResult(false, "Execution error: " + e.getMessage(), filePath);
        }
    }
    
    /**
     * Check if a file can be safely deleted (no active operations)
     * 
     * @param filename Name of the file to check
     * @return true if file can be safely deleted
     */
    public boolean canDeleteFile(String filename) {
        String filePath = getFilePath(filename);
        boolean inUse = syncHandler.isFileInUse(filePath);
        
        if (inUse) {
            System.out.println("[Coordinator] Cannot delete file (in use): " + filename 
                              + " (Active operations: " + syncHandler.getActiveOperations(filePath) + ")");
        }
        
        return !inUse;
    }
    
    /**
     * Wait for all operations on a file to complete before performing action
     * Useful for file deletion, moving, or renaming
     * 
     * @param filename Name of the file
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if all operations completed within timeout
     */
    public boolean waitForFileAvailability(String filename, long timeoutMs) {
        String filePath = getFilePath(filename);
        System.out.println("[Coordinator] Waiting for file availability: " + filename);
        return syncHandler.waitForFileOperations(filePath, timeoutMs);
    }
    
    /**
     * Get current statistics about file operations
     * Useful for monitoring and debugging
     * 
     * @return Map containing statistics
     */
    public java.util.Map<String, Object> getStatistics() {
        return syncHandler.getStatistics();
    }
    
    /**
     * Get full file path in shared directory
     */
    private String getFilePath(String filename) {
        return new File(sharedDirectory, filename).getPath();
    }
    
    /**
     * Shutdown the coordinator gracefully
     * Should be called when server is shutting down
     */
    public void shutdown() {
        System.out.println("[Coordinator] Shutting down File Transfer Coordinator...");
        syncHandler.shutdown();
        System.out.println("[Coordinator] Shutdown complete");
    }
    
    /**
     * Result class for transfer operations
     */
    public static class TransferResult {
        private final boolean success;
        private final String message;
        private final String filePath;
        
        public TransferResult(boolean success, String message, String filePath) {
            this.success = success;
            this.message = message;
            this.filePath = filePath;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        @Override
        public String toString() {
            return "TransferResult{" +
                   "success=" + success +
                   ", message='" + message + '\'' +
                   ", filePath='" + filePath + '\'' +
                   '}';
        }
    }
    
    /**
     * Demo/Test method
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== File Transfer Coordinator Demo ===\n");
        
        FileTransferCoordinator coordinator = new FileTransferCoordinator("./shared_files");
        
        // Create test file
        String testFile = "demo_file.txt";
        String testContent = "This is a test file for demonstrating coordinated file transfers.\n";
        
        // Simulate upload
        System.out.println("1. Simulating file upload...");
        ByteArrayInputStream uploadStream = new ByteArrayInputStream(testContent.getBytes());
        TransferResult uploadResult = coordinator.handleUpload(testFile, uploadStream, 
                                                               testContent.getBytes().length);
        System.out.println("Upload result: " + uploadResult + "\n");
        
        // Simulate multiple concurrent downloads
        System.out.println("2. Simulating concurrent downloads...");
        for (int i = 0; i < 3; i++) {
            final int num = i + 1;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ByteArrayOutputStream downloadStream = new ByteArrayOutputStream();
                    TransferResult result = coordinator.handleDownload(testFile, downloadStream);
                    System.out.println("Download " + num + " result: " + result);
                    System.out.println("Downloaded content length: " + downloadStream.size() + " bytes");
                }
            }).start();
        }
        
        // Wait a bit for downloads to complete
        Thread.sleep(2000);
        
        // Show statistics
        System.out.println("\n3. Current statistics:");
        System.out.println(coordinator.getStatistics());
        
        // Test file availability check
        System.out.println("\n4. Checking file availability...");
        boolean available = coordinator.waitForFileAvailability(testFile, 5000);
        System.out.println("File available: " + available);
        
        // Shutdown
        coordinator.shutdown();
        
        System.out.println("\n=== Demo Complete ===");
    }
}
