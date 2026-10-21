package io.github.kernelperson.camera;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.map.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LegacyMapsTest {
    @Test void restoresExistingIdentityWithoutAllocatingOrRewriting(@TempDir Path folder)throws Exception {
        var plugin=mock(Plugin.class);var server=mock(Server.class);var view=mock(MapView.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());when(plugin.getServer()).thenReturn(server);
        when(server.getMap(73)).thenReturn(view);when(view.getId()).thenReturn(73);when(view.getRenderers()).thenReturn(List.of());
        Path path=folder.resolve("maps/73.map");byte[] pixels=new byte[16384];pixels[500]=42;PaletteFile.write(path,pixels);
        byte[] original=Files.readAllBytes(path);assertEquals(16388,original.length);
        new SavedMaps(plugin).restore();assertArrayEquals(original,Files.readAllBytes(path));verify(server,never()).createMap(any());
        var added=org.mockito.ArgumentCaptor.forClass(MapRenderer.class);verify(view).addRenderer(added.capture());
        var canvas=mock(MapCanvas.class);added.getValue().render(view,canvas,mock(org.bukkit.entity.Player.class));
        verify(canvas).setPixel(116,3,(byte)42);assertTrue(Files.exists(path));
    }
}
