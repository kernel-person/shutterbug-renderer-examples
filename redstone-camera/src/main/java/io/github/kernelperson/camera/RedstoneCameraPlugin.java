package io.github.kernelperson.camera;

import ke.ric.renderer.api.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.*;

/** A tagged dispenser owns the camera and inventory; optional native displays are cosmetic. */
public final class RedstoneCameraPlugin extends JavaPlugin implements Listener {
    private final Map<String,CameraTarget> cameras=new HashMap<>();
    private NamespacedKey tag;
    private RenderSession renders;
    private SavedMaps maps;
    private CameraModel models;
    private boolean sampleScheduled;
    @Override public void onEnable() {
        saveDefaultConfig();models=new CameraModel(getConfig().getBoolean("native-models",false));
        tag=new NamespacedKey(this,"camera"); maps=new SavedMaps(this); renders=new RenderSession(this);
        try { maps.restore(); }
        catch(IOException failure) { getLogger().severe("Cannot restore saved maps."); getServer().getPluginManager().disablePlugin(this); return; }
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(renders,this);
        for(World world:getServer().getWorlds()) for(Chunk chunk:world.getLoadedChunks()) load(chunk);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)) { sender.sendMessage("Run /rendercamera in game."); return true; }
        if(!player.hasPermission("rendererexamples.camera")) return true;
        int slot=player.getInventory().firstEmpty();
        if(slot<0) { player.sendMessage("Make space first.");return true; }
        ItemStack item=getConfig().getBoolean("native-models",false)?CameraModel.item(Material.DISPENSER,"redstone_camera"):new ItemStack(Material.DISPENSER);
        var meta=item.getItemMeta();meta.setDisplayName("Redstone Camera");
        meta.getPersistentDataContainer().set(tag,PersistentDataType.STRING,"camera-item");
        item.setItemMeta(meta);player.getInventory().setItem(slot,item);
        player.sendMessage("Placement remembers your exact look angle (including up/down). Pulse adjacent redstone; photos appear inside.");
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) {
        ItemStack item=event.getItemInHand();
        if(!item.hasItemMeta()||!item.getItemMeta().getPersistentDataContainer().has(tag,PersistentDataType.STRING)) return;
        if(!(event.getBlockPlaced().getState() instanceof Dispenser dispenser)) return;
        if(cameras.size()>=256) { event.setCancelled(true);event.getPlayer().sendMessage("Example camera limit reached (256 loaded cameras).");return; }
        Block block=event.getBlockPlaced();var placed=block.getBlockData();
        Location view=event.getPlayer().getEyeLocation();
        BlockFace face=event.getPlayer().getFacing();
        // Final protection listeners must finish before identity or transient visuals exist.
        getServer().getScheduler().runTask(this,()->{
            if(event.isCancelled()||!event.canBuild()||!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)
                    ||block.getType()!=Material.DISPENSER||!Objects.equals(placed,block.getBlockData())||cameras.size()>=256)return;
            if(!(block.getState() instanceof Dispenser current))return;
            Directional data=(Directional)current.getBlockData();data.setFacing(face);current.setBlockData(data);
            String id=UUID.randomUUID().toString();current.getPersistentDataContainer().set(tag,PersistentDataType.STRING,id);
            new CameraPose(view.getYaw(),view.getPitch()).save(current);
            current.update(true,false);remember(block,id);
        });
    }
    private void remember(Block block,String id) {
        CameraTarget camera=new CameraTarget(block,id,tag,new PowerEdge(powered(block)));
        cameras.put(id,camera);
        if(models!=null)try {models.apply(camera);}catch(RuntimeException failure) {getLogger().warning("Camera cosmetic could not be loaded; dispenser remains functional.");}
    }
    private boolean powered(Block block) { return block.isBlockPowered()||block.isBlockIndirectlyPowered(); }
    private void load(Chunk chunk) {
        for(BlockState state:chunk.getTileEntities()) if(state instanceof Dispenser dispenser) {
            String id=dispenser.getPersistentDataContainer().get(tag,PersistentDataType.STRING);
            if(id!=null) {
                try { UUID.fromString(id); if(cameras.size()<256) remember(state.getBlock(),id); }
                catch(IllegalArgumentException ignored) { getLogger().warning("Ignored invalid camera identity."); }
            }
        }
    }
    @EventHandler public void loaded(ChunkLoadEvent event) { load(event.getChunk()); }
    @EventHandler public void unloaded(ChunkUnloadEvent event) {
        for(CameraTarget camera:List.copyOf(cameras.values()))
            if(camera.block().getWorld().equals(event.getWorld())
                    && camera.block().getX()>>4==event.getChunk().getX()
                    && camera.block().getZ()>>4==event.getChunk().getZ()) remove(camera);
    }
    private void remove(CameraTarget camera) { cameras.remove(camera.id());renders.invalidate(camera.id());if(models!=null)models.remove(camera.id()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(BlockBreakEvent event) {
        for(CameraTarget camera:List.copyOf(cameras.values())) if(camera.block().equals(event.getBlock())) remove(camera);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent event) {
        if(event.getBlock().getState() instanceof Dispenser dispenser
                && dispenser.getPersistentDataContainer().has(tag,PersistentDataType.STRING)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent event) { if(event.getBlocks().stream().anyMatch(this::isCamera)) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent event) { if(event.getBlocks().stream().anyMatch(this::isCamera)) event.setCancelled(true); }
    private boolean isCamera(Block block) { return cameras.values().stream().anyMatch(c->c.block().equals(block)); }
    // Direct redstone-block placement/removal need not emit BlockRedstoneEvent.
    @EventHandler public void physics(BlockPhysicsEvent event) { scheduleSample(); }
    @EventHandler public void redstone(BlockRedstoneEvent event) { scheduleSample(); }
    private void scheduleSample() {
        if(sampleScheduled||cameras.isEmpty()) return;
        sampleScheduled=true;
        // Redstone events report pre-physics state. Sample after that update, once per tick.
        getServer().getScheduler().runTask(this,()->{
            sampleScheduled=false;
            for(CameraTarget camera:List.copyOf(cameras.values())) {
                if(!camera.valid()) { remove(camera);continue; }
                if(camera.edge().sample(powered(camera.block()))) capture(camera);
            }
        });
    }
    private void capture(CameraTarget camera) {
        Dispenser dispenser=(Dispenser)camera.block().getState();
        if(dispenser.getInventory().firstEmpty()<0) { feedback(camera,false);return; }
        CameraPose pose=CameraPose.read(dispenser);
        Location lens=CameraPose.lens(camera.block().getLocation(),pose.yaw(),pose.pitch());
        renders.request(camera.id(),lens,camera::valid,frame->{
            if(!camera.valid()) return;
            Dispenser current=(Dispenser)camera.block().getState();
            int slot=current.getInventory().firstEmpty();
            if(slot<0) { feedback(camera,false);return; }
            try {
                ItemStack map=maps.create(camera.block().getWorld(),MinecraftMapConverter.quantize(frame,MapDither.BAYER),"Camera Photo");
                current.getInventory().setItem(slot,map);feedback(camera,true);
            } catch(IOException failure) { getLogger().warning("Camera photo could not be saved.");feedback(camera,false); }
        },message->feedback(camera,false));
    }
    private void feedback(CameraTarget camera,boolean success) {
        if(camera.valid()) camera.block().getWorld().playSound(camera.block().getLocation(),
                success?Sound.BLOCK_NOTE_BLOCK_PLING:Sound.BLOCK_NOTE_BLOCK_BASS,.5f,success?1.5f:.5f);
    }
    @Override public void onDisable() { if(renders!=null) renders.close();if(models!=null)models.close();cameras.clear(); }
}
