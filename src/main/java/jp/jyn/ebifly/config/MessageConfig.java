package jp.jyn.ebifly.config;

import jp.jyn.ebifly.EbiFlyPlugin;
import jp.jyn.jbukkitlib.config.YamlLoader;
import jp.jyn.jbukkitlib.config.parser.component.ComponentParser;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public class MessageConfig {
    public final static String PREFIX = "[EbiFly] ";
    public final static String HEADER = "========== EbiFly ==========";
    public final static String PLAYER_ONLY = PREFIX + ChatColor.RED + "This command can only be run by players.";

    public final String locale;

    public final ComponentParser invalidNumber;
    public final ComponentParser playerNotFound;
    public final ComponentParser permissionError;

    public final ComponentParser flyDisable;
    //public final Consumer<Player> flyDisable;
    //public final Consumer<Player> flyTimeout;

    public final ComponentParser paymentInsufficient;
    public final ComponentParser paymentRefund;

    public final HelpMessage help;

    public MessageConfig(EbiFlyPlugin plugin, String locale, MainConfig main) {
        this.locale = locale;
        var config = loadConfig(plugin, locale);

        invalidNumber = p(config.getString("invalidNumber"));
        playerNotFound = p(config.getString("playerNotFound"));
        permissionError = p(config.getString("permissionError"));

        flyDisable = m(main.noticeDisable, config.getString("fly.disable"));
        paymentRefund = m(main.noticePayment, config.getString("payment.refund"));

        /*flyDisable = n(main.noticeDisable.message, config.getString("fly.disable"));
        flyTimeout = nc(main.noticeTimeout.message, config.getString("fly.timeout"),
            ComponentVariable.init().put("time", main.noticeTimeoutSecond));*/

        paymentInsufficient = p(config.getString("payment.insufficient"));

        help = new HelpMessage(Objects.requireNonNull(config.getConfigurationSection("help")));
    }

    private static ComponentParser p(String value) {
        return ComponentParser.parse(PREFIX + value);
    }

    public static ComponentParser m(MainConfig.NoticeConfig config, String value) {
        return switch (config.message) {
            case ACTION_BAR -> p(value);
            case CHAT -> ComponentParser.parse(value);
            case FALSE -> null;
        };
    }

    /*private static Consumer<Player> n(MainConfig.NoticeConfig.MessagePosition position, String value) {
        return switch (position) {
            case FALSE -> p -> {};
            case CHAT -> p(value).apply()::send;
            case ACTION_BAR -> ComponentParser.parse(value).apply()::actionbar;
        };
    }

    private static Consumer<Player> nc(MainConfig.NoticeConfig.MessagePosition position, String value,
                                       ComponentVariable variable) {
        return switch (position) {
            case FALSE -> p -> {};
            case CHAT -> p(value).apply(variable)::send;
            case ACTION_BAR -> p(value).apply(variable)::actionbar;
        };
    }*/

    private FileConfiguration loadConfig(Plugin plugin, String locale) {
        var f = "locale/" + locale + ".yml";
        var l = plugin.getLogger();
        var c = YamlLoader.load(plugin, f);

        if (c.getInt("version", -1) != Objects.requireNonNull(c.getDefaults()).getInt("version")) {
            l.warning("Detected plugin downgrade!!");
            l.warning("Incompatible changes in " + f);
            l.warning("Please check " + f + ". If you need downgrade, use backup.");
        }
        return c;
    }

    public final static class HelpMessage {
        public final ComponentParser fly;
        public final ComponentParser version;
        public final ComponentParser reload;
        public final ComponentParser help;

        private HelpMessage(ConfigurationSection config) {
            fly = p("/fly [%s] [%s] - %s".formatted(
                config.getString("time", "time"),
                config.getString("player", "player"),
                config.getString("fly"))
            );
            version = p("/fly version - " + config.getString("version"));
            reload = p("/fly reload - " + config.getString("reload"));
            help = p("/fly help - " + config.getString("help"));
        }
    }
}
