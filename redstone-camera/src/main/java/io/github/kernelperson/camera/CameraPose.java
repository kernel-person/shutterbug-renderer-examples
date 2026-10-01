package io.github.kernelperson.camera;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataType;

/** Exact view is independent of the vanilla dispenser's six visual orientations. */
record CameraPose(float yaw,float pitch) {
    private static final NamespacedKey YAW=new NamespacedKey("rendererredstonecamera","yaw");
    private static final NamespacedKey PITCH=new NamespacedKey("rendererredstonecamera","pitch");
    CameraPose {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch < -90||pitch > 90)
            throw new IllegalArgumentException("Invalid camera angles");
    }
    void save(Dispenser dispenser) {
        dispenser.getPersistentDataContainer().set(YAW,PersistentDataType.FLOAT,yaw);
        dispenser.getPersistentDataContainer().set(PITCH,PersistentDataType.FLOAT,pitch);
    }
    static CameraPose read(Dispenser dispenser) {
        Float yaw=dispenser.getPersistentDataContainer().get(YAW,PersistentDataType.FLOAT);
        Float pitch=dispenser.getPersistentDataContainer().get(PITCH,PersistentDataType.FLOAT);
        if(yaw!=null&&pitch!=null&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&pitch>=-90&&pitch<=90)
            return new CameraPose(yaw,pitch);
        // Cameras placed before precise aiming retain their existing facing.
        Location facing=new Location(null,0,0,0);
        facing.setDirection(((Directional)dispenser.getBlockData()).getFacing().getDirection());
        return new CameraPose(facing.getYaw(),facing.getPitch());
    }
    static Location lens(Location block,float yaw,float pitch) {
        Location lens=socket(block,yaw,pitch);
        return lens.add(lens.getDirection().multiply(CameraModelContract.LENS_FRONT+CameraModelContract.CLEARANCE));
    }
    static Location socket(Location block,float yaw,float pitch) {
        new CameraPose(yaw,pitch); // validate before constructing a render request
        Location lens=block.clone().add(.5,.5,.5);
        lens.setYaw(yaw);lens.setPitch(pitch);
        var direction=lens.getDirection();
        double extent=Math.max(Math.abs(direction.getX()),Math.max(Math.abs(direction.getY()),Math.abs(direction.getZ())));
        return lens.add(direction.multiply(CameraModelContract.BODY_HALF_EXTENT/extent+CameraModelContract.SOCKET_GAP));
    }
}
