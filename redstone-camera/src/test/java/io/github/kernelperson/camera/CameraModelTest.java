package io.github.kernelperson.camera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraModelTest {
    // Ignoring block facing, reversing the optics, or moving an entity into the adjacent chunk fails.
    @ParameterizedTest @CsvSource({"SOUTH,0,0,1", "WEST,-1,0,0", "NORTH,0,0,-1", "EAST,1,0,0", "UP,0,1,0", "DOWN,0,-1,0"})
    void physicalLensUsesDispenserFacingAndStaysInOwningChunk(BlockFace face,double x,double y,double z) {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(face);f.models.apply(f.target);
            var transformation=ArgumentCaptor.forClass(Transformation.class);
            verify(f.lens).setTransformation(transformation.capture());
            Transformation t=transformation.getValue();
            // Native Y180 acts first: raw optical front [0,0,+.25] becomes negative Z.
            Vector3f front=new Vector3f(0,0,-.25f).rotate(t.getLeftRotation()).add(t.getTranslation());
            var origin=f.block.getLocation().add(.5,.5,.5);
            assertEquals(.7973125*x,front.x,1e-6);assertEquals(.7973125*y,front.y,1e-6);assertEquals(.7973125*z,front.z,1e-6);
            verify(f.world,times(2)).spawn(eq(origin),eq(ItemDisplay.class));
            verify(f.dispenser,never()).getInventory();verify(f.block,never()).setType(any(),anyBoolean());
            f.models.close();verify(f.body).remove();verify(f.lens).remove();
        }
    }
    @Test void reloadReplacesTransientDisplaysAndFailedSpawnCleansUp() {
        try(var items=mockStatic(CameraModel.class,CALLS_REAL_METHODS)) {
            items.when(()->CameraModel.item(any(),anyString())).thenReturn(mock(ItemStack.class));
            var f=new Fixture(BlockFace.SOUTH);f.models.apply(f.target);f.models.remove("one");
            verify(f.body).remove();verify(f.lens).remove();verify(f.dispenser,never()).update(anyBoolean(),anyBoolean());
            var failed=new Fixture(BlockFace.SOUTH);doThrow(new IllegalStateException()).when(failed.lens).setItemStack(any());
            assertThrows(IllegalStateException.class,()->failed.models.apply(failed.target));
            verify(failed.body).remove();verify(failed.lens).remove();
        }
    }
    @Test void nativeModelsAreOptional() {var f=new Fixture(BlockFace.SOUTH);new CameraModel(false).apply(f.target);verify(f.world,never()).spawn(any(Location.class),eq(ItemDisplay.class));}
    static final class Fixture {
        final World world=mock(World.class);final Block block=mock(Block.class);final Dispenser dispenser=mock(Dispenser.class);
        final ItemDisplay body=mock(ItemDisplay.class),lens=mock(ItemDisplay.class);
        final CameraModel models=new CameraModel(true);
        final CameraTarget target=new CameraTarget(block,"one",new NamespacedKey("test","camera"),new PowerEdge(false));
        Fixture(BlockFace face) {
            when(block.getLocation()).thenAnswer(i->new Location(world,15,64,15));when(block.getState()).thenReturn(dispenser);
            when(world.spawn(any(Location.class),eq(ItemDisplay.class))).thenReturn(body,lens);
            var pdc=mock(PersistentDataContainer.class);when(dispenser.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(argThat(k->k!=null&&k.getKey().equals("yaw")),eq(PersistentDataType.FLOAT))).thenReturn(37f);
            when(pdc.get(argThat(k->k!=null&&k.getKey().equals("pitch")),eq(PersistentDataType.FLOAT))).thenReturn(-21f);
            var data=mock(Directional.class);when(dispenser.getBlockData()).thenReturn(data);when(data.getFacing()).thenReturn(face);
        }
    }
}
