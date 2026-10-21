package io.github.kernelperson.pov;

import java.lang.reflect.Field;
import java.util.*;
import ke.ric.renderer.api.*;
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
    @Test void demoRatesConfigureFreshSharedStreamAndPlainDemoKeepsRate() throws Exception {
        var f=new Fixture();f.command("demo","20");assertEquals(20,f.loop.updatesPerSecond());
        verify(f.builder).fps(20);verify(f.builder).fresh();verify(f.builder).radius(16);verify(f.builder).chunksPerTick(2);
        verify(f.builder).settings(org.mockito.ArgumentMatchers.argThat(settings->settings.profile()==RenderSettings.Profile.CLASSIC
                &&settings.width()==128&&settings.height()==128&&settings.workers()==1));
        verify(f.builder).excluding(f.owner);verify(f.builder).mapOptions(argThat(options->options.dither()==MapDither.NONE));
        f.command("demo");assertEquals(20,f.loop.updatesPerSecond());verify(f.builder,times(1)).start();
    }
    @Test void invalidSyntaxAndRatesLeaveBindingsAndRateUnchanged() throws Exception {
        for(String[] invalid:List.of(new String[]{"demo","0"},new String[]{"demo","21"},new String[]{"demo","1.5"},new String[]{"Other","4"})){
            var f=new Fixture();f.command("demo");var binding=f.bindings.get(f.owner);f.command(invalid);
            assertSame(binding,f.bindings.get(f.owner));assertEquals(4,f.loop.updatesPerSecond());verify(f.builder,times(1)).start();
        }
    }
    @Test void failedStreamRetainsLastPixelsAndEntersBackoff() throws Exception {
        var f=new Fixture();f.command("demo");f.completed((byte)42);f.error.accept(new IllegalStateException("capacity"));
        assertEquals(42,f.render(f.stranger)[500]);assertFalse(f.loop.ready(f.owner,AdminPovPlugin.now()));
        f.tick();verify(f.builder,times(1)).start();verify(f.stream).close();
    }
    @Test void sameTargetOwnersShareOneStreamAndEitherCanKeepDemand() throws Exception {
        var f=new Fixture();f.command("demo");UUID owner2=UUID.randomUUID();var second=mock(Player.class);
        f.bindings.put(owner2,new BindingStore.Binding(owner2,7,f.owner));when(f.server.getPlayer(owner2)).thenReturn(second);
        when(second.getUniqueId()).thenReturn(owner2);when(second.isOnline()).thenReturn(true);
        when(second.hasPermission(PovAccess.PERMISSION)).thenReturn(true);when(second.canSee(f.player)).thenReturn(true);
        when(second.getInventory()).thenReturn(f.inventory);
        f.tick();verify(f.builder,times(1)).start();
        when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(false);f.tick();verify(f.stream,never()).close();
        when(second.hasPermission(PovAccess.PERMISSION)).thenReturn(false);f.tick();verify(f.stream).close();assertEquals(0,f.loop.size());
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
        var f=new Fixture();f.command("demo");var old=f.sink;MapRenderer receiver=f.renderer();
        f.command("Other");
        assertSame(receiver,f.renderer());assertFalse(f.renderer().isContextual());
        assertEquals(f.otherId,f.bindings.get(f.owner).target());
        old.accept(f.frame((byte)12));assertNull(f.loop.frame(f.owner),"old completion cannot republish after target change");
        assertArrayEquals(f.completed((byte)66),f.render(f.stranger));
    }

    @Test void sharedReceiverKeepsNoFeedAndStaleStatusWithoutStartingAnotherRender() throws Exception {
        var f=new Fixture();f.command("demo");
        assertFalse(f.renderer().isContextual());f.loop.clear();
        assertEquals(119,f.render(f.stranger)[500]);assertTrue(f.statuses.contains("NO FEED"));
        f.loop.demand(List.of(f.owner));
        byte[] pixels=new byte[16384];Arrays.fill(pixels,(byte)42);f.loop.complete(f.owner,AdminPovPlugin.now()-4_000,pixels);
        assertEquals(42,f.render(f.stranger)[5_000]);assertTrue(f.statuses.contains("STALE"));
    }

    @Test void publicViewingDoesNotGrantControlPermissionOrBypassTargetVisibility() throws Exception {
        for(String selection:List.of("demo","Self","Other")) {
            var f=new Fixture();when(f.player.hasPermission(PovAccess.PERMISSION)).thenReturn(false);
            f.command(selection);assertNull(f.sink);assertEquals(f.owner,f.bindings.get(f.owner).target());
            verify(f.player,never()).sendMessage(contains("PUBLIC"));
        }
        var f=new Fixture();when(f.player.canSee(f.other)).thenReturn(false);
        f.command("Other");assertNull(f.sink);assertEquals(f.owner,f.bindings.get(f.owner).target());
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
            MapSink obsolete=f.sink;
            switch(reason) {
                case "stop"->f.command("stop");
                case "provider"->{var registration=mock(RegisteredServiceProvider.class);when(registration.getService()).thenReturn(RendererService.class);f.plugin.providerRemoved(new ServiceUnregisterEvent(registration));}
                case "shutdown"->f.plugin.onDisable();
            }
            obsolete.accept(f.frame((byte)66));
            assertEquals(119,f.render(f.stranger)[500],reason+" must reject obsolete asynchronous pixels");
            assertNull(f.loop.frame(f.otherId));
        }
    }

    @Test void feedbackUsesOnlyLastCompletedFrameAndSpectatorsNeverRequestWork() throws Exception {
        var f=new Fixture();f.command("demo");
        assertArrayEquals(f.completed((byte)42),f.render(f.stranger));

        assertEquals(42,f.render(f.stranger)[500],"in-progress frame is never published");
        byte[] next=new byte[16384];Arrays.fill(next,(byte)66);
        f.sink.accept(f.frame((byte)66));
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
        final Renderer api=mock(Renderer.class);
        final StreamBuilder builder=mock(StreamBuilder.class,RETURNS_SELF);
        final RenderStream stream=mock(RenderStream.class);
        MapSink sink;java.util.function.Consumer<Throwable> error;
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
            set("store",mock(BindingStore.class));set("renderer",api);set("feeds",new HashMap<>());
            when(api.stream(org.mockito.ArgumentMatchers.<java.util.function.Supplier<Location>>any())).thenReturn(builder);
            when(builder.start()).thenReturn(stream);when(stream.active()).thenReturn(true);
            when(builder.toMapSink(any())).thenAnswer(i->{sink=i.getArgument(0);return builder;});
            when(builder.onError(any())).thenAnswer(i->{error=i.getArgument(0);return builder;});
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
        MapFrame frame(byte color) {var frame=mock(MapFrame.class);byte[] pixels=new byte[16384];Arrays.fill(pixels,color);when(frame.palette()).thenReturn(pixels);when(frame.worldCapturedNanos()).thenReturn(System.nanoTime());return frame;}
        byte[] completed(byte color) {assertNotNull(sink);MapFrame frame=frame(color);sink.accept(frame);return frame.palette();}
        byte[] render(Player viewer) {
            byte[] result=canvases.computeIfAbsent(renderer(),ignored->new byte[16384]);var canvas=mock(MapCanvas.class);
            doAnswer(i->{int x=i.getArgument(0),y=i.getArgument(1);result[y*128+x]=i.getArgument(2);return null;}).when(canvas).setPixel(anyInt(),anyInt(),anyByte());
            doAnswer(i->{statuses.add(i.getArgument(3));return null;}).when(canvas).drawText(anyInt(),anyInt(),any(),anyString());
            renderer().render(view,canvas,viewer);return result.clone();
        }
    }
}
