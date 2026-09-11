package com.h2ph.checker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FalconCheckerCommand implements CommandExecutor, TabCompleter {

    private final FalconCheckerManager manager;

    public FalconCheckerCommand(FalconCheckerManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("falcon.checker") && !sender.hasPermission("falcon.signprobe")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.YELLOW));
            return true;
        }

        manager.handleModsCommand(sender, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && (sender.hasPermission("falcon.checker") || sender.hasPermission("falcon.signprobe"))) {
            List<String> players = new ArrayList<>();
            for (Player p : manager.getPlugin().getServer().getOnlinePlayers()) {
                players.add(p.getName());
            }
            return players.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}

