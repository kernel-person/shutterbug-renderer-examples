package io.github.kernelperson.camera;
import org.bukkit.Server;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.HashMap;
import static org.mockito.Mockito.*;

class PhysicsTriggerTest {
    @Test void directPowerPhysicsSchedulesOneCoalescedSample() throws Exception {
        var plugin=mock(RedstoneCameraPlugin.class,CALLS_REAL_METHODS);
        Field field=RedstoneCameraPlugin.class.getDeclaredField("cameras");field.setAccessible(true);
        var cameras=new HashMap<String,CameraTarget>();cameras.put("test",mock(CameraTarget.class));field.set(plugin,cameras);
        var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);
        doReturn(server).when(plugin).getServer();when(server.getScheduler()).thenReturn(scheduler);
        plugin.physics(mock(BlockPhysicsEvent.class));plugin.physics(mock(BlockPhysicsEvent.class));
        verify(scheduler,times(1)).runTask(eq(plugin),any(Runnable.class));
    }
}
