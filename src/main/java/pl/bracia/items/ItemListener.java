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
import org.bukkit.scheduler.BukkitRunnable;
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
    /** haczyki Graplera, ktore jeszcze nie odpalily: uuid haczyka -> uuid gracza */
    private final Map<UUID, UUID> activeHooks = new HashMap<>();

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

    /** Rzut wedka: blokujemy na cooldownie, w przeciwnym razie obserwujemy haczyk. */
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

        FishHook hook = e.getHook();
        items.tag(hook, Items.GRAPLER);
        activeHooks.put(hook.getUniqueId(), p.getUniqueId());
        watch(p, hook);
    }

    /** Co tick sprawdza haczyk: odpala, gdy doleci na zasieg albo minie czas lotu. */
    private void watch(Player p, FishHook hook) {
        FileConfiguration cfg = plugin.getConfig();
        double range = cfg.getDouble("grapler.range", 10.0);
        int airTicks = cfg.getInt("grapler.air-ticks", 12);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!activeHooks.containsKey(hook.getUniqueId())) {
                    cancel(); // juz odpalil (trafil w cos)
                    return;
                }
                if (!hook.isValid() || !p.isOnline() || hook.getWorld() != p.getWorld()) {
                    activeHooks.remove(hook.getUniqueId());
                    cancel();
                    return;
                }
                double dist = hook.getLocation().distance(p.getLocation());
                if (dist >= range || ticks >= airTicks) {
                    fire(p, hook, hook.getLocation());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Haczyk trafil w blok lub moba. */
    @EventHandler
    public void onHookHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof FishHook hook)) {
            return;
        }
        UUID ownerId = activeHooks.get(hook.getUniqueId());
        if (ownerId == null) {
            return;
        }
        Player p = plugin.getServer().getPlayer(ownerId);
        if (p == null) {
            activeHooks.remove(hook.getUniqueId());
            return;
        }
        Entity hitEntity = e.getHitEntity();
        Location target = hitEntity != null ? hitEntity.getLocation() : hook.getLocation();
        fire(p, hook, target);
    }

    /** Odpala wyrzut dokladnie raz na haczyk. */
    private void fire(Player p, FishHook hook, Location target) {
        if (activeHooks.remove(hook.getUniqueId()) == null) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        startCooldown(p, Items.GRAPLER, cfg.getInt("grapler.cooldown-seconds", 60));
        if (cfg.getBoolean("grapler.no-fall-damage", true)) {
            noFall.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
        }

        Location t = target.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            launch(p, t);
            hook.remove();
        });
    }

    /** Wyrzuca gracza w strone haka. Rozped sprawia, ze leci troche dalej niz hak. */
    private void launch(Player p, Location target) {
        if (!p.isOnline() || p.getWorld() != target.getWorld()) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        double power = cfg.getDouble("grapler.power", 1.0);
        double max = cfg.getDouble("grapler.max-speed", 2.4);

        Vector dir = target.toVector().subtract(p.getEyeLocation().toVector());
        double dist = dir.length();
        if (dist < 0.5) {
            dir = p.getLocation().getDirection();
            dist = 1.0;
        }
        double speed = Math.min(max, (0.7 + dist * 0.12) * power);
        dir.normalize().multiply(speed);
        dir.setY(dir.getY() + 0.25);
        p.setVelocity(dir);
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
