/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.Sound
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package com.falconcore.survival.sell.gui;

import com.falconcore.survival.sell.FalconSell;
import com.falconcore.survival.sell.category.Category;
import com.falconcore.survival.sell.data.PlayerData;
import com.falconcore.survival.sell.utils.MessageUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ProgressGUI
        implements Listener {
    private final FalconSell plugin;
    private final Map<UUID, String> openInventories;

    public ProgressGUI(FalconSell plugin) {
        this.plugin = plugin;
        this.openInventories = new HashMap<UUID, String>();
    }

    public void openCategoryGUI(Player player, Category category) {
        String title = this.plugin.getGUIManager().getProgressGUITitle(category.getKey());
        Inventory inv = Bukkit.createInventory(null, (int) 54, (String) title);
        PlayerData data = this.plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        double progress = data.getProgress(category);
        double multiplier = data.getMultiplier(category);
        ItemStack darkGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta darkMeta = darkGlass.getItemMeta();
        if (darkMeta != null) {
            darkMeta.setDisplayName(" ");
            darkGlass.setItemMeta(darkMeta);
        }
        int i = 0;
        while (i < 54) {
            inv.setItem(i, darkGlass);
            ++i;
        }
        ItemStack categoryIcon = this.createCategoryIcon(category, multiplier, progress);
        inv.setItem(1, categoryIcon);
        List<Double> levelPrices = this.plugin.getConfig().getDoubleList("level-prices");
        int currentLevel = this.getCurrentLevel(multiplier);
        int backButtonSlot = this.plugin.getGUIManager().getBackButtonSlot();
        int[] levelSlots = new int[] { 10, 19, 28, 37, 38, 39, 30, 21, 12, 13, 14, 23, 32, 41, 42, 43, 34, 25, 16, 7 };
        int i2 = 0;
        while (i2 < Math.min(levelSlots.length, levelPrices.size())) {
            int slot = levelSlots[i2];
            double requiredProgress = levelPrices.get(i2);
            String status = i2 < currentLevel ? "COMPLETE" : (i2 == currentLevel ? "WORKING" : "INCOMPLETE");
            ItemStack levelIndicator = this.createLevelIndicator(status, i2, progress, multiplier, requiredProgress);
            inv.setItem(slot, levelIndicator);
            ++i2;
        }
        inv.setItem(backButtonSlot, this.createBackButton());
        this.openInventories.put(player.getUniqueId(), title);
        player.openInventory(inv);
    }

    private ItemStack createLevelIndicator(String status, int level, double progress, double multiplier,
            double requiredProgress) {
        Material material = this.plugin.getGUIManager().getProgressItemMaterial(status);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = this.plugin.getGUIManager().getProgressItemName(status);
        meta.setDisplayName(MessageUtil.colorize(displayName));
        double percentage = 0.0;
        int currentLevel = this.getCurrentLevel(multiplier);
        if (level < currentLevel) {
            percentage = 100.0;
        } else if (level == currentLevel) {
            percentage = Math.min(progress / requiredProgress * 100.0, 100.0);
        }
        ArrayList<String> lore = new ArrayList<String>();
        List<String> configLore = this.plugin.getGUIManager().getProgressItemLore(status);
        String progressBar = this.plugin.getGUIManager().buildProgressBar(percentage);
        double baseMultiplier = this.plugin.getConfig().getDouble("settings.base-multiplier", 1.0);
        double multiplierIncrement = this.plugin.getConfig().getDouble("settings.multiplier-increment", 0.1);
        double levelMultiplier = baseMultiplier + (double) (level + 1) * multiplierIncrement;
        for (String line : configLore) {
            line = line.replace("%progress-bar%", progressBar);
            line = line.replace("%multiplier%", String.format("%.1f", levelMultiplier));
            line = line.replace("%progress%", String.format("%.0f", percentage));
            line = level == currentLevel ? line.replace("%current-spent%", MessageUtil.formatMoney(progress))
                    : (level < currentLevel ? line.replace("%current-spent%", MessageUtil.formatMoney(requiredProgress))
                            : line.replace("%current-spent%", "0"));
            line = line.replace("%needing-spent%", MessageUtil.formatMoney(requiredProgress));
            lore.add(MessageUtil.colorize(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCategoryIcon(Category category, double multiplier, double progress) {
        Material material = this.plugin.getGUIManager().getProgressIconMaterial(category.getKey());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = this.plugin.getGUIManager().getProgressIconName(category.getKey());
        meta.setDisplayName(MessageUtil.colorize(displayName));
        ArrayList<String> lore = new ArrayList<String>();
        List<String> configLore = this.plugin.getGUIManager().getProgressIconLore(category.getKey());
        for (String line : configLore) {
            lore.add(MessageUtil.colorize(line));
        }
        meta.setLore(lore);
        for (ItemFlag flag : this.plugin.getGUIManager().getProgressIconFlags(category.getKey())) {
            meta.addItemFlags(new ItemFlag[] { flag });
        }
        int customModelData = this.plugin.getGUIManager().getProgressIconCustomModelData(category.getKey());
        if (customModelData > 0) {
            meta.setCustomModelData(Integer.valueOf(customModelData));
        }
        for (String enchantStr : this.plugin.getGUIManager().getProgressIconEnchantments(category.getKey())) {
            String[] parts = enchantStr.split(":");
            if (parts.length != 2)
                continue;
            try {
                Enchantment ench = Enchantment.getByName((String) parts[0]);
                int level = Integer.parseInt(parts[1]);
                if (ench == null)
                    continue;
                meta.addEnchant(ench, level, true);
            } catch (NumberFormatException numberFormatException) {
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private double getRequiredProgressForLevel(double currentMultiplier) {
        List levelPrices;
        int currentLevel = this.getCurrentLevel(currentMultiplier);
        if (currentLevel < (levelPrices = this.plugin.getConfig().getDoubleList("level-prices")).size()) {
            return (Double) levelPrices.get(currentLevel);
        }
        return levelPrices.isEmpty() ? 1000000.0 : (Double) levelPrices.get(levelPrices.size() - 1);
    }

    private int getCurrentLevel(double multiplier) {
        double baseMultiplier = this.plugin.getConfig().getDouble("settings.base-multiplier", 1.0);
        double multiplierIncrement = this.plugin.getConfig().getDouble("settings.multiplier-increment", 0.1);
        int level = (int) Math.round((multiplier - baseMultiplier) / multiplierIncrement);
        return Math.max(0, level);
    }

    private ItemStack createBackButton() {
        Material material = this.plugin.getGUIManager().getBackButtonMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = this.plugin.getGUIManager().getBackButtonName();
        meta.setDisplayName(MessageUtil.colorize(name));
        ArrayList<String> lore = new ArrayList<String>();
        List<String> configLore = this.plugin.getGUIManager().getBackButtonLore();
        for (String line : configLore) {
            lore.add(MessageUtil.colorize(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack clickedItem;
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        if (!this.openInventories.containsKey(player.getUniqueId())) {
            return;
        }
        if (!this.openInventories.get(player.getUniqueId()).equals(title)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 54 && (clickedItem = event.getCurrentItem()) != null && clickedItem.getType() != Material.AIR) {
            player.playSound(player.getLocation(), this.plugin.getSound("button-click", Sound.UI_BUTTON_CLICK), 1.0f, 1.0f);
        }
        int backButtonSlot = this.plugin.getGUIManager().getBackButtonSlot();
        if (event.getRawSlot() == backButtonSlot) {
            this.plugin.getSellGUI().openGUI(player);
            this.openInventories.remove(player.getUniqueId());
        }
    }
}
