package com.h2ph.managers;

import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryWorthManager {

    private final Falcon plugin;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingJoinPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask sweepTask;
    private boolean enabled = true;

    private static final String WORTH_LORE_PREFIX = ChatColor.GRAY + "Worth:" + ChatColor.GREEN + " ";
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    public InventoryWorthManager(Falcon plugin) {
        this.plugin = plugin;
    }

    public void reloadConfig() {
        if (plugin.getFalconSell() != null) {
            boolean wasEnabled = this.enabled;
            this.enabled = plugin.getFalconSell().getConfig().getBoolean("settings.worth-lore-enabled", true);
            
            if (wasEnabled && !this.enabled) {
                for (UUID uuid : activePlayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        stripWorthLore(player);
                    }
                }
                activePlayers.clear();
                pendingJoinPlayers.clear();
            } else if (!wasEnabled && this.enabled) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    scheduleActivation(p);
                }
            }
        }
    }

    public void scheduleActivation(Player player) {
        UUID uuid = player.getUniqueId();
        pendingJoinPlayers.add(uuid);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                stripWorthLore(p);
            }
        }, 5L);

        plugin.getSchedulerAdapter().runEntityTaskLater(player, () -> {
            if (!pendingJoinPlayers.contains(uuid)) return;
            
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                activePlayers.add(uuid);
                if (applyWorthLore(p)) p.updateInventory();
            }
            pendingJoinPlayers.remove(uuid);
        }, 400L);
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        activePlayers.remove(uuid);
        pendingJoinPlayers.remove(uuid);
        
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                ItemStack clone = item.clone();
                if (stripFromItem(clone)) {
                    inv.setItem(i, clone);
                }
            }
        }
        
        if (player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (isStandardContainer(top)) {
                if (player.getOpenInventory().getTopInventory().getViewers().size() <= 1) {
                    for (int i = 0; i < top.getSize(); i++) {
                        ItemStack item = top.getItem(i);
                        if (item != null && item.getType() != org.bukkit.Material.AIR) {
                            ItemStack clone = item.clone();
                            if (stripFromItem(clone)) {
                                top.setItem(i, clone);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isActive(Player player) {
        if (!enabled) return false;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return false;
        return activePlayers.contains(player.getUniqueId());
    }

    public boolean isStandardContainer(Inventory top) {
        if (top == null) return false;
        org.bukkit.event.inventory.InventoryType type = top.getType();
        if (type == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return true;
        if (type == org.bukkit.event.inventory.InventoryType.SHULKER_BOX) return true;
        if (top.getHolder() instanceof org.bukkit.block.Container) return true;
        if (top.getHolder() instanceof org.bukkit.block.DoubleChest) return true;
        return false;
    }

    public boolean applyWorthLore(Player player) {
        if (!enabled) return false;
        if (plugin.getFalconSell() == null || plugin.getFalconSell().getPricesManager() == null) return false;

        boolean changed = false;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                ItemStack clone = item.clone();
                if (applyToItem(clone)) {
                    inv.setItem(i, clone);
                    changed = true;
                }
            }
        }
        
        if (player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (isStandardContainer(top)) {
                for (int i = 0; i < top.getSize(); i++) {
                    ItemStack item = top.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        ItemStack clone = item.clone();
                        if (applyToItem(clone)) {
                            top.setItem(i, clone);
                            changed = true;
                        }
                    }
                }
            }
        }
        
        return changed;
    }

    public boolean stripWorthLore(Player player) {
        boolean changed = false;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                if (stripFromItem(item)) {
                    changed = true;
                }
            }
        }
        
        if (player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (isStandardContainer(top)) {
                for (int i = 0; i < top.getSize(); i++) {
                    ItemStack item = top.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        if (stripFromItem(item)) {
                            changed = true;
                        }
                    }
                }
            }
        }
        
        return changed;
    }

    public boolean stripWorthLoreByMaterial(Player player, Material material) {
        boolean changed = false;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == material) {
                if (stripFromItem(item)) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    public boolean applyToItem(ItemStack item) {
        if (!enabled) return false;
        if (item == null || item.getType() == Material.AIR) return false;
        if (plugin.getFalconSell() == null || plugin.getFalconSell().getPricesManager() == null) return false;

        double unitPrice = plugin.getFalconSell().getPricesManager().getPrice(item);
        if (unitPrice <= 0) return false;

        double totalPrice = unitPrice * item.getAmount();
        String expectedLine = WORTH_LORE_PREFIX + "$" + formatPrice(totalPrice);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        boolean needsUpdate = false;
        int worthIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line != null && org.bukkit.ChatColor.stripColor(line).toLowerCase().contains("worth:")) {
                if (worthIndex != -1) {
                    needsUpdate = true;
                }
                worthIndex = i;
            }
        }

        if (!needsUpdate && worthIndex != -1) {
            if (lore.get(worthIndex).equals(expectedLine)) {
                return false;
            }
        }

        lore.removeIf(line -> line != null && org.bukkit.ChatColor.stripColor(line).toLowerCase().contains("worth:"));

        lore.add(expectedLine);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    public boolean stripFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;

        List<String> lore = new ArrayList<>(meta.getLore());
        boolean removed = lore.removeIf(line -> line != null && org.bukkit.ChatColor.stripColor(line).toLowerCase().contains("worth:"));

        if (removed) {
            meta.setLore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }


    private String formatPrice(double number) {
        if (number >= 1_000_000_000_000.0) return formatWithSuffix(number, 1_000_000_000_000.0, "T");
        if (number >= 1_000_000_000.0) return formatWithSuffix(number, 1_000_000_000.0, "B");
        if (number >= 1_000_000.0) return formatWithSuffix(number, 1_000_000.0, "M");
        if (number >= 1_000.0) return formatWithSuffix(number, 1_000.0, "K");
        return String.valueOf((long) number);
    }

    private String formatWithSuffix(double number, double divisor, String suffix) {
        double scaled = Math.floor((number / divisor) * 10) / 10.0;
        if (scaled == (long) scaled) return (long) scaled + suffix;
        return DF.format(scaled) + suffix;
    }

    public void shutdown() {
        pendingJoinPlayers.clear();
        for (UUID uuid : activePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                stripWorthLore(player);
            }
        }
        activePlayers.clear();
    }
}
