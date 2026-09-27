package io.github.kernelperson.postcards;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.Plugin;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Owns only maps created by this plugin. Persisted palette bytes restore collected maps after restart. */
final class SavedMaps {
    private final Plugin plugin;
    private final Path folder;
    private final Map<Integer, Pixels> images = new HashMap<>();
    SavedMaps(Plugin plugin) { this.plugin=plugin; folder=plugin.getDataFolder().toPath().resolve("maps"); }
    void restore() throws IOException {
        Files.createDirectories(folder);
        try (var paths = Files.newDirectoryStream(folder, "*.map")) {
            for (Path path : paths) {
                try {
                    int id = Integer.parseInt(path.getFileName().toString().replace(".map",""));
                    MapView view = plugin.getServer().getMap(id);
                    if (view != null) attach(view,PaletteFile.read(path));
                } catch (IOException | IllegalArgumentException failure) {
                    plugin.getLogger().warning("Skipped an invalid saved map: " + path.getFileName());
                }
            }
        }
    }
    ItemStack create(World world, byte[] palette, String name) throws IOException {
        MapView view = plugin.getServer().createMap(world);
        PaletteFile.write(folder.resolve(view.getId()+".map"),palette);
        attach(view,palette);
        return item(view.getId(),name);
    }
    ItemStack item(int id, String name) {
        MapView view = plugin.getServer().getMap(id);
        if (view == null) throw new IllegalStateException("Saved map ID unavailable");
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta)item.getItemMeta();
        meta.setMapView(view); meta.setDisplayName(name); item.setItemMeta(meta); return item;
    }
    int id(ItemStack item) { return ((MapMeta)item.getItemMeta()).getMapId(); }
    void update(int id, byte[] palette) {
        Pixels image=images.get(id);
        if (image == null) {
            MapView view = plugin.getServer().getMap(id);
            if (view == null) throw new IllegalStateException("Saved map ID unavailable");
            attach(view,palette);
        } else image.update(palette);
    }
    void save(int id) throws IOException {
        Pixels image=images.get(id);
        if (image == null) throw new IOException("Map not owned by this plugin");
        PaletteFile.write(folder.resolve(id+".map"),image.bytes);
    }
    private void attach(MapView view, byte[] palette) {
        for (MapRenderer renderer : view.getRenderers()) view.removeRenderer(renderer);
        view.setLocked(true);
        Pixels renderer = new Pixels(palette); images.put(view.getId(),renderer); view.addRenderer(renderer);
    }
    private static final class Pixels extends MapRenderer {
        byte[] bytes; boolean dirty=true;
        Pixels(byte[] bytes) { super(false); update(bytes); }
        void update(byte[] value) {
            if (value.length!=16384) throw new IllegalArgumentException("Map dimensions");
            bytes=value.clone(); dirty=true;
        }
        @Override public void render(MapView view, MapCanvas canvas, Player viewer) {
            if (!dirty) return;
            for (int y=0;y<128;y++) for(int x=0;x<128;x++) canvas.setPixel(x,y,bytes[y*128+x]);
            dirty=false;
        }
    }
}
