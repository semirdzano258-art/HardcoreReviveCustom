package com.smp.hardcorerevive;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

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
            sender.sendMessage("\u00a7cUsage:");
            sender.sendMessage("\u00a7c/revive <joueur>");
            sender.sendMessage("\u00a7c/revive reset <joueur>");
            sender.sendMessage("\u00a7c/revive head <joueur>");
            return true;
        }

        // /revive reset <joueur>
        if (args[0].equalsIgnoreCase("reset") && args.length >= 2) {
            UUID targetUUID = getUUID(args[1]);
            if (targetUUID == null) {
                sender.sendMessage("\u00a7cJoueur introuvable : " + args[1]);
                return true;
            }
            plugin.getDeadPlayerManager().resetDeathCount(targetUUID);
            sender.sendMessage("\u00a7aCompteur de morts de \u00a7l" + args[1] + "\u00a7r\u00a7a remis a zero !");
            return true;
        }

        // /revive head <joueur> - donne la vraie tete avec le bon lore
        if (args[0].equalsIgnoreCase("head") && args.length >= 2) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage("\u00a7cCette commande doit etre executee en jeu.");
                return true;
            }

            String targetName = args[1];
            UUID targetUUID = getUUID(targetName);

            if (targetUUID == null) {
                sender.sendMessage("\u00a7cJoueur introuvable : " + targetName);
                return true;
            }

            if (!plugin.getDeadPlayerManager().isDead(targetUUID)) {
                sender.sendMessage("\u00a7c" + targetName + " n'est pas mort sur ce serveur !");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            ReviveCost cost = plugin.getDeadPlayerManager().getReviveCost(targetUUID);
            int deaths = plugin.getDeadPlayerManager().getDeathCount(targetUUID);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName("\u00a7c\u2620 \u00a7lAme de \u00a7r\u00a7c\u00a7l" + targetName + " \u00a77(Mort n\u00b0" + deaths + ")");

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("\u00a78\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac");
            lore.add("\u00a76\u2756 \u00a7lObjet de Reanimation");
            lore.add("\u00a77Cette tete renferme l'ame de");
            lore.add("\u00a7f\u00a7l" + targetName + "\u00a7r\u00a77, prisonnier de l'au-dela.");
            lore.add("");
            lore.add("\u00a7e\u2699 \u00a7lComment reanimat :");
            lore.add("\u00a77Posez cette tete sur un");
            lore.add("\u00a7f\u00a7nBloc d'Obsidienne\u00a7r\u00a77 avec les");
            lore.add("\u00a77ressources ci-dessous.");
            lore.add("");
            lore.addAll(cost.toLore());
            lore.add("");
            lore.add("\u00a78\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac");

            meta.setLore(lore);
            skull.setItemMeta(meta);

            admin.getInventory().addItem(skull);
            sender.sendMessage("\u00a7aTete de \u00a7l" + targetName + "\u00a7r\u00a7a ajoutee dans votre inventaire !");
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
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op != null && op.hasPlayedBefore()) return op.getUniqueId();
        return null;
    }
}
