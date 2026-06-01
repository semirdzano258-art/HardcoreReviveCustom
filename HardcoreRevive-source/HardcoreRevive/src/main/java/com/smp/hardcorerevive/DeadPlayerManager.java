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
                    p.sendMessage("\u00a7c\u00a7lVous etes mort ! \u00a7r\u00a7cPremiere mort - reanimation \u00a7aGRATUITE\u00a7r\u00a7c !");
                    p.sendMessage("\u00a77Vos amis ont \u00a7f24h \u00a77pour ramasser votre tete.");
                } else {
                    p.sendMessage("\u00a7c\u00a7lVous etes mort ! \u00a7r\u00a7cMort n\u00b0\u00a7l" + deaths + "\u00a7r\u00a7c - la reanimation coute des ressources !");
                    p.sendMessage("\u00a77Vos amis ont \u00a7f24h \u00a77pour ramasser votre tete.");
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
                dead.sendMessage("\u00a7aVotre tete a ete ramassee ! Vos amis peuvent vous reranimer sans limite de temps.");
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            Bukkit.broadcastMessage("\u00a7eLa tete de \u00a7l" + name + "\u00a7r\u00a7e a ete ramassee ! Le compte a rebours est arrete.");
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
            player.sendMessage("\u00a7a\u00a7lVous avez ete reamine ! \u00a7r\u00a7aSoyez prudent, vous n'avez qu'un coeur !");
        }
    }

    public void banPlayer(UUID uuid) {
        deadPlayers.remove(uuid);
        deathLocations.remove(uuid);
        deathTimestamps.remove(uuid);
        headPickedUp.remove(uuid);
        saveDeadPlayers();

        String name = Bukkit.getOfflinePlayer(uuid).getName();
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(name, "Non reamine a temps (24h).", null, "HardcoreRevive");

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.kickPlayer("\u00a7c\u00a7lVous n'avez pas ete reamine a temps.\n\u00a7rLes 24h se sont ecoulees.");
        }
        Bukkit.broadcastMessage("\u00a7c\u00a7l" + name + "\u00a7r\u00a7c a ete banni - personne n'a ramasse sa tete en 24h.");
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
                    Bukkit.broadcastMessage("\u00a7eIl reste \u00a7l1 heure \u00a7r\u00a7epour ramasser la tete de \u00a7l" + name + "\u00a7r\u00a7e !");
                }
                if (remaining > 0 && remaining <= 30 * 60 * 1000L && remaining > 29 * 60 * 1000L) {
                    Bukkit.broadcastMessage("\u00a7cIl reste \u00a7l30 minutes \u00a7r\u00a7cpour ramasser la tete de \u00a7l" + name + "\u00a7r\u00a7c !");
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
        plugin.getLogger().info("Charge " + deadPlayers.size() + " joueur(s) mort(s).");
    }
}
