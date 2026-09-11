/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package com.falconcore.survival.sell.commands;

import com.falconcore.survival.sell.FalconSell;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand
        implements CommandExecutor {
    private final FalconSell plugin;

    public SellCommand(FalconSell plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(com.falconcore.survival.sell.utils.MessageUtil.colorize(
                    this.plugin.getMessage("only-players", "&cOnly players can use this command!")));
            return true;
        }
        Player player = (Player) sender;
        this.plugin.getSellGUI().openGUI(player);
        return true;
    }
}
