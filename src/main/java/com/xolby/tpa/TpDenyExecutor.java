package com.xolby.tpa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpDenyExecutor implements CommandExecutor {
    private final XolbyTpaPlugin pl;
    public TpDenyExecutor(XolbyTpaPlugin pl){ this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.hasPermission("xolby.tpa.deny")) { p.sendMessage(pl.mm("prefix") + "You lack permission."); return true; }
        pl.deny(p);
        return true;
    }
}
