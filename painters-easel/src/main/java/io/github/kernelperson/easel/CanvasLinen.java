package io.github.kernelperson.easel;

/** Warm woven ground, deliberately separated shades after Minecraft map quantization. */
final class CanvasLinen {
    static int rgb(int x,int y) {
        int shade=(x%8==0&&y%6<4)?220:(y%6==0?248:238);
        int variation=((x/16+y/16)%3)-1;
        int r=shade+variation,g=shade-3+variation,b=shade-11+variation;
        return (r<<16)|(g<<8)|b;
    }
}
