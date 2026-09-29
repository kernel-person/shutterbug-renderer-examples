package io.github.kernelperson.camera;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraPoseTest {
    @Test void preciseAnglesSurviveTileStorageAndOldCamerasUseTheirFacing() {
        var dispenser=mock(org.bukkit.block.Dispenser.class);
        var tags=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(dispenser.getPersistentDataContainer()).thenReturn(tags);
        java.util.Map<org.bukkit.NamespacedKey,Float> stored=new java.util.HashMap<>();
        doAnswer(i->{stored.put(i.getArgument(0),i.getArgument(2));return null;}).when(tags)
                .set(any(),eq(org.bukkit.persistence.PersistentDataType.FLOAT),any(Float.class));
        when(tags.get(any(),eq(org.bukkit.persistence.PersistentDataType.FLOAT)))
                .thenAnswer(i->stored.get(i.getArgument(0)));
        var facing=mock(org.bukkit.block.data.Directional.class);
        when(dispenser.getBlockData()).thenReturn(facing);
        when(facing.getFacing()).thenReturn(org.bukkit.block.BlockFace.WEST);
        assertEquals(90,CameraPose.read(dispenser).yaw());
        var pose=new CameraPose(32.75f,-19.25f);
        pose.save(dispenser);
        assertEquals(pose,CameraPose.read(dispenser));
        stored.replaceAll((key,value)->Float.NaN);
        assertEquals(90,CameraPose.read(dispenser).yaw(),"malformed stored pose safely falls back to block facing");
    }
    @Test void lensPreservesPreciseAimAndClearsTheWholeDispenserAtEveryAngle() {
        for(float yaw:new float[]{0,90,-90,180,45,135,-135,-45,23.5f})
            for(float pitch:new float[]{0,-45,45,-90,90,27.5f}) {
                Location block=new Location(null,-8,64,12);
                Location lens=CameraPose.lens(block,yaw,pitch);
                assertEquals(yaw,lens.getYaw());assertEquals(pitch,lens.getPitch());
                var delta=lens.toVector().subtract(block.toVector().add(new org.bukkit.util.Vector(.5,.5,.5)));
                double extent=Math.max(Math.abs(delta.getX()),Math.max(Math.abs(delta.getY()),Math.abs(delta.getZ())));
                assertTrue(extent>.5,"lens outside block at "+yaw+", "+pitch);
                var expected=new Location(null,0,0,0,yaw,pitch).getDirection();
                double max=Math.max(Math.abs(expected.getX()),Math.max(Math.abs(expected.getY()),Math.abs(expected.getZ())));
                assertEquals(.5323125/max+.015+.25+.05,delta.length(),1e-9,"capture clears body, socket and complete lens");
                assertTrue(delta.normalize().distance(expected)<1e-9);
                assertEquals(-8,block.getX(),"caller location is not mutated");
            }
    }
    @Test void nonFiniteAnglesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->CameraPose.lens(new Location(null,0,0,0),Float.NaN,0));
        assertThrows(IllegalArgumentException.class,()->CameraPose.lens(new Location(null,0,0,0),0,Float.POSITIVE_INFINITY));
    }
}
