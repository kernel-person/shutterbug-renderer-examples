package io.github.kernelperson.easel;
import java.io.*;
final class PigmentCanvas {
    private static final int PIXELS=128*128;
    private final byte[] needed=new byte[PIXELS*3], applied=new byte[PIXELS*3];
    PigmentCanvas(byte[] rgba) {
        if(rgba.length!=PIXELS*4) throw new IllegalArgumentException("Expected 128x128 RGBA");
        for(int p=0;p<PIXELS;p++) for(int c=0;c<3;c++) {
            int alpha=rgba[p*4+3]&255;
            needed[p*3+c]=(byte)((255-(rgba[p*4+c]&255))*alpha/255);
        }
    }
    /** CMY channels are RGB complements. Wrong pigment is ignored, never allowed to spoil the target. */
    void stroke(int x,int y,int radius,int pigment) {
        strokeLine(x,y,x,y,radius,pigment);
    }
    /** A round brush swept along the input segment; each pass fills the selected channel. */
    void strokeLine(int fromX,int fromY,int x,int y,int radius,int pigment) {
        if(fromX<0||fromX>=128||fromY<0||fromY>=128||x<0||x>=128||y<0||y>=128
                ||radius<1||radius>32||pigment<0||pigment>2) throw new IllegalArgumentException();
        double dx=x-fromX,dy=y-fromY,lengthSquared=dx*dx+dy*dy;
        for(int py=Math.max(0,Math.min(fromY,y)-radius);py<=Math.min(127,Math.max(fromY,y)+radius);py++)
            for(int px=Math.max(0,Math.min(fromX,x)-radius);px<=Math.min(127,Math.max(fromX,x)+radius);px++) {
                double t=lengthSquared==0?0:Math.max(0,Math.min(1,((px-fromX)*dx+(py-fromY)*dy)/lengthSquared));
                double ox=px-fromX-t*dx,oy=py-fromY-t*dy;
                if(ox*ox+oy*oy>=radius*radius) continue;
                int at=(py*128+px)*3+pigment;
                applied[at]=needed[at];
            }
    }
    int rgb(int x,int y) {
        int at=(y*128+x)*3, need=0, have=0;
        for(int c=0;c<3;c++) {need+=needed[at+c]&255;have+=applied[at+c]&255;}
        double completion=need==0?1:(double)have/need;
        int paper=246+Math.floorMod(x*37+y*17,7);
        int base=(int)Math.round(paper+(255-paper)*completion);
        int r=Math.max(0,base-(applied[at]&255)), g=Math.max(0,base-(applied[at+1]&255)), b=Math.max(0,base-(applied[at+2]&255));
        return (r<<16)|(g<<8)|b;
    }
    double progress() {
        long need=0,have=0;
        for(int i=0;i<needed.length;i++) {need+=needed[i]&255;have+=applied[i]&255;}
        return need==0?1:(double)have/need;
    }
    void write(OutputStream stream) throws IOException {
        var out=new DataOutputStream(stream);out.writeInt(0x434d5931);out.write(needed);out.write(applied);
    }
    static PigmentCanvas read(InputStream stream) throws IOException {
        var in=new DataInputStream(stream);
        if(in.readInt()!=0x434d5931) throw new IOException("Unknown canvas format");
        var result=new PigmentCanvas(new byte[PIXELS*4]);
        in.readFully(result.needed);in.readFully(result.applied);
        if(in.read()!=-1) throw new IOException("Extra canvas data");
        for(int i=0;i<result.needed.length;i++)
            if((result.applied[i]&255)>(result.needed[i]&255)) throw new IOException("Invalid pigment progress");
        return result;
    }
}
