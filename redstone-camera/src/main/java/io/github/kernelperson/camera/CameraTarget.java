package io.github.kernelperson.camera;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.block.Dispenser;
import org.bukkit.persistence.PersistentDataType;
record CameraTarget(Block block,String id,NamespacedKey tag,PowerEdge edge) {
    boolean valid() {
        return block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)
                && block.getType()==Material.DISPENSER
                && block.getState() instanceof Dispenser dispenser
                && id.equals(dispenser.getPersistentDataContainer().get(tag,PersistentDataType.STRING));
    }
}
