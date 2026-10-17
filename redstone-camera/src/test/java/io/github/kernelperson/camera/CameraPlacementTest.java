package io.github.kernelperson.camera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraPlacementTest {
    @Test void finalCancellationLeavesNoIdentityOrGhostModel()throws Exception {
        var f=new Fixture(BlockFace.NORTH);f.plugin.place(f.event);
        verify(f.models,never()).apply(any());verify(f.dispenser,never()).update(anyBoolean(),anyBoolean());
        when(f.event.isCancelled()).thenReturn(true);f.drain();
        verify(f.models,never()).apply(any());assertTrue(f.stored.isEmpty());assertTrue(f.cameras.isEmpty());
    }
    // Reintroducing the player's raw eye angles or horizontal facing breaks these six cases.
    @ParameterizedTest @MethodSource("io.github.kernelperson.camera.CameraPoseTest#facings")
    void acceptedPlacementKeepsVanillaFacingTowardPlacerAndSavesCanonicalPose(BlockFace face,float yaw,float pitch)throws Exception {
        var f=new Fixture(face);f.plugin.place(f.event);
        verify(f.models,never()).apply(any());assertTrue(f.stored.isEmpty());f.drain();
        assertEquals(face,f.facing,"vanilla dispenser facing, including up/down, is not overwritten");
        assertEquals(yaw,f.stored.get(f.yawKey));assertEquals(pitch,f.stored.get(f.pitchKey));
        String id=(String)f.stored.get(f.key);assertNotNull(UUID.fromString(id));
        assertEquals(id,f.cameras.get(id).id());assertTrue(f.cameras.get(id).valid());
        verify(f.models).apply(f.cameras.get(id));verify(f.dispenser).update(true,false);
        verify(f.dispenser,never()).getInventory();
    }
    @Test void finalBuildDenialLeavesNoIdentityOrGhostModel()throws Exception {
        var f=new Fixture(BlockFace.NORTH);f.plugin.place(f.event);when(f.event.canBuild()).thenReturn(false);f.drain();
        assertTrue(f.stored.isEmpty());assertTrue(f.cameras.isEmpty());verifyNoInteractions(f.models);
    }
    @Test void replacedBlockBeforeDeferredCommitRemainsUntouched()throws Exception {
        var f=new Fixture(BlockFace.NORTH);f.plugin.place(f.event);when(f.block.getBlockData()).thenReturn(mock(Directional.class));f.drain();
        assertTrue(f.stored.isEmpty());assertTrue(f.cameras.isEmpty());verifyNoInteractions(f.models);
    }
    @Test void loadedLegacyCameraNormalizesSavedPoseWithoutChangingIdentityOrInventory()throws Exception {
        var f=new Fixture(BlockFace.WEST);String id="f02411c2-1d9e-499b-859e-9ca031e9ef4c";
        f.stored.put(f.key,id);f.stored.put(f.yawKey,32.75f);f.stored.put(f.pitchKey,-19.25f);
        var chunk=mock(Chunk.class);when(chunk.getTileEntities()).thenReturn(new BlockState[]{f.dispenser});
        var event=mock(ChunkLoadEvent.class);when(event.getChunk()).thenReturn(chunk);f.plugin.loaded(event);
        assertEquals(90f,f.stored.get(f.yawKey));assertEquals(0f,f.stored.get(f.pitchKey));assertEquals(id,f.stored.get(f.key));
        assertEquals(BlockFace.WEST,f.facing);assertEquals(id,f.cameras.get(id).id());
        verify(f.models).apply(f.cameras.get(id));verify(f.dispenser).update(true,false);verify(f.dispenser,never()).getInventory();
    }
    static class Fixture {
        final RedstoneCameraPlugin plugin=mock(RedstoneCameraPlugin.class,CALLS_REAL_METHODS);
        final CameraModel models=mock(CameraModel.class);final BlockPlaceEvent event=mock(BlockPlaceEvent.class);
        final Block block=mock(Block.class);final Dispenser dispenser=mock(Dispenser.class);
        final PersistentDataContainer tags=mock(PersistentDataContainer.class);final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        final Map<String,CameraTarget> cameras=new HashMap<>();final Map<NamespacedKey,Object> stored=new HashMap<>();
        final NamespacedKey key=new NamespacedKey("test","camera"),yawKey=new NamespacedKey("rendererredstonecamera","yaw"),pitchKey=new NamespacedKey("rendererredstonecamera","pitch");
        BlockFace facing;
        Fixture(BlockFace face)throws Exception {
            facing=face;set("cameras",cameras);set("models",models);set("tag",key);
            var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);doReturn(server).when(plugin).getServer();when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{tasks.add(i.getArgument(1));return mock(BukkitTask.class);});
            var item=mock(ItemStack.class);var meta=mock(ItemMeta.class);var itemTags=mock(PersistentDataContainer.class);
            when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(itemTags);
            when(itemTags.has(key,PersistentDataType.STRING)).thenReturn(true);when(event.getItemInHand()).thenReturn(item);
            when(event.getBlockPlaced()).thenReturn(block);when(block.getState()).thenReturn(dispenser);when(dispenser.getBlock()).thenReturn(block);
            when(block.getType()).thenReturn(Material.DISPENSER);var direction=mock(Directional.class);
            when(direction.getFacing()).thenAnswer(i->facing);doAnswer(i->{facing=i.getArgument(0);return null;}).when(direction).setFacing(any());
            when(dispenser.getBlockData()).thenReturn(direction);when(block.getBlockData()).thenReturn(direction);when(dispenser.getPersistentDataContainer()).thenReturn(tags);
            doAnswer(i->{stored.put(i.getArgument(0),i.getArgument(2));return null;}).when(tags).set(any(),any(),any());
            when(tags.get(any(),eq(PersistentDataType.FLOAT))).thenAnswer(i->stored.get(i.getArgument(0)));
            when(tags.get(any(),eq(PersistentDataType.STRING))).thenAnswer(i->stored.get(i.getArgument(0)));
            var player=mock(Player.class);when(event.getPlayer()).thenReturn(player);when(player.getFacing()).thenReturn(BlockFace.SOUTH);
            when(player.getEyeLocation()).thenReturn(new Location(null,0,64,0,32,-21));when(event.canBuild()).thenReturn(true);
            var world=mock(World.class);when(block.getWorld()).thenReturn(world);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        }
        void set(String name,Object value)throws Exception{var field=RedstoneCameraPlugin.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,value);}
        void drain(){while(!tasks.isEmpty())tasks.remove().run();}
    }
}
