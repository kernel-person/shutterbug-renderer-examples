package io.github.kernelperson.camera;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Transient cosmetics only. The tagged dispenser owns inventory and saved identity. */
final class CameraModel implements AutoCloseable {
    private record Visual(Block block,List<ItemDisplay> parts) {}
    private static final List<BlockFace> LIGHT_FACES=List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN);
    private final Map<String,Visual> displays=new HashMap<>();
    private final boolean enabled;
    private BukkitTask lightingTask;
    CameraModel(boolean enabled) {this.enabled=enabled;}
    void startLighting(Plugin plugin) {
        if(enabled&&lightingTask==null)
            lightingTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::refreshLighting,20,20);
    }
    static ItemStack item(Material material,String id) {
        var item=new ItemStack(material);var meta=item.getItemMeta();
        meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString("village_trades:"+id)));
        item.setItemMeta(meta);return item;
    }
    void apply(CameraTarget camera) {
        remove(camera.id());if(!enabled)return;
        List<ItemDisplay> created=new ArrayList<>();
        try {
            Location center=camera.block().getLocation().add(.5,.5,.5);
            spawn(center,new Vector3f(),"redstone_camera_body",new Quaternionf().rotationY((float)Math.PI),(float)CameraModelContract.BODY_SCALE,created);
            var pose=CameraPose.read((Dispenser)camera.block().getState());
            Location socket=CameraPose.socket(camera.block().getLocation(),pose.yaw(),pose.pitch());
            var direction=socket.getDirection();
            Quaternionf aim=new Quaternionf().rotationTo(new Vector3f(0,0,1),
                    new Vector3f((float)direction.getX(),(float)direction.getY(),(float)direction.getZ()));
            // Native ItemDisplay adds Y180 inside the item transform; cancel it once.
            aim.rotateY((float)Math.PI);
            var delta=socket.toVector().subtract(center.toVector());
            // Both entities stay in the owning block's chunk even when the visual lens crosses its edge.
            spawn(center,new Vector3f((float)delta.getX(),(float)delta.getY(),(float)delta.getZ()),"redstone_camera_lens",aim,1,created);
            Visual visual=new Visual(camera.block(),created);
            updateLighting(visual);displays.put(camera.id(),visual);
        } catch(RuntimeException failure) {created.forEach(Entity::remove);throw failure;}
    }
    private void spawn(Location location,Vector3f translation,String id,Quaternionf rotation,float scale,List<ItemDisplay> created) {
        ItemDisplay display=location.getWorld().spawn(location,ItemDisplay.class);created.add(display);
        display.setPersistent(false);display.setGravity(false);display.setInvulnerable(true);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTransformation(new Transformation(translation,rotation,new Vector3f(scale),new Quaternionf()));
        display.setDisplayWidth(3);display.setDisplayHeight(3);
        display.setItemStack(item(Material.PAPER,id));
    }
    void refreshLighting() {displays.values().forEach(this::updateLighting);}
    private void updateLighting(Visual visual) {
        Block block=visual.block();World world=block.getWorld();
        if(!world.isChunkLoaded(block.getX()>>4,block.getZ()>>4))return;
        int blockLight=0,skyLight=0;
        // Native item-display lighting samples the entity origin, inside our opaque dispenser.
        // Keep chunk ownership there, but light its artwork from the exposed neighboring cells.
        for(BlockFace face:LIGHT_FACES) {
            Block neighbor=block.getRelative(face);
            if(!world.isChunkLoaded(neighbor.getX()>>4,neighbor.getZ()>>4))continue;
            blockLight=Math.max(blockLight,neighbor.getLightFromBlocks());
            skyLight=Math.max(skyLight,neighbor.getLightFromSky());
        }
        // Raw sky light still follows the client's day/night lightmap; this is not fullbright.
        var brightness=new Display.Brightness(blockLight,skyLight);
        visual.parts().forEach(part->part.setBrightness(brightness));
    }
    void remove(String id) {var old=displays.remove(id);if(old!=null)old.parts().forEach(Entity::remove);}
    @Override public void close() {
        if(lightingTask!=null) {lightingTask.cancel();lightingTask=null;}
        displays.values().forEach(visual->visual.parts().forEach(Entity::remove));displays.clear();
    }
}
