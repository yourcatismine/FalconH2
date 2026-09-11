package com.h2ph.listeners;

import com.h2ph.commands.admin.moderations.OffendPlugin;
import com.falconcore.survival.manager.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BanListener implements Listener {

    private final OffendPlugin plugin;

    public BanListener(OffendPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();

        if (plugin.getDatabaseManager().isBanned(uuid)) {
            DatabaseManager.BanInfo info = plugin.getDatabaseManager().getBanInfo(uuid);

            if (info != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.formatBanMessage(info));
            } else {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "You are banned.");
            }
        }
    }
}