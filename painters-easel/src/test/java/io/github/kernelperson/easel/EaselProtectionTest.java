package io.github.kernelperson.easel;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EaselProtectionTest {
    @TempDir Path folder;
    final NamespacedKey kind=new NamespacedKey("test","kind"), owner=new NamespacedKey("test","owner"), baseTag=new NamespacedKey("test","base");
    PaintersEaselPlugin plugin() throws Exception {
        var plugin=mock(PaintersEaselPlugin.class,CALLS_REAL_METHODS);
        set(plugin,"kind",kind);set(plugin,"ownerTag",owner);set(plugin,"baseTag",baseTag);
        set(plugin,"easels",new HashMap<>());set(plugin,"store",new CanvasStore(folder));
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        return plugin;
    }
    @Test void lateOriginalCancellationCannotCommitExtraModel() throws Exception {
        var plugin=plugin();var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);
        doReturn(server).when(plugin).getServer();when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        List<Runnable> tasks=new ArrayList<>();
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{tasks.add(i.getArgument(1));return null;});
        var item=mock(ItemStack.class);var meta=mock(ItemMeta.class);var data=mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(kind,PersistentDataType.STRING)).thenReturn("easel");
        var event=mock(BlockPlaceEvent.class);var player=mock(Player.class);var base=mock(Block.class);var board=mock(Block.class);var air=mock(Block.class);
        when(event.getItemInHand()).thenReturn(item);when(event.getBlockPlaced()).thenReturn(base);when(event.getPlayer()).thenReturn(player);
        when(event.canBuild()).thenReturn(true);when(player.getFacing()).thenReturn(BlockFace.SOUTH);
        when(base.getType()).thenReturn(Material.OAK_FENCE);when(base.getRelative(BlockFace.UP)).thenReturn(board);
        Material empty=mock(Material.class);when(empty.isAir()).thenReturn(true);
        when(board.getType()).thenReturn(empty);when(board.getRelative(BlockFace.NORTH)).thenReturn(air);when(air.getType()).thenReturn(empty);
        when(board.getState()).thenReturn(mock(BlockState.class));when(event.getBlockReplacedState()).thenReturn(mock(BlockState.class));
        when(base.getWorld()).thenReturn(mock(World.class));when(air.getLocation()).thenReturn(new Location(null,0,0,0));
        plugin.place(event);
        verify(board,never()).setType(any(Material.class),anyBoolean());
        assertEquals(1,tasks.size());
        when(event.isCancelled()).thenReturn(true); // later listener cancels the original event
        tasks.forEach(Runnable::run);
        verify(board,never()).setType(any(Material.class),anyBoolean());
    }
    @Test void corruptCanvasKeepsOwnershipAndDamageProtection() throws Exception {
        var plugin=plugin();var frame=frame();
        Files.write(folder.resolve(frame.getUniqueId()+".cmy"),new byte[]{1,2,3});
        load(plugin,frame);
        assertProtected(plugin,frame);
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(folder.resolve(frame.getUniqueId()+".cmy")));
    }
    @Test void RestoredFramesOverActiveLimitRemainProtected() throws Exception {
        var plugin=plugin();ItemFrame last=null;
        for(int i=0;i<130;i++) {last=frame();load(plugin,last);}
        assertProtected(plugin,last);
    }
    ItemFrame frame() {
        var frame=mock(ItemFrame.class);var tags=mock(PersistentDataContainer.class);var world=mock(World.class);
        when(frame.getUniqueId()).thenReturn(UUID.randomUUID());when(frame.getPersistentDataContainer()).thenReturn(tags);when(frame.getWorld()).thenReturn(world);
        when(tags.get(owner,PersistentDataType.STRING)).thenReturn(UUID.randomUUID().toString());when(tags.get(baseTag,PersistentDataType.INTEGER_ARRAY)).thenReturn(new int[]{1,2,3});
        when(world.getBlockAt(1,2,3)).thenReturn(mock(Block.class));return frame;
    }
    void load(PaintersEaselPlugin plugin,ItemFrame frame) {
        var chunk=mock(Chunk.class);when(chunk.getEntities()).thenReturn(new Entity[]{frame});
        plugin.loaded(new ChunkLoadEvent(chunk,false));
    }
    void assertProtected(PaintersEaselPlugin plugin,ItemFrame frame) {
        var hit=mock(EntityDamageEvent.class);when(hit.getEntity()).thenReturn(frame);plugin.damage(hit);verify(hit).setCancelled(true);
        var interact=mock(PlayerInteractEntityEvent.class);when(interact.getRightClicked()).thenReturn(frame);when(interact.getPlayer()).thenReturn(mock(Player.class));
        plugin.interact(interact);verify(interact).setCancelled(true);
    }
    static void set(Object object,String name,Object value) throws Exception {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
}
