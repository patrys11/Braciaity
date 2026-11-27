package pl.bracia.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ItemListener implements Listener {

    private final BraciaItems plugin;
    private final Items items;

    /** klucz: uuid:itemId -> kiedy konczy sie cooldown (ms) */
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** gracze, ktorzy nie dostaja obrazen od upadku po grapler (ms) */
    private final Map<UUID, Long> noFall = new HashMap<>();

    public ItemListener(BraciaItems plugin, Items items) {
        this.plugin = plugin;
        this.items = items;
    }

    // ---------------------------------------------------------------- cooldown

    private long remainingMs(Player p, String id) {
        Long until = cooldowns.get(p.getUniqueId() + ":" + id);
        if (until == null) {
            return 0;
        }
        return Math.max(until - System.currentTimeMillis(), 0);
    }

    private void startCooldown(Player p, String id, int seconds) {
        cooldowns.put(p.getUniqueId() + ":" + id, System.currentTimeMillis() + seconds * 1000L);
    }

    private void cooldownMessage(Player p, String name, long leftMs) {
        long sec = (leftMs + 999) / 1000;
        p.sendActionBar(Component.text(name + ": cooldown " + sec + "s", NamedTextColor.RED));
    }

    // ---------------------------------------------------------------- grapler

    private boolean holdsGrapler(Player p) {
        return Items.GRAPLER.equals(items.idOf(p.getInventory().getItemInMainHand()))
                || Items.GRAPLER.equals(items.idOf(p.getInventory().getItemInOffHand()));
    }

    /** Rzut wedka: blokujemy na cooldownie, w przeciwnym razie oznaczamy haczyk. */
    @EventHandler
    public void onCast(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player p = e.getPlayer();
        if (!holdsGrapler(p)) {
            return;
        }

        long left = remainingMs(p, Items.GRAPLER);
        if (left > 0) {
            e.setCancelled(true);
            cooldownMessage(p, "Grapler", left);
            return;
        }
        items.tag(e.getHook(), Items.GRAPLER);
    }

    /** Haczyk trafil w blok lub moba. */
    @EventHandler
    public void onHookHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof FishHook hook)) {
            return;
        }
        if (!Items.GRAPLER.equals(items.entityId(hook))) {
            return;
        }
        if (!(hook.getShooter() instanceof Player p)) {
            return;
        }

        FileConfiguration cfg = plugin.getConfig();
        double range = cfg.getDouble("grapler.range", 10.0);
        int cooldown = cfg.getInt("grapler.cooldown-seconds", 60);
        String mode = cfg.getString("grapler.mode", "BOTH").toUpperCase();

        Entity hitEntity = e.getHitEntity();
        Location impact = hitEntity != null ? hitEntity.getLocation() : hook.getLocation();
        Location from = p.getLocation();

        if (from.getWorld() != impact.getWorld()) {
            return;
        }
        if (remainingMs(p, Items.GRAPLER) > 0) {
            return;
        }

        double dist = from.distance(impact);
        if (dist > range) {
            p.sendActionBar(Component.text("Za daleko! Zasieg: " + (int) range + " kratek", NamedTextColor.RED));
            plugin.getServer().getScheduler().runTask(plugin, hook::remove);
            return;
        }

        boolean hitLiving = hitEntity instanceof LivingEntity;
        if (hitEntity != null && !hitLiving) {
            return; // przedmioty, lodki itp. ignorujemy
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (hitLiving) {
                if (mode.equals("SELF")) {
                    pull(p, impact);
                } else {
                    pull(hitEntity, p.getLocation());
                }
            } else {
                if (mode.equals("ENEMY")) {
                    hook.remove();
                    return;
                }
                pull(p, impact);
            }

            if (cfg.getBoolean("grapler.no-fall-damage", true)) {
                noFall.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
            }
            hook.remove();
        });

        startCooldown(p, Items.GRAPLER, cooldown);
    }

    private void pull(Entity entity, Location to) {
        Vector v = to.toVector().subtract(entity.getLocation().toVector());
        double dist = v.length();
        if (dist < 0.5) {
            return;
        }
        double max = plugin.getConfig().getDouble("grapler.max-speed", 2.2);
        double speed = Math.min(max, 0.5 + dist * 0.14);
        v.normalize().multiply(speed);
        v.setY(Math.max(v.getY(), 0.0) + 0.35);
        entity.setVelocity(v);
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
            return;
        }
        Long until = noFall.get(p.getUniqueId());
        if (until == null) {
            return;
        }
        if (until > System.currentTimeMillis()) {
            e.setCancelled(true);
        } else {
            noFall.remove(p.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- miecz brata

    @EventHandler(ignoreCancelled = true)
    public void onSwordHit(EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK) {
            return; // tylko zwykle uderzenie, nie sweep
        }
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!Items.BRATA.equals(items.idOf(p.getInventory().getItemInMainHand()))) {
            return;
        }

        long left = remainingMs(p, Items.BRATA);
        if (left > 0) {
            cooldownMessage(p, "Miecz Brata", left);
            return;
        }

        FileConfiguration cfg = plugin.getConfig();
        int glowing = cfg.getInt("brata.glowing-seconds", 30);
        int poison = cfg.getInt("brata.poison-seconds", 10);
        int poisonLevel = Math.max(cfg.getInt("brata.poison-level", 2), 1);

        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowing * 20, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poison * 20, poisonLevel - 1));

        startCooldown(p, Items.BRATA, cfg.getInt("brata.cooldown-seconds", 120));
        p.sendActionBar(Component.text("Miecz Brata: trafiony!", NamedTextColor.DARK_RED));
    }
}
