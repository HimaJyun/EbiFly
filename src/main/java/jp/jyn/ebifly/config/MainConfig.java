package jp.jyn.ebifly.config;

import jp.jyn.ebifly.EbiFlyPlugin;
import jp.jyn.jbukkitlib.config.YamlLoader;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;

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
    public final NoticeConfig.MessagePosition noticePaymentPersist;

    public MainConfig(EbiFlyPlugin plugin) {
        var config = loadConfig(plugin);
        versionCheck = config.getBoolean("versionCheck");

        safetyFall = config.getBoolean("safety.fall");
        safetyLava = config.getInt("safety.lava");

        localeEnable = config.getBoolean("locale.enable");
        localeDefault = Objects.requireNonNull(config.getString("locale.default"), "locale.default is null");

        economy = config.getBoolean("economy.enable")
            ? new EconomyConfig(plugin, getSection(plugin, config, "economy"))
            : null;

        noticeEnable = new NoticeConfig(plugin, getSection(plugin, config, "notice.enable"));
        noticeDisable = new NoticeConfig(plugin, getSection(plugin, config, "notice.disable"));
        noticeTimeout = new NoticeConfig(plugin, getSection(plugin, config, "notice.timeout"));
        noticePayment = new NoticeConfig(plugin, getSection(plugin, config, "notice.payment"));

        int second = config.getInt("notice.timeout.second");
        if (second > 60) {
            plugin.getLogger().severe("notice.timeout.second is greater than 60 in config.yml");
            plugin.getLogger().severe("Using 60");
        }
        noticeTimeoutSecond = Math.min(second, 60);
        noticePaymentPersist = NoticeConfig.getPosition(config.getString("notice.payment.persist"));
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

    // TODO: plugin渡すのではなくLoggerを渡す方が良い
    private static ConfigurationSection getSection(Plugin plugin, ConfigurationSection config, String key) {
        final String msg = "Default is null, broken plugin!!" +
            " You using non-modded jar? Please try re-download jar." +
            " (from: https://github.com/HimaJyun/EbiFly/ )";
        var l = plugin.getLogger();
        if (!config.contains(key, true)) {
            l.severe("%s is not found in config.yml".formatted(key));
            l.severe("Using default value.");
            return Objects.requireNonNull(config.getConfigurationSection(key), msg);
        } else if (!config.isConfigurationSection(key)) {
            l.severe("%s is not valid value in config.yml".formatted(key));
            l.severe("Using default value.");
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
        public final double margin; // TODO: 使わない？
        //public final ExpressionParser dynamicCost; // TODO: 要らない？

        public final String server;
        public final RefundType refund;

        private EconomyConfig(Plugin plugin, ConfigurationSection config) {
            async = config.getBoolean("async");
            margin = config.getDouble("margin");
            server = config.getString("server");

            var l = plugin.getLogger();
            double v = config.getDouble("price");
            if (Double.compare(v, 0.0) < 0) {
                l.warning("economy.price is less than 0 in config.yml");
                l.warning("Using 0");
            }
            price = Math.max(0.0d, v);

            var r = config.getString("refund");
            RefundType t;
            if (r == null) {
                l.severe("economy.refund is null in config.yml");
                l.severe("Using default value");
                r = RefundType.TRUE.name();
            }
            try {
                t = RefundType.valueOf(r.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                l.severe("%s is invalid value in config.yml".formatted(r));
                l.severe("Using default value");
                t = RefundType.TRUE;
            }
            refund = t;
        }
    }

    public static class NoticeConfig {
        public enum MessagePosition {CHAT, ACTION_BAR, FALSE}

        public final MessagePosition message;

        public final Particle particle;
        public final int particleCount;
        public final double particleOffsetX;
        public final double particleOffsetY;
        public final double particleOffsetZ;

        public final Sound sound;
        public final float soundVolume;
        public final float soundPitch;

        private NoticeConfig(Plugin plugin, ConfigurationSection config) {
            message = getPosition(config.getString("message"));

            if (isEnable(config, "particle")) {
                particle = getType(Particle.class, plugin, config, "particle");

                particleCount = config.getInt("particle.count", 1);
                if (config.contains("particle.offset", true) && config.isDouble("particle.offset")) {
                    // 隠し機能、offsetに数値を指定するとxyzに同じ値を設定
                    particleOffsetX = particleOffsetY = particleOffsetZ = config.getDouble("particle.offset", 0d);
                } else {
                    particleOffsetX = config.getDouble("particle.offset.x", 0d);
                    particleOffsetY = config.getDouble("particle.offset.y", 0d);
                    particleOffsetZ = config.getDouble("particle.offset.z", 0d);
                }
            } else {
                particle = null;
                particleCount = 0;
                particleOffsetX = particleOffsetY = particleOffsetZ = 0;
            }

            if (isEnable(config, "sound")) {
                sound = getType(Sound.class, plugin, config, "sound");
                soundVolume = (float) config.getDouble("sound.volume", 1.0d);
                soundPitch = (float) config.getDouble("sound.pitch", 1.0d);
            } else {
                sound = null;
                soundVolume = soundPitch = 0.0f;
            }
        }

        private static <E extends Enum<E>> E getType(Class<E> clazz, Plugin plugin,
                                                     ConfigurationSection config, String key) {
            var k = key + ".type";
            try {
                return Enum.valueOf(clazz, config.getString(k, "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("%s is invalid value in config.yml".formatted(
                    config.getString(k, config.getCurrentPath() + "." + k))
                );
                return null;
            }
        }

        private static boolean isEnable(ConfigurationSection config, String key) {
            return config.contains(key, true)
                && config.isConfigurationSection(key)
                && switch (config.getString(key + ".type", "false").toUpperCase(Locale.ROOT)) {
                case "FALSE", "NULL" -> false;
                default -> true;
            };
        }

        private static MessagePosition getPosition(String value) {
            if (value == null) {
                return MessagePosition.FALSE;
            }
            return switch (value.toUpperCase(Locale.ROOT)) {
                case "CHAT", "TRUE" -> MessagePosition.CHAT;
                case "ACTIONBAR", "ACTION_BAR" -> MessagePosition.ACTION_BAR;
                default -> MessagePosition.FALSE;
            };
        }
    }
}
