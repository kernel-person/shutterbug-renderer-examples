package io.github.kernelperson.camera;

import java.util.stream.Stream;
import org.bukkit.*;
import org.bukkit.block.Dispenser;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraPoseTest {
    // Keeping the old raw PDC angles or reversing any face must break this test.
    @ParameterizedTest @MethodSource("facings")
    void dispenserFacingOverridesLegacyDiagonalPose(BlockFace face,float yaw,float pitch) {
        var dispenser=mock(Dispenser.class);var tags=mock(PersistentDataContainer.class);
        when(dispenser.getPersistentDataContainer()).thenReturn(tags);
        when(tags.get(argThat(k->k!=null&&k.getKey().equals("yaw")),eq(PersistentDataType.FLOAT))).thenReturn(32.75f);
        when(tags.get(argThat(k->k!=null&&k.getKey().equals("pitch")),eq(PersistentDataType.FLOAT))).thenReturn(-19.25f);
        var facing=mock(Directional.class);when(dispenser.getBlockData()).thenReturn(facing);when(facing.getFacing()).thenReturn(face);
        var pose=CameraPose.read(dispenser);
        assertEquals(yaw,pose.yaw());assertEquals(pitch,pose.pitch());
    }
    static Stream<Arguments> facings() {
        return Stream.of(Arguments.of(BlockFace.SOUTH,0f,0f),Arguments.of(BlockFace.WEST,90f,0f),
                Arguments.of(BlockFace.NORTH,180f,0f),Arguments.of(BlockFace.EAST,-90f,0f),
                Arguments.of(BlockFace.UP,0f,-90f),Arguments.of(BlockFace.DOWN,0f,90f));
    }
    // Preserving a legacy diagonal input in either socket or capture optics must fail.
    @ParameterizedTest @CsvSource({"32,-19,0,0,0,0,1", "132,21,90,0,-1,0,0", "172,19,180,0,0,0,-1",
            "-55,-21,-90,0,1,0,0", "37,-78,0,-90,0,1,0", "32,83,0,90,0,-1,0"})
    void legacyAnglesNormalizeToSixDirectionsAndClearWholeCamera(float oldYaw,float oldPitch,float yaw,float pitch,double x,double y,double z) {
        Location block=new Location(null,-8,64,12);
        var pose=new CameraPose(oldYaw,oldPitch);
        assertEquals(yaw,pose.yaw());assertEquals(pitch,pose.pitch());
        Location lens=CameraPose.lens(block,oldYaw,oldPitch),socket=CameraPose.socket(block,oldYaw,oldPitch);
        assertEquals(yaw,lens.getYaw());assertEquals(pitch,lens.getPitch());
        assertEquals(yaw,socket.getYaw());assertEquals(pitch,socket.getPitch());
        Vector center=new Vector(-7.5,64.5,12.5);
        Vector lensOffset=lens.toVector().subtract(center),socketOffset=socket.toVector().subtract(center);
        assertEquals(.8473125*x,lensOffset.getX(),1e-9);assertEquals(.8473125*y,lensOffset.getY(),1e-9);assertEquals(.8473125*z,lensOffset.getZ(),1e-9);
        assertEquals(.5473125*x,socketOffset.getX(),1e-9);assertEquals(.5473125*y,socketOffset.getY(),1e-9);assertEquals(.5473125*z,socketOffset.getZ(),1e-9);
        assertEquals(-8,block.getX());assertEquals(64,block.getY());assertEquals(12,block.getZ());
    }
    @Test void nonFiniteAndOutOfRangeAnglesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->CameraPose.lens(new Location(null,0,0,0),Float.NaN,0));
        assertThrows(IllegalArgumentException.class,()->CameraPose.lens(new Location(null,0,0,0),0,Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->CameraPose.lens(new Location(null,0,0,0),0,91));
    }
}
