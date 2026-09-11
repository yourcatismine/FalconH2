package com.falconcore.survival.orders.store;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.falconcore.survival.orders.Utils;
import com.falconcore.survival.orders.data.SortType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class ConfigManager {
    private final Plugin plugin;
    private FileConfiguration cfg;
    private FileConfiguration messagesConfig;
    private final Set<String> disabledTokens = new HashSet<>();
    private final Set<String> lockedWorlds = new HashSet<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;

        File ordersDir = new File(plugin.getDataFolder(), "economy/orders");
        if (!ordersDir.exists()) {
            ordersDir.mkdirs();
        }

        File configFile = new File(ordersDir, "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("economy/orders/config.yml", false);
        }
        this.cfg = YamlConfiguration.loadConfiguration(configFile);
        this.loadMessagesConfig();

        this.loadDisabled();
        this.loadLockedWorlds();
    }

    public void reload() {
        File configFile = new File(plugin.getDataFolder(), "economy/orders/config.yml");
        this.cfg = YamlConfiguration.loadConfiguration(configFile);
        this.loadMessagesConfig();
        this.loadDisabled();
        this.loadLockedWorlds();
    }

    private void loadMessagesConfig() {
        File messagesFile = new File(plugin.getDataFolder(), "messages/economy/order.yml");
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            plugin.saveResource("messages/economy/order.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        if (this.cfg != null && this.messagesConfig != null) {
            this.cfg.setDefaults(this.messagesConfig);
        }
    }

    public FileConfiguration getMessagesConfig() {
        return this.messagesConfig;
    }

    private void loadDisabled() {
        this.disabledTokens.clear();
        List<String> list = this.cfg.getStringList("disabled-items");
        if (list != null) {
            for (String s : list) {
                this.disabledTokens.add(s.trim().toUpperCase());
            }
        }
        if (this.disabledTokens.isEmpty()) {
            this.disabledTokens.add("SPAWNER");
            this.disabledTokens.add("SPAWNER_EGGS");
        }
    }

    private void loadLockedWorlds() {
        this.lockedWorlds.clear();
        List<String> list = this.cfg.getStringList("droplootlocked");
        if (list != null) {
            for (String s : list) {
                this.lockedWorlds.add(s.trim().toLowerCase());
            }
        }
    }

    public boolean isWorldLocked(String worldName) {
        if (worldName == null)
            return false;
        return this.lockedWorlds.contains(worldName.toLowerCase());
    }

    public boolean isDisabled(Material m) {
        if (m == null)
            return false;
        return this.disabledTokens.contains(m.name());
    }

    public FileConfiguration cfg() {
        return this.cfg;
    }

    public String msg(String path, String def) {
        return Utils.formatColors(this.cfg.getString(path, def));
    }

    public int rows(String path, int def) {
        return this.cfg.getInt(path, def);
    }

    public String title(String path, String def) {
        return Utils.formatColors(this.cfg.getString(path, def));
    }

    public int slot(String path, int def) {
        return this.cfg.getInt(path, def);
    }

    public ItemStack button(String path, String defMat, String defName, List<?> defLoreLines) {
        String name = Utils.formatColors(this.cfg.getString(path + ".displayname", defName));
        String matName = this.cfg.getString(path + ".material", defMat);
        Material m = Material.matchMaterial((String) matName);
        if (m == null) {
            m = Material.ARROW;
        }
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = this.cfg.getStringList(path + ".lore");
            if (lore.isEmpty() && defLoreLines != null && !defLoreLines.isEmpty()) {
                lore = new ArrayList<>();
                for (Object o : defLoreLines)
                    lore.add(o.toString());
            }
            if (lore.isEmpty()) {
                lore = new ArrayList<>();
            }
            meta.setLore(Utils.formatColors(lore));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void play(Player p, String path, String defSound, float vol, float pitch) {
        if (p == null) {
            return;
        }
        String s = this.cfg.getString(path + ".sound", defSound);
        if (s == null || s.equalsIgnoreCase("NONE")) {
            return;
        }
        try {
            Sound sound = Sound.valueOf((String) s.toUpperCase());
            if (sound != null) {
                p.playSound(p.getLocation(), sound, vol, pitch);
            }
        } catch (IllegalArgumentException | NullPointerException illegalArgumentException) {
        }
    }

    public void message(Player p, String msg) {
        if (p != null && msg != null) {
            p.sendMessage(Utils.formatColors(msg));
        }
    }

    public String sortName(SortType t) {
        String k = "sort-names." + t.name();
        String def = switch (t) {
            case MOST_PAID -> "Most Paid";
            case MOST_DELIVERED -> "Most Delivered";
            case RECENTLY_LISTED -> "Recently Listed";
            case MOST_MONEY_PER_ITEM -> "Most Money Per Item";
            default -> throw new IllegalStateException("Unexpected value: " + t);
        };
        return Utils.formatColors(this.cfg.getString(k, def));
    }

    public String selectedPrefix(String gui) {
        return Utils.formatColors(this.cfg.getString("gui." + gui + ".format.selected_prefix", "&a• "));
    }

    public String unselectedPrefix(String gui) {
        return Utils.formatColors(this.cfg.getString("gui." + gui + ".format.unselected_prefix", "&f• "));
    }

    public ItemStack dynamicItem(Material mat, String path, String defName, List<String> defLore,
            Map<String, String> placeholders) {
        String name = Utils.formatColors(this.cfg.getString(path + ".displayname", defName));
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = this.cfg.getStringList(path + ".lore");
            if (lore.isEmpty() && defLore != null) {
                lore = new ArrayList<String>(defLore);
            }
            if (placeholders != null) {
                ArrayList<String> finalLore = new ArrayList<String>();
                for (String line : lore) {
                    String processed = line;
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
                    }
                    finalLore.add(processed);
                }
                lore = finalLore;
            }
            meta.setLore(Utils.formatColors(lore));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
