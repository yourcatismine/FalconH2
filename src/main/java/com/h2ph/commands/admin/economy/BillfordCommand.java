package com.h2ph.commands.admin.economy;

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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.TabCompleter;

public class BillfordCommand implements CommandExecutor, Listener, TabCompleter {

    private final Falcon plugin;
    private final String GUI_TITLE_PLAYER = ChatColor.translateAlternateColorCodes('&', "&8ʙɪʟʟꜰᴏʀᴅ");
    private final String GUI_TITLE_ADMIN = ChatColor.translateAlternateColorCodes('&', "&8ʙɪʟʟꜰᴏʀᴅ &cᴀᴅᴍɪɴ");

    private final File tradeFile;
    private FileConfiguration tradeConfig;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    private Map<Integer, ItemStack> currentInputs = new HashMap<>();
    private ItemStack currentOutput;

    private final List<Integer> VALID_INPUT_SLOTS = Arrays.asList(10, 11, 12, 19, 20, 21, 28, 29, 30);
    private final int OUTPUT_SLOT = 25;
    private final int SAVE_SLOT = 53;

    public BillfordCommand(Falcon plugin) {
        this.plugin = plugin;

        messagesFile = new File(plugin.getDataFolder(), "messages/economy/billford.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages/economy/billford.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        tradeFile = new File(plugin.getDataFolder(), "survival/billford/billford.yml");
        if (!tradeFile.exists()) {
            try {
                if (tradeFile.getParentFile() != null && !tradeFile.getParentFile().exists())
                    tradeFile.getParentFile().mkdirs();
                tradeFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        tradeConfig = YamlConfiguration.loadConfiguration(tradeFile);

        loadTrade();
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

    private void loadTrade() {
        currentInputs.clear();

        if (tradeConfig.contains("inputs")) {
            for (String key : tradeConfig.getConfigurationSection("inputs").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = tradeConfig.getItemStack("inputs." + key);
                    if (item != null) {
                        currentInputs.put(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            currentInputs.put(10, new ItemStack(Material.IRON_INGOT, 64));
        }

        if (tradeConfig.contains("output")) {
            currentOutput = tradeConfig.getItemStack("output");
        } else {
            currentOutput = new ItemStack(Material.ARROW, 16);
        }
    }

    private void saveTrade(Map<Integer, ItemStack> inputs, ItemStack output) {
        try {
            tradeConfig.set("inputs", null);
            tradeConfig.set("input", null);

            for (Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
                tradeConfig.set("inputs." + entry.getKey(), entry.getValue());
            }

            tradeConfig.set("output", output);
            tradeConfig.save(tradeFile);

            this.currentInputs = new HashMap<>(inputs);
            this.currentOutput = output;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("only-players", "&cOnly players can use Billford.")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("falcon.billford")) {
                openPlayerGUI(player);
                return true;
            }
            openAdminGUI(player);
            return true;
        }

        openPlayerGUI(player);
        return true;
    }

    private void openPlayerGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE_PLAYER);
        fillBackground(gui);

        for (Map.Entry<Integer, ItemStack> entry : currentInputs.entrySet()) {
            gui.setItem(entry.getKey(), entry.getValue().clone());
        }

        if (currentOutput != null) {
            gui.setItem(OUTPUT_SLOT, currentOutput.clone());
        }

        ItemStack hopper = new ItemStack(Material.HOPPER);
        ItemMeta hMeta = hopper.getItemMeta();
        hMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&8ᴛʀᴀᴅᴇ"));
        List<String> hLore = new ArrayList<>();
        hLore.add(ChatColor.WHITE + "Click to confirm the trade");
        hLore.add("");
        hLore.add(ChatColor.GRAY + "(you need the items in your inventory)");
        hMeta.setLore(hLore);
        hopper.setItemMeta(hMeta);
        gui.setItem(23, hopper);

        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta bMeta = book.getItemMeta();
        bMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aʙɪʟʟꜰᴏʀᴅ᾽ѕ ᴛʀᴀᴅᴇ"));

        List<String> bLore = new ArrayList<>();

        for (ItemStack input : currentInputs.values()) {
            bLore.add(ChatColor.GRAY + "" + input.getAmount() + "x " + ChatColor.WHITE + formatName(input));
        }

        bLore.add(ChatColor.GRAY + "for");
        if (currentOutput != null) {
            bLore.add(ChatColor.GRAY + "" + currentOutput.getAmount() + "x " + ChatColor.GREEN
                    + formatName(currentOutput));
        }

        bMeta.setLore(bLore);
        book.setItemMeta(bMeta);
        gui.setItem(49, book);

        player.openInventory(gui);
    }

    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE_ADMIN);
        fillBackground(gui);

        for (int i : VALID_INPUT_SLOTS)
            gui.setItem(i, null);
        gui.setItem(OUTPUT_SLOT, null);

        for (Map.Entry<Integer, ItemStack> entry : currentInputs.entrySet()) {
            gui.setItem(entry.getKey(), entry.getValue().clone());
        }
        if (currentOutput != null) {
            gui.setItem(OUTPUT_SLOT, currentOutput.clone());
        }

        ItemStack save = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta sMeta = save.getItemMeta();
        sMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aѕᴀᴠᴇ ᴛʀᴀᴅᴇ"));
        sMeta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', "&fClick to save the billford")));
        save.setItemMeta(sMeta);
        gui.setItem(SAVE_SLOT, save);

        player.openInventory(gui);
    }

    private void fillBackground(Inventory gui) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        for (int slot : VALID_INPUT_SLOTS)
            gui.setItem(slot, null);
        gui.setItem(OUTPUT_SLOT, null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(GUI_TITLE_PLAYER) && !title.equals(GUI_TITLE_ADMIN))
            return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        int slot = event.getSlot();

        if (title.equals(GUI_TITLE_PLAYER)) {
            event.setCancelled(true);

            if (clicked != null && clicked.getType() != Material.BLACK_STAINED_GLASS_PANE
                    && clicked.getType() != Material.AIR) {
                playSound(player, getSound("click", Sound.BLOCK_TRIPWIRE_CLICK_ON));
            }

            if (slot == 23 && clicked != null && clicked.getType() == Material.HOPPER) {
                performTrade(player);
            }
        }

        else if (title.equals(GUI_TITLE_ADMIN)) {
            boolean isTopInv = (event.getClickedInventory() == event.getView().getTopInventory());

            if (isTopInv) {
                if (slot == SAVE_SLOT) {
                    event.setCancelled(true);
                    handleAdminSave(player, event.getInventory());
                    return;
                }

                if (VALID_INPUT_SLOTS.contains(slot) || slot == OUTPUT_SLOT) {
                    return;
                }

                event.setCancelled(true);
            }
        }
    }

    private void handleAdminSave(Player player, Inventory inv) {
        Map<Integer, ItemStack> foundInputs = new HashMap<>();
        ItemStack foundOutput = inv.getItem(OUTPUT_SLOT);

        boolean hasInput = false;
        for (int slot : VALID_INPUT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                foundInputs.put(slot, item);
                hasInput = true;
            }
        }

        if (!hasInput || foundOutput == null || foundOutput.getType() == Material.AIR) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getMessage("missing-items", "&cMissing items! Place items in the input area and an output in Slot 25.")));
            playSound(player, getSound("error", Sound.ENTITY_VILLAGER_NO));
            return;
        }

        saveTrade(foundInputs, foundOutput);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                getMessage("trade-saved", "&aBillford Trade Saved!")));
        playSound(player, getSound("admin-save", Sound.ENTITY_PLAYER_LEVELUP));
        player.closeInventory();
    }

    private void performTrade(Player player) {
        if (!canAfford(player, currentInputs.values())) {
            String failMsg = ChatColor.translateAlternateColorCodes('&',
                    getMessage("insufficient-items", "&cYou do not have all the required contents."));
            player.sendMessage(failMsg);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(failMsg));
            playSound(player, getSound("error", Sound.ENTITY_VILLAGER_NO));
            return;
        }

        removeItems(player, currentInputs.values());

        ItemStack reward = currentOutput.clone();
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), reward);
        } else {
            player.getInventory().addItem(reward);
        }

        String successMsg = ChatColor.translateAlternateColorCodes('&',
                getMessage("trade-success", "&7Traded for &f{amount}x &f{item}")
                        .replace("{amount}", String.valueOf(currentOutput.getAmount()))
                        .replace("{item}", formatName(currentOutput)));

        player.sendMessage(successMsg);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(successMsg));

    }

    private boolean canAfford(Player player, java.util.Collection<ItemStack> requirements) {
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] sim = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                sim[i] = contents[i].clone();
            }
        }

        for (ItemStack req : requirements) {
            if (req == null || req.getType() == Material.AIR)
                continue;
            int needed = req.getAmount();
            boolean strict = hasCustomMeta(req);
            com.falconcore.survival.orders.data.ItemKey key = strict ? null
                    : com.falconcore.survival.orders.data.ItemKey.fromStack(req);

            for (int i = 0; i < sim.length; i++) {
                if (sim[i] == null || sim[i].getType() == Material.AIR)
                    continue;

                boolean matches = false;
                if (strict) {
                    if (sim[i].isSimilar(req))
                        matches = true;
                } else {
                    if (key != null && key.matches(sim[i]))
                        matches = true;
                }

                if (matches) {
                    int has = sim[i].getAmount();
                    if (has >= needed) {
                        sim[i].setAmount(has - needed);
                        needed = 0;
                        break;
                    } else {
                        needed -= has;
                        sim[i] = null;
                    }
                }
            }

            if (needed > 0)
                return false;
        }
        return true;
    }

    private void removeItems(Player player, java.util.Collection<ItemStack> requirements) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack req : requirements) {
            if (req == null || req.getType() == Material.AIR)
                continue;
            int needed = req.getAmount();
            boolean strict = hasCustomMeta(req);
            com.falconcore.survival.orders.data.ItemKey key = strict ? null
                    : com.falconcore.survival.orders.data.ItemKey.fromStack(req);

            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null || contents[i].getType() == Material.AIR)
                    continue;

                boolean matches = false;
                if (strict) {
                    if (contents[i].isSimilar(req))
                        matches = true;
                } else {
                    if (key != null && key.matches(contents[i]))
                        matches = true;
                }

                if (matches) {
                    int has = contents[i].getAmount();
                    if (has >= needed) {
                        contents[i].setAmount(has - needed);
                        needed = 0;
                        break;
                    } else {
                        needed -= has;
                        contents[i] = null;
                    }
                }
            }
        }
        player.getInventory().setContents(contents);
    }

    private boolean hasCustomMeta(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() || meta.hasLore();
    }

    private void playSound(Player p, Sound s) {
        try {
            p.playSound(p.getLocation(), s, 1f, 1f);
        } catch (Exception ignored) {
        }
    }

    private String formatName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().name().toLowerCase().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("falcon.billford")) {
                List<String> completions = new ArrayList<>();
                if ("admin".startsWith(args[0].toLowerCase())) {
                    completions.add("admin");
                }
                return completions;
            }
        }
        return new ArrayList<>();
    }
}