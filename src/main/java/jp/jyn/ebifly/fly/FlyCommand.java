package jp.jyn.ebifly.fly;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.jbukkitlib.config.locale.BukkitLocale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public class FlyCommand implements TabExecutor {
    private final MainConfig config;
    private final BukkitLocale<MessageConfig> message;

    private final EbiFly fly;
    private final VaultEconomy economy;

    public FlyCommand(MainConfig config, BukkitLocale<MessageConfig> message, EbiFly fly, VaultEconomy economy) {
        this.config = config; // TODO: 値取り出す
        this.message = message;
        this.fly = fly;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = null;
        Integer time = null;

        // 引数解析
        //fly -> 無限飛行/飛行中なら解除
        //fly <時間> -> クレジット追加して飛行
        //fly <時間> <プレイヤー> -> 指定プレイヤーにクレジット追加
        // TODO: reload, version, help
        // TODO: TabComplete -> reload,versionは権限を確認してから出す。helpは隠す。数字が出る？
        switch (args.length) {
            default:
                player = Bukkit.getPlayer(args[1]);
                if (player == null) {
                    message.get(sender).playerNotFound.apply("name", args[1]).send(sender);
                    return true;
                }
                // fallthrough
            case 1:
                try {
                    time = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    message.get(sender).invalidNumber.apply("value", args[0]).send(sender);
                    return true;
                }
                // fallthrough
            case 0:
                if (player == null /*not default*/ && sender instanceof Player p) {
                    player = p;
                } else {
                    sender.sendMessage(MessageConfig.PLAYER_ONLY);
                    return true;
                }
                break;
        }

        if (time == null) {
            //fly
            if (fly.isFlying(player)) {
                stop(player);
            } else {
                persistFly(player);
            }
            return true;
        }

        if (time <= 0) {
            message.get(sender).invalidNumber.apply("value", args[0]);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null; // TODO: 実装
    }
}
