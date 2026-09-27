package io.github.kernelperson.postcards;
import ke.ric.renderer.api.MapDither;
import ke.ric.renderer.api.MinecraftMapConverter;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
public final class PostcardsPlugin extends JavaPlugin implements Listener {
    private RenderSession renders;
    private SavedMaps maps;
    @Override public void onEnable() {
        maps=new SavedMaps(this);
        try { maps.restore(); }
        catch(IOException failure) { getLogger().severe("Cannot read saved maps; disabling."); getServer().getPluginManager().disablePlugin(this); return; }
        renders=new RenderSession(this);
        getServer().getPluginManager().registerEvents(renders,this);
        getServer().getPluginManager().registerEvents(this,this);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Run /postcard in game."); return true; }
        if (!player.hasPermission("rendererexamples.postcards")) return true;
        if (player.getInventory().firstEmpty()<0) { player.sendMessage("Make space for a map first."); return true; }
        boolean started=renders.request(player.getUniqueId().toString(),player.getEyeLocation(),
            () -> player.isOnline() && player.getInventory().firstEmpty()>=0,
            frame -> {
                try {
                    ItemStack item=maps.create(player.getWorld(),MinecraftMapConverter.quantize(frame,MapDither.BAYER),"Postcard");
                    if (give(player,item)) player.sendMessage("Your postcard is ready!");
                } catch(IOException failure) { player.sendMessage("Could not save the postcard; check server storage."); }
            }, message -> { if(player.isOnline()) player.sendMessage(message); });
        if(started) player.sendMessage("Taking your postcard...");
        return true;
    }
    static boolean give(Player player, ItemStack map) {
        if (!player.isOnline()) return false;
        int slot=player.getInventory().firstEmpty();
        if(slot<0) return false;
        player.getInventory().setItem(slot,map); return true;
    }
    @EventHandler public void quit(PlayerQuitEvent event) { renders.invalidate(event.getPlayer().getUniqueId().toString()); }
    @Override public void onDisable() { if(renders!=null) renders.close(); }
}
