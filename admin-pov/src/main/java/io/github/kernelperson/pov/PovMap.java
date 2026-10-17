package io.github.kernelperson.pov;

import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.map.*;

/** Shared public canvas for every receiver and desk screen. Reads completed frames only. */
final class PovMap extends MapRenderer {
    private final Supplier<FeedLoop.Frame> frame;
    private final LongSupplier clock;
    private Stamp delivered;
    private record Stamp(FeedLoop.Frame frame,boolean stale) {}
    PovMap(Supplier<FeedLoop.Frame> frame,LongSupplier clock) {
        super(false);this.frame=frame;this.clock=clock;
    }
    @SuppressWarnings("deprecation")
    @Override public void render(MapView view,MapCanvas canvas,Player viewer) {
        FeedLoop.Frame current=frame.get();
        var stamp=new Stamp(current,current!=null&&current.stale(clock.getAsLong()));
        if(stamp.equals(delivered))return;
        byte[] pixels=current==null?null:current.pixels();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)canvas.setPixel(x,y,pixels==null?(byte)119:pixels[y*128+x]);
        if(current==null||stamp.stale()) {
            for(int y=0;y<14;y++)for(int x=0;x<128;x++)canvas.setPixel(x,y,(byte)119);
            canvas.drawText(4,4,MinecraftFont.Font,current==null?"NO FEED":"STALE");
        }
        delivered=stamp;
    }
}
