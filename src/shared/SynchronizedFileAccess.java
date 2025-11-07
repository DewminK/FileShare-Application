package shared;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * Member 4 - Synchronization Handler
 * 
 * Ensures thread-safe access to shared files during concurrent operations.
 * Manages synchronization to prevent data corruption when multiple clients 
 * read or write simultaneously.
 * 
 * Networking Concepts Used:
 * - Multithreading and Synchronization using ReentrantLock
 * - Future and Callable Interfaces for asynchronous file transfer tasks
 * - TCP Communication management for multiple streams safely
 * 
 * @author Member 4 - Synchronization Handler
 */
public class SynchronizedFileAccess {
    
    // ReentrantLock for each file - ensures only one write operation at a time per file
    private final Map<String, ReentrantReadWriteLock> fileLocks;
    
    // ExecutorService for handling asynchronous file transfer tasks
    private final ExecutorService executorService;
    
    // Tracks active file operations
    private final Map<String, Integer> activeOperations;
    
    // Lock for managing the file locks map itself
    private final ReentrantLock mapLock;
    
    // Semaphore to limit concurrent file operations
    private final Semaphore operationSemaphore;
    
    // Maximum concurrent file operations
    private static final int MAX_CONCURRENT_OPERATIONS = 10;
    
    /**
     * Constructor initializes the synchronization mechanisms
     */
    public SynchronizedFileAccess() {
        this.fileLocks = new ConcurrentHashMap<>();
        this.activeOperations = new ConcurrentHashMap<>();
        this.mapLock = new ReentrantLock();
        this.operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS, true);
        
