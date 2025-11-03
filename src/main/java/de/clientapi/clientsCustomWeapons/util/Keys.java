package de.clientapi.clientsCustomWeapons.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    private Keys() {}

    public static NamespacedKey weaponId(Plugin p)         { return new NamespacedKey(p, "weapon_id"); }
    public static NamespacedKey weaponClass(Plugin p)      { return new NamespacedKey(p, "weapon_class"); }
    public static NamespacedKey weaponRange(Plugin p)      { return new NamespacedKey(p, "weapon_range"); }
    public static NamespacedKey weaponDamage(Plugin p)     { return new NamespacedKey(p, "weapon_damage"); }
    public static NamespacedKey weaponSpeedAPS(Plugin p)   { return new NamespacedKey(p, "weapon_attack_speed"); }
    public static NamespacedKey specialFlags(Plugin p)     { return new NamespacedKey(p, "specials_csv"); }
    public static NamespacedKey modelName(Plugin p)        { return new NamespacedKey(p, "model_name"); }

    public static NamespacedKey maxDurBackup(Plugin p)     { return new NamespacedKey(p, "max_durability"); }
}
