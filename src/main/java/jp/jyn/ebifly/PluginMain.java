package jp.jyn.ebifly;

import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.ebifly.fly.FlyCommand;
import jp.jyn.ebifly.fly.FlyRepository;
import jp.jyn.ebifly.listener.RestrictListener;
import jp.jyn.ebifly.listener.SafetyListener;
import jp.jyn.ebifly.fly.VaultEconomy;
import jp.jyn.jbukkitlib.config.YamlLoader;
import jp.jyn.jbukkitlib.config.locale.BukkitLocale;
import jp.jyn.jbukkitlib.config.locale.MultiLocale;
import jp.jyn.jbukkitlib.config.locale.SingleLocale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class PluginMain extends JavaPlugin {
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
        // メッセージロード
        var def = YamlConfiguration.loadConfiguration(new InputStreamReader(
            Objects.requireNonNull(this.getResource("locale/en_us.yml"),
                "can't load locale/en_us.yml from jar." +
                    " You using non-modded jar? Please try re-download jar and checking hash." +
                    " (from: https://github.com/HimaJyun/EbiFly/ )"
            ), StandardCharsets.UTF_8)
        );
        BukkitLocale<MessageConfig> message;
        YamlLoader.copyDir(this, "locale");
        if (config.localeEnable) {
            var locale = YamlLoader.findYaml(this, "locale")
                .stream()
                .map(YamlLoader::removeExtension)
                .collect(Collectors.toMap(UnaryOperator.identity(), l -> new MessageConfig(this, def, l, config)));
            message = new MultiLocale<>(config.localeDefault, locale);
        } else {
            message = new SingleLocale<>(config.localeDefault,
                new MessageConfig(this, def, config.localeDefault, config)
            );
        }

        // バージョンチェッカー
        Consumer<CommandSender> checker;
        if (config.versionCheck) {
            var v = new VersionChecker(this);
            checker = v::check;

            // 起動時確認
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

        // スレッド
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.submit(() -> Thread.currentThread().setName("ebifly-timer"));
        destructor.addFirst(executor::shutdown);
        Consumer<Runnable> syncCall = r -> getServer().getScheduler().runTask(this, r);

        // 安全機能
        SafetyListener safety;
        if (config.safetyFall || config.safetyVoid || config.safetyLava > 0) {
            safety = new SafetyListener(this, config, syncCall);
            destructor.addFirst(() -> {
                HandlerList.unregisterAll(safety);
                safety.save(this);
            });
            if (config.safetySave > 0) {
                // Bukkitのタイマーは精度悪い(tick基準なのでTPSでズレまくる)ので、ScheduledExecutorServiceで定期実行する
                // タイマー処理に影響を与えないように実際の処理はBukkitのスレッドを使う
                var f = executor.scheduleAtFixedRate(
                    () -> Bukkit.getScheduler().runTaskAsynchronously(this, () -> safety.save(this)),
                    config.safetySave, config.safetySave, TimeUnit.SECONDS
                );
                destructor.addFirst(() -> f.cancel(false));
            }
        } else {
            safety = null;
        }

        // 経済
        var economy = config.economy == null ? null : new VaultEconomy(config);

        // リポジトリ
        var repository = new FlyRepository(config, message, executor, economy,
            syncCall, safety == null ? p -> {} : safety::add);
        destructor.addFirst(() -> getServer().getOnlinePlayers().forEach(repository::stopRefund)); // 全ユーザー飛行停止
        // ログアウト時の停止
        var quit = repository.new FlyQuitListener(safety == null ? p -> {} : safety::stopCount);
        getServer().getPluginManager().registerEvents(quit, this);
        destructor.addFirst(() -> HandlerList.unregisterAll(quit));

        // API登録(たぶん誰も使わない)
        instance.setInstance(repository);
        destructor.addFirst(() -> instance.setInstance(null));
        getServer().getServicesManager().register(EbiFly.class, instance, this, ServicePriority.Normal);
        destructor.addFirst(() -> getServer().getServicesManager().unregister(instance));

        // 制限機能
        if (config.restrictRespawn || config.restrictWorld || config.restrictGamemode
            || config.restrictLevitation != null || config.restrictWater != null) {
            var listener = new RestrictListener(this, config, repository,
                syncCall, safety == null || config.safetyLevitation ? p -> {} : safety::remove);
            destructor.addFirst(() -> HandlerList.unregisterAll(listener));
        }

        // コマンド
        var command = new FlyCommand(this, config, message, repository, economy, checker, this::reload);
        var fly = Objects.requireNonNull(getServer().getPluginCommand("fly"));
        fly.setExecutor(command);
        fly.setTabCompleter(command);
        destructor.addFirst(() -> {
            var f = Objects.requireNonNull(getServer().getPluginCommand("fly"));
            f.setTabCompleter(this);
            f.setExecutor(this);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 何らかの要因で正しく初期化されてない時用
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(MessageConfig.PREFIX + "EbiFly is not working normally.");
            sender.sendMessage(MessageConfig.PREFIX + "Please contact to administrator.");
            sender.sendMessage(MessageConfig.PREFIX + "If you administrator, try '/fly reload' command.");
        } else {
            reload(sender, s -> s.sendMessage(MessageConfig.PREFIX + "You don't have permission!!"));
        }
        return true;
    }

    private void reload(CommandSender sender, Consumer<CommandSender> permissionError) {
        if (!sender.hasPermission("ebifly.reload")) {
            permissionError.accept(sender);
            return;
        }
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
    }
}
