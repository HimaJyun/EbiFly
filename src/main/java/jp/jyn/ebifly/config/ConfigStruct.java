package jp.jyn.ebifly.config;

import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class ConfigStruct {
	/**
	 * 設定
	 */
	private FileConfiguration conf = null;
	/**
	 * カスタムコンフィグクラス
	 */
	private final CustomConfig customconfig;
	/**
	 * 使用されるプラグイン
	 */
	private final Plugin plugin;

	private int flightTime;

	private boolean noticeEnable;
	private int noticeTime;

	private boolean economyEnable;
	private boolean economyRefund;
	private double economyCost;

	private boolean economyTaxEnable;
	private OfflinePlayer economyTaxRemittee;

	/**
	 * 各種設定構造体を初期化します。
	 *
	 * @param plugin
	 *            対象のプラグイン
	 */
	public ConfigStruct(Plugin plugin) {
		// プラグイン
		this.plugin = plugin;
		// カスタムコンフィグクラスをインスタンス化
		customconfig = new CustomConfig(plugin, "config.yml");

		// 読み込み
		loadConfig();
	}

	/**
	 * 設定をロードします。
	 */
	@SuppressWarnings("deprecation")
	public void loadConfig() {
		// デフォルト設定をセーブ
		customconfig.saveDefaultConfig();

		if (conf != null) { // confがnullではない(=リロード)
			// リロードを行う
			customconfig.reloadConfig();
		}

		// 設定を取得
		conf = customconfig.getConfig();
		// ロガーを取得
		Logger log = plugin.getLogger();

		flightTime = conf.getInt("FlightTime");
		if (flightTime < 0) { // 負数の場合
			// 警告を発して10に変更
			log.warning("FlightTime of config.yml can specify only positive value.");
			log.warning("Set the flight time to 10 minutes.");
			flightTime = 10;
		}

		noticeEnable = conf.getBoolean("Notice.Enable");
		if (noticeEnable) { // 通知が有効な場合
			noticeTime = conf.getInt("Notice.Time");
			if (noticeTime < 1) { // 負数の場合
				// 警告を発して30に変更
				log.warning("Notice.Time of config.yml can specify only positive value.");
				log.warning("Set the notice time to 30 seconds.");
				flightTime = 30;
			} else if (noticeTime >= 60) { // 60(1分、飛行時間の最小単位)以上の場合
				// 警告を発して30に変更
				log.warning("Notice.Time of config.yml Please specify a less than 60 seconds.");
				log.warning("Set the notice time to 30 seconds.");
				flightTime = 30;
			}
		} else { // 無効なら0にしておく
			noticeTime = 0;
		}

		economyEnable = conf.getBoolean("Economy.Enable");
		if (economyEnable) { // 経済が有効な場合
			economyRefund = conf.getBoolean("Economy.Refund");
			economyCost = conf.getDouble("Economy.Cost");
			if (economyCost <= 0) { // 無料、もしくは負数の場合
				// 警告を発して100に変更
				log.warning("Economy.Cost of config.yml can specify only positive value.");
				log.warning("Set the cost to 100.");
				economyCost = 100.0;
			}

			economyTaxEnable = conf.getBoolean("Economy.Tax.Enable");
			if (economyTaxEnable) {
				economyTaxRemittee = plugin.getServer().getOfflinePlayer(conf.getString("Economy.Tax.Remittee"));
			}
		} else { // 経済が無効ならfalseにしておく
			economyRefund = false;
			economyTaxEnable = false;
		}
	}

	public int getFlightTime() {
		return flightTime;
	}

	public boolean isNoticeEnable() {
		return noticeEnable;
	}

	public int getNoticeTime() {
		return noticeTime;
	}

	public boolean isEconomyEnable() {
		return economyEnable;
	}

	public boolean isEconomyRefund() {
		return economyRefund;
	}

	public double getEconomyCost() {
		return economyCost;
	}

	public boolean isEconomyTaxEnable() {
		return economyTaxEnable;
	}

	public OfflinePlayer getEconomyTaxRemittee() {
		return economyTaxRemittee;
	}
}
