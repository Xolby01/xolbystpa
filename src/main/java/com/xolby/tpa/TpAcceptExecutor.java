package com.xolby.tpa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpAcceptExecutor implements CommandExecutor {
    private final XolbyTpaPlugin pl;
    public TpAcceptExecutor(XolbyTpaPlugin pl){ this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.hasPermission("xolby.tpa.accept")) { p.sendMessage(pl.mm("prefix") + "You lack permission."); return true; }
        pl.accept(p);
        return true;
    }
}
