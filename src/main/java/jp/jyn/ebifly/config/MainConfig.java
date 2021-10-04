package jp.jyn.ebifly.config;

import jp.jyn.ebifly.PluginMain;
import jp.jyn.jbukkitlib.config.YamlLoader;
import jp.jyn.jbukkitlib.config.parser.TimeParser;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class MainConfig {
    public final boolean versionCheck;

    public final boolean safetyFall;
    public final boolean safetyVoid;
    public final int safetyLava;
    public final boolean safetyLevitation;
    public final long safetyLimit;
    public final long safetyCleanup;
    public final long safetySave;

    public final boolean localeEnable;
    public final String localeDefault;

    public final boolean restrictRespawn;
    public final boolean restrictWorld;
    public final boolean restrictGamemode;
    public final Boolean restrictLevitation;
    public final Boolean restrictWater;

    public final EconomyConfig economy;

    public final NoticeConfig noticeEnable;
    public final NoticeConfig noticeDisable;
    public final NoticeConfig noticeTimeout;
    public final NoticeConfig noticePayment;

    public final int noticeTimeoutSecond;
    public final NoticePosition noticeTimeoutPosition;
    public final NoticePosition noticePaymentPosition;

    public MainConfig(PluginMain plugin) {
        var logger = plugin.getLogger();
        var config = loadConfig(plugin);
        versionCheck = config.getBoolean("versionCheck");

        safetyFall = config.getBoolean("safety.fall");
        safetyVoid = config.getBoolean("safety.void");
        safetyLava = config.getInt("safety.lava");
        safetyLevitation = config.getBoolean("safety.levitation");
        safetyLimit = TimeParser.parse(config.getString("safety.limit", "0"), TimeUnit.NANOSECONDS);
        safetyCleanup = TimeParser.parse(config.getString("safety.cleanup", "0"), TimeUnit.NANOSECONDS);
        safetySave = TimeParser.parse(config.getString("safety.save", "0"), TimeUnit.SECONDS);

        localeEnable = config.getBoolean("locale.enable");
        localeDefault = Objects.requireNonNull(config.getString("locale.default"), "locale.default is null");

        restrictRespawn = config.getBoolean("restrict.respawn");
        restrictWorld = config.getBoolean("restrict.world");
        restrictGamemode = config.getBoolean("restrict.gamemode");
        restrictLevitation = triple(config.getString("restrict.levitation"), "temporary");
        restrictWater = triple(config.getString("restrict.water"), "temporary");

        if (config.getBoolean("economy.enable")) {
            var e = new EconomyConfig(getSection(logger, config, "economy"));
            if (Double.compare(e.price, 0) <= 0) {
                logger.warning("economy.price is 0 or less in config.yml");
                logger.warning("Disabled economy feature.");
                economy = null;
            } else {
                economy = e;
            }
        } else {
            economy = null;
        }

        noticeEnable = new NoticeConfig(logger, getSection(logger, config, "notice.enable"));
        noticeDisable = new NoticeConfig(logger, getSection(logger, config, "notice.disable"));
        noticeTimeout = new NoticeConfig(logger, getSection(logger, config, "notice.timeout"));
        noticePayment = new NoticeConfig(logger, getSection(logger, config, "notice.payment"));

        int second = config.getInt("notice.timeout.second");
        if (second > 60) {
            logger.severe("notice.timeout.second is greater than 60 in config.yml");
            logger.severe("Using 60");
        }
        noticeTimeoutSecond = Math.min(second, 60);
        noticeTimeoutPosition = pos(logger, config, "notice.timeout");
        noticePaymentPosition = pos(logger, config, "notice.payment");
    }

    private static FileConfiguration loadConfig(Plugin plugin) {
        var l = plugin.getLogger();
        var c = YamlLoader.load(plugin, "config.yml");
        if (!c.contains("version", true)) {
            // 1.0
            l.warning("IMPORTANT NOTICE: Older than 2.0 is not compatible this version!!");
            l.warning("Detected older version config.yml, this file backup to config_old.yml");
            l.warning("Please check new config.yml");
            YamlLoader.move(plugin, "config.yml", "config_old.yml");
            return YamlLoader.load(plugin, "config.yml"); // ファイル作り直し
        }

        if (c.getInt("version", -1) != Objects.requireNonNull(c.getDefaults()).getInt("version")) {
            l.warning("Detected plugin downgrade!!");
            l.warning("Incompatible changes in config.yml");
            l.warning("Please check config.yml. If you need downgrade, use backup.");
        }
        return c;
    }

    private static ConfigurationSection getSection(Logger logger, ConfigurationSection config, String key) {
        final String msg = "Default is null, broken plugin!!" +
            " You using non-modded jar? Please try re-download jar and checking hash." +
            " (from: https://github.com/HimaJyun/EbiFly/ )";
        if (!config.contains(key, true)) {
            logger.severe("%s is not found in config.yml".formatted(key));
            logger.severe("Using default value.");
            return Objects.requireNonNull(config.getConfigurationSection(key), msg);
        } else if (!config.isConfigurationSection(key)) {
            logger.severe("%s is not valid value in config.yml".formatted(key));
            logger.severe("Using default value.");
            var d = Objects.requireNonNull(config.getDefaultSection(), msg);
            return Objects.requireNonNull(d.getConfigurationSection(key), msg);
        } else {
            return Objects.requireNonNull(config.getConfigurationSection(key), "Unknown error in config.yml");
        }
    }

    private static Boolean triple(String value, String tripe) {
        if (value == null) {
            return null;
        }

        var v = value.toLowerCase(Locale.ROOT);
        return v.equals("true") ? Boolean.TRUE
            : v.equals(tripe) ? Boolean.FALSE
            : null;
    }

    private static NoticePosition pos(Logger logger,
                                      ConfigurationSection config, String key) {
        var p = config.getString(key + ".position", key + ".position");
        var e = switch (p.toLowerCase(Locale.ROOT)) {
            case "false", "null" -> NoticePosition.Position.FALSE;
            case "true", "chat" -> NoticePosition.Position.CHAT;
            case "actionbar" -> NoticePosition.Position.ACTION_BAR;
            case "subtitle" -> NoticePosition.Position.SUB_TITLE;
            case "title" -> NoticePosition.Position.TITLE;
            default -> {
                logger.warning(p + " is invalid in config.yml");
                yield NoticePosition.Position.FALSE;
            }
        };

        // 本当はrecord作った方が良い
        Function<String, Integer> s = str -> {
            var k = key + ".title." + str;
            if (config.get(k) instanceof Number n) { // isXxxだと型がピッタリ合わないと認識しない
                if (Double.compare(n.doubleValue(), 0) < 0) {
                    logger.warning(k + " is 0 or less in config.yml");
                    return 0;
                } else {
                    return (int) (n.doubleValue() * 20);
                }
            } else {
                logger.warning(k + " is invalid in config.yml");
                return 0;
            }
        };

        return switch (e) {
            case TITLE, SUB_TITLE -> new NoticePosition(e, s.apply("fadeIn"), s.apply("stay"), s.apply("fadeOut"));
            default -> new NoticePosition(e, 0, 0, 0);
        };
    }

    public static class EconomyConfig {
        public final double price;
        public final String server;
        public final Boolean refund; // trueならself、falseならpayer

        private EconomyConfig(ConfigurationSection config) {
            price = config.getDouble("price");
            server = config.getString("server");
            refund = triple(config.getString("refund"), "payer");
        }
    }

    public static class NoticeConfig {
        private final static Consumer<Player> DISABLE = ignore -> {};

        public final Consumer<Player> particle;
        public final Consumer<Player> sound;

        private Consumer<Player> merged = null;

        private NoticeConfig(Logger logger, ConfigurationSection config) {
            var p = getType(Particle.class, logger, config, "particle");
            if (p == null) {
                particle = DISABLE;
            } else {
                var c = config.getInt("particle.count", 1);
                var e = config.getDouble("particle.extra", 0d);
                double x, y, z;
                if (config.contains("particle.offset", true)
                    && (config.get("particle.offset", null) instanceof Number n)) {
                    // offsetに数値を指定するとxyzに同じ値を設定
                    x = y = z = n.doubleValue();
                } else {
                    x = config.getDouble("particle.offset.x", 0.5);
                    y = config.getDouble("particle.offset.y", 0.5);
                    z = config.getDouble("particle.offset.z", 0.5);
                }
                // 目の高さで表示した方が分かりやすい
                particle = config.getBoolean("particle.global")
                    ? player -> player.getWorld().spawnParticle(p, player.getEyeLocation(), c, x, y, z, e)
                    : player -> player.spawnParticle(p, player.getEyeLocation(), c, x, y, z, e);
            }

            var s = getType(Sound.class, logger, config, "sound");
            if (s == null) {
                sound = DISABLE;
            } else {
                var v = (float) config.getDouble("sound.volume", 1.0d);
                var pi = (float) config.getDouble("sound.pitch", 1.0d);
                sound = config.getBoolean("sound.global")
                    ? player -> player.getWorld().playSound(player.getLocation(), s, v, pi)
                    : player -> player.playSound(player.getLocation(), s, v, pi);
            }

            // Player#playSoundやspawnParticleは該当プレイヤーに対してだけ効果が起きる
            // World版はその場にいる人全員に起きる。
        }

        public Consumer<Player> merge() {
            return merged != null ? merged
                : (merged = (particle != DISABLE && sound != DISABLE ? particle.andThen(sound)
                : particle == DISABLE ? sound : particle));
        }

        public Consumer<Player> merge(NoticeConfig second) {
            var p = particle != DISABLE ? particle : second.particle;
            var s = sound != DISABLE ? sound : second.sound;
            return p != DISABLE && s != DISABLE ? p.andThen(s) : p == DISABLE ? s : p;
        }

        private static <E extends Enum<E>> E getType(Class<E> clazz, Logger logger,
                                                     ConfigurationSection config, String key) {
            if (!config.contains(key, true) || !config.isConfigurationSection(key)) {
                return null;
            }

            final var k = key + ".type";
            try {
                var v = config.getString(k, "").toUpperCase(Locale.ROOT);
                if (v.equals("FALSE") || v.equals("NULL") || v.isEmpty()) { // 隠し機能、実はNULLや空文字でも出力ストップが可能
                    return null;
                }

                return Enum.valueOf(clazz, v);
            } catch (IllegalArgumentException e) {
                logger.severe("%s is invalid value in config.yml".formatted(
                    config.getString(k, config.getCurrentPath() + "." + k))
                );
                return null;
            }
        }
    }

    public static final record NoticePosition(Position position, int fadeIn, int stay, int fadeOut) {
        public enum Position {FALSE, CHAT, ACTION_BAR, SUB_TITLE, TITLE}
    }
}
