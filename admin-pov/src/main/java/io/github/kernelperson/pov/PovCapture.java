package io.github.kernelperson.pov;

import ke.ric.renderer.api.*;
import java.time.Duration;
import java.util.Set;
import java.util.function.*;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Single owned capture/render chain. Client closure cancels captures as well as native tasks. */
final class PovCapture implements AutoCloseable {
    static final RenderSettings SETTINGS=new RenderSettings(1,RenderSettings.Profile.CLASSIC,
            128,128,Set.of(),RenderSettings.DEFAULT_MEMORY_LIMIT,1,Duration.ofSeconds(30));
    private final Plugin plugin;
    private RendererClient client;
    private RenderTask task;
    private BukkitTask timeout;
    private long epoch;
    private boolean busy;
    private volatile boolean closed;
    PovCapture(Plugin plugin){this.plugin=plugin;}
    static boolean loaded(Location eye) {
        if(eye.getWorld()==null)return false;
        for(int x=(eye.getBlockX()-16)>>4;x<=((eye.getBlockX()+16)>>4);x++)
            for(int z=(eye.getBlockZ()-16)>>4;z<=((eye.getBlockZ()+16)>>4);z++)
                if(!eye.getWorld().isChunkLoaded(x,z))return false;
        return true;
    }
    void start(Location eye,BooleanSupplier valid,Consumer<byte[]> success,Runnable failure) {
        if(closed||busy)throw new IllegalStateException("Only one pipeline is permitted");
        if(!valid.getAsBoolean()||!loaded(eye)){failure.run();return;}
        var service=plugin.getServer().getServicesManager().load(RendererService.class);
        if(service==null){failure.run();return;}
        long token=++epoch;busy=true;
        Runnable fail=()->{if(token==epoch&&busy){cancel();failure.run();}};
        try {
            if(client==null||!client.active()){if(client!=null)client.close();client=service.createClient(plugin);}
            RendererClient owner=client;
            timeout=plugin.getServer().getScheduler().runTaskLater(plugin,fail,600);
            owner.capture(new CaptureRequest(eye.clone(),16,SETTINGS,2)).whenComplete((scene,error)->onMain(()->{
                if(!current(token,valid)){if(token==epoch)fail.run();return;}
                if(error!=null||owner!=client||!owner.active()||!loaded(eye)){fail.run();return;}
                try {
                    task=owner.submit(new RenderJob(1,scene,SETTINGS));
                    task.completion().whenComplete((result,problem)->onMain(()->{
                        if(!current(token,valid)){if(token==epoch)fail.run();return;}
                        if(problem!=null){fail.run();return;}
                        try {
                            byte[] pixels=MinecraftMapConverter.quantize(result,MapDither.NONE);
                            busy=false;task=null;if(timeout!=null)timeout.cancel();timeout=null;
                            success.accept(pixels);
                        }catch(RuntimeException badFrame){fail.run();}
                    }));
                }catch(RuntimeException unavailable){fail.run();}
            }));
        }catch(RuntimeException unavailable){fail.run();}
    }
    private boolean current(long token,BooleanSupplier valid){return !closed&&busy&&token==epoch&&valid.getAsBoolean();}
    private void onMain(Runnable action) {
        if(closed||!plugin.isEnabled())return;
        try {plugin.getServer().getScheduler().runTask(plugin,()->{if(!closed&&plugin.isEnabled())action.run();});}
        catch(org.bukkit.plugin.IllegalPluginAccessException ignored) { /* close owns cancellation */ }
    }
    void cancel() {
        epoch++;busy=false;if(timeout!=null)timeout.cancel();timeout=null;
        if(task!=null)task.cancel();task=null;
        if(client!=null)client.close();client=null;
    }
    @Override public void close(){closed=true;cancel();}
}
