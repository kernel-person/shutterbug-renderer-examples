package io.github.kernelperson.camera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CameraPlacementTest {
    @Test void finalCancellationLeavesNoIdentityOrGhostModel()throws Exception {
        var f=new Fixture();f.plugin.place(f.event);
        verify(f.models,never()).apply(any());verify(f.dispenser,never()).update(anyBoolean(),anyBoolean());
        when(f.event.isCancelled()).thenReturn(true);f.drain();
        verify(f.models,never()).apply(any());verify(f.tags,never()).set(any(),any(),any());
    }
    @Test void acceptedPlacementCommitsPoseAndVisualOnlyAfterListenersFinish()throws Exception {
        var f=new Fixture();f.plugin.place(f.event);verify(f.models,never()).apply(any());f.drain();
        verify(f.models).apply(any());verify(f.dispenser).update(true,false);
    }
    static class Fixture {
        final RedstoneCameraPlugin plugin=mock(RedstoneCameraPlugin.class,CALLS_REAL_METHODS);
        final CameraModel models=mock(CameraModel.class);final BlockPlaceEvent event=mock(BlockPlaceEvent.class);
        final Block block=mock(Block.class);final Dispenser dispenser=mock(Dispenser.class);
        final PersistentDataContainer tags=mock(PersistentDataContainer.class);final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        Fixture()throws Exception {
            set("cameras",new HashMap<String,CameraTarget>());set("models",models);var key=new NamespacedKey("test","camera");set("tag",key);
            var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);doReturn(server).when(plugin).getServer();when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{tasks.add(i.getArgument(1));return mock(BukkitTask.class);});
            var item=mock(ItemStack.class);var meta=mock(ItemMeta.class);var itemTags=mock(PersistentDataContainer.class);
            when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(itemTags);
            when(itemTags.has(key,PersistentDataType.STRING)).thenReturn(true);when(event.getItemInHand()).thenReturn(item);
            when(event.getBlockPlaced()).thenReturn(block);when(block.getState()).thenReturn(dispenser);when(dispenser.getBlock()).thenReturn(block);
            when(block.getType()).thenReturn(Material.DISPENSER);var direction=mock(Directional.class);
            when(dispenser.getBlockData()).thenReturn(direction);when(block.getBlockData()).thenReturn(direction);when(dispenser.getPersistentDataContainer()).thenReturn(tags);
            var player=mock(Player.class);when(event.getPlayer()).thenReturn(player);when(player.getFacing()).thenReturn(BlockFace.SOUTH);
            when(player.getEyeLocation()).thenReturn(new Location(null,0,64,0,32,-21));when(event.canBuild()).thenReturn(true);
            var world=mock(World.class);when(block.getWorld()).thenReturn(world);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        }
        void set(String name,Object value)throws Exception{var field=RedstoneCameraPlugin.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,value);}
        void drain(){while(!tasks.isEmpty())tasks.remove().run();}
    }
}
