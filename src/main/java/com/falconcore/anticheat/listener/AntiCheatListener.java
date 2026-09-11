package com.falconcore.anticheat.listener;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;

public class AntiCheatListener implements Listener {

    private final AntiCheatManager manager;

    public AntiCheatListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!manager.isEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = manager.getOrCreatePlayerData(player);

        data.updateMove(from, to);

        for (Check check : manager.getChecks()) {
            if (check.isEnabled()) {
                check.process(player, data, from, to);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setVelocityTicks(manager.getVelocityGraceTicks(), event.getVelocity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!manager.isEnabled()) return;
        if (event.getEntity() instanceof Player player) {
            PlayerData data = manager.getPlayerData(player.getUniqueId());
            if (data != null) {
                EntityDamageEvent.DamageCause cause = event.getCause();
                if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                    cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                    data.setExplosionTicks(manager.getVelocityGraceTicks());
                } else if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                           cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK ||
                           cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                    data.setDamageTicks(6);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data != null) {
            if (data.isAnticheatSetback()) {
                data.setAnticheatSetback(false);
                return;
            }
            data.setTeleportTicks(manager.getTeleportGraceTicks());
            data.setLastGroundLocation(event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setRespawnTicks(manager.getRespawnGraceTicks());
            data.setLastGroundLocation(event.getRespawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setWorldChangeTicks(manager.getWorldChangeGraceTicks());
            data.setLastGroundLocation(player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setGamemodeChangeTicks(80);
            data.resetMovementState(player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setFlightToggleTicks(80);
            data.resetMovementState(player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!manager.isEnabled()) return;
        if (event.getEntity() instanceof Player player) {
            PlayerData data = manager.getPlayerData(player.getUniqueId());
            if (data != null) {
                data.setElytraTicks(100);
                data.resetMovementState(player.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent event) {
        if (!manager.isEnabled()) return;

        org.bukkit.entity.Vehicle vehicle = event.getVehicle();
        if (vehicle == null || vehicle.isEmpty()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double deltaX = to.getX() - from.getX();
        double deltaY = to.getY() - from.getY();
        double deltaZ = to.getZ() - from.getZ();
        double deltaXZ = Math.hypot(deltaX, deltaZ);

        if (deltaX == 0.0 && deltaY == 0.0 && deltaZ == 0.0) {
            return;
        }

        for (org.bukkit.entity.Entity passenger : vehicle.getPassengers()) {
            if (!(passenger instanceof Player player)) continue;

            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (player.getAllowFlight() || player.isFlying()) continue;
            if (player.hasPermission(manager.getBypassPermission())) continue;

            PlayerData data = manager.getOrCreatePlayerData(player);
            if (manager.isIgnoreBedrock() && data.isBedrock()) continue;
            if (data.hasGracePeriod()) continue;

            Location vLoc = to.clone();
            org.bukkit.World world = vLoc.getWorld();
            if (world == null) continue;

            boolean inLiquid = vehicle.isInWater() || vLoc.getBlock().isLiquid();
            boolean nearGround = false;
            boolean onIce = false;
            boolean onSlime = false;

            for (double yOff = 0.0; yOff <= 1.2; yOff += 0.4) {
                for (double xOff = -0.5; xOff <= 0.5; xOff += 0.5) {
                    for (double zOff = -0.5; zOff <= 0.5; zOff += 0.5) {
                        org.bukkit.block.Block b = world.getBlockAt(
                                (int) Math.floor(vLoc.getX() + xOff),
                                (int) Math.floor(vLoc.getY() - yOff),
                                (int) Math.floor(vLoc.getZ() + zOff)
                        );
                        if (b.getType().isSolid() || b.isLiquid()) {
                            nearGround = true;
                        }
                        String name = b.getType().name();
                        if (name.contains("ICE")) {
                            onIce = true;
                        }
                        if (b.getType() == org.bukkit.Material.SLIME_BLOCK) {
                            onSlime = true;
                        }
                        if (b.getType() == org.bukkit.Material.BUBBLE_COLUMN) {
                            inLiquid = true;
                        }
                    }
                }
            }

            if (nearGround && deltaY <= 0.05) {
                data.setLastGroundLocation(vLoc);
                continue;
            }

            boolean illegalAscent = !nearGround && !inLiquid && !onSlime && deltaY > 0.05;

            boolean illegalHover = !nearGround && !inLiquid && Math.abs(deltaY) < 0.005 && (deltaXZ > 0.08 || from.getY() == to.getY());

            boolean illegalAirSpeed = !nearGround && !onIce && deltaXZ > 0.65;

            if (illegalAscent || illegalHover || illegalAirSpeed) {
                Check flyCheck = manager.getCheck("fly");
                String reason = illegalAscent ? "Type H (Boat Ascent)" :
                                (illegalHover ? "Type H (Boat Hover)" : "Type H (Boat Speed Fly)");

                if (flyCheck != null && flyCheck.isEnabled()) {
                    flyCheck.fail(player, data, reason, 2.0,
                            String.format("Vehicle Fly detected (dY=%.4f, dXZ=%.4f, nearGround=%b, inLiquid=%b)",
                                    deltaY, deltaXZ, nearGround, inLiquid));
                }

                vehicle.removePassenger(player);
                player.leaveVehicle();

                Location setbackLoc = data.getLastGroundLocation();
                if (setbackLoc != null && setbackLoc.getWorld() != null) {
                    data.setAnticheatSetback(true);
                    data.resetMovementState(setbackLoc);
                    try {
                        player.teleport(setbackLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                        vehicle.teleport(setbackLoc);
                    } catch (Throwable t) {
                        player.teleport(setbackLoc);
                    }
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.getOrCreatePlayerData(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.removePlayerData(event.getPlayer().getUniqueId());
    }
}
