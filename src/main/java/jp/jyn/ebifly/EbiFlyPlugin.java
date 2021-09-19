package jp.jyn.ebifly;

import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.ebifly.fly.FlyCommand;
import jp.jyn.ebifly.fly.FlyRepository;
import jp.jyn.ebifly.fly.VaultEconomy;
import jp.jyn.jbukkitlib.config.YamlLoader;
import jp.jyn.jbukkitlib.config.locale.BukkitLocale;
import jp.jyn.jbukkitlib.config.locale.MultiLocale;
import jp.jyn.jbukkitlib.config.locale.SingleLocale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class EbiFlyPlugin extends JavaPlugin {
    private final FlyDelegate instance = new FlyDelegate();

    // Stack(LIFO)
    private final Deque<Runnable> destructor = new ArrayDeque<>();

    @Override
    synchronized public void onEnable() {
        if (!destructor.isEmpty()) { // 競合
            return;
        }

        // 設定ロード
        var config = new MainConfig(this);
        BukkitLocale<MessageConfig> message;
        YamlLoader.copyDir(this, "locale");
        if (config.localeEnable) {
            message = new MultiLocale<>(config.localeDefault, YamlLoader.findYaml(this, "locale")
                .stream()
                .map(YamlLoader::removeExtension)
                .collect(Collectors.toMap(UnaryOperator.identity(), l -> new MessageConfig(this, l))));
        } else {
            message = new SingleLocale<>(config.localeDefault, new MessageConfig(this, config.localeDefault));
        }

        // バージョンチェッカー
        Consumer<CommandSender> checker;
        if (config.versionCheck) {
            var v = new VersionChecker(this);
            checker = v::check;

            // 定期確認
            var task = getServer().getScheduler().runTaskLaterAsynchronously(
                this,
                () -> v.check(Bukkit.getConsoleSender()),
                20 * 30
            );
            destructor.addFirst(task::cancel);

            // ログイン時チェック
            var e = v.new LoginChecker();
            getServer().getPluginManager().registerEvents(e, this);
            destructor.addFirst(() -> HandlerList.unregisterAll(e));
        } else {
            checker = ignore -> {};
        }

        // リポジトリ
        var economy = config.economy == null ? null : new VaultEconomy(config);
        var repository = new FlyRepository(config, economy);
        instance.setInstance(repository);
        destructor.addFirst(() -> instance.setInstance(null));

        // TODO: イベント

        // コマンド
        var command = new FlyCommand(this, config, message, repository, economy, checker);
        var fly = Objects.requireNonNull(getServer().getPluginCommand("fly"));
        fly.setExecutor(command);
        fly.setTabCompleter(command);
        destructor.addFirst(() -> {
            var f = Objects.requireNonNull(getServer().getPluginCommand("fly"));
            fly.setTabCompleter(this);
            fly.setExecutor(this);
        });
    }

    @Override
    synchronized public void onDisable() {
        while (!destructor.isEmpty()) {
            try {
                destructor.removeFirst().run();
            } catch (Exception e) {
                e.printStackTrace(); // 例外が出ても全ての処理を続行すべき。
            }
        }
    }

    public EbiFly getEbiFly() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 何らかの要因で正しく初期化されてない時用
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(MessageConfig.PREFIX + "EbiFly is not working normally.");
            sender.sendMessage(MessageConfig.PREFIX + "Please contact to administrator.");
            sender.sendMessage(MessageConfig.PREFIX + "If you administrator, try '/fly reload' command.");
        } else if (sender.hasPermission("ebifly.reload")) {
            sender.sendMessage(MessageConfig.PREFIX + "Trying reload...");
            onDisable(); // 例外は無視
            try {
                onEnable();
                sender.sendMessage(MessageConfig.PREFIX + "Reload done!");
            } catch (Exception e) {
                e.printStackTrace();
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(e.toString());
                }
                sender.sendMessage(MessageConfig.PREFIX + "Reload error!");
            }
        } else {
            sender.sendMessage(MessageConfig.PREFIX + "You don't have permission!!");
        }
        return true;
    }
}
