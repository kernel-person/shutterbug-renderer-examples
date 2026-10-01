package io.github.kernelperson.pov;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Saved ItemFrame owns the monitor; ItemDisplay is transient, optional, and never owns pixels. */
final class PovMonitors implements Listener,AutoCloseable {
    private static final double MAP_FACE_OFFSET=-.023359375;
    private final AdminPovPlugin plugin;
    private final boolean nativeModels;
    private final NamespacedKey ownerTag,baseTag,itemTag,modelTag;
    private final Map<UUID,Monitor> loaded=new HashMap<>();
    private final Map<UUID,ItemDisplay> displays=new HashMap<>();
    private BlockMultiPlaceEvent ownProtectionEvent;
    private record Monitor(UUID owner,ItemFrame frame,Block base,boolean active) {}
    PovMonitors(AdminPovPlugin plugin,boolean nativeModels) {
        this.plugin=plugin;this.nativeModels=nativeModels;
        ownerTag=new NamespacedKey(plugin,"owner");baseTag=new NamespacedKey(plugin,"base");
        itemTag=new NamespacedKey(plugin,"monitor");modelTag=new NamespacedKey(plugin,"native-model");
    }
    ItemStack item(UUID owner) {
        ItemStack item=new ItemStack(Material.OAK_FENCE);var meta=item.getItemMeta();
        meta.setDisplayName("Private POV Desk Monitor");meta.getPersistentDataContainer().set(itemTag,PersistentDataType.STRING,owner.toString());
        if(nativeModels)meta.setItemModel(NamespacedKey.fromString("village_trades:pov_monitor"));
        item.setItemMeta(meta);return item;
    }
    void restore(){for(World world:plugin.getServer().getWorlds())for(Chunk chunk:world.getLoadedChunks())load(chunk);}
    boolean watching(Player owner) {
        for(Monitor monitor:loaded.values()) {
            ItemFrame frame=monitor.frame();
            if(!monitor.active()||!owner.getUniqueId().equals(monitor.owner())||!frame.isValid()||!owner.getWorld().equals(frame.getWorld()))continue;
            var toward=frame.getLocation().toVector().subtract(owner.getEyeLocation().toVector());
            if(toward.lengthSquared()>256||toward.dot(frame.getFacing().getDirection())>=0)continue;
            if(toward.lengthSquared()<.01||(owner.getEyeLocation().getDirection().dot(toward.normalize())>.4&&owner.hasLineOfSight(frame)))return true;
        }
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) {
        if(event==ownProtectionEvent)return;
        ItemStack item=event.getItemInHand();
        if(!item.hasItemMeta())return;
        String owner=item.getItemMeta().getPersistentDataContainer().get(itemTag,PersistentDataType.STRING);if(owner==null)return;
        if(event instanceof BlockMultiPlaceEvent||!event.getPlayer().getUniqueId().toString().equals(owner)
                ||!event.getPlayer().hasPermission(PovAccess.PERMISSION)||!plugin.authorized(event.getPlayer())){event.setCancelled(true);return;}
        Block base=event.getBlockPlaced(),board=base.getRelative(BlockFace.UP);BlockFace front=event.getPlayer().getFacing().getOppositeFace();
        if(base.getType()!=Material.OAK_FENCE||!space(board,front)||loaded.size()>=128){event.setCancelled(true);return;}
        var data=base.getBlockData();
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(event.isCancelled()||!event.canBuild()||!chunkLoaded(base)||base.getType()!=Material.OAK_FENCE||!Objects.equals(data,base.getBlockData()))return;
            if(!space(board,front)||loaded.size()>=128||!plugin.authorized(event.getPlayer())){rollback(event);return;}
            commit(event,base,board,front);
        });
    }
    private static boolean chunkLoaded(Block block){return block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4);}
    private static boolean space(Block board,BlockFace front) {
        Block air=board.getRelative(front);
        // Keep supports and the authoritative frame in one chunk, so they share a lifecycle.
        return board.getX()>>4==air.getX()>>4&&board.getZ()>>4==air.getZ()>>4
                &&chunkLoaded(board)&&chunkLoaded(air)&&board.getType().isAir()&&air.getType().isAir();
    }
    private void commit(BlockPlaceEvent event,Block base,Block board,BlockFace front) {
        BlockState original=board.getState();ItemFrame frame=null;
        try {
            board.setType(Material.OAK_PLANKS,false);
            var blockProtection=new BlockMultiPlaceEvent(List.of(event.getBlockReplacedState(),original),event.getBlockAgainst(),event.getItemInHand(),event.getPlayer(),true);
            ownProtectionEvent=blockProtection;
            try {plugin.getServer().getPluginManager().callEvent(blockProtection);}
            finally {ownProtectionEvent=null;}
            if(blockProtection.isCancelled()||!blockProtection.canBuild())throw new IllegalStateException("Protected placement");
            frame=base.getWorld().spawn(board.getRelative(front).getLocation().add(.5,.5,.5),ItemFrame.class);
            frame.setFacingDirection(front,true);frame.setItemDropChance(0);
            var hanging=new HangingPlaceEvent(frame,event.getPlayer(),board,front,event.getHand(),event.getItemInHand());
            plugin.getServer().getPluginManager().callEvent(hanging);if(hanging.isCancelled())throw new IllegalStateException("Protected hanging");
            UUID owner=event.getPlayer().getUniqueId();
            frame.getPersistentDataContainer().set(ownerTag,PersistentDataType.STRING,owner.toString());
            frame.getPersistentDataContainer().set(baseTag,PersistentDataType.INTEGER_ARRAY,new int[]{base.getX(),base.getY(),base.getZ()});
            frame.setItem(plugin.receiver(owner),false);frame.setFixed(true);
            Monitor monitor=new Monitor(owner,frame,base,true);loaded.put(frame.getUniqueId(),monitor);apply(monitor);
        }catch(RuntimeException failure) {
            if(frame!=null){unload(frame.getUniqueId());frame.remove();}
            if(board.getType()==Material.OAK_PLANKS||board.getType()==Material.BARRIER)original.update(true,false);
            if(base.getType()==Material.BARRIER)base.setType(Material.OAK_FENCE,false);
            rollback(event);event.getPlayer().sendMessage("Monitor placement was rejected; your item was returned.");
        }
    }
    private void rollback(BlockPlaceEvent event) {
        if(event.getBlockPlaced().getType()!=Material.OAK_FENCE)return;
        event.getBlockReplacedState().update(true,false);
        if(event.getPlayer().getGameMode()==GameMode.CREATIVE)return;
        ItemStack refund=event.getItemInHand().clone();refund.setAmount(1);
        for(ItemStack leftover:event.getPlayer().getInventory().addItem(refund).values())event.getBlockPlaced().getWorld().dropItemNaturally(event.getBlockPlaced().getLocation(),leftover);
    }
    private void load(Chunk chunk) {
        for(Entity entity:chunk.getEntities())if(entity instanceof ItemFrame frame) {
            String owner=frame.getPersistentDataContainer().get(ownerTag,PersistentDataType.STRING);
            int[] pos=frame.getPersistentDataContainer().get(baseTag,PersistentDataType.INTEGER_ARRAY);
            if(owner==null||pos==null||pos.length!=3||loaded.containsKey(frame.getUniqueId()))continue;
            try {
                // Validate proximity before accessing support state; malformed PDC must not force-load chunks.
                Location location=frame.getLocation();
                if(Math.abs((long)pos[0]-location.getBlockX())>1||Math.abs((long)pos[1]-location.getBlockY())>2||Math.abs((long)pos[2]-location.getBlockZ())>1)continue;
                Block base=frame.getWorld().getBlockAt(pos[0],pos[1],pos[2]);if(!chunkLoaded(base))continue;
                Monitor monitor=new Monitor(UUID.fromString(owner),frame,base,loaded.size()<128);loaded.put(frame.getUniqueId(),monitor);
                frame.setFixed(true);
                if(monitor.active()){frame.setItem(plugin.receiver(monitor.owner()),false);apply(monitor);}
                else {
                    frame.setItem(new ItemStack(Material.AIR),false);frame.setVisible(true);
                    if(loaded.size()==129)plugin.getLogger().warning("POV monitor limit reached; excess saved monitors stay protected but inactive. Unload other monitors, then reload this chunk.");
                }
            }catch(RuntimeException failure){plugin.getLogger().warning("Saved monitor remains protected but could not be restored.");}
        }
    }
    private void apply(Monitor monitor) {
        ItemFrame frame=monitor.frame();Block base=monitor.base(),board=base.getRelative(BlockFace.UP);
        boolean marked=frame.getPersistentDataContainer().has(modelTag,PersistentDataType.BYTE);
        if(!nativeModels) {
            if(marked){if(base.getType()==Material.BARRIER)base.setType(Material.OAK_FENCE,false);if(board.getType()==Material.BARRIER)board.setType(Material.OAK_PLANKS,false);frame.getPersistentDataContainer().remove(modelTag);}
            frame.setVisible(true);return;
        }
        if((base.getType()!=Material.OAK_FENCE&&!(marked&&base.getType()==Material.BARRIER))
                ||(board.getType()!=Material.OAK_PLANKS&&!(marked&&board.getType()==Material.BARRIER)))throw new IllegalStateException("Unrelated support blocks");
        Location origin=frame.getLocation().clone();origin.setDirection(frame.getFacing().getDirection());origin.setYaw(origin.getYaw()+180);origin.setPitch(0);
        ItemDisplay display=frame.getWorld().spawn(origin,ItemDisplay.class);
        try {
            display.setPersistent(false);display.setGravity(false);display.setInvulnerable(true);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(new Transformation(new Vector3f(0,0,(float)(.125-MAP_FACE_OFFSET)),new Quaternionf(),new Vector3f(1),new Quaternionf()));
            display.setDisplayWidth(3);display.setDisplayHeight(4);
            var visual=new ItemStack(Material.PAPER);var meta=visual.getItemMeta();meta.setItemModel(NamespacedKey.fromString("village_trades:pov_monitor"));visual.setItemMeta(meta);display.setItemStack(visual);
            base.setType(Material.BARRIER,false);board.setType(Material.BARRIER,false);frame.setVisible(false);
            frame.getPersistentDataContainer().set(modelTag,PersistentDataType.BYTE,(byte)1);displays.put(frame.getUniqueId(),display);
        }catch(RuntimeException failure){display.remove();throw failure;}
    }
    private void unload(UUID id){loaded.remove(id);ItemDisplay display=displays.remove(id);if(display!=null)display.remove();}
    @EventHandler public void loaded(ChunkLoadEvent event){load(event.getChunk());}
    @EventHandler public void unloaded(ChunkUnloadEvent event) {
        for(Monitor monitor:List.copyOf(loaded.values())) {
            var location=monitor.frame().getLocation();
            if(location.getWorld().equals(event.getWorld())&&location.getBlockX()>>4==event.getChunk().getX()&&location.getBlockZ()>>4==event.getChunk().getZ())unload(monitor.frame().getUniqueId());
        }
    }
    private boolean owned(Entity entity){return entity instanceof ItemFrame frame&&frame.getPersistentDataContainer().has(ownerTag,PersistentDataType.STRING);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEntityEvent event) {
        if(!owned(event.getRightClicked()))return;event.setCancelled(true);
        Monitor monitor=loaded.get(event.getRightClicked().getUniqueId());Player player=event.getPlayer();
        if(monitor==null||event.getHand()!=EquipmentSlot.HAND||!player.isSneaking()||!player.hasPermission(PovAccess.PERMISSION)||!player.getUniqueId().equals(monitor.owner()))return;
        if(player.getInventory().firstEmpty()<0){player.sendMessage("Make space for the monitor item.");return;}
        remove(monitor);player.getInventory().addItem(item(player.getUniqueId()));
    }
    private void remove(Monitor monitor) {
        unload(monitor.frame().getUniqueId());
        boolean marked=monitor.frame().getPersistentDataContainer().has(modelTag,PersistentDataType.BYTE);
        Block base=monitor.base(),board=base.getRelative(BlockFace.UP);
        if(chunkLoaded(base)&&base.getType()==(marked?Material.BARRIER:Material.OAK_FENCE))base.setType(Material.AIR,false);
        if(chunkLoaded(board)&&board.getType()==(marked?Material.BARRIER:Material.OAK_PLANKS))board.setType(Material.AIR,false);
        monitor.frame().remove();
    }
    private boolean protectedBlock(Block block){return loaded.values().stream().anyMatch(m->m.base().equals(block)||m.base().getRelative(BlockFace.UP).equals(block));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent event){if(owned(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hanging(HangingBreakEvent event){if(owned(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent event){if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void burn(BlockBurnEvent event){if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explode(EntityExplodeEvent event){event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explodeBlock(BlockExplodeEvent event){event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void piston(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::protectedBlock))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void retract(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::protectedBlock))event.setCancelled(true);}
    @Override public void close(){displays.values().forEach(Entity::remove);displays.clear();loaded.clear();}
}
