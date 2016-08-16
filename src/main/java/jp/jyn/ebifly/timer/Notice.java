package jp.jyn.ebifly.timer;

import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.EbiFly.FlightStatus;
import jp.jyn.ebifly.config.ConfigStruct;
import jp.jyn.ebifly.config.MessageStruct;

public class Notice extends BukkitRunnable {

	private final EbiFly ebifly;
	private final Player target;
	private final MessageStruct message;
	private final ConfigStruct conf;

	public Notice(EbiFly ebifly, Player target) {
		this.ebifly = ebifly;
		this.target = target;
		this.message = ebifly.getMessage();
		this.conf = ebifly.getConf();
	}

	@Override
	public void run() {
		if (target.getGameMode() != GameMode.SURVIVAL || // サバイバルモードではないか
				!target.isOnline() || // オンラインではないか
				!target.getAllowFlight()) { // 飛行していなければ
			// 処理中断
			ebifly.getFlightStatus().remove(target);
			return;
		}

		// 状態取得
		Map<Player, FlightStatus> flightStatus = ebifly.getFlightStatus();
		if (!flightStatus.containsKey(target)) { // 含まれていない(飛んでいない
			// return
			return;
		}

		// 通知
		target.sendMessage(message.getFlyLeft().replace("[%time%]", String.valueOf(conf.getNoticeTime())));

		FlightStatus status = flightStatus.get(target);
		// 新しいタイマーを起動
		BukkitRunnable timer = new End(ebifly, target);
		timer.runTaskLater(ebifly, conf.getNoticeTime() * 20);
		// 更新
		status.timer = timer;
	}

}
