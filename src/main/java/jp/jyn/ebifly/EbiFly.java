package jp.jyn.ebifly;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import jp.jyn.ebifly.config.ConfigStruct;
import jp.jyn.ebifly.config.MessageStruct;
import jp.jyn.ebifly.timer.End;
import jp.jyn.ebifly.timer.Notice;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public class EbiFly extends JavaPlugin {

	private ConfigStruct conf = null;
	private MessageStruct message = null;
	private Economy vaultEcon = null;

	private Map<Player, FlightStatus> flightStatus = new HashMap<>();

	public void onEnable() {
		if (conf == null) { // confがnullなら
			// 初期化
			conf = new ConfigStruct(this);
		} else { // 違えば
			// リロード
			conf.loadConfig();
		}

		if (message == null) { // messageがnullなら
			// 初期化
			message = new MessageStruct(this);
		} else { // 違えば
			// リロード
			message.loadConfig();
		}

		if (conf.isEconomyEnable()) { // 経済を利用するなら
			vaultEcon = getEconomy();
			if (vaultEcon == null) { // コケたら死ぬ
				getLogger().warning("Unknown error in Vault.");
				getLogger().warning("Make sure the Vault and the economy has been introduced correctly.");
				getServer().getPluginManager().disablePlugin(this);
			}
		}

		// TODO:イベントリスナー登録

		// コマンドを登録
		getCommand("fly").setExecutor(new Executer(this));
	}

	@Override
	public void onDisable() {
		// イベントの登録解除
		HandlerList.unregisterAll((Plugin) this);

		// コマンドの登録解除?
		getCommand("fly").setExecutor(this);
	}

	/**
	 * 落ちろ！！落ちたな
	 * @param target 停止するプレイヤー
	 */
	public void flyDisable(Player target) {
		flyDisable(target, false, false);
	}

	/**
	 * 落ちろ！！落ちたな
	 * @param target 停止するプレイヤー
	 * @param isEnd  時間切れによる終了ならtrue
	 * @param isSendMessage 返金時にそのことを通知するならtrue
	 */
	public void flyDisable(Player target, boolean isEnd, boolean isSendMessage) {
		if (!flightStatus.containsKey(target)) { // 飛んでいなければ
			// return
			return;
		}
		// 無効化
		target.setFlying(false);
		target.setAllowFlight(false);

		// 状態取得
		FlightStatus status = flightStatus.get(target);
		if (!isEnd) { // 時間切れではない(途中終了)なら
			if (status.timer != null) {
				// タイマー止める
				status.timer.cancel();
			}

			// 返金処理
			if (conf.isEconomyRefund() && status.flightTime != 0 && !target.hasPermission("ebifly.free")) {
				// 経過秒取得
				double elapse = (System.currentTimeMillis() - status.startTime) / 1000;
				// 返金額算出
				double amount = (status.flightTime - (elapse / 60)) * conf.getEconomyCost();

				EconomyResponse r = vaultEcon.depositPlayer(target, amount);
				if (!r.transactionSuccess()) {
					getLogger().warning("An error occured: " + r.errorMessage);
				}
				if (isSendMessage) {
					double timeLeft = (status.flightTime * 60) - elapse;
					int minutes = (int) (timeLeft / 60);
					int seconds = (int) (timeLeft % 60);
					target.sendMessage(message.getEconomyRefund()
							.replace("[%price%]", vaultEcon.format(amount))
							.replace("[%minutes%]", String.valueOf(minutes))
							.replace("[%second%]", String.valueOf(seconds)));
				}
				if (conf.isEconomyTaxEnable() && vaultEcon.has(conf.getEconomyTaxRemittee(), amount)) {
					r = vaultEcon.withdrawPlayer(conf.getEconomyTaxRemittee(), amount);
					if (!r.transactionSuccess()) {
						getLogger().warning("An error occured: " + r.errorMessage);

					}
				}
			}
		}

		// 削除
		flightStatus.remove(target);
	}

	public void flyEnable(Player target, int flightTime) {
		if (flightStatus.containsKey(target)) { // 飛んでいれば
			// return
			return;
		}
		// 飛行許可
		target.setAllowFlight(true);
		target.setFlySpeed(0.1F);

		BukkitRunnable timer = null;
		if (flightTime != 0) {
			// タイマー起動
			timer = conf.isNoticeEnable() ? new Notice(this, target) : new End(this, target);
			timer.runTaskLater(this, (((flightTime * 60) - conf.getNoticeTime()) * 20));
		}
		// 状態更新
		flightStatus.put(target, new FlightStatus(System.currentTimeMillis(), flightTime, timer));
	}

	/**
	 * VaultのEconomyを取得します。
	 * @return 取得したEconomyのインスタンス、取得できなかった場合はnull
	 */
	private Economy getEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return null;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return null;
		}
		return rsp.getProvider();
	}

	public ConfigStruct getConf() {
		return conf;
	}

	public MessageStruct getMessage() {
		return message;
	}

	public Economy getVaultEcon() {
		return vaultEcon;
	}

	public Map<Player, FlightStatus> getFlightStatus() {
		return flightStatus;
	}

	/**
	 * 飛行状態記録用構造体
	 * @author HimaJyun
	 */
	public class FlightStatus {
		/**
		 * 飛び始めた時間
		 */
		public final long startTime;
		/**
		 * 飛行する時間
		 */
		public final int flightTime;
		/**
		 * タイマースレッド
		 */
		public BukkitRunnable timer;

		public FlightStatus(long startTime, int flightTime, BukkitRunnable timer) {
			this.startTime = startTime;
			this.flightTime = flightTime;
			this.timer = timer;
		}
	}
}
