package com.smp.hardcorerevive;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DeathListener implements Listener {

    private final HardcoreRevive plugin;
    private final Map<UUID, Location> spectatorLocks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> beamTasks = new HashMap<>();

    public DeathListener(HardcoreRevive plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        Location deathLoc = player.getLocation();

        plugin.getDeadPlayerManager().markAsDead(player);

        ReviveCost cost = plugin.getDeadPlayerManager().getReviveCost(uuid);
        ItemStack skull = createPlayerHead(player, cost);
        player.getWorld().dropItemNaturally(deathLoc, skull);

        startBeam(uuid, deathLoc);
        spectatorLocks.put(uuid, deathLoc);

        int deaths = plugin.getDeadPlayerManager().getDeathCount(uuid);
        Bukkit.broadcastMessage("§c☠ §l" + player.getName() + "§r§c est mort ! (Mort n°" + deaths + ") Vous avez §l24h §r§cpour ramasser sa tête !");

        event.setKeepInventory(false);
        event.getDrops().clear();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.getGameMode() != GameMode.SPECTATOR) return;
        if (!spectatorLocks.containsKey(uuid)) return;
        if (player.isOp()) return;
        Location lock = spectatorLocks.get(uuid);
        Location to = event.getTo();

        if (to == null) return;
        if (!to.getWorld().equals(lock.getWorld())) {
            event.setCancelled(true);
            return;
        }

        if (to.distance(lock) > 100) {
            Location dir = to.clone().subtract(lock);
            dir.setY(0);
            double length = Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ());
            if (length > 0) {
                dir.setX(dir.getX() / length * 99);
                dir.setZ(dir.getZ() / length * 99);
            }
            Location newLoc = lock.clone().add(dir.getX(), to.getY() - lock.getY(), dir.getZ());
            player.teleport(newLoc);
            player.sendActionBar("§c⚠ Vous ne pouvez pas vous éloigner à plus de 100 blocs de votre mort !");
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) return;

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.PLAYER_HEAD) return;
        if (item.getItemMeta() == null) return;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta.getOwnerProfile() == null) return;

        String deadName = meta.getOwnerProfile().getName();
        if (deadName == null) return;

        for (UUID uuid : plugin.getDeadPlayerManager().getDeadPlayers()) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null && op.getName().equalsIgnoreCase(deadName)) {
                if (!plugin.getDeadPlayerManager().isHeadPickedUp(uuid)) {
                    plugin.getDeadPlayerManager().markHeadPickedUp(uuid);
                    stopBeam(uuid);

                    ReviveCost cost = plugin.getDeadPlayerManager().getReviveCost(uuid);
                    picker.sendMessage("§6☠ §lVous avez récupéré la tête de §r§l" + deadName + "§r§6 !");
                    if (cost.isFree()) {
                        picker.sendMessage("§a✨ Première mort — Posez simplement la tête sur de l'§nObsidienne §r§apour le réanimer gratuitement !");
                    } else {
                        picker.sendMessage("§cRessources nécessaires pour réanimer §l" + deadName + "§r§c :");
                        for (String line : cost.toLore()) {
                            if (!line.contains("Posez")) picker.sendMessage("  " + line);
                        }
                        picker.sendMessage("§7Posez la tête sur de l'§fObsidienne §7avec ces items dans l'inventaire.");
                    }
                }
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getDeadPlayerManager().isDead(uuid)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                Location deathLoc = plugin.getDeadPlayerManager().getDeathLocation(uuid);
                if (deathLoc != null) spectatorLocks.put(uuid, deathLoc);

                long remaining = plugin.getDeadPlayerManager().getRemainingTimeMs(uuid);
                if (remaining > 0) {
                    long hours = remaining / 3600000;
                    long minutes = (remaining % 3600000) / 60000;
                    player.sendMessage("§c§lVous êtes toujours mort. §r§cTemps restant : §l" + hours + "h " + minutes + "min");
                } else if (plugin.getDeadPlayerManager().isHeadPickedUp(uuid)) {
                    player.sendMessage("§a✅ Votre tête a été ramassée ! Vos amis peuvent vous réanimer à tout moment.");
                }
            }, 5L);
        }
    }

    public void unlockSpectator(UUID uuid) {
        spectatorLocks.remove(uuid);
        stopBeam(uuid);
    }

    private void startBeam(UUID uuid, Location loc) {
        BukkitRunnable beam = new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (!plugin.getDeadPlayerManager().isDead(uuid)) {
                    cancel();
                    return;
                }
                for (double y = 0; y <= 15; y += 0.5) {
                    Location particleLoc = loc.clone().add(0, y, 0);
                    loc.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0.05, 0, 0.05, 0);
                }
                angle += 10;
                for (int i = 0; i < 8; i++) {
                    double rad = Math.toRadians(angle + (i * 45));
                    double x = Math.cos(rad) * 1.5;
                    double z = Math.sin(rad) * 1.5;
                    loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
                }
            }
        };
        beam.runTaskTimer(plugin, 0L, 5L);
        beamTasks.put(uuid, beam);
    }

    private void stopBeam(UUID uuid) {
        BukkitRunnable beam = beamTasks.remove(uuid);
        if (beam != null) beam.cancel();
    }

    private ItemStack createPlayerHead(Player player, ReviveCost cost) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        PlayerProfile profile = player.getPlayerProfile();
        meta.setPlayerProfile(profile);

        int deaths = plugin.getDeadPlayerManager().getDeathCount(player.getUniqueId());
        meta.setDisplayName("§c☠ §lÂme de §r§c§l" + player.getName() + " §7(Mort n°" + deaths + ")");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§6✦ §lObjet de Réanimation");
        lore.add("§7Cette tête renferme l'âme de");
        lore.add("§f§l" + player.getName() + "§r§7, prisonnier de l'au-delà.");
        lore.add("");
        lore.add("§e⚙ §lComment réanimer :");
        lore.add("§7Posez cette tête sur un");
        lore.add("§f§nBloc d'Obsidienne§r§7 avec les");
        lore.add("§7ressources ci-dessous.");
        lore.add("");
        lore.addAll(cost.toLore());
        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }
}
