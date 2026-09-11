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
        String name = event.getName();

        DatabaseManager.BanInfo info = plugin.getDatabaseManager().getBanInfo(uuid);
        if (info == null && name != null) {
            info = plugin.getDatabaseManager().getBanInfoByName(name);
            // If they were banned by name prior to joining, link their real login UUID
            if (info != null && (info.expire == -1 || info.expire > System.currentTimeMillis())) {
                try {
                    plugin.getDatabaseManager().addBan(
                            uuid,
                            name,
                            info.id,
                            info.reasonKey,
                            info.reason,
                            info.count,
                            info.date,
                            info.expire,
                            info.bannedBy
                    );
                } catch (Throwable ignored) {
                }
            }
        }

        if (info != null) {
            if (info.expire == -1 || info.expire > System.currentTimeMillis()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.formatBanMessage(info));
            } else {
                // Ban expired
                plugin.getDatabaseManager().removeBan(uuid);
                if (name != null) {
                    plugin.getDatabaseManager().removeBan(name);
                }
            }
        }
    }
}