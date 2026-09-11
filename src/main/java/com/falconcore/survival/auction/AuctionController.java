package com.falconcore.survival.auction;

import java.io.File;
import java.io.IOException;
import com.h2ph.Falcon;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

public class AuctionController {
    private final Falcon plugin;
    private AuctionManager auctionManager;
    private TransactionManager transactionManager;
    private GUIListener guiListener;
    private File storageFile;
    private FileConfiguration storageConfig;
    private File filterFile;
    private FileConfiguration filterConfig;
    private File configFile;
    private FileConfiguration config;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public AuctionController(Falcon plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.loadConfig();
        this.loadMessagesConfig();
        this.setupFilterFile();
        this.auctionManager = new AuctionManager(this);
        this.transactionManager = new TransactionManager(this);
        boolean useVault = this.config.getBoolean("settings.use-vault", true);
        EconomyHandler.setup(plugin, useVault);
        this.setupStorageFile();
        this.auctionManager.loadFromConfig();
        this.transactionManager.loadFromConfig();
        AHCommand ahCmd = new AHCommand(this);
        plugin.getCommand("ah").setExecutor(ahCmd);
        plugin.getCommand("ah").setTabCompleter(ahCmd);
        AdminCommand adminCmd = new AdminCommand(this);
        if (plugin.getCommand("auction") != null) {
            plugin.getCommand("auction").setExecutor((CommandExecutor) adminCmd);
            plugin.getCommand("auction").setTabCompleter((TabCompleter) adminCmd);
        } else {
            plugin.getLogger().severe("Command 'auction' not found in plugin.yml!");
        }
        ProfileCommand profileCmd = new ProfileCommand(this);
        if (plugin.getCommand("profile") != null) {
            plugin.getCommand("profile").setExecutor((CommandExecutor) profileCmd);
            plugin.getCommand("profile").setTabCompleter((TabCompleter) profileCmd);
        } else {
            plugin.getLogger().severe("Command 'profile' not found in plugin.yml!");
        }
        this.guiListener = new GUIListener(this);
        plugin.getServer().getPluginManager().registerEvents((Listener) this.guiListener, plugin);
        ProfileGUIListener profileGUIListener = new ProfileGUIListener(plugin);
        plugin.getServer().getPluginManager().registerEvents((Listener) profileGUIListener, plugin);
        this.startAutoSaveTask();
        plugin.getLogger().info("Auction system enabled.");
    }

    public void disable() {
        this.saveAllData();
        plugin.getLogger().info("Auction system disabled.");
    }

