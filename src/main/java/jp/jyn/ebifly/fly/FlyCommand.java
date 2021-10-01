package jp.jyn.ebifly.fly;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.PluginMain;
import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.jbukkitlib.config.locale.BukkitLocale;
import jp.jyn.jbukkitlib.config.parser.component.ComponentVariable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FlyCommand implements TabExecutor {
    private final BukkitLocale<MessageConfig> message;
    private final EbiFly fly;
    private final VaultEconomy economy;

    private final Consumer<CommandSender> checker;
    private final BiConsumer<CommandSender, Consumer<CommandSender>> reload;

    private final PluginDescriptionFile description;

    private final double price;
    private final MainConfig.EconomyConfig.RefundType refundType;

    private final Consumer<Location> noticeEnable;
    private final Consumer<Location> noticeDisable;
    private final Consumer<Location> noticeEnablePaid;
    private final Consumer<Location> noticePayment;

    public FlyCommand(PluginMain plugin, MainConfig config, BukkitLocale<MessageConfig> message,
                      EbiFly fly, VaultEconomy economy,
                      Consumer<CommandSender> checker, BiConsumer<CommandSender, Consumer<CommandSender>> reload) {
        this.message = message;
        this.fly = fly;
        this.economy = economy;

        this.checker = checker;
        this.reload = reload;

        this.description = plugin.getDescription();

        if (isEconomyEnable()) {
            this.price = config.economy.price;
            this.refundType = config.economy.refund;

            this.noticeEnablePaid = config.noticeEnable.merge(config.noticePayment);
        } else {
            this.price = 0;
            this.refundType = null;

            this.noticeEnablePaid = config.noticeEnable.merge();
        }

        noticeEnable = config.noticeEnable.merge();
        noticeDisable = config.noticeDisable.merge();
        noticePayment = config.noticePayment.merge();
    }

    private boolean isEconomyEnable() {
        return economy != null;
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
            case "reload" -> s1 -> reload.accept(s1, s2 -> message.get(s2).permissionError.apply().send(s2));
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
        message.get(player).flyDisable.apply().send(player);
        noticeDisable.accept(player.getEyeLocation());
        if (!isEconomyEnable() || refundType == MainConfig.EconomyConfig.RefundType.FALSE || refund.isEmpty()) {
            return;
        }


        if (refundType == MainConfig.EconomyConfig.RefundType.TRUE) {
            refund = Map.of(player, refund.values().stream().mapToDouble(Double::doubleValue).sum());
        }

        var v = ComponentVariable.init();
        for (var entry : refund.entrySet()) {
            economy.deposit(entry.getKey(), entry.getValue());
            var p = entry.getKey().getPlayer();
            if (p != null) {
                v.put("player", p::getName);
                v.put("price", () -> economy.format(entry.getValue()));

                var l = message.get(p).payment;
                (p.equals(player) ? l.refund : l.refundOther).accept(p, v);
            }
        }
    }

    private void persistFly(Player player) {
        var l = message.get(player);
        if (!player.hasPermission("ebifly.fly")) {
            l.permissionError.apply().send(player);
            return;
        }
        switch (player.getGameMode()) { // クリエでは飛ばさない
            case CREATIVE, SPECTATOR -> {
                l.flyCreative.apply().send(player);
                return;
            }
        }

        double price;
        OfflinePlayer payer;
        Consumer<Location> notice;
        if (isEconomyEnable() && !player.hasPermission("ebifly.free")) {
            var v = ComponentVariable.init().put("price", () -> economy.format(this.price));
            if (!economy.withdraw(player, this.price)) {
                l.payment.insufficient.accept(player, v);
                return;
            }

            l.payment.persistP.accept(player, v);
            price = this.price;
            payer = player;
            notice = noticeEnablePaid;
        } else {
            price = 0;
            payer = null;
            notice = noticeEnable;
        }

        if (fly.addCredit(player, price, 1, payer, true)) {
            notice.accept(player.getEyeLocation());
            l.flyEnable.apply().send(player);
        }
    }

    private void addCredit(CommandSender sender, Player recipient, int minute) {
        boolean self = sender.equals(recipient);
        if (!sender.hasPermission(self ? "ebifly.fly" : "ebifly.other")) {
            message.get(sender).permissionError.apply().send(sender);
            return;
        }
        switch (recipient.getGameMode()) {
            case CREATIVE, SPECTATOR -> {
                message.get(sender).flyCreative.apply().send(sender);
                return;
            }
        }

        Player payer = sender instanceof Player p ? p : null;
        var p = price;
        if (isEconomyEnable() && payer != null && !payer.hasPermission("ebifly.free")) {
            var l = message.get(payer).payment;
            var v = ComponentVariable.init().put("price", economy.format(p * minute));
            if (!economy.withdraw(payer, p * minute)) {
                l.insufficient.accept(payer, v);
                return;
            }

            v.put("time", minute);
            if (self) {
                l.self.accept(payer, v);
            } else {
                v.put("player", recipient.getName());
                l.other.accept(payer, v);
                v.put("player", payer.getName());
                l.receive.accept(recipient, v);
            }
        } else {
            payer = null;
            p = 0.0d;

            var v = ComponentVariable.init().put("time", minute);
            v.put("player", recipient.getName());
            message.get(sender).flySend.apply(v).send(sender);
            v.put("player", sender.getName()); // Consoleの時はCONSOLEになる
            message.get(recipient).flyReceive.apply(v).send(recipient);
        }

        // 経済無効、飛び始め -> 自分にenable
        // 自腹、飛び始め -> 自分にenable paid
        // 奢り、飛び始め -> 自分にenable、支払人にpaid
        // 飛行中 -> 支払人にpaid
        if (fly.addCredit(recipient, p, minute, payer)) {
            // 飛び始めた
            message.get(recipient).flyEnable.apply().send(recipient);
            if (payer == null) {
                noticeEnable.accept(recipient.getEyeLocation());
            } else if (self) {
                noticeEnablePaid.accept(recipient.getEyeLocation());
            } else {
                noticeEnable.accept(recipient.getEyeLocation());
                noticePayment.accept(payer.getEyeLocation());
            }
        } else {
            if (payer != null) {
                noticePayment.accept(payer.getEyeLocation());
            }
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
