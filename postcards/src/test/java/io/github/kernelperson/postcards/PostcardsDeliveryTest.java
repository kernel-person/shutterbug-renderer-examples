package io.github.kernelperson.postcards;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PostcardsDeliveryTest {
    @Test void givesOnlyToConnectedPlayerWithSpaceAndNeverDropsOnTheGround() {
        Player player=mock(Player.class); PlayerInventory inventory=mock(PlayerInventory.class);
        ItemStack map=mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isOnline()).thenReturn(true); when(inventory.firstEmpty()).thenReturn(3);
        assertTrue(PostcardsPlugin.give(player,map));
        verify(inventory).setItem(3,map);
        when(inventory.firstEmpty()).thenReturn(-1);
        assertFalse(PostcardsPlugin.give(player,map));
        when(player.isOnline()).thenReturn(false); when(inventory.firstEmpty()).thenReturn(2);
        assertFalse(PostcardsPlugin.give(player,map));
        verify(inventory,never()).setItem(2,map);
    }
}
