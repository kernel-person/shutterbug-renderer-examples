package io.github.kernelperson.pov;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Only identities and current bindings. No pixels, locations, names, or recording history. */
final class BindingStore {
    record Binding(UUID owner,int mapId,UUID target) {}
    private final Path file;
    BindingStore(Path file){this.file=file;}
    List<Binding> load() throws IOException {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return List.of();
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file))throw new IOException("Invalid bindings file");
        try {
            var yaml=new YamlConfiguration();yaml.load(file.toFile());
            List<Binding> result=new ArrayList<>();
            for(var entry:yaml.getMapList("owners"))result.add(new Binding(UUID.fromString((String)entry.get("owner")),
                    ((Number)entry.get("map")).intValue(),entry.get("target")==null?null:UUID.fromString((String)entry.get("target"))));
            validate(result);return result;
        }catch(Exception bad){throw new IOException("Invalid receiver bindings",bad);}
    }
    void save(Collection<Binding> bindings) throws IOException {
        validate(bindings);
        if(Files.isSymbolicLink(file)||Files.isSymbolicLink(file.getParent()))throw new IOException("Symlink bindings path");
        Files.createDirectories(file.getParent());var yaml=new YamlConfiguration();
        List<Map<String,Object>> values=new ArrayList<>();
        for(Binding binding:bindings.stream().sorted(Comparator.comparing(b->b.owner().toString())).toList()) {
            Map<String,Object> entry=new LinkedHashMap<>();entry.put("owner",binding.owner().toString());entry.put("map",binding.mapId());
            if(binding.target()!=null)entry.put("target",binding.target().toString());values.add(entry);
        }
        yaml.set("owners",values);Path temp=Files.createTempFile(file.getParent(),"bindings-",".tmp");
        try {yaml.save(temp.toFile());Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }
    private static void validate(Collection<Binding> values)throws IOException {
        if(values.size()>128)throw new IOException("Too many receiver owners");
        Set<UUID> owners=new HashSet<>();Set<Integer> maps=new HashSet<>();
        for(Binding value:values)if(value.owner()==null||value.mapId()<0||!owners.add(value.owner())||!maps.add(value.mapId()))
            throw new IOException("Invalid or duplicate receiver identity");
    }
}
