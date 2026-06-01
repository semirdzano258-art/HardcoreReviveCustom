package com.smp.hardcorerevive;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
    private final Set<Location> activatingAltars = new HashSet<>();

    public AltarListener(HardcoreRevive plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player placer = event.getPlayer();
        Block placed = event.getBlockPlaced();

        if (placed.getType() != Material.PLAYER_HEAD && placed.getType() != Material.PLAYER_WALL_HEAD) return;

        Block below = placed.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.OBSIDIAN) return;

        ItemStack item = event.getItemInHand();
        if (item.getItemMeta() == null) return;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta.getOwnerProfile() == null) return;

        String deadPlayerName = meta.getOwnerProfile().getName();
        if (deadPlayerName == null) return;

        if (meta.getLore() == null || meta.getLore().stream().noneMatch(l -> l.contains("Objet de Réanimation"))) return;

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

        ReviveCost cost = plugin.getDeadPlayerManager().getReviveCost(deadUUID);

        if (!cost.hasEnoughItems(placer)) {
            placer.sendMessage(cost.getMissingItemsMessage());
            return;
        }

        Location altarLoc = placed.getLocation();
        if (activatingAltars.contains(altarLoc)) return;

        final UUID finalDeadUUID = deadUUID;
        startReviveAnimation(placer, altarLoc, finalDeadUUID, deadPlayerName, cost);
    }

    private void startReviveAnimation(Player placer, Location altarLoc, UUID deadUUID, String deadName, ReviveCost cost) {
        activatingAltars.add(altarLoc);
        cost.removeItems(placer);

        Bukkit.broadcastMessage("§6✨ §l" + placer.getName() + "§r§6 tente de réanimer §l" + deadName + "§r§6...");

        World world = altarLoc.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                world.spawnParticle(Particle.TOTEM_OF_UNDYING, altarLoc.clone().add(0.5, 1, 0.5), 10, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticle(Particle.ENCHANT, altarLoc.clone().add(0.5, 0.5, 0.5), 15, 0.5, 0.5, 0.5, 0.1);
                world.spawnParticle(Particle.END_ROD, altarLoc.clone().add(0.5, 1, 0.5), 5, 0.2, 0.5, 0.2, 0.02);

                if (ticks % 10 == 0) {
                    world.playSound(altarLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f + (ticks * 0.05f));
                }

                int progress = (int) ((ticks / 100.0) * 20);
                String bar = "§a" + "█".repeat(progress) + "§7" + "█".repeat(20 - progress);
                placer.sendActionBar("§6Réanimation en cours... " + bar);

                if (ticks >= 100) {
                    activatingAltars.remove(altarLoc);

                    Block skull = altarLoc.getBlock();
                    skull.setType(Material.AIR);

                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, altarLoc.clone().add(0.5, 1, 0.5), 200, 0.5, 1, 0.5, 0.1);
                    world.playSound(altarLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    world.strikeLightningEffect(altarLoc);

                    plugin.getDeathListener().unlockSpectator(deadUUID);
                    plugin.getDeadPlayerManager().revivePlayer(deadUUID);

                    Bukkit.broadcastMessage("§a✨ §l" + deadName + "§r§a a été arraché à l'au-delà par §l" + placer.getName() + "§r§a !");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
