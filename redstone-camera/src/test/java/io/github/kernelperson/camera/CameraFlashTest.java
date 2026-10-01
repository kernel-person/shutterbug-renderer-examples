package io.github.kernelperson.camera;

import java.lang.reflect.Field;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraFlashTest {
    // Removing the accepted-request guard must fail the rejection test; placing
    // the particle at the block centre must fail the optical-clearance assertion.
    @Test void acceptedShotFlashesOnceOutsideTheLens() throws Exception {
        Fixture f=new Fixture(0,true);
        f.capture();
        var location=ArgumentCaptor.forClass(Location.class);
        verify(f.world).spawnParticle(eq(Particle.FLASH),location.capture(),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));
        assertEquals(10.5,location.getValue().getX(),1e-9);
        assertEquals(64.5,location.getValue().getY(),1e-9);
        assertEquals(21.3473125,location.getValue().getZ(),1e-9);
        verify(f.renders).request(eq("camera"),any(),any(),any(),any());
        verifyNoMoreInteractions(f.world);
    }
    @Test void busyOrUnavailableRequestDoesNotFlash() throws Exception {
        Fixture f=new Fixture(0,false);f.capture();
        verify(f.renders).request(eq("camera"),any(),any(),any(),any());
        verifyNoInteractions(f.world);
    }
    @Test void fullInventoryDoesNotCaptureOrFlash() throws Exception {
        Fixture f=new Fixture(-1,true);
        // Suppress the pre-existing sound path: Bukkit's sound registry needs a
        // running server. Full inventory must reject even an accepting session.
        when(f.camera.valid()).thenReturn(false);f.capture();
        verifyNoInteractions(f.renders);
        verifyNoInteractions(f.world);
    }
    private static final class Fixture {
        final World world=mock(World.class);
        final RenderSession renders=mock(RenderSession.class);
        final RedstoneCameraPlugin plugin=mock(RedstoneCameraPlugin.class,CALLS_REAL_METHODS);
        final CameraTarget camera=mock(CameraTarget.class);
        Fixture(int freeSlot,boolean accepted) throws Exception {
            var block=mock(Block.class);var dispenser=mock(Dispenser.class);
            var inventory=mock(Inventory.class);var data=mock(Directional.class);
            when(camera.block()).thenReturn(block);when(camera.id()).thenReturn("camera");when(camera.valid()).thenReturn(true);
            when(block.getState()).thenReturn(dispenser);when(block.getWorld()).thenReturn(world);
            when(block.getLocation()).thenAnswer(i->new Location(world,10,64,20));
            when(dispenser.getInventory()).thenReturn(inventory);when(inventory.firstEmpty()).thenReturn(freeSlot);
            when(dispenser.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(dispenser.getBlockData()).thenReturn(data);when(data.getFacing()).thenReturn(BlockFace.SOUTH);
            when(renders.request(any(),any(),any(),any(),any())).thenReturn(accepted);
            Field field=RedstoneCameraPlugin.class.getDeclaredField("renders");field.setAccessible(true);field.set(plugin,renders);
        }
        void capture() throws Exception {
            var method=RedstoneCameraPlugin.class.getDeclaredMethod("capture",CameraTarget.class);
            method.setAccessible(true);method.invoke(plugin,camera);
        }
    }
}
