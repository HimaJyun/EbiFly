package jp.jyn.ebifly.fly;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.EbiFlyPlugin;
import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.jbukkitlib.config.locale.BukkitLocale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public class FlyCommand implements TabExecutor {
    private final EbiFlyPlugin plugin;

    private final MainConfig config;
    private final BukkitLocale<MessageConfig> message;
    private final EbiFly fly;
    private final VaultEconomy economy;
    private final Consumer<CommandSender> checker;

    private final PluginDescriptionFile description;

    public FlyCommand(EbiFlyPlugin plugin, MainConfig config, BukkitLocale<MessageConfig> message,
                      EbiFly fly, VaultEconomy economy,
                      Consumer<CommandSender> checker) {
        this.plugin = plugin;

        this.config = config; // TODO: 値取り出す
        this.message = message;
        this.fly = fly;
        this.economy = economy;
        this.description = plugin.getDescription();
        this.checker = checker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 引数解析
        //fly -> 無限飛行/飛行中なら解除
        //fly <時間> -> クレジット追加して飛行
        //fly <時間> <プレイヤー> -> 指定プレイヤーにクレジット追加
        //fly reload/version/help -> サブコマンド実行

        // fly
        if (args.length == 0) {
            if (sender instanceof Player p) {
                if (fly.isFlying(p)) {
                    stop(p);
                } else {
                    persistFly(p);
                }
            } else {
                sender.sendMessage(MessageConfig.PLAYER_ONLY);
            }
            return true;
        }

        //fly reload/version/help
        Consumer<CommandSender> cmd = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> this::version;
            case "reload" -> this::reload;
            case "help" -> this::help;
            default -> null;
        };
        if (cmd != null) {
            cmd.accept(sender);
            return true;
        }

        //fly [time] [player]
        int time = -1;
        try {
            time = Integer.parseInt(args[0]);
        } catch (NumberFormatException ignore) {
        }
        if (time < 1) {
            message.get(sender).invalidNumber.apply("value", args[0]).send(sender);
            return true;
        }

        Player player;
        if (args.length > 1) {
            player = Bukkit.getPlayer(args[1]);
            if (player == null) {
                message.get(sender).playerNotFound.apply("name", args[1]).send(sender);
                return true;
            }
        } else if (sender instanceof Player p) {
            player = p;
        } else {
            sender.sendMessage(MessageConfig.PLAYER_ONLY);
            return true;
        }

        addCredit(sender, player, time);
        return true;
    }

    private void stop(Player player) {
        var refund = fly.stop(player);
        switch (config.economy.refund) {
            case FALSE:
                return;
            case TRUE:
                var v = refund.values().stream().mapToDouble(Double::doubleValue).sum();
                refund.clear(); // 使いまわし
                refund.put(player, v);
                break;
            case PAYER:
                break;
        }

        for (var entry : refund.entrySet()) {
            economy.deposit(entry.getKey(), entry.getValue());
            var p = entry.getKey().getPlayer();
            if (p != null) {
                p.sendMessage("refund " + entry.getValue());
            }
            // TODO: メッセージ
        }
    }

    private void persistFly(Player player) {
        if (!economy.withdraw(player, config.economy.price)) {
            // TODO: お金足りない
            player.sendMessage("money not enough");
            return;
        }

        fly.addCredit(player, config.economy.price, 1, player);
        if (!fly.persist(player)) {
            // TODO: エラー、普通はないはず。
            player.sendMessage("unknown error");
            return;
        }

        // TODO: メッセージ
        player.sendMessage("start persist fly");
    }

    private void addCredit(CommandSender sender, Player recipient, int minute) {
        Player payer = sender == recipient ? recipient : sender instanceof Player p ? p : null;

        if (payer != null && !economy.withdraw(payer, config.economy.price * minute)) {
            // TODO: 金タリン
            payer.sendMessage("money not enough");
            return;
        }

        if (fly.addCredit(recipient, config.economy.price, minute, payer)) {
            // 飛び始めた
            recipient.sendMessage("start fly");
        }

        // TODO: メッセージ
        if (recipient.equals(sender)) {
            // 自分で払った
            recipient.sendMessage("pay " + config.economy.price * minute);
        } else {
            recipient.sendMessage("receive credit " + minute);
            sender.sendMessage("send credit " + minute);
        }
    }

    private void version(CommandSender sender) {
        if (!sender.hasPermission("ebifly.version")) {
            message.get(sender).permissionError.apply().send(sender);
            return;
        }
        sender.sendMessage(MessageConfig.PREFIX + description.getName() + " - " + description.getVersion());
        sender.sendMessage(MessageConfig.PREFIX + Objects.requireNonNull(description.getDescription()));
        sender.sendMessage(MessageConfig.PREFIX + "Developer: " + String.join(", ", description.getAuthors()));
        sender.sendMessage(MessageConfig.PREFIX + "SourceCode: " + description.getWebsite());
        sender.sendMessage("%sLocale: %s (%s)".formatted(
            MessageConfig.PREFIX,
            message.get(sender).locale,
            sender instanceof Player p ? p.getLocale() : message.get().locale)
        );
        checker.accept(sender);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("ebifly.reload")) {
            message.get(sender).permissionError.apply().send(sender);
            return;
        }
        sender.sendMessage(MessageConfig.PREFIX + "Trying reload...");
        plugin.onDisable(); // 例外は無視
        try {
            plugin.onEnable();
            sender.sendMessage(MessageConfig.PREFIX + "Reload done!");
        } catch (Exception e) {
            e.printStackTrace();
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(e.toString());
            }
            sender.sendMessage(MessageConfig.PREFIX + "Reload error!");
        }
    }

    private void help(CommandSender sender) {
        var m = message.get(sender).help;
        m.fly.apply().send(sender);
        if (sender.hasPermission("ebifly.version")) {
            m.version.apply().send(sender);
        }
        if (sender.hasPermission("ebifly.reload")) {
            m.reload.apply().send(sender);
        }
        m.help.apply().send(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> l = new ArrayList<>(2);
            if ("version".startsWith(args[0]) && sender.hasPermission("ebifly.version")) {
                l.add("version");
            }
            if ("reload".startsWith(args[0]) && sender.hasPermission("ebifly.reload")) {
                l.add("reload");
            }
            return l;
        } else if (args.length == 2 && sender.hasPermission("ebifly.other")) {
            try {
                Integer.parseInt(args[0]);
                return null; // nullを返せばプレイヤー一覧にマッチする
            } catch (NumberFormatException ignore) {
            }
        }
        return Collections.emptyList();
    }
}
