package com.smp.hardcorerevive;

import org.bukkit.plugin.java.JavaPlugin;

public class HardcoreRevive extends JavaPlugin {

    private static HardcoreRevive instance;
    private DeadPlayerManager deadPlayerManager;

    @Override
    public void onEnable() {
        instance = this;
        deadPlayerManager = new DeadPlayerManager(this);

        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarListener(this), this);

        getCommand("revive").setExecutor(new ReviveCommand(this));

        saveDefaultConfig();
        getLogger().info("HardcoreRevive activé ! Autel = Obsidienne + Tête + 2 Totems + 2 Diamants");
    }

    @Override
    public void onDisable() {
        if (deadPlayerManager != null) {
            deadPlayerManager.saveDeadPlayers();
        }
        getLogger().info("HardcoreRevive désactivé.");
    }

    public static HardcoreRevive getInstance() {
        return instance;
    }

    public DeadPlayerManager getDeadPlayerManager() {
        return deadPlayerManager;
    }
}