    private void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), "economy/auction/config.yml");
        if (!this.configFile.exists()) {
            this.configFile.getParentFile().mkdirs();
            plugin.saveResource("economy/auction/config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
    }

    public void loadMessagesConfig() {
        this.messagesFile = new File(plugin.getDataFolder(), "messages/economy/auction.yml");
        if (!this.messagesFile.exists()) {
            this.messagesFile.getParentFile().mkdirs();
            plugin.saveResource("messages/economy/auction.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(this.messagesFile);
        if (this.config != null && this.messagesConfig != null) {
            this.config.setDefaults(this.messagesConfig);
        }
    }

    public FileConfiguration getMessagesConfig() {
        if (this.messagesConfig == null) {
            loadMessagesConfig();
        }
        return this.messagesConfig;
    }

    public String getMessage(String path, String def) {
        FileConfiguration msgCfg = getMessagesConfig();
        if (msgCfg != null && msgCfg.contains("messages." + path)) {
            return msgCfg.getString("messages." + path);
        }
        FileConfiguration mainCfg = getConfig();
        if (mainCfg != null && mainCfg.contains("messages." + path)) {
            return mainCfg.getString("messages." + path);
        }
        return def;
    }

    public String getMessage(String path) {
        return getMessage(path, "");
    }

    public String getSoundName(String path, String def) {
        FileConfiguration msgCfg = getMessagesConfig();
        if (msgCfg != null && msgCfg.contains("sounds." + path)) {
            return msgCfg.getString("sounds." + path);
        }
        FileConfiguration mainCfg = getConfig();
        if (mainCfg != null && mainCfg.contains("sounds." + path)) {
            return mainCfg.getString("sounds." + path);
        }
        return def;
    }

    public FileConfiguration getConfig() {
        if (this.config == null) {
            loadConfig();
        }
        return this.config;
    }

    public void reloadConfig() {
        loadConfig();
        loadMessagesConfig();
    }

    private void setupStorageFile() {
        this.storageFile = new File(plugin.getDataFolder(), "economy/auction/storage.yml");
        if (this.storageFile.exists()) {
            this.storageConfig = YamlConfiguration.loadConfiguration(this.storageFile);
        } else {
            this.storageConfig = new YamlConfiguration();
        }
    }

    private void setupFilterFile() {
        this.filterFile = new File(plugin.getDataFolder(), "economy/auction/filter.yml");
        if (!this.filterFile.exists()) {
            this.filterFile.getParentFile().mkdirs();
            plugin.saveResource("economy/auction/filter.yml", false);
        }
        this.filterConfig = YamlConfiguration.loadConfiguration(this.filterFile);
    }

    public void reloadAllConfigs() {
        this.reloadConfig();
        this.loadMessagesConfig();
        this.setupFilterFile();
    }

    public FileConfiguration getFilterConfig() {
        return this.filterConfig;
    }

    public AuctionManager getAuctionManager() {
        return this.auctionManager;
    }

    public TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    public FileConfiguration getStorageConfig() {
        return this.storageConfig;
    }

    public void saveStorageFile() {
        if (this.storageConfig == null || this.storageFile == null) {
            return;
        }
        try {
            this.storageConfig.save(this.storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save storage.yml: " + e.getMessage());
        }
    }

    public void startAutoSaveTask() {
        plugin.getSchedulerAdapter().runTaskTimerAsync(() -> {
            this.saveAllData();
        }, 12000L, 12000L);
    }

    private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> activeTasks = new java.util.concurrent.ConcurrentHashMap<>();

    public void startUpdateTask(org.bukkit.entity.Player player) {
        if (activeTasks.containsKey(player.getUniqueId()))
            return;

        org.bukkit.NamespacedKey auctionKey = new org.bukkit.NamespacedKey(plugin, "auction-expire");
        org.bukkit.NamespacedKey txKey = new org.bukkit.NamespacedKey(plugin, "transaction-timestamp");

        org.bukkit.scheduler.BukkitTask task = plugin.getSchedulerAdapter().runEntityTaskTimer(player, () -> {
            if (!player.isOnline()) {
                stopUpdateTask(player);
                return;
            }
            org.bukkit.inventory.InventoryView view = player.getOpenInventory();
            if (view == null)
                return;
            org.bukkit.inventory.Inventory top = view.getTopInventory();
            org.bukkit.inventory.InventoryHolder holder = top.getHolder();

            if (!(holder instanceof GUIHandler.MainHolder
                    || holder instanceof GUIHandler.YourItemsHolder
                    || holder instanceof GUIHandler.TransactionsHolder
                    || holder instanceof GUIHandler.AdminPlayerDetailsHolder
                    || holder instanceof GUIHandler.AdminTransactionsHolder)) {
                return;
            }

            for (int i = 0; i < top.getSize(); i++) {
                org.bukkit.inventory.ItemStack item = top.getItem(i);
                if (item == null || !item.hasItemMeta())
                    continue;
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();

                boolean changed = false;

                if (pdc.has(auctionKey, org.bukkit.persistence.PersistentDataType.LONG)) {
                    long expireTime = pdc.get(auctionKey, org.bukkit.persistence.PersistentDataType.LONG);
                    long remaining = expireTime - System.currentTimeMillis();

                    if (remaining <= 0) {
                        if (holder instanceof GUIHandler.MainHolder
                                || holder instanceof GUIHandler.AdminPlayerDetailsHolder) {
                            top.setItem(i, null);
                            continue;
                        }
                    }

                    String timeStr = remaining <= 0 ? "&#ff4444Expired"
                            : FormatUtils.formatTime((int) (remaining / 1000));
                    if (meta.hasLore()) {
                        java.util.List<String> lore = new java.util.ArrayList<>(meta.getLore());
                        for (int j = 0; j < lore.size(); ++j) {
                            String line = lore.get(j);
                            String plainLine = org.bukkit.ChatColor.stripColor(line);
                            if (plainLine.contains("Time left:")) {
                                String newLine = Utils
                                        .formatColors("&fTime left: " + (remaining <= 0 ? "" : "&#34ee80") + timeStr);
                                lore.set(j, newLine);
                                changed = true;
                                break;
                            }
                        }
                        if (changed) {
                            meta.setLore(lore);
                        }
                    }
                }

                if (pdc.has(com.falconcore.survival.tools.ToolsManager.EXPIRY_KEY, org.bukkit.persistence.PersistentDataType.LONG)) {
                    long toolExpiry = pdc.get(com.falconcore.survival.tools.ToolsManager.EXPIRY_KEY, org.bukkit.persistence.PersistentDataType.LONG);
                    long toolRemain = (toolExpiry - System.currentTimeMillis()) / 1000L;
                    String toolTimeStr = toolRemain <= 0 ? "Expired" : com.falconcore.survival.tools.Utils.formatDuration(toolRemain);
                    
                    if (meta.hasLore()) {
                        java.util.List<String> lore = new java.util.ArrayList<>(meta.getLore());
                        boolean toolChanged = false;
                        for (int j = 0; j < lore.size(); ++j) {
                            String line = lore.get(j);
                            String plainLine = org.bukkit.ChatColor.stripColor(line);
                            
                            if (!plainLine.contains("Time left:")) {
                                if (plainLine.matches(".*\\d+d \\d+h \\d+m \\d+s.*")) {
                                    
                                    java.util.regex.Pattern durationPattern = java.util.regex.Pattern.compile("\\d+d \\d+h \\d+m \\d+s");
                                    java.util.regex.Matcher matcher = durationPattern.matcher(plainLine);
                                    
                                    if (matcher.find()) {
                                        String oldDuration = matcher.group();
                                        String newDuration = toolRemain <= 0 ? "Expired" : toolTimeStr;
                                        
                                        String updatedLine = line.replace(oldDuration, newDuration);
                                        
                                        lore.set(j, updatedLine);
                                        toolChanged = true;
                                        break;
                                    }
                                } else if (plainLine.contains("Expired") && toolRemain > 0) {
                                    String updatedLine = line.replaceAll("(?i)expired", toolTimeStr);
                                    lore.set(j, updatedLine);
                                    toolChanged = true;
                                    break;
                                }
                            }
                        }
                        if (toolChanged) {
                            meta.setLore(lore);
                            changed = true;
                        }
                    }
                } else if (pdc.has(txKey, org.bukkit.persistence.PersistentDataType.LONG)) {
                    long soldTime = pdc.get(txKey, org.bukkit.persistence.PersistentDataType.LONG);
                    long elapsedSeconds = (System.currentTimeMillis() - soldTime) / 1000L;
                    String timeAgo = FormatUtils.formatTime((int) elapsedSeconds);
                    if (meta.hasLore()) {
                        java.util.List<String> lore = meta.getLore();
                        for (int j = 0; j < lore.size(); ++j) {
                            String check = lore.get(j);
                            if (check.contains("Sold:")) {
                                String newLine = Utils.formatColors("&fSold: &7" + timeAgo + " ago");
                                if (!check.equals(newLine)) {
                                    lore.set(j, newLine);
                                    changed = true;
                                }
                            }
                            else if (check.endsWith(" ago")) {
                                String newLine = Utils.formatColors("&a" + timeAgo + " ago");
                                if (!check.equals(newLine)) {
                                    lore.set(j, newLine);
                                    changed = true;
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    item.setItemMeta(meta);
                    top.setItem(i, item);
                }
            }
        }, 20L, 20L);

        activeTasks.put(player.getUniqueId(), task);
    }

    public void stopUpdateTask(org.bukkit.entity.Player player) {
        org.bukkit.scheduler.BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void saveAllData() {
        if (this.auctionManager != null) {
            this.auctionManager.saveToConfig();
        }
        if (this.transactionManager != null) {
            this.transactionManager.saveToConfig();
        }
        this.saveStorageFile();
    }

    public Falcon getPlugin() {
        return plugin;
    }
}
