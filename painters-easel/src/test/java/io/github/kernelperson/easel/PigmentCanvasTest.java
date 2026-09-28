package io.github.kernelperson.easel;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class PigmentCanvasTest {
    byte[] solid(int r,int g,int b) {
        byte[] rgba=new byte[128*128*4];
        for(int i=0;i<rgba.length;i+=4) {rgba[i]=(byte)r; rgba[i+1]=(byte)g; rgba[i+2]=(byte)b; rgba[i+3]=(byte)255;}
        return rgba;
    }
    @Test void wrongMagentaDoesNothingOnGreenAndCyanYellowBuildGreen() {
        var canvas=new PigmentCanvas(solid(0,255,0));
        int empty=canvas.rgb(64,64);
        canvas.stroke(64,64,8,1);
        assertEquals(empty,canvas.rgb(64,64)); assertEquals(0,canvas.progress());
        for(int n=0;n<4;n++) canvas.stroke(64,64,8,0);
        int cyan=canvas.rgb(64,64);
        assertEquals(0,(cyan>>16)&255); assertTrue((cyan&255)>200);
        for(int n=0;n<4;n++) canvas.stroke(64,64,8,2);
        assertEquals(0x00ff00,canvas.rgb(64,64));
        assertEquals(0xf6f6f6,canvas.rgb(0,0));
        double progress=canvas.progress();
        for(int n=0;n<4;n++) {canvas.stroke(64,64,1,0); canvas.stroke(64,64,1,2);}
        assertEquals(0x00ff00,canvas.rgb(64,64));
        assertTrue(canvas.progress()>=progress && canvas.progress()<=1);
    }
    @Test void blackNeedsAllThreeAndWhiteHasFiniteCompleteProgress() {
        var white=new PigmentCanvas(solid(255,255,255));
        assertEquals(1,white.progress());
        var black=new PigmentCanvas(solid(0,0,0));
        for(int color=0;color<3;color++) for(int n=0;n<4;n++) black.stroke(0,0,8,color);
        assertEquals(0,black.rgb(0,0));
        assertTrue(black.progress()>0);
        assertThrows(IllegalArgumentException.class,()->black.stroke(-1,0,8,0));
        assertThrows(IllegalArgumentException.class,()->black.stroke(0,0,0,0));
    }
    @Test void roundtripKeepsReferenceAndProgressAndRejectsMalformedData() throws Exception {
        var original=new PigmentCanvas(solid(13,78,201));
        original.stroke(127,127,8,0);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); original.write(out);
        var restored=PigmentCanvas.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original.rgb(127,127),restored.rgb(127,127));
        assertEquals(original.progress(),restored.progress());
        assertThrows(IOException.class,()->PigmentCanvas.read(new ByteArrayInputStream(new byte[8])));
        assertThrows(IOException.class,()->PigmentCanvas.read(new ByteArrayInputStream(Arrays.copyOf(out.toByteArray(),20))));
    }
    @Test void oneBroadDragFillsItsEntireColourChannelWithoutGaps() {
        var canvas=new PigmentCanvas(solid(0,255,255));
        canvas.strokeLine(20,64,100,64,12,0);
        for(int x=20;x<=100;x++) for(int y=53;y<=75;y++) assertEquals(0x00ffff,canvas.rgb(x,y));
        assertNotEquals(0x00ffff,canvas.rgb(64,51));
        assertNotEquals(0x00ffff,canvas.rgb(6,64));
        double done=canvas.progress();canvas.strokeLine(100,64,20,64,12,0);assertEquals(done,canvas.progress());
    }
    @Test void diagonalStrokeClipsAtCanvasEdgesAndWrongDyeStillDoesNothing() {
        var canvas=new PigmentCanvas(solid(0,255,255));
        canvas.strokeLine(0,0,127,127,12,1);assertEquals(0,canvas.progress());
        canvas.strokeLine(0,0,127,127,12,0);
        for(int i=0;i<128;i++) assertEquals(0x00ffff,canvas.rgb(i,i));
        assertNotEquals(0x00ffff,canvas.rgb(0,127));
    }
}
