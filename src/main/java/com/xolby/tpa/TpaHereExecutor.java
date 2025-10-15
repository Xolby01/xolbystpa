package com.xolby.tpa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TpaHereExecutor implements CommandExecutor {
    private final XolbyTpaPlugin pl;
    public TpaHereExecutor(XolbyTpaPlugin pl){ this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("xolby.tpa.here")) {
            p.sendMessage(pl.mm("prefix") + "You lack permission.");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(pl.mm("prefix") + "Usage: /tpahere <player>");
            return true;
        }
        if (!pl.canUseTpa(p)) return true;

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(pl.mm("prefix") + pl.mm("player_not_found"));
            return true;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(pl.mm("prefix") + pl.mm("self_request"));
            return true;
        }
        var existing = pl.getOutgoing(p);
        if (existing != null && existing.getTarget().equals(target.getUniqueId())) {
            Map<String,String> v = new HashMap<>();
            v.put("target", target.getName());
            pl.msg(p, "already_requested", v);
            return true;
        }
        pl.createRequest(p, target, PendingRequest.Direction.TARGET_TO_SENDER);
        return true;
    }
}
