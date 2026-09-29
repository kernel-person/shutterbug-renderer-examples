package io.github.kernelperson.pov;

import org.bukkit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PovCaptureTest {
    @Test void missingChunksAreRejectedWithoutFetchingOrGeneratingThem() {
        World world=mock(World.class);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var view=new Location(world,0,64,0,23,-17);
        assertTrue(PovCapture.loaded(view));
        when(world.isChunkLoaded(-1,0)).thenReturn(false);assertFalse(PovCapture.loaded(view));
        verify(world,never()).getChunkAt(anyInt(),anyInt());
        verify(world,never()).loadChunk(anyInt(),anyInt());
    }
}
