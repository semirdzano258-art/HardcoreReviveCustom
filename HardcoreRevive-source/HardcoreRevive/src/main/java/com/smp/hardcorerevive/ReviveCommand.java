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
            sender.sendMessage("\u00a7cVous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("\u00a7cUsage: /revive <joueur> ou /revive reset <joueur>");
            return true;
        }

        // /revive reset <joueur>
        if (args[0].equalsIgnoreCase("reset") && args.length >= 2) {
            String targetName = args[1];
            UUID targetUUID = getUUID(targetName);

            if (targetUUID == null) {
                sender.sendMessage("\u00a7cJoueur introuvable : " + targetName);
                return true;
            }

            plugin.getDeadPlayerManager().resetDeathCount(targetUUID);
            sender.sendMessage("\u00a7aCompteur de morts de \u00a7l" + targetName + "\u00a7r\u00a7a remis a zero !");
            return true;
        }

        // /revive <joueur>
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
            sender.sendMessage("\u00a7c" + targetName + " n'est pas dans la liste des joueurs morts.");
            return true;
        }

        plugin.getDeadPlayerManager().revivePlayer(targetUUID);
        sender.sendMessage("\u00a7a" + targetName + " a ete reamine(e) par commande admin !");
        Bukkit.broadcastMessage("\u00a7a[Admin] \u00a7l" + targetName + "\u00a7r\u00a7a a ete reamine(e) par un administrateur.");
        return true;
    }

    private UUID getUUID(String name) {
        for (UUID uuid : plugin.getDeadPlayerManager().getDeadPlayers()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return uuid;
        }
        // Cherche aussi les joueurs pas morts
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op != null && op.hasPlayedBefore()) return op.getUniqueId();
        return null;
    }
}
