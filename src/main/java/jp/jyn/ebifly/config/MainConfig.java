package jp.jyn.ebifly.config;

import jp.jyn.ebifly.PluginMain;
import jp.jyn.jbukkitlib.config.YamlLoader;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class MainConfig {
    public final boolean versionCheck;

    public final boolean safetyFall;
    public final int safetyLava;

    public final boolean localeEnable;
    public final String localeDefault;

    // TODO: 権限チェック？
    //public final boolean disablingLevitation;
    //public final boolean disablingChangeWorld;

    public final EconomyConfig economy;

    public final NoticeConfig noticeEnable;
    public final NoticeConfig noticeDisable;
    public final NoticeConfig noticeTimeout;
    public final NoticeConfig noticePayment;

    public final int noticeTimeoutSecond;
    public final boolean noticeTimeoutActionbar;
    public final boolean noticePaymentActionbar;

    public MainConfig(PluginMain plugin) {
        var logger = plugin.getLogger();
        var config = loadConfig(plugin);
        versionCheck = config.getBoolean("versionCheck");

        safetyFall = config.getBoolean("safety.fall");
        safetyLava = config.getInt("safety.lava");

        localeEnable = config.getBoolean("locale.enable");
        localeDefault = Objects.requireNonNull(config.getString("locale.default"), "locale.default is null");

        if (config.getBoolean("economy.enable")) {
            var e = new EconomyConfig(logger, getSection(logger, config, "economy"));
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
        noticeTimeoutActionbar = config.getBoolean("notice.timeout.actionbar", false);
        noticePaymentActionbar = config.getBoolean("notice.payment.actionbar", false);
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

    public static class EconomyConfig {
        public enum RefundType {TRUE, FALSE, PAYER}

        public final boolean async; // TODO: 常に同期で良い？
        public final double price;

        public final String server;
        public final RefundType refund;

        private EconomyConfig(Logger logger, ConfigurationSection config) {
            price = config.getDouble("price");
            server = config.getString("server");
            async = config.getBoolean("async");

            var r = config.getString("refund");
            RefundType t;
            if (r == null) {
                logger.severe("economy.refund is null in config.yml");
                logger.severe("Using default value");
                r = RefundType.TRUE.name();
            }
            try {
                t = RefundType.valueOf(r.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.severe("%s is invalid value in config.yml".formatted(r));
                logger.severe("Using default value");
                t = RefundType.TRUE;
            }
            refund = t;
        }
    }

    public static class NoticeConfig {
        private final static Consumer<Location> DISABLE = ignore -> {};

        public final Consumer<Location> particle;
        public final Consumer<Location> sound;

        private Consumer<Location> merged = null;

        private NoticeConfig(Logger logger, ConfigurationSection config) {
            var p = getType(Particle.class, logger, config, "particle");
            if (p == null) {
                particle = DISABLE;
            } else {
                var c = config.getInt("particle.count", 1);
                var e = config.getDouble("particle.extra", 0d);
                double x, y, z;
                if (config.contains("particle.offset", true) && config.isDouble("particle.offset")) {
                    // offsetに数値を指定するとxyzに同じ値を設定
                    x = y = z = config.getDouble("particle.offset", 0.5);
                } else {
                    x = config.getDouble("particle.offset.x", 0.5);
                    y = config.getDouble("particle.offset.y", 0.5);
                    z = config.getDouble("particle.offset.z", 0.5);
                }
                particle = l -> Objects.requireNonNull(l.getWorld(), "Location don't have world.")
                    .spawnParticle(p, l, c, x, y, z, e);
            }

            var s = getType(Sound.class, logger, config, "sound");
            if (s == null) {
                sound = DISABLE;
            } else {
                var v = (float) config.getDouble("sound.volume", 1.0d);
                var pi = (float) config.getDouble("sound.pitch", 1.0d);
                sound = l -> Objects.requireNonNull(l.getWorld(), "Location don't have world.")
                    .playSound(l, s, SoundCategory.PLAYERS, v, pi);
            }
        }

        public Consumer<Location> merge() {
            return merged != null ? merged
                : (merged = (particle != DISABLE && sound != DISABLE ? particle.andThen(sound)
                : particle == DISABLE ? sound : particle));
        }

        public Consumer<Location> merge(NoticeConfig second) {
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
}
