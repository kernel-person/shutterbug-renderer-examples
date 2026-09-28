package io.github.kernelperson.easel;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Optional cosmetic adapter. Frames and their saved canvases remain authoritative. */
final class EaselModel implements AutoCloseable {
    // 26.2 invisible item-frame map face: .46875 - .5 + (1 + .01)/128.
    static final double MAP_FACE_OFFSET=-.023359375;
    private final NamespacedKey marker;
    private final ItemStack item;
    private final Map<UUID,ItemDisplay> displays=new HashMap<>();
    EaselModel(NamespacedKey marker,ItemStack item) {this.marker=marker;this.item=item;}

    void apply(ItemFrame frame,Block base) {
        Block board=base.getRelative(BlockFace.UP);
        if(item==null) {
            if(marked(frame)) {
                if(base.getType()==Material.BARRIER) base.setType(Material.OAK_FENCE,false);
                if(board.getType()==Material.BARRIER) board.setType(Material.OAK_PLANKS,false);
                frame.setVisible(true);frame.setFixed(false);
                frame.getPersistentDataContainer().remove(marker);
            }
            return;
        }
        ItemDisplay existing=displays.get(frame.getUniqueId());
        if(existing!=null&&existing.isValid()) return;
        if((base.getType()!=Material.OAK_FENCE&&!(marked(frame)&&base.getType()==Material.BARRIER))
                ||(board.getType()!=Material.OAK_PLANKS&&!(marked(frame)&&board.getType()==Material.BARRIER)))
            throw new IllegalStateException("Easel support was changed; refusing to replace unrelated blocks");
        // Keep the entity in the frame's chunk. A local transform moves only the artwork;
        // frame unload/reload then owns the complete transient visual lifecycle.
        Location origin=frame.getLocation().clone();
        origin.setDirection(frame.getFacing().getDirection());origin.setYaw(origin.getYaw()+180);origin.setPitch(0);
        ItemDisplay display=frame.getWorld().spawn(origin,ItemDisplay.class);
        BlockState originalBase=base.getState(),originalBoard=board.getState();
        boolean changed=false,visible=frame.isVisible(),fixed=frame.isFixed(),wasMarked=marked(frame);
        try {
            display.setPersistent(false);display.setGravity(false);display.setInvulnerable(true);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(new Transformation(new Vector3f(0,-1,(float)(.125-MAP_FACE_OFFSET)),
                    new Quaternionf(),new Vector3f(1),new Quaternionf()));
            display.setDisplayWidth(3);display.setDisplayHeight(4);
            display.setItemStack(item);
            changed=true;
            base.setType(Material.BARRIER,false);board.setType(Material.BARRIER,false);
            frame.setVisible(false);frame.setFixed(true);
            frame.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
            displays.put(frame.getUniqueId(),display);
        } catch(RuntimeException failure) {
            display.remove();
            if(changed) {
                originalBase.update(true,false);originalBoard.update(true,false);
                frame.setVisible(visible);frame.setFixed(fixed);
                if(!wasMarked) frame.getPersistentDataContainer().remove(marker);
            }
            throw failure;
        }
    }
    void unload(ItemFrame frame) {
        ItemDisplay display=displays.remove(frame.getUniqueId());if(display!=null) display.remove();
    }
    void remove(ItemFrame frame,Block base) {
        unload(frame);
        if(!marked(frame)) return;
        if(base.getType()==Material.BARRIER) base.setType(Material.AIR,false);
        Block board=base.getRelative(BlockFace.UP);
        if(board.getType()==Material.BARRIER) board.setType(Material.AIR,false);
    }
    private boolean marked(ItemFrame frame) {return frame.getPersistentDataContainer().has(marker,PersistentDataType.BYTE);}
    @Override public void close() {displays.values().forEach(Entity::remove);displays.clear();}
}
