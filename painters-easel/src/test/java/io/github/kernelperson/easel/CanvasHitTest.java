package io.github.kernelperson.easel;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CanvasHitTest {
    @Test void mapsFrontViewToPixelsAndRejectsBehindOutOfBoundsAndFarAway() {
        var c=new Vector(0,0,0); var n=new Vector(0,0,1);
        assertEquals(new CanvasHit.Pixel(64,64),CanvasHit.intersect(new Vector(0,0,2),new Vector(0,0,-1),c,n));
        assertEquals(new CanvasHit.Pixel(96,32),CanvasHit.intersect(new Vector(.25,.25,2),new Vector(0,0,-1),c,n));
        assertNull(CanvasHit.intersect(new Vector(0,0,-2),new Vector(0,0,1),c,n));
        assertNull(CanvasHit.intersect(new Vector(1,0,2),new Vector(0,0,-1),c,n));
        assertNull(CanvasHit.intersect(new Vector(0,0,9),new Vector(0,0,-1),c,n));
        assertNull(CanvasHit.intersect(new Vector(0,0,2),new Vector(1,0,0),c,n));
    }
}
