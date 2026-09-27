package io.github.kernelperson.postcards;
import java.io.*;
import java.nio.file.*;
final class PaletteFile {
    private static final int PIXELS = 128 * 128;
    static void write(Path file, byte[] pixels) throws IOException {
        if (pixels.length != PIXELS) throw new IllegalArgumentException("Expected one 128x128 map");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".map-", ".tmp");
        try {
            try (var out = new DataOutputStream(Files.newOutputStream(temporary))) {
                out.writeInt(0x524d5031); out.write(pixels);
            }
            try { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    static byte[] read(Path file) throws IOException {
        if (Files.size(file) != PIXELS + 4) throw new IOException("Invalid map file size");
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != 0x524d5031) throw new IOException("Unknown map format");
            byte[] pixels = new byte[PIXELS]; in.readFully(pixels); return pixels;
        }
    }
}
