package io.github.kernelperson.easel;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EaselSwingTest {
    @Test void realBrushSwingPaintsWithoutAnyDamageEvent() throws Exception {
        Fixture f=new Fixture(); f.swing(); assertTrue(f.canvas.progress()>0);
        var updates=inOrder(f.maps,f.player);
        updates.verify(f.maps).update(eq(7),any(byte[].class));
        updates.verify(f.player).sendMap(f.view);
        double first=f.canvas.progress();
        // Mockito verification and first palette conversion can exceed 100ms on slow hosts.
        // Freeze the debounce window instead of depending on test-machine speed.
        EaselProtectionTest.set(f.easel,"lastStroke",System.nanoTime()+1_000_000_000L);
        f.swing();
        assertEquals(first,f.canvas.progress(),"duplicate swing packets do not double-paint");
        verify(f.player,times(1)).sendMap(f.view);
    }
    @Test void cancelledOffhandSpectatorAndOtherOwnerCannotPaint() throws Exception {
        Fixture cancelled=new Fixture();cancelled.event.setCancelled(true);cancelled.swing();assertEquals(0,cancelled.canvas.progress());
        Fixture offhand=new Fixture();offhand.event=new PlayerAnimationEvent(offhand.player,PlayerAnimationType.OFF_ARM_SWING);
        offhand.swing();assertEquals(0,offhand.canvas.progress());
        Fixture spectator=new Fixture();when(spectator.player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        spectator.swing();assertEquals(0,spectator.canvas.progress());
        Fixture stranger=new Fixture();when(stranger.player.getUniqueId()).thenReturn(UUID.randomUUID());
        stranger.swing();assertEquals(0,stranger.canvas.progress());
    }
    @Test void wallBetweenBrushAndCanvasBlocksPainting() throws Exception {
        Fixture f=new Fixture();
        when(f.world.rayTraceBlocks(any(Location.class),any(org.bukkit.util.Vector.class),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true)))
                .thenReturn(new RayTraceResult(new org.bukkit.util.Vector(0,0,1),mock(Block.class),BlockFace.SOUTH));
        f.swing();assertEquals(0,f.canvas.progress());
        verify(f.player,never()).sendMap(any());
    }
    @Test void backingBoardBehindCanvasDoesNotBlockPainting() throws Exception {
        Fixture f=new Fixture();
        when(f.world.rayTraceBlocks(any(Location.class),any(org.bukkit.util.Vector.class),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true)))
                .thenReturn(new RayTraceResult(new org.bukkit.util.Vector(0,0,-.03),mock(Block.class),BlockFace.SOUTH));
        f.swing();assertTrue(f.canvas.progress()>0);
    }
    @Test void repeatedRightClicksOnReadyCanvasDoNotSpamChat() throws Exception {
        Fixture f=new Fixture();
        for(int i=0;i<5;i++) {
            var event=new PlayerInteractEntityEvent(f.player,f.frame,EquipmentSlot.HAND);
            f.plugin.interact(event);
            assertTrue(event.isCancelled(),"frame remains protected");
        }
        verify(f.player,never()).sendMessage(anyString());
    }
    @Test void heldRightClickDragPaintsConnectedBroadStroke() throws Exception {
        Fixture f=new Fixture();
        when(f.player.getEyeLocation()).thenReturn(new Location(f.world,-.34375,0,2,180,0));
        f.plugin.interact(new PlayerInteractEntityEvent(f.player,f.frame,EquipmentSlot.HAND));
        assertTrue(f.canvas.progress()>0,"right-click must paint, not just print instructions");
        EaselProtectionTest.set(f.easel,"lastStroke",System.nanoTime()-200_000_000L);
        when(f.player.getEyeLocation()).thenReturn(new Location(f.world,.28125,0,2,180,0));
        f.plugin.interact(new PlayerInteractEntityEvent(f.player,f.frame,EquipmentSlot.HAND));
        assertEquals(0,(f.canvas.rgb(64,64)>>16)&255,"drag fills between input samples");
        assertEquals(0,(f.canvas.rgb(64,74)>>16)&255,"brush is broad, not a tiny dot");
        verify(f.player,never()).sendMessage(anyString());
    }
    @Test void heldRightClickDoesNotRetryFailedCaptureOnEveryPacket() throws Exception {
        Fixture f=new Fixture();EaselProtectionTest.set(f.easel,"canvas",null);
        when(f.player.isOnline()).thenReturn(true);
        var renders=mock(RenderSession.class);EaselProtectionTest.set(f.plugin,"renders",renders);
        when(renders.request(anyString(),any(),any(),any(),any())).thenAnswer(invocation->{
            Consumer<String> failure=invocation.getArgument(4);
            failure.accept("This view exceeds the renderer memory limit.");
            return false;
        });
        when(f.frame.getItem()).thenReturn(new ItemStack(Material.FILLED_MAP));
        for(int i=0;i<5;i++) f.plugin.interact(new PlayerInteractEntityEvent(f.player,f.frame,EquipmentSlot.HAND));
        verify(renders,times(1)).request(anyString(),any(),any(),any(),any());
        verify(f.player,times(1)).sendMessage("This view exceeds the renderer memory limit.");
    }
    static final class Fixture {
        final PaintersEaselPlugin plugin=mock(PaintersEaselPlugin.class,CALLS_REAL_METHODS);
        final World world=mock(World.class);final Player player=mock(Player.class);
        final SavedMaps maps=mock(SavedMaps.class);final PigmentCanvas canvas;
        final ItemFrame frame=mock(ItemFrame.class);
        final org.bukkit.map.MapView view=mock(org.bukkit.map.MapView.class);
        PlayerAnimationEvent event;
        Object easel;
        Fixture() throws Exception {
            NamespacedKey kind=new NamespacedKey("test","kind");
            doReturn(new YamlConfiguration()).when(plugin).getConfig();
            EaselProtectionTest.set(plugin,"kind",kind);EaselProtectionTest.set(plugin,"maps",maps);
            var server=mock(Server.class);doReturn(server).when(plugin).getServer();when(server.getMap(7)).thenReturn(view);
            UUID owner=UUID.randomUUID();when(player.getUniqueId()).thenReturn(owner);
            when(player.getWorld()).thenReturn(world);when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
            when(player.getEyeLocation()).thenReturn(new Location(world,0,0,2,180,0));
            when(player.spigot()).thenReturn(mock(Player.Spigot.class));
            var inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
            var brush=mock(ItemStack.class);var meta=mock(ItemMeta.class);var tags=mock(PersistentDataContainer.class);
            when(brush.hasItemMeta()).thenReturn(true);when(brush.getItemMeta()).thenReturn(meta);
            when(meta.getPersistentDataContainer()).thenReturn(tags);when(tags.get(kind,PersistentDataType.STRING)).thenReturn("brush");
            when(inventory.getItemInMainHand()).thenReturn(brush);
            when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.CYAN_DYE));
            when(frame.getUniqueId()).thenReturn(UUID.randomUUID());
            when(frame.getLocation()).thenReturn(new Location(world,0,0,0));when(frame.getFacing()).thenReturn(BlockFace.SOUTH);
            when(maps.id(any())).thenReturn(7);
            byte[] rgba=new byte[128*128*4];for(int i=0;i<rgba.length;i+=4){rgba[i+1]=(byte)255;rgba[i+3]=(byte)255;}
            canvas=new PigmentCanvas(rgba);
            Class<?> type=Class.forName(PaintersEaselPlugin.class.getName()+"$Easel");
            Constructor<?> constructor=type.getDeclaredConstructor(ItemFrame.class,UUID.class,Block.class);constructor.setAccessible(true);
            easel=constructor.newInstance(frame,owner,mock(Block.class));
            EaselProtectionTest.set(easel,"canvas",canvas);EaselProtectionTest.set(easel,"palette",new byte[16384]);
            EaselProtectionTest.set(plugin,"easels",new HashMap<>(Map.of(frame.getUniqueId(),easel)));
            when(world.rayTraceEntities(any(Location.class),any(org.bukkit.util.Vector.class),anyDouble(),any()))
                    .thenReturn(new RayTraceResult(new org.bukkit.util.Vector(0,0,0),frame));
            event=new PlayerAnimationEvent(player);
        }
        void swing(){plugin.brushSwing(event);}
    }
}
