package de.clientapi.clientsCustomWeapons.config;

import org.bukkit.Material;

import java.util.Objects;
import java.util.Set;

public final class WeaponSpec {
    private final String id;
    private final String name;
    private final String modelName;
    private final Material material;
    private final double attackDamage;
    private final double attackSpeedAps;
    private final int maxDurability;
    private final boolean twoHanded;
    private final Double attackRange;
    private final Set<Special> specials;

    public WeaponSpec(String id, String name, String modelName, Material material,
                      double attackDamage, double attackSpeedAps, int maxDurability,
                      boolean twoHanded, Double attackRange, Set<Special> specials) {

        this.id = Objects.requireNonNull(id, "id");
        this.name = name;
        this.modelName = modelName;
        this.material = Objects.requireNonNull(material, "material");

        if (attackDamage <= 0) throw new IllegalArgumentException("attackDamage > 0");
        if (attackSpeedAps <= 0.05 || attackSpeedAps > 10.0) throw new IllegalArgumentException("attackSpeed out of range");
        if (maxDurability <= 0 || maxDurability > 10000) throw new IllegalArgumentException("maxDurability out of range");
        if (attackRange != null) {
            if (attackRange <= 0.0 || attackRange > 8.0) throw new IllegalArgumentException("attackRange out of range");
        }

        this.attackDamage = attackDamage;
        this.attackSpeedAps = attackSpeedAps;
        this.maxDurability = maxDurability;
        this.twoHanded = twoHanded;
        this.attackRange = attackRange;
        this.specials = Objects.requireNonNull(specials, "specials");
    }

    public String id() { return id; }
    public String name() { return name; }
    public String modelName() { return modelName; }
    public Material material() { return material; }
    public double attackDamage() { return attackDamage; }
    public double attackSpeedAps() { return attackSpeedAps; }
    public int maxDurability() { return maxDurability; }
    public boolean twoHanded() { return twoHanded; }
    public Double attackRange() { return attackRange; }
    public Set<Special> specials() { return specials; }
}
