package com.falconcore.anticheat.check.movement;

import com.falconcore.anticheat.AntiCheatManager;
import com.falconcore.anticheat.check.Check;
import com.falconcore.anticheat.check.CheckCategory;
import com.falconcore.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class SpeedCheck extends Check {

    private boolean typeAEnabled = true;
    private double typeAMaxBaseSpeed = 0.287;
    private double typeASprintJumpBuffer = 0.35;
    private double typeAVlIncrement = 1.5;

    private boolean typeBEnabled = true;
    private double typeBAirFriction = 0.91;
    private double typeBMaxStrafeAccel = 0.035;
    private double typeBVlIncrement = 2.0;

    private boolean typeCEnabled = true;
    private double typeCMaxIceSpeed = 1.25;
    private double typeCVlIncrement = 1.5;

    private boolean typeDEnabled = true;
    private double typeDMaxSneakSpeed = 0.15;
    private double typeDMaxUsingItemSpeed = 0.16;
    private double typeDVlIncrement = 2.0;

    private boolean typeEEnabled = true;
    private double typeEMaxWaterSpeed = 0.22;
    private double typeEVlIncrement = 1.5;

    private boolean typeFEnabled = true;
    private double typeFMaxAngleDeviation = 65.0;
    private double typeFVlIncrement = 1.5;

    public SpeedCheck(AntiCheatManager manager) {
        super(manager, "speed", "Speed", CheckCategory.MOVEMENT, "Detects unnatural movement speed, bunny-hopping, omni-sprint, and NoSlow.");
    }

    @Override
    public void process(Player player, PlayerData data, Location from, Location to) {
        if (!enabled) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;

        if (player.hasPermission(manager.getBypassPermission())) return;

        if (manager.isIgnoreBedrock() && data.isBedrock()) return;

        if (data.hasHardGrace()) return;
        if (data.getExplosionTicks() > 0 || data.getElytraTicks() > 0) return;

        double deltaXZ = data.getDeltaXZ();
        double lastDeltaXZ = data.getLastDeltaXZ();
        double deltaY = data.getDeltaY();
        int airTicks = data.getAirTicks();
        int groundTicks = data.getGroundTicks();
        int jumpTicks = data.getJumpTicks();

        boolean inWaterOrRain = player.isInWaterOrRain() || (player.getWorld().hasStorm() && player.getLocation().getY() >= player.getWorld().getHighestBlockYAt(player.getLocation()) - 1);
        boolean isLegitRiptide = (data.isRiptiding() && inWaterOrRain) || data.getRiptideTicks() > 0;

        double velBonusXZ = 0.0;
        if (data.getVelocityTicks() > 0 && data.getLastVelocity() != null) {
            velBonusXZ = Math.hypot(data.getLastVelocity().getX(), data.getLastVelocity().getZ());
        }
        if (isLegitRiptide) {
            velBonusXZ += 2.5;
        }

        int speedBoostLevel = data.getPotionAmplifier(player, PotionEffectType.SPEED);
        int slownessLevel = data.getPotionAmplifier(player, PotionEffectType.SLOWNESS);
        double potionMultiplier = 1.0 + (speedBoostLevel * 0.20) - (slownessLevel * 0.15);
        if (potionMultiplier < 0.2) potionMultiplier = 0.2;

        if (typeDEnabled && data.isOnGround() && !data.hasIceFriction() && !data.isUnderLowCeiling() && velBonusXZ <= 0.08) {
            if (player.isSneaking() && data.getSneakTicks() >= 8 && groundTicks >= 5 && Math.abs(deltaY) < 0.01 && !data.isNearWall()) {
                double maxSneak = (typeDMaxSneakSpeed * potionMultiplier) + 0.06;
                if (deltaXZ > maxSneak) {
                    fail(player, data, "Type D (Fast Sneak)", typeDVlIncrement,
                            String.format("Exceeded sneak speed limit (dXZ=%.4f > max=%.4f, sneakTicks=%d)", deltaXZ, maxSneak, data.getSneakTicks()));
                    return;
                }
            }

            if (data.isUsingItem() && data.getUsingItemTicks() >= 8 && groundTicks >= 5 && Math.abs(deltaY) < 0.01 && !data.isNearWall()) {
                double maxUsing = (typeDMaxUsingItemSpeed * potionMultiplier) + 0.07;
                if (deltaXZ > maxUsing) {
                    fail(player, data, "Type D (NoSlow)", typeDVlIncrement,
                            String.format("Exceeded item-use speed limit (dXZ=%.4f > max=%.4f, usingTicks=%d)",
                                    deltaXZ, maxUsing, data.getUsingItemTicks()));
                    return;
                }
            }
        }

        if (typeEEnabled && (data.isInWater() || data.isInLava()) && !isLegitRiptide && data.getRiptideTicks() <= 0) {
            int depthStrider = data.getDepthStriderLevel();
            int dolphinsGrace = data.getPotionAmplifier(player, PotionEffectType.DOLPHINS_GRACE);
            double baseWater = (typeEMaxWaterSpeed * (1.0 + (depthStrider * 0.35)) * (1.0 + (dolphinsGrace * 1.0)) * potionMultiplier) + velBonusXZ + 0.06;

            double frictionDecelLimit = 0.0;
            if (lastDeltaXZ > 0.20) {
                double waterFriction = 0.85 + (depthStrider * 0.035);
                frictionDecelLimit = (lastDeltaXZ * waterFriction) + (0.06 * potionMultiplier) + velBonusXZ + 0.05;
            }
            double maxWater = Math.max(baseWater, frictionDecelLimit);

            if (deltaXZ > maxWater && !data.hasIceFriction()) {
                fail(player, data, "Type E (Water Speed)", typeEVlIncrement,
                        String.format("Exceeded water speed limit (dXZ=%.4f > max=%.4f, ds=%d, dg=%d)",
                                deltaXZ, maxWater, depthStrider, dolphinsGrace));
                return;
            }
        }

        if (typeFEnabled && data.isOnGround() && groundTicks >= 4 && !data.hadVelocityThisAir() && player.isSprinting() && deltaXZ > 0.26 && !data.isInWater() && !data.hasIceFriction() && !data.isNearWall() && data.getVelocityTicks() <= 0 && lastDeltaXZ <= 0.35) {
            double angleDeviation = data.getMovementAngleDeviation(player);
            double maxAngle = Math.max(typeFMaxAngleDeviation, 85.0);
            if (angleDeviation > maxAngle) {
                fail(player, data, "Type F (Omni-Sprint)", typeFVlIncrement,
                        String.format("Sprinting in illegal direction (angle=%.1f deg > max=%.1f deg, dXZ=%.4f)",
                                angleDeviation, maxAngle, deltaXZ));
                return;
            }
        }

        if (typeCEnabled && data.hasIceFriction()) {
            double maxIce = typeCMaxIceSpeed + (speedBoostLevel * 0.20);
            if (deltaXZ > maxIce) {
                fail(player, data, "Type C (Ice Speed)", typeCVlIncrement,
                        String.format("Exceeded ice sliding speed limit (dXZ=%.4f > max=%.4f)", deltaXZ, maxIce));
                return;
            }
            return;
        }

        if (typeBEnabled && airTicks >= 2 && !data.isBouncedOnSlime() && !data.isBouncedOnBed() && !isLegitRiptide && data.getRiptideTicks() <= 0) {
            if (lastDeltaXZ > 0.12) {
                double ceilingAirBonus = data.isUnderLowCeiling() ? 0.18 : 0.0;
                double wallAirBonus = data.isNearWall() ? 0.08 : 0.0;
                double highSpeedTolerance = lastDeltaXZ > 0.40 ? (lastDeltaXZ * 0.04) : 0.0;
                double expectedMaxAirSpeed = (lastDeltaXZ * typeBAirFriction) + typeBMaxStrafeAccel + (speedBoostLevel * 0.015) + ceilingAirBonus + wallAirBonus + velBonusXZ + highSpeedTolerance;
                double diff = deltaXZ - expectedMaxAirSpeed;

                if (diff > 0.055) {
                    fail(player, data, "Type B (Bhop)", typeBVlIncrement,
                            String.format("Airborne acceleration exceeded (dXZ=%.4f > exp=%.4f, diff=%.4f, airTicks=%d)",
                                    deltaXZ, expectedMaxAirSpeed, diff, airTicks));
                    return;
                }
            }
        }

        if (typeAEnabled && (data.isOnGround() || groundTicks > 0) && !data.isInWater() && !data.isInLava()) {
            double soulSpeedBonus = 0.0;
            if (data.isOnSoulSand() && data.getSoulSpeedLevel() > 0) {
                soulSpeedBonus = 0.10 + (data.getSoulSpeedLevel() * 0.06);
            }

            double ceilingBonus = 0.0;
            if (data.isUnderLowCeiling()) {
                ceilingBonus = 0.35;
            }

            double wallBonus = 0.0;
            if (data.isNearWall()) {
                wallBonus = 0.08;
            }

            double jumpBuffer = 0.0;
            if (player.isSprinting() || jumpTicks >= 1 || deltaY > 0.05 || groundTicks <= 3 || data.isUnderLowCeiling() || data.isNearWall() || lastDeltaXZ > 0.30) {
                jumpBuffer = typeASprintJumpBuffer;
            }

            double frictionDecelLimit = 0.0;
            if (lastDeltaXZ > 0.20) {
                double groundFriction = 0.546;
                double sprintAccel = 0.13 * potionMultiplier;
                boolean freshLiquidLanding = (groundTicks >= 8 && groundTicks <= 12 && data.hadVelocityThisAir());
                double landingBuffer = (lastDeltaXZ > 0.30 && (groundTicks <= 4 || freshLiquidLanding)) ? (lastDeltaXZ * 0.18) : 0.0;
                frictionDecelLimit = (lastDeltaXZ * groundFriction) + sprintAccel + ceilingBonus + wallBonus + velBonusXZ + landingBuffer + 0.045;
            }

            double baseGroundSpeed = (typeAMaxBaseSpeed * potionMultiplier) + soulSpeedBonus + jumpBuffer + ceilingBonus + wallBonus + velBonusXZ + 0.045;
            double maxGroundSpeed = Math.max(baseGroundSpeed, frictionDecelLimit);

            if (deltaXZ > maxGroundSpeed) {
                fail(player, data, "Type A (Ground Speed)", typeAVlIncrement,
                        String.format("Exceeded maximum ground speed (dXZ=%.4f > max=%.4f, last=%.4f, ceil=%s, wall=%s, grnd=%d)",
                                deltaXZ, maxGroundSpeed, lastDeltaXZ, data.isUnderLowCeiling(), data.isNearWall(), groundTicks));
                return;
            }

            double flatSprintLimit = (0.360 * potionMultiplier) + soulSpeedBonus + wallBonus + velBonusXZ + 0.045;
            if (!data.isUnderLowCeiling() && !data.isNearWall() && !data.hadVelocityThisAir()
                    && groundTicks >= 10 && Math.abs(deltaY) < 0.005 && deltaXZ > flatSprintLimit) {
                fail(player, data, "Type A (Ground Speed)", typeAVlIncrement,
                        String.format("Sustained flat ground speed without jumping (dXZ=%.4f > max=%.4f, grnd=%d)",
                                deltaXZ, flatSprintLimit, groundTicks));
            }
        }
    }

    @Override
    public void reloadConfig(FileConfiguration config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.setbackEnabled = config.getBoolean("setback.enabled", true);
        this.alertVl = config.getDouble("violations.alert-threshold", 2.0);
        this.maxVl = config.getDouble("violations.punishment-threshold", 25.0);

        this.typeAEnabled = config.getBoolean("subchecks.type-a.enabled", true);
        this.typeAMaxBaseSpeed = config.getDouble("subchecks.type-a.max-base-speed", 0.287);
        this.typeASprintJumpBuffer = config.getDouble("subchecks.type-a.sprint-jump-buffer", 0.35);
        this.typeAVlIncrement = config.getDouble("subchecks.type-a.vl-increment", 1.5);

        this.typeBEnabled = config.getBoolean("subchecks.type-b.enabled", true);
        this.typeBAirFriction = config.getDouble("subchecks.type-b.air-friction", 0.91);
        this.typeBMaxStrafeAccel = config.getDouble("subchecks.type-b.max-strafe-accel", 0.035);
        this.typeBVlIncrement = config.getDouble("subchecks.type-b.vl-increment", 2.0);

        this.typeCEnabled = config.getBoolean("subchecks.type-c.enabled", true);
        this.typeCMaxIceSpeed = config.getDouble("subchecks.type-c.max-ice-speed", 1.25);
        this.typeCVlIncrement = config.getDouble("subchecks.type-c.vl-increment", 1.5);

        this.typeDEnabled = config.getBoolean("subchecks.type-d.enabled", true);
        this.typeDMaxSneakSpeed = config.getDouble("subchecks.type-d.max-sneak-speed", 0.15);
        this.typeDMaxUsingItemSpeed = config.getDouble("subchecks.type-d.max-using-item-speed", 0.16);
        this.typeDVlIncrement = config.getDouble("subchecks.type-d.vl-increment", 2.0);

        this.typeEEnabled = config.getBoolean("subchecks.type-e.enabled", true);
        this.typeEMaxWaterSpeed = config.getDouble("subchecks.type-e.max-water-speed", 0.22);
        this.typeEVlIncrement = config.getDouble("subchecks.type-e.vl-increment", 1.5);

        this.typeFEnabled = config.getBoolean("subchecks.type-f.enabled", true);
        this.typeFMaxAngleDeviation = config.getDouble("subchecks.type-f.max-angle-deviation", 65.0);
        this.typeFVlIncrement = config.getDouble("subchecks.type-f.vl-increment", 1.5);
    }
}
