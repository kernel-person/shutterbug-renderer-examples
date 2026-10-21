package io.github.kernelperson.postcards;

import ke.ric.renderer.api.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenderSessionTest {
    final Plugin plugin=mock(Plugin.class);
    final Server server=mock(Server.class);
    final Renderer renderer=mock(Renderer.class);
    final SnapshotBuilder snapshot=mock(SnapshotBuilder.class,RETURNS_SELF);
    final RendererService service=mock(RendererService.class);
    final CompletableFuture<ItemStack> operation=new CompletableFuture<>();
    final Location camera=new Location(mock(World.class),1,64,2);
    Consumer<ItemStack> success;Consumer<Throwable> failure;
    final AtomicInteger deliveries=new AtomicInteger();
    final List<String> failures=new ArrayList<>();
    RenderSession session;
    @BeforeEach void setup() {
        when(plugin.getServer()).thenReturn(server);
        var services=mock(ServicesManager.class);when(server.getServicesManager()).thenReturn(services);
        when(services.load(RendererService.class)).thenReturn(service);
        when(renderer.snapshot(camera)).thenReturn(snapshot);
        when(snapshot.captureMap(any(),any())).thenAnswer(i->{success=i.getArgument(0);failure=i.getArgument(1);return operation;});
        session=new RenderSession(plugin,renderer);
    }
    boolean start(BooleanSupplier valid){return session.request("one",camera,valid,value->deliveries.incrementAndGet(),failures::add);}
    ItemStack result(){return mock(ItemStack.class);}
    @Test void retainsCaptureBudgetAndLetsSdkOwnConversionAndDeadline() {
        assertTrue(start(()->true));
        verify(snapshot).settings(argThat(s->s.profile()==RenderSettings.Profile.NORMAL && s.width()==128 && s.height()==128
                && s.workers()==2 && s.timeout().equals(java.time.Duration.ofSeconds(30))));
        verify(snapshot).radius(32);verify(snapshot).chunksPerTick(4);
        verify(snapshot).mapOptions(argThat(options->options.dither()==MapDither.BAYER));
        success.accept(result());assertEquals(1,deliveries.get());assertTrue(failures.isEmpty());
    }
    @Test void duplicatePendingRequestAndCooldownDoNotAllocateAnotherPhoto() {
        assertTrue(start(()->true));assertFalse(start(()->true));
        success.accept(result());assertFalse(start(()->true));verify(snapshot,times(1)).captureMap(any(),any());
    }
    @Test void cancelledIdentityRejectsLateMapAndCancelsSdkFuture() {
        assertTrue(start(()->true));session.invalidate("one");assertTrue(operation.isCancelled());
        success.accept(result());assertEquals(0,deliveries.get());assertEquals(1,failures.size());
    }
    @Test void destinationDisappearanceNeverDeliversSavedResult() {
        var valid=new AtomicBoolean(true);assertTrue(start(valid::get));valid.set(false);
        success.accept(result());assertEquals(0,deliveries.get());assertTrue(operation.isCancelled());
    }
    @Test void providerRemovalRejectsAlreadyQueuedCallbacks() {
        assertTrue(start(()->true));
        var registration=new RegisteredServiceProvider<>(RendererService.class,service,ServicePriority.Normal,plugin);
        session.providerRemoved(new org.bukkit.event.server.ServiceUnregisterEvent(registration));
        success.accept(result());assertEquals(0,deliveries.get());assertTrue(operation.isCancelled());
    }
    @Test void closeCancelsPendingWorkAndPreventsDelivery() {
        assertTrue(start(()->true));session.close();success.accept(result());
        assertTrue(operation.isCancelled());assertEquals(0,deliveries.get());verify(renderer).close();assertFalse(start(()->true));
    }
    @Test void missingProviderFailsBeforeStartingSdkWork() {
        when(server.getServicesManager().load(RendererService.class)).thenReturn(null);
        assertFalse(start(()->true));assertEquals(1,failures.size());verifyNoInteractions(renderer);
    }
    @Test void failureReleasesPendingSlotWithoutPublishingAnything() {
        assertTrue(start(()->true));failure.accept(new RendererException(RenderFailureCode.TIMEOUT,"timeout"));
        assertEquals(0,deliveries.get());assertEquals(1,failures.size());assertTrue(operation.isCancelled());
    }
}
