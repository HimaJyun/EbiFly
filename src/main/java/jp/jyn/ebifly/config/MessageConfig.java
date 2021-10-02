package jp.jyn.ebifly.config;

import jp.jyn.ebifly.PluginMain;
import jp.jyn.jbukkitlib.config.YamlLoader;
import jp.jyn.jbukkitlib.config.parser.component.ComponentParser;
import jp.jyn.jbukkitlib.config.parser.component.ComponentVariable;
import jp.jyn.jbukkitlib.config.parser.template.TemplateParser;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class MessageConfig {
    public final static String PREFIX = "[EbiFly] ";
    public final static String PLAYER_ONLY = PREFIX + ChatColor.RED + "This command can only be run by players.";

    public final String locale;

    public final ComponentParser invalidNumber;
    public final ComponentParser playerNotFound;
    public final ComponentParser permissionError;

    public final ComponentParser flyEnable;
    public final ComponentParser flyDisable;
    public final ComponentParser flyAdd;
    public final ComponentParser flySend;
    public final ComponentParser flyReceive;
    public final ComponentParser flyCreative;
    public final ComponentParser flyUnavailable;
    public final BiConsumer<Player, Supplier<String>> flyTimeout;

    public final PaymentMessage payment;
    public final HelpMessage help;

    public MessageConfig(PluginMain plugin, String locale, MainConfig main) {
        this.locale = locale;
        var config = loadConfig(plugin, locale);
        UnaryOperator<String> s = k -> {
            var v = config.getString(k);
            if (v == null || v.isEmpty()) {
                plugin.getLogger().warning("%s is null or empty in locale/%s.yml".formatted(k, locale));
                return "";
            }
            return v;
        };

        invalidNumber = p(s.apply("invalidNumber"));
        playerNotFound = p(s.apply("playerNotFound"));
        permissionError = p(s.apply("permissionError"));

        flyEnable = p(s.apply("fly.enable"));
        flyDisable = p(s.apply("fly.disable"));
        flyAdd = p(s.apply("fly.add"));
        flySend = p(s.apply("fly.send"));
        flyReceive = p(s.apply("fly.receive"));
        flyCreative = p(s.apply("fly.creative"));
        flyUnavailable = p(s.apply("fly.unavailable"));
        flyTimeout = cp(main.noticeTimeoutPosition, s.apply("fly.timeout"), "time");
        // ↑ その気になれば事前にapplyしておける

        payment = new PaymentMessage(main.economy != null
            ? Objects.requireNonNull(config.getConfigurationSection("payment"))
            : new MemoryConfiguration()
            , main
        );
        help = new HelpMessage(Objects.requireNonNull(config.getConfigurationSection("help")));
    }

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

    private static ComponentParser p(String value) {
        return ComponentParser.parse(PREFIX + value);
    }

    private static <T extends CommandSender> BiConsumer<T, ComponentVariable> c(String value) {
        if (value == null || value.isEmpty()) {
            return (p, v) -> {};
        }

        var c = p(value);
        return (p, v) -> c.apply(v).send(p);
    }

    private static BiConsumer<Player, Supplier<String>> cp(MainConfig.NoticePosition pos, String value, String key) {
        if (value == null) {
            return (p, v) -> {};
        }
        return switch (pos.position()) {
            case FALSE -> (p, v) -> {};
            case CHAT -> {
                var cc = p(value);
                yield (p, v) -> cc.apply(key, v).send(p);
            }
            case ACTION_BAR -> {
                var ca = ComponentParser.parse(value);
                yield (p, v) -> ca.apply(key, v).actionbar(p);
            }
            case TITLE -> {
                var tt = TemplateParser.parse(value);
                yield (p, v) -> p.sendTitle(tt.apply(key, v), "", pos.fadeIn(), pos.stay(), pos.fadeOut());
            }
            case SUB_TITLE -> {
                var ts = TemplateParser.parse(value);
                yield (p, v) -> p.sendTitle(" " /* 空文字だと表示されないので空白を送る */, ts.apply(key, v),
                    pos.fadeIn(), pos.stay(), pos.fadeOut());
            }
        };
    }

    public final static class PaymentMessage {
        public final BiConsumer<Player, Supplier<String>> persist;
        public final BiConsumer<Player, ComponentVariable> persistP; // 常にプレフィックス持ち
        public final BiConsumer<Player, ComponentVariable> self;
        public final BiConsumer<Player, ComponentVariable> other;
        public final BiConsumer<Player, ComponentVariable> receive;
        public final BiConsumer<Player, ComponentVariable> refund;
        public final BiConsumer<Player, ComponentVariable> refundOther;
        public final BiConsumer<Player, ComponentVariable> insufficient;

        private PaymentMessage(ConfigurationSection config, MainConfig main) {
            UnaryOperator<String> s = k -> config.getString(k, null);
            persist = cp(main.noticePaymentPosition, s.apply("persist"), "price");
            persistP = c(s.apply("persist"));
            self = c(s.apply("self"));
            other = c(s.apply("other"));
            receive = c(s.apply("receive"));
            refund = c(s.apply("refund"));
            refundOther = c(s.apply("refundOther"));
            insufficient = c(s.apply("insufficient"));
        }
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
