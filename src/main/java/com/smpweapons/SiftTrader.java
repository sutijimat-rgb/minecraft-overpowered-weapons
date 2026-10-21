package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

/** A villager with overpowered trades. */
public final class SiftTrader {

    private final WeaponItems weapons;
    private final SiftItems sift;

    public SiftTrader(WeaponItems weapons, SiftItems sift) {
        this.weapons = weapons;
        this.sift = sift;
    }

    private static MerchantRecipe trade(int emeralds, ItemStack result) {
        MerchantRecipe recipe = new MerchantRecipe(result, 0, Integer.MAX_VALUE, false);
        recipe.addIngredient(new ItemStack(Material.EMERALD, emeralds));
        return recipe;
    }

    private List<MerchantRecipe> recipes() {
        List<MerchantRecipe> list = new ArrayList<>();
        list.add(trade(1, new ItemStack(Material.DIAMOND, 16)));
        list.add(trade(1, new ItemStack(Material.NETHERITE_INGOT, 4)));
        list.add(trade(1, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4)));
        list.add(trade(1, new ItemStack(Material.TOTEM_OF_UNDYING, 2)));
        list.add(trade(1, new ItemStack(Material.ENDER_PEARL, 16)));
        list.add(trade(1, new ItemStack(Material.EXPERIENCE_BOTTLE, 64)));
        list.add(trade(1, sift.ichorBottle(8)));
        list.add(trade(1, sift.ichorBucket()));
        list.add(trade(1, sift.ichorSnowball(16)));
        list.add(trade(1, sift.riftKey()));
        list.add(trade(3, weapons.createSword()));
        for (ItemStack piece : sift.siftiteSet()) {
            boolean armor = piece.getType().name().endsWith("_HELMET") || piece.getType().name().endsWith("_CHESTPLATE")
                    || piece.getType().name().endsWith("_LEGGINGS") || piece.getType().name().endsWith("_BOOTS");
            list.add(trade(armor ? 3 : 2, piece));
        }
        return list;
    }

    public void spawn(Location at) {
        at.getWorld().spawn(at, Villager.class, v -> {
            v.customName(Component.text("Sift Trader", NamedTextColor.GOLD));
            v.setCustomNameVisible(true);
            v.setProfession(Villager.Profession.ARMORER);
            v.setVillagerLevel(5);
            v.setVillagerExperience(250);
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setAI(false);
            v.setRecipes(recipes());
        });
    }
}
