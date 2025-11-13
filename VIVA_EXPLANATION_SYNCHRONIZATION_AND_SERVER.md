# VIVA PREPARATION: SYNCHRONIZATION HANDLER & SERVER CREATION
## Detailed Step-by-Step Explanation with Networking Concepts

---

## TABLE OF CONTENTS
1. [My Responsibilities Overview](#my-responsibilities-overview)
2. [Part 1: Server Creation (ServerMain.java)](#part-1-server-creation)
3. [Part 2: Synchronization Handler (SynchronizedFileAccess.java)](#part-2-synchronization-handler)
4. [Part 3: File Transfer Coordinator (FileTransferCoordinator.java)](#part-3-file-transfer-coordinator)
5. [Key Networking Concepts Used](#key-networking-concepts-used)
6. [Common Viva Questions & Answers](#common-viva-questions--answers)

---

## MY RESPONSIBILITIES OVERVIEW

I was responsible for **TWO main components**:

### 1. **Server Creation** (ServerMain.java)
- Creating TCP server using ServerSocket
- Managing multiple client connections concurrently
- Thread-per-client model for concurrent handling
- Server lifecycle management (start/stop)

### 2. **Synchronization Handler** (SynchronizedFileAccess.java + FileTransferCoordinator.java)
- Preventing data corruption during concurrent file access
- Thread-safe file read/write operations
- Managing locks and semaphores for concurrency control
- Timeout management for operations

**NOTE:** Client-side code, file upload/download UI, and authentication are NOT my responsibility.

---

## PART 1: SERVER CREATION

### 🎯 Purpose
Create a TCP server that can accept and handle multiple client connections simultaneously without blocking.

### 📚 Networking Concepts Used

#### 1. **TCP/IP Protocol**
- **What:** Reliable, connection-oriented protocol
- **Why:** Ensures all data packets arrive in order without loss
- **How:** Uses 3-way handshake (SYN, SYN-ACK, ACK) to establish connection

#### 2. **ServerSocket**
- **What:** Java class for creating TCP server
- **Why:** Listens for incoming client connection requests
- **How:** Binds to a specific port (9090 in our case)

```java
serverSocket = new ServerSocket(port);  // Binds to port 9090
```

**Step-by-step:**
1. Creates socket
2. Binds to IP address and port
3. Listens for incoming connections

#### 3. **Multithreading - Thread-per-Client Model**
- **What:** Creating a separate thread for each connected client
- **Why:** Server can handle multiple clients simultaneously
- **How:** For each client connection, spawn a new thread

```java
Thread acceptThread = new Thread(new Runnable() {
    @Override
    public void run() {
        acceptConnections();  // Runs in separate thread
    }
});
acceptThread.start();
```

**Why this matters:**
- Without threads: Server can only handle ONE client at a time (blocking)
- With threads: Server can handle MULTIPLE clients concurrently

#### 4. **Socket Communication**
- **What:** Two-way communication channel between client and server
- **Why:** Allows data exchange
- **How:** Uses Input/Output streams

```java
Socket clientSocket = serverSocket.accept();  // Blocking call - waits for client
```

### 🔄 Server Workflow - Step by Step

#### **Step 1: Server Initialization**
```java
public ServerMain(int port, String sharedDirectory) {
    this.port = port;
    this.sharedDirectory = sharedDirectory;
    this.connectedClients = new CopyOnWriteArrayList<>();
    // ... initialization
}
```

**What happens:**
1. Port number set (9090)
2. Shared directory path stored
3. CopyOnWriteArrayList created for thread-safe client list
4. Shared directory created if it doesn't exist

**Networking Concept: Port Binding**
- Port is like an "apartment number" on your computer
- Port 9090 is where our server "lives"
- Clients need to know this port to connect

#### **Step 2: Server Start**
```java
public void start() {
    serverSocket = new ServerSocket(port);  // 1. Create and bind socket
    running = true;                          // 2. Set running flag
    
    Thread acceptThread = new Thread(() -> {  // 3. Create listener thread
        acceptConnections();
    });
    acceptThread.start();                    // 4. Start accepting connections
}
```

**What happens:**
1. **ServerSocket creation:** Opens port 9090 and starts listening
2. **Running flag:** Boolean to control server state
3. **Accept thread:** Dedicated thread that waits for client connections
4. **Thread start:** Begins accepting clients in background

**Networking Concept: Listening State**
- Server enters "LISTEN" state
- Operating system queues incoming connection requests
- Server can accept them one at a time

#### **Step 3: Accepting Client Connections**
```java
private void acceptConnections() {
    while (running) {                        // 1. Keep looping while server runs
        Socket clientSocket = serverSocket.accept();  // 2. BLOCKING - waits for client
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        
        ClientHandler handler = new ClientHandler(clientSocket, this);  // 3. Create handler
        Thread handlerThread = new Thread(handler);  // 4. Create thread for client
        handlerThread.start();               // 5. Start handling client in new thread
    }
}
```

**Step-by-step explanation:**

**Step 3.1:** Server continuously loops waiting for connections
**Step 3.2:** `serverSocket.accept()` - **BLOCKS** here until a client connects
- This is why we run it in a separate thread
- Main server thread would freeze without this

**Step 3.3:** When client connects:
- Creates `ClientHandler` object
- Passes the client socket to it

**Step 3.4:** Creates new thread for this specific client
**Step 3.5:** Starts thread - now this client runs independently

**Networking Concept: Concurrent Connection Handling**
- Each client gets own thread → independent execution
- One slow client doesn't block others
- All clients can send/receive data simultaneously

#### **Step 4: Client Handler (Per-Client Thread)**
```java
private static class ClientHandler implements Runnable {
    @Override
    public void run() {
        // 1. Setup I/O streams
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        // 2. Read commands from client
        String command;
        while ((command = in.readLine()) != null) {
            if (!authenticated) {
                handleAuthentication(command);  // Login/Signup
            } else {
                handleCommand(command);  // LIST_FILES, UPLOAD, DOWNLOAD, CHAT
            }
        }
    }
}
```

**What happens:**

**Step 4.1: Stream Setup**
- **Output Stream (PrintWriter):** Send data TO client
- **Input Stream (BufferedReader):** Receive data FROM client
- Uses socket's built-in streams

**Networking Concept: I/O Streams**
- `OutputStream`: Server → Client (download, responses)
- `InputStream`: Client → Server (commands, upload)
- TCP guarantees order and delivery

**Step 4.2: Command Processing Loop**
- Continuously reads lines from client
- Each line is a command (e.g., "LIST_FILES", "UPLOAD:file.txt:1024")
- Processes command and sends response

**Protocol Design:**
```
Client sends: "LIST_FILES"
Server sends: "FILE_LIST:file1.txt:1024:Mon Nov 13 10:30:00|file2.pdf:2048:Mon Nov 13 11:00:00"
```

#### **Step 5: Server Stop/Cleanup**
```java
public void stop() {
    running = false;  // 1. Stop accepting new clients
    
    // 2. Disconnect all connected clients
    for (ClientHandler handler : connectedClients) {
        handler.disconnect();
    }
    
    // 3. Close server socket
    if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
    }
}
```

**Networking Concept: Graceful Shutdown**
- Close all client connections first
- Then close server socket
- Prevents resource leaks

---

## PART 2: SYNCHRONIZATION HANDLER

### 🎯 Purpose
**THE PROBLEM:** When multiple clients access the same file simultaneously:
- **Scenario 1:** Client A uploads `file.txt` while Client B downloads it → **CORRUPTION**
- **Scenario 2:** Client A and Client B both upload to `file.txt` → **DATA LOSS**

**MY SOLUTION:** Thread-safe synchronization to prevent these issues.

### 📚 Networking Concepts Used for Synchronization

#### 1. **Concurrency Problem**
- **What:** Multiple threads accessing shared resource (files) simultaneously
- **Why it's bad:** Data corruption, inconsistent state, crashes
- **Example:**
  ```
  Thread 1: Write "Hello" to file
  Thread 2: Write "World" to file
  Result without sync: "HeWolrllod" (interleaved - CORRUPTED!)
  Result with sync: "HelloWorld" or "WorldHello" (one at a time - CORRECT)
  ```

#### 2. **ReadWriteLock** - The Core Synchronization Mechanism

**Concept: Reader-Writer Lock**
- **Multiple Readers:** ✅ Many clients can READ the same file simultaneously
- **Single Writer:** ❌ Only ONE client can WRITE at a time
- **No Simultaneous Read/Write:** ❌ Can't read while writing (prevents reading incomplete data)

```java
private final Map<String, ReentrantReadWriteLock> fileLocks;

// For each file, we create a separate lock
private ReentrantReadWriteLock getFileLock(String filename) {
    return fileLocks.computeIfAbsent(filename, k -> {
        return new ReentrantReadWriteLock(true);  // 'true' = fair lock
    });
}
```

**Why separate locks per file?**
- Client A can read `file1.txt` while Client B reads `file2.txt` (different files = no conflict)
- Locks only needed when accessing the SAME file

**Fair Lock Explained:**
- Without fairness: Thread might wait forever (starvation)
- With fairness: First-come, first-served (no starvation)

#### 3. **Semaphore** - Resource Limitation

**Concept:** Limit number of concurrent operations

```java
private final Semaphore operationSemaphore;
private static final int MAX_CONCURRENT_OPERATIONS = 10;

operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS, true);
```

**Why needed?**
- Prevent server overload
- If 1000 clients try to download simultaneously → server crashes
- Semaphore allows maximum 10 concurrent operations
- Operation 11 waits until one of the first 10 finishes

**How Semaphore Works:**
```java
operationSemaphore.acquire();  // Get permit (blocks if no permits available)
try {
    // Perform file operation
} finally {
    operationSemaphore.release();  // Return permit
}
```

**Visual Example:**
```
Semaphore with 3 permits:
[✓] [✓] [✓]  → 3 operations running

Client 4 arrives:
[✓] [✓] [✓] [⏳ Client 4 waiting...]

Client 1 finishes:
[✓] [✓] [✓ Client 4 now running]
```

#### 4. **Future and Callable** - Asynchronous Operations

**Concept:** Non-blocking operations with timeout support

**Why needed?**
- File operations can take time (large files)
- Don't want to block server waiting for completion
- Need timeout to prevent infinite waiting

```java
public Future<Boolean> synchronizedFileRead(String filename, OutputStream targetStream) {
    return executorService.submit(new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
            // Perform actual file read
            return true;  // Success
        }
    });
}
```

**How it works:**
1. Submit task to executor service (thread pool)
2. Returns `Future` immediately (doesn't wait)
3. Caller can check result later or wait with timeout

**Usage with timeout:**
```java
Future<Boolean> future = syncHandler.synchronizedFileRead(file, stream);
Boolean success = future.get(30, TimeUnit.SECONDS);  // Wait max 30 seconds
```

If operation takes > 30 seconds → `TimeoutException` thrown

#### 5. **ExecutorService** - Thread Pool Management

**Concept:** Reuse threads instead of creating new ones

```java
this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_OPERATIONS);
```

**Why thread pool?**
- Creating threads is expensive (memory, CPU)
- Thread pool maintains 10 ready-to-use threads
- When task submitted, assigns to available thread
- When task completes, thread returns to pool (reused)

**Without thread pool:**
```
Task 1 → Create Thread → Execute → Destroy Thread
Task 2 → Create Thread → Execute → Destroy Thread
(Expensive! Slow!)
```

**With thread pool:**
```
Task 1 → Use Thread from Pool → Execute → Return to Pool
Task 2 → Reuse Same Thread → Execute → Return to Pool
(Fast! Efficient!)
```

### 🔄 Synchronization Workflow - Step by Step

#### **SCENARIO 1: File READ Operation (Multiple clients can read simultaneously)**

```java
public Future<Boolean> synchronizedFileRead(String filename, OutputStream targetStream) {
    return executorService.submit(new Callable<Boolean>() {
        public Boolean call() throws Exception {
            ReentrantReadWriteLock lock = getFileLock(filename);  // Step 1
            Lock readLock = lock.readLock();                      // Step 2
            
            try {
                operationSemaphore.acquire();  // Step 3: Get permit
                readLock.lock();               // Step 4: Acquire READ lock
                incrementActiveOperations(filename);  // Step 5: Track operation
                
                // Step 6: Actual file reading
                File file = new File(filename);
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        targetStream.write(buffer, 0, bytesRead);
                    }
                }
                return true;  // Step 7: Success
                
            } finally {
                decrementActiveOperations(filename);  // Step 8: Untrack
                readLock.unlock();                    // Step 9: Release READ lock
                operationSemaphore.release();         // Step 10: Release permit
            }
        }
    });
}
```

**Detailed Step-by-Step:**

**Step 1: Get or Create Lock for File**
```java
ReentrantReadWriteLock lock = getFileLock(filename);
```
- Each file has its own lock
- If first access to this file → create new lock
- If file already has lock → reuse it
- Uses `ConcurrentHashMap.computeIfAbsent()` for thread-safety

**Step 2: Get Read Lock**
```java
Lock readLock = lock.readLock();
```
- ReadWriteLock has TWO locks: `readLock()` and `writeLock()`
- Read lock allows multiple threads

**Step 3: Acquire Semaphore Permit**
```java
operationSemaphore.acquire();
```
- Checks if < 10 operations running
- If yes: proceeds immediately
- If no: **BLOCKS** until permit available

**Step 4: Acquire Read Lock**
```java
readLock.lock();
```
- Multiple readers can acquire this simultaneously
- Blocks if someone has write lock on this file
- Allows concurrent reads of same file

**Step 5: Track Active Operations**
```java
incrementActiveOperations(filename);
```
- Keeps count of how many operations on this file
- Used for monitoring and statistics

**Step 6: Perform Actual Read**
```java
FileInputStream fis = new FileInputStream(file);
byte[] buffer = new byte[4096];  // Read in 4KB chunks
while ((bytesRead = fis.read(buffer)) != -1) {
    targetStream.write(buffer, 0, bytesRead);
}
```
- Opens file
- Reads in chunks (memory efficient for large files)
- Writes to output stream (sends to client)

**Why 4096 bytes?**
- Balance between memory usage and performance
- Reading 1 byte at a time = slow
- Reading entire file at once = high memory
- 4KB is optimal for most file systems

**Step 7: Return Success**
```java
return true;
```
- Indicates successful read
- Returned as `Future<Boolean>`

**Steps 8-10: Cleanup (ALWAYS executed due to `finally`)**
```java
finally {
    decrementActiveOperations(filename);  // Decrease counter
    readLock.unlock();                    // Release read lock
    operationSemaphore.release();         // Return permit
}
```
- Even if exception occurs, cleanup happens
- Prevents deadlocks and resource leaks

**Visual Timeline - 3 Clients Reading Same File:**
```
Time →
Client A: [Get Lock] --------[Read File]--------- [Release]
Client B:    [Get Lock] --------[Read File]--------- [Release]
Client C:       [Get Lock] --------[Read File]--------- [Release]

All happen SIMULTANEOUSLY (shared read lock)
```

#### **SCENARIO 2: File WRITE Operation (Only ONE writer at a time)**

```java
public Future<Boolean> synchronizedFileWrite(String filename, InputStream sourceStream, long fileSize) {
    return executorService.submit(new Callable<Boolean>() {
        public Boolean call() throws Exception {
            ReentrantReadWriteLock lock = getFileLock(filename);
            Lock writeLock = lock.writeLock();  // WRITE lock (exclusive)
            
            try {
                operationSemaphore.acquire();
                writeLock.lock();  // BLOCKS all readers and other writers
                incrementActiveOperations(filename);
                
                // Actual file writing
                File file = new File(filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalBytes = 0;
                    
                    while (totalBytes < fileSize && 
                           (bytesRead = sourceStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                }
                return true;
                
            } finally {
                decrementActiveOperations(filename);
                writeLock.unlock();
                operationSemaphore.release();
            }
        }
    });
}
```

**Key Difference from READ:**
```java
Lock writeLock = lock.writeLock();  // EXCLUSIVE lock
```

**What happens:**
1. **Only ONE thread** can hold write lock
2. Write lock **BLOCKS**:
   - All other write attempts
   - All read attempts
3. Ensures complete write before anyone can read

**Visual Timeline - 3 Clients Writing Same File:**
```
Time →
Client A: [Get Lock] --------[Write File]--------- [Release]
Client B:                                            [Get Lock] --------[Write]--- [Release]
Client C:                                                                            [Get Lock]--[Write]--[Release]

They happen SEQUENTIALLY (exclusive write lock)
```

**Why sequential writes are necessary:**
```
Without sync:
Client A writes: "Hello"
Client B writes: "World"
File content: "HeWolrllod" ❌ CORRUPTED

With sync:
Client A writes: "Hello"
(Client B waits...)
After A finishes, Client B writes: "World"
File content: "HelloWorld" ✓ CORRECT
```

---

## PART 3: FILE TRANSFER COORDINATOR

### 🎯 Purpose
High-level wrapper that connects Server with Synchronization Handler. Adds timeout management and error handling.

### 🔄 How Upload Works with Coordinator

```java
public TransferResult handleUpload(String filename, InputStream inputStream, long fileSize) {
    String filePath = getFilePath(filename);  // Step 1: Get full path
    
    // Step 2: Check if file is in use
    if (syncHandler.isFileInUse(filePath)) {
        System.out.println("[Coordinator] File is currently in use: " + filename);
        // Still proceed but log the concurrent access
    }
    
    try {
        // Step 3: Call synchronized write
        Future<Boolean> future = syncHandler.synchronizedFileWrite(filePath, inputStream, fileSize);
        
        // Step 4: Wait for completion with 30-second timeout
        Boolean success = future.get(30, TimeUnit.SECONDS);
        
        // Step 5: Return result
        if (success) {
            return new TransferResult(true, "File uploaded successfully", filePath);
        } else {
            return new TransferResult(false, "File upload failed", filePath);
        }
        
    } catch (TimeoutException e) {
        return new TransferResult(false, "Upload timeout", filePath);
    } catch (InterruptedException e) {
        return new TransferResult(false, "Upload interrupted", filePath);
    } catch (ExecutionException e) {
        return new TransferResult(false, "Execution error", filePath);
    }
}
```

**Step-by-Step:**

**Step 1: Path Construction**
```java
private String getFilePath(String filename) {
    return new File(sharedDirectory, filename).getPath();
    // Example: "./shared_files" + "document.pdf" = "./shared_files/document.pdf"
}
```

**Step 2: Concurrent Access Check**
- Checks if other clients are using this file
- Logs warning but doesn't block (synchronized write will handle it)
- Good for monitoring/debugging

**Step 3: Submit to Synchronization Handler**
```java
Future<Boolean> future = syncHandler.synchronizedFileWrite(...)
```
- Returns immediately (non-blocking)
- Actual write happens asynchronously in thread pool

**Step 4: Wait with Timeout**
```java
Boolean success = future.get(30, TimeUnit.SECONDS);
```
- Waits maximum 30 seconds for operation
- If completes in 5 seconds → returns after 5 seconds
- If takes > 30 seconds → throws `TimeoutException`

**Why 30 seconds?**
- Prevents indefinite waiting
- Reasonable time for most file sizes
- Configurable if needed for very large files

**Step 5: Error Handling**
Three types of exceptions:
1. **TimeoutException:** Operation took too long
2. **InterruptedException:** Thread was interrupted (rare)
3. **ExecutionException:** Error during actual write (disk full, permissions, etc.)

---

## KEY NETWORKING CONCEPTS USED

### Summary Table

| Concept | Purpose | Implementation | Benefit |
|---------|---------|----------------|---------|
| **TCP/IP** | Reliable communication | `ServerSocket`, `Socket` | Guaranteed delivery |
| **ServerSocket** | Accept connections | `new ServerSocket(port)` | Multi-client support |
| **Multithreading** | Concurrent clients | `Thread`, `Runnable` | Non-blocking server |
| **ReadWriteLock** | Sync file access | `ReentrantReadWriteLock` | Multiple readers, single writer |
| **Semaphore** | Limit concurrency | `Semaphore(10)` | Prevent overload |
| **Future/Callable** | Async operations | `ExecutorService.submit()` | Timeout support |
| **Thread Pool** | Reuse threads | `newFixedThreadPool(10)` | Performance |
| **I/O Streams** | Data transfer | `InputStream`, `OutputStream` | Byte-level transfer |
| **Fair Locking** | Prevent starvation | `ReentrantReadWriteLock(true)` | Equal opportunity |

---

## COMMON VIVA QUESTIONS & ANSWERS

### Q1: Why did you use TCP instead of UDP?

**Answer:**
"I used TCP because file transfer requires **reliability**. TCP provides:
1. **Guaranteed delivery:** All bytes arrive without loss
2. **Order preservation:** File bytes arrive in correct sequence
3. **Error detection:** Corrupted packets are retransmitted
4. **Flow control:** Prevents overwhelming receiver

UDP would be unsuitable because:
- Packets can be lost → incomplete file
- Out-of-order delivery → corrupted file
- No automatic retransmission → manual handling needed

For file sharing, data integrity is critical, so TCP is the right choice."

---

### Q2: Explain how ReadWriteLock prevents data corruption.

**Answer:**
"ReadWriteLock has TWO locks: read lock and write lock.

**Rules:**
1. **Multiple readers allowed:** 5 clients can download simultaneously
2. **Only one writer allowed:** Only 1 client can upload at a time
3. **No simultaneous read/write:** Can't read while writing

**Example scenario:**
- Client A starts downloading `file.txt` (read lock acquired)
- Client B also downloads `file.txt` (second read lock acquired) ✓ Allowed
- Client C tries uploading to `file.txt` (write lock requested) ✗ Blocked
- Client C waits until A and B finish reading
- Then C gets write lock and uploads safely

This prevents:
- Reading incomplete data (half-written file)
- Two clients overwriting each other's uploads
- Data corruption from concurrent writes"

---

### Q3: What is the purpose of Semaphore?

**Answer:**
"Semaphore limits concurrent operations to prevent server overload.

**Problem without Semaphore:**
If 1000 clients download simultaneously:
- 1000 file read operations
- 1000 threads consuming memory
- Server runs out of resources and crashes

**Solution with Semaphore:**
```java
Semaphore operationSemaphore = new Semaphore(10);
```

**How it works:**
- Maximum 10 concurrent file operations allowed
- Client 11 waits until one of first 10 finishes
- Then gets permit and proceeds
- Ensures server never exceeds capacity

**Real-world analogy:**
Restaurant with 10 tables. If full, customers wait. When table becomes free, next customer is seated."

---

### Q4: Why use thread pool instead of creating new threads?

**Answer:**
"Thread creation is expensive in terms of:
1. **Memory:** Each thread needs stack space (usually 1MB)
2. **CPU:** OS overhead for thread creation and context switching
3. **Time:** Creating/destroying threads takes milliseconds

**Without thread pool:**
- 100 clients = create 100 threads, destroy 100 threads
- High memory usage, slow

**With thread pool (10 threads):**
- Create 10 threads once at startup
- Reuse same 10 threads for all clients
- Much faster and memory-efficient

**Example:**
If 100 clients connect:
- Thread pool: 10 threads handle all clients (by queuing tasks)
- Without pool: 100 threads created simultaneously (memory intensive)"

---

### Q5: How does timeout prevent server from hanging?

**Answer:**
"Timeout prevents indefinite waiting using `Future.get(timeout)`.

**Problem scenario:**
- Large file upload (100GB) starts
- Network connection breaks mid-transfer
- Without timeout: server waits forever for completion
- Server resources locked indefinitely

**Solution:**
```java
Future<Boolean> future = syncHandler.synchronizedFileWrite(...);
Boolean success = future.get(30, TimeUnit.SECONDS);  // Max 30 seconds
```

**What happens:**
- If operation completes in 10 seconds → returns successfully
- If takes 50 seconds → throws `TimeoutException` after 30 seconds
- Server can then clean up resources and notify client

**Benefits:**
1. Prevents resource leaks
2. Frees locks for other clients
3. Provides feedback to user (operation failed)
4. Server remains responsive"

---

### Q6: What happens if two clients upload the same filename simultaneously?

**Answer:**
"The ReadWriteLock ensures only ONE upload proceeds at a time:

**Step-by-step:**
1. Client A uploads `document.pdf` → acquires write lock
2. Client B uploads `document.pdf` → tries to acquire write lock
3. Client B's thread **BLOCKS** (waits) because A holds lock
4. Client A completes upload → releases write lock
5. Client B now acquires write lock → uploads (overwrites A's file)

**Result:**
- No data corruption (no interleaved writes)
- B's file overwrites A's file (last upload wins)
- File remains valid (not corrupted)

**Alternative design consideration:**
We could add filename conflict checking before upload:
```java
if (fileExists(filename)) {
    return new TransferResult(false, \"File already exists\", filePath);
}
```
But this is application logic, not my synchronization responsibility."

---

### Q7: Explain CopyOnWriteArrayList - why did you use it?

**Answer:**
"CopyOnWriteArrayList is used for the connected clients list:

```java
private List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
```

**Problem with normal ArrayList:**
- Multiple threads access this list (add/remove clients)
- Without synchronization → `ConcurrentModificationException`

**Why CopyOnWriteArrayList:**
1. **Thread-safe:** Multiple threads can read/write safely
2. **No explicit synchronization needed:** Built-in thread safety
3. **Optimized for reads:** Our use case has many reads (broadcast to all clients), few writes (add/remove client)

**How it works:**
- When adding client: Creates NEW copy of array with new client added
- When removing client: Creates NEW copy without that client
- Readers always see consistent snapshot
- No locks needed for iteration

**Trade-off:**
- Slower writes (copying entire array)
- Faster reads (no locking)
- Perfect for our scenario (many broadcasts, few connect/disconnect events)"

---

### Q8: What is the difference between synchronized keyword and ReentrantLock?

**Answer:**
"Both provide thread synchronization, but ReentrantLock offers more features:

**synchronized keyword:**
```java
public synchronized void method() {
    // Only one thread can execute this at a time
}
```
- Simple to use
- Automatically releases lock at method end
- Cannot interrupt waiting thread
- No fairness guarantee

**ReentrantLock:**
```java
Lock lock = new ReentrantLock(true);  // fair lock
lock.lock();
try {
    // Critical section
} finally {
    lock.unlock();  // Must manually unlock
}
```
- More flexible
- Can try to acquire lock with timeout: `tryLock(5, TimeUnit.SECONDS)`
- Can interrupt waiting threads
- Supports fairness (first-come, first-served)
- Has ReadWriteLock variant (my use case)

**Why I chose ReentrantLock:**
I needed ReadWriteLock for allowing multiple simultaneous reads, which is not possible with synchronized keyword."

---

### Q9: How do you prevent deadlock?

**Answer:**
"Deadlock occurs when threads wait for each other indefinitely. I prevent it using:

**1. Consistent Lock Ordering:**
Always acquire locks in same order:
```java
// Always: Semaphore → then ReadWriteLock
operationSemaphore.acquire();
readLock.lock();
```
Never:
```java
readLock.lock();
operationSemaphore.acquire();  // This could deadlock with above
```

**2. Try-Lock with Timeout:**
```java
if (lock.tryLock(5, TimeUnit.SECONDS)) {
    try {
        // Use resource
    } finally {
        lock.unlock();
    }
} else {
    // Timeout - handle gracefully
}
```

**3. Always Release in Finally Block:**
```java
try {
    lock.lock();
    // Use resource
} finally {
    lock.unlock();  // ALWAYS executes, even if exception
}
```

**4. Fair Locking:**
```java
new ReentrantReadWriteLock(true);  // Fair = no starvation
```

**Example deadlock scenario (prevented by my design):**
- Thread A: has ReadLock on file1, wants WriteLock on file2
- Thread B: has WriteLock on file2, wants ReadLock on file1
- Both wait forever ❌

My design prevents this because each file has independent lock (no cross-file locking)."

---

### Q10: What monitoring/debugging features did you add?

**Answer:**
"I added several monitoring features:

**1. Active Operations Tracking:**
```java
private Map<String, Integer> activeOperations;

public synchronized int getActiveOperations(String filename) {
    return activeOperations.getOrDefault(filename, 0);
}
```
Shows how many operations currently running on each file.

**2. Statistics Method:**
```java
public Map<String, Object> getStatistics() {
    stats.put(\"totalFileLocks\", fileLocks.size());
    stats.put(\"totalActiveOperations\", sum of all operations);
    stats.put(\"availablePermits\", operationSemaphore.availablePermits());
    return stats;
}
```

**3. Detailed Logging:**
```java
System.out.println(\"[SyncHandler] Thread \" + Thread.currentThread().getId() 
                  + \" acquired READ lock for: \" + filename 
                  + \" (Active ops: \" + getActiveOperations(filename) + \")\");
```

**Benefits:**
- Real-time monitoring of server state
- Debugging concurrent access issues
- Performance analysis (how many concurrent operations)
- Identifying bottlenecks (which files are heavily accessed)"

---

## FINAL TIPS FOR VIVA

### Do's:
✅ Start with high-level explanation, then go into details
✅ Use diagrams/timelines if asked to explain
✅ Mention WHY you chose each approach
✅ Relate to real-world analogies
✅ Be honest if you don't know something

### Don'ts:
❌ Don't claim you did parts that aren't yours (client code, authentication)
❌ Don't memorize code line-by-line
❌ Don't use jargon without explaining
❌ Don't be vague - give concrete examples

### Key Phrases to Use:
- "I implemented thread-safe synchronization to prevent data corruption"
- "I used TCP for reliable file transfer"
- "The ReadWriteLock allows concurrent reads but exclusive writes"
- "Semaphore prevents server overload by limiting concurrent operations"
- "Thread pool improves performance by reusing threads"

---

## QUICK REFERENCE CHEAT SHEET

**Your Components:**
1. **ServerMain.java** - TCP server with multithreading
2. **SynchronizedFileAccess.java** - Thread-safe file operations
3. **FileTransferCoordinator.java** - High-level coordination with timeout

**NOT Your Components:**
- Client code
- File upload/download UI
- Authentication system
- Chat functionality

**Key Concepts:**
- TCP/IP, ServerSocket, Socket
- Multithreading, Thread Pool
- ReadWriteLock (multiple readers, single writer)
- Semaphore (limit concurrency)
- Future/Callable (async with timeout)
- Fair Locking (prevent starvation)

---

## Good luck with your viva! 🎓
