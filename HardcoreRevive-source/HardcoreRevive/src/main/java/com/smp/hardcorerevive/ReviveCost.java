package com.smp.hardcorerevive;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReviveCost {

    private final int totems;
    private final int diamonds;
    private final int netherite;
    private final int beacons;
    private final boolean free;

    public ReviveCost(boolean free, int totems, int diamonds, int netherite, int beacons) {
        this.free = free;
        this.totems = totems;
        this.diamonds = diamonds;
        this.netherite = netherite;
        this.beacons = beacons;
    }

    public static ReviveCost forDeathCount(int deathCount) {
        switch (deathCount) {
            case 1: return new ReviveCost(true, 0, 0, 0, 0);
            case 2: return new ReviveCost(false, 2, 2, 0, 0);
            case 3: return new ReviveCost(false, 4, 4, 0, 0);
            case 4: return new ReviveCost(false, 6, 4, 2, 0);
            default: return new ReviveCost(false, 8, 4, 4, 1);
        }
    }

    public boolean isFree() { return free; }
    public int getTotems() { return totems; }
    public int getDiamonds() { return diamonds; }
    public int getNetherite() { return netherite; }
    public int getBeacons() { return beacons; }

    public boolean hasEnoughItems(org.bukkit.entity.Player player) {
        if (free) return true;
        return countItem(player, Material.TOTEM_OF_UNDYING) >= totems
            && countItem(player, Material.DIAMOND) >= diamonds
            && countItem(player, Material.NETHERITE_INGOT) >= netherite
            && countItem(player, Material.BEACON) >= beacons;
    }

    public void removeItems(org.bukkit.entity.Player player) {
        if (free) return;
        removeFromInventory(player, Material.TOTEM_OF_UNDYING, totems);
        removeFromInventory(player, Material.DIAMOND, diamonds);
        removeFromInventory(player, Material.NETHERITE_INGOT, netherite);
        removeFromInventory(player, Material.BEACON, beacons);
    }

    private int countItem(org.bukkit.entity.Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    private void removeFromInventory(org.bukkit.entity.Player player, Material material, int amount) {
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

    public List<String> toLore() {
        List<String> lore = new ArrayList<>();
        if (free) {
            lore.add("§a✨ Première mort — Réanimation GRATUITE !");
            lore.add("§7Posez simplement cette tête sur de l'§fObsidienne§7.");
        } else {
            lore.add("§cRessources nécessaires pour la réanimation :");
            if (totems > 0) lore.add("§7▸ §f" + totems + "x §eTotem of Undying");
            if (diamonds > 0) lore.add("§7▸ §f" + diamonds + "x §bDiamant");
            if (netherite > 0) lore.add("§7▸ §f" + netherite + "x §8Lingot de Netherite");
            if (beacons > 0) lore.add("§7▸ §f" + beacons + "x §6Beacon");
            lore.add("§7Posez cette tête sur de l'§fObsidienne§7 avec ces items.");
        }
        return lore;
    }

    public String getMissingItemsMessage() {
        StringBuilder sb = new StringBuilder("§cIl vous manque : ");
        if (totems > 0) sb.append("§e").append(totems).append("x Totem §c");
        if (diamonds > 0) sb.append("§b").append(diamonds).append("x Diamant §c");
        if (netherite > 0) sb.append("§8").append(netherite).append("x Netherite §c");
        if (beacons > 0) sb.append("§6").append(beacons).append("x Beacon §c");
        return sb.toString();
    }
}
