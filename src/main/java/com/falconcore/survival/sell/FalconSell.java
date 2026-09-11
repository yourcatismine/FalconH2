package com.falconcore.survival.sell;

import com.h2ph.Falcon;
import com.falconcore.survival.sell.commands.SellCommand;
import com.falconcore.survival.sell.data.PlayerDataManagerDB;
import com.falconcore.survival.sell.database.DatabaseManager;
import com.falconcore.survival.sell.economy.FalconDirectEconomyProvider;
import com.falconcore.survival.sell.economy.SellEconomyProvider;
import com.falconcore.survival.sell.economy.VaultEconomyProvider;
import com.falconcore.survival.sell.gui.ProgressGUI;
import com.falconcore.survival.sell.gui.SellGUI;
import com.falconcore.survival.sell.managers.GUIManager;
import com.falconcore.survival.sell.managers.PricesManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;

public class FalconSell {

    private final Falcon plugin;
    private SellEconomyProvider economy;
    private DatabaseManager databaseManager;
    private PlayerDataManagerDB playerDataManager;
    private PricesManager pricesManager;
    private GUIManager guiManager;
    private SellGUI sellGUI;
    private ProgressGUI progressGUI;

    private File configFile;
    private FileConfiguration config;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public FalconSell(Falcon plugin) {
        this.plugin = plugin;
    }

    public java.util.logging.Logger getLogger() {
        return plugin.getLogger();
    }

    public org.bukkit.Server getServer() {
        return plugin.getServer();
    }

    public java.io.InputStream getResource(String filename) {
        return plugin.getResource(filename);
    }

    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "economy/sell/config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        java.io.InputStream defConfigStream = plugin.getResource("economy/sell/config.yml");
        if (defConfigStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defConfigStream, java.nio.charset.StandardCharsets.UTF_8)));
        }

        reloadMessagesConfig();

        if (this.pricesManager != null) {
            this.pricesManager.loadPrices();
        }
        if (this.guiManager != null) {
            this.guiManager.reload();
        }
        if (plugin.getInventoryWorthManager() != null) {
            plugin.getInventoryWorthManager().reloadConfig();
        }
    }

    public void reloadMessagesConfig() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages/economy/sell.yml");
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages/economy/sell.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            reloadMessagesConfig();
        }
        return messagesConfig;
    }

    public String getMessage(String path, String def) {
        if (getMessagesConfig() == null)
            return def;
        return getMessagesConfig().getString("messages." + path, def);
    }

    public org.bukkit.Sound getSound(String key, org.bukkit.Sound def) {
        if (getMessagesConfig() == null)
            return def;
        String soundName = getMessagesConfig().getString("sounds." + key);
        if (soundName == null || soundName.isEmpty())
            return def;
        try {
            return org.bukkit.Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "economy/sell/config.yml");
        }
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try (java.io.InputStream in = plugin.getResource("economy/sell/config.yml")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, configFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    plugin.getLogger().warning("Could not find economy/sell/config.yml resource!");
                }
            } catch (java.io.IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save config to " + configFile, e);
            }
        }
        reloadMessagesConfig();
    }

    public void saveConfig() {
        if (config == null || configFile == null) {
            return;
        }
        try {
            getConfig().save(configFile);
        } catch (java.io.IOException ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    public void onEnable() {
        this.saveDefaultConfig();
        if (!setupEconomy()) {
            plugin.getLogger().warning("No economy found for FalconSell!");
        }

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        this.pricesManager = new PricesManager(this);
        this.guiManager = new GUIManager(this);
        this.playerDataManager = new PlayerDataManagerDB(this);
        this.sellGUI = new SellGUI(this);
        this.progressGUI = new ProgressGUI(this);

        plugin.getCommand("sell").setExecutor(new SellCommand(this));

        plugin.getServer().getPluginManager().registerEvents(this.sellGUI, plugin);
        plugin.getServer().getPluginManager().registerEvents(this.progressGUI, plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new com.falconcore.survival.sell.listeners.PlayerStatsListener(this), plugin);

        plugin.getLogger().info("FalconSell has been enabled!");
    }

    public void onDisable() {
        if (this.playerDataManager != null && this.databaseManager != null && this.databaseManager.isConnected()) {
            this.playerDataManager.saveAllData();
        }
        if (this.databaseManager != null) {
            this.databaseManager.disconnect();
        }
        plugin.getLogger().info("FalconSell has been disabled!");
    }

    private boolean setupEconomy() {
        File ecoConfig = new File(plugin.getDataFolder(), "economy/config.yml");
        boolean falconEcoEnabled = true;

        if (ecoConfig.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(ecoConfig);
            falconEcoEnabled = config.getBoolean("vault-enabled", true);
        }

        if (falconEcoEnabled) {
            this.economy = new FalconDirectEconomyProvider(this);
            plugin.getLogger().info("FalconSell using Falcon Direct Economy.");
            return true;
        } else {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager()
                    .getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            this.economy = new VaultEconomyProvider(rsp.getProvider());
            plugin.getLogger().info("FalconSell using Vault Economy (" + this.economy.getName() + ").");
            return true;
        }
    }

    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    public Falcon getPlugin() {
        return plugin;
    }

    public SellEconomyProvider getEconomy() {
        return this.economy;
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public PlayerDataManagerDB getPlayerDataManager() {
        return this.playerDataManager;
    }

    public PricesManager getPricesManager() {
        return this.pricesManager;
    }

    public GUIManager getGUIManager() {
        return this.guiManager;
    }

    public SellGUI getSellGUI() {
        return this.sellGUI;
    }

    public ProgressGUI getProgressGUI() {
        return this.progressGUI;
    }
}
