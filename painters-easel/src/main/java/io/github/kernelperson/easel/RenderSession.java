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
                    if (error != null) { fail(request, "capture", error); return; }
                    try {
                        if (owner != client || !owner.active()) { fail(request, "Renderer was reloaded; try again."); return; }
                        request.task = owner.submit(new RenderJob(1, scene, SETTINGS));
                        request.task.completion().whenComplete((frame, problem) -> onMain(() -> {
                            if (!usable(request)) return;
                            if (problem != null) { fail(request, "render", problem); return; }
                            finish(request);
                            request.success.accept(frame);
                        }));
                    } catch (RuntimeException problem) { fail(request, "submit", problem); }
                }));
            return true;
        } catch (RuntimeException error) { fail(request, "setup", error); return false; }
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
    private void fail(Pending request, String phase, Throwable error) {
        if (!gate.current(request.key, request.token)) return;
        RendererException renderer = rendererFailure(error);
        if (renderer != null && renderer.code() == RenderFailureCode.MEMORY_LIMIT)
            plugin.getLogger().warning("Renderer failure phase=" + phase + " code=MEMORY_LIMIT reason=" + memoryReason(renderer));
        fail(request, safeError(error));
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
        RendererException renderer = rendererFailure(error);
        if (renderer != null && renderer.code() == RenderFailureCode.MEMORY_LIMIT) {
            return switch (memoryReason(renderer)) {
                case "per-request-admission" -> "This view exceeds the renderer memory limit. Try a less crowded area or ask an administrator.";
                case "aggregate-admission" -> "Renderer capacity is busy. Try again shortly.";
                default -> "Renderer memory limit reached. Try again later or ask an administrator.";
            };
        }
        return renderer != null
                ? "Renderer failed (" + renderer.code() + "). Check the operator guide."
                : "Capture failed. Ensure nearby chunks are loaded and the Renderer is available.";
    }
    private static RendererException rendererFailure(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof RendererException renderer) return renderer;
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        return null;
    }
    private static String memoryReason(RendererException renderer) {
        String message = renderer.getMessage();
        if ("request live memory exceeds effective limit".equals(message)) return "per-request-admission";
        if ("aggregate provider residency exceeds limit".equals(message)) return "aggregate-admission";
        if ("provider residency overflow".equals(message)) return "provider-overflow";
        if ("registered asset memory limit exceeded".equals(message)) return "asset-admission";
        if ("compact native renderer failed".equals(message)) return "native-compact";
        if ("native memory limit exceeded".equals(message)) return "native-render";
        return "unspecified";
    }
}
