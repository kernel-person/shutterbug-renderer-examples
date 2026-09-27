package io.github.kernelperson.easel;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.map.MapPalette;
import java.awt.Color;
import java.io.IOException;
import java.util.*;

/** Dummy model: fence, backboard, framed map. The renderer is used once per new painting. */
public final class PaintersEaselPlugin extends JavaPlugin implements Listener {
    private NamespacedKey kind,ownerTag,baseTag;
    private RenderSession renders;
    private SavedMaps maps;
    private CanvasStore store;
    private final Map<UUID,Easel> easels=new HashMap<>();
    private static final class Easel {
        final ItemFrame frame; final UUID owner; final Block base;
        PigmentCanvas canvas; byte[] palette; boolean capturing,dirty,blocked; long lastStroke;
        Easel(ItemFrame frame,UUID owner,Block base) {this.frame=frame;this.owner=owner;this.base=base;}
    }
    @Override public void onEnable() {
        kind=new NamespacedKey(this,"kind");ownerTag=new NamespacedKey(this,"owner");baseTag=new NamespacedKey(this,"base");
        maps=new SavedMaps(this);store=new CanvasStore(getDataFolder().toPath().resolve("canvases"));
        renders=new RenderSession(this);
        try {maps.restore();}
        catch(IOException failure) {getLogger().severe("Cannot restore saved maps.");getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(renders,this);
        for(World world:getServer().getWorlds()) for(Chunk chunk:world.getLoadedChunks()) load(chunk);
        getServer().getScheduler().runTaskTimer(this,()->easels.values().forEach(this::save),100,100);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)) {sender.sendMessage("Use /easel or /easel remove in game.");return true;}
        if(!player.hasPermission("rendererexamples.easel")) return true;
        if(args.length==1&&args[0].equalsIgnoreCase("remove")) {
            var hit=player.getWorld().rayTraceEntities(player.getEyeLocation(),player.getEyeLocation().getDirection(),5,
                    entity->easels.containsKey(entity.getUniqueId()));
            Easel easel=hit==null?null:easels.get(hit.getHitEntity().getUniqueId());
            if(easel==null||!easel.owner.equals(player.getUniqueId())) {player.sendMessage("Look at your own easel within five blocks.");return true;}
            if(easel.blocked) {player.sendMessage("This easel is protected but unavailable. Ask an administrator to restore its canvas backup, or unload excess easels and reload the chunk.");return true;}
            if(easel.canvas!=null||easel.capturing) {player.sendMessage("Collect your painting first (sneak + right-click with the brush).");return true;}
            remove(easel);player.sendMessage("Easel removed.");return true;
        }
        if(player.getInventory().firstEmpty()<0) {player.sendMessage("Make two inventory spaces first.");return true;}
        ItemStack easel=item(Material.OAK_FENCE,"easel","Painter's Easel");
        ItemStack brush=item(Material.BRUSH,"brush","Painter's Brush");
        // Check both slots before modifying the inventory.
        int free=0;for(ItemStack slot:player.getInventory().getStorageContents()) if(slot==null||slot.getType().isAir()) free++;
        if(free<2) {player.sendMessage("Make two inventory spaces first.");return true;}
        player.getInventory().addItem(easel,brush);
        player.sendMessage("Place the easel. Brush + right-click starts a canvas; offhand CMY dye + punch paints; sneak + right-click collects.");
        return true;
    }
    private ItemStack item(Material material,String type,String name) {
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(kind,PersistentDataType.STRING,type);item.setItemMeta(meta);return item;
    }
    private boolean tagged(ItemStack item,String type) {
        return item!=null&&item.hasItemMeta()&&type.equals(item.getItemMeta().getPersistentDataContainer().get(kind,PersistentDataType.STRING));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) {
        if(event instanceof BlockMultiPlaceEvent||!tagged(event.getItemInHand(),"easel")) return;
        Block base=event.getBlockPlaced(),board=base.getRelative(BlockFace.UP);
        BlockFace front=event.getPlayer().getFacing().getOppositeFace();
        if(base.getType()!=Material.OAK_FENCE||!board.getType().isAir()
                ||!board.getRelative(front).getType().isAir()||easels.size()>=128) {
            event.setCancelled(true);event.getPlayer().sendMessage("Need clear space above and in front of the easel (128 loaded easels maximum).");return;
        }
        var placedData=base.getBlockData();
        // Wait for every listener on the original placement, including later HIGHEST listeners.
        getServer().getScheduler().runTask(this,()->{
            if(event.isCancelled()||!event.canBuild()
                    ||!base.getWorld().isChunkLoaded(base.getX()>>4,base.getZ()>>4)
                    ||base.getType()!=Material.OAK_FENCE||!Objects.equals(base.getBlockData(),placedData)) return;
            if(!board.getType().isAir()||!board.getRelative(front).getType().isAir()||easels.size()>=128) {
                rollbackBase(event);return;
            }
            commitPlacement(event,base,board,front);
        });
    }
    private void commitPlacement(BlockPlaceEvent event,Block base,Block board,BlockFace front) {
        BlockState original=board.getState();
        board.setType(Material.OAK_PLANKS,false);
        var protection=new BlockMultiPlaceEvent(List.of(event.getBlockReplacedState(),original),
                event.getBlockAgainst(),event.getItemInHand(),event.getPlayer(),event.canBuild());
        getServer().getPluginManager().callEvent(protection);
        if(protection.isCancelled()||!protection.canBuild()) {original.update(true,false);rollbackBase(event);return;}
        ItemFrame frame=null;
        try {
            frame=base.getWorld().spawn(board.getRelative(front).getLocation().add(.5,.5,.5),ItemFrame.class);
            frame.setFacingDirection(front,true);frame.setItemDropChance(0);
            var hanging=new HangingPlaceEvent(frame,event.getPlayer(),board,front,event.getHand(),event.getItemInHand());
            getServer().getPluginManager().callEvent(hanging);
            if(hanging.isCancelled()) {frame.remove();original.update(true,false);rollbackBase(event);return;}
            frame.getPersistentDataContainer().set(ownerTag,PersistentDataType.STRING,event.getPlayer().getUniqueId().toString());
            frame.getPersistentDataContainer().set(baseTag,PersistentDataType.INTEGER_ARRAY,new int[]{base.getX(),base.getY(),base.getZ()});
            Easel easel=new Easel(frame,event.getPlayer().getUniqueId(),base);
            easels.put(frame.getUniqueId(),easel);
            frame.setItem(maps.create(base.getWorld(),blank(),"Blank Canvas"),false);
        } catch(RuntimeException|IOException failure) {
            if(frame!=null) {easels.remove(frame.getUniqueId());frame.remove();}
            original.update(true,false);rollbackBase(event);event.getPlayer().sendMessage("Could not place the easel.");
        }
    }
    private void rollbackBase(BlockPlaceEvent event) {
        // Original event already finished. Restore the placed fence and refund one item ourselves.
        if(event.getBlockPlaced().getType()!=Material.OAK_FENCE) return;
        event.getBlockReplacedState().update(true,false);
        Player player=event.getPlayer();
        if(player.getGameMode()==GameMode.CREATIVE) return;
        ItemStack refund=event.getItemInHand().clone();refund.setAmount(1);
        for(ItemStack leftover:player.getInventory().addItem(refund).values())
            event.getBlockPlaced().getWorld().dropItemNaturally(event.getBlockPlaced().getLocation(),leftover);
    }
    private void load(Chunk chunk) {
        for(Entity entity:chunk.getEntities()) if(entity instanceof ItemFrame frame) {
            String owner=frame.getPersistentDataContainer().get(ownerTag,PersistentDataType.STRING);
            int[] base=frame.getPersistentDataContainer().get(baseTag,PersistentDataType.INTEGER_ARRAY);
            if(owner==null||base==null||base.length!=3||easels.containsKey(frame.getUniqueId())) continue;
            Easel easel;
            try {
                easel=new Easel(frame,UUID.fromString(owner),frame.getWorld().getBlockAt(base[0],base[1],base[2]));
            } catch(IllegalArgumentException invalidOwner) {continue;}
            // Ownership survives bad files and the active-work cap. Never expose a saved map to theft.
            easel.blocked=easels.size()>=128;
            easels.put(frame.getUniqueId(),easel);
            if(easel.blocked) continue;
            try {
                easel.canvas=store.load(frame.getUniqueId());
                if(easel.canvas!=null) {easel.palette=palette(easel.canvas);maps.update(maps.id(frame.getItem()),easel.palette);}
            } catch(IOException|RuntimeException failure) {
                easel.blocked=true;easel.canvas=null;easel.palette=null;
                getLogger().warning("Could not load canvas "+frame.getUniqueId()+"; easel stays protected. Restore its backup and reload the chunk.");
            }
        }
    }
    @EventHandler public void loaded(ChunkLoadEvent event) {load(event.getChunk());}
    @EventHandler public void unloaded(ChunkUnloadEvent event) {
        for(Easel easel:List.copyOf(easels.values()))
            if(easel.frame.getWorld().equals(event.getWorld())&&easel.frame.getLocation().getBlockX()>>4==event.getChunk().getX()
                    &&easel.frame.getLocation().getBlockZ()>>4==event.getChunk().getZ()) {
                save(easel);renders.invalidate(easel.frame.getUniqueId().toString());easels.remove(easel.frame.getUniqueId());
            }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEntityEvent event) {
        Easel easel=easels.get(event.getRightClicked().getUniqueId());
        if(easel==null) return;event.setCancelled(true);
        Player player=event.getPlayer();
        if(easel.blocked) {player.sendMessage("This easel is unavailable but protected. Ask an administrator to restore its canvas backup or unload excess easels and reload the chunk.");return;}
        if(event.getHand()!=EquipmentSlot.HAND||!easel.owner.equals(player.getUniqueId())
                ||!tagged(player.getInventory().getItemInMainHand(),"brush")) return;
        if(player.isSneaking()) collect(easel,player);
        else if(easel.canvas==null&&!easel.capturing) start(easel,player);
        else player.sendMessage("Paint with the brush and cyan, magenta or yellow dye in your offhand.");
    }
    private void start(Easel easel,Player player) {
        if(easel.frame.getItem().getType()!=Material.FILLED_MAP) {
            try {easel.frame.setItem(maps.create(easel.base.getWorld(),blank(),"Blank Canvas"),false);}
            catch(IOException failure) {player.sendMessage("Cannot save a new canvas.");return;}
        }
        Location view=easel.frame.getLocation().subtract(easel.frame.getFacing().getDirection().multiply(1.25));
        view.setDirection(easel.frame.getFacing().getOppositeFace().getDirection());
        easel.capturing=true;
        boolean accepted=renders.request(easel.frame.getUniqueId().toString(),view,()->valid(easel),frame->{
            easel.capturing=false;easel.canvas=new PigmentCanvas(frame.rgba8());easel.palette=palette(easel.canvas);
            easel.dirty=true;maps.update(maps.id(easel.frame.getItem()),easel.palette);save(easel);
            if(player.isOnline()) player.sendMessage("Reference captured! Paint the canvas with CMY dyes.");
        },message->{easel.capturing=false;if(player.isOnline()) player.sendMessage(message);});
        if(!accepted) easel.capturing=false;
        else player.sendMessage("Capturing the view beyond the easel...");
    }
    private boolean valid(Easel easel) {
        return easels.get(easel.frame.getUniqueId())==easel&&easel.frame.isValid()
                &&easel.frame.getWorld().isChunkLoaded(easel.frame.getLocation().getBlockX()>>4,easel.frame.getLocation().getBlockZ()>>4);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event) {
        Easel easel=easels.get(event.getEntity().getUniqueId());if(easel==null) return;
        event.setCancelled(true);
        if(event instanceof EntityDamageByEntityEvent hit&&hit.getDamager() instanceof Player player) paint(easel,player);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hanging(HangingBreakEvent event) {
        Easel easel=easels.get(event.getEntity().getUniqueId());if(easel==null) return;
        event.setCancelled(true); // includes creative-mode destruction and environmental damage
        if(event instanceof HangingBreakByEntityEvent hit&&hit.getRemover() instanceof Player player) paint(easel,player);
    }
    private void paint(Easel easel,Player player) {
        if(easel.blocked||!easel.owner.equals(player.getUniqueId())||easel.canvas==null
                ||!tagged(player.getInventory().getItemInMainHand(),"brush")) return;
        int pigment=switch(player.getInventory().getItemInOffHand().getType()) {
            case CYAN_DYE -> 0;case MAGENTA_DYE -> 1;case YELLOW_DYE -> 2;default -> -1;
        };
        if(pigment<0) {player.sendMessage("Put cyan, magenta or yellow dye in your offhand.");return;}
        long now=System.nanoTime();if(now-easel.lastStroke<100_000_000L) return;easel.lastStroke=now;
        var eye=player.getEyeLocation();
        var hit=CanvasHit.intersect(eye.toVector(),eye.getDirection(),easel.frame.getLocation().toVector(),easel.frame.getFacing().getDirection());
        if(hit==null) return;
        easel.canvas.stroke(hit.x(),hit.y(),8,pigment);
        for(int y=Math.max(0,hit.y()-8);y<=Math.min(127,hit.y()+8);y++)
            for(int x=Math.max(0,hit.x()-8);x<=Math.min(127,hit.x()+8);x++)
                easel.palette[y*128+x]=color(easel.canvas.rgb(x,y));
        maps.update(maps.id(easel.frame.getItem()),easel.palette);easel.dirty=true;
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(String.format(Locale.ROOT,"Painting: %.1f%%",easel.canvas.progress()*100)));
    }
    private void collect(Easel easel,Player player) {
        if(easel.canvas==null||easel.capturing) {player.sendMessage("No painting ready to collect.");return;}
        int slot=player.getInventory().firstEmpty();if(slot<0) {player.sendMessage("Make space for the painting.");return;}
        try {
            int id=maps.id(easel.frame.getItem());maps.save(id);store.remove(easel.frame.getUniqueId());
            ItemStack painting=maps.item(id,"Painting");easel.frame.setItem(new ItemStack(Material.AIR),false);
            easel.canvas=null;easel.palette=null;easel.dirty=false;
            player.getInventory().setItem(slot,painting);player.sendMessage("Painting collected.");
        } catch(IOException failure) {player.sendMessage("Could not save the painting; nothing was collected.");}
    }
    private void save(Easel easel) {
        if(!easel.dirty||easel.canvas==null) return;
        try {store.save(easel.frame.getUniqueId(),easel.canvas);maps.save(maps.id(easel.frame.getItem()));easel.dirty=false;}
        catch(IOException|RuntimeException failure) {getLogger().warning("Could not save an easel; check disk space.");}
    }
    private void remove(Easel easel) {
        renders.invalidate(easel.frame.getUniqueId().toString());easels.remove(easel.frame.getUniqueId());easel.frame.remove();
        if(easel.base.getType()==Material.OAK_FENCE) easel.base.setType(Material.AIR);
        Block board=easel.base.getRelative(BlockFace.UP);if(board.getType()==Material.OAK_PLANKS) board.setType(Material.AIR);
    }
    private boolean protectedBlock(Block block) {
        return easels.values().stream().anyMatch(e->e.base.equals(block)||e.base.getRelative(BlockFace.UP).equals(block));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void broken(BlockBreakEvent event) {
        if(protectedBlock(event.getBlock())) {event.setCancelled(true);event.getPlayer().sendMessage("Collect the painting, then use /easel remove while looking at the canvas.");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent event) {if(protectedBlock(event.getBlock())) event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent event) {event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explodeBlock(BlockExplodeEvent event) {event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent event) {if(event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent event) {if(event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true);}
    private static byte[] blank() {
        byte[] pixels=new byte[16384];for(int y=0;y<128;y++) for(int x=0;x<128;x++) {
            int shade=246+Math.floorMod(x*37+y*17,7);pixels[y*128+x]=color((shade<<16)|(shade<<8)|shade);
        }return pixels;
    }
    private static byte[] palette(PigmentCanvas canvas) {
        byte[] pixels=new byte[16384];for(int y=0;y<128;y++) for(int x=0;x<128;x++) pixels[y*128+x]=color(canvas.rgb(x,y));return pixels;
    }
    @SuppressWarnings("deprecation") private static byte color(int rgb) {return MapPalette.matchColor(new Color(rgb));}
    @Override public void onDisable() {
        if(renders!=null) renders.close();easels.values().forEach(this::save);easels.clear();
    }
}
