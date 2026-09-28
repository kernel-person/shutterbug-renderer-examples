package io.github.kernelperson.easel;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EaselModelTest {
    @Test void modelCanvasMatchesInvisibleMapForEveryCardinalWithoutMovingEntityAcrossChunks() {
        for(BlockFace face:List.of(BlockFace.SOUTH,BlockFace.WEST,BlockFace.NORTH,BlockFace.EAST)) {
            Fixture f=new Fixture();when(f.frame.getFacing()).thenReturn(face);
            f.models.apply(f.frame,f.base);
            var location=ArgumentCaptor.forClass(Location.class);
            verify(f.world).spawn(location.capture(),eq(ItemDisplay.class));
            assertEquals(new org.bukkit.util.Vector(16,65.5,16),location.getValue().toVector());
            var transformation=ArgumentCaptor.forClass(Transformation.class);
            verify(f.display).setTransformation(transformation.capture());
            // Model raw map centre [8,24,10], centred /16 => [0,1,.125].
            // Native ItemDisplay adds Y180 before entity yaw is applied.
            Vector3f point=new Vector3f(0,1,-.125f).add(transformation.getValue().getTranslation());
            point.rotateY((float)Math.toRadians(-location.getValue().getYaw()));
            var normal=face.getDirection();
            assertEquals(-.023359375*normal.getX(),point.x,1e-6);
            assertEquals(0,point.y,1e-6);
            assertEquals(-.023359375*normal.getZ(),point.z,1e-6);
            verify(f.display).setPersistent(false);
            verify(f.display).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            verify(f.base).setType(Material.BARRIER,false);
            verify(f.board).setType(Material.BARRIER,false);
            verify(f.frame).setVisible(false);
            verify(f.frame,never()).setItem(any());
        }
    }
    @Test void repeatedLoadDoesNotDuplicateModelAndUnloadRemovesOnlyTheVisual() {
        Fixture f=new Fixture();f.models.apply(f.frame,f.base);f.models.apply(f.frame,f.base);
        verify(f.world,times(1)).spawn(any(Location.class),eq(ItemDisplay.class));
        f.models.unload(f.frame);
        verify(f.display).remove();verify(f.frame,never()).remove();
        verify(f.base,never()).setType(Material.AIR);
    }
    @Test void removalClearsOnlyOwnedBarrierBlocks() {
        Fixture f=new Fixture();f.marked=true;
        when(f.base.getType()).thenReturn(Material.BARRIER);when(f.board.getType()).thenReturn(Material.STONE);
        f.models.remove(f.frame,f.base);
        verify(f.base).setType(Material.AIR,false);verify(f.board,never()).setType(any(),anyBoolean());
    }
    @Test void disabledModelRestoresLegacySupportsWithoutChangingMap() {
        Fixture f=new Fixture();f.marked=true;
        when(f.base.getType()).thenReturn(Material.BARRIER);when(f.board.getType()).thenReturn(Material.BARRIER);
        new EaselModel(f.tag,null).apply(f.frame,f.base);
        verify(f.base).setType(Material.OAK_FENCE,false);verify(f.board).setType(Material.OAK_PLANKS,false);
        verify(f.frame).setVisible(true);verify(f.frame,never()).setItem(any());
    }
    @Test void unknownBlocksAreNotConvertedAndSpawnFailureDoesNotChangeSupports() {
        Fixture unrelated=new Fixture();when(unrelated.base.getType()).thenReturn(Material.STONE);
        assertThrows(IllegalStateException.class,()->unrelated.models.apply(unrelated.frame,unrelated.base));
        verify(unrelated.base,never()).setType(any(),anyBoolean());
        Fixture failed=new Fixture();doThrow(new IllegalStateException("spawn failed")).when(failed.display).setItemStack(any());
        assertThrows(IllegalStateException.class,()->failed.models.apply(failed.frame,failed.base));
        verify(failed.display).remove();verify(failed.base,never()).setType(any(),anyBoolean());
    }
    static class Fixture {
        final NamespacedKey tag=new NamespacedKey("rendererexample","native-model");
        final World world=mock(World.class);final ItemFrame frame=mock(ItemFrame.class);
        final ItemDisplay display=mock(ItemDisplay.class);final Block base=mock(Block.class),board=mock(Block.class);
        final PersistentDataContainer data=mock(PersistentDataContainer.class);
        final EaselModel models=new EaselModel(tag,mock(ItemStack.class));
        boolean marked;
        Fixture() {
            when(frame.getUniqueId()).thenReturn(UUID.randomUUID());when(frame.getWorld()).thenReturn(world);
            when(frame.getLocation()).thenReturn(new Location(world,16,65.5,16));when(frame.getFacing()).thenReturn(BlockFace.SOUTH);
            when(frame.getPersistentDataContainer()).thenReturn(data);when(frame.isVisible()).thenReturn(true);
            when(data.has(tag,PersistentDataType.BYTE)).thenAnswer(i->marked);
            doAnswer(i->{marked=true;return null;}).when(data).set(eq(tag),eq(PersistentDataType.BYTE),anyByte());
            when(base.getRelative(BlockFace.UP)).thenReturn(board);
            when(base.getType()).thenReturn(Material.OAK_FENCE);when(board.getType()).thenReturn(Material.OAK_PLANKS);
            when(base.getState()).thenReturn(mock(BlockState.class));when(board.getState()).thenReturn(mock(BlockState.class));
            when(world.spawn(any(Location.class),eq(ItemDisplay.class))).thenReturn(display);when(display.isValid()).thenReturn(true);
        }
    }
}
