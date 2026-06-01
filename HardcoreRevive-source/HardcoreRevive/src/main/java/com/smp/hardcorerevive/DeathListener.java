package com.smp.hardcorerevive;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathListener implements Listener {

    private final HardcoreRevive plugin;

    public DeathListener(HardcoreRevive plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Drop la tête du joueur
        ItemStack skull = createPlayerHead(player);
        player.getWorld().dropItemNaturally(player.getLocation(), skull);

        // Marque le joueur comme mort
        plugin.getDeadPlayerManager().markAsDead(player);

        // Message global
        Bukkit.broadcastMessage("§c☠ §l" + player.getName() + "§r§c est mort ! Vous avez §l24h §r§cpour ramasser sa tête !");

        event.setKeepInventory(false);
        event.getDrops().clear(); // On ne drop que la tête
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) return;

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.PLAYER_HEAD) return;
        if (item.getItemMeta() == null) return;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta.getOwnerProfile() == null) return;
        if (meta.getLore() == null || meta.getLore().isEmpty()) return;

        // Vérifie que c'est bien une tête de mort (lore contient notre marqueur)
        String deadName = meta.getOwnerProfile().getName();
        if (deadName == null) return;

        // Cherche le joueur mort correspondant
        for (UUID uuid : plugin.getDeadPlayerManager().getDeadPlayers()) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null && op.getName().equalsIgnoreCase(deadName)) {
                // Annule le timer si pas déjà ramassée
                if (!plugin.getDeadPlayerManager().isHeadPickedUp(uuid)) {
                    plugin.getDeadPlayerManager().markHeadPickedUp(uuid);
                    picker.sendMessage("§a✅ Vous avez ramassé la tête de §l" + deadName + "§r§a ! Construisez l'autel pour le réanimer.");
                }
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getDeadPlayerManager().isDead(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                long remaining = plugin.getDeadPlayerManager().getRemainingTimeMs(player.getUniqueId());
                if (remaining > 0) {
                    long hours = remaining / 3600000;
                    long minutes = (remaining % 3600000) / 60000;
                    player.sendMessage("§c§lVous êtes toujours mort. §r§cTemps restant pour que votre tête soit ramassée : §l" + hours + "h " + minutes + "min");
                } else if (plugin.getDeadPlayerManager().isHeadPickedUp(player.getUniqueId())) {
                    player.sendMessage("§a✅ Votre tête a été ramassée ! Vos amis peuvent vous réanimer à tout moment.");
                }
            }, 5L);
        }
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        PlayerProfile profile = player.getPlayerProfile();
        meta.setPlayerProfile(profile);
        meta.setDisplayName("§c☠ Tête de §l" + player.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§7Posez cette tête sur un §fBloc d'Obsidienne");
        lore.add("§7avec §f2 Totems §7et §f2 Diamants");
        lore.add("§7pour réanimer §l" + player.getName());
        lore.add("§c⚠ Ramassez-la dans les §l24h §r§csinon ban définitif !");
        meta.setLore(lore);

        skull.setItemMeta(meta);
        return skull;
    }
}
