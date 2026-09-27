package io.github.kernelperson.probe;

import ke.ric.renderer.api.RendererService;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * Test-only plugin: synthetic Player boundary, real Paper world/events, real licensed Renderer.
 * It never represents a network-client or human visual playtest. Do not distribute in example ZIP.
 */
public final class ExamplesProbe extends JavaPlugin {
    private static final UUID OWNER=UUID.fromString("48fcf80c-5232-4459-bedd-39a46db6bc9c");
    private final ItemStack[] inventory=new ItemStack[41];
    private Player player;
    private Location eye;
    private World world;
    private ItemFrame frame;
    private Block camera;
    private boolean sneaking;
    private int stage,ticks,stroke,actionBars;
    private ItemStack brush;
    private boolean restart;
    private int baseX,baseY,baseZ;
    @Override public void onEnable() {
        getServer().getScheduler().runTaskTimer(this,()->{
            try {tick();} catch(Throwable error) {
                getLogger().severe("EXAMPLES_PROBE_FAILED stage="+stage+" "+error);
                error.printStackTrace();
                getServer().getScheduler().cancelTasks(this);
            }
        },40,3);
    }
    private void tick() throws Exception {
        if(++ticks>1600) throw new IllegalStateException("deadline");
        if(stage==0) {
            RendererService renderer=getServer().getServicesManager().load(RendererService.class);
            if(renderer==null) return;
            world=getServer().getWorlds().getFirst();
            // Own fixture area in a fresh disposable world only.
            baseX=8;baseY=world.getHighestBlockYAt(8,8)+2;baseZ=8;
            for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) world.getChunkAt(x,z).load();
            eye=new Location(world,baseX+.5,baseY+1.5,baseZ-2,0,0);
            player=fakePlayer(OWNER);
            Path phase=getDataFolder().toPath().resolve("restart.flag");
            restart=Files.exists(phase);
            if(restart) {stage=10;restore();return;}
            for(int x=baseX-4;x<=baseX+20;x++) for(int y=baseY;y<baseY+7;y++)
                world.getBlockAt(x,y,baseZ+8).setType(Material.LIME_CONCRETE,false);
            command("postcard");
            stage=1;
        } else if(stage==1) {
            ItemStack postcard=find(Material.FILLED_MAP);
            if(postcard==null) return;
            check(((MapMeta)postcard.getItemMeta()).hasMapView(),"postcard map");
            getLogger().info("EXAMPLES_POSTCARD_OK");
            Arrays.fill(inventory,null);
            command("rendercamera");
            ItemStack item=find(Material.DISPENSER);check(item!=null,"camera item");
            camera=world.getBlockAt(baseX+4,baseY,baseZ);
            BlockState before=camera.getState();camera.setType(Material.DISPENSER);
            inventory[0]=item;
            var place=new BlockPlaceEvent(camera,before,camera.getRelative(BlockFace.DOWN),item,player,true,EquipmentSlot.HAND);
            getServer().getPluginManager().callEvent(place);check(!place.isCancelled(),"camera placement");
            world.getBlockAt(baseX+5,baseY,baseZ).setType(Material.REDSTONE_BLOCK);
            stage=2;
        } else if(stage==2) {
            ItemStack photo=Arrays.stream(((Dispenser)camera.getState()).getInventory().getContents())
                    .filter(Objects::nonNull).filter(i->i.getType()==Material.FILLED_MAP).findFirst().orElse(null);
            if(photo==null) return;
            getLogger().info("EXAMPLES_CAMERA_OK");
            Arrays.fill(inventory,null);command("easel");
            ItemStack easel=find(Material.OAK_FENCE);brush=find(Material.BRUSH);
            check(easel!=null&&brush!=null,"easel equipment");
            Block base=world.getBlockAt(baseX+12,baseY,baseZ);
            BlockState before=base.getState();base.setType(Material.OAK_FENCE);
            inventory[0]=easel;
            var place=new BlockPlaceEvent(base,before,base.getRelative(BlockFace.DOWN),easel,player,true,EquipmentSlot.HAND);
            getServer().getPluginManager().callEvent(place);check(!place.isCancelled(),"easel placement");
            stage=20;
        } else if(stage==20) {
            Block base=world.getBlockAt(baseX+12,baseY,baseZ);
            frame=world.getEntitiesByClass(ItemFrame.class).stream().filter(e->e.getLocation().distanceSquared(base.getLocation())<10).findFirst().orElseThrow();
            inventory[0]=brush;
            aim();
            getServer().getPluginManager().callEvent(new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND));
            stage=3;
        } else if(stage==3) {
            Object canvas=canvas();if(canvas==null) return;
            check(progress(canvas)==0,"new canvas empty pigment");
            getLogger().info("EXAMPLES_EASEL_CAPTURE_OK");
            stage=4;
        } else if(stage==4) {
            inventory[40]=new ItemStack(new Material[]{Material.CYAN_DYE,Material.MAGENTA_DYE,Material.YELLOW_DYE}[stroke/4]);
            var hit=new EntityDamageByEntityEvent(player,frame,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);
            getServer().getPluginManager().callEvent(hit);check(hit.isCancelled(),"frame protected from punches");
            if(++stroke<12) return;
            check(progress(canvas())>0,"painting advances");
            check(actionBars>0,"painting progress action bar delivered");
            double before=progress(canvas());
            Player intruder=fakePlayer(UUID.fromString("e89799f4-aed5-4e21-b7df-67376c933ea8"));
            getServer().getPluginManager().callEvent(new EntityDamageByEntityEvent(intruder,frame,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1));
            check(progress(canvas())==before,"other player cannot paint");
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("restart.flag"),baseY+"\n"+frame.getUniqueId());
            getLogger().info("EXAMPLES_EASEL_PAINT_OK");
            getLogger().info("EXAMPLES_PROBE_OK phase=initial");
            stage=99;getServer().getScheduler().cancelTasks(this);
        } else if(stage==10) {
            check(frame!=null&&canvas()!=null,"easel restored after restart");
            check(progress(canvas())>0,"pigment progress restored");
            int mapId=((MapMeta)frame.getItem().getItemMeta()).getMapId();
            check(getServer().getMap(mapId).getRenderers().stream().anyMatch(r->r.getClass().getName().startsWith("io.github.kernelperson.easel")),"persistent map renderer restored");
            Arrays.fill(inventory,null);command("easel");inventory[0]=find(Material.BRUSH);
            aim();sneaking=true;
            getServer().getPluginManager().callEvent(new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND));
            check(find(Material.FILLED_MAP)!=null,"collected map");
            check(frame.getItem().getType().isAir(),"canvas emptied");
            long count=mapCount();
            getServer().getPluginManager().callEvent(new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND));
            check(mapCount()==count,"no duplicate collection");
            check(Arrays.stream(((Dispenser)camera.getState()).getInventory().getContents()).filter(Objects::nonNull).filter(i->i.getType()==Material.FILLED_MAP).count()==1,"sustained power/restart does not repeat camera shot");
            getLogger().info("EXAMPLES_RESTART_COLLECTION_OK");
            getLogger().info("EXAMPLES_PROBE_OK phase=restart");
            stage=99;getServer().getScheduler().cancelTasks(this);
        }
    }
    private void restore() throws Exception {
        var lines=Files.readAllLines(getDataFolder().toPath().resolve("restart.flag"));
        baseY=Integer.parseInt(lines.get(0));frame=(ItemFrame)getServer().getEntity(UUID.fromString(lines.get(1)));
        camera=world.getBlockAt(baseX+4,baseY,baseZ);
    }
    private Object canvas() throws Exception {
        Object plugin=getServer().getPluginManager().getPlugin("RendererPaintersEasel");
        Field state=plugin.getClass().getDeclaredField("easels");state.setAccessible(true);
        Object easel=((Map<?,?>)state.get(plugin)).get(frame.getUniqueId());
        if(easel==null) return null;
        Field canvas=easel.getClass().getDeclaredField("canvas");canvas.setAccessible(true);return canvas.get(easel);
    }
    private double progress(Object canvas) throws Exception {
        Method method=canvas.getClass().getDeclaredMethod("progress");method.setAccessible(true);return (double)method.invoke(canvas);
    }
    private void aim() {
        eye=frame.getLocation().add(frame.getFacing().getDirection().multiply(2));
        eye.setDirection(frame.getFacing().getOppositeFace().getDirection());
    }
    private void command(String name) {
        PluginCommand command=getServer().getPluginCommand(name);check(command!=null,"command exists");
        command.execute(player,name,new String[0]);
    }
    private ItemStack find(Material material) {return Arrays.stream(inventory).filter(Objects::nonNull).filter(i->i.getType()==material).findFirst().orElse(null);}
    private long mapCount() {return Arrays.stream(inventory).filter(Objects::nonNull).filter(i->i.getType()==Material.FILLED_MAP).count();}
    private Player fakePlayer(UUID id) {
        PlayerInventory bag=(PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class[]{PlayerInventory.class},(proxy,method,args)->switch(method.getName()) {
            case "getStorageContents" -> Arrays.copyOf(inventory,36);
            case "getContents" -> inventory.clone();
            case "getItemInMainHand" -> empty(inventory[0]);
            case "getItemInOffHand" -> empty(inventory[40]);
            case "getHeldItemSlot" -> 0;
            case "firstEmpty" -> firstEmpty();
            case "setItem" -> {inventory[(int)args[0]]=(ItemStack)args[1];yield null;}
            case "addItem" -> {
                Map<Integer,ItemStack> leftovers=new HashMap<>();ItemStack[] items=(ItemStack[])args[0];
                for(int i=0;i<items.length;i++) {int slot=firstEmpty();if(slot<0) leftovers.put(i,items[i]);else inventory[slot]=items[i];}
                yield leftovers;
            }
            default -> defaultValue(method.getReturnType());
        });
        return (Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(proxy,method,args)->switch(method.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> "RendererAcceptance";
            case "getInventory" -> bag;
            case "getEyeLocation" -> eye.clone();
            case "getLocation" -> eye.clone().subtract(0,1.62,0);
            case "getWorld" -> world;
            case "getFacing" -> BlockFace.SOUTH;
            case "isOnline","isValid","hasPermission","isPermissionSet","isOp" -> true;
            case "isSneaking" -> sneaking;
            case "getServer" -> getServer();
            case "getGameMode" -> GameMode.SURVIVAL;
            case "spigot" -> new Player.Spigot() {
                @Override public void sendMessage(net.md_5.bungee.api.ChatMessageType position,net.md_5.bungee.api.chat.BaseComponent component) {actionBars++;}
                @Override public void sendMessage(net.md_5.bungee.api.ChatMessageType position,net.md_5.bungee.api.chat.BaseComponent... components) {actionBars++;}
            };
            case "sendMessage" -> {getLogger().info("PROBE_MESSAGE "+Arrays.deepToString(args));yield null;}
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy==args[0];
            case "toString" -> "AcceptancePlayer";
            default -> defaultValue(method.getReturnType());
        });
    }
    private int firstEmpty() {for(int i=0;i<36;i++) if(inventory[i]==null||inventory[i].getType().isAir()) return i;return -1;}
    private ItemStack empty(ItemStack item) {return item==null?new ItemStack(Material.AIR):item;}
    private static Object defaultValue(Class<?> type) {
        if(!type.isPrimitive()) return null;
        if(type==boolean.class) return false;if(type==int.class) return 0;if(type==long.class) return 0L;
        if(type==double.class) return 0d;if(type==float.class) return 0f;
        if(type==short.class) return (short)0;if(type==byte.class) return (byte)0;if(type==char.class) return (char)0;return null;
    }
    private static void check(boolean condition,String message) {if(!condition) throw new IllegalStateException(message);}
}
