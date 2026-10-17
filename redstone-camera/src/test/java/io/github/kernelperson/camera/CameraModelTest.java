package io.github.kernelperson.camera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraModelTest {
    // Sampling the opaque owning block or forcing fullbright loses these independent light channels.
    @Test void exposedNeighborsLightBothPartsWithoutChangingTheOwningBlock() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(BlockFace.NORTH);
            when(f.neighbors.get(BlockFace.UP).getLightFromSky()).thenReturn((byte)15);
            when(f.neighbors.get(BlockFace.EAST).getLightFromBlocks()).thenReturn((byte)7);
            f.models.apply(f.target);
            verify(f.body).setBrightness(new Display.Brightness(7,15));
            verify(f.lens).setBrightness(new Display.Brightness(7,15));
            verify(f.block,never()).getLightFromBlocks();verify(f.block,never()).getLightFromSky();
            verify(f.block,never()).setType(any(),anyBoolean());verify(f.dispenser,never()).getInventory();
        }
    }
    // A fixed spawn-time override leaves the model bright after lamps are removed.
    @Test void lightingTimerRefreshesBothPartsAndCloseCancelsIt() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(BlockFace.UP);
            when(f.neighbors.get(BlockFace.UP).getLightFromSky()).thenReturn((byte)15);
            f.models.apply(f.target);
            var plugin=mock(Plugin.class);var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);var task=mock(BukkitTask.class);
            when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(20L),eq(20L))).thenReturn(task);
            f.models.startLighting(plugin);f.models.startLighting(plugin);
            var callback=ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler,times(1)).runTaskTimer(eq(plugin),callback.capture(),eq(20L),eq(20L));
            clearInvocations(f.body,f.lens);
            when(f.neighbors.get(BlockFace.UP).getLightFromSky()).thenReturn((byte)0);
            when(f.neighbors.get(BlockFace.WEST).getLightFromBlocks()).thenReturn((byte)4);
            callback.getValue().run();
            verify(f.body).setBrightness(new Display.Brightness(4,0));verify(f.lens).setBrightness(new Display.Brightness(4,0));
            verify(f.world,times(2)).spawn(any(Location.class),eq(ItemDisplay.class));
            f.models.close();verify(task).cancel();verify(f.body).remove();verify(f.lens).remove();
            clearInvocations(f.body,f.lens);callback.getValue().run();verifyNoInteractions(f.body,f.lens);
        }
    }
    // Looking up light across an owning chunk boundary must never load the neighbor chunk.
    @Test void unloadedNeighborChunksAreNotReadAndRemovedModelsAreNotRefreshed() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(BlockFace.EAST);when(f.world.isChunkLoaded(1,0)).thenReturn(false);when(f.world.isChunkLoaded(0,1)).thenReturn(false);
            when(f.neighbors.get(BlockFace.WEST).getLightFromBlocks()).thenReturn((byte)3);
            f.models.apply(f.target);
            for(BlockFace face:List.of(BlockFace.EAST,BlockFace.SOUTH)) {
                verify(f.neighbors.get(face),never()).getLightFromBlocks();verify(f.neighbors.get(face),never()).getLightFromSky();
            }
            verify(f.body).setBrightness(new Display.Brightness(3,0));
            f.models.remove("one");clearInvocations(f.body,f.lens);f.models.refreshLighting();verifyNoInteractions(f.body,f.lens);
            when(f.world.spawn(any(Location.class),eq(ItemDisplay.class))).thenReturn(f.body,f.lens);
            f.models.apply(f.target);verify(f.body).setBrightness(new Display.Brightness(3,0));verify(f.lens).setBrightness(new Display.Brightness(3,0));
            when(f.world.isChunkLoaded(0,0)).thenReturn(false);clearInvocations(f.body,f.lens);f.models.refreshLighting();verifyNoInteractions(f.body,f.lens);
        }
    }
    // Ignoring block facing, reversing the optics, or moving an entity into the adjacent chunk fails.
    @ParameterizedTest @CsvSource({"SOUTH,0,0,1", "WEST,-1,0,0", "NORTH,0,0,-1", "EAST,1,0,0", "UP,0,1,0", "DOWN,0,-1,0"})
    void physicalLensUsesDispenserFacingAndStaysInOwningChunk(BlockFace face,double x,double y,double z) {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(face);f.models.apply(f.target);
            var transformation=ArgumentCaptor.forClass(Transformation.class);
            verify(f.lens).setTransformation(transformation.capture());
            Transformation t=transformation.getValue();
            // Native Y180 acts first: raw optical front [0,0,+.25] becomes negative Z.
            Vector3f front=new Vector3f(0,0,-.25f).rotate(t.getLeftRotation()).add(t.getTranslation());
            var origin=f.block.getLocation().add(.5,.5,.5);
            assertEquals(.7973125*x,front.x,1e-6);assertEquals(.7973125*y,front.y,1e-6);assertEquals(.7973125*z,front.z,1e-6);
            verify(f.world,times(2)).spawn(eq(origin),eq(ItemDisplay.class));
            verify(f.dispenser,never()).getInventory();verify(f.block,never()).setType(any(),anyBoolean());
            f.models.close();verify(f.body).remove();verify(f.lens).remove();
        }
    }
    @Test void reloadReplacesTransientDisplaysAndFailedSpawnCleansUp() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(BlockFace.SOUTH);f.models.apply(f.target);f.models.remove("one");
            verify(f.body).remove();verify(f.lens).remove();verify(f.dispenser,never()).update(anyBoolean(),anyBoolean());
            var failed=new Fixture(BlockFace.SOUTH);doThrow(new IllegalStateException()).when(failed.lens).setItemStack(any());
            assertThrows(IllegalStateException.class,()->failed.models.apply(failed.target));
            verify(failed.body).remove();verify(failed.lens).remove();
        }
    }
    @Test void nativeModelsAreOptional() {var f=new Fixture(BlockFace.SOUTH);var disabled=new CameraModel(false);disabled.apply(f.target);var plugin=mock(Plugin.class);disabled.startLighting(plugin);verifyNoInteractions(plugin);verify(f.world,never()).spawn(any(Location.class),eq(ItemDisplay.class));}
    static final class Fixture {
        final World world=mock(World.class);final Block block=mock(Block.class);final Dispenser dispenser=mock(Dispenser.class);
        final ItemDisplay body=mock(ItemDisplay.class),lens=mock(ItemDisplay.class);
        final CameraModel models=new CameraModel(true);
        final Map<BlockFace,Block> neighbors=new EnumMap<>(BlockFace.class);
        final CameraTarget target=new CameraTarget(block,"one",new NamespacedKey("test","camera"),new PowerEdge(false));
        Fixture(BlockFace face) {
            when(block.getWorld()).thenReturn(world);when(block.getX()).thenReturn(15);when(block.getY()).thenReturn(64);when(block.getZ()).thenReturn(15);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            for(BlockFace side:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN)) {
                var neighbor=mock(Block.class);neighbors.put(side,neighbor);when(block.getRelative(side)).thenReturn(neighbor);
                when(neighbor.getX()).thenReturn(15+side.getModX());when(neighbor.getZ()).thenReturn(15+side.getModZ());
            }
            when(block.getLocation()).thenAnswer(i->new Location(world,15,64,15));when(block.getState()).thenReturn(dispenser);
            when(world.spawn(any(Location.class),eq(ItemDisplay.class))).thenReturn(body,lens);
            var pdc=mock(PersistentDataContainer.class);when(dispenser.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(argThat(k->k!=null&&k.getKey().equals("yaw")),eq(PersistentDataType.FLOAT))).thenReturn(37f);
            when(pdc.get(argThat(k->k!=null&&k.getKey().equals("pitch")),eq(PersistentDataType.FLOAT))).thenReturn(-21f);
            var data=mock(Directional.class);when(dispenser.getBlockData()).thenReturn(data);when(data.getFacing()).thenReturn(face);
        }
    }
}
