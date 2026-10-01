package io.github.kernelperson.camera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraModelTest {
    @Test void physicalLensAndCaptureShareAimAndOwningChunkAtEveryAngle() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            for(float yaw:new float[]{0,37,90,180,271})for(float pitch:new float[]{-90,-21,0,47,90}) {
                var f=new Fixture(yaw,pitch);f.models.apply(f.target);
                var transformation=ArgumentCaptor.forClass(Transformation.class);
                verify(f.lens).setTransformation(transformation.capture());
                Transformation t=transformation.getValue();
                // Native Y180 acts first: raw optical front [0,0,+.25] becomes negative Z.
                Vector3f front=new Vector3f(0,0,-.25f).rotate(t.getLeftRotation()).add(t.getTranslation());
                var expected=CameraPose.lens(f.block.getLocation(),yaw,pitch);
                var origin=f.block.getLocation().add(.5,.5,.5);
                var lensFront=expected.toVector().subtract(expected.getDirection().multiply(.05)).subtract(origin.toVector());
                assertEquals(lensFront.getX(),front.x,1e-6);assertEquals(lensFront.getY(),front.y,1e-6);assertEquals(lensFront.getZ(),front.z,1e-6);
                verify(f.world,times(2)).spawn(eq(origin),eq(ItemDisplay.class));
                verify(f.dispenser,never()).getInventory();verify(f.block,never()).setType(any(),anyBoolean());
                f.models.close();verify(f.body).remove();verify(f.lens).remove();
            }
        }
    }
    @Test void reloadReplacesTransientDisplaysAndFailedSpawnCleansUp() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(0,0);f.models.apply(f.target);f.models.remove("one");
            verify(f.body).remove();verify(f.lens).remove();verify(f.dispenser,never()).update(anyBoolean(),anyBoolean());
            var failed=new Fixture(0,0);doThrow(new IllegalStateException()).when(failed.lens).setItemStack(any());
            assertThrows(IllegalStateException.class,()->failed.models.apply(failed.target));
            verify(failed.body).remove();verify(failed.lens).remove();
        }
    }
    @Test void nativeModelsAreOptional() {var f=new Fixture(0,0);new CameraModel(false).apply(f.target);verify(f.world,never()).spawn(any(Location.class),eq(ItemDisplay.class));}
    static final class Fixture {
        final World world=mock(World.class);final Block block=mock(Block.class);final Dispenser dispenser=mock(Dispenser.class);
        final ItemDisplay body=mock(ItemDisplay.class),lens=mock(ItemDisplay.class);
        final CameraModel models=new CameraModel(true);
        final CameraTarget target=new CameraTarget(block,"one",new NamespacedKey("test","camera"),new PowerEdge(false));
        Fixture(float yaw,float pitch) {
            when(block.getLocation()).thenAnswer(i->new Location(world,15,64,15));when(block.getState()).thenReturn(dispenser);
            when(world.spawn(any(Location.class),eq(ItemDisplay.class))).thenReturn(body,lens);
            var pdc=mock(PersistentDataContainer.class);when(dispenser.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(argThat(k->k!=null&&k.getKey().equals("yaw")),eq(PersistentDataType.FLOAT))).thenReturn(yaw);
            when(pdc.get(argThat(k->k!=null&&k.getKey().equals("pitch")),eq(PersistentDataType.FLOAT))).thenReturn(pitch);
        }
    }
}
