package io.github.kernelperson.pov;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PovMonitorsTest {
    @Test void restoring129MonitorsKeepsOverflowProtectedButWithoutALiveReceiver() {
        var f=new Fixture(false);ItemFrame[] frames=new ItemFrame[129];
        for(int i=0;i<frames.length;i++) {
            var frame=mock(ItemFrame.class);frames[i]=frame;
            when(frame.getUniqueId()).thenReturn(UUID.randomUUID());when(frame.getPersistentDataContainer()).thenReturn(f.tags);
            when(frame.getWorld()).thenReturn(f.world);when(frame.getLocation()).thenReturn(new Location(f.world,5.5,65.5,6));
        }
        when(f.chunk.getEntities()).thenReturn(frames);f.monitors.restore();
        for(int i=0;i<128;i++)verify(frames[i]).setItem(f.receiver,false);
        verify(frames[128],never()).setItem(f.receiver,false);
        verify(frames[128]).setItem(argThat(item->item.getType()==Material.AIR),eq(false));
        var event=new org.bukkit.event.hanging.HangingBreakEvent(frames[128],org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.PHYSICS);
        f.monitors.hanging(event);assertTrue(event.isCancelled());
    }
    @Test void normalPlacementSurvivesItsOwnProtectionEventDispatch() throws Exception {
        var f=new Fixture(false);when(f.player.getUniqueId()).thenReturn(f.owner);
        when(f.player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(true);when(f.plugin.authorized(f.player)).thenReturn(true);
        var item=mock(ItemStack.class);var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);var tags=mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(tags);
        when(tags.get(new NamespacedKey("rendereradminpov","monitor"),PersistentDataType.STRING)).thenReturn(f.owner.toString());
        var original=mock(BlockState.class);when(original.getBlock()).thenReturn(f.board);when(f.board.getState()).thenReturn(original);
        var replaced=mock(BlockState.class);when(replaced.getBlock()).thenReturn(f.base);
        var front=mock(Block.class);when(f.board.getRelative(BlockFace.SOUTH)).thenReturn(front);
        when(front.getLocation()).thenReturn(new Location(f.world,5,65,6));
        when(f.world.spawn(any(Location.class),eq(ItemFrame.class))).thenReturn(f.frame);
        var event=mock(BlockPlaceEvent.class);when(event.getPlayer()).thenReturn(f.player);when(event.getItemInHand()).thenReturn(item);
        when(event.getBlockAgainst()).thenReturn(f.base);when(event.getBlockPlaced()).thenReturn(f.base);when(event.getBlockReplacedState()).thenReturn(replaced);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        var manager=mock(org.bukkit.plugin.PluginManager.class);when(f.server.getPluginManager()).thenReturn(manager);
        doAnswer(i->{Event emitted=i.getArgument(0);if(emitted instanceof BlockPlaceEvent placement)f.monitors.place(placement);return null;}).when(manager).callEvent(any());
        var commit=PovMonitors.class.getDeclaredMethod("commit",BlockPlaceEvent.class,Block.class,Block.class,BlockFace.class);commit.setAccessible(true);
        commit.invoke(f.monitors,event,f.base,f.board,BlockFace.SOUTH);
        verify(f.frame).setItem(f.receiver,false);verify(replaced,never()).update(anyBoolean(),anyBoolean());
    }
    @Test void restartRestoresOwnedScreenProtectsSupportsAndDoesNotOverwriteUnknownBlocks() {
        var f=new Fixture(false);f.monitors.restore();
        verify(f.frame).setItem(f.receiver,false);verify(f.frame).setFixed(true);
        var event=new BlockBreakEvent(f.base,f.player);f.monitors.breakBlock(event);assertTrue(event.isCancelled());
        when(f.player.isSneaking()).thenReturn(true);when(f.player.getUniqueId()).thenReturn(f.owner);
        when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(true);
        when(f.base.getType()).thenReturn(Material.DIAMOND_BLOCK);when(f.board.getType()).thenReturn(Material.STONE);
        // A full inventory must prevent removal before any mutation.
        when(f.inventory.firstEmpty()).thenReturn(-1);
        f.monitors.interact(new PlayerInteractEntityEvent(f.player,f.frame,EquipmentSlot.HAND));
        verify(f.frame,never()).remove();verify(f.base,never()).setType(any(),anyBoolean());
        f.monitors.close();verify(f.frame,never()).remove();
    }
    @Test void strangerCannotRemoveOrWatchAnOwnersScreen() {
        var f=new Fixture(false);f.monitors.restore();when(f.player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(f.player.isSneaking()).thenReturn(true);when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(true);
        assertFalse(f.monitors.watching(f.player));
        var event=new PlayerInteractEntityEvent(f.player,f.frame,EquipmentSlot.HAND);f.monitors.interact(event);
        assertTrue(event.isCancelled());verify(f.frame,never()).remove();
    }
    @Test void invalidRemoteSupportCoordinatesNeverLoadChunks() {
        var f=new Fixture(false);when(f.tags.get(f.baseTag,PersistentDataType.INTEGER_ARRAY)).thenReturn(new int[]{1_000_000,64,1_000_000});
        f.monitors.restore();verify(f.world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    static class Fixture {
        final AdminPovPlugin plugin=mock(AdminPovPlugin.class);final UUID owner=UUID.randomUUID();
        final World world=mock(World.class);final Chunk chunk=mock(Chunk.class);final Server server=mock(Server.class);
        final ItemFrame frame=mock(ItemFrame.class);final Block base=mock(Block.class),board=mock(Block.class);
        final Player player=mock(Player.class);final PlayerInventory inventory=mock(PlayerInventory.class);
        final ItemStack receiver=mock(ItemStack.class);final PersistentDataContainer tags=mock(PersistentDataContainer.class);
        final NamespacedKey baseTag=new NamespacedKey("rendereradminpov","base");
        final PovMonitors monitors;
        Fixture(boolean nativeModels) {
            when(plugin.getName()).thenReturn("RendererAdminPov");when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            when(server.getWorlds()).thenReturn(List.of(world));when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
            when(chunk.getEntities()).thenReturn(new Entity[]{frame});when(frame.getPersistentDataContainer()).thenReturn(tags);
            when(tags.get(new NamespacedKey("rendereradminpov","owner"),PersistentDataType.STRING)).thenReturn(owner.toString());
            when(tags.get(baseTag,PersistentDataType.INTEGER_ARRAY)).thenReturn(new int[]{5,64,5});
            when(tags.has(new NamespacedKey("rendereradminpov","owner"),PersistentDataType.STRING)).thenReturn(true);
            when(frame.getUniqueId()).thenReturn(UUID.randomUUID());when(frame.getWorld()).thenReturn(world);when(frame.getLocation()).thenAnswer(i->new Location(world,5.5,65.5,6));
            when(world.getBlockAt(5,64,5)).thenReturn(base);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(base.getWorld()).thenReturn(world);when(base.getRelative(BlockFace.UP)).thenReturn(board);
            when(base.getType()).thenReturn(Material.OAK_FENCE);when(board.getType()).thenReturn(Material.OAK_PLANKS);
            when(plugin.receiver(owner)).thenReturn(receiver);when(player.getInventory()).thenReturn(inventory);
            monitors=new PovMonitors(plugin,nativeModels);
        }
    }
}
