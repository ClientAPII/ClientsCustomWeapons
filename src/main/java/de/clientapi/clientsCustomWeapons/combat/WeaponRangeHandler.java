package de.clientapi.clientsCustomWeapons.combat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.clientapi.clientsCustomWeapons.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class WeaponRangeHandler implements Listener {

    private static final double DEFAULT_RANGE = 3.0;
    private static final double TWO_HAND_RANGE = 4.0;
    private static final double ENTITY_PADDING = 0.25;
    private static final double CRIT_MULT = 1.5;
    private static final double MIN_RECHARGE = 0.9;
    private static final int SHIELD_DISABLE_TICKS = 20 * 5;

    private static final Map<UUID, Long> LAST_SWING_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PROCESSED_ATTACKS = new ConcurrentHashMap<>();
    private static final Set<UUID> ALLOW_DAMAGE_PASS = ConcurrentHashMap.newKeySet();

    private static final class AttackMarker {
        final UUID victimId;
        volatile boolean permitted;
        AttackMarker(UUID victimId) { this.victimId = victimId; }
    }
    private static final Map<UUID, AttackMarker> ATTACK_MARKERS = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final ProtocolManager protocol;

    public WeaponRangeHandler(Plugin plugin, ProtocolManager protocol) {
        this.plugin = plugin;
        this.protocol = protocol;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        protocol.addPacketListener(new PacketAdapter(plugin, com.comphenix.protocol.events.ListenerPriority.NORMAL,
                PacketType.Play.Client.ARM_ANIMATION) {
            @Override public void onPacketReceiving(PacketEvent event) { handleAttack(event, false); }
        });

        protocol.addPacketListener(new PacketAdapter(plugin, com.comphenix.protocol.events.ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ENTITY) {
            @Override public void onPacketReceiving(PacketEvent event) {
                var action = event.getPacket().getEnumEntityUseActions().read(0);
                if (action.getAction() != EnumWrappers.EntityUseAction.ATTACK) return;
                handleAttack(event, true);
            }
        });
    }

    private void handleAttack(PacketEvent event, boolean fromUseEntity) {
        Player p = event.getPlayer();
        if (event.isCancelled() || p == null || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) return;

        if (fromUseEntity) {
            ItemStack main = p.getInventory().getItemInMainHand();
            if (main == null || main.getType().isAir() || main.getItemMeta() == null) return;
            if (!isCustomWeapon(main)) return;
            event.setCancelled(true);
        } else {
            var hands = event.getPacket().getHands();
            if (hands == null || hands.size() == 0 || hands.read(0) != EnumWrappers.Hand.MAIN_HAND) return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack weapon = p.getInventory().getItemInMainHand();
            if (weapon == null || weapon.getType().isAir() || weapon.getItemMeta() == null) return;
            if (!isCustomWeapon(weapon)) return;

            long now = System.currentTimeMillis();
            Long last = PROCESSED_ATTACKS.get(p.getUniqueId());
            if (last != null && now - last < 50) return;

            PROCESSED_ATTACKS.put(p.getUniqueId(), now);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Long ts = PROCESSED_ATTACKS.get(p.getUniqueId());
                if (ts != null && System.currentTimeMillis() - ts > 50) PROCESSED_ATTACKS.remove(p.getUniqueId());
            }, 2L);

            double range = resolveRange(weapon);
            performRaycastAttack(p, weapon, range);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVanillaDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (ALLOW_DAMAGE_PASS.contains(p.getUniqueId())) return;
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            ItemStack weapon = p.getInventory().getItemInMainHand();
            if (weapon != null && isCustomWeapon(weapon)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMonitorDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        AttackMarker marker = ATTACK_MARKERS.get(p.getUniqueId());
        if (marker == null) return;
        if (!event.getEntity().getUniqueId().equals(marker.victimId)) return;
        marker.permitted = !event.isCancelled();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (isCustomWeapon(event.getItem())) {
            Long ts = PROCESSED_ATTACKS.get(event.getPlayer().getUniqueId());
            if (ts != null && System.currentTimeMillis() - ts < 50) event.setCancelled(true);
        }
    }

    private boolean performRaycastAttack(Player p, ItemStack weapon, double range) {
        if (!isAttackRecharged(p, weapon)) return false;

        var eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        Predicate<Entity> filter = e -> e instanceof LivingEntity && e != p && e.isValid() && ((LivingEntity)e).getHealth() > 0.0;

        RayTraceResult eHit = p.getWorld().rayTraceEntities(eye, dir, range, ENTITY_PADDING, filter);
        if (eHit == null || eHit.getHitEntity() == null) return false;
        LivingEntity target = (LivingEntity) eHit.getHitEntity();

        RayTraceResult bHit = p.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (bHit != null && bHit.getHitBlock() != null) {
            double blockDist = eye.toVector().distance(bHit.getHitPosition());
            double targetDist = eye.toVector().distance(eHit.getHitPosition() != null ? eHit.getHitPosition() : target.getBoundingBox().getCenter());
            if (blockDist < targetDist) return false;
        }

        double finalDmg = applyCritIfAny(p, getTotalAttackDamage(p));

        AttackMarker marker = new AttackMarker(target.getUniqueId());
        ATTACK_MARKERS.put(p.getUniqueId(), marker);

        try {
            ALLOW_DAMAGE_PASS.add(p.getUniqueId());
            target.damage(finalDmg, p);
        } finally {
            ALLOW_DAMAGE_PASS.remove(p.getUniqueId());
        }

        boolean damagePermitted = marker.permitted;
        ATTACK_MARKERS.remove(p.getUniqueId());

        applySpecials(p, target, weapon, damagePermitted);

        p.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
        LAST_SWING_MS.put(p.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    private void applySpecials(Player attacker, LivingEntity target, ItemStack weapon, boolean damagePermitted) {
        var meta = weapon.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        String csv = pdc.get(Keys.specialFlags(plugin), PersistentDataType.STRING);
        boolean isTwoHand = "ZWEIHAENDER".equalsIgnoreCase(pdc.get(Keys.weaponClass(plugin), PersistentDataType.STRING));

        if (damagePermitted && target instanceof Player def && isTwoHand && isShieldBlocking(def)) {
            sendReleaseUseItem(def);
            def.setCooldown(Material.SHIELD, SHIELD_DISABLE_TICKS);
            def.playSound(def.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
            attacker.playSound(attacker.getLocation(), Sound.ITEM_AXE_STRIP, 1f, 1f);
        }

        if (damagePermitted && csv != null && csv.contains("DISMOUNT_MOUNTED") && target.getVehicle() != null) {
            var veh = target.getVehicle();
            if (veh instanceof AbstractHorse || veh.getScoreboardTags().contains("mount")) {
                target.leaveVehicle();
                // kleiner Nudge, bleibt erhalten
                target.damage(0.01, attacker);
                target.setNoDamageTicks(0);
            }
        }
    }

    private boolean isAttackRecharged(Player p, ItemStack weapon) {
        double aps = resolveAPS(p, weapon);
        long rechargeMs = Math.round(1000.0 / Math.max(0.1, aps));
        long needed = Math.round(rechargeMs * MIN_RECHARGE);
        long now = System.currentTimeMillis();
        long last = LAST_SWING_MS.getOrDefault(p.getUniqueId(), 0L);
        return now - last >= needed;
    }

    private double resolveAPS(Player p, ItemStack weapon) {
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            Double fromPdc = meta.getPersistentDataContainer().get(Keys.weaponSpeedAPS(plugin), PersistentDataType.DOUBLE);
            if (fromPdc != null && fromPdc > 0) return fromPdc;
        }
        AttributeInstance inst = p.getAttribute(Attribute.ATTACK_SPEED);
        return inst != null ? Math.max(0.1, inst.getValue()) : 4.0;
    }

    private double resolveRange(ItemStack weapon) {
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return DEFAULT_RANGE;
        var pdc = meta.getPersistentDataContainer();

        Double explicit = pdc.get(Keys.weaponRange(plugin), PersistentDataType.DOUBLE);
        if (explicit == null) {
            Integer legacy = pdc.get(Keys.weaponRange(plugin), PersistentDataType.INTEGER);
            if (legacy != null && legacy > 0) explicit = legacy.doubleValue();
        }
        if (explicit != null && explicit > 0.0) return Math.min(explicit, 8.0);

        String cls = pdc.get(Keys.weaponClass(plugin), PersistentDataType.STRING);
        if ("ZWEIHAENDER".equalsIgnoreCase(cls)) return TWO_HAND_RANGE;
        return DEFAULT_RANGE;
    }

    private double getTotalAttackDamage(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.ATTACK_DAMAGE);
        return (inst != null) ? Math.max(0.1, inst.getValue()) : 1.0;
    }

    private double applyCritIfAny(Player p, double base) {
        boolean airborne = !p.isOnGround() && p.getFallDistance() > 0.0f;
        boolean ok = airborne && !p.isClimbing() && !p.isInWater() && !p.isSprinting()
                && !p.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS) && !p.isInsideVehicle();
        return ok ? base * CRIT_MULT : base;
    }

    private boolean isCustomWeapon(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(Keys.weaponId(plugin), PersistentDataType.STRING);
    }

    private boolean isShieldBlocking(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off  = p.getInventory().getItemInOffHand();
        boolean hasShield = (main != null && main.getType() == Material.SHIELD) || (off != null && off.getType() == Material.SHIELD);
        return hasShield && (p.isBlocking() || p.isHandRaised());
    }

    private void sendReleaseUseItem(Player defender) {
        PacketContainer pkt = new PacketContainer(PacketType.Play.Client.BLOCK_DIG);
        pkt.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.RELEASE_USE_ITEM);
        pkt.getBlockPositionModifier().write(0, new BlockPosition(0, 0, 0));
        pkt.getDirections().write(0, EnumWrappers.Direction.DOWN);
        try { pkt.getIntegers().write(0, 0); } catch (Exception ignored) {}
        protocol.receiveClientPacket(defender, pkt);
    }
}
