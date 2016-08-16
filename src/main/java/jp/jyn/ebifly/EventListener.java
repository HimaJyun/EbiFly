package jp.jyn.ebifly;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

	private final EbiFly ebifly;

	public EventListener(EbiFly ebifly) {
		this.ebifly = ebifly;
		ebifly.getServer().getPluginManager().registerEvents(this, ebifly);
	}

	@EventHandler
	public void quit(PlayerQuitEvent e) {
		ebifly.flyDisable(e.getPlayer());
	}

	@EventHandler
	public void ctw(PlayerChangedWorldEvent e) {
		Player player = e.getPlayer();
		if (player.getAllowFlight() && // 飛行状態で
				player.getGameMode() == GameMode.SURVIVAL && // サバイバルで
				!player.hasPermission("ebifly.worldchange")) { // 世界移動権限を持たなければ
			// 無効化
			ebifly.flyDisable(player);
		}
	}
}
