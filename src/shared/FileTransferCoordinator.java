package shared;

import java.io.*;
import java.util.concurrent.*;

/**
 This component manages and coordinates file transfers safely across threads.
 It connects the client/server with the low-level file access system, while also handling timeouts, errors, and monitoring operations.
 */
public class FileTransferCoordinator {
    
    private final SynchronizedFileAccess syncHandler;
    private final String sharedDirectory;
    
    public FileTransferCoordinator(String sharedDirectory) {
        this.syncHandler = new SynchronizedFileAccess();
        this.sharedDirectory = sharedDirectory;
        
        File dir = new File(sharedDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("[Coordinator] Created shared directory: " + sharedDirectory);
        }
        
        System.out.println("[Coordinator] File Transfer Coordinator initialized");
    }
    
    /**
     * Handle file UPLOAD from client (Server-side).
     * Uses synchronized write to prevent data corruption when multiple clients upload simultaneously.
     * Implements 30-second timeout to prevent indefinite blocking.
     */
    public TransferResult handleUpload(String filename, InputStream inputStream, long fileSize) {
        System.out.println("[Coordinator] Handling upload request for: " + filename 
                          + " (" + fileSize + " bytes)");
        
        String filePath = getFilePath(filename);
        
        if (syncHandler.isFileInUse(filePath)) {
            System.out.println("[Coordinator] File is currently in use: " + filename 
                              + " (Active operations: " + syncHandler.getActiveOperations(filePath) + ")");
        }
        
        try {
            Future<Boolean> future = syncHandler.synchronizedFileWrite(filePath, inputStream, fileSize);
            
            Boolean success = future.get(30, TimeUnit.SECONDS);
            
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
     * Handle file DOWNLOAD request (Server-side).
     * Ensures thread-safe reading when multiple clients download the same file.
     * Multiple simultaneous downloads are allowed (shared read lock).
     */
    public TransferResult handleDownload(String filename, OutputStream outputStream) {
        System.out.println("[Coordinator] Handling download request for: " + filename);
        
        String filePath = getFilePath(filename);
        
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("[Coordinator] File not found: " + filename);
            return new TransferResult(false, "File not found", filePath);
        }
        
        if (syncHandler.isFileInUse(filePath)) {
            System.out.println("[Coordinator] File is being accessed concurrently: " + filename 
                              + " (Active operations: " + syncHandler.getActiveOperations(filePath) + ")");
        }
        
        try {
            Future<Boolean> future = syncHandler.synchronizedFileRead(filePath, outputStream);
            
            Boolean success = future.get(30, TimeUnit.SECONDS);
            
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
     * Check if a file can be safely deleted (no active operations).
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
     * Wait for all operations on a file to complete before performing action.
     * Useful for file deletion, moving, or renaming.
     */
    public boolean waitForFileAvailability(String filename, long timeoutMs) {
        String filePath = getFilePath(filename);
        System.out.println("[Coordinator] Waiting for file availability: " + filename);
        return syncHandler.waitForFileOperations(filePath, timeoutMs);
    }
    
    public java.util.Map<String, Object> getStatistics() {
        return syncHandler.getStatistics();
    }
    
    private String getFilePath(String filename) {
        return new File(sharedDirectory, filename).getPath();
    }
    
    /**
     * Shutdown the coordinator gracefully.
     * Should be called when server is shutting down.
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
    
    // Demo: Tests upload and concurrent downloads with statistics
    public static void main(String[] args) throws Exception {
        System.out.println("=== File Transfer Coordinator Demo ===\n");
        
        FileTransferCoordinator coordinator = new FileTransferCoordinator("./shared_files");
        
        String testFile = "demo_file.txt";
        String testContent = "This is a test file for demonstrating coordinated file transfers.\n";
        
        // Test 1: Simulate upload
        System.out.println("1. Simulating file upload...");
        ByteArrayInputStream uploadStream = new ByteArrayInputStream(testContent.getBytes());
        TransferResult uploadResult = coordinator.handleUpload(testFile, uploadStream, 
                                                               testContent.getBytes().length);
        System.out.println("Upload result: " + uploadResult + "\n");
        
        // Test 2: Simulate multiple concurrent downloads
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
        
        Thread.sleep(2000);
        
        // Test 3: Show statistics
        System.out.println("\n3. Current statistics:");
        System.out.println(coordinator.getStatistics());
        
        // Test 4: File availability check
        System.out.println("\n4. Checking file availability...");
        boolean available = coordinator.waitForFileAvailability(testFile, 5000);
        System.out.println("File available: " + available);
        
        coordinator.shutdown();
        
        System.out.println("\n=== Demo Complete ===");
    }
}
