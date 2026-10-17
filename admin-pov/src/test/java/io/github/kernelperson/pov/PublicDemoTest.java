package io.github.kernelperson.pov;

import java.lang.reflect.Field;
import java.util.*;
import ke.ric.renderer.api.RendererService;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicDemoTest {
    @Test void demoCommandPublishesOnlySelfAndWarnsThatCopiesArePublic() throws Exception {
        var f=new Fixture();f.command("demo");
        assertFalse(f.renderer().isContextual(), "explicit self demo must be photographable");
        assertEquals(f.owner,f.bindings.get(f.owner).target());
        verify(f.player).sendMessage(contains("PUBLIC"));
        verify(f.server,never()).getPlayerExact("demo");
        assertArrayEquals(f.completed((byte)42),f.render(f.stranger));
    }

    @Test void selectingAnotherPlayerAlwaysReplacesPublicRendererWithPrivateOne() throws Exception {
        var f=new Fixture();f.command("demo");var old=f.active();assertTrue(f.loop.current(old));
        f.command("Other");
        assertTrue(f.renderer().isContextual());
        assertEquals(f.otherId,f.bindings.get(f.owner).target());
        assertFalse(f.loop.complete(old,AdminPovPlugin.now(),new byte[16384]),"old public completion cannot republish after target change");
        assertEquals(119,f.render(f.stranger)[500]);
    }

    @Test void publicDemoKeepsNoFeedAndStaleStatusWithoutStartingAnotherRender() throws Exception {
        var f=new Fixture();f.command("demo");
        assertFalse(f.renderer().isContextual());f.loop.clear();
        assertEquals(119,f.render(f.stranger)[500]);assertTrue(f.statuses.contains("NO FEED"));
        f.loop.demand(List.of(f.owner));var old=f.loop.begin(AdminPovPlugin.now()-4_000);
        byte[] pixels=new byte[16384];Arrays.fill(pixels,(byte)42);f.loop.complete(old,AdminPovPlugin.now(),pixels);
        assertEquals(42,f.render(f.stranger)[5_000]);assertTrue(f.statuses.contains("STALE"));
    }

    @Test void demoStillRequiresTheNormalAdminPermission() throws Exception {
        var f=new Fixture();when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(false);
        f.command("demo");assertTrue(f.renderer().isContextual());
        assertEquals(119,f.render(f.stranger)[500]);
        verify(f.player,never()).sendMessage(contains("PUBLIC"));
    }

    @Test void demoMonitorCommandDoesNotPromiseOwnerOnlyViewing() throws Exception {
        var f=new Fixture();f.set("monitors",mock(PovMonitors.class));f.command("demo");
        f.command("monitor");verify(f.player).sendMessage(contains("PUBLIC self-view monitor"));
    }

    @Test void stopPermissionLossLogoutProviderRemovalAndShutdownRevokePublicMode() throws Exception {
        for(String reason:List.of("stop","permission","visibility","offline","logout","teleport","world","provider","shutdown")) {
            var f=new Fixture();f.command("demo");assertFalse(f.renderer().isContextual());f.completed((byte)42);
            switch(reason) {
                case "stop"->f.command("stop");
                case "permission"->{when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(false);f.tick();}
                case "visibility"->{when(f.player.canSee(f.player)).thenReturn(false);f.tick();}
                case "offline"->{when(f.player.isOnline()).thenReturn(false);f.tick();}
                case "logout"->f.plugin.logout(new PlayerQuitEvent(f.player,"bye"));
                case "teleport"->{var event=mock(org.bukkit.event.player.PlayerTeleportEvent.class);when(event.getPlayer()).thenReturn(f.player);f.plugin.teleport(event);}
                case "world"->f.plugin.worldChanged(new org.bukkit.event.player.PlayerChangedWorldEvent(f.player,mock(World.class)));
                case "provider"->{var registration=mock(RegisteredServiceProvider.class);when(registration.getService()).thenReturn(RendererService.class);f.plugin.providerRemoved(new ServiceUnregisterEvent(registration));}
                case "shutdown"->f.plugin.onDisable();
            }
            assertTrue(f.renderer().isContextual(),reason+" must remove the cached public canvas");
            assertEquals(119,f.render(f.stranger)[500],reason);
        }
    }

    @Test void feedbackUsesOnlyLastCompletedFrameAndSpectatorsNeverRequestWork() throws Exception {
        var f=new Fixture();f.command("demo");
        assertArrayEquals(f.completed((byte)42),f.render(f.stranger));
        var pending=f.loop.begin(AdminPovPlugin.now()+2_000);assertNotNull(pending);
        assertEquals(42,f.render(f.stranger)[500],"in-progress frame is never published");
        byte[] next=new byte[16384];Arrays.fill(next,(byte)66);
        assertTrue(f.loop.complete(pending,AdminPovPlugin.now()+2_001,next));
        assertArrayEquals(next,f.render(f.stranger));assertEquals(1,f.loop.size());
        when(f.inventory.getItemInMainHand()).thenReturn(null);
        f.tick();assertEquals(0,f.loop.size(),"a spectator cannot retain owner capture demand");
    }

    @Test void restoredReceiverStartsPrivateEvenAfterItsPriorInstanceUsedDemo() throws Exception {
        var first=new Fixture();first.command("demo");first.completed((byte)42);
        var restored=new Fixture(first.owner);restored.attach();
        assertTrue(restored.renderer().isContextual());
        assertEquals(119,restored.render(restored.stranger)[500]);
    }

    private static final class Fixture {
        final UUID owner,otherId=UUID.randomUUID();
        final AdminPovPlugin plugin=mock(AdminPovPlugin.class,CALLS_REAL_METHODS);
        final Server server=mock(Server.class);
        final Player player=mock(Player.class),stranger=mock(Player.class),other=mock(Player.class);
        final PlayerInventory inventory=mock(PlayerInventory.class);
        final MapView view=mock(MapView.class);
        final List<MapRenderer> renderers=new ArrayList<>();
        final Map<MapRenderer,byte[]> canvases=new IdentityHashMap<>();
        final List<String> statuses=new ArrayList<>();
        final Map<UUID,BindingStore.Binding> bindings=new LinkedHashMap<>();
        final FeedLoop<UUID> loop=new FeedLoop<>();
        Fixture() throws Exception {this(UUID.randomUUID());}
        Fixture(UUID owner) throws Exception {
            this.owner=owner;
            set("bindings",bindings);set("views",new HashMap<UUID,MapView>());set("loop",loop);
            // New opt-in state is deliberately never part of persisted bindings.
            set("demoOwners",new HashSet<UUID>());
            set("store",mock(BindingStore.class));set("capture",mock(PovCapture.class));
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer(owner)).thenReturn(player);when(server.getPlayer(otherId)).thenReturn(other);
            when(server.getPlayerExact("Other")).thenReturn(other);
            when(player.getUniqueId()).thenReturn(owner);when(other.getUniqueId()).thenReturn(otherId);
            when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);when(other.isOnline()).thenReturn(true);
            when(player.hasPermission(PovAccess.PERMISSION)).thenReturn(true);when(player.canSee(any())).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory);when(view.getId()).thenReturn(7);
            var item=mock(ItemStack.class);var meta=mock(MapMeta.class);
            when(item.getType()).thenReturn(Material.FILLED_MAP);when(item.getItemMeta()).thenReturn(meta);
            when(meta.hasMapView()).thenReturn(true);when(meta.getMapView()).thenReturn(view);
            when(inventory.getContents()).thenReturn(new ItemStack[]{item});when(inventory.getItemInMainHand()).thenReturn(item);
            when(view.getRenderers()).thenAnswer(i->List.copyOf(renderers));
            doAnswer(i->{renderers.add(i.getArgument(0));return null;}).when(view).addRenderer(any());
            when(view.removeRenderer(any())).thenAnswer(i->renderers.remove(i.getArgument(0)));
            var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);when(player.getEyeLocation()).thenReturn(new Location(world,0,64,0));
            when(other.getWorld()).thenReturn(world);when(other.getEyeLocation()).thenReturn(new Location(world,0,64,0));
            bindings.put(owner,new BindingStore.Binding(owner,7,owner));attach();
        }
        void set(String name,Object value) throws Exception {Field field=AdminPovPlugin.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,value);}
        void command(String value){plugin.onCommand(player,mock(Command.class),"pov",new String[]{value});}
        void tick() throws Exception {var tick=AdminPovPlugin.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(plugin);}
        void attach() throws Exception {var attach=AdminPovPlugin.class.getDeclaredMethod("attach",UUID.class,MapView.class);attach.setAccessible(true);attach.invoke(plugin,owner,view);}
        MapRenderer renderer(){assertEquals(1,renderers.size());return renderers.getFirst();}
        @SuppressWarnings("unchecked") FeedLoop.Ticket<UUID> active() throws Exception {var field=AdminPovPlugin.class.getDeclaredField("active");field.setAccessible(true);return (FeedLoop.Ticket<UUID>)field.get(plugin);}
        byte[] completed(byte color) throws Exception {var ticket=active();assertTrue(loop.current(ticket));byte[] pixels=new byte[16384];Arrays.fill(pixels,color);assertTrue(loop.complete(ticket,AdminPovPlugin.now(),pixels));return pixels;}
        byte[] render(Player viewer) {
            byte[] result=canvases.computeIfAbsent(renderer(),ignored->new byte[16384]);var canvas=mock(MapCanvas.class);
            doAnswer(i->{int x=i.getArgument(0),y=i.getArgument(1);result[y*128+x]=i.getArgument(2);return null;}).when(canvas).setPixel(anyInt(),anyInt(),anyByte());
            doAnswer(i->{statuses.add(i.getArgument(3));return null;}).when(canvas).drawText(anyInt(),anyInt(),any(),anyString());
            renderer().render(view,canvas,viewer);return result.clone();
        }
    }
}
