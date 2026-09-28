package io.github.kernelperson.easel;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class BrushAppearanceTest {
    @Test void existingTaggedBrushesGetModelWithoutChangingOrdinaryTools() throws Exception {
        var plugin=mock(PaintersEaselPlugin.class,CALLS_REAL_METHODS);
        var kind=new NamespacedKey("rendererexample","kind");
        var model=new NamespacedKey("village_trades","paintbrush");
        EaselProtectionTest.set(plugin,"kind",kind);EaselProtectionTest.set(plugin,"brushModel",model);
        var player=mock(Player.class);var inventory=mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack brush=mock(ItemStack.class),ordinary=mock(ItemStack.class),current=mock(ItemStack.class);
        ItemMeta brushMeta=meta(brush,kind,"brush"),ordinaryMeta=meta(ordinary,kind,null),currentMeta=meta(current,kind,"brush");
        when(currentMeta.getItemModel()).thenReturn(model);
        when(inventory.getContents()).thenReturn(new ItemStack[]{brush,ordinary,null,current});
        plugin.refreshBrushes(player);
        verify(brushMeta).setItemModel(model);verify(brush).setItemMeta(brushMeta);verify(inventory).setItem(0,brush);
        verify(brushMeta,never()).setDisplayName(anyString());
        verify(brushMeta.getPersistentDataContainer(),never()).set(any(),any(),any());
        verify(ordinaryMeta,never()).setItemModel(any());verify(currentMeta,never()).setItemModel(any());
        verify(inventory,never()).setItem(eq(1),any());verify(inventory,never()).setItem(eq(3),any());
    }
    @Test void defaultWithoutPackDoesNotModifyInventory() {
        var plugin=mock(PaintersEaselPlugin.class,CALLS_REAL_METHODS);var player=mock(Player.class);
        plugin.refreshBrushes(player);verifyNoInteractions(player);
    }
    private static ItemMeta meta(ItemStack item,NamespacedKey kind,String type) {
        var meta=mock(ItemMeta.class);var tags=mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(tags);when(tags.get(kind,PersistentDataType.STRING)).thenReturn(type);
        return meta;
    }
}
