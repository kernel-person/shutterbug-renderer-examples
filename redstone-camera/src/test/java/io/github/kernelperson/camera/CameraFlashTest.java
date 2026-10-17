package io.github.kernelperson.camera;

import java.lang.reflect.Field;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ResourceLock("Particle.FLASH.dataType")
class CameraFlashTest {
    private Object originalFlashType;
    @BeforeEach void useOlderVoidContractForOpticalTests()throws Exception {
        Field type=Particle.class.getDeclaredField("dataType");type.setAccessible(true);
        originalFlashType=type.get(Particle.FLASH);type.set(Particle.FLASH,Void.class);
    }
    @AfterEach void restoreRuntimeFlashContract()throws Exception {
        Field type=Particle.class.getDeclaredField("dataType");type.setAccessible(true);type.set(Particle.FLASH,originalFlashType);
    }
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
    // Missing Color.WHITE fails on the 26.2 contract; passing Color on the old Void contract also fails.
    @ParameterizedTest @ValueSource(classes={Void.class,Color.class})
    void acceptedFlashUsesRuntimeParticleDataContract(Class<?> runtimeDataType)throws Exception {
        // This test-only compatibility fixture gives the older compile API the runtime's declared type.
        Field type=Particle.class.getDeclaredField("dataType");type.setAccessible(true);Object previous=type.get(Particle.FLASH);
        try {
            type.set(Particle.FLASH,runtimeDataType);Fixture f=new Fixture(0,true);f.capture();
            if(runtimeDataType==Color.class)
                verify(f.world).spawnParticle(eq(Particle.FLASH),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d),eq(Color.WHITE));
            else verify(f.world).spawnParticle(eq(Particle.FLASH),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));
            verifyNoMoreInteractions(f.world);
        } finally {type.set(Particle.FLASH,previous);}
    }
    @Test void cosmeticParticleFailureDoesNotEscapeAcceptedCapture()throws Exception {
        Fixture f=new Fixture(0,true);
        doThrow(new IllegalArgumentException("missing required data class org.bukkit.Color")).when(f.world)
                .spawnParticle(eq(Particle.FLASH),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));
        assertDoesNotThrow(f::capture);
        verify(f.renders).request(eq("camera"),any(),any(),any(),any());
    }
    // Leaving capture or flash on legacy diagonal PDC values fails all six literal coordinates.
    @ParameterizedTest @CsvSource({"SOUTH,0,0,10.5,64.5,21.3473125", "WEST,90,0,9.6526875,64.5,20.5",
            "NORTH,180,0,10.5,64.5,19.6526875", "EAST,-90,0,11.3473125,64.5,20.5",
            "UP,0,-90,10.5,65.3473125,20.5", "DOWN,0,90,10.5,63.6526875,20.5"})
    void captureAndFlashShareCanonicalSixWayLens(BlockFace face,float yaw,float pitch,double x,double y,double z)throws Exception {
        Fixture f=new Fixture(0,true,face);f.capture();
        var render=ArgumentCaptor.forClass(Location.class);var flash=ArgumentCaptor.forClass(Location.class);
        verify(f.renders).request(eq("camera"),render.capture(),any(),any(),any());
        verify(f.world).spawnParticle(eq(Particle.FLASH),flash.capture(),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));
        assertEquals(x,render.getValue().getX(),1e-9);assertEquals(y,render.getValue().getY(),1e-9);assertEquals(z,render.getValue().getZ(),1e-9);
        assertEquals(yaw,render.getValue().getYaw());assertEquals(pitch,render.getValue().getPitch());assertEquals(render.getValue(),flash.getValue());
    }
    private static final class Fixture {
        final World world=mock(World.class);
        final RenderSession renders=mock(RenderSession.class);
        final RedstoneCameraPlugin plugin=mock(RedstoneCameraPlugin.class,CALLS_REAL_METHODS);
        final CameraTarget camera=mock(CameraTarget.class);
        Fixture(int freeSlot,boolean accepted)throws Exception {this(freeSlot,accepted,BlockFace.SOUTH);}
        Fixture(int freeSlot,boolean accepted,BlockFace face) throws Exception {
            var block=mock(Block.class);var dispenser=mock(Dispenser.class);
            var inventory=mock(Inventory.class);var data=mock(Directional.class);
            when(camera.block()).thenReturn(block);when(camera.id()).thenReturn("camera");when(camera.valid()).thenReturn(true);
            when(block.getState()).thenReturn(dispenser);when(block.getWorld()).thenReturn(world);
            when(block.getLocation()).thenAnswer(i->new Location(world,10,64,20));
            when(dispenser.getInventory()).thenReturn(inventory);when(inventory.firstEmpty()).thenReturn(freeSlot);
            var tags=mock(PersistentDataContainer.class);when(dispenser.getPersistentDataContainer()).thenReturn(tags);
            when(tags.get(argThat(k->k!=null&&k.getKey().equals("yaw")),eq(PersistentDataType.FLOAT))).thenReturn(32.75f);
            when(tags.get(argThat(k->k!=null&&k.getKey().equals("pitch")),eq(PersistentDataType.FLOAT))).thenReturn(-19.25f);
            when(dispenser.getBlockData()).thenReturn(data);when(data.getFacing()).thenReturn(face);
            when(renders.request(any(),any(),any(),any(),any())).thenReturn(accepted);
            doReturn(mock(java.util.logging.Logger.class)).when(plugin).getLogger();
            Field field=RedstoneCameraPlugin.class.getDeclaredField("renders");field.setAccessible(true);field.set(plugin,renders);
        }
        void capture() throws Exception {
            var method=RedstoneCameraPlugin.class.getDeclaredMethod("capture",CameraTarget.class);
            method.setAccessible(true);method.invoke(plugin,camera);
        }
    }
}
