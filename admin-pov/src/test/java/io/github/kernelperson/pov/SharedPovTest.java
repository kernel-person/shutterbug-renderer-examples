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
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedPovTest {
    @Test void demoRateChangesTheSharedCadenceAndPlainDemoKeepsIt() throws Exception {
        var f=new Fixture();f.command("Other");var other=f.active();
        f.loop.complete(other,other.started()+10,new byte[16384]);
        f.command("demo","20");
        assertEquals(f.owner,f.bindings.get(f.owner).target());
        var self=f.active();assertNotNull(self);f.loop.complete(self,self.started()+10,new byte[16384]);
        assertNull(f.loop.begin(self.started()+49));
        var next=f.loop.begin(self.started()+50);assertNotNull(next,"20/sec needs a 50ms cadence");
        f.command("demo");assertTrue(f.loop.current(next),"plain demo retains rate and healthy active ticket");
        f.loop.complete(next,next.started()+10,new byte[16384]);
        assertNotNull(f.loop.begin(next.started()+50),"plain demo must not restore the default rate");
        verify(f.player,atLeastOnce()).sendMessage(contains("20 updates/second per target"));
        verify(f.player,atLeastOnce()).sendMessage(contains("all POV feeds until restart"));
    }

    @Test void changingRateNeverCancelsHealthyActiveWorkOrAcceptsItsStaleCallback() throws Exception {
        var f=new Fixture();f.command("demo");var active=f.active();
        f.command("demo","1");assertTrue(f.loop.current(active));assertSame(active,f.active());
        verify(f.capture,never()).cancel();verify(f.capture,times(1)).start(any(),eq(f.owner),any(),any(),any());
        f.loop.complete(active,active.started()+100,new byte[16384]);
        assertNull(f.loop.begin(active.started()+999));
        var next=f.loop.begin(active.started()+1_000);assertNotNull(next);
        assertFalse(f.loop.complete(active,next.started(),new byte[16384]));assertTrue(f.loop.current(next));
        f.command("demo","20");assertTrue(f.loop.current(next));
        f.loop.complete(next,next.started()+10,new byte[16384]);
        assertNotNull(f.loop.begin(next.started()+50),"new cadence applies after the active frame completes");
    }

    @Test void rateChangesPreserveOutstandingFailureCooldownAndFailureCount() throws Exception {
        var f=new Fixture();f.command("demo");var active=f.active();long start=active.started();
        assertTrue(f.loop.complete(active,start+10,new byte[16384]));var completed=f.loop.frame(f.owner);
        active=f.loop.begin(start+250);assertNotNull(active);
        f.loop.failed(active,start+260);
        f.command("demo","20");assertNull(f.active());
        assertSame(completed,f.loop.frame(f.owner),"failure and rate change retain the last completed frame");
        assertNull(f.loop.begin(start+2_259),"rate must not shorten backoff even when an older frame exists");
        var retry=f.loop.begin(start+2_260);assertNotNull(retry);
        f.loop.failed(retry,start+2_270);f.command("demo","1");
        assertNull(f.loop.begin(start+6_269));assertNotNull(f.loop.begin(start+6_270));
    }

    @Test void invalidSyntaxAndRatesLeaveBindingsFramesTicketsAndCadenceUnchanged() throws Exception {
        for(String[] invalid:List.of(new String[]{"demo","0"},new String[]{"demo","21"},
                new String[]{"demo","-1"},new String[]{"demo","1.5"},new String[]{"demo","fast"},
                new String[]{"demo","2147483648"},new String[]{"demo","4","extra"},
                new String[]{"Other","4"})) {
            var f=new Fixture();f.command("demo");var active=f.active();var binding=f.bindings.get(f.owner);
            f.command(invalid);assertSame(binding,f.bindings.get(f.owner));assertTrue(f.loop.current(active));
            verify(f.capture,never()).cancel();
            f.loop.complete(active,active.started()+10,new byte[16384]);var frame=f.loop.frame(f.owner);
            f.command(invalid);assertSame(frame,f.loop.frame(f.owner));
            assertNull(f.loop.begin(active.started()+249));assertNotNull(f.loop.begin(active.started()+250));
            verify(f.player,atLeastOnce()).sendMessage(contains("1-20"));
        }
    }

    @Test void unauthorizedAndRejectedSelectionsCannotChangeRateOrActiveBinding() throws Exception {
        for(String reason:List.of("permission","visibility","inventory","save")) {
            var f=new Fixture();f.command("Other");var active=f.active();var binding=f.bindings.get(f.owner);
            switch(reason) {
                case "permission"->when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(false);
                case "visibility"->when(f.player.canSee(f.player)).thenReturn(false);
                case "inventory"->{when(f.inventory.getContents()).thenReturn(new ItemStack[0]);when(f.inventory.firstEmpty()).thenReturn(-1);}
                case "save"->{var field=AdminPovPlugin.class.getDeclaredField("store");field.setAccessible(true);
                    doThrow(new java.io.IOException("test rejection")).when((BindingStore)field.get(f.plugin)).save(any());}
            }
            f.command("demo","20");assertEquals(binding,f.bindings.get(f.owner),reason);assertTrue(f.loop.current(active),reason);
            f.loop.complete(active,active.started()+10,new byte[16384]);
            assertNull(f.loop.begin(active.started()+249),reason);assertNotNull(f.loop.begin(active.started()+250),reason);
            verify(f.capture,never()).cancel();
        }
    }

    @Test void schedulerChecksEveryTickButPeriodicMapBroadcastsRemainEveryFiveTicks(@TempDir java.nio.file.Path data) throws Exception {
        var f=new Fixture();var scheduler=mock(BukkitScheduler.class);
        when(f.server.getScheduler()).thenReturn(scheduler);
        when(f.server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
        doNothing().when(f.plugin).saveDefaultConfig();doReturn(data.toFile()).when(f.plugin).getDataFolder();
        doReturn(new YamlConfiguration()).when(f.plugin).getConfig();doReturn("RendererAdminPov").when(f.plugin).getName();
        f.plugin.onEnable();
        var actions=org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(f.plugin),actions.capture(),eq(1L),eq(1L));
        Runnable processing=actions.getValue();
        verify(scheduler).runTaskTimer(eq(f.plugin),actions.capture(),eq(1L),eq(5L));
        Runnable status=actions.getValue();clearInvocations(f.player);
        for(int i=0;i<20;i++)processing.run();
        verify(f.player,never()).sendMap(any());status.run();verify(f.player).sendMap(f.view);
    }

    @Test void normalAndDemoCommandsSharePhotographablePixelsForEveryViewer() throws Exception {
        for(String selection:List.of("demo","Self","Other")) {
            var f=new Fixture();MapRenderer receiver=f.renderer();f.command(selection);
            assertFalse(f.renderer().isContextual(),selection+" must be photographable");
            assertSame(receiver,f.renderer(),"selection never swaps public/private renderers");
            assertEquals(selection.equals("Other")?f.otherId:f.owner,f.bindings.get(f.owner).target());
            verify(f.player).sendMessage(contains("PUBLIC"));
            byte[] pixels=f.completed((byte)42);
            assertArrayEquals(pixels,f.render(f.player));
            assertArrayEquals(pixels,f.render(f.stranger),"copied or watched maps are public without viewer permission");
            if(selection.equals("demo"))verify(f.server,never()).getPlayerExact("demo");
        }
    }

    @Test void changingTargetsKeepsPublicRendererButRejectsTheOldCompletion() throws Exception {
        var f=new Fixture();f.command("demo");var old=f.active();MapRenderer receiver=f.renderer();
        f.command("Other");
        assertSame(receiver,f.renderer());assertFalse(f.renderer().isContextual());
        assertEquals(f.otherId,f.bindings.get(f.owner).target());
        assertFalse(f.loop.complete(old,AdminPovPlugin.now(),new byte[16384]),"old completion cannot republish after target change");
        assertArrayEquals(f.completed((byte)66),f.render(f.stranger));
    }

    @Test void sharedReceiverKeepsNoFeedAndStaleStatusWithoutStartingAnotherRender() throws Exception {
        var f=new Fixture();f.command("demo");
        assertFalse(f.renderer().isContextual());f.loop.clear();
        assertEquals(119,f.render(f.stranger)[500]);assertTrue(f.statuses.contains("NO FEED"));
        f.loop.demand(List.of(f.owner));var old=f.loop.begin(AdminPovPlugin.now()-4_000);
        byte[] pixels=new byte[16384];Arrays.fill(pixels,(byte)42);f.loop.complete(old,AdminPovPlugin.now(),pixels);
        assertEquals(42,f.render(f.stranger)[5_000]);assertTrue(f.statuses.contains("STALE"));
    }

    @Test void publicViewingDoesNotGrantControlPermissionOrBypassTargetVisibility() throws Exception {
        for(String selection:List.of("demo","Self","Other")) {
            var f=new Fixture();when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(false);
            f.command(selection);assertNull(f.active());assertEquals(f.owner,f.bindings.get(f.owner).target());
            verify(f.player,never()).sendMessage(contains("PUBLIC"));
        }
        var f=new Fixture();when(f.player.canSee(f.other)).thenReturn(false);
        f.command("Other");assertNull(f.active());assertEquals(f.owner,f.bindings.get(f.owner).target());
        verify(f.player).sendMessage("That player is not available to you.");
    }

    @Test void everyMonitorCommandWarnsThatItsSharedScreenIsPublic() throws Exception {
        for(String selection:List.of("demo","Other")) {
            var f=new Fixture();f.set("monitors",mock(PovMonitors.class));f.command(selection);
            f.command("monitor");verify(f.player).sendMessage(contains("PUBLIC monitor"));
        }
    }

    @Test void feedInvalidationClearsSharedPixelsWithoutChangingRendererMode() throws Exception {
        for(String reason:List.of("stop","permission","visibility","offline","logout","teleport","world","provider","shutdown")) {
            var f=new Fixture();f.command("demo");MapRenderer receiver=f.renderer();f.completed((byte)42);
            assertEquals(42,f.render(f.stranger)[500]);
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
            assertSame(receiver,f.renderer(),reason+" clears feed state, never changes public/private mode");
            assertFalse(f.renderer().isContextual());
            assertEquals(119,f.render(f.stranger)[500],reason);
            assertTrue(f.statuses.contains("NO FEED"),reason);
        }
    }

    @Test void stopProviderReloadAndShutdownDiscardLateCaptureCompletions() throws Exception {
        for(String reason:List.of("stop","provider","shutdown")) {
            var f=new Fixture();f.command("Other");
            org.mockito.ArgumentCaptor<java.util.function.Consumer<byte[]>> success=org.mockito.ArgumentCaptor.captor();
            verify(f.capture).start(any(),eq(f.otherId),any(),success.capture(),any());
            switch(reason) {
                case "stop"->f.command("stop");
                case "provider"->{var registration=mock(RegisteredServiceProvider.class);when(registration.getService()).thenReturn(RendererService.class);f.plugin.providerRemoved(new ServiceUnregisterEvent(registration));}
                case "shutdown"->f.plugin.onDisable();
            }
            byte[] late=new byte[16384];Arrays.fill(late,(byte)66);success.getValue().accept(late);
            assertEquals(119,f.render(f.stranger)[500],reason+" must reject obsolete asynchronous pixels");
            assertNull(f.loop.frame(f.otherId));
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

    @Test void freshPluginInstanceStartsPublicWithoutPreviousInstancePixels() throws Exception {
        var first=new Fixture();first.command("demo");first.completed((byte)42);
        var restored=new Fixture(first.owner);restored.attach();
        assertFalse(restored.renderer().isContextual());
        assertEquals(119,restored.render(restored.stranger)[500]);
        restored.command("Self");assertArrayEquals(restored.completed((byte)66),restored.render(restored.stranger));
    }

    private static final class Fixture {
        final UUID owner,otherId=UUID.randomUUID();
        final AdminPovPlugin plugin=mock(AdminPovPlugin.class,CALLS_REAL_METHODS);
        final Server server=mock(Server.class);
        final Player player=mock(Player.class),stranger=mock(Player.class),other=mock(Player.class);
        final PlayerInventory inventory=mock(PlayerInventory.class);
        final PovCapture capture=mock(PovCapture.class);
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
            set("store",mock(BindingStore.class));set("capture",capture);
            doReturn(server).when(plugin).getServer();
            when(server.getPlayer(owner)).thenReturn(player);when(server.getPlayer(otherId)).thenReturn(other);
            when(server.getPlayerExact("Other")).thenReturn(other);
            when(server.getPlayerExact("Self")).thenReturn(player);
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
        void command(String... values){plugin.onCommand(player,mock(Command.class),"pov",values);}
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
