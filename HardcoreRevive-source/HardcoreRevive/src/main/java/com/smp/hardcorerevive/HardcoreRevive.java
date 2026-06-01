package com.smp.hardcorerevive;

import org.bukkit.plugin.java.JavaPlugin;

public class HardcoreRevive extends JavaPlugin {

    private static HardcoreRevive instance;
    private DeadPlayerManager deadPlayerManager;
    private DeathListener deathListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        deadPlayerManager = new DeadPlayerManager(this);
        deathListener = new DeathListener(this);

        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(new AltarListener(this), this);

        getCommand("revive").setExecutor(new ReviveCommand(this));

        getLogger().info("HardcoreRevive activé ! Autel = Obsidienne + Tête + 2 Totems + 2 Diamants");
    }

    @Override
    public void onDisable() {
        if (deadPlayerManager != null) deadPlayerManager.saveDeadPlayers();
        getLogger().info("HardcoreRevive désactivé.");
    }

    public static HardcoreRevive getInstance() { return instance; }
    public DeadPlayerManager getDeadPlayerManager() { return deadPlayerManager; }
    public DeathListener getDeathListener() { return deathListener; }
}
