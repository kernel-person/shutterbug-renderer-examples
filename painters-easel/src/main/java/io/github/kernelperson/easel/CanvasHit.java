package io.github.kernelperson.easel;
import org.bukkit.util.Vector;
final class CanvasHit {
    record Pixel(int x,int y) {}
    static Pixel intersect(Vector eye,Vector direction,Vector center,Vector normal) {
        if(!Double.isFinite(direction.lengthSquared())||direction.lengthSquared()<1e-8) return null;
        Vector ray=direction.clone().normalize();
        double facing=ray.dot(normal);
        if(facing>=-1e-6) return null;
        double distance=center.clone().subtract(eye).dot(normal)/facing;
        if(!Double.isFinite(distance)||distance<0||distance>5) return null;
        Vector offset=eye.clone().add(ray.multiply(distance)).subtract(center);
        double u=.5+offset.dot(new Vector(normal.getZ(),0,-normal.getX())),v=.5-offset.getY();
        if(u<0||u>=1||v<0||v>=1) return null;
        return new Pixel((int)(u*128),(int)(v*128));
    }
}
