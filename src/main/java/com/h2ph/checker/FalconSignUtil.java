package com.h2ph.checker;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Low-level utility for sign editor manipulation and NMS block entity authorization.
 */
public class FalconSignUtil {

    public static void setAllowedEditor(Location loc, UUID playerUUID, Plugin plugin) {
        try {
            Object world = loc.getWorld().getClass().getMethod("getHandle").invoke(loc.getWorld());
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");
            Object bp = bpClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Method gbe = Arrays.stream(world.getClass().getMethods())
                    .filter(m -> (m.getName().equals("getBlockEntity") || m.getName().equals("c_") || m.getName().equals("getBlockEntityDirect")) && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (gbe == null) return;
            Object be = gbe.invoke(world, bp);
            if (be == null) return;

            for (Method m : be.getClass().getMethods()) {
                if ((m.getName().equals("setAllowedPlayerEditor") || m.getName().equals("a"))
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].equals(UUID.class)) {
                    m.invoke(be, playerUUID);
                    return;
                }
            }
            for (Field f : getAllFields(be.getClass())) {
                if (f.getType().equals(UUID.class)) {
                    f.setAccessible(true);
                    f.set(be, playerUUID);
                    return;
                }
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("[FalconChecker] setAllowedEditor error: " + t.getMessage());
            }
        }
    }

    public static void sendBlockEntityPacket(Player player, Location loc, Plugin plugin) {
        try {
            Object world = loc.getWorld().getClass().getMethod("getHandle").invoke(loc.getWorld());
            Class<?> bpClass = Class.forName("net.minecraft.core.BlockPos");
            Object bp = bpClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Method gbe = Arrays.stream(world.getClass().getMethods())
                    .filter(m -> (m.getName().equals("getBlockEntity") || m.getName().equals("c_")) && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (gbe == null) return;
            Object be = gbe.invoke(world, bp);
            if (be == null) return;

            Method gup = Arrays.stream(be.getClass().getMethods())
                    .filter(m -> (m.getName().equals("getUpdatePacket") || m.getName().equals("j") || m.getName().equals("f_")) && m.getParameterCount() == 0)
                    .findFirst().orElse(null);
            if (gup == null) return;
            Object packet = gup.invoke(be);
            if (packet == null) return;

            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Field connField = Arrays.stream(handle.getClass().getFields())
                    .filter(f -> f.getName().equals("connection") || f.getName().equals("c") || f.getName().equals("b"))
                    .findFirst().orElse(null);
            if (connField == null) {
                connField = Arrays.stream(handle.getClass().getDeclaredFields())
                        .filter(f -> f.getName().equals("connection") || f.getName().equals("c") || f.getName().equals("b"))
                        .findFirst().orElse(null);
            }
            if (connField == null) return;
            connField.setAccessible(true);
            Object conn = connField.get(handle);

            Method sp = Arrays.stream(conn.getClass().getMethods())
                    .filter(m -> (m.getName().equals("send") || m.getName().equals("sendPacket") || m.getName().equals("a") || m.getName().equals("b")) && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (sp != null) {
                sp.invoke(conn, packet);
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("[FalconChecker] sendBlockEntityPacket error: " + t.getMessage());
            }
        }
    }

    public static void openSignEditor(Player player, Location loc) {
        if (player == null || !player.isOnline()) return;

        try {
            if (loc.getBlock().getState() instanceof org.bukkit.block.Sign sign) {
                try {
                    player.openSign(sign, org.bukkit.block.sign.Side.FRONT);
                    return;
                } catch (Throwable t) {
                    player.openSign(sign);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            Vector3i pos = new Vector3i(x, y, z);
            WrapperPlayServerOpenSignEditor packet = new WrapperPlayServerOpenSignEditor(pos, true);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable ignored) {}
    }

    public static void closeEditor(Player player) {
        try {
            WrapperPlayServerCloseWindow packet = new WrapperPlayServerCloseWindow(0);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable ignored) {}
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }
}

