package io.github.kernelperson.pov;

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

class PovPipelineTest {
    final Plugin plugin=mock(Plugin.class);
    final Server server=mock(Server.class);
    final BukkitScheduler scheduler=mock(BukkitScheduler.class);
    final RendererService service=mock(RendererService.class);
    final RendererClient client=mock(RendererClient.class);
    final RenderTask task=mock(RenderTask.class);
    final World world=mock(World.class);
    final CompletableFuture<Scene> captured=new CompletableFuture<>();
    final CompletableFuture<RenderResult> rendered=new CompletableFuture<>();
    final Deque<Runnable> main=new ArrayDeque<>();
    final List<Runnable> deadlines=new ArrayList<>();
    final AtomicInteger delivered=new AtomicInteger(),failures=new AtomicInteger();
    final AtomicBoolean valid=new AtomicBoolean(true);
    PovCapture capture;
    @BeforeEach void setup() {
        when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);
        when(server.getScheduler()).thenReturn(scheduler);var services=mock(ServicesManager.class);
        when(server.getServicesManager()).thenReturn(services);when(services.load(RendererService.class)).thenReturn(service);
        when(service.createClient(plugin)).thenReturn(client);when(client.active()).thenReturn(true);
        when(client.capture(any())).thenReturn(captured);when(client.submit(any())).thenReturn(task);
        when(task.completion()).thenReturn(rendered);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{main.add(i.getArgument(1));return mock(BukkitTask.class);});
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(i->{deadlines.add(i.getArgument(1));return mock(BukkitTask.class);});
        capture=new PovCapture(plugin);
    }
    void start(){capture.start(new Location(world,0,64,0,33,-21),valid::get,pixels->delivered.incrementAndGet(),failures::incrementAndGet);}
    void drain(){while(!main.isEmpty())main.remove().run();}
    @Test void actualRgbaQuantizationDeliversOneMapOnMainThreadAndReleasesPipeline() {
        start();captured.complete(mock(Scene.class));drain();
        byte[] rgba=new byte[128*128*4];Arrays.fill(rgba,(byte)255);
        rendered.complete(new RenderResult(128,128,rgba,null,null,null,null,mock(VisibilityStatistics.class),mock(RenderTimings.class)));
        assertEquals(0,delivered.get());drain();assertEquals(1,delivered.get());assertEquals(0,failures.get());
        assertDoesNotThrow(this::start);capture.close();
    }
    @Test void boundedCaptureUsesExactSettingsAndPreventsParallelStarts() {
        start();verify(client).capture(argThat(request->request.radius()==16&&request.chunksPerTick()==2));
        assertThrows(IllegalStateException.class,this::start);
        captured.complete(mock(Scene.class));verify(client,never()).submit(any());drain();
        verify(client).submit(argThat(job->job.settings().profile()==RenderSettings.Profile.CLASSIC
                &&job.settings().width()==128&&job.settings().height()==128
                &&job.settings().workers()==1&&job.settings().nativeMemoryLimitBytes()==RenderSettings.DEFAULT_MEMORY_LIMIT));
        capture.close();verify(task).cancel();verify(client).close();
    }
    @Test void lostAuthorizationDuringCaptureCancelsAndReleasesTicket() {
        start();valid.set(false);captured.complete(mock(Scene.class));drain();
        verify(client,never()).submit(any());verify(client).close();
        assertEquals(1,failures.get(),"state owner must be notified so its sole ticket cannot get stuck");assertEquals(0,delivered.get());
    }
    @Test void providerReloadAndCloseDiscardQueuedCompletions() {
        start();captured.complete(mock(Scene.class));drain();
        rendered.complete(mock(RenderResult.class));capture.cancel();drain();
        verify(task).cancel();verify(client).close();assertEquals(0,delivered.get());
    }
    @Test void timeoutCancelsNeverCompletingCaptureAndIgnoresItsLateResult() {
        start();deadlines.getFirst().run();captured.complete(mock(Scene.class));drain();
        verify(client).close();verify(client,never()).submit(any());assertEquals(1,failures.get());
    }
    @Test void missingChunksAndCapacityFailuresReleasePipelineWithoutLoggingPayloads() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);start();assertEquals(1,failures.get());
        verify(client,never()).capture(any());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);start();
        captured.completeExceptionally(new IllegalStateException("capacity"));drain();
        assertEquals(2,failures.get());verify(client).close();assertEquals(0,delivered.get());
    }
}
