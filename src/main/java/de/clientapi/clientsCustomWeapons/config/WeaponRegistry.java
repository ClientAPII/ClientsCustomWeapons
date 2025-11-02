package de.clientapi.clientsCustomWeapons.config;

import de.clientapi.clientsCustomWeapons.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class WeaponRegistry {

    private final Plugin plugin;
    private final Map<String, WeaponSpec> byId = new HashMap<>();

    public WeaponRegistry(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        byId.clear();
        File f = new File(plugin.getDataFolder(), "weapons.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = cfg.getConfigurationSection("weapons");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            try {
                var s = root.getConfigurationSection(id);
                if (s == null) continue;

                String name = s.getString("name", null);
                String modelName = s.getString("modelName", "");
                Material material = Material.valueOf(s.getString("material", "AIR").toUpperCase(Locale.ROOT));
                double dmg = s.getDouble("attackDamage", 5.0);
                double aps = s.getDouble("attackSpeed", 1.6);
                int maxDur = s.getInt("maxDurability", 250);
                boolean twoHanded = s.getBoolean("twoHanded", false);
                Double range = null;
                if (s.contains("range")) {
                    double raw = s.getDouble("range");
                    if (Double.isFinite(raw)) {
                        range = raw;
                    } else {
                        throw new IllegalArgumentException("range must be finite");
                    }
                }
                Set<Special> specials = s.getStringList("special").stream()
                        .filter(v -> v != null && !v.isBlank())
                        .map(Special::parse)
                        .collect(Collectors.toSet());

                byId.put(id.toLowerCase(Locale.ROOT),
                        new WeaponSpec(id, name, modelName, material, dmg, aps, maxDur, twoHanded, range, specials));
            } catch (Exception ex) {
                plugin.getLogger().warning("Fehler in weapons.yml bei '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + byId.size() + " weapons.");
    }

    public Optional<WeaponSpec> get(String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<WeaponSpec> all() { return Collections.unmodifiableCollection(byId.values()); }

    public ItemStack buildItem(WeaponSpec spec) {
        ItemStack stack = new ItemStack(spec.material());
        ItemMeta meta = stack.getItemMeta();

        if (spec.name() != null) {
            meta.displayName(Component.text(ChatColor.translateAlternateColorCodes('&', spec.name()))
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Geschwindigkeit: ", NamedTextColor.GRAY)
                .append(Component.text(format(spec.attackSpeedAps()))
                        .color(TextColor.fromHexString("#c2d6d6")))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Schaden: ", NamedTextColor.GRAY)
                .append(Component.text(format(spec.attackDamage()))
                        .color(TextColor.fromHexString("#c2d6d6")))
                .decoration(TextDecoration.ITALIC, false));
        if (spec.attackRange() != null) {
            lore.add(Component.text("Reichweite: ", NamedTextColor.GRAY)
                    .append(Component.text(format(spec.attackRange()))
                            .color(TextColor.fromHexString("#c2d6d6")))
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        AttributeModifier dmgMod = buildAttr("ccw_attack_damage", spec.attackDamage(), EquipmentSlotGroup.MAINHAND);
        AttributeModifier spdMod = buildAttr("ccw_attack_speed", spec.attackSpeedAps() - 4.0, EquipmentSlotGroup.MAINHAND);

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, dmgMod);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, spdMod);

        setItemModel(meta, spec.modelName());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.weaponId(plugin), PersistentDataType.STRING, spec.id());
        pdc.set(Keys.weaponClass(plugin), PersistentDataType.STRING, spec.twoHanded() ? "ZWEIHAENDER" : "EINHAENDER");
        pdc.set(Keys.weaponDamage(plugin), PersistentDataType.DOUBLE, spec.attackDamage());
        pdc.set(Keys.weaponSpeedAPS(plugin), PersistentDataType.DOUBLE, spec.attackSpeedAps());
        if (spec.attackRange() != null) {
            pdc.set(Keys.weaponRange(plugin), PersistentDataType.DOUBLE, spec.attackRange());
        } else {
            pdc.remove(Keys.weaponRange(plugin));
        }
        if (!spec.specials().isEmpty()) {
            String csv = spec.specials().stream().map(Enum::name).collect(Collectors.joining(","));
            pdc.set(Keys.specialFlags(plugin), PersistentDataType.STRING, csv);
        }

        setMaxDamage(meta, spec.maxDurability());

        stack.setItemMeta(meta);
        return stack;
    }

    private AttributeModifier buildAttr(String name, double value, EquipmentSlotGroup group) {
        try {
            return new AttributeModifier(UUID.randomUUID(), name, value,
                    AttributeModifier.Operation.ADD_NUMBER, group);
        } catch (Throwable t) {
            return new AttributeModifier(UUID.randomUUID(), name, value,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
        }
    }

    private void setItemModel(ItemMeta meta, String modelName) {
        if (modelName == null || modelName.isBlank()) return;
        NamespacedKey key = NamespacedKey.fromString(modelName);
        if (key != null) meta.setItemModel(key);
    }

    private void setMaxDamage(ItemMeta meta, int value) {
        try {
            Method m = meta.getClass().getMethod("setMaxDamage", Integer.class);
            m.setAccessible(true);
            m.invoke(meta, value);
        } catch (NoSuchMethodException ex) {
            meta.getPersistentDataContainer().set(Keys.maxDurBackup(plugin), PersistentDataType.INTEGER, value);
        } catch (Throwable t) {
            plugin.getLogger().warning("MaxDamage setzen fehlgeschlagen: " + t.getMessage());
        }
    }

    private String format(double v) {
        return (Math.abs(v - Math.rint(v)) < 1e-9) ? String.valueOf((int)Math.rint(v)) : String.format(java.util.Locale.US, "%.1f", v);
    }
}
