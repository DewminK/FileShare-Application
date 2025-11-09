package shared;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Handles efficient file I/O and transfer using Java NIO.
 */
public class FileHandler {

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB direct buffer

    /**
     * Reads entire file into a byte array (for small/medium files).
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
     * Writes byte array to file (overwrites or creates).
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
     * Copies file using buffer loop. Returns bytes transferred.
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
     * Sends a file's contents to a WritableByteChannel (e.g. SocketChannel).
     * Returns bytes sent.
     */
    public long sendFile(Path file, WritableByteChannel dest) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("File missing: " + file);
        }
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = 0;
            long size = in.size();
            // Use FileChannel.transferTo for efficiency; fallback loop if partial.
            while (position < size) {
                long sent = in.transferTo(position, size - position, dest);
                if (sent <= 0) {
                    // Fallback manual copy
                    sent = manualPump(in, dest, position);
                }
                position += sent;
            }
            return position;
        }
    }

    /**
     * Receives data from a ReadableByteChannel and writes to file until expectedSize reached.
     * Returns bytes received.
     */
    public long receiveFile(ReadableByteChannel src, Path target, long expectedSize) throws IOException {
        if (expectedSize < 0) throw new IllegalArgumentException("expectedSize < 0");
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
                    received += out.write(buffer);
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
     * Manual copy fallback when transferTo stalls.
     */
    private long manualPump(FileChannel in, WritableByteChannel dest, long startPos) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        in.position(startPos);
        int read = in.read(buffer);
        if (read <= 0) return 0;
        buffer.flip();
        long written = 0;
        while (buffer.hasRemaining()) {
            written += dest.write(buffer);
        }
        return written;
    }
}
