package com.smp.hardcorerevive;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
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
        Location deathLoc = player.getLocation().clone();

        plugin.getDeadPlayerManager().markAsDead(player);

        ReviveCost cost = plugin.getDeadPlayerManager().getReviveCost(uuid);
        ItemStack skull = createPlayerHead(player, cost);

        Location dropLoc = deathLoc.clone().add(0, 0.5, 0);
        Item droppedItem = player.getWorld().dropItem(dropLoc, skull);
        droppedItem.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));

        startBeam(uuid, deathLoc);
        spectatorLocks.put(uuid, deathLoc);

        int deaths = plugin.getDeadPlayerManager().getDeathCount(uuid);
        int x = (int) deathLoc.getX();
        int y = (int) deathLoc.getY();
        int z = (int) deathLoc.getZ();

        Bukkit.broadcastMessage("\u00a7c\u2620 \u00a7l" + player.getName() + "\u00a7r\u00a7c est mort ! (Mort n\u00b0" + deaths + ")");
        Bukkit.broadcastMessage("\u00a77Coordonnees : \u00a7fX: " + x + " Y: " + y + " Z: " + z);
        Bukkit.broadcastMessage("\u00a77Vous avez \u00a7l24h \u00a7r\u00a77pour ramasser sa tete !");

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
            player.sendActionBar("\u00a7c\u26a0 Vous ne pouvez pas vous eloigner a plus de 100 blocs de votre mort !");
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
                    picker.sendMessage("\u00a76\u2620 \u00a7lVous avez recupere la tete de \u00a7r\u00a7l" + deadName + "\u00a7r\u00a76 !");
                    if (cost.isFree()) {
                        picker.sendMessage("\u00a7a\u2728 Premiere mort - Posez simplement la tete sur de l'\u00a7nObsidienne \u00a7r\u00a7apour le reanimater gratuitement !");
                    } else {
                        picker.sendMessage("\u00a7cRessources necessaires pour reanimater \u00a7l" + deadName + "\u00a7r\u00a7c :");
                        for (String line : cost.toLore()) {
                            if (!line.contains("Posez")) picker.sendMessage("  " + line);
                        }
                        picker.sendMessage("\u00a77Posez la tete sur de l'\u00a7fObsidienne \u00a77avec ces items dans l'inventaire.");
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
                if (deathLoc != null) {
                    spectatorLocks.put(uuid, deathLoc);
                    player.teleport(deathLoc.clone().add(0, 5, 0));
                }

                long remaining = plugin.getDeadPlayerManager().getRemainingTimeMs(uuid);
                if (remaining > 0) {
                    long hours = remaining / 3600000;
                    long minutes = (remaining % 3600000) / 60000;
                    player.sendMessage("\u00a7c\u00a7lVous etes toujours mort. \u00a7r\u00a7cTemps restant : \u00a7l" + hours + "h " + minutes + "min");
                } else if (plugin.getDeadPlayerManager().isHeadPickedUp(uuid)) {
                    player.sendMessage("\u00a7a\u2705 Votre tete a ete ramassee ! Vos amis peuvent vous reanimat a tout moment.");
                }
            }, 5L);
        }
    }

    public void unlockSpectator(UUID uuid, Location altarLoc) {
        spectatorLocks.remove(uuid);
        stopBeam(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && altarLoc != null) {
            Location spawnLoc = altarLoc.clone().add(0.5, 1, 0.5);
            spawnLoc.setYaw(0);
            spawnLoc.setPitch(0);
            player.teleport(spawnLoc);
        }
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

                for (double y = 0; y <= 100; y += 0.5) {
                    Location particleLoc = loc.clone().add(0, y, 0);
                    loc.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 3, 0.15, 0, 0.15, 0);
                    loc.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 2, 0.3, 0, 0.3, 0);
                }

                angle += 10;
                for (int i = 0; i < 8; i++) {
                    double rad = Math.toRadians(angle + (i * 45));
                    double x = Math.cos(rad) * 1.5;
                    double z = Math.sin(rad) * 1.5;
                    loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
                }

                for (int i = 0; i < 6; i++) {
                    double rad = Math.toRadians(-angle + (i * 60));
                    double x = Math.cos(rad) * 3;
                    double z = Math.sin(rad) * 3;
                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x, 25, z), 1, 0, 0, 0, 0);
                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x, 50, z), 1, 0, 0, 0, 0);
                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x, 75, z), 1, 0, 0, 0, 0);
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
        meta.setDisplayName("\u00a7c\u2620 \u00a7lAme de \u00a7r\u00a7c\u00a7l" + player.getName() + " \u00a77(Mort n\u00b0" + deaths + ")");

        List<String> lore = new ArrayList<>();
        lore.add("\u00a78\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac");
        lore.add("\u00a76\u2756 \u00a7lObjet de Reanimation");
        lore.add("\u00a77Cette tete renferme l'ame de");
        lore.add("\u00a7f\u00a7l" + player.getName() + "\u00a7r\u00a77, prisonnier de l'au-dela.");
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
        return skull;
    }
}
