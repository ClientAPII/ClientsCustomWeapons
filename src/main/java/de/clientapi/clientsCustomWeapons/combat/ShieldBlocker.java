package de.clientapi.clientsCustomWeapons.combat;

import de.clientapi.clientsCustomWeapons.util.Keys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ShieldBlocker implements Listener {

    private static final int OFFHAND_RAW_SLOT = 45;
    private static final int OFFHAND_SLOT = 40;

    private final Plugin plugin;

    public ShieldBlocker(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        ItemStack newMain = e.getOffHandItem();
        ItemStack newOff  = e.getMainHandItem();

        if ((isTwoHanded(newMain) && isShield(newOff)) || (isTwoHanded(newOff) && isShield(newMain))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getType() != InventoryType.CRAFTING) return;

        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        int rawSlot = e.getRawSlot();
        int slot = e.getSlot();
        int heldSlot = p.getInventory().getHeldItemSlot();

        if (slot == heldSlot && isTwoHanded(cursor) && isShield(p.getInventory().getItemInOffHand())) {
            e.setCancelled(true);
            return;
        }

        if (e.getClick().isShiftClick()) {
            if (isShield(current) && holdsTwoHanded(p)) {
                e.setCancelled(true);
            }
            return;
        }

        if (rawSlot == OFFHAND_RAW_SLOT) {
            if (isShield(cursor) && isTwoHanded(p.getInventory().getItemInMainHand())) { e.setCancelled(true); return; }
            if (isTwoHanded(cursor) && isShield(p.getInventory().getItemInMainHand())) { e.setCancelled(true); return; }
        }

        if (e.getAction() == InventoryAction.HOTBAR_SWAP || e.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack hotbarItem = (e.getHotbarButton() != -1) ? p.getInventory().getItem(e.getHotbarButton()) : current;

            if (e.getSlot() == OFFHAND_SLOT || rawSlot == OFFHAND_RAW_SLOT) {
                if (isShield(hotbarItem) && isTwoHanded(p.getInventory().getItemInMainHand())) e.setCancelled(true);
                if (isTwoHanded(hotbarItem) && isShield(p.getInventory().getItemInMainHand())) e.setCancelled(true);
            } else {
                ItemStack off = p.getInventory().getItemInOffHand();
                if (isShield(hotbarItem) && isTwoHanded(off)) e.setCancelled(true);
                if (isTwoHanded(hotbarItem) && isShield(off)) e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getType() != InventoryType.CRAFTING) return;

        int heldSlot = p.getInventory().getHeldItemSlot();
        if (e.getRawSlots().stream().anyMatch(raw -> raw == heldSlot + 36)) {
            ItemStack incoming = e.getOldCursor();
            if (isTwoHanded(incoming) && isShield(p.getInventory().getItemInOffHand())) e.setCancelled(true);
        }
        if (e.getRawSlots().contains(OFFHAND_RAW_SLOT)) {
            ItemStack incoming = e.getOldCursor();
            if (isShield(incoming) && isTwoHanded(p.getInventory().getItemInMainHand())) e.setCancelled(true);
            if (isTwoHanded(incoming) && isShield(p.getInventory().getItemInMainHand())) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHotbarSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack newMain = p.getInventory().getItem(e.getNewSlot());
        ItemStack off = p.getInventory().getItem(EquipmentSlot.OFF_HAND);
        if (isTwoHanded(newMain) && isShield(off)) moveOutOfHand(p, EquipmentSlot.OFF_HAND, off);
    }

    private boolean holdsTwoHanded(Player p) {
        PlayerInventory inv = p.getInventory();
        return isTwoHanded(inv.getItemInMainHand()) || isTwoHanded(inv.getItemInOffHand());
    }

    private boolean isTwoHanded(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        String cls = meta.getPersistentDataContainer().get(Keys.weaponClass(plugin), PersistentDataType.STRING);
        return "ZWEIHAENDER".equalsIgnoreCase(cls);
    }

    private boolean isShield(ItemStack s) { return s != null && s.getType() == Material.SHIELD; }

    private void moveOutOfHand(Player p, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        p.getInventory().setItem(slot, null);
        var left = p.getInventory().addItem(item);
        if (!left.isEmpty()) left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
    }
}
