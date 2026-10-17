package io.github.kernelperson.camera;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Dispenser;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataType;

/** All optics use one of the dispenser's six orthogonal directions. */
record CameraPose(float yaw,float pitch) {
    private static final NamespacedKey YAW=new NamespacedKey("rendererredstonecamera","yaw");
    private static final NamespacedKey PITCH=new NamespacedKey("rendererredstonecamera","pitch");
    CameraPose {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch < -90||pitch > 90)
            throw new IllegalArgumentException("Invalid camera angles");
        // Normalize old angle-based callers too; never leave diagonal optical geometry.
        var direction=new Location(null,0,0,0,yaw,pitch).getDirection();
        double x=direction.getX(),y=direction.getY(),z=direction.getZ();
        if(Math.abs(y)>=Math.max(Math.abs(x),Math.abs(z))) {yaw=0;pitch=y>0?-90:90;}
        else {yaw=Math.abs(x)>Math.abs(z)?(x>0?-90:90):(z>0?0:180);pitch=0;}
    }
    void save(Dispenser dispenser) {
        dispenser.getPersistentDataContainer().set(YAW,PersistentDataType.FLOAT,yaw);
        dispenser.getPersistentDataContainer().set(PITCH,PersistentDataType.FLOAT,pitch);
    }
    static CameraPose read(Dispenser dispenser) {
        // Vanilla placement already faces the dispenser toward its placer, including UP/DOWN.
        // The live block is authoritative even when legacy yaw/pitch tags disagree.
        return fromFacing(((Directional)dispenser.getBlockData()).getFacing());
    }
    static CameraPose fromFacing(BlockFace face) {
        return switch(face) {
            case SOUTH -> new CameraPose(0,0);
            case WEST -> new CameraPose(90,0);
            case NORTH -> new CameraPose(180,0);
            case EAST -> new CameraPose(-90,0);
            case UP -> new CameraPose(0,-90);
            case DOWN -> new CameraPose(0,90);
            default -> throw new IllegalArgumentException("Camera facing must be orthogonal");
        };
    }
    static Location lens(Location block,float yaw,float pitch) {
        Location lens=socket(block,yaw,pitch);
        return lens.add(lens.getDirection().multiply(CameraModelContract.LENS_FRONT+CameraModelContract.CLEARANCE));
    }
    static Location socket(Location block,float yaw,float pitch) {
        CameraPose pose=new CameraPose(yaw,pitch);
        Location lens=block.clone().add(.5,.5,.5);
        lens.setYaw(pose.yaw());lens.setPitch(pose.pitch());
        var direction=lens.getDirection();
        double extent=Math.max(Math.abs(direction.getX()),Math.max(Math.abs(direction.getY()),Math.abs(direction.getZ())));
        return lens.add(direction.multiply(CameraModelContract.BODY_HALF_EXTENT/extent+CameraModelContract.SOCKET_GAP));
    }
}
