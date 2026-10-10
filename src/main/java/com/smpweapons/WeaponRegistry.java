package com.smpweapons;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Remembers which mace and which spear are "the real one" (saved in plugins/SmpWeapons/data.yml). */
public final class WeaponRegistry {

    private final SmpWeaponsPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public WeaponRegistry(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** The id of the one valid weapon of this type, or null if none has been issued. */
    public String validId(String type) {
        return data.getString(type + "-id");
    }

    public void setValidId(String type, String id) {
        data.set(type + "-id", id);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}
