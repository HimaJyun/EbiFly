package jp.jyn.ebifly.listener;

import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.jbukkitlib.config.YamlLoader;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SafetyListener implements Listener {
    private final static String FILE_NAME = "safety.yml";
    private final static int FILE_VERSION = 1;

    // key=カウント開始時間, value=経過時間
    private final Map<UUID, Map.Entry<Long, Long>> safety = new ConcurrentHashMap<>();

    private final Consumer<Runnable> syncCall;

    private final boolean fall;
    private final boolean _void;
    private final int lava;
    private final long limit;
    private final long cleanup;

    public SafetyListener(Plugin plugin, MainConfig config,
                          Consumer<Runnable> syncCall) {
        this.syncCall = syncCall; // 実はいらない

        this.fall = config.safetyFall;
        this._void = config.safetyVoid;
        this.lava = config.safetyLava * 20; // tick
        this.limit = config.safetyLimit;
        this.cleanup = config.safetyCleanup;

        // 非同期ロード
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> load(plugin));

        // イベント登録
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    synchronized private void load(Plugin plugin) { // ロード中に保存が走るとマズいので同期する (起動即終了やリロードで十分あり得る)
        var logger = plugin.getLogger();
        var f = new File(plugin.getDataFolder(), FILE_NAME);
        if (!f.exists()) {
            return;
        } else if (!f.isFile() || !f.canRead()) {
            logger.warning("%s cannot file or readable".formatted(f.getAbsolutePath()));
            return;
        }

        var config = YamlConfiguration.loadConfiguration(f);
        if (config.getInt("version", -1) != FILE_VERSION) {
            logger.warning("Detected plugin downgrade!!");
            logger.warning("Incompatible changes in %s".formatted(f.getAbsolutePath()));
            logger.warning("Can't read safety targets. If you need downgrade, ignore this error.");
            logger.warning("Files have been backed up for safety.");
            YamlLoader.backup(plugin, FILE_NAME);
            return;
        }

        var players = config.getConfigurationSection("player");
        if (players == null) {
            return;
        }

        var nano = System.nanoTime();
        var unix = System.currentTimeMillis();
        for (String key : players.getKeys(false)) {
            var player = players.getConfigurationSection(key);
            if (player == null) {
                continue;
            }

            long time, elapsed;
            if (player.get("time") instanceof Number t
                && player.get("elapsed") instanceof Number n) {
                time = t.longValue();
                elapsed = n.longValue();
            } else {
                return;
            }

            // UnixTimeになってるので、現在時刻との差に戻してnanoTimeに換算する
            var startAt = nano - TimeUnit.MILLISECONDS.toNanos(unix - time);

            // チェック
            if (checkAll(startAt, elapsed, nano)) {
                return;
            }
            safety.put(UUID.fromString(key), Map.entry(startAt, elapsed));
        }
    }

    synchronized public void save(Plugin plugin) { // 同時に保存が走るとマズいので一応同期しておく (本当はファイルロックもしなきゃダメ)
        var file = new File(plugin.getDataFolder(), FILE_NAME);
        // オンライン中の全てのユーザーのカウントを止める (そうしないと経過時間が更新されずに終了してしまう)
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getOnlinePlayers().forEach(this::stopCount);
        } else {
            var f = Bukkit.getScheduler().callSyncMethod(plugin, () ->
                Bukkit.getOnlinePlayers().toArray(new Player[0]) // スレッドセーフじゃないので同期コピーする
            );
            try {
                Arrays.stream(f.get()).forEach(this::stopCount);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        var config = new YamlConfiguration();
        config.options().copyHeader(true);
        config.options().header("""
            This file generated automatically for safety feature.
            If you don't want this feature, you can disable it from 'config.yml' and can delete this file.
            You can also use 'limit' and 'cleanup' to prevent old data from accumulating."""
        );
        config.set("version", FILE_VERSION);

        var nano = System.nanoTime();
        var unix = System.currentTimeMillis();
        var c1 = config.createSection("player");
        var it = safety.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var v = entry.getValue();
            // 保存の必要性をチェック
            if (checkAll(v.getKey(), v.getValue(), nano)) {
                // 要らない
                it.remove();
                continue;
            }

            var c2 = c1.createSection(entry.getKey().toString());
            // nanoTimeで取れた値は保存できないのでUnixTimeに換算する
            var startAt = nano - v.getKey();
            c2.set("time", unix - TimeUnit.NANOSECONDS.toMillis(startAt)); // 精度少し落ちる
            c2.set("elapsed", v.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void add(Player player) {
        safety.put(player.getUniqueId(), Map.entry(System.nanoTime(), 0L));
    }

    public void remove(Player player) {
        safety.remove(player.getUniqueId());
    }

    private boolean checkAll(long startAt, long elapsed, long nanoTime) {
        return (limit > 0 && elapsed > limit)
            || (cleanup > 0 && (nanoTime - startAt) > cleanup);
    }

    public void stopCount(Player player) {
        // 経過時間を数えてカウント開始時間をリセットする (クリーンアップは停止後の放置時間の処理なのでここでは触らなくていい)
        safety.computeIfPresent(player.getUniqueId(), (u, e) -> {
            var nano = System.nanoTime();
            var elapsed = (nano - e.getKey()) + e.getValue();

            return limit > 0 && elapsed > limit ? null
                : Map.entry(nano, elapsed);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onPlayerJoin(PlayerJoinEvent e) {
        // ログイン時は経過時間を数えてクリーンアップする (リミットは実行時に判断される+停止時に処理してるのでここでは触らなくていい)
        safety.computeIfPresent(e.getPlayer().getUniqueId(), (u, entry) -> {
            long nano = System.nanoTime();

            return cleanup > 0 && (nano - entry.getKey()) > cleanup ? null
                : Map.entry(nano, entry.getValue());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public final void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntityType() != EntityType.PLAYER) {
            return;
        }

        // 落下、奈落 → 1回限り
        // 溶岩 -> 時間内だけ
        switch (e.getCause()) {
            case FALL -> {
                if (fall) {
                    onEntityDamageByFall(e);
                }
            }
            case VOID -> {
                if (_void) {
                    onEntityDamageByVoid(e);
                }
            }
            case LAVA -> {
                if (lava > 0) {
                    onEntityDamageByLava(e);
                }
            }
        }
    }

    private boolean checkLimit(Entity entity) {
        var v = safety.remove(entity.getUniqueId());
        if (v == null) {
            return true;
        }

        var elapsed = (System.nanoTime() - v.getKey());
        return limit > 0 && (elapsed + v.getValue()) > limit;
    }

    private void onEntityDamageByFall(EntityDamageEvent e) {
        if (checkLimit(e.getEntity())) {
            return;
        }

        e.setCancelled(true);
    }

    private void onEntityDamageByVoid(EntityDamageEvent e) {
        var entity = e.getEntity();
        if (entity.getLocation().getBlockY() > 0 /* kill */ || checkLimit(entity)) {
            return;
        }

        // 1tickズラしてから移動しないとダメ
        syncCall.accept(() -> {
            entity.setFallDistance(0);
            entity.teleport(entity.getWorld().getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        });
        e.setCancelled(true);
    }

    private void onEntityDamageByLava(EntityDamageEvent e) {
        var entity = e.getEntity();
        if (checkLimit(entity)) {
            return;
        }

        // ここに来てる時点で溶岩ダメージのはずなので、火炎耐性はない -> 火炎耐性を追加
        ((Player) entity).addPotionEffect(new PotionEffect(
            PotionEffectType.FIRE_RESISTANCE, lava, 0, false, false, false)
        );
        e.setCancelled(true);
    }
}
