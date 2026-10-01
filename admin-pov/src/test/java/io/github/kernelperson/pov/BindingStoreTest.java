package io.github.kernelperson.pov;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BindingStoreTest {
    @TempDir Path directory;
    @Test void restartKeepsOnlyOwnershipMapIdentityAndBindingNotFrames() throws Exception {
        var store=new BindingStore(directory.resolve("bindings.yml"));
        UUID owner=UUID.randomUUID(),target=UUID.randomUUID();
        var binding=new BindingStore.Binding(owner,42,target);
        store.save(List.of(binding));assertEquals(List.of(binding),store.load());
        String text=Files.readString(directory.resolve("bindings.yml"));
        assertFalse(text.contains("frame"));assertFalse(text.contains("pixels"));
        store.save(List.of(new BindingStore.Binding(owner,42,null)));
        assertNull(store.load().getFirst().target());
    }
    @Test void invalidMapIdsAndDuplicateMapOwnershipFailClosed() throws Exception {
        var store=new BindingStore(directory.resolve("bindings.yml"));
        assertThrows(Exception.class,()->store.save(List.of(new BindingStore.Binding(UUID.randomUUID(),-1,null))));
        assertThrows(Exception.class,()->store.save(List.of(new BindingStore.Binding(UUID.randomUUID(),5,null),new BindingStore.Binding(UUID.randomUUID(),5,null))));
    }
}
