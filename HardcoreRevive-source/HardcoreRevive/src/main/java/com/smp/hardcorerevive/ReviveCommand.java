package com.smp.hardcorerevive;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class ReviveCommand implements CommandExecutor {

    private final HardcoreRevive plugin;

    public ReviveCommand(HardcoreRevive plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hardcorerevive.admin")) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /revive <joueur>");
            return true;
        }

        String targetName = args[0];
        UUID targetUUID = null;

        for (UUID uuid : plugin.getDeadPlayerManager().getDeadPlayers()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                targetUUID = uuid;
                break;
            }
        }

        if (targetUUID == null) {
            sender.sendMessage("§c" + targetName + " n'est pas dans la liste des joueurs morts.");
            return true;
        }

        plugin.getDeadPlayerManager().revivePlayer(targetUUID);
        sender.sendMessage("§a" + targetName + " a été réanimé(e) par commande admin !");
        Bukkit.broadcastMessage("§a[Admin] §l" + targetName + "§r§a a été réanimé(e) par un administrateur.");
        return true;
    }
}
