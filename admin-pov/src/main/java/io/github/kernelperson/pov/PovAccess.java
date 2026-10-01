package io.github.kernelperson.pov;

import java.util.UUID;
import org.bukkit.entity.Player;

final class PovAccess {
    static final String PERMISSION="rendererexamples.pov";
    static boolean allowed(UUID owner,Player viewer,Player target) {
        return viewer!=null&&target!=null&&owner.equals(viewer.getUniqueId())
                &&viewer.isOnline()&&target.isOnline()&&viewer.hasPermission(PERMISSION)&&viewer.canSee(target);
    }
}
