package io.github.kernelperson.pov;

import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateMapTest {
    @Test void contextualDeliveryNeverLeaksToCopiedMapsAndClearsAfterAccessLoss() {
        UUID owner=UUID.randomUUID();Player admin=mock(Player.class),stranger=mock(Player.class);
        when(admin.getUniqueId()).thenReturn(owner);when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
        boolean[] authorized={true};byte[] pixels=new byte[16384];Arrays.fill(pixels,(byte)42);
        var renderer=new PrivateMap(owner,p->authorized[0],()->new FeedLoop.Frame(pixels,0),()->100L);
        assertTrue(renderer.isContextual());
        MapCanvas canvas=mock(MapCanvas.class);MapView view=mock(MapView.class);
        renderer.render(view,canvas,admin);verify(canvas).setPixel(60,60,(byte)42);
        clearInvocations(canvas);renderer.render(view,canvas,stranger);verify(canvas).setPixel(60,60,(byte)119);
        clearInvocations(canvas);authorized[0]=false;renderer.render(view,canvas,admin);verify(canvas).setPixel(60,60,(byte)119);
    }
    @Test void noFrameAndStaleFrameHaveDistinctSafeStatus() {
        UUID owner=UUID.randomUUID();Player admin=mock(Player.class);when(admin.getUniqueId()).thenReturn(owner);
        MapCanvas canvas=mock(MapCanvas.class);MapView view=mock(MapView.class);
        var renderer=new PrivateMap(owner,p->true,()->new FeedLoop.Frame(new byte[16384],0),()->4_000L);
        renderer.render(view,canvas,admin);
        verify(canvas).drawText(eq(4),eq(4),eq(MinecraftFont.Font),eq("STALE"));
        clearInvocations(canvas);
        new PrivateMap(owner,p->true,()->null,()->4_000L).render(view,canvas,admin);
        verify(canvas).drawText(eq(4),eq(4),eq(MinecraftFont.Font),eq("NO FEED"));
    }
}
