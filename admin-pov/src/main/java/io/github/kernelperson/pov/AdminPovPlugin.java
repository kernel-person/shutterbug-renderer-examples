package io.github.kernelperson.pov;

import java.io.IOException;
import java.util.*;
import ke.ric.renderer.api.*;
import java.time.Duration;
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
    private Renderer renderer;
    private final Map<UUID,TargetFeed> feeds=new HashMap<>();
    private static final RenderSettings SETTINGS=new RenderSettings(1,RenderSettings.Profile.CLASSIC,
            128,128,Set.of(),RenderSettings.DEFAULT_MEMORY_LIMIT,1,Duration.ofSeconds(30));
    private static final class TargetFeed {
        final UUID world;RenderStream stream;
        TargetFeed(UUID world){this.world=world;}
    }
    private PovMonitors monitors;
    private boolean closed;
    static long now(){return System.nanoTime()/1_000_000;}
    @Override public void onEnable() {
        saveDefaultConfig();store=new BindingStore(getDataFolder().toPath().resolve("bindings.yml"));renderer=Renderer.forPlugin(this);
        try {
            for(var binding:store.load()) {
                bindings.put(binding.owner(),binding);
                var view=getServer().getMap(binding.mapId());if(view!=null)attach(binding.owner(),view);
            }
        }catch(IOException failure){getLogger().severe("Invalid POV receiver bindings; refusing to expose saved receivers.");getServer().getPluginManager().disablePlugin(this);return;}
        monitors=new PovMonitors(this,getConfig().getBoolean("native-models",false));
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(monitors,this);monitors.restore();
        getServer().getScheduler().runTaskTimer(this,this::tick,1,1);
        getServer().getScheduler().runTaskTimer(this,this::refreshOwners,1,5);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)){sender.sendMessage("Use /pov in game.");return true;}
        UUID owner=player.getUniqueId();
        if(!player.hasPermission(PovAccess.PERMISSION))return true;
        if(args.length<1||args.length>2||(args.length==2&&!args[0].equalsIgnoreCase("demo"))) {
            player.sendMessage("/pov <player> | /pov stop | /pov monitor | /pov demo [updates-per-second: 1-20]");return true;
        }
        Integer rate=null;
        if(args.length==2) {
            try {rate=Integer.parseInt(args[1]);}
            catch(NumberFormatException invalid){player.sendMessage("Use /pov demo [updates-per-second: 1-20], a whole number.");return true;}
            if(rate<1||rate>20){player.sendMessage("Use /pov demo [updates-per-second: 1-20], a whole number.");return true;}
        }
        if(args[0].equalsIgnoreCase("stop")) {
            var old=bindings.get(owner);if(old!=null&&!change(new BindingStore.Binding(owner,old.mapId(),null),player))return true;
            tick();refreshOwners();player.sendMessage("POV stopped. Your receivers are blank.");return true;
        }
        if(args[0].equalsIgnoreCase("monitor")) {
            if(!authorized(player)){player.sendMessage("First select a visible online player using /pov <player>.");return true;}
            if(player.getInventory().firstEmpty()<0){player.sendMessage("Make inventory space first.");return true;}
            player.getInventory().addItem(monitors.item(owner));
            player.sendMessage("PUBLIC monitor: anyone can watch or photograph it. Sneak + right-click your screen to remove it.");return true;
        }
        Player target=args[0].equalsIgnoreCase("demo")?player:getServer().getPlayerExact(args[0]);
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
        if(rate!=null && rate!=loop.updatesPerSecond()){loop.updatesPerSecond(rate);stopFeeds();}
        if(old==null)attach(owner,view);
        if(!hasReceiver(player,view.getId()))player.getInventory().addItem(receiver(owner));
        tick();refreshOwners();
        player.sendMessage("PUBLIC POV selected: "+target.getName()+". Anyone can see and photograph your receiver/monitor, including copies. Hold the map or watch your monitor. Periodic world snapshots; no HUD, chat or audio.");
        player.sendMessage("POV target: "+loop.updatesPerSecond()+" updates/second per target, shared by all POV feeds until restart. Best-effort; fresh world captures, no frame queue.");return true;
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
        view.addRenderer(new PovMap(()->{
            var selected=bindings.get(owner);return closed||selected==null||selected.target()==null?null:loop.frame(selected.target());
        },AdminPovPlugin::now));
        views.put(owner,view);
    }
    ItemStack receiver(UUID owner) {
        MapView view=views.get(owner);if(view==null)throw new IllegalStateException("Missing owned receiver");
        var item=new ItemStack(Material.FILLED_MAP);var meta=(MapMeta)item.getItemMeta();
        meta.setMapView(view);meta.setDisplayName("POV Receiver");item.setItemMeta(meta);return item;
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
        for(UUID key:List.copyOf(feeds.keySet()))if(!loop.targets().contains(key))stopFeed(key);
        for(UUID key:loop.targets()) {
            Player target=getServer().getPlayer(key);
            TargetFeed existing=feeds.get(key);
            if(existing!=null && (target==null || !target.isOnline() || !existing.world.equals(target.getWorld().getUID()))) {
                stopFeed(key);loop.invalidate(key);existing=null;
            }
            if(existing!=null && existing.stream!=null && !existing.stream.active()){retry(key,existing);continue;}
            if(existing!=null || !loop.ready(key,now()))continue;
            if(target==null || !target.isOnline()){loop.failed(key,now());continue;}
            TargetFeed feed=new TargetFeed(target.getWorld().getUID());feeds.put(key,feed);
            try {
                feed.stream=renderer.stream(()-> {
                    if(!current(key,feed,target) || !loaded(target.getEyeLocation()))return null;
                    return target.getEyeLocation();
                }).fresh().fps(loop.updatesPerSecond()).settings(SETTINGS).radius(16).chunksPerTick(2)
                  .excluding(key).mapOptions(MapOutputOptions.defaults().dither(MapDither.NONE))
                  .toMapSink(frame -> {
                      if(current(key,feed,target) && loop.complete(key,frame.worldCapturedNanos()/1_000_000,frame.palette()))refreshOwners();
                  }).onError(error -> retry(key,feed)).start();
                if(feeds.get(key)!=feed && feed.stream!=null)feed.stream.close();
            }catch(RuntimeException unavailable){retry(key,feed);}
        }
    }
    private boolean current(UUID key,TargetFeed feed,Player target) {
        return !closed && feeds.get(key)==feed && demanded(key) && target.isOnline()
                && feed.world.equals(target.getWorld().getUID());
    }
    static boolean loaded(Location eye) {
        if(eye.getWorld()==null)return false;
        for(int x=(eye.getBlockX()-16)>>4;x<=((eye.getBlockX()+16)>>4);x++)
            for(int z=(eye.getBlockZ()-16)>>4;z<=((eye.getBlockZ()+16)>>4);z++)
                if(!eye.getWorld().isChunkLoaded(x,z))return false;
        return true;
    }
    private void retry(UUID key,TargetFeed feed) {
        if(feeds.get(key)!=feed)return;
        stopFeed(key);loop.failed(key,now());
    }
    private void stopFeed(UUID key) {
        TargetFeed old=feeds.remove(key);if(old!=null && old.stream!=null)old.stream.close();
    }
    private void stopFeeds(){for(UUID key:List.copyOf(feeds.keySet()))stopFeed(key);}
    private void refreshOwners() {
        for(var entry:views.entrySet()){Player owner=getServer().getPlayer(entry.getKey());if(owner!=null&&owner.isOnline())owner.sendMap(entry.getValue());}
    }
    private void invalidate(UUID target) {
        stopFeed(target);loop.invalidate(target);refreshOwners();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){invalidate(event.getPlayer().getUniqueId());}
    @EventHandler public void worldChanged(PlayerChangedWorldEvent event){invalidate(event.getPlayer().getUniqueId());}
    @EventHandler public void logout(PlayerQuitEvent event){invalidate(event.getPlayer().getUniqueId());}
    @EventHandler public void providerRemoved(ServiceUnregisterEvent event) {
        if(event.getProvider().getService()!=RendererService.class)return;
        stopFeeds();loop.clear();refreshOwners();
    }
    @Override public void onDisable() {
        closed=true;stopFeeds();loop.clear();if(renderer!=null)renderer.close();
        refreshOwners();if(monitors!=null)monitors.close();
    }
}
