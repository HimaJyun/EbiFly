package jp.jyn.ebifly.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class CustomConfig {

	/* 　　　＿＿＿＿
	 * 　　r勺z勺z勺ｭ＼
	 * 　〈/⌒⌒⌒＼乙 ヽ
	 * 　//　 |　|　ヽ〉|
	 * ／｜ /ﾚ| Ｎ∧ |＼|
	 * ７ ﾚｲ=ｭヽ|r=ヽ|＿＞
	 * `ﾚ|ﾊ|Oｿ　 ﾋOｿＶ|Ｎ　＜ｵｼﾞｮｳｻﾏｰ
	 * 　(人ﾞ `＿　ﾞ(ｿ从
	 * 　(ｿﾚ＞――＜(ｿﾉ
	 * 　(ｿ｜ ﾚ|/L/ (ｿ
	 * ［>ヘL/ 只 L[>O<]
	 * （⌒O｜んz>/ /⌒}
	 * ⊂ニ⊃L　 / ∩＜
	 * / /＼/＼[(⌒)|二フ
	 * )ﾉ　/　　 ￣∪ﾀ＼
	 * 　 /ﾋ辷辷辷辷ﾀ　 >
	 * 　 ＼＿＿＿＿＿／
	 * 　　 |　/ |　/
	 */

	private FileConfiguration config = null;
	private final File configFile;
	private final String file;
	private final Plugin plugin;

	/**
	 * config.ymlを設定として読み書きするカスタムコンフィグクラスをインスタンス化します。
	 *
	 * @param plugin
	 *            ロード対象のプラグイン
	 */
	CustomConfig(Plugin plugin) {
		this(plugin, "config.yml");
	}

	/**
	 * 指定したファイル名で設定を読み書きするカスタムコンフィグクラスをインスタンス化します。
	 *
	 * @param plugin
	 *            ロード対象のプラグイン
	 * @param fileName
	 *            読み込みファイル名
	 */
	CustomConfig(Plugin plugin, String fileName) {
		this.plugin = plugin;
		this.file = fileName;
		configFile = new File(plugin.getDataFolder(), file);
	}

	/**
	 * デフォルト設定を保存します。
	 */
	public void saveDefaultConfig() {
		if (!configFile.exists()) {
			plugin.saveResource(file, false);
		}
	}

	/**
	 * 読み込んだFileConfiguretionを提供します。
	 *
	 * @return 読み込んだ設定
	 */
	public FileConfiguration getConfig() {
		if (config == null) {
			reloadConfig();
		}
		return config;
	}

	/**
	 * 設定を保存します。
	 */
	public void saveConfig() {
		if (config == null) {
			return;
		}
		try {
			getConfig().save(configFile);
		} catch (IOException ex) {
			plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
		}
	}

	/**
	 * 設定をリロードします。
	 */
	public void reloadConfig() {
		config = YamlConfiguration.loadConfiguration(configFile);

		final InputStream defConfigStream = plugin.getResource(file);
		if (defConfigStream == null) {
			return;
		}

		config.setDefaults(
				YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
	}

	/**
	 * 色コードを置換します、置換される文字列は以下の通りです。<br>
	 * &0-><span style="color:#000;">Black</span><br>
	 * &1-><span style="color:#00A;">Dark Blue</span><br>
	 * &2-><span style="color:#0A0;">Dark Green</span><br>
	 * &3-><span style="color:#0AA;">Dark Aqua</span><br>
	 * &4-><span style="color:#A00;">Dark Red</span><br>
	 * &5-><span style="color:#A0A;">Purple</span><br>
	 * &6-><span style="color:#FA0;">Gold</span><br>
	 * &7-><span style="color:#AAA;">Gray</span><br>
	 * &8-><span style="color:#555;">Dark Gray</span><br>
	 * &9-><span style="color:#55F;">Blue</span><br>
	 * &a-><span style="color:#5F5;">Green</span><br>
	 * &b-><span style="color:#5FF;">Aqua</span><br>
	 * &c-><span style="color:#F55;">Red</span><br>
	 * &d-><span style="color:#F5F;">Light Purple</span><br>
	 * &e-><span style="color:#FF5;background-color:#000;">Yellow</span><br>
	 * &f-><span style="color:#FFF;background-color:#000;">White</span><br>
	 * &k->Obfuscated<br>
	 * &l-><b>Bold</b><br>
	 * &m-><del>Strikethrough</del><br>
	 * &n-><u>Underline</u><br>
	 * &o-><i>Italic</i><br>
	 * &r->Reset<br>
	 * &&で上記の変換を無効化出来ます。
	 *
	 * @param str
	 *            置換対象の色コード
	 * @return 置換後の色コード、入力がnullならnull
	 */
	public String replaceColor(String str) {
		return str == null ? null
				: ChatColor.translateAlternateColorCodes('&', str).replace(ChatColor.COLOR_CHAR + "&", "&");
	}
}
