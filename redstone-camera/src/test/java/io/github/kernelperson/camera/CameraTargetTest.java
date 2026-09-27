package io.github.kernelperson.camera;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CameraTargetTest {
    @Test void destroyedReplacedAndUnloadedCamerasCannotReceiveOldResults() {
        Block block=mock(Block.class); World world=mock(World.class); Dispenser tile=mock(Dispenser.class);
        var key=new NamespacedKey("example","camera");
        var data=mock(PersistentDataContainer.class);
        when(block.getWorld()).thenReturn(world); when(world.isChunkLoaded(0,0)).thenReturn(true);
        when(block.getType()).thenReturn(Material.DISPENSER); when(block.getState()).thenReturn(tile);
        when(tile.getPersistentDataContainer()).thenReturn(data);
        when(data.get(key,PersistentDataType.STRING)).thenReturn("first");
        var target=new CameraTarget(block,"first",key,new PowerEdge(false));
        assertTrue(target.valid());
        when(data.get(key,PersistentDataType.STRING)).thenReturn("replacement");
        assertFalse(target.valid());
        when(data.get(key,PersistentDataType.STRING)).thenReturn("first");
        when(block.getType()).thenReturn(Material.AIR); assertFalse(target.valid());
        when(block.getType()).thenReturn(Material.DISPENSER);
        when(world.isChunkLoaded(0,0)).thenReturn(false);
        clearInvocations(block);
        assertFalse(target.valid()); verify(block,never()).getState();
    }
}
