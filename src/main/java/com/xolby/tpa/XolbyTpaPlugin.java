package com.xolby.tpa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XolbyTpaPlugin extends JavaPlugin implements Listener {

    private ConfigManager config;
    private final Map<UUID, PendingRequest> pendingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRequest> pendingBySender = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTpaUse = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> teleportStartLoc = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultJsonConfig();
        this.config = new ConfigManager(new File(getDataFolder(), "config.json"));
        this.config.load();

        getServer().getPluginManager().registerEvents(this, this);

        register("tpa", new TpaExecutor(this));
        register("tpahere", new TpaHereExecutor(this));
        register("tpaccept", new TpAcceptExecutor(this));
        register("tpacancel", new TpCancelExecutor(this));
        register("tpadeny", new TpDenyExecutor(this));

        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (PendingRequest pr : new ArrayList<>(pendingByTarget.values())) {
                    if (pr.isExpired(now, config.getRequestExpireSeconds())) {
                        expireRequest(pr);
                    }
                }
            }
        }.runTaskTimer(this, 20L*5, 20L*5);

        getLogger().info("Xolbys Tpa enabled.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : teleportTasks.values()) {
            task.cancel();
        }
        teleportTasks.clear();
    }

    private void register(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand pc = getCommand(name);
        if (pc != null) {
            pc.setExecutor(exec);
        } else {
            getLogger().severe("Command not found in plugin.yml: " + name);
        }
    }

    private void saveDefaultJsonConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        File cfg = new File(getDataFolder(), "config.json");
        if (!cfg.exists()) {
            try (InputStream in = getResource("config.json")) {
                if (in != null) {
                    try (FileOutputStream out = new FileOutputStream(cfg)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    public ConfigManager getCfg() { return config; }

    public String mm(String key) { return color(config.getMessage(key)); }
    public String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    public void msg(Player p, String key) { p.sendMessage(mm("prefix") + mm(key)); }
    public void msg(Player p, String key, Map<String,String> vars) {
        String m = config.getMessage(key);
        if (m == null) m = "";
        for (Map.Entry<String,String> e : vars.entrySet()) m = m.replace("%"+e.getKey()+"%", e.getValue());
        p.sendMessage(color(config.getMessage("prefix") + m));
    }

    public boolean canUseTpa(Player p) {
        if (p.hasPermission("xolby.tpa.bypasscooldown")) return true;
        long cd = config.getCooldownSeconds() * 1000L;
        long now = System.currentTimeMillis();
        long last = lastTpaUse.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < cd) {
            long remain = (cd - (now - last))/1000L + 1;
            Map<String,String> v = new HashMap<>();
            v.put("seconds", String.valueOf(remain));
            msg(p, "on_cooldown", v);
            return false;
        }
        lastTpaUse.put(p.getUniqueId(), now);
        return true;
    }

    public void createRequest(Player sender, Player target, PendingRequest.Direction dir) {
        cancelExisting(sender);
        cancelExisting(target);

        PendingRequest pr = new PendingRequest(sender.getUniqueId(), target.getUniqueId(), System.currentTimeMillis(), dir);
        pendingByTarget.put(target.getUniqueId(), pr);
        pendingBySender.put(sender.getUniqueId(), pr);

        Map<String,String> vars = new HashMap<>();
        vars.put("target", target.getName());
        Map<String,String> vars2 = new HashMap<>();
        vars2.put("sender", sender.getName());

        if (dir == PendingRequest.Direction.SENDER_TO_TARGET) {
            msg(sender, "request_sent", vars);
            msg(target, "request_received", vars2);
        } else {
            msg(sender, "request_sent_here", vars);
            msg(target, "request_received_here", vars2);
        }
    }

    public void cancelExisting(Player p) {
        PendingRequest prT = pendingByTarget.remove(p.getUniqueId());
        if (prT != null) pendingBySender.remove(prT.getSender());
        PendingRequest prS = pendingBySender.remove(p.getUniqueId());
        if (prS != null) pendingByTarget.remove(prS.getTarget());
    }

    public PendingRequest getIncoming(Player target) { return pendingByTarget.get(target.getUniqueId()); }
    public PendingRequest getOutgoing(Player sender) { return pendingBySender.get(sender.getUniqueId()); }

    public void accept(Player target) {
        PendingRequest pr = getIncoming(target);
        if (pr == null) { msg(target, "no_request"); return; }
        Player sender = Bukkit.getPlayer(pr.getSender());
        Player tgt = Bukkit.getPlayer(pr.getTarget());
        if (sender == null || !sender.isOnline() || tgt == null || !tgt.isOnline()) {
            pendingByTarget.remove(target.getUniqueId());
            if (pr != null) pendingBySender.remove(pr.getSender());
            msg(target, "no_request"); return;
        }

        Player teleported;
        org.bukkit.Location destination;
        if (pr.getDirection() == PendingRequest.Direction.SENDER_TO_TARGET) {
            teleported = sender;
            destination = tgt.getLocation();
        } else {
            teleported = tgt;
            destination = sender.getLocation();
        }

        Map<String,String> vSender = new HashMap<>();
        vSender.put("target", tgt.getName());
        Map<String,String> vTarget = new HashMap<>();
        vTarget.put("sender", sender.getName());

        int delay = config.getTeleportDelaySeconds();
        String tMsg = delay > 0 ? mm("teleporting_in").replace("%seconds%", String.valueOf(delay)) : mm("teleport_complete");
        vSender.put("teleport_msg", tMsg);

        msg(sender, "accepted_sender", vSender);
        msg(tgt, "accepted_target", vTarget);

        if (delay > 0 && teleported.isOnline()) {
            Map<String,String> tmp = new HashMap<>();
            tmp.put("seconds", String.valueOf(delay));
            msg(teleported, "teleporting_in", tmp);
        }

        if (delay <= 0) {
            doTeleport(teleported, destination);
        } else {
            if (config.isCancelOnMove()) teleportStartLoc.put(teleported.getUniqueId(), teleported.getLocation().clone());
            BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() {
                    doTeleport(teleported, destination);
                    teleportTasks.remove(teleported.getUniqueId());
                    teleportStartLoc.remove(teleported.getUniqueId());
                }
            }.runTaskLater(this, delay * 20L);
            teleportTasks.put(teleported.getUniqueId(), task);
        }

        pendingByTarget.remove(tgt.getUniqueId());
        pendingBySender.remove(pr.getSender());
    }

    private void doTeleport(Player p, org.bukkit.Location to) {
        p.teleport(to);
        msg(p, "teleport_complete");
    }

    public void deny(Player target) {
        PendingRequest pr = getIncoming(target);
        if (pr == null) { msg(target, "no_request"); return; }
        Player sender = Bukkit.getPlayer(pr.getSender());
        if (sender != null && sender.isOnline()) {
            Map<String,String> v = new HashMap<>();
            v.put("target", target.getName());
            msg(sender, "denied_sender", v);
        }
        Map<String,String> v2 = new HashMap<>();
        v2.put("sender", sender != null ? sender.getName() : "Unknown");
        msg(target, "denied_target", v2);
        pendingByTarget.remove(target.getUniqueId());
        pendingBySender.remove(pr.getSender());
    }

    public void cancel(Player sender) {
        PendingRequest pr = getOutgoing(sender);
        if (pr == null) { msg(sender, "no_request"); return; }
        Player target = Bukkit.getPlayer(pr.getTarget());
        if (target != null && target.isOnline()) {
            Map<String,String> v = new HashMap<>();
            v.put("sender", sender.getName());
            msg(target, "request_cancelled_sender_info", v);
        }
        Map<String,String> v2 = new HashMap<>();
        v2.put("target", target != null ? target.getName() : "Unknown");
        msg(sender, "request_cancelled", v2);
        pendingBySender.remove(sender.getUniqueId());
        pendingByTarget.remove(pr.getTarget());
    }

    private void expireRequest(PendingRequest pr) {
        Player sender = Bukkit.getPlayer(pr.getSender());
        Player target = Bukkit.getPlayer(pr.getTarget());
        if (sender != null && sender.isOnline()) {
            Map<String,String> v = new HashMap<>();
            v.put("target", target != null ? target.getName() : "Unknown");
            msg(sender, "request_expired_sender", v);
        }
        if (target != null && target.isOnline()) {
            Map<String,String> v2 = new HashMap<>();
            v2.put("sender", sender != null ? sender.getName() : "Unknown");
            msg(target, "request_expired_target", v2);
        }
        pendingByTarget.remove(pr.getTarget());
        pendingBySender.remove(pr.getSender());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!config.isCancelOnMove()) return;
        Player p = e.getPlayer();
        BukkitTask task = teleportTasks.get(p.getUniqueId());
        if (task == null) return;
        org.bukkit.Location start = teleportStartLoc.get(p.getUniqueId());
        if (start == null) return;
        if (e.getTo() == null) return;
        if (e.getTo().getWorld() != start.getWorld()) {
            task.cancel();
            teleportTasks.remove(p.getUniqueId());
            teleportStartLoc.remove(p.getUniqueId());
            msg(p, "moved_cancel");
            return;
        }
        if (e.getTo().distanceSquared(start) > 0.01) {
            task.cancel();
            teleportTasks.remove(p.getUniqueId());
            teleportStartLoc.remove(p.getUniqueId());
            msg(p, "moved_cancel");
        }
    }
}
