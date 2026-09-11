package com.h2ph.listeners;

import com.h2ph.Falcon;
import com.h2ph.gui.EnderChestGUI;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class EnderChestGUIListener implements Listener {

    private final Falcon plugin;

    public EnderChestGUIListener(Falcon plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts right-clicks on ender chest blocks and opens the custom GUI.
     * Passes the clicked block so the lid animation can be tracked per-block.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderChestOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (e.getHand() != EquipmentSlot.HAND)
            return;

        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST)
            return;

        Player player = e.getPlayer();

        e.setCancelled(true);

        new EnderChestGUI(plugin).open(player, block);
    }

    /**
     * Triggered when the player closes the GUI.
     * Plays the close sound, fires the lid-close animation, and saves
     * asynchronously.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof EnderChestGUI.EnderChestHolder))
            return;
        if (!(e.getPlayer() instanceof Player))
            return;

        Player player = (Player) e.getPlayer();
        EnderChestGUI.EnderChestHolder holder = (EnderChestGUI.EnderChestHolder) e.getInventory().getHolder();
        UUID ownerUUID = holder.getOwnerUUID();

        ItemStack[] contents = e.getInventory().getContents().clone();

        Block sourceBlock = holder.getSourceBlock();
        if (sourceBlock != null) {
            plugin.getEnderChestManager().unregisterViewer(sourceBlock, player);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1f, 1f);
        }

        if (e.getInventory().getViewers().size() <= 1) {
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                plugin.getEnderChestManager().saveEnderChest(ownerUUID, contents);
            });
        }
    }

    /**
     * Safety save on disconnect — ensures items are never lost on quit.
     * Also unregisters the viewer so the lid closes for other players nearby.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        Inventory openInv = player.getOpenInventory().getTopInventory();

        if (!(openInv.getHolder() instanceof EnderChestGUI.EnderChestHolder))
            return;

        EnderChestGUI.EnderChestHolder holder = (EnderChestGUI.EnderChestHolder) openInv.getHolder();
        UUID ownerUUID = holder.getOwnerUUID();
        ItemStack[] contents = openInv.getContents().clone();

        Block sourceBlock = holder.getSourceBlock();
        if (sourceBlock != null) {
            plugin.getEnderChestManager().unregisterViewer(sourceBlock, player);
        }

        if (openInv.getViewers().size() <= 1) {
            plugin.getSchedulerAdapter().runTaskAsync(() -> {
                plugin.getEnderChestManager().saveEnderChest(ownerUUID, contents);
            });
        }
    }
}
