package jp.jyn.ebifly.fly;

import jp.jyn.ebifly.config.MainConfig;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;

public class VaultEconomy {
    private final Economy economy;

    private final DoubleConsumer serverD;
    private final DoubleConsumer serverW;

    public VaultEconomy(MainConfig config) {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            throw new IllegalStateException("Vault not found");
        }

        var rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            throw new IllegalStateException("Vault Economy is not available");
        }

        this.economy = rsp.getProvider();

        var s = getOfflinePlayer(config.economy.server);
        // 処理は共通でIFをすり替える
        if (s == null) {
            serverD = d -> {};
            serverW = d -> {};
        } else {
            serverD = d -> this.economy.depositPlayer(s, d);
            serverW = d -> {
                if (this.economy.has(s, d)) {
                    this.economy.withdrawPlayer(s, d);
                } else if (this.economy.hasAccount(s)) {
                    // あるだけ抜き取る
                    this.economy.withdrawPlayer(s, this.economy.getBalance(s));
                }
            };
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer getOfflinePlayer(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        var m = Pattern.compile(
            "^(\\p{XDigit}{8})-?(\\p{XDigit}{4})-?(\\p{XDigit}{4})-?(\\p{XDigit}{4})-?(\\p{XDigit}{12})$"
        ).matcher(value);

        return m.matches()
            ? Bukkit.getOfflinePlayer(UUID.fromString(m.replaceFirst("$1-$2-$3-$4-$5")))
            : Bukkit.getOfflinePlayer(value);
    }

    public String format(double value) {
        return economy.format(value);
    }

    // TODO: インターフェースすり替え方針ではダメ、都度有効か見ないと
    // TODO: メインスレッドかどうかを見た方が良いかも -> そもそもスレッド使ってない
    public boolean deposit(OfflinePlayer player, double amount) {
        serverW.accept(amount);
        // サーバーの残金がないからと言って特に何か出来るわけじゃない(どっちみち停止を要求されたflyは止める他ない)ので、あろうがなかろうが入金(返金)処理は続行する
        return economy.depositPlayer(player, amount).type == EconomyResponse.ResponseType.SUCCESS;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy.has(player, amount) &&
            economy.withdrawPlayer(player, amount).type == EconomyResponse.ResponseType.SUCCESS) {
            serverD.accept(amount);
            return true;
        }
        return false;
    }
}
