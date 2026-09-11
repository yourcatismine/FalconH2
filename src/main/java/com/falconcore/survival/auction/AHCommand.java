package com.falconcore.survival.auction;

import java.util.List;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class AHCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final AuctionController controller;

    public AHCommand(AuctionController controller) {
        this.controller = controller;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Utils.formatColors("&#ff4444Only players can use this command!"));
            return true;
        }
        Player player = (Player) sender;
        FileConfiguration cfg = this.controller.getConfig();
        Sound villagerNo;
        try {
            villagerNo = Sound.valueOf(cfg.getString("sounds.villager-no", "ENTITY_VILLAGER_NO"));
        } catch (Exception e) {
            villagerNo = Sound.ENTITY_VILLAGER_NO;
        }
        if (args.length == 0) {
            Plugin plugin = (Plugin) this.controller.getPlugin();
            if (plugin != null) {
                player.removeMetadata("ah-filter", plugin);
                player.removeMetadata("ah-cat", plugin);
                player.removeMetadata("ah-page", plugin);
                player.removeMetadata("ah-admin-view", plugin);
            }

            this.controller.getAuctionManager().setPlayerFilter(player.getUniqueId(), "");
            this.controller.getAuctionManager().setPlayerCategory(player.getUniqueId(), "All");
            this.controller.getAuctionManager().setPlayerSort(player.getUniqueId(), "Highest Price");

            GUIHandler.openMainGUI(player, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("sell")) {
            int max = cfg.getInt("settings.max-auction-listed");
            int currentCount = 0;
            long now = System.currentTimeMillis();
            for (AuctionItem ai : this.controller.getAuctionManager().getItems()) {
                if (ai.getSeller().equals(player.getName()) && !this.controller.getAuctionManager().isExpired(ai)) {
                    ++currentCount;
                }
            }
            if (currentCount >= max) {
                String msg = cfg.getString("messages.max-listings").replace("{count}", String.valueOf(currentCount))
                        .replace("{max}", String.valueOf(max));
                player.sendMessage(Utils.formatColors(msg));
                player.playSound(player.getLocation(), villagerNo, 1.0f, 1.0f);
                return true;
            }
            String priceArg = args[1];
            double rawPrice = Utils.parsePrice(priceArg);
            if (rawPrice < 0.0) {
                player.removeMetadata("ah-admin-view", (Plugin) this.controller.getPlugin());
                GUIHandler.openMainGUI(player, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
                return true;
            }
            double maxPrice = cfg.getDouble("settings.max-auction-price", 1000000000000.0);
            if (rawPrice > maxPrice) {
                player.sendMessage(Utils.formatColors(cfg.getString("messages.max-price-exceeded",
                        "&#ff4444Price exceeds the maximum allowed limit!")));
                player.playSound(player.getLocation(), villagerNo, 1.0f, 1.0f);
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                player.sendMessage(Utils.formatColors(this.controller.getMessage("must-hold-item", "&#ff4444You must hold an item to sell!")));
                player.playSound(player.getLocation(), villagerNo, 1.0f, 1.0f);
                return true;
            }
            String matName = held.getType().name();
            List disabled = cfg.getStringList("settings.disabled-items");
            for (Object dObj : disabled) {
                String d = (String) dObj;
                if (!d.trim().equalsIgnoreCase(matName))
                    continue;
                player.sendMessage(Utils.formatColors(cfg.getString("messages.disabled-item")));
                player.playSound(player.getLocation(), villagerNo, 1.0f, 1.0f);
                return true;
            }
            GUIHandler.openSellConfirm(player, rawPrice, this.controller);
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : args) {
            sb.append(s).append(" ");
        }
        String searchTerm = sb.toString().trim().toLowerCase();
        player.setMetadata("ah-filter",
                (MetadataValue) new FixedMetadataValue((Plugin) this.controller.getPlugin(), (Object) searchTerm));
        player.removeMetadata("ah-admin-view", (Plugin) this.controller.getPlugin());
        GUIHandler.openMainGUI(player, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            java.util.List<String> completions = new java.util.ArrayList<>();
            completions.add("<search>");
            return org.bukkit.util.StringUtil.copyPartialMatches(args[0], completions, new java.util.ArrayList<>());
        }
        return java.util.Collections.emptyList();
    }
}
