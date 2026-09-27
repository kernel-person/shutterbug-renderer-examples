package io.github.kernelperson.easel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CanvasStoreTest {
    @TempDir Path folder;
    @Test void retainsProgressAcrossStoreInstancesAndRemovesOnlySelectedCanvas() throws Exception {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        byte[] rgba=new byte[65536];for(int p=3;p<rgba.length;p+=4) rgba[p]=(byte)255;
        var painting=new PigmentCanvas(rgba);painting.stroke(64,64,8,0);
        var store=new CanvasStore(folder);store.save(first,painting);store.save(second,painting);
        PigmentCanvas recovered=new CanvasStore(folder).load(first);
        assertNotNull(recovered);assertEquals(painting.rgb(64,64),recovered.rgb(64,64));
        assertEquals(painting.progress(),recovered.progress());
        store.remove(first);assertNull(store.load(first));assertNotNull(store.load(second));
    }
}
