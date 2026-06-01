package com.smp.hardcorerevive;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AltarListener implements Listener {

    private final HardcoreRevive plugin;
    // Autel en cours d'activation (pour éviter double activation)
    private final Set<Location> activatingAltars = new HashSet<>();

    public AltarListener(HardcoreRevive plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player placer = event.getPlayer();
        Block placed = event.getBlockPlaced();

        // Vérifie si c'est une tête de joueur
        if (placed.getType() != Material.PLAYER_HEAD && placed.getType() != Material.PLAYER_WALL_HEAD) return;

        // Vérifie si posé sur de la Pierre Taillée Ciselée
        Block below = placed.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.OBSIDIAN) return;

        // Récupère le nom du joueur depuis la tête
        ItemStack item = event.getItemInHand();
        if (item.getItemMeta() == null) return;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta.getOwnerProfile() == null) return;

        String deadPlayerName = meta.getOwnerProfile().getName();
        if (deadPlayerName == null) return;

        // Cherche le joueur mort correspondant
        UUID deadUUID = null;
        for (UUID uuid : plugin.getDeadPlayerManager().getDeadPlayers()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null && op.getName().equalsIgnoreCase(deadPlayerName)) {
                deadUUID = uuid;
                break;
            }
        }

        if (deadUUID == null) {
            placer.sendMessage("§eCette tête n'appartient pas à un joueur mort sur ce serveur.");
            return;
        }

        // Vérifie les items dans l'inventaire du poseur : 2 totems + 2 diamants
        if (!hasRequiredItems(placer)) {
            placer.sendMessage("§cIl manque des items ! Vous avez besoin de §l2 Totems of Undying §r§cet §l2 Diamants §r§cdans votre inventaire !");
            return;
        }

        Location altarLoc = placed.getLocation();
        if (activatingAltars.contains(altarLoc)) return;

        // Lance la réanimation
        final UUID finalDeadUUID = deadUUID;
        startReviveAnimation(placer, altarLoc, finalDeadUUID, deadPlayerName);
    }

    private boolean hasRequiredItems(Player player) {
        int totems = 0;
        int diamonds = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.TOTEM_OF_UNDYING) totems += item.getAmount();
            if (item.getType() == Material.DIAMOND) diamonds += item.getAmount();
        }

        return totems >= 2 && diamonds >= 2;
    }

    private void removeRequiredItems(Player player) {
        removeItems(player, Material.TOTEM_OF_UNDYING, 2);
        removeItems(player, Material.DIAMOND, 2);
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        player.getInventory().setContents(contents);
    }

    private void startReviveAnimation(Player placer, Location altarLoc, UUID deadUUID, String deadName) {
        activatingAltars.add(altarLoc);

        // Retire les items du poseur
        removeRequiredItems(placer);

        // Message à tous
        Bukkit.broadcastMessage("§6✨ §l" + placer.getName() + "§r§6 tente de réanimer §l" + deadName + "§r§6... (5 secondes)");

        World world = altarLoc.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                // Particules et sons pendant l'animation
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, altarLoc.clone().add(0.5, 1, 0.5), 10, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticle(Particle.ENCHANT, altarLoc.clone().add(0.5, 0.5, 0.5), 15, 0.5, 0.5, 0.5, 0.1);

                if (ticks % 10 == 0) {
                    world.playSound(altarLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f + (ticks * 0.05f));
                }

                // Après 5 secondes (100 ticks)
                if (ticks >= 100) {
                    activatingAltars.remove(altarLoc);

                    // Retire la tête posée
                    Block skull = altarLoc.getBlock();
                    skull.setType(Material.AIR);

                    // Effets finaux
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, altarLoc.clone().add(0.5, 1, 0.5), 100, 0.5, 1, 0.5, 0.1);
                    world.playSound(altarLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    world.strikeLightningEffect(altarLoc);

                    // Réanime le joueur
                    plugin.getDeadPlayerManager().revivePlayer(deadUUID);

                    Bukkit.broadcastMessage("§a✨ §l" + deadName + "§r§a a été réanimé par §l" + placer.getName() + "§r§a !");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
