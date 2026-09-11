/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.Sound
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package com.falconcore.survival.sell.gui;

import com.falconcore.survival.sell.FalconSell;
import com.falconcore.survival.sell.category.Category;
import com.falconcore.survival.sell.data.PlayerData;
import com.falconcore.survival.sell.utils.MessageUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.HashMap;

public class SellGUI
        implements Listener {
    private final FalconSell plugin;
    private final String guiTitle;

    public SellGUI(FalconSell plugin) {
        this.plugin = plugin;
        this.guiTitle = MessageUtil.colorize(plugin.getGUIManager().getSellGUITitle());
    }

    public void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory((InventoryHolder) null, (int) 54, (String) this.guiTitle);

        for (Category category : Category.values()) {
            int slot = this.plugin.getGUIManager().getCategorySlot(category.getKey());
            if (slot >= 0 && slot < 54) {
                inv.setItem(slot, this.createCategoryIcon(player, category));
            }
        }

        player.openInventory(inv);
    }

    private ItemStack createCategoryIcon(Player player, Category category) {
        Material material = this.plugin.getGUIManager().getCategoryIconMaterial(category.getKey());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = this.plugin.getGUIManager().getCategoryIconName(category.getKey());
            meta.setDisplayName(MessageUtil.colorize(name));

            PlayerData data = this.plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            double progress = data.getProgress(category);
            double multiplier = data.getMultiplier(category);

            double requiredProgress = this.getRequiredProgressForLevel(multiplier);

            double percentage = 0.0;
            if (requiredProgress > 0) {
                percentage = Math.min(progress / requiredProgress * 100.0, 100.0);
            } else {
                percentage = 100.0;
            }

            String progressBar = this.plugin.getGUIManager().buildProgressBar(percentage);

            List<String> lore = this.plugin.getGUIManager().getCategoryIconLore(category.getKey());
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                line = line.replace("%progress-bar%", progressBar);
                line = line.replace("%multiplier%", String.format("%.1f", multiplier));
                line = line.replace("%progress%", String.format("%.0f", percentage));
                coloredLore.add(MessageUtil.colorize(line));
            }
            meta.setLore(coloredLore);

            for (ItemFlag flag : this.plugin.getGUIManager().getCategoryIconFlags(category.getKey())) {
                meta.addItemFlags(flag);
            }

            int modelData = this.plugin.getGUIManager().getCategoryIconCustomModelData(category.getKey());
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }

            List<String> enchants = this.plugin.getGUIManager().getCategoryIconEnchantments(category.getKey());
            for (String enchantStr : enchants) {
                String[] parts = enchantStr.split(":");
                if (parts.length == 2) {
                    try {
                        Enchantment ench = Enchantment.getByName(parts[0]);
                        int level = Integer.parseInt(parts[1]);
                        if (ench != null) {
                            meta.addEnchant(ench, level, true);
                        }
                    } catch (Exception e) {
                    }
                }
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private double getRequiredProgressForLevel(double currentMultiplier) {
        List<Double> levelPrices = this.plugin.getConfig().getDoubleList("level-prices");
        int currentLevel = this.getCurrentLevel(currentMultiplier);
        if (currentLevel < levelPrices.size()) {
            return levelPrices.get(currentLevel);
        }
        return levelPrices.isEmpty() ? 1000000.0 : levelPrices.get(levelPrices.size() - 1);
    }

    private int getCurrentLevel(double multiplier) {
        double baseMultiplier = this.plugin.getConfig().getDouble("settings.base-multiplier", 1.0);
        double multiplierIncrement = this.plugin.getConfig().getDouble("settings.multiplier-increment", 0.1);
        int level = (int) Math.round((multiplier - baseMultiplier) / multiplierIncrement);
        return Math.max(0, level);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!event.getView().getTitle().equals(this.guiTitle)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot >= 0 && slot < 54) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                for (Category category : Category.values()) {
                    if (this.plugin.getGUIManager().getCategorySlot(category.getKey()) == slot) {
                        event.setCancelled(true);
                        player.playSound(player.getLocation(), this.plugin.getSound("button-click", Sound.UI_BUTTON_CLICK), 1.0f, 1.0f);
                        this.plugin.getProgressGUI().openCategoryGUI(player, category);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        if (!event.getView().getTitle().equals(this.guiTitle)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        double totalSold = 0.0;
        Map<Category, Double> categoryProgressToAdd = new HashMap<>();
        List<ItemStack> unsoldItems = new ArrayList<>();
        Map<String, double[]> soldItemsToAdd = new HashMap<>();

        for (int i = 0; i < 54; i++) {
            boolean isCategorySlot = false;
            for (Category category : Category.values()) {
                if (this.plugin.getGUIManager().getCategorySlot(category.getKey()) == i) {
                    isCategorySlot = true;
                    break;
                }
            }
            if (isCategorySlot)
                continue;

            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR)
                continue;

            double itemTotal = 0.0;
            boolean contentSold = false;

            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta) {
                org.bukkit.inventory.meta.BlockStateMeta bsm = (org.bukkit.inventory.meta.BlockStateMeta) item
                        .getItemMeta();
                if (bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox) {
                    org.bukkit.block.ShulkerBox shulker = (org.bukkit.block.ShulkerBox) bsm.getBlockState();
                    ItemStack[] contents = shulker.getInventory().getContents();
                    boolean shulkerModified = false;

                    for (int j = 0; j < contents.length; j++) {
                        ItemStack content = contents[j];
                        if (content == null || content.getType() == Material.AIR)
                            continue;

                        double price = this.plugin.getPricesManager().getPrice(content);
                        if (price > 0) {
                            Category category = this.plugin.getPricesManager().getCategory(content);
                            double amount = price * content.getAmount();

                            if (category != null) {
                                PlayerData data = this.plugin.getPlayerDataManager()
                                        .getPlayerData(player.getUniqueId());
                                double multiplier = data.getMultiplier(category);
                                amount *= multiplier;

                                categoryProgressToAdd.put(category,
                                        categoryProgressToAdd.getOrDefault(category, 0.0) + amount);
                            }

                            itemTotal += amount * item.getAmount();

                            String matName = content.getType().name();
                            double[] stats = soldItemsToAdd.computeIfAbsent(matName, k -> new double[] { 0, 0 });
                            stats[0] += content.getAmount() * item.getAmount();
                            stats[1] += amount * item.getAmount();

                            contents[j] = null;
                            shulkerModified = true;
                            contentSold = true;
                        }
                    }

                    if (shulkerModified) {
                        shulker.getInventory().setContents(contents);
                        bsm.setBlockState(shulker);
                        item.setItemMeta(bsm);
                    }
                }
            }

            double selfPrice = this.plugin.getPricesManager().getPrice(item);
            if (selfPrice > 0) {
                Category category = this.plugin.getPricesManager().getCategory(item);
                double amount = selfPrice * item.getAmount();

                if (category != null) {
                    PlayerData data = this.plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    double multiplier = data.getMultiplier(category);
                    amount *= multiplier;

                    categoryProgressToAdd.put(category, categoryProgressToAdd.getOrDefault(category, 0.0) + amount);
                }

                itemTotal += amount;

                String matName = item.getType().name();
                double[] stats = soldItemsToAdd.computeIfAbsent(matName, k -> new double[] { 0, 0 });
                stats[0] += item.getAmount();
                stats[1] += amount;
            }

            if (itemTotal > 0) {
                totalSold += itemTotal;

                if (selfPrice <= 0) {
                    unsoldItems.add(item);
                }
            } else {
                unsoldItems.add(item);
            }
        }

        if (totalSold > 0) {
            this.plugin.getEconomy().deposit(player.getUniqueId(), totalSold);

            if (!soldItemsToAdd.isEmpty()) {
                this.plugin.getDatabaseManager().saveSellHistoryAsync(player.getUniqueId(), soldItemsToAdd);
            }

            PlayerData sellData = this.plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            sellData.setSellMade(sellData.getSellMade() + totalSold);
            this.plugin.getPlugin().getPlayerDataManager().invalidateSellLeaderboard();
            this.plugin.getPlayerDataManager().savePlayerDataAsync(player.getUniqueId());

            String formattedAmount = MessageUtil.formatMoney(totalSold);
            String chatMsg = this.plugin.getMessage("sold-total", "#00f900+$%amount%");
            if (chatMsg != null && !chatMsg.isEmpty()) {
                player.sendMessage(MessageUtil.colorize(chatMsg.replace("%amount%", formattedAmount)));
            }

            String actionBarMsg = this.plugin.getMessage("sold-total-action-bar", "#00f900+$%amount%");
            if (actionBarMsg != null && !actionBarMsg.isEmpty()) {
                MessageUtil.sendActionBar(player, actionBarMsg.replace("%amount%", formattedAmount));
            }

            player.playSound(player.getLocation(), this.plugin.getSound("sell-success", Sound.ENTITY_EXPERIENCE_ORB_PICKUP), 1.0f, 2.0f);

            PlayerData data = this.plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            for (Map.Entry<Category, Double> entry : categoryProgressToAdd.entrySet()) {
                data.addProgress(entry.getKey(), entry.getValue());
                this.checkLevelUp(player, entry.getKey(), data);
            }

            com.falconcore.survival.manager.PlayerData mainPD = this.plugin.getPlugin().getPlayerDataManager()
                    .get(player.getUniqueId());
            if (mainPD != null) {
                String dateTime = LocalDateTime.now(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
                mainPD.addHistory(dateTime + " - Shop Sale\nSold items for $" + String.format("%.2f", totalSold));
                this.plugin.getPlugin().getPlayerDataManager().savePlayerAsync(player.getUniqueId());
            }
        }

        for (ItemStack item : unsoldItems) {
            HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
            if (!leftOver.isEmpty()) {
                for (ItemStack drop : leftOver.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                player.sendMessage(MessageUtil.colorize(this.plugin.getMessage("inventory-full-dropped", "&cInventory full! Some items were dropped.")));
            }
        }
    }

    private void checkLevelUp(Player player, Category category, PlayerData data) {
        List<Double> levelPrices = this.plugin.getConfig().getDoubleList("level-prices");
        double multiplierIncrement = this.plugin.getConfig().getDouble("settings.multiplier-increment", 0.1);

        while (true) {
            double currentMultiplier = data.getMultiplier(category);
            int currentLevel = this.getCurrentLevel(currentMultiplier);

            if (currentLevel >= levelPrices.size()) {
                return;
            }

            double required = levelPrices.get(currentLevel);
            double progress = data.getProgress(category);

            if (progress >= required) {
                double newMultiplier = currentMultiplier + multiplierIncrement;
                data.setMultiplier(category, newMultiplier);
            } else {
                break;
            }
        }
    }
}
