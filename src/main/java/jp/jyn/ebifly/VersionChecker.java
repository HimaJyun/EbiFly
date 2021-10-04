package jp.jyn.ebifly;

import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import jp.jyn.jbukkitlib.util.updater.GitHubReleaseChecker;
import jp.jyn.jbukkitlib.util.updater.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@PackagePrivate
class VersionChecker {
    private final static long CHECK_PERIOD = TimeUnit.HOURS.toNanos(12); // 決め打ち

    private final UpdateChecker checker = new GitHubReleaseChecker("HimaJyun", "EbiFly");
    private final AtomicLong lastChecked = new AtomicLong(0);
    private final AtomicReference<String[]> cache = new AtomicReference<>(null);

    private final Plugin plugin;

    @PackagePrivate
    VersionChecker(PluginMain plugin) {
        this.plugin = plugin;
    }

    @PackagePrivate
    void check(CommandSender recipient) {
        if ((System.nanoTime() - lastChecked.get()) < CHECK_PERIOD) {
            var c = cache.get();
            if (c != null) {
                recipient.sendMessage(c);
                return;
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateChecker.LatestVersion latest;
            synchronized (this) {
                latest = checker.callEx();
            }

            var current = Objects.requireNonNull(plugin.getDescription(), "Can't get current version").getVersion();
            if (current.equals(latest.version)) {
                return;
            }

            var n = new String[]{
                "%sNew version available: %s -> %s".formatted(MessageConfig.PREFIX, current, latest.version),
                "%sDownload: %s".formatted(MessageConfig.PREFIX, latest.url)
            };
            cache.set(n);
            lastChecked.set(System.nanoTime());
            recipient.sendMessage(n);
        });
    }

    @PackagePrivate
    final /*static*/ class LoginChecker implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onPlayerJoin(PlayerJoinEvent e) {
            var p = e.getPlayer();
            if (p.hasPermission("ebifly.version")) {
                VersionChecker.this.check(p);
            }
        }
    }
}
