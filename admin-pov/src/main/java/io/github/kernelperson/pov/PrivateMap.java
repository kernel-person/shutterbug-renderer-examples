package io.github.kernelperson.pov;

import java.util.*;
import java.util.function.*;
import org.bukkit.entity.Player;
import org.bukkit.map.*;

/** Contextual canvas, never writes the base map (so no frame is saved in world map data). */
final class PrivateMap extends MapRenderer {
    private final UUID owner;
    private final Predicate<Player> authorized;
    private final Supplier<FeedLoop.Frame> frame;
    private final LongSupplier clock;
    private final Map<Player,Stamp> delivered=new WeakHashMap<>();
    private record Stamp(boolean allowed,FeedLoop.Frame frame,boolean stale) {}
    PrivateMap(UUID owner,Predicate<Player> authorized,Supplier<FeedLoop.Frame> frame,LongSupplier clock) {
        super(true);this.owner=owner;this.authorized=authorized;this.frame=frame;this.clock=clock;
    }
    @SuppressWarnings("deprecation")
    @Override public void render(MapView view,MapCanvas canvas,Player player) {
        boolean allowed=owner.equals(player.getUniqueId())&&authorized.test(player);
        FeedLoop.Frame current=allowed?frame.get():null;
        var stamp=new Stamp(allowed,current,current!=null&&current.stale(clock.getAsLong()));
        if(stamp.equals(delivered.get(player)))return;
        byte[] pixels=current==null?null:current.pixels();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)canvas.setPixel(x,y,pixels==null?(byte)119:pixels[y*128+x]);
        if(allowed&&(current==null||stamp.stale())) {
            for(int y=0;y<14;y++)for(int x=0;x<128;x++)canvas.setPixel(x,y,(byte)119);
            canvas.drawText(4,4,MinecraftFont.Font,current==null?"NO FEED":"STALE");
        }
        delivered.put(player,stamp);
    }
    void invalidate(){delivered.clear();}
}
