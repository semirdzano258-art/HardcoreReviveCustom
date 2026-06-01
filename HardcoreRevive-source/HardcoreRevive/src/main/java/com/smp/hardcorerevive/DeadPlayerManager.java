package com.smp.hardcorerevive;

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
    // Timer 24h : UUID -> timestamp de mort (ms). Null = tête déjà ramassée
    private final Map<UUID, Long> deathTimestamps = new HashMap<>();
    // UUIDs dont la tête a été ramassée (timer annulé)
    private final Set<UUID> headPickedUp = new HashSet<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private static final long TIMER_DURATION_MS = 24 * 60 * 60 * 1000L; // 24h

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
        saveDeadPlayers();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage("§c§lVous êtes mort ! §r§cVos amis ont §l24h§r§c pour ramasser votre tête et vous réanimer !");
                p.sendMessage("§7Autel : §fBloc d'Obsidienne §7+ §fTête §7+ §f2 Totems §7+ §f2 Diamants");
            }
        }, 20L);
    }

    // Appelé quand quelqu'un ramasse la tête → annule le timer
    public void markHeadPickedUp(UUID uuid) {
        if (!headPickedUp.contains(uuid)) {
            headPickedUp.add(uuid);
            deathTimestamps.remove(uuid); // Timer annulé
            saveDeadPlayers();

            // Notifie le joueur mort
            Player dead = Bukkit.getPlayer(uuid);
            if (dead != null) {
                dead.sendMessage("§a✅ Votre tête a été ramassée ! Vos amis peuvent maintenant vous réanimer sans limite de temps.");
            }
            Bukkit.broadcastMessage("§e✅ La tête de §l" + (Bukkit.getOfflinePlayer(uuid).getName()) + "§r§e a été ramassée ! Plus de limite de temps pour l'autel.");
        }
    }

    public boolean isHeadPickedUp(UUID uuid) {
        return headPickedUp.contains(uuid);
    }

    public boolean isDead(UUID uuid) {
        return deadPlayers.contains(uuid);
    }

    public long getRemainingTimeMs(UUID uuid) {
        Long deathTime = deathTimestamps.get(uuid);
        if (deathTime == null) return -1; // Tête ramassée, pas de timer
        long remaining = (deathTime + TIMER_DURATION_MS) - System.currentTimeMillis();
        return Math.max(0, remaining);
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
            player.setHealth(2.0); // 1 coeur
            player.setFoodLevel(20);
            player.sendMessage("§a§lVous avez été réanimé ! §r§aSoyez prudent, vous n'avez qu'un coeur !");
        }
    }

    public void banPlayer(UUID uuid) {
        deadPlayers.remove(uuid);
        deathLocations.remove(uuid);
        deathTimestamps.remove(uuid);
        headPickedUp.remove(uuid);
        saveDeadPlayers();

        String name = Bukkit.getOfflinePlayer(uuid).getName();
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(name, "§cVous n'avez pas été réanimé à temps (24h écoulées).", null, "HardcoreRevive");

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.kickPlayer("§c§lVous n'avez pas été réanimé à temps.\n§rLes 24h se sont écoulées sans que votre tête soit ramassée.");
        }
        Bukkit.broadcastMessage("§c☠ §l" + name + "§r§c a été banni définitivement — personne n'a ramassé sa tête en 24h.");
    }

    // Vérifie toutes les minutes si un timer a expiré
    private void startBanCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Set<UUID> toban = new HashSet<>();
            for (UUID uuid : deadPlayers) {
                if (headPickedUp.contains(uuid)) continue; // Tête ramassée, pas de timer
                Long deathTime = deathTimestamps.get(uuid);
                if (deathTime == null) continue;
                if (System.currentTimeMillis() - deathTime >= TIMER_DURATION_MS) {
                    toban.add(uuid);
                }
            }
            for (UUID uuid : toban) {
                banPlayer(uuid);
            }

            // Avertissements à 1h et 30min restantes
            for (UUID uuid : deadPlayers) {
                if (headPickedUp.contains(uuid)) continue;
                long remaining = getRemainingTimeMs(uuid);
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (remaining > 0 && remaining <= 60 * 60 * 1000L && remaining > 59 * 60 * 1000L) {
                    Bukkit.broadcastMessage("§e⚠ Il reste §l1 heure §r§epour ramasser la tête de §l" + name + "§r§e !");
                }
                if (remaining > 0 && remaining <= 30 * 60 * 1000L && remaining > 29 * 60 * 1000L) {
                    Bukkit.broadcastMessage("§c⚠ Il reste §l30 minutes §r§cpour ramasser la tête de §l" + name + "§r§c !");
                }
            }
        }, 20 * 60L, 20 * 60L); // toutes les minutes
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

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder dead_players.yml : " + e.getMessage());
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
                    UUID uuid = UUID.fromString(key);
                    long ts = dataConfig.getLong("death_timestamps." + key);
                    deathTimestamps.put(uuid, ts);
                } catch (Exception ignored) {}
            }
        }

        plugin.getLogger().info("Chargé " + deadPlayers.size() + " joueur(s) mort(s).");
    }
}
