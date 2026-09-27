package io.github.kernelperson.postcards;

import ke.ric.renderer.api.*;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenderSessionTest {
    Plugin plugin = mock(Plugin.class);
    Server server = mock(Server.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    RendererService service = mock(RendererService.class);
    RendererClient client = mock(RendererClient.class);
    RenderTask task = mock(RenderTask.class);
    CompletableFuture<Scene> captured = new CompletableFuture<>();
    CompletableFuture<RenderResult> rendered = new CompletableFuture<>();
    Deque<Runnable> main = new ArrayDeque<>();
    List<Runnable> deadlines = new ArrayList<>();
    AtomicInteger deliveries = new AtomicInteger();
    List<String> failures = new ArrayList<>();
    RenderSession session;
    Location location = new Location(mock(World.class), 1, 64, 2);

    @BeforeEach void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(server.getScheduler()).thenReturn(scheduler);
        ServicesManager services = mock(ServicesManager.class);
        when(server.getServicesManager()).thenReturn(services);
        when(services.load(RendererService.class)).thenReturn(service);
        when(service.createClient(plugin)).thenReturn(client);
        when(client.active()).thenReturn(true);
        when(client.capture(any())).thenReturn(captured);
        when(client.submit(any())).thenReturn(task);
        when(task.completion()).thenReturn(rendered);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> {
            main.add(i.getArgument(1)); return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(i -> {
            deadlines.add(i.getArgument(1)); return mock(BukkitTask.class);
        });
        session = new RenderSession(plugin);
    }
    void drain() { while (!main.isEmpty()) main.remove().run(); }
    boolean start(BooleanSupplierFixture alive) {
        return session.request("one", location, alive::get, result -> deliveries.incrementAndGet(), failures::add);
    }
    interface BooleanSupplierFixture { boolean get(); }

    @Test void captureSubmitAndDeliveryCrossTheMainThreadBoundary() {
        assertTrue(start(() -> true));
        captured.complete(mock(Scene.class));
        verify(client, never()).submit(any());
        drain();
        verify(client).submit(argThat(job -> job.settings().width() == 128 && job.settings().height() == 128));
        rendered.complete(mock(RenderResult.class));
        assertEquals(0, deliveries.get());
        drain();
        assertEquals(1, deliveries.get());
        assertTrue(failures.isEmpty());
    }
    @Test void closeDuringCapturePreventsLateSubmissionAndDelivery() {
        assertTrue(start(() -> true));
        session.close();
        captured.complete(mock(Scene.class)); drain();
        verify(client, never()).submit(any());
        verify(client).close();
        assertEquals(0, deliveries.get());
    }
    @Test void invalidDestinationPreventsDeliveryAndCancelsWork() {
        var valid = new AtomicBoolean(true);
        assertTrue(start(valid::get));
        captured.complete(mock(Scene.class)); drain();
        valid.set(false);
        rendered.complete(mock(RenderResult.class)); drain();
        assertEquals(0, deliveries.get());
    }
    @Test void deadlineAlsoCoversNeverFinishingCapture() {
        assertTrue(start(() -> true));
        assertEquals(1, deadlines.size());
        deadlines.getFirst().run();
        captured.complete(mock(Scene.class)); drain();
        verify(client, never()).submit(any());
        assertEquals(1, failures.size());
        assertTrue(failures.getFirst().contains("timed out"));
    }
    @Test void missingProviderReturnsAUsefulFailureWithoutStartingWork() {
        when(server.getServicesManager().load(RendererService.class)).thenReturn(null);
        assertFalse(start(() -> true));
        assertEquals(1, failures.size());
        verify(client, never()).capture(any());
    }
    @Test void cancelledIdentityCannotDeliverAfterReplacement() {
        assertTrue(start(() -> true));
        session.invalidate("one");
        captured.complete(mock(Scene.class)); drain();
        verify(client, never()).submit(any());
        assertEquals(0, deliveries.get());
    }
}
