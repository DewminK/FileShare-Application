package shared;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Set;

/**
 * Handles efficient file I/O and transfer using Java NIO.
 * Supports both blocking and non-blocking channels with Selector-based I/O.
 * For non-blocking SelectableChannels, uses Selector for efficient event-driven I/O.
 * For blocking channels, uses traditional read/write operations.
 */
public class FileHandler {

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB direct buffer
    private static final long SELECTOR_TIMEOUT_MS = 5000; // 5 seconds timeout for selector operations
    private static final long TOTAL_TIMEOUT_MS = 60000; // 60 seconds total timeout

    /**
     * Reads an entire file into a byte array.
     * Suitable for small to medium-sized files that can fit in memory.
     *
     * @param path the path to the file to read
     * @return a byte array containing the complete file contents
     * @throws IOException if the file doesn't exist, is not a regular file,
     *                     is too large for a byte array, or an I/O error occurs
     */
    public byte[] readFile(Path path) throws IOException {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("File not found: " + path);
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size > Integer.MAX_VALUE) {
                throw new IOException("File too large for byte[]: " + size);
            }
            byte[] data = new byte[(int) size];
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break;
            }
            return data;
        }
    }

    /**
     * Writes a byte array to a file, creating the file if it doesn't exist or overwriting if it does.
     * Parent directories are created automatically if they don't exist.
     *
     * @param path the path where the file will be written
     * @param data the byte array to write to the file
     * @throws IOException if an I/O error occurs during writing
     */
    public void writeFile(Path path, byte[] data) throws IOException {
        Files.createDirectories(path.getParent());
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    /**
     * Copies a file from source to target using efficient NIO buffer-based transfer.
     * Parent directories of the target are created automatically if they don't exist.
     *
     * @param source the path to the source file
     * @param target the path where the file will be copied
     * @return the total number of bytes transferred
     * @throws IOException if the source file doesn't exist or an I/O error occurs
     */
    public long copyFile(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Source missing: " + source);
        }
        Files.createDirectories(target.getParent());
        try (FileChannel in = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(target,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {

            long transferred = 0;
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            while (in.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    transferred += out.write(buffer);
                }
                buffer.clear();
            }
            return transferred;
        }
    }

    /**
     * Sends a file's contents to a WritableByteChannel such as a SocketChannel.
     * Automatically detects channel blocking mode and uses appropriate transfer method:
     * non-blocking channels use Selector-based event-driven I/O, while blocking channels
     * use traditional buffer-based transfer.
     *
     * @param file the path to the file to send
     * @param dest the destination channel to write to (e.g., SocketChannel)
     * @return the total number of bytes sent
     * @throws IOException if the file doesn't exist or an I/O error occurs during transfer
     */
    public long sendFile(Path file, WritableByteChannel dest) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("File missing: " + file);
        }

        // Check if channel is non-blocking and selectable
        boolean isNonBlocking = dest instanceof SelectableChannel &&
                                !((SelectableChannel) dest).isBlocking();

        if (isNonBlocking) {
            return sendFileNonBlocking(file, (SelectableChannel) dest);
        } else {
            return sendFileBlocking(file, dest);
        }
    }

    /**
     * Sends a file using blocking I/O operations.
     * This method uses traditional buffer-based transfer suitable for blocking channels.
     *
     * @param file the path to the file to send
     * @param dest the destination channel to write to
     * @return the total number of bytes sent
     * @throws IOException if an I/O error occurs during transfer
     */
    private long sendFileBlocking(Path file, WritableByteChannel dest) throws IOException {
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = 0;
            long size = in.size();
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            while (position < size) {
                buffer.clear();
                long remaining = size - position;
                if (remaining < buffer.capacity()) {
                    buffer.limit((int) remaining);
                }

                in.position(position);
                int read = in.read(buffer);
                if (read == -1) break;

                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = dest.write(buffer);
                    position += written;
                }
            }
            return position;
        }
    }

    /**
     * Sends a file using non-blocking I/O with Selector for efficient event-driven transfer.
     * This method registers the channel with a Selector and waits for write-ready events,
     * eliminating busy-waiting and improving CPU efficiency.
     *
     * @param file the path to the file to send
     * @param dest the non-blocking selectable channel to write to
     * @return the total number of bytes sent
     * @throws IOException if an I/O error occurs or transfer times out
     */
    private long sendFileNonBlocking(Path file, SelectableChannel dest) throws IOException {
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ);
             Selector selector = Selector.open()) {

            long position = 0;
            long size = in.size();
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            long startTime = System.currentTimeMillis();

            SelectionKey key = dest.register(selector, SelectionKey.OP_WRITE);

            while (position < size) {
                if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS) {
                    throw new IOException("Transfer timeout: exceeded " + TOTAL_TIMEOUT_MS + "ms");
                }

                if (!buffer.hasRemaining()) {
                    buffer.clear();
                    long remaining = size - position;
                    if (remaining < buffer.capacity()) {
                        buffer.limit((int) remaining);
                    }

                    in.position(position);
                    int read = in.read(buffer);
                    if (read == -1) break;
                    buffer.flip();
                }

                int readyChannels = selector.select(SELECTOR_TIMEOUT_MS);

                if (readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey selectedKey = keyIterator.next();
                    keyIterator.remove();

                    if (selectedKey.isWritable()) {
                        WritableByteChannel channel = (WritableByteChannel) selectedKey.channel();
                        int written = channel.write(buffer);
                        position += written;
                    }
                }
            }

            key.cancel();
            return position;
        }
    }

    /**
     * Receives data from a ReadableByteChannel and writes to a file until the expected size is reached.
     * Automatically detects channel blocking mode and uses appropriate transfer method:
     * non-blocking channels use Selector-based event-driven I/O, while blocking channels
     * use traditional buffer-based transfer.
     *
     * @param src the source channel to read from (e.g., SocketChannel)
     * @param target the path where the received file will be saved
     * @param expectedSize the expected number of bytes to receive
     * @return the total number of bytes received
     * @throws IOException if an I/O error occurs or the received size doesn't match expected size
     * @throws IllegalArgumentException if expectedSize is negative
     */
    public long receiveFile(ReadableByteChannel src, Path target, long expectedSize) throws IOException {
        if (expectedSize < 0) throw new IllegalArgumentException("expectedSize < 0");

        // Check if channel is non-blocking and selectable
        boolean isNonBlocking = src instanceof SelectableChannel &&
                                !((SelectableChannel) src).isBlocking();

        if (isNonBlocking) {
            return receiveFileNonBlocking((SelectableChannel) src, target, expectedSize);
        } else {
            return receiveFileBlocking(src, target, expectedSize);
        }
    }

    /**
     * Receives a file using blocking I/O operations.
     * Reads data from the source channel and writes to the target file until expectedSize is reached.
     *
     * @param src the source channel to read from
     * @param target the path where the file will be saved
     * @param expectedSize the expected number of bytes to receive
     * @return the total number of bytes received
     * @throws IOException if an I/O error occurs or received size doesn't match expected
     */
    private long receiveFileBlocking(ReadableByteChannel src, Path target, long expectedSize) throws IOException {
        Files.createDirectories(target.getParent());
        try (FileChannel out = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

            long received = 0;
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            while (received < expectedSize) {
                int read = src.read(buffer);
                if (read == -1) break;

                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = out.write(buffer);
                    received += written;
                }
                buffer.clear();
            }

            if (received != expectedSize) {
                throw new IOException("Incomplete file. Expected " + expectedSize + " got " + received);
            }
            return received;
        }
    }

    /**
     * Receives a file using non-blocking I/O with Selector for efficient event-driven transfer.
     * This method registers the channel with a Selector and waits for read-ready events,
     * providing efficient I/O without blocking threads.
     *
     * @param src the non-blocking selectable channel to read from
     * @param target the path where the file will be saved
     * @param expectedSize the expected number of bytes to receive
     * @return the total number of bytes received
     * @throws IOException if an I/O error occurs, transfer times out, or received size doesn't match expected
     */
    private long receiveFileNonBlocking(SelectableChannel src, Path target, long expectedSize) throws IOException {
        Files.createDirectories(target.getParent());

        try (FileChannel out = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
             Selector selector = Selector.open()) {

            long received = 0;
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            long startTime = System.currentTimeMillis();

            SelectionKey key = src.register(selector, SelectionKey.OP_READ);

            while (received < expectedSize) {
                if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS) {
                    throw new IOException("Transfer timeout: exceeded " + TOTAL_TIMEOUT_MS + "ms");
                }

                int readyChannels = selector.select(SELECTOR_TIMEOUT_MS);

                if (readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey selectedKey = keyIterator.next();
                    keyIterator.remove();

                    if (selectedKey.isReadable()) {
                        ReadableByteChannel channel = (ReadableByteChannel) selectedKey.channel();
                        int read = channel.read(buffer);

                        if (read == -1) {
                            key.cancel();
                            if (received != expectedSize) {
                                throw new IOException("Incomplete file. Expected " + expectedSize + " got " + received);
                            }
                            return received;
                        } else if (read > 0) {
                            buffer.flip();
                            while (buffer.hasRemaining()) {
                                int written = out.write(buffer);
                                received += written;
                            }
                            buffer.clear();
                        }
                    }
                }
            }

            key.cancel();

            if (received != expectedSize) {
                throw new IOException("Incomplete file. Expected " + expectedSize + " got " + received);
            }
            return received;
        }
    }

}
