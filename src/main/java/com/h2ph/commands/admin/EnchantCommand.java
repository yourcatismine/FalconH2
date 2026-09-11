package com.h2ph.commands.admin;

import com.h2ph.Falcon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class EnchantCommand implements CommandExecutor, TabCompleter {

    private final Falcon plugin;

    public EnchantCommand(Falcon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("falcon.enchant") && !sender.hasPermission("falcon.admin")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        }

        Player target;
        String enchantArg;
        String levelArg = null;

        // If sender is a player and args[0] matches an enchantment, target is sender
        if (sender instanceof Player && findEnchantment(args[0]) != null) {
            target = (Player) sender;
            enchantArg = args[0];
            if (args.length >= 2) {
                levelArg = args[1];
            }
        } else if (args.length >= 2) {
            // First argument is a target player
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    p.sendActionBar(Component.text("This user is not online.", NamedTextColor.RED));
                }
                sender.sendMessage(ChatColor.RED + "This user is not online.");
                return true;
            }
            enchantArg = args[1];
            if (args.length >= 3) {
                levelArg = args[2];
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
            enchantArg = args[0];
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /" + label + " <player> <enchantment> [level]");
            return true;
        }

        if (target == null || !target.isOnline()) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendActionBar(Component.text("This user is not online.", NamedTextColor.RED));
            }
            sender.sendMessage(ChatColor.RED + "This user is not online.");
            return true;
        }

        ItemStack held = target.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        }

        Enchantment enchantment = findEnchantment(enchantArg);
        if (enchantment == null) {
            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return true;
        }

        int level = 1;
        boolean remove = false;

        if (levelArg != null) {
            if (levelArg.equalsIgnoreCase("remove") || levelArg.equalsIgnoreCase("none") || levelArg.equalsIgnoreCase("off") || levelArg.equalsIgnoreCase("clear")) {
                remove = true;
            } else {
                try {
                    level = Integer.parseInt(levelArg);
                    if (level <= 0) {
                        remove = true;
                    } else if (level > 255) {
                        level = 255;
                    }
                } catch (NumberFormatException e) {
                    if (sender instanceof Player) {
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                    return true;
                }
            }
        }

        String enchName = formatEnchantmentName(enchantment);
        String itemName = formatItemName(held);

        ItemMeta meta = held.getItemMeta();
        if (meta != null) {
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey pdcKey = new NamespacedKey(plugin, "custom_ench_" + enchantment.getKey().getKey());

            if (remove) {
                pdc.remove(pdcKey);
                if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                    ((org.bukkit.inventory.meta.EnchantmentStorageMeta) meta).removeStoredEnchant(enchantment);
                } else {
                    meta.removeEnchant(enchantment);
                }
            } else {
                pdc.set(pdcKey, org.bukkit.persistence.PersistentDataType.INTEGER, level);
                if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                    ((org.bukkit.inventory.meta.EnchantmentStorageMeta) meta).addStoredEnchant(enchantment, level, true);
                } else {
                    meta.addEnchant(enchantment, level, true);
                }
            }
            held.setItemMeta(meta);
        }

        if (remove) {
            held.removeEnchantment(enchantment);
        } else {
            held.addUnsafeEnchantment(enchantment, level);
        }
        target.getInventory().setItemInMainHand(held);

        if (remove) {
            String successMsg = ChatColor.translateAlternateColorCodes('&', "&aRemoved &e" + enchName + " &afrom &f" + itemName + "&a.");
            sender.sendMessage(successMsg);
            if (!sender.equals(target)) {
                target.sendMessage(successMsg);
            }
            target.playSound(target.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
            return true;
        }

        String levelStr = (level == 1 && enchantment.getMaxLevel() == 1) ? "" : " " + level;
        String successMsg = ChatColor.translateAlternateColorCodes('&', "&aEnchanted &f" + itemName + " &awith &e" + enchName + levelStr + "&a!");
        sender.sendMessage(successMsg);
        if (!sender.equals(target)) {
            target.sendMessage(successMsg);
        }
        target.playSound(target.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        return true;
    }

    public static ItemStack restoreEnchantments(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean modified = false;
        Map<Enchantment, Integer> enchantsToApply = new HashMap<>();

        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().startsWith("custom_ench_")) {
                String enchKey = key.getKey().substring("custom_ench_".length());
                Enchantment ench = findEnchantment(enchKey);
                if (ench != null) {
                    Integer level = pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
                    if (level != null && level > 0) {
                        enchantsToApply.put(ench, level);
                        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                            ((org.bukkit.inventory.meta.EnchantmentStorageMeta) meta).addStoredEnchant(ench, level, true);
                        } else {
                            meta.addEnchant(ench, level, true);
                        }
                        modified = true;
                    }
                }
            }
        }

        if (modified) {
            item.setItemMeta(meta);
            for (Map.Entry<Enchantment, Integer> entry : enchantsToApply.entrySet()) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
        }
        return item;
    }

    public static ItemStack[] restoreEnchantments(ItemStack[] items) {
        if (items == null) return null;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                items[i] = restoreEnchantments(items[i]);
            }
        }
        return items;
    }

    public static Enchantment findEnchantment(String input) {
        if (input == null || input.isEmpty()) return null;
        String clean = input.toLowerCase().replace("minecraft:", "").trim();

        // Try key lookup
        try {
            NamespacedKey key = NamespacedKey.minecraft(clean);
            Enchantment ench = Enchantment.getByKey(key);
            if (ench != null) return ench;
        } catch (Throwable ignored) {}

        // Try by uppercase name
        try {
            Enchantment ench = Enchantment.getByName(clean.toUpperCase());
            if (ench != null) return ench;
        } catch (Throwable ignored) {}

        // Try common aliases & fuzzy match
        Map<String, String> aliases = new HashMap<>();
        aliases.put("sharpness", "sharpness");
        aliases.put("smite", "smite");
        aliases.put("baneofarthropods", "bane_of_arthropods");
        aliases.put("arthropods", "bane_of_arthropods");
        aliases.put("knockback", "knockback");
        aliases.put("fireaspect", "fire_aspect");
        aliases.put("fire", "fire_aspect");
        aliases.put("looting", "looting");
        aliases.put("sweeping", "sweeping_edge");
        aliases.put("sweepingedge", "sweeping_edge");
        aliases.put("efficiency", "efficiency");
        aliases.put("silktouch", "silk_touch");
        aliases.put("unbreaking", "unbreaking");
        aliases.put("fortune", "fortune");
        aliases.put("power", "power");
        aliases.put("punch", "punch");
        aliases.put("flame", "flame");
        aliases.put("infinity", "infinity");
        aliases.put("luckofthesea", "luck_of_the_sea");
        aliases.put("lucksea", "luck_of_the_sea");
        aliases.put("lure", "lure");
        aliases.put("loyalty", "loyalty");
        aliases.put("impaling", "impaling");
        aliases.put("riptide", "riptide");
        aliases.put("channeling", "channeling");
        aliases.put("multishot", "multishot");
        aliases.put("quickcharge", "quick_charge");
        aliases.put("piercing", "piercing");
        aliases.put("mending", "mending");
        aliases.put("vanishingcurse", "vanishing_curse");
        aliases.put("bindingcurse", "binding_curse");
        aliases.put("protection", "protection");
        aliases.put("fireprotection", "fire_protection");
        aliases.put("featherfalling", "feather_falling");
        aliases.put("blastprotection", "blast_protection");
        aliases.put("projectileprotection", "projectile_protection");
        aliases.put("respiration", "respiration");
        aliases.put("aquaaffinity", "aqua_affinity");
        aliases.put("thorns", "thorns");
        aliases.put("depthstrider", "depth_strider");
        aliases.put("frostwalker", "frost_walker");
        aliases.put("soulspeed", "soul_speed");
        aliases.put("swiftsneak", "swift_sneak");
        aliases.put("density", "density");
        aliases.put("breach", "breach");
        aliases.put("windburst", "wind_burst");

        String mapped = aliases.get(clean.replace("_", ""));
        if (mapped != null) {
            try {
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(mapped));
                if (ench != null) return ench;
            } catch (Throwable ignored) {}
        }

        // Iterate all registered enchantments
        for (Enchantment ench : getAllEnchantments()) {
            if (ench.getKey().getKey().equalsIgnoreCase(clean) || ench.getName().equalsIgnoreCase(clean)) {
                return ench;
            }
            if (ench.getKey().getKey().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                return ench;
            }
        }

        return null;
    }

    public static List<Enchantment> getAllEnchantments() {
        List<Enchantment> list = new ArrayList<>();
        for (Enchantment e : Enchantment.values()) {
            if (e != null) {
                list.add(e);
            }
        }
        return list;
    }

    public static String formatEnchantmentName(Enchantment ench) {
        String key = ench.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatItemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "Air";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String name = item.getType().name().replace("_", " ").toLowerCase();
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("falcon.enchant") && !sender.hasPermission("falcon.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            // Offer enchantment names
            for (Enchantment ench : getAllEnchantments()) {
                completions.add(ench.getKey().getKey());
            }
            // Also offer online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }

            String current = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(current))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // If arg[0] is player name, offer enchantments
            if (Bukkit.getPlayerExact(args[0]) != null) {
                List<String> completions = getAllEnchantments().stream()
                        .map(e -> e.getKey().getKey())
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .sorted()
                        .collect(Collectors.toList());
                return completions;
            }
            // Else offer level suggestions
            List<String> levels = Arrays.asList("1", "2", "3", "4", "5", "10", "50", "100", "255", "0", "remove");
            return levels.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            if (Bukkit.getPlayerExact(args[0]) != null) {
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5", "10", "50", "100", "255", "0", "remove");
                return levels.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}

