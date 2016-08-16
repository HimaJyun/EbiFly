package jp.jyn.ebifly;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import jp.jyn.ebifly.config.ConfigStruct;
import jp.jyn.ebifly.config.MessageStruct;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public class Executer implements CommandExecutor {

	private final ConfigStruct conf;
	private final MessageStruct message;
	private final Economy vaultEcon;
	private final EbiFly ebifly;

	public Executer(EbiFly ebifly) {
		this.ebifly = ebifly;
		conf = ebifly.getConf();
		message = ebifly.getMessage();
		vaultEcon = ebifly.getVaultEcon();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		// プレイヤーか否か
		boolean isPlayer = (sender instanceof Player);
		// 飛行時間
		int flightTime = conf.getFlightTime();
		// 対象のプレイヤー
		Player target = null;
		// 権限フラグ
		boolean hasPermission = false;
		// ターゲット指定フラグ
		boolean isOther = false;

		switch (args.length) {
		case 1: // 1個あり
			// 飛行時間を取得
			flightTime = castInt(args[0]);
			// breakしない(スルーして次へ)
		case 0: // 引数なし
			if (!isPlayer) { // コンソールやん！！使い方間違ってるで！！
				sender.sendMessage(MessageStruct.PREFIX_FAIL + ChatColor.RED + "Usage: /fly <Time> <Player>");
				return true;
			}
			// 権限チェック
			hasPermission = sender.hasPermission("ebifly.fly");
			// ターゲット取得
			target = (Player) sender;
			break;
		default: // 2個以上あり
			// フラグ切り替え
			isOther = true;
			// 権限チェック
			hasPermission = sender.hasPermission("ebifly.other");
			// 飛行時間とターゲットを取得
			flightTime = castInt(args[0]);
			target = Bukkit.getPlayerExact(args[1]);
			break;
		}

		// 各種入力値チェーック！！
		if (!hasPermission) { // 権限不足
			return a(sender, message.getDontHavePermission());
		}
		if (target == null) { // ターゲットがnullい
			return a(sender, message.getNotFound());
		}
		if (!target.isOnline()) { // ターゲットがオフライン
			return a(sender, message.getNotOnline().replace("[%player%]", target.getName()));
		}
		if (target.getGameMode() != GameMode.SURVIVAL) { // サバイバルじゃない(アドベンチャーモード?ﾅﾆｿﾚ?)
			return a(sender, message.getNotSurvival().replace("[%player%]", target.getName()));
		}

		if (target.getAllowFlight()) { // 飛行中なら撃ち落とす
			ebifly.flyDisable(target, false, true);
			// 無効になりましたメッセ
			if (isOther) { // ユーザ指定ならターゲットにもメッセージを
				target.sendMessage(message.getFlyDisable());
			}
			return a(sender, message.getFlyDisable());
		}

		if (flightTime < 0) { // 飛行時間が負数
			return a(sender, message.getInvalidValue());
		}

		if (conf.getFlightTime() != 0 && flightTime == 0) { // 無限時間飛行
			if (!sender.hasPermission("ebifly.infinity")) { // 権限不足
				return a(sender, message.getDontHavePermission());
			}
		} else if (conf.isEconomyEnable()) { // 経済が有効なら
			double cost = conf.getEconomyCost() * flightTime;
			if (!economyExec(sender, cost, isPlayer)) { // 経済処理実行
				return a(sender, message.getEconomyNotEnough());
			}
		}

		ebifly.flyEnable(target, flightTime);
		// 飛行時間を取得
		String time = flightTime == 0 ? message.getFlyInfinity() : String.valueOf(flightTime);
		time = message.getFlyEnable().replace("[%time%]", time);
		target.sendMessage(time);
		if (isOther) { // ユーザ指定ならそいつにもメッセージを
			sender.sendMessage(time);
		}

		// 常時true(エラーはこっちで出すからBukkitは黙れ)
		return true;
	}

	/**
	 * sendMessageのラッパー関数、手抜き用
	 * @param s メッセージの送信先
	 * @param m メッセージの内容
	 * @return 常にtrue
	 */
	private boolean a(CommandSender s, String m) {
		s.sendMessage(m);
		return true;
	}

	/**
	 * 文字列をintに変換します。
	 * @param num 変換する文字列
	 * @return 変換後の値、不正なフォーマットの場合は-1
	 */
	private int castInt(String num) {
		int result = -1;
		try {
			result = Integer.parseInt(num);
		} catch (NumberFormatException ignore) { // 無視(最初に-1が入ってるので)
		}
		return result;
	}

	private boolean economyExec(CommandSender sender, double cost, boolean isPlayer) {
		if (sender.hasPermission("ebifly.free") || !isPlayer) { // 無料権限持ちか、コンソールならtrue
			return true;
		}

		OfflinePlayer payer = (OfflinePlayer) sender;
		if (!vaultEcon.hasAccount(payer) || // アカウントがない、もしくは
				!vaultEcon.has(payer, cost)) { // 所持金が足りない
			return false;
		}

		// 先に経済アカウントに送金(取るだけ取ってコケたら厄介なので)
		EconomyResponse r = null;
		if (conf.isEconomyTaxEnable()) { // 送金が有効なら
			// 送金する
			r = vaultEcon.depositPlayer(conf.getEconomyTaxRemittee(), cost);
			if (!r.transactionSuccess()) { // 処理でコケたっぽい
				sender.sendMessage(ChatColor.RED + "An unknown error occurred.");
				ebifly.getLogger().warning("An error occured: " + r.errorMessage);
				return false;
			}
		}

		// 出金
		r = vaultEcon.withdrawPlayer(payer, cost);
		if (r.transactionSuccess()) { // 出金出来た
			// メッセージ送信
			sender.sendMessage(message.getEconomyPayment().replace("[%price%]", vaultEcon.format(cost)));
		} else { // 処理でコケた
			sender.sendMessage(ChatColor.RED + "An unknown error occurred.");
			ebifly.getLogger().warning("An error occured: " + r.errorMessage);
			return false;
		}
		return true;
	}

}
