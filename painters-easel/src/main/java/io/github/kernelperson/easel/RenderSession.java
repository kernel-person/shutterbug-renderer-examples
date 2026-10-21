package io.github.kernelperson.easel;

import ke.ric.renderer.api.*;
import org.bukkit.Location;
import org.bukkit.event.*;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Addon admission and destination identity only; Renderer owns capture, deadlines and conversion. */
final class RenderSession implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final WorkGate<String> gate = new WorkGate<>(8, Duration.ofSeconds(5).toNanos());
    private final Map<String, Pending> pending = new HashMap<>();
    private final Renderer renderer;
    private boolean closed;
    static final RenderSettings SETTINGS = new RenderSettings(1, RenderSettings.Profile.NORMAL,
            128, 128, Set.of(), RenderSettings.DEFAULT_MEMORY_LIMIT, 2, Duration.ofSeconds(30));
    private static final class Pending {
        final String key; final long token; final BooleanSupplier valid;
        final Consumer<RenderResult> success; final Consumer<String> failure;
        CompletableFuture<?> operation;
        Pending(String key,long token,BooleanSupplier valid,Consumer<RenderResult> success,Consumer<String> failure) {
            this.key=key;this.token=token;this.valid=valid;this.success=success;this.failure=failure;
        }
    }
    RenderSession(Plugin plugin) { this(plugin, Renderer.forPlugin(plugin)); }
    RenderSession(Plugin plugin, Renderer renderer) { this.plugin=plugin;this.renderer=renderer; }
    boolean request(String key,Location camera,BooleanSupplier valid,Consumer<RenderResult> success,Consumer<String> failure) {
        if(closed || !valid.getAsBoolean())return false;
        if(plugin.getServer().getServicesManager().load(RendererService.class)==null) {
            failure.accept("Renderer unavailable. Check ShutterBugRenderer activation.");return false;
        }
        long token=gate.begin(key,System.nanoTime());
        if(token==0){failure.accept("Already busy or cooling down; try again shortly.");return false;}
        Pending request=new Pending(key,token,valid,success,failure);pending.put(key,request);
        try {
            request.operation=renderer.snapshot(camera).settings(SETTINGS).radius(32).chunksPerTick(4)
                    .capture(frame -> complete(request, frame.result()), error -> fail(request, "snapshot", error));
            return true;
        }catch(RuntimeException error){fail(request,safeError(error));return false;}
    }
    private boolean current(Pending request){return !closed && gate.current(request.key,request.token);}
    private void complete(Pending request,RenderResult result) {
        if(!current(request))return;
        if(!request.valid.getAsBoolean()){fail(request,"Destination no longer available.");return;}
        finish(request);request.success.accept(result);
    }
    private void finish(Pending request){pending.remove(request.key,request);gate.finish(request.key,request.token);}
    private void fail(Pending request,String message) {
        if(!current(request))return;
        finish(request);if(request.operation!=null)request.operation.cancel(true);request.failure.accept(message);
    }
    private void fail(Pending request, String phase, Throwable error) {
        if (!gate.current(request.key, request.token)) return;
        RendererException renderer = rendererFailure(error);
        if (renderer != null && renderer.code() == RenderFailureCode.MEMORY_LIMIT)
            plugin.getLogger().warning("Renderer failure phase=" + phase + " code=MEMORY_LIMIT reason=" + memoryReason(renderer));
        fail(request, safeError(error));
    }
    void invalidate(String key) {
        Pending request=pending.get(key);if(request!=null)fail(request,"Capture cancelled.");gate.invalidate(key);
    }
    @EventHandler public void providerRemoved(ServiceUnregisterEvent event) {
        if(event.getProvider().getService()!=RendererService.class)return;
        for(Pending request:List.copyOf(pending.values()))fail(request,"Renderer was disabled; try again after activation.");
    }
    @Override public void close() {
        closed=true;
        for(Pending request:List.copyOf(pending.values()))if(request.operation!=null)request.operation.cancel(true);
        pending.clear();gate.clear();renderer.close();
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
