package io.github.kernelperson.pov;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PovAccessTest {
    @Test void permissionOwnershipOnlineAndTargetVisibilityAreAllRequired() {
        UUID owner=UUID.randomUUID();Player player=mock(Player.class),target=mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);when(player.isOnline()).thenReturn(true);
        when(target.isOnline()).thenReturn(true);when(player.canSee(target)).thenReturn(true);
        when(player.hasPermission("rendererexamples.pov")).thenReturn(true);
        assertTrue(PovAccess.allowed(owner,player,target));
        assertFalse(PovAccess.allowed(UUID.randomUUID(),player,target));
        when(player.hasPermission("rendererexamples.pov")).thenReturn(false);assertFalse(PovAccess.allowed(owner,player,target));
        when(player.hasPermission("rendererexamples.pov")).thenReturn(true);
        when(player.canSee(target)).thenReturn(false);assertFalse(PovAccess.allowed(owner,player,target));
        when(player.canSee(target)).thenReturn(true);when(target.isOnline()).thenReturn(false);
        assertFalse(PovAccess.allowed(owner,player,target));assertFalse(PovAccess.allowed(owner,player,null));
    }
}
