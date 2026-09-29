package io.github.kernelperson.easel;

import java.awt.Color;
import java.util.HashSet;
import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasLinenTest {
    @SuppressWarnings("deprecation")
    @Test void blankLinenRetainsMultipleLightShadesAfterMapQuantization() {
        byte[] black = new byte[128*128*4];
        for(int p=3;p<black.length;p+=4) black[p]=(byte)255;
        var canvas=new PigmentCanvas(black);
        var shades=new HashSet<Byte>();
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) {
            int rgb=canvas.rgb(x,y);
            assertTrue(((rgb>>16)&255)>200 && ((rgb>>8)&255)>200 && (rgb&255)>190);
            assertTrue(((rgb>>16)&255)-(rgb&255)>=5,"linen has restrained warm variation, not flat grey noise");
            shades.add(MapPalette.matchColor(new Color(rgb)));
        }
        assertTrue(shades.size()>=2,"the linen should not quantize to a single white colour");
        assertEquals(0,canvas.progress());
    }
}
