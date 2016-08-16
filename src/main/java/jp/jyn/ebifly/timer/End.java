package jp.jyn.ebifly.timer;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.config.MessageStruct;

public class End extends BukkitRunnable {

	private final EbiFly ebifly;
	private final Player target;
	private final MessageStruct message;

	public End(EbiFly ebifly, Player target) {
		this.ebifly = ebifly;
		this.target = target;
		this.message = ebifly.getMessage();
	}

	@Override
	public void run() {
		if (target.getGameMode() == GameMode.SURVIVAL && // サバイバルモードで
				target.isOnline() && // オンラインかつ
				target.getAllowFlight()) { // 飛行許可状態なら
			// 無効化
			ebifly.flyDisable(target, true, false);
			target.sendMessage(message.getFlyDisable());
		}
	}

}
