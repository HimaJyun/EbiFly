package jp.jyn.ebifly.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class MessageStruct {

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
	private final Plugin plg;

	// 定数
	public final static String PREFIX_SUCCESS = "[" + ChatColor.GREEN + "EbiFly" + ChatColor.RESET + "] ";
	public final static String PREFIX_FAIL = "[" + ChatColor.RED + "EbiFly" + ChatColor.RESET + "] ";
	public final static String PLAYER_ONRY = PREFIX_FAIL + ChatColor.RED + "This command can only be run by a player.";

	private String dontHavePermission;
	private String invalidValue;

	private String flyDisable;
	private String flyEnable;
	private String flyInfinity;
	private String flyLeft;

	private String notOnline;
	private String notSurvival;
	private String notFound;

	private String economyNotEnough;
	private String economyPayment;
	private String economyRefund;

	/**
	 * 各種設定構造体を初期化します。
	 *
	 * @param plugin
	 *            対象のプラグイン
	 */
	public MessageStruct(Plugin plugin) {
		// プラグイン
		plg = plugin;
		// カスタムコンフィグクラスをインスタンス化
		customconfig = new CustomConfig(plg, "message.yml");

		// 読み込み
		loadConfig();
	}

	/**
	 * 設定をロードします。
	 */
	public void loadConfig() {
		// デフォルト設定をセーブ
		customconfig.saveDefaultConfig();

		if (conf != null) { // confがnullではない(=リロード)
			// リロードを行う
			customconfig.reloadConfig();
		}

		// 設定を取得
		conf = customconfig.getConfig();

		// 取得
		dontHavePermission = PREFIX_FAIL + a("DontHavePermission");
		invalidValue = PREFIX_FAIL + a("InvalidValue");
		flyDisable = PREFIX_FAIL + a("Fly.Disable");
		flyEnable = PREFIX_SUCCESS + a("Fly.Enable");
		flyInfinity = a("Fly.Infinity");
		flyLeft = PREFIX_FAIL + a("Fly.Left");
		notOnline = PREFIX_FAIL + a("Not.Online");
		notSurvival = PREFIX_FAIL + a("Not.Survival");
		notFound = PREFIX_FAIL + a("Not.Found");
		economyNotEnough = PREFIX_FAIL + a("Economy.NotEnough");
		economyPayment = PREFIX_SUCCESS + a("Economy.Payment");
		economyRefund = PREFIX_SUCCESS + a("Economy.Refund");
	}

	/**
	 * 色置換と設定取得のラッパー関数、手抜き用
	 * @param path 設定値のパス
	 * @return 取得、色置換を施した文字列
	 */
	private String a(String path) {
		return customconfig.replaceColor(conf.getString(path));
	}

	public String getDontHavePermission() {
		return dontHavePermission;
	}

	public String getInvalidValue() {
		return invalidValue;
	}

	public String getFlyDisable() {
		return flyDisable;
	}

	public String getFlyEnable() {
		return flyEnable;
	}

	public String getFlyInfinity() {
		return flyInfinity;
	}

	public String getFlyLeft() {
		return flyLeft;
	}

	public String getNotOnline() {
		return notOnline;
	}

	public String getNotSurvival() {
		return notSurvival;
	}

	public String getNotFound() {
		return notFound;
	}

	public String getEconomyNotEnough() {
		return economyNotEnough;
	}

	public String getEconomyPayment() {
		return economyPayment;
	}

	public String getEconomyRefund() {
		return economyRefund;
	}

}
