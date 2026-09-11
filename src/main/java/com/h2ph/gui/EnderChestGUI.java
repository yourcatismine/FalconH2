package com.h2ph.gui;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class EnderChestGUI {

    private final Falcon plugin;

    public EnderChestGUI(Falcon plugin) {
        this.plugin = plugin;
    }

    public static Map<UUID, Inventory> getActiveInventories() {
        return Falcon.getInstance().getEnderChestManager().getActiveInventories();
    }

    /** Open own echest via /echest command — no block animation. */
    public void open(Player player) {
        open(player, player.getUniqueId(), player.getName(), null);
    }

    /**
     * Open own echest via block interaction — triggers lid open/close animation on
     * the block.
     */
    public void open(Player player, @Nullable Block block) {
        open(player, player.getUniqueId(), player.getName(), block);
    }

    /**
     * Open an enderchest for a specific player (viewer may be different from
     * owner).
     */
    public void open(Player viewer, UUID ownerUUID, String ownerName, @Nullable Block block) {
        Inventory inv = getActiveInventories().get(ownerUUID);

        if (inv != null) {
            if (inv.getHolder() instanceof EnderChestHolder) {
                ((EnderChestHolder) inv.getHolder()).setSourceBlock(block);
            }
            viewer.openInventory(inv);
            if (block != null) {
                plugin.getEnderChestManager().registerViewer(block, viewer);
            } else {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
            }
            return;
        }

        plugin.getSchedulerAdapter().runTaskAsync(() -> {
            ItemStack[] contents = plugin.getEnderChestManager().loadEnderChest(ownerUUID);

            plugin.getSchedulerAdapter().runEntityTask(viewer, () -> {
                if (!viewer.isOnline())
                    return;

                Inventory finalInv = plugin.getEnderChestManager().getOrCreateInventory(ownerUUID, ownerName, block,
                        contents);

                if (finalInv.getHolder() instanceof EnderChestHolder) {
                    ((EnderChestHolder) finalInv.getHolder()).setSourceBlock(block);
                }

                viewer.openInventory(finalInv);

                if (block != null) {
                    plugin.getEnderChestManager().registerViewer(block, viewer);
                } else {
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
                }
            });
        });
    }

    public static class EnderChestHolder implements InventoryHolder {
        private final UUID ownerUUID;
        private final String ownerName;
        @Nullable
        private Block sourceBlock;

        public EnderChestHolder(UUID ownerUUID, String ownerName, @Nullable Block sourceBlock) {
            this.ownerUUID = ownerUUID;
            this.ownerName = ownerName;
            this.sourceBlock = sourceBlock;
        }

        public UUID getOwnerUUID() {
            return ownerUUID;
        }

        public String getOwnerName() {
            return ownerName;
        }

        @Nullable
        public Block getSourceBlock() {
            return sourceBlock;
        }

        public void setSourceBlock(@Nullable Block sourceBlock) {
            this.sourceBlock = sourceBlock;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }
}
