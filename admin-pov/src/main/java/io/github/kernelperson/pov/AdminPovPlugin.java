package io.github.kernelperson.pov;

import java.io.IOException;
import java.util.*;
import ke.ric.renderer.api.RendererService;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/** Public API example: rendered server-world snapshots, never a player's client screen. */
public final class AdminPovPlugin extends JavaPlugin implements Listener {
    private final Map<UUID,BindingStore.Binding> bindings=new LinkedHashMap<>();
    private final Map<UUID,MapView> views=new HashMap<>();
    private final FeedLoop<UUID> loop=new FeedLoop<>();
    private BindingStore store;
    private PovCapture capture;
    private PovMonitors monitors;
    private FeedLoop.Ticket<UUID> active;
    private boolean closed;
    static long now(){return System.nanoTime()/1_000_000;}
    @Override public void onEnable() {
        saveDefaultConfig();store=new BindingStore(getDataFolder().toPath().resolve("bindings.yml"));capture=new PovCapture(this);
        try {
            for(var binding:store.load()) {
                bindings.put(binding.owner(),binding);
                var view=getServer().getMap(binding.mapId());if(view!=null)attach(binding.owner(),view);
            }
        }catch(IOException failure){getLogger().severe("Invalid POV receiver bindings; refusing to expose saved receivers.");getServer().getPluginManager().disablePlugin(this);return;}
        monitors=new PovMonitors(this,getConfig().getBoolean("native-models",false));
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(monitors,this);monitors.restore();
        getServer().getScheduler().runTaskTimer(this,this::tick,1,5);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)){sender.sendMessage("Use /pov in game.");return true;}
        if(!player.hasPermission(PovAccess.PERMISSION))return true;
        if(args.length!=1){player.sendMessage("/pov <player> | /pov stop | /pov monitor");return true;}
        UUID owner=player.getUniqueId();
        if(args[0].equalsIgnoreCase("stop")) {
            var old=bindings.get(owner);if(old!=null&&!change(new BindingStore.Binding(owner,old.mapId(),null),player))return true;
            tick();player.sendMessage("POV stopped. Your receivers are blank.");return true;
        }
        if(args[0].equalsIgnoreCase("monitor")) {
            if(!authorized(player)){player.sendMessage("First select a visible online player using /pov <player>.");return true;}
            if(player.getInventory().firstEmpty()<0){player.sendMessage("Make inventory space first.");return true;}
            player.getInventory().addItem(monitors.item(owner));
            player.sendMessage("Place your monitor; sneak + right-click its screen to remove it. Only you can watch it.");return true;
        }
        Player target=getServer().getPlayerExact(args[0]);
        if(!PovAccess.allowed(owner,player,target)){player.sendMessage("That player is not available to you.");return true;}
        Set<UUID> selected=new HashSet<>();
        for(var binding:bindings.values())if(!binding.owner().equals(owner)) {
            var other=getServer().getPlayer(binding.owner());
            if(other!=null&&authorized(other)&&watching(other))selected.add(binding.target());
        }
        if(!selected.contains(target.getUniqueId())&&selected.size()>=2){player.sendMessage("Two target feeds are active. Stop one before selecting another target.");return true;}
        var old=bindings.get(owner);
        if(old==null&&bindings.size()>=128){player.sendMessage("Example receiver-owner limit reached (128).");return true;}
        if((old==null||!hasReceiver(player,old.mapId()))&&player.getInventory().firstEmpty()<0){player.sendMessage("Make space for your reusable POV map first.");return true;}
        MapView view=old==null?Bukkit.createMap(player.getWorld()):views.get(owner);
        if(view==null){player.sendMessage("Saved receiver map is unavailable; ask an operator to restore map data.");return true;}
        if(!change(new BindingStore.Binding(owner,view.getId(),target.getUniqueId()),player))return true;
        attach(owner,view);
        if(!hasReceiver(player,view.getId()))player.getInventory().addItem(receiver(owner));
        tick();player.sendMessage("POV selected: "+target.getName()+". Hold the map or watch your monitor. Periodic world snapshots; no HUD, chat or audio.");return true;
    }
    private boolean change(BindingStore.Binding replacement,Player player) {
        var old=bindings.put(replacement.owner(),replacement);
        try {store.save(bindings.values());return true;}
        catch(IOException failure){if(old==null)bindings.remove(replacement.owner());else bindings.put(old.owner(),old);
            player.sendMessage("Could not save POV binding; selection unchanged.");return false;}
    }
    private void attach(UUID owner,MapView view) {
        for(var renderer:List.copyOf(view.getRenderers()))view.removeRenderer(renderer);
        view.setTrackingPosition(false);view.setUnlimitedTracking(false);view.setLocked(true);
        view.addRenderer(new PrivateMap(owner,p->!closed&&authorized(p)&&watching(p),()->{
            var binding=bindings.get(owner);return binding==null||binding.target()==null?null:loop.frame(binding.target());
        },AdminPovPlugin::now));views.put(owner,view);
    }
    ItemStack receiver(UUID owner) {
        MapView view=views.get(owner);if(view==null)throw new IllegalStateException("Missing owned receiver");
        var item=new ItemStack(Material.FILLED_MAP);var meta=(MapMeta)item.getItemMeta();
        meta.setMapView(view);meta.setDisplayName("Private POV Receiver");item.setItemMeta(meta);return item;
    }
    boolean authorized(Player owner) {
        var binding=bindings.get(owner.getUniqueId());
        return binding!=null&&binding.target()!=null&&PovAccess.allowed(binding.owner(),owner,getServer().getPlayer(binding.target()));
    }
    private static int mapId(ItemStack item) {
        if(item==null||item.getType()!=Material.FILLED_MAP||!(item.getItemMeta() instanceof MapMeta meta)||!meta.hasMapView())return -1;
        return meta.getMapView().getId();
    }
    private boolean hasReceiver(Player player,int id){for(var item:player.getInventory().getContents())if(mapId(item)==id)return true;return false;}
    private boolean watching(Player player) {
        var binding=bindings.get(player.getUniqueId());if(binding==null)return false;
        return mapId(player.getInventory().getItemInMainHand())==binding.mapId()
                ||mapId(player.getInventory().getItemInOffHand())==binding.mapId()
                ||(monitors!=null&&monitors.watching(player));
    }
    private boolean demanded(UUID target) {
        for(var binding:bindings.values())if(target.equals(binding.target())) {
            Player owner=getServer().getPlayer(binding.owner());
            if(owner!=null&&authorized(owner)&&watching(owner))return true;
        }
        return false;
    }
    private void tick() {
        if(closed)return;
        Set<UUID> demand=new LinkedHashSet<>();
        for(var binding:bindings.values()) {
            Player owner=getServer().getPlayer(binding.owner());
            if(owner!=null&&authorized(owner)&&watching(owner))demand.add(binding.target());
        }
        loop.demand(demand);
        if(active!=null&&!loop.current(active)){capture.cancel();active=null;}
        if(active==null) {
            var next=loop.begin(now());
            if(next!=null) {
                Player target=getServer().getPlayer(next.target());
                if(target==null||!target.isOnline()){loop.failed(next,now());}
                else {
                    active=next;UUID world=target.getWorld().getUID();
                    capture.start(target.getEyeLocation(),()->loop.current(next)&&demanded(next.target())
                            &&target.isOnline()&&world.equals(target.getWorld().getUID()),pixels->{
                        if(loop.complete(next,now(),pixels)){active=null;refreshOwners();}
                    },()->{loop.failed(next,now());if(Objects.equals(active,next))active=null;});
                }
            }
        }
        refreshOwners(); // permission loss and stale status also clear/update handheld client caches
    }
    private void refreshOwners() {
        for(var entry:views.entrySet()){Player owner=getServer().getPlayer(entry.getKey());if(owner!=null&&owner.isOnline())owner.sendMap(entry.getValue());}
    }
    private void invalidate(UUID target) {
        loop.invalidate(target);if(active!=null&&!loop.current(active)){capture.cancel();active=null;}refreshOwners();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){invalidate(event.getPlayer().getUniqueId());}
    @EventHandler public void worldChanged(PlayerChangedWorldEvent event){invalidate(event.getPlayer().getUniqueId());}
    @EventHandler public void logout(PlayerQuitEvent event){invalidate(event.getPlayer().getUniqueId());}
    @EventHandler public void providerRemoved(ServiceUnregisterEvent event) {
        if(event.getProvider().getService()!=RendererService.class)return;
        loop.clear();active=null;capture.cancel();refreshOwners();
    }
    @Override public void onDisable() {
        closed=true;loop.clear();active=null;if(capture!=null)capture.close();
        refreshOwners();if(monitors!=null)monitors.close();
    }
}
