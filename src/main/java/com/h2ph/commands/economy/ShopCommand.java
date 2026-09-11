package com.h2ph.commands.economy;

import com.h2ph.Falcon;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class ShopCommand implements CommandExecutor, Listener {

    private final Falcon plugin;
    private final Map<String, FileConfiguration> categoryConfigs = new HashMap<>();
    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;

    private final Map<Integer, String> mainMenuSlots = new HashMap<>();
    private final Map<UUID, BuyingSession> buyingSessions = new HashMap<>();
    private final Map<UUID, ShardPurchaseSession> shardPurchaseSessions = new HashMap<>();
    private final Map<String, FileConfiguration> titleToConfig = new HashMap<>();
    private final Map<String, Map<Integer, BuyingSession>> sessionLookup = new HashMap<>();
    private final Map<String, Map<Integer, ShardPurchaseSession>> shardSessionLookup = new HashMap<>();

    private String cachedMainTitle;
    private String cachedShopPrefix;
    private String cachedBuyingPrefix;
    private String cachedConfirmTitle;

    public ShopCommand(Falcon plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void reload() {
        loadConfigs();
    }

    private void loadConfigs() {
        categoryConfigs.clear();
        mainMenuSlots.clear();

        File messagesFile = new File(plugin.getDataFolder(), "messages/economy/shop.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages/economy/shop.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        File configFile = new File(plugin.getDataFolder(), "economy/shop/config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("economy/shop/config.yml", false);
        }
        mainConfig = YamlConfiguration.loadConfiguration(configFile);

        File categoriesDir = new File(plugin.getDataFolder(), "economy/shop/categories");
        if (!categoriesDir.exists()) {
            categoriesDir.mkdirs();
        }

        String[] defaultCategories = { "end.yml", "nether.yml", "gear.yml", "food.yml", "shard.yml" };
        for (String categoryFile : defaultCategories) {
            File targetFile = new File(categoriesDir, categoryFile);
            if (!targetFile.exists()) {
                try {
                    plugin.saveResource("economy/shop/categories/" + categoryFile, false);
                } catch (Exception e) {
                }
            }
        }

        File[] categoryFiles = categoriesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (categoryFiles != null) {
            for (File categoryFile : categoryFiles) {
                String fileName = categoryFile.getName();
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(categoryFile);
                categoryConfigs.put(fileName, cfg);
            }
        }

        if (mainConfig.contains("categories")) {
            ConfigurationSection cats = mainConfig.getConfigurationSection("categories");
            for (String key : cats.getKeys(false)) {
                int slot = cats.getInt(key + ".slot");
                String file = cats.getString(key + ".file");
                mainMenuSlots.put(slot, file);
            }
        }

        cachedMainTitle = color(mainConfig.getString("gui-title", "&8ѕʜᴏᴘ"));
        cachedShopPrefix = color("&8ѕʜᴏᴘ - ");
        cachedBuyingPrefix = color("&8ʙᴜʏɪɴɢ");
        cachedConfirmTitle = color("&8ᴄᴏɴꜰɪʀᴍ ᴘᴜʀᴄʜᴀѕᴇ");

        titleToConfig.clear();
        for (FileConfiguration cfg : categoryConfigs.values()) {
            if (cfg.contains("gui-title")) {
                String t = color(cfg.getString("gui-title"));
                titleToConfig.put(t, cfg);
            }
        }

        sessionLookup.clear();
        for (String title : titleToConfig.keySet()) {
            FileConfiguration config = titleToConfig.get(title);
            String fileName = null;
            for (Map.Entry<String, FileConfiguration> entry : categoryConfigs.entrySet()) {
                if (entry.getValue().equals(config)) {
                    fileName = entry.getKey();
                    break;
                }
            }
            if (fileName == null)
                continue;

            Map<Integer, BuyingSession> slots = new HashMap<>();
            ConfigurationSection items = config.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    int slot = items.getInt(key + ".slot");

                    if (items.contains(key + ".shard_price") || items.contains(key + ".command")) {
                        continue;
                    }

                    String matName = items.getString(key + ".material", "STONE");
                    Material mat = Material.getMaterial(matName.toUpperCase());
                    if (mat == null)
                        mat = Material.STONE;

                    ItemStack base = new ItemStack(mat);
                    double price = items.getDouble(key + ".price");
                    List<Integer> values = items.getIntegerList(key + ".values");
                    if (values.isEmpty())
                        values = Arrays.asList(1, 10, 64);

                    List<String> effects = items.getStringList(key + ".effects");
                    int duration = items.getInt(key + ".effect_duration", 30);
                    int level = items.getInt(key + ".effect_level", 1);

                    slots.put(slot, new BuyingSession(base, price, fileName, values, effects, duration, level));
                }
            }
            if (!slots.isEmpty()) {
                sessionLookup.put(title, slots);
            }

            Map<Integer, ShardPurchaseSession> shardSlots = new HashMap<>();
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    int slot = items.getInt(key + ".slot");
                    if (items.contains(key + ".shard_price") || items.contains(key + ".command")) {
                        String matName = items.getString(key + ".material", "STONE");
                        Material mat = Material.getMaterial(matName.toUpperCase());
                        if (mat == null)
                            mat = Material.STONE;

                        String displayName = items.getString(key + ".name", "");
                        String currency = items.getString(key + ".currency", "SHARDS").toUpperCase();
                        double price;

                        if (currency.equals("MONEY")) {
                            price = items.getDouble(key + ".price", 0.0);
                        } else {
                            price = items.getDouble(key + ".shard_price", items.getDouble(key + ".price", 0.0));
                        }

                        String keyType = items.getString(key + ".key_type");
                        String spawnerType = items.getString(key + ".spawner_type");
                        String command = items.getString(key + ".command");
                        List<Integer> values = items.getIntegerList(key + ".values");

                        shardSlots.put(slot, new ShardPurchaseSession(mat, displayName, price, currency, fileName,
                                keyType, spawnerType, command, values));
                    }
                }
            }
            if (!shardSlots.isEmpty()) {
                shardSessionLookup.put(title, shardSlots);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(getMessage("only-players", "&cOnly players can use the shop.")));
            return true;
        }

        openMainMenu(player);
        return true;
    }

    private void openMainMenu(Player player) {
        buyingSessions.remove(player.getUniqueId());
        String title = color(mainConfig.getString("gui-title", "&8ѕʜᴏᴘ"));
        Inventory gui = Bukkit.createInventory(null, 27, title);

        if (mainConfig.contains("categories")) {
            ConfigurationSection cats = mainConfig.getConfigurationSection("categories");
            for (String key : cats.getKeys(false)) {
                int slot = cats.getInt(key + ".slot");
                ItemStack item = createConfigItem(cats.getConfigurationSection(key));
                gui.setItem(slot, item);
            }
        }
        player.openInventory(gui);
    }

    private void openCategory(Player player, String fileName) {
        buyingSessions.remove(player.getUniqueId());
        shardPurchaseSessions.remove(player.getUniqueId());

        FileConfiguration config = categoryConfigs.get(fileName);
        if (config == null) {
            player.sendMessage(color(getMessage("category-not-found", "&cCategory file not found: {file}").replace("{file}", fileName)));
            return;
        }

        String title = color(config.getString("gui-title", "&8ѕʜᴏᴘ"));
        Inventory gui = Bukkit.createInventory(null, 27, title);

        if (config.contains("items")) {
            ConfigurationSection items = config.getConfigurationSection("items");
            for (String key : items.getKeys(false)) {
                int slot = items.getInt(key + ".slot");

                String matName = items.getString(key + ".material", "STONE");
                Material mat = Material.getMaterial(matName.toUpperCase());
                if (mat == null)
                    mat = Material.STONE;

                if (items.contains(key + ".shard_price") || items.contains(key + ".price")) {
                    String itemName = items.getString(key + ".name", "");
                    String currency = items.getString(key + ".currency", "SHARDS").toUpperCase();

                    ItemStack item = new ItemStack(mat, 1);
                    ItemMeta meta = item.getItemMeta();

                    if (!itemName.isEmpty()) {
                        meta.setDisplayName(color(itemName));
                    }

                    List<String> lore = new ArrayList<>();
                    if (currency.equals("MONEY")) {
                        double price = items.getDouble(key + ".price", 0.0);
                        String priceStr = String.format("%,.0f", price);
                        lore.add(color("&fBuy price: &a$" + priceStr));
                    } else {
                        int shardPrice = items.getInt(key + ".shard_price", items.getInt(key + ".price", 0));
                        lore.add(color("&fBuy price: &d" + shardPrice + "x shards"));
                    }
                    meta.setLore(lore);

                    item.setItemMeta(meta);
                    gui.setItem(slot, item);
                } else {
                    double price = items.getDouble(key + ".price", 0.0);
                    int amount = items.getInt(key + ".amount", 1);

                    ItemStack item = new ItemStack(mat, amount);
                    ItemMeta meta = item.getItemMeta();

                    List<String> lore = new ArrayList<>();
                    String priceStr = String.format("%,.0f", price);
                    lore.add(color("&fBuy price: &a$" + priceStr));
                    meta.setLore(lore);

                    item.setItemMeta(meta);
                    gui.setItem(slot, item);
                }
            }
        }

        ItemStack back = createGuiItem(Material.RED_STAINED_GLASS_PANE, "&cʙᴀᴄᴋ", "&fClick to return");
        gui.setItem(18, back);

        player.openInventory(gui);
    }

    private void openBuyingMenu(Player player, BuyingSession session) {
        int space = getSpaceFor(player.getInventory(), session.baseItem);
        if (space <= 0) {
            sendInventoryFull(player);
            return;
        }

        String fancyName;
        if (session.displayName != null && !session.displayName.isEmpty()) {
            fancyName = color(session.displayName);
        } else {
            String rawName = formatName(session.baseItem).toUpperCase();
            fancyName = toSmallCaps(rawName);
        }

        String title = color("&8ʙᴜʏɪɴɢ " + fancyName);
        if (title.length() > 32)
            title = title.substring(0, 32);

        Inventory gui = Bukkit.createInventory(null, 27, title);

        int maxStack = session.baseItem.getMaxStackSize();

        ItemStack displayItem = session.baseItem.clone();
        displayItem.setAmount(Math.min(session.quantity, maxStack));
        ItemMeta meta = displayItem.getItemMeta();

        double total = session.unitPrice * session.quantity;
        String totalStr = String.format("%,.0f", total);

        List<String> lore = new ArrayList<>();
        if (session.currency.equals("SHARDS")) {
            lore.add(color("&fBuy price: &d" + totalStr + "x shards"));
        } else {
            lore.add(color("&fBuy price: &a$" + totalStr));
        }

        meta.setLore(lore);
        if (session.displayName != null && !session.displayName.isEmpty()) {
            meta.setDisplayName(color(session.displayName));
        }
        displayItem.setItemMeta(meta);
        gui.setItem(13, displayItem);

        gui.setItem(21, createGuiItem(Material.RED_STAINED_GLASS_PANE, "&4ᴄᴀɴᴄᴇʟ", "&fClick to cancel"));
        gui.setItem(23, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&aᴄᴏɴꜰɪʀᴍ", "&fClick to buy"));

        List<Integer> values = session.incrementValues;
        int[] addSlots = { 15, 16, 17 };
        int[] remSlots = { 11, 10, 9 };

        for (int i = 0; i < values.size(); i++) {
            if (i >= addSlots.length)
                break;
            int val = values.get(i);

            if (session.quantity < maxStack) {
                gui.setItem(addSlots[i], createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&aAdd " + val, ""));
            }

            boolean canRemoveNormal = (session.quantity - val >= 1);

            boolean canRemoveReset = (session.quantity == maxStack && val == maxStack && maxStack > 1);

            if (canRemoveNormal || canRemoveReset) {
                gui.setItem(remSlots[i], createGuiItem(Material.RED_STAINED_GLASS_PANE, "&cRemove " + val, ""));
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(cachedMainTitle) ||
                title.startsWith(cachedShopPrefix) ||
                title.startsWith(cachedBuyingPrefix)) {

            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        if (clickedInv == null)
            return;

        boolean isShop = title.equals(cachedMainTitle) ||
                title.startsWith(cachedShopPrefix) ||
                title.startsWith(cachedBuyingPrefix) ||
                title.equals(cachedConfirmTitle);

        if (!isShop)
            return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (clickedInv.equals(topInv)) {
            event.setCancelled(true);
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            } else {
                event.setCancelled(false);
            }
            return;
        }

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
            return;

        if (event.getCurrentItem().getType() != Material.BLACK_STAINED_GLASS_PANE) {
            playSound(player, getSound("menu-click", Sound.BLOCK_TRIPWIRE_CLICK_ON));
        }

        int slot = event.getSlot();

        if (title.equals(cachedMainTitle)) {
            if (mainMenuSlots.containsKey(slot)) {
                openCategory(player, mainMenuSlots.get(slot));
            }
            return;
        }

        if (title.startsWith(cachedShopPrefix)) {
            if (slot == 18 && event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE) {
                openMainMenu(player);
                return;
            }

            ShardPurchaseSession shardSession = findShardSessionFromClick(player, title, slot);
            if (shardSession != null) {
                ItemStack itemToTest = new ItemStack(shardSession.displayMaterial, 1);
                if (getSpaceFor(player.getInventory(), itemToTest) <= 0) {
                    sendInventoryFull(player);
                    return;
                }

                if (shardSession.incrementValues != null && !shardSession.incrementValues.isEmpty()) {
                    BuyingSession session = new BuyingSession(
                            new ItemStack(shardSession.displayMaterial),
                            shardSession.price,
                            shardSession.currency,
                            shardSession.categoryFile,
                            shardSession.incrementValues,
                            shardSession.command,
                            shardSession.displayName,
                            shardSession.keyType,
                            shardSession.spawnerType);
                    buyingSessions.put(player.getUniqueId(), session);
                    openBuyingMenu(player, session);
                    return;
                }

                shardPurchaseSessions.put(player.getUniqueId(), shardSession);
                openShardConfirmation(player, shardSession);
                return;
            }

            BuyingSession session = findSessionFromClick(player, title, slot);
            if (session != null) {
                if (getSpaceFor(player.getInventory(), session.baseItem) <= 0) {
                    sendInventoryFull(player);
                    return;
                }
                buyingSessions.put(player.getUniqueId(), session);
                openBuyingMenu(player, session);
            }
            return;
        }

        if (buyingSessions.containsKey(player.getUniqueId()) && title.startsWith(cachedBuyingPrefix)) {
            BuyingSession session = buyingSessions.get(player.getUniqueId());
            int maxStack = session.baseItem.getMaxStackSize();

            if (slot == 21) {
                openCategory(player, session.categoryFileName);
                return;
            }

            if (slot == 23) {
                processPurchase(player, session);
                return;
            }

            List<Integer> values = session.incrementValues;
            int[] addSlots = { 15, 16, 17 };
            int[] remSlots = { 11, 10, 9 };

            for (int i = 0; i < addSlots.length; i++) {
                if (slot == addSlots[i] && i < values.size()) {
                    int val = values.get(i);
                    if (session.quantity < maxStack) {
                        session.quantity = Math.min(session.quantity + val, maxStack);
                        openBuyingMenu(player, session);
                    }
                    return;
                }
            }

            for (int i = 0; i < remSlots.length; i++) {
                if (slot == remSlots[i] && i < values.size()) {
                    int val = values.get(i);
                    if (session.quantity - val >= 1) {
                        session.quantity -= val;
                        openBuyingMenu(player, session);
                    } else if (session.quantity == maxStack && val == maxStack && maxStack > 1) {
                        session.quantity = 1;
                        openBuyingMenu(player, session);
                    }
                    return;
                }
            }
        }

        if (shardPurchaseSessions.containsKey(player.getUniqueId()) && title.equals(cachedConfirmTitle)) {
            event.setCancelled(true);
            ShardPurchaseSession session = shardPurchaseSessions.get(player.getUniqueId());

            if (slot == 11 && event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE) {
                shardPurchaseSessions.remove(player.getUniqueId());
                openCategory(player, session.categoryFile);
                return;
            }

            if (slot == 15 && event.getCurrentItem().getType() == Material.GREEN_STAINED_GLASS_PANE) {
                processShardPurchase(player, session);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
    }

    private void processPurchase(Player player, BuyingSession session) {
        int spaceAvailable = getSpaceFor(player.getInventory(), session.baseItem);

        if (spaceAvailable <= 0) {
            sendInventoryFull(player);
            return;
        }

        int buyAmount = Math.min(session.quantity, spaceAvailable);

        com.falconcore.survival.manager.PlayerData pd = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (pd == null) {
            player.sendMessage(color(getMessage("error-loading-data", "&cError loading your data!")));
            playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
            return;
        }

        double totalCost = session.unitPrice * buyAmount;
        String itemName = session.displayName != null ? session.displayName : formatName(session.baseItem);

        if (session.currency.equals("MONEY")) {
            if (!com.falconcore.survival.auction.EconomyHandler.chargePlayer(player, totalCost, "Shop: " + itemName)) {
                player.sendMessage(color(getMessage("not-enough-money", "&cYou do not have enough money!")));
                playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
                return;
            }
        } else {
            if (pd.getShards() < totalCost) {
                player.sendMessage(color(getMessage("not-enough-shards", "&cYou do not have enough shards!")));
                playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
                return;
            }
            pd.removeShards(totalCost, "Shop: " + itemName);
        }

        plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());

        if (session.command != null && !session.command.isEmpty()) {
            String cmd = session.command
                    .replace("{gamertag}", player.getName())
                    .replace("{amount}", String.valueOf(buyAmount))
                    .replace("{quantity}", String.valueOf(buyAmount))
                    .replace("{value}", String.valueOf(buyAmount));
            plugin.getSchedulerAdapter().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        } else if (session.keyType != null) {
            String normalizedKey = plugin.normalizeKeyName(session.keyType);
            for (int k = 0; k < buyAmount; k++)
                pd.addKey(normalizedKey);
            plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());
        } else if (session.spawnerType != null) {
            String cmd = "spawner give " + player.getName() + " " + session.spawnerType + " " + buyAmount;
            plugin.getSchedulerAdapter().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        } else {
            ItemStack toGive = session.baseItem.clone();
            toGive.setAmount(buyAmount);

            if (session.potionEffects != null && !session.potionEffects.isEmpty()) {
                if (toGive.getType() == Material.ARROW || toGive.getType() == Material.TIPPED_ARROW) {
                    if (toGive.getType() == Material.ARROW) {
                        toGive.setType(Material.TIPPED_ARROW);
                    }

                    org.bukkit.inventory.meta.PotionMeta potionMeta = (org.bukkit.inventory.meta.PotionMeta) toGive
                            .getItemMeta();
                    if (potionMeta != null) {
                        for (String effectName : session.potionEffects) {
                            try {
                                org.bukkit.potion.PotionEffectType effectType = org.bukkit.potion.PotionEffectType
                                        .getByName(effectName);
                                if (effectType != null) {
                                    int durationTicks = session.effectDuration * 20;
                                    int amplifier = session.effectLevel - 1;
                                    org.bukkit.potion.PotionEffect effect = new org.bukkit.potion.PotionEffect(
                                            effectType, durationTicks, amplifier, false, true, true);
                                    potionMeta.addCustomEffect(effect, true);
                                }
                            } catch (Exception e) {
                            }
                        }
                        toGive.setItemMeta(potionMeta);
                    }
                }
            }
            player.getInventory().addItem(toGive);
        }

        playSound(player, getSound("purchase-success", Sound.ENTITY_EXPERIENCE_ORB_PICKUP), 1.0f, 2.0f);

        pd.addShopSpent(totalCost);
        plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());
    }

    private void sendInventoryFull(Player player) {
        String msg = color(getMessage("inventory-full", "&cYour inventory is full!"));
        player.sendMessage(msg);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
        playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
    }

    private int getSpaceFor(Inventory inv, ItemStack item) {
        int space = 0;
        int maxStack = item.getMaxStackSize();

        for (ItemStack i : inv.getStorageContents()) {
            if (i == null || i.getType() == Material.AIR) {
                space += maxStack;
            } else if (i.isSimilar(item)) {
                space += (maxStack - i.getAmount());
            }
        }
        return space;
    }

    private BuyingSession findSessionFromClick(Player player, String guiTitle, int clickedSlot) {
        Map<Integer, BuyingSession> slots = sessionLookup.get(guiTitle);
        if (slots != null) {
            BuyingSession template = slots.get(clickedSlot);
            if (template != null) {
                return new BuyingSession(template.baseItem, template.unitPrice, template.categoryFileName,
                        template.incrementValues, template.potionEffects, template.effectDuration,
                        template.effectLevel);
            }
        }
        return null;
    }

    private ItemStack createConfigItem(ConfigurationSection section) {
        String matName = section.getString("material", "STONE");
        Material mat = Material.getMaterial(matName.toUpperCase());
        if (mat == null)
            mat = Material.STONE;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (section.contains("name"))
            meta.setDisplayName(color(section.getString("name")));
        if (section.contains("lore")) {
            List<String> lore = new ArrayList<>();
            for (String s : section.getStringList("lore"))
                lore.add(color(s));
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material mat, String name, String loreLine) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        if (!loreLine.isEmpty())
            meta.setLore(Collections.singletonList(color(loreLine)));
        item.setItemMeta(meta);
        return item;
    }

    private String getMessage(String path, String def) {
        if (messagesConfig == null)
            return def;
        return messagesConfig.getString("messages." + path, def);
    }

    private Sound getSound(String key, Sound def) {
        if (messagesConfig == null)
            return def;
        String soundName = messagesConfig.getString("sounds." + key);
        if (soundName == null || soundName.isEmpty())
            return def;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private String color(String s) {
        if (s == null || s.isEmpty())
            return "";
        if (!s.contains("&"))
            return s;
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void playSound(Player p, Sound s) {
        try {
            p.playSound(p.getLocation(), s, 1f, 1f);
        } catch (Exception ignored) {
        }
    }

    private void playSound(Player p, Sound s, float volume, float pitch) {
        try {
            p.playSound(p.getLocation(), s, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private String formatName(ItemStack item) {
        return item.getType().name().toLowerCase().replace("_", " ");
    }

    private String toSmallCaps(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case 'A' -> sb.append("ᴀ");
                case 'B' -> sb.append("ʙ");
                case 'C' -> sb.append("ᴄ");
                case 'D' -> sb.append("ᴅ");
                case 'E' -> sb.append("ᴇ");
                case 'F' -> sb.append("ꜰ");
                case 'G' -> sb.append("ɢ");
                case 'H' -> sb.append("ʜ");
                case 'I' -> sb.append("ɪ");
                case 'J' -> sb.append("ᴊ");
                case 'K' -> sb.append("ᴋ");
                case 'L' -> sb.append("ʟ");
                case 'M' -> sb.append("ᴍ");
                case 'N' -> sb.append("ɴ");
                case 'O' -> sb.append("ᴏ");
                case 'P' -> sb.append("ᴘ");
                case 'Q' -> sb.append("ǫ");
                case 'R' -> sb.append("ʀ");
                case 'S' -> sb.append("ѕ");
                case 'T' -> sb.append("ᴛ");
                case 'U' -> sb.append("ᴜ");
                case 'V' -> sb.append("ᴠ");
                case 'W' -> sb.append("ᴡ");
                case 'X' -> sb.append("x");
                case 'Y' -> sb.append("ʏ");
                case 'Z' -> sb.append("ᴢ");
                case ' ' -> sb.append(" ");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private void processShardPurchase(Player player, ShardPurchaseSession session) {
        com.falconcore.survival.manager.PlayerData pd = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (pd == null) {
            player.sendMessage(color(getMessage("error-loading-data", "&cError loading your data!")));
            playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
            return;
        }

        ItemStack itemToTest = new ItemStack(session.displayMaterial, 1);
        if (getSpaceFor(player.getInventory(), itemToTest) <= 0) {
            sendInventoryFull(player);
            return;
        }

        String itemName = !session.displayName.isEmpty() ? session.displayName : session.displayMaterial.name();

        if (session.currency.equals("MONEY")) {
            if (!com.falconcore.survival.auction.EconomyHandler.chargePlayer(player, session.price,
                    "Shop: " + itemName)) {
                String errorMsg = color(getMessage("not-enough-money", "&cYou do not have enough money!"));
                player.sendMessage(errorMsg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(errorMsg));
                playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    ShardPurchaseSession stored = shardPurchaseSessions.get(player.getUniqueId());
                    if (stored != null) {
                        openShardConfirmation(player, stored);
                    }
                }, 1L);
                return;
            }
        } else {
            double currentShards = pd.getShards();
            if (currentShards < session.price) {
                String errorMsg = color(getMessage("not-enough-shards", "&cYou do not have enough shards!"));
                player.sendMessage(errorMsg);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(errorMsg));
                playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
                plugin.getSchedulerAdapter().runTaskLater(() -> {
                    if (shardPurchaseSessions.containsKey(player.getUniqueId())) {
                        openShardConfirmation(player, session);
                    }
                }, 1L);
                return;
            }
            pd.removeShards(session.price, "Shop: " + itemName);
        }

        plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());

        if (session.command != null && !session.command.isEmpty()) {
            String cmd = session.command
                    .replace("{gamertag}", player.getName())
                    .replace("{amount}", "1")
                    .replace("{quantity}", "1")
                    .replace("{value}", "1");
            plugin.getSchedulerAdapter().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        } else {
            if (session.keyType != null) {
                String normalizedKey = plugin.normalizeKeyName(session.keyType);
                pd.addKey(normalizedKey);
                plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());
            } else if (session.spawnerType != null) {
                String cmd = "spawner give " + player.getName() + " " + session.spawnerType + " 1";
                plugin.getSchedulerAdapter().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            } else {
                player.sendMessage(color(getMessage("item-not-recognized", "&cError: Item type not recognized!")));
                if (session.currency.equals("MONEY")) {
                    com.falconcore.survival.auction.EconomyHandler.depositPlayer(player, session.price, "Shop Refund");
                } else {
                    pd.setShards(pd.getShards() + session.price, "Shop Refund");
                    plugin.getPlayerDataManager().savePlayerAsync(player.getUniqueId());
                }
                playSound(player, getSound("purchase-error", Sound.ENTITY_VILLAGER_NO));
                return;
            }
        }

        playSound(player, getSound("purchase-success", Sound.ENTITY_EXPERIENCE_ORB_PICKUP));
        openCategory(player, session.categoryFile);
        shardPurchaseSessions.remove(player.getUniqueId());
    }

    private ShardPurchaseSession findShardSessionFromClick(Player player, String guiTitle, int clickedSlot) {
        Map<Integer, ShardPurchaseSession> slots = shardSessionLookup.get(guiTitle);
        return slots != null ? slots.get(clickedSlot) : null;
    }

    private void openShardConfirmation(Player player, ShardPurchaseSession session) {
        Inventory gui = Bukkit.createInventory(null, 27, color("&8ᴄᴏɴꜰɪʀᴍ ᴘᴜʀᴄʜᴀѕᴇ"));

        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(color("&4ᴄᴀɴᴄᴇʟ"));
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(color("&fClick to cancel"));
        cancelMeta.setLore(cancelLore);
        cancel.setItemMeta(cancelMeta);
        gui.setItem(11, cancel);

        ItemStack preview = new ItemStack(session.displayMaterial);
        ItemMeta previewMeta = preview.getItemMeta();
        if (!session.displayName.isEmpty()) {
            previewMeta.setDisplayName(color(session.displayName));
        }
        List<String> previewLore = new ArrayList<>();
        if (session.currency.equals("MONEY")) {
            String priceStr = String.format("%,.0f", session.price);
            previewLore.add(color("&fBuy price: &a$" + priceStr));
        } else {
            previewLore.add(color("&fBuy price: &d" + (int) session.price + "x shards"));
        }
        previewMeta.setLore(previewLore);
        preview.setItemMeta(previewMeta);
        gui.setItem(13, preview);

        ItemStack confirm = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(color("&aᴄᴏɴꜰɪʀᴍ"));
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(color("&fClick to confirm"));
        confirmMeta.setLore(confirmLore);
        confirm.setItemMeta(confirmMeta);
        gui.setItem(15, confirm);

        player.openInventory(gui);
    }

    private static class BuyingSession {
        ItemStack baseItem;
        double unitPrice;
        int quantity;
        String categoryFileName;
        List<Integer> incrementValues;
        List<String> potionEffects;
        int effectDuration;
        int effectLevel;

        String currency = "MONEY";
        String command;
        String displayName;
        String keyType;
        String spawnerType;

        public BuyingSession(ItemStack baseItem, double unitPrice, String categoryFileName,
                List<Integer> incrementValues, List<String> potionEffects, int effectDuration, int effectLevel) {
            this.baseItem = baseItem;
            this.unitPrice = unitPrice;
            this.categoryFileName = categoryFileName;
            this.incrementValues = incrementValues;
            this.potionEffects = potionEffects;
            this.effectDuration = effectDuration;
            this.effectLevel = effectLevel;
            this.quantity = 1;
        }

        public BuyingSession(ItemStack baseItem, double unitPrice, String currency, String categoryFileName,
                List<Integer> incrementValues, String command, String displayName, String keyType, String spawnerType) {
            this.baseItem = baseItem;
            this.unitPrice = unitPrice;
            this.currency = (currency != null) ? currency.toUpperCase() : "MONEY";
            this.categoryFileName = categoryFileName;
            this.incrementValues = incrementValues;
            this.command = command;
            this.displayName = displayName;
            this.keyType = keyType;
            this.spawnerType = spawnerType;
            this.quantity = 1;
        }
    }

    private static class ShardPurchaseSession {
        Material displayMaterial;
        String displayName;
        double price;
        String currency;
        String categoryFile;
        String keyType;
        String spawnerType;
        String command;
        List<Integer> incrementValues;

        public ShardPurchaseSession(Material displayMaterial, String displayName, double price, String currency,
                String categoryFile, String keyType, String spawnerType, String command,
                List<Integer> incrementValues) {
            this.displayMaterial = displayMaterial;
            this.displayName = displayName;
            this.price = price;
            this.currency = currency;
            this.categoryFile = categoryFile;
            this.keyType = keyType;
            this.spawnerType = spawnerType;
            this.command = command;
            this.incrementValues = incrementValues;
        }
    }
}