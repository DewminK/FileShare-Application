package shared;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * Synchronization Handler - Ensures thread-safe access to shared files during concurrent operations.
 * Prevents data corruption when multiple clients read or write simultaneously.
 * 
 * Key Networking Concepts:
 * - ReadWriteLock: Multiple readers OR single writer (no simultaneous read/write)
 * - Semaphore: Limits maximum concurrent operations (prevents resource exhaustion)
 * - Future/Callable: Asynchronous operations with timeout support
 * - Fair locking: Prevents thread starvation in high-concurrency scenarios
 */
public class SynchronizedFileAccess {
    
    private final Map<String, ReentrantReadWriteLock> fileLocks;
    private final ExecutorService executorService;
    private final Map<String, Integer> activeOperations;
    private final ReentrantLock mapLock;
    private final Semaphore operationSemaphore;
    private static final int MAX_CONCURRENT_OPERATIONS = 10;
    
    public SynchronizedFileAccess() {
        this.fileLocks = new ConcurrentHashMap<>();
        this.activeOperations = new ConcurrentHashMap<>();
        this.mapLock = new ReentrantLock();
        this.operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS, true);
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_OPERATIONS);
        
        System.out.println("[SyncHandler] Synchronization Handler initialized with max " 
                          + MAX_CONCURRENT_OPERATIONS + " concurrent operations");
    }
    
    /**
     * Thread-safe method to get or create a ReadWriteLock for a specific file.
     * Uses fair locking to prevent thread starvation.
     */
    private ReentrantReadWriteLock getFileLock(String filename) {
        return fileLocks.computeIfAbsent(filename, k -> {
            System.out.println("[SyncHandler] Created new lock for file: " + filename);
            return new ReentrantReadWriteLock(true);
        });
    }
    
    /**
     * Synchronized file READ operation.
     * Multiple threads can read simultaneously (shared lock), but writes are blocked during reads.
     * Returns Future for asynchronous execution with timeout capability.
     */
    public Future<Boolean> synchronizedFileRead(String filename, OutputStream targetStream) {
        return executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ReentrantReadWriteLock lock = getFileLock(filename);
                Lock readLock = lock.readLock();
                
                try {
                    operationSemaphore.acquire();
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired semaphore for reading: " + filename);
                    
                    readLock.lock();
                    incrementActiveOperations(filename);
                    
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired READ lock for: " + filename 
                                      + " (Active ops: " + getActiveOperations(filename) + ")");
                    
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
     * Synchronized file WRITE operation.
     * Only one thread can write at a time (exclusive lock), blocking all reads and writes.
     * Ensures data integrity during file modifications.
     */
    public Future<Boolean> synchronizedFileWrite(String filename, InputStream sourceStream, long fileSize) {
        return executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ReentrantReadWriteLock lock = getFileLock(filename);
                Lock writeLock = lock.writeLock();
                
                try {
                    operationSemaphore.acquire();
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired semaphore for writing: " + filename);
                    
                    writeLock.lock();
                    incrementActiveOperations(filename);
                    
                    System.out.println("[SyncHandler] Thread " + Thread.currentThread().getId() 
                                      + " acquired WRITE lock for: " + filename 
                                      + " (Active ops: " + getActiveOperations(filename) + ")");
                    
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
     * Synchronized file APPEND operation.
     * Exclusive write lock for appending data without overwriting existing content.
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
                    try (FileOutputStream fos = new FileOutputStream(file, true);
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
     * Check if a file is currently being accessed.
     * Thread-safe using synchronized block.
     */
    public synchronized boolean isFileInUse(String filename) {
        return activeOperations.getOrDefault(filename, 0) > 0;
    }
    
    public synchronized int getActiveOperations(String filename) {
        return activeOperations.getOrDefault(filename, 0);
    }
    
    private synchronized void incrementActiveOperations(String filename) {
        activeOperations.put(filename, activeOperations.getOrDefault(filename, 0) + 1);
    }
    
    private synchronized void decrementActiveOperations(String filename) {
        int current = activeOperations.getOrDefault(filename, 0);
        if (current > 0) {
            activeOperations.put(filename, current - 1);
        }
    }
    
    /**
     * Wait for all operations on a file to complete.
     * Uses polling with timeout to prevent indefinite waiting.
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
     * Graceful shutdown - waits for pending operations before terminating executor service.
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
    
    // Demo: Tests concurrent reads (allowed) and concurrent writes (queued)
    public static void main(String[] args) {
        System.out.println("=== Synchronization Handler Demo ===\n");
        
        SynchronizedFileAccess syncHandler = new SynchronizedFileAccess();
        
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
        
        // Test 1: Concurrent reads (should all execute simultaneously)
        System.out.println("Testing concurrent READS...");
        List<Future<Boolean>> readFutures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int readerNum = i + 1;
            Future<Boolean> future = syncHandler.synchronizedFileRead(testFile, 
                new ByteArrayOutputStream() {
                    @Override
                    public void write(byte[] b, int off, int len) {
                        super.write(b, off, len);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            readFutures.add(future);
        }
        
        readFutures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                System.err.println("Read failed: " + e.getMessage());
            }
        });
        
        System.out.println("\n" + syncHandler.getStatistics() + "\n");
        
        // Test 2: Concurrent writes (should execute sequentially - one at a time)
        System.out.println("Testing concurrent WRITES...");
        List<Future<Boolean>> writeFutures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final int writerNum = i + 1;
            String writeContent = "Write from thread " + writerNum + "\n";
            Future<Boolean> future = syncHandler.synchronizedFileAppend(testFile, 
                                                                        writeContent.getBytes());
            writeFutures.add(future);
        }
        
        writeFutures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                System.err.println("Write failed: " + e.getMessage());
            }
        });
        
        System.out.println("\n" + syncHandler.getStatistics() + "\n");
        
        syncHandler.waitForFileOperations(testFile, 5000);
        syncHandler.shutdown();
        
        System.out.println("\n=== Demo Complete ===");
    }
}
