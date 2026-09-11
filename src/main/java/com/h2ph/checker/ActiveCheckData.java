package com.h2ph.checker;

import org.bukkit.Location;
import org.bukkit.block.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveCheckData {

    private final UUID playerUUID;
    private final UUID initiatorUUID;
    private final List<List<HackDefinition>> batches;
    private final boolean autoCheck;
    private final String reason;
    private int currentBatchIndex = 0;

    private Location signLocation;
    private BlockState originalState;
    private boolean barrierPlaced;
    private Location barrierLocation;

    private long timeoutToken;
    private final Map<String, HackResult> results = new ConcurrentHashMap<>();

    public ActiveCheckData(UUID playerUUID, UUID initiatorUUID, List<List<HackDefinition>> batches, boolean autoCheck, String reason) {
        this.playerUUID = playerUUID;
        this.initiatorUUID = initiatorUUID;
        this.batches = batches != null ? batches : Collections.emptyList();
        this.autoCheck = autoCheck;
        this.reason = reason != null ? reason : "Manual";
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public UUID getInitiatorUUID() {
        return initiatorUUID;
    }

    public List<List<HackDefinition>> getBatches() {
        return batches;
    }

    public boolean isAutoCheck() {
        return autoCheck;
    }

    public String getReason() {
        return reason;
    }

    public int getCurrentBatchIndex() {
        return currentBatchIndex;
    }

    public void incrementBatch() {
        this.currentBatchIndex++;
    }

    public void advanceBatch() {
        this.currentBatchIndex++;
    }

    public boolean hasMoreBatches() {
        return currentBatchIndex < batches.size();
    }

    public boolean isFinished() {
        return !hasMoreBatches();
    }

    public void recordResult(String hackId, HackResult result) {
        this.results.put(hackId, result);
    }

    public List<HackDefinition> getCurrentBatch() {
        if (currentBatchIndex >= 0 && currentBatchIndex < batches.size()) {
            return batches.get(currentBatchIndex);
        }
        return Collections.emptyList();
    }

    public Location getSignLocation() {
        return signLocation;
    }

    public void setSignLocation(Location signLocation) {
        this.signLocation = signLocation;
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public void setOriginalState(BlockState originalState) {
        this.originalState = originalState;
    }

    public boolean isBarrierPlaced() {
        return barrierPlaced;
    }

    public void setBarrierPlaced(boolean barrierPlaced) {
        this.barrierPlaced = barrierPlaced;
    }

    public Location getBarrierLocation() {
        return barrierLocation;
    }

    public void setBarrierLocation(Location barrierLocation) {
        this.barrierLocation = barrierLocation;
    }

    public long getTimeoutToken() {
        return timeoutToken;
    }

    public void setTimeoutToken(long timeoutToken) {
        this.timeoutToken = timeoutToken;
    }

    public Map<String, HackResult> getResults() {
        return results;
    }
}

