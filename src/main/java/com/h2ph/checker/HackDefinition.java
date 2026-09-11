package com.h2ph.checker;

public class HackDefinition {

    private final String id;
    private final String displayName;
    private final String key;
    private final String fallback;
    private final DetectionMode mode;
    private final String lowerKey;
    private final String lowerFallback;

    public HackDefinition(String id, String displayName, String key, String fallback, DetectionMode mode) {
        this.id = id != null ? id : key;
        this.displayName = displayName != null ? displayName : id;
        this.key = key;
        this.fallback = fallback != null && !fallback.isEmpty() ? fallback : "fallb";
        this.mode = mode != null ? mode : DetectionMode.TRANSLATE;
        this.lowerKey = this.key.toLowerCase();
        this.lowerFallback = this.fallback.toLowerCase();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKey() {
        return key;
    }

    public String getFallback() {
        return fallback;
    }

    public DetectionMode getMode() {
        return mode;
    }

    public String getLowerKey() {
        return lowerKey;
    }

    public String getLowerFallback() {
        return lowerFallback;
    }

    @Override
    public String toString() {
        return "HackDefinition{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", key='" + key + '\'' +
                ", mode=" + mode +
                '}';
    }
}

