package jp.jyn.ebifly.fly;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.config.MainConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FlyRepository implements EbiFly {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, FlightStatus> flying = new ConcurrentHashMap<>(); // TODO: keyはUUIDじゃなくてPlayerで良いのでは

    private final VaultEconomy economy;
    private final double price;
    private final int notify;
    // TODO: 誤差への考慮が必要

    public FlyRepository(MainConfig config, VaultEconomy economy) {
        this.economy = economy;
        this.price = economy == null ? 0 : config.economy.price;
        this.notify = config.noticeTimeoutSecond;

        executor.submit(() -> Thread.currentThread().setName("ebifly-timer"));
    }

    private void persistTimer(Player player) {
        if (!player.isOnline()) {
            return;
        }
        var v = flying.get(player.getUniqueId());
        if (v == null) {
            // 飛行してないのにタイマーだけ動いてる、スレッド競合？
            return;
        }

        // 周期タイマーのはずなので特に気にせずクレジットを減らす
        v.lastConsume().set(System.currentTimeMillis());
        var cs = v.credit(); // TODO: このpollして処理して戻すって処理は定型なので共通化できるのでは？
        Credit credit;
        while ((credit = cs.pollFirst()) != null) {
            var i = credit.minute().decrementAndGet();
            if (i > 0) { // クレジット残ありなら返却して今周の消費は終わり
                cs.addFirst(credit);
                return;
            } else if (i == 0) {
                // 取り出したクレジットが空になったら次の支払いへ
                break;
            }
            // 0を下回る == スレッド間競合で既に0だったのを更に減らしたっぽい
            // 捨ててやり直しが必要
        }

        // クレジットは空になった、まだあるかな？
        while ((credit = cs.pollFirst()) != null) {
            if (credit.minute().get() > 0) {
                // まだあるみたいなので返却して終わり
                cs.addFirst(credit);
                return;
            }
        }

        // どうやらないっぽい -> ｵｶﾈﾀﾞｰ!!
        if (economy.withdraw(player, price)) {
            // 支払い完了、ﾏｲﾄﾞｱﾘｰ
            player.sendMessage("payment for persist " + price);
            cs.addLast(new Credit(price, 1, player));
            return;
        }

        // お金ないならｵﾁﾛｰ!
        player.sendMessage("money not enough for persist");
        flying.remove(player.getUniqueId()); // TODO: この処理切り出す方が良いかもね
        player.setAllowFlight(false);
        // TODO: タイマーキャンセル
    }

    // TODO: int nofityじゃなくてboolean stopとかにして、値はthis.notify <= 0で渡せばいいのでは？ <- それだとここを呼び出す全ての場所でこの式が必要になる
    private void stopTimer(Player player, boolean notify) {
        if (!player.isOnline()) {
            return;
        }
        var v = flying.get(player.getUniqueId());
        if (v == null) {
            return;
        }

        // 後の簡略化のためにクレジットを消費させる
        long time, use;
        while (true) { // TODO: これを関数で切り出す。-1ならクレジット切れ、+nなら未徴収分のミリ秒(それか時間そのもの？)。みたいな？
            long old = v.lastConsume().get();
            time = System.currentTimeMillis() - old; // 経過時間
            use = TimeUnit.MILLISECONDS.toMinutes(time);
            if (v.lastConsume().compareAndSet(old, old + TimeUnit.MINUTES.toMillis(use))) { // 消費した分だけ進めておく
                break;
            }
        }
        //TimeUnit.MILLISECONDS.toMinutes(time); TODO: TimeUnitを使う
        useCredit(v.credit(), (int) use);

        // クレジット残数チェック
        int c = 0;
        for (Credit credit : v.credit()) {
            c += Math.max(credit.minute().get(), 0); // 念のためのマイナス避け
        }

        // 後からクレジットが追加されてる
        if (c > 1) {
            // 警告タイマーの入れ直し
            // TODO: CASの段階でメモリに取っておく方が良いか？
            long delay = TimeUnit.MINUTES.toMillis(c) + v.lastConsume().get(); // (残クレジット*60*1000)+最終徴収時刻 == 終了予定時刻
            delay -= System.currentTimeMillis(); // 終了予定時刻 - 現時刻 == 終了予定までの時間
            delay -= this.notify;
            executor.schedule(() -> stopTimer(player, this.notify > 0), delay, TimeUnit.MILLISECONDS);
        } else if (notify) { // 停止タイマー入れ直す
            player.sendMessage("timeout warning");
            executor.schedule(() -> stopTimer(player, false), this.notify, TimeUnit.MILLISECONDS); // TODO:
            // ズレ補正のために再計算した方が良い？
        } else { // 停止
            player.sendMessage("timeout stop");
            flying.remove(player.getUniqueId());
            player.setAllowFlight(false);
            // TODO: 止める
        }
    }

    @Override
    public boolean isFlying(Player player) {
        return flying.containsKey(player.getUniqueId());
    }

    @Override
    public boolean persist(Player player) {
        var v = flying.computeIfPresent(
            player.getUniqueId(),
            (u, s) -> new FlightStatus(s.lastConsume(), s.credit(), true)
        );

        return v != null && !v.credit().isEmpty();
    }

    @Override
    public boolean addCredit(Player player, double price, int minute, OfflinePlayer payer) {
        var v = flying.get(player.getUniqueId());
        var r = v == null;
        if (r) {
            v = flying.computeIfAbsent(player.getUniqueId(), u -> new FlightStatus());
        }
        v.credit().addLast(new Credit(price, minute, payer));

        // TODO: 停止タイマー
        player.setAllowFlight(true);
        return r;
    }

    // 飛んでなければ-1、十分なら0、不足していたら足りない数
    @Override
    public int useCredit(Player player, int minute) {
        var v = flying.get(player.getUniqueId());
        if (v == null) {
            return -1;
        }

        v.lastConsume().set(System.currentTimeMillis());
        return useCredit(v.credit(), minute);
    }

    @Override
    public Map<OfflinePlayer, Double> stop(Player player) {
        var v = flying.remove(player.getUniqueId());
        if (v == null) {
            return null;
        }
        player.setAllowFlight(false);

        long time = System.currentTimeMillis() - v.lastConsume().get(); // 経過時間
        long min = TimeUnit.MILLISECONDS.toMinutes(time); // 消費クレジット
        long sec = time % (60 * 1000); // 消費秒(ミリ単位) TODO: これ、moduloを先にやってからなら減算1発で済むのでは？

        // クレジット消費
        var d = v.credit();
        useCredit(d, (int) min);
        if (d.isEmpty()) {
            return Collections.emptyMap();
        }

        // 秒割料金
        Credit c;
        int remain;
        while (true) {
            c = d.pollFirst();
            if (c == null) {
                return Collections.emptyMap();
            }
            if (c.payer() == null) {
                continue;
            }
            remain = c.minute().get();
            if (remain > 0) { // 空クレジットでなければ抜ける(==空なら捨ててやり直し)
                break;
            }
        }
        long rs = (60 * 1000) - sec; // 残秒
        // (単価/単位==秒単価)*残秒 == 返金額。除算を後回しにすることで浮動小数の演算誤差を減らすことを意図した
        double refund = (c.price() * rs) / (60d * 1000d);
        refund += c.price() * (remain - 1); // クレジット残ってるならそれも入れとく

        Map<OfflinePlayer, Double> ret = new HashMap<>();
        ret.put(c.payer(), refund);

        // 残クレジットまとめて入れる
        while (!d.isEmpty()) {
            var cc = d.remove();
            if (cc.payer() != null) {
                ret.merge(cc.payer(), cc.price(), Double::sum);
            }
        }

        return ret;
    }

    private int useCredit(Deque<Credit> credits, int minute) {
        Credit credit;
        OUTER:
        while (minute != 0 && (credit = credits.pollFirst()) != null) {
            int old, use, remain;
            do {
                old = credit.minute().get();
                if (old <= 0) { // そもそもが空クレジットなら何をしても無駄
                    continue OUTER;
                }
                use = Math.min(old, minute);
                remain = old - use;
            } while (!credit.minute().compareAndSet(old, remain));

            // 消費して戻す
            minute -= use;
            if (remain > 0) {
                credits.addFirst(credit);
            }
        }
        return minute;
    }

    private static final record FlightStatus(AtomicLong lastConsume, Deque<Credit> credit,
                                             boolean persist) { // TODO: 止めた時用に稼働中タイマーを持つ必要がある
        private FlightStatus() {
            this(new AtomicLong(System.currentTimeMillis()), new ConcurrentLinkedDeque<>(), false);
        }
    }

    private static final record Credit(double price, AtomicInteger minute, /*nullable*/ OfflinePlayer payer) {
        private Credit {
            if (Double.compare(price, 0.0) < 0) throw new IllegalArgumentException("price is negative"); // -0.0も弾く
            if (!Double.isFinite(price)) throw new IllegalArgumentException("price is not finite");
            if (minute == null) throw new IllegalArgumentException("minute is null");
            if (minute.get() <= 0) throw new IllegalArgumentException("minute is negative");
        }

        private Credit(double price, int minute, OfflinePlayer payer) {
            this(price, new AtomicInteger(minute), payer);
        }
    }
}
