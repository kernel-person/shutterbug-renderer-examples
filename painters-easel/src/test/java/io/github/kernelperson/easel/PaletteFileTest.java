package io.github.kernelperson.easel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class PaletteFileTest {
    @TempDir Path folder;
    @Test void persistsExactlyOneMapAndRejectsTruncatedOrExtendedData() throws Exception {
        byte[] pixels = new byte[128*128]; pixels[0]=17; pixels[16383]=(byte)200;
        Path file = folder.resolve("12.map");
        PaletteFile.write(file,pixels);
        assertArrayEquals(pixels,PaletteFile.read(file));
        Files.write(file,new byte[12]);
        assertThrows(IOException.class,()->PaletteFile.read(file));
        Files.write(file,new byte[20000]);
        assertThrows(IOException.class,()->PaletteFile.read(file));
    }
    @Test void rejectsWrongDimensionsWithoutOverwritingThePriorMap() throws Exception {
        Path file = folder.resolve("12.map"); byte[] pixels = new byte[16384]; pixels[1]=2;
        PaletteFile.write(file,pixels);
        assertThrows(IllegalArgumentException.class,()->PaletteFile.write(file,new byte[10]));
        assertArrayEquals(pixels,PaletteFile.read(file));
    }
}