        // Create a thread pool for asynchronous operations
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_OPERATIONS);
        
        System.out.println("[SyncHandler] Synchronization Handler initialized with max " 
                          + MAX_CONCURRENT_OPERATIONS + " concurrent operations");
    }
    
    /**
     * Get or create a ReadWriteLock for a specific file
     * Thread-safe method to manage locks
     */
    private ReentrantReadWriteLock getFileLock(String filename) {
        return fileLocks.computeIfAbsent(filename, k -> {
            System.out.println("[SyncHandler] Created new lock for file: " + filename);
            return new ReentrantReadWriteLock(true); // Fair lock
        });
    }
    
    /**
     * Synchronized file read operation
     * Multiple threads can read simultaneously, but no writes during read
     * 
     * @param filename Name of the file to read
     * @param targetStream Output stream to write file data to
     * @return Future<Boolean> indicating success or failure
     */
    public Future<Boolean> synchronizedFileRead(String filename, OutputStream targetStream) {
        return executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ReentrantReadWriteLock lock = getFileLock(filename);
                Lock readLock = lock.readLock();
                
                try {
                    // Acquire semaphore permit to limit concurrent operations
                    operationSemaphore.acquire();
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired semaphore for reading: " + filename);
                    
                    // Acquire read lock - multiple readers allowed
                    readLock.lock();
                    incrementActiveOperations(filename);
                    
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired READ lock for: " + filename 
                                      + " (Active ops: " + getActiveOperations(filename) + ")");
                    
                    // Perform the actual file reading
                    File file = new File(filename);
                    if (!file.exists()) {
                        System.err.println("[SyncHandler] File not found: " + filename);
                        return false;
                    }
                    
                    try (FileInputStream fis = new FileInputStream(file);
                         BufferedInputStream bis = new BufferedInputStream(fis)) {
                        
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytes = 0;
                        
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            targetStream.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }
                        
                        targetStream.flush();
                        System.out.println("[SyncHandler] Successfully read " + totalBytes 
                                          + " bytes from: " + filename);
                        return true;
                    }
                    
                } catch (InterruptedException e) {
                    System.err.println("[SyncHandler] Read operation interrupted for: " + filename);
                    Thread.currentThread().interrupt();
                    return false;
                } catch (IOException e) {
                    System.err.println("[SyncHandler] IO Error reading file: " + filename 
                                      + " - " + e.getMessage());
                    return false;
                } finally {
                    decrementActiveOperations(filename);
                    readLock.unlock();
                    operationSemaphore.release();
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " released READ lock for: " + filename);
                }
            }
        });
    }
    
    /**
     * Synchronized file write operation
     * Only one thread can write at a time, blocks all reads during write
     * 
     * @param filename Name of the file to write
     * @param sourceStream Input stream to read file data from
     * @param fileSize Expected file size in bytes
     * @return Future<Boolean> indicating success or failure
     */
    public Future<Boolean> synchronizedFileWrite(String filename, InputStream sourceStream, long fileSize) {
        return executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ReentrantReadWriteLock lock = getFileLock(filename);
                Lock writeLock = lock.writeLock();
                
                try {
                    // Acquire semaphore permit to limit concurrent operations
                    operationSemaphore.acquire();
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired semaphore for writing: " + filename);
                    
                    // Acquire write lock - exclusive access
                    writeLock.lock();
                    incrementActiveOperations(filename);
                    
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired WRITE lock for: " + filename 
                                      + " (Active ops: " + getActiveOperations(filename) + ")");
                    
                    // Perform the actual file writing
                    File file = new File(filename);
                    File parentDir = file.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(file);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytes = 0;
                        
                        while (totalBytes < fileSize && 
                               (bytesRead = sourceStream.read(buffer, 0, 
                                   (int) Math.min(buffer.length, fileSize - totalBytes))) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }
                        
                        bos.flush();
                        System.out.println("[SyncHandler] Successfully wrote " + totalBytes 
                                          + " bytes to: " + filename);
                        return true;
                    }
                    
                } catch (InterruptedException e) {
                    System.err.println("[SyncHandler] Write operation interrupted for: " + filename);
                    Thread.currentThread().interrupt();
                    return false;
                } catch (IOException e) {
                    System.err.println("[SyncHandler] IO Error writing file: " + filename 
                                      + " - " + e.getMessage());
                    return false;
                } finally {
                    decrementActiveOperations(filename);
                    writeLock.unlock();
                    operationSemaphore.release();
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " released WRITE lock for: " + filename);
                }
            }
        });
    }
    
    /**
     * Synchronized file append operation
     * Exclusive write lock for appending data to file
     * 
     * @param filename Name of the file to append to
     * @param data Data to append
     * @return Future<Boolean> indicating success or failure
     */
    public Future<Boolean> synchronizedFileAppend(String filename, byte[] data) {
        return executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ReentrantReadWriteLock lock = getFileLock(filename);
                Lock writeLock = lock.writeLock();
                
                try {
                    operationSemaphore.acquire();
                    writeLock.lock();
                    incrementActiveOperations(filename);
                    
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " appending to: " + filename);
                    
                    File file = new File(filename);
                    try (FileOutputStream fos = new FileOutputStream(file, true); // append mode
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        bos.write(data);
                        bos.flush();
                        System.out.println("[SyncHandler] Successfully appended " + data.length 
                                          + " bytes to: " + filename);
                        return true;
                    }
                    
                } catch (InterruptedException e) {
                    System.err.println("[SyncHandler] Append operation interrupted for: " + filename);
                    Thread.currentThread().interrupt();
                    return false;
                } catch (IOException e) {
                    System.err.println("[SyncHandler] IO Error appending to file: " + filename 
                                      + " - " + e.getMessage());
                    return false;
                } finally {
                    decrementActiveOperations(filename);
                    writeLock.unlock();
                    operationSemaphore.release();
                }
            }
        });
    }
    
    /**
     * Check if a file is currently being accessed
     * Thread-safe method using synchronized block
     * 
     * @param filename Name of the file to check
     * @return true if file has active operations
     */
    public synchronized boolean isFileInUse(String filename) {
        return activeOperations.getOrDefault(filename, 0) > 0;
    }
    
    /**
     * Get number of active operations on a file
     * Thread-safe method using synchronized block
     * 
     * @param filename Name of the file
     * @return Number of active operations
     */
    public synchronized int getActiveOperations(String filename) {
        return activeOperations.getOrDefault(filename, 0);
    }
    
    /**
     * Increment active operations counter for a file
     */
    private synchronized void incrementActiveOperations(String filename) {
        activeOperations.put(filename, activeOperations.getOrDefault(filename, 0) + 1);
    }
    
    /**
     * Decrement active operations counter for a file
     */
    private synchronized void decrementActiveOperations(String filename) {
        int current = activeOperations.getOrDefault(filename, 0);
        if (current > 0) {
            activeOperations.put(filename, current - 1);
        }
    }
    
    /**
     * Wait for all operations on a file to complete
     * Uses polling with timeout
     * 
     * @param filename Name of the file
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if all operations completed within timeout
     */
    public boolean waitForFileOperations(String filename, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (isFileInUse(filename)) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                System.err.println("[SyncHandler] Timeout waiting for operations on: " + filename);
                return false;
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        System.out.println("[SyncHandler] All operations completed for: " + filename);
        return true;
    }
    
    /**
     * Get statistics about synchronization handler
     */
    public synchronized Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFileLocks", fileLocks.size());
        stats.put("totalActiveOperations", 
                  activeOperations.values().stream().mapToInt(Integer::intValue).sum());
        stats.put("availablePermits", operationSemaphore.availablePermits());
        stats.put("filesInUse", activeOperations.entrySet().stream()
                                   .filter(e -> e.getValue() > 0)
                                   .count());
        return stats;
    }
    
    /**
     * Shutdown the synchronization handler gracefully
     */
    public void shutdown() {
        System.out.println("[SyncHandler] Shutting down Synchronization Handler...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[SyncHandler] Shutdown complete");
    }
    
    /**
     * Test demonstration of synchronization handler
     */
    public static void main(String[] args) {
        System.out.println("=== Synchronization Handler Demo ===\n");
        
        SynchronizedFileAccess syncHandler = new SynchronizedFileAccess();
        
        // Create a test file
        String testFile = "test_sync_file.txt";
        try {
            File file = new File(testFile);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("This is a test file for synchronization demonstration.\n");
                writer.write("Testing concurrent read and write operations.\n");
            }
            System.out.println("Created test file: " + testFile + "\n");
        } catch (IOException e) {
            System.err.println("Failed to create test file: " + e.getMessage());
            return;
        }
        
        // Test concurrent reads (should succeed)
        System.out.println("Testing concurrent READS...");
        List<Future<Boolean>> readFutures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int readerNum = i + 1;
            Future<Boolean> future = syncHandler.synchronizedFileRead(testFile, 
                new ByteArrayOutputStream() {
                    @Override
                    public void write(byte[] b, int off, int len) {
                        super.write(b, off, len);
                        // Simulate some processing time
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            readFutures.add(future);
        }
        
        // Wait for all reads to complete
        readFutures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                System.err.println("Read failed: " + e.getMessage());
            }
        });
        
        System.out.println("\n" + syncHandler.getStatistics() + "\n");
        
        // Test concurrent write (should be exclusive)
        System.out.println("Testing concurrent WRITES...");
        List<Future<Boolean>> writeFutures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final int writerNum = i + 1;
            String writeContent = "Write from thread " + writerNum + "\n";
            Future<Boolean> future = syncHandler.synchronizedFileAppend(testFile, 
                                                                        writeContent.getBytes());
            writeFutures.add(future);
        }
        
        // Wait for all writes to complete
        writeFutures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                System.err.println("Write failed: " + e.getMessage());
            }
        });
        
        System.out.println("\n" + syncHandler.getStatistics() + "\n");
        
        // Wait for all operations and shutdown
        syncHandler.waitForFileOperations(testFile, 5000);
        syncHandler.shutdown();
        
        System.out.println("\n=== Demo Complete ===");
    }
}
