package com.xolby.tpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private int cooldownSeconds = 60;
    private int requestExpireSeconds = 120;
    private int teleportDelaySeconds = 3;
    private boolean cancelOnMove = true;
    private Map<String,String> messages = new HashMap<>();

    public ConfigManager(File file) {
        this.file = file;
    }

    public void load() {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root.has("cooldown_seconds")) cooldownSeconds = root.get("cooldown_seconds").getAsInt();
            if (root.has("request_expire_seconds")) requestExpireSeconds = root.get("request_expire_seconds").getAsInt();
            if (root.has("teleport_delay_seconds")) teleportDelaySeconds = root.get("teleport_delay_seconds").getAsInt();
            if (root.has("cancel_on_move")) cancelOnMove = root.get("cancel_on_move").getAsBoolean();
            if (root.has("messages")) {
                for (var e : root.getAsJsonObject("messages").entrySet()) {
                    messages.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getRequestExpireSeconds() { return requestExpireSeconds; }
    public int getTeleportDelaySeconds() { return teleportDelaySeconds; }
    public boolean isCancelOnMove() { return cancelOnMove; }
    public String getMessage(String key) { return messages.getOrDefault(key, ""); }
}
