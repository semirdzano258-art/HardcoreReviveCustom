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
Commit et dis moi quand c'est fait, je te donne le suivant ! 🎮Vous avez dit : cbn?package com.smp.hardcorerevive;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.ipastedcbn?06:52Claude a répondu : Oui c'est bon !Oui c'est bon ! Commit et passe au suivant — DeadPlayerManager.java :
javapackage com.smp.hardcorerevive;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DeadPlayerManager {

    private final HardcoreRevive plugin;
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, Long> deathTimestamps = new HashMap<>();
    private final Set<UUID> headPickedUp = new HashSet<>();
    private final Map<UUID, Integer> deathCounts = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private static final long TIMER_DURATION_MS = 24 * 60 * 60 * 1000L;

    public DeadPlayerManager(HardcoreRevive plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "dead_players.yml");
        loadDeadPlayers();
        startBanCheckTask();
    }

    public void markAsDead(Player player) {
        UUID uuid = player.getUniqueId();
        deadPlayers.add(uuid);
        deathLocations.put(uuid, player.getLocation());
        deathTimestamps.put(uuid, System.currentTimeMillis());
        deathCounts.merge(uuid, 1, Integer::sum);
        saveDeadPlayers();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SPECTATOR);
                int deaths = deathCounts.getOrDefault(uuid, 1);
                if (deaths == 1) {
                    p.sendMessage("§c§lVous êtes mort ! §r§cC'est votre §l1ère mort§r§c — la réanimation est §aGRATUITE§r§c !");
                    p.sendMessage("§7Vos amis ont §f24h §7pour ramasser votre tête et la poser sur de l'§fObsidienne§7.");
                } else {
                    p.sendMessage("§c§lVous êtes mort ! §r§cMort n°§l" + deaths + "§r§c — la réanimation coûte des ressources !");
                    p.sendMessage("§7Vos amis ont §f24h §7pour ramasser votre tête.");
                }
            }
        }, 20L);
    }

    public void markHeadPickedUp(UUID uuid) {
        if (!headPickedUp.contains(uuid)) {
            headPickedUp.add(uuid);
            deathTimestamps.remove(uuid);
            saveDeadPlayers();

            Player dead = Bukkit.getPlayer(uuid);
            if (dead != null) {
                dead.sendMessage("§a✅ Votre tête a été ramassée ! Vos amis peuvent vous réanimer sans limite de temps.");
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            Bukkit.broadcastMessage("§e✅ La tête de §l" + name + "§r§e a été ramassée ! Le compte à rebours est arrêté.");
        }
    }

    public boolean isHeadPickedUp(UUID uuid) {
        return headPickedUp.contains(uuid);
    }

    public boolean isDead(UUID uuid) {
        return deadPlayers.contains(uuid);
    }

    public int getDeathCount(UUID uuid) {
        return deathCounts.getOrDefault(uuid, 0);
    }

    public long getRemainingTimeMs(UUID uuid) {
        Long deathTime = deathTimestamps.get(uuid);
        if (deathTime == null) return -1;
        long remaining = (deathTime + TIMER_DURATION_MS) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public ReviveCost getReviveCost(UUID uuid) {
        int deaths = deathCounts.getOrDefault(uuid, 1);
        return ReviveCost.forDeathCount(deaths);
    }

    public void revivePlayer(UUID uuid) {
        deadPlayers.remove(uuid);
        deathLocations.remove(uuid);
        deathTimestamps.remove(uuid);
        headPickedUp.remove(uuid);
        saveDeadPlayers();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(2.0);
            player.setFoodLevel(20);
            player.sendMessage("§a§lVous avez été réanimé ! §r§aSoyez prudent, vous n'avez qu'un cœur !");
        }
    }

    public void banPlayer(UUID uuid) {
        deadPlayers.remove(uuid);
        deathLocations.remove(uuid);
        deathTimestamps.remove(uuid);
        headPickedUp.remove(uuid);
        saveDeadPlayers();

        String name = Bukkit.getOfflinePlayer(uuid).getName();
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(name, "Vous n'avez pas été réanimé à temps (24h écoulées).", null, "HardcoreRevive");

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.kickPlayer("§c§lVous n'avez pas été réanimé à temps.\n§rLes 24h se sont écoulées sans que votre tête soit ramassée.");
        }
        Bukkit.broadcastMessage("§c☠ §l" + name + "§r§c a été banni définitivement — personne n'a ramassé sa tête en 24h.");
    }

    private void startBanCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Set<UUID> toban = new HashSet<>(deadPlayers);
            for (UUID uuid : toban) {
                if (headPickedUp.contains(uuid)) continue;
                Long deathTime = deathTimestamps.get(uuid);
                if (deathTime == null) continue;
                if (System.currentTimeMillis() - deathTime >= TIMER_DURATION_MS) {
                    banPlayer(uuid);
                }
                long remaining = getRemainingTimeMs(uuid);
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (remaining > 0 && remaining <= 60 * 60 * 1000L && remaining > 59 * 60 * 1000L) {
                    Bukkit.broadcastMessage("§e⚠ Il reste §l1 heure §r§epour ramasser la tête de §l" + name + "§r§e !");
                }
                if (remaining > 0 && remaining <= 30 * 60 * 1000L && remaining > 29 * 60 * 1000L) {
                    Bukkit.broadcastMessage("§c⚠ Il reste §l30 minutes §r§cpour ramasser la tête de §l" + name + "§r§c !");
                }
            }
        }, 20 * 60L, 20 * 60L);
    }

    public Location getDeathLocation(UUID uuid) {
        return deathLocations.get(uuid);
    }

    public Set<UUID> getDeadPlayers() {
        return Collections.unmodifiableSet(deadPlayers);
    }

    public void saveDeadPlayers() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataConfig = new YamlConfiguration();

        List<String> uuids = new ArrayList<>();
        for (UUID uuid : deadPlayers) uuids.add(uuid.toString());
        dataConfig.set("dead_players", uuids);

        List<String> pickedUp = new ArrayList<>();
        for (UUID uuid : headPickedUp) pickedUp.add(uuid.toString());
        dataConfig.set("head_picked_up", pickedUp);

        for (Map.Entry<UUID, Location> entry : deathLocations.entrySet()) {
            Location loc = entry.getValue();
            String key = "death_locations." + entry.getKey();
            dataConfig.set(key + ".world", loc.getWorld().getName());
            dataConfig.set(key + ".x", loc.getX());
            dataConfig.set(key + ".y", loc.getY());
            dataConfig.set(key + ".z", loc.getZ());
        }

        for (Map.Entry<UUID, Long> entry : deathTimestamps.entrySet()) {
            dataConfig.set("death_timestamps." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<UUID, Integer> entry : deathCounts.entrySet()) {
            dataConfig.set("death_counts." + entry.getKey(), entry.getValue());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder : " + e.getMessage());
        }
    }

    public void loadDeadPlayers() {
        if (!dataFile.exists()) return;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String s : dataConfig.getStringList("dead_players")) {
            try { deadPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        for (String s : dataConfig.getStringList("head_picked_up")) {
            try { headPickedUp.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        if (dataConfig.getConfigurationSection("death_locations") != null) {
            for (String key : dataConfig.getConfigurationSection("death_locations").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String worldName = dataConfig.getString("death_locations." + key + ".world");
                    double x = dataConfig.getDouble("death_locations." + key + ".x");
                    double y = dataConfig.getDouble("death_locations." + key + ".y");
                    double z = dataConfig.getDouble("death_locations." + key + ".z");
                    if (Bukkit.getWorld(worldName) != null) {
                        deathLocations.put(uuid, new Location(Bukkit.getWorld(worldName), x, y, z));
                    }
                } catch (Exception ignored) {}
            }
        }
        if (dataConfig.getConfigurationSection("death_timestamps") != null) {
            for (String key : dataConfig.getConfigurationSection("death_timestamps").getKeys(false)) {
                try {
                    deathTimestamps.put(UUID.fromString(key), dataConfig.getLong("death_timestamps." + key));
                } catch (Exception ignored) {}
            }
        }
        if (dataConfig.getConfigurationSection("death_counts") != null) {
            for (String key : dataConfig.getConfigurationSection("death_counts").getKeys(false)) {
                try {
                    deathCounts.put(UUID.fromString(key), dataConfig.getInt("death_counts." + key));
                } catch (Exception ignored) {}
            }
        }
        plugin.getLogger().info("Chargé " + deadPlayers.size() + " joueur(s) mort(s).");
    }
}
