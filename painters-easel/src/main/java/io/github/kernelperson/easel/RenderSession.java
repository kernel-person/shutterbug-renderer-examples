package io.github.kernelperson.easel;

import ke.ric.renderer.api.*;
import org.bukkit.Location;
import org.bukkit.event.*;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Only public API calls. All request state and application callbacks run on the server thread. */
final class RenderSession implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final WorkGate<String> gate = new WorkGate<>(8, Duration.ofSeconds(5).toNanos());
    private final Map<String, Pending> pending = new HashMap<>();
    private RendererClient client;
    private volatile boolean closed;
    private static final RenderSettings SETTINGS = new RenderSettings(1, RenderSettings.Profile.NORMAL,
            128, 128, Set.of(), RenderSettings.DEFAULT_MEMORY_LIMIT, 2, Duration.ofSeconds(30));
    private static final class Pending {
        final String key; final long token; final BooleanSupplier valid;
        final Consumer<RenderResult> success; final Consumer<String> failure;
        RenderTask task; BukkitTask deadline;
        Pending(String key, long token, BooleanSupplier valid, Consumer<RenderResult> success, Consumer<String> failure) {
            this.key=key; this.token=token; this.valid=valid; this.success=success; this.failure=failure;
        }
    }
    RenderSession(Plugin plugin) { this.plugin = plugin; }
    boolean request(String key, Location camera, BooleanSupplier valid,
                    Consumer<RenderResult> success, Consumer<String> failure) {
        if (closed || !valid.getAsBoolean()) return false;
        RendererService service = plugin.getServer().getServicesManager().load(RendererService.class);
        if (service == null) { failure.accept("Renderer unavailable. Check ShutterBugRenderer activation."); return false; }
        long token = gate.begin(key, System.nanoTime());
        if (token == 0) { failure.accept("Already busy or cooling down; try again shortly."); return false; }
        Pending request = new Pending(key, token, valid, success, failure);
        pending.put(key, request);
        try {
            if (client == null || !client.active()) {
                if (client != null) client.close();
                client = service.createClient(plugin);
            }
            RendererClient owner = client;
            request.deadline = plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> fail(request, "Capture timed out."), 600);
            owner.capture(new CaptureRequest(camera.clone(), 32, SETTINGS, 4)).whenComplete((scene, error) ->
                onMain(() -> {
                    if (!usable(request)) return;
                    if (error != null) { fail(request, safeError(error)); return; }
                    try {
                        if (owner != client || !owner.active()) { fail(request, "Renderer was reloaded; try again."); return; }
                        request.task = owner.submit(new RenderJob(1, scene, SETTINGS));
                        request.task.completion().whenComplete((frame, problem) -> onMain(() -> {
                            if (!usable(request)) return;
                            if (problem != null) { fail(request, safeError(problem)); return; }
                            finish(request);
                            request.success.accept(frame);
                        }));
                    } catch (RuntimeException problem) { fail(request, safeError(problem)); }
                }));
            return true;
        } catch (RuntimeException error) { fail(request, safeError(error)); return false; }
    }
    private boolean usable(Pending request) {
        if (closed || !gate.current(request.key, request.token)) return false;
        if (!request.valid.getAsBoolean()) { fail(request, "Destination no longer available."); return false; }
        return true;
    }
    private void finish(Pending request) {
        pending.remove(request.key, request);
        gate.finish(request.key, request.token);
        if (request.deadline != null) request.deadline.cancel();
    }
    private void fail(Pending request, String message) {
        if (!gate.current(request.key, request.token)) return;
        finish(request);
        if (request.task != null) request.task.cancel();
        request.failure.accept(message);
    }
    void invalidate(String key) {
        Pending request = pending.get(key);
        if (request != null) fail(request, "Capture cancelled.");
        gate.invalidate(key);
    }
    private void onMain(Runnable action) {
        if (closed || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!closed && plugin.isEnabled()) action.run();
            });
        } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) { /* disabling; close owns cleanup */ }
    }
    @EventHandler public void providerRemoved(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() != RendererService.class) return;
        for (Pending request : List.copyOf(pending.values())) fail(request, "Renderer was disabled; try again after activation.");
        if (client != null) client.close();
        client = null;
    }
    @Override public void close() {
        closed = true;
        for (Pending request : pending.values()) {
            if (request.deadline != null) request.deadline.cancel();
            if (request.task != null) request.task.cancel();
        }
        pending.clear(); gate.clear();
        if (client != null) client.close();
        client = null;
    }
    private static String safeError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause instanceof RendererException renderer
                ? "Renderer failed (" + renderer.code() + "). Check the operator guide."
                : "Capture failed. Ensure nearby chunks are loaded and the Renderer is available.";
    }
}
