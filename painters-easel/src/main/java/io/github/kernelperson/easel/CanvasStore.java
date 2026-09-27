package io.github.kernelperson.easel;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
final class CanvasStore {
    private final Path folder;
    CanvasStore(Path folder) { this.folder=folder; }
    void save(UUID id,PigmentCanvas canvas) throws IOException {
        Files.createDirectories(folder);
        Path temporary=Files.createTempFile(folder,".canvas-",".tmp");
        try {
            try(var stream=Files.newOutputStream(temporary)) {canvas.write(stream);}
            try {Files.move(temporary,file(id),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored) {Files.move(temporary,file(id),StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
    PigmentCanvas load(UUID id) throws IOException {
        if(!Files.exists(file(id))) return null;
        if(Files.size(file(id))!=98308) throw new IOException("Invalid canvas size");
        try(var stream=Files.newInputStream(file(id))) {return PigmentCanvas.read(stream);}
    }
    void remove(UUID id) throws IOException {Files.deleteIfExists(file(id));}
    private Path file(UUID id) {return folder.resolve(id+".cmy");}
}
