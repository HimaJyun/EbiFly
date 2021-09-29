package jp.jyn.ebifly.fly;

import jp.jyn.ebifly.EbiFly;
import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.config.MessageConfig;
import jp.jyn.jbukkitlib.config.locale.BukkitLocale;
import jp.jyn.jbukkitlib.config.parser.component.ComponentVariable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public class FlyRepository implements EbiFly {
    private final Map<UUID, FlightStatus> flying = new ConcurrentHashMap<>();

    private final BukkitLocale<MessageConfig> message;
    private final ScheduledExecutorService executor;
    private final VaultEconomy economy;
    private final Consumer<Runnable> syncCall;

    private final double price;
    private final int notify;
    private final Consumer<Location> noticeDisable;
    private final Consumer<Location> noticeTimeout;
    private final Consumer<Location> noticePayment;

    public FlyRepository(MainConfig config, BukkitLocale<MessageConfig> message,
                         ScheduledExecutorService executor, VaultEconomy economy,
                         Consumer<Runnable> syncCall) {
        this.message = message;
        this.executor = executor;
        this.economy = economy;
        this.syncCall = syncCall;

        this.price = economy == null ? 0 : config.economy.price;
        this.notify = config.noticeTimeoutSecond;

        this.noticeDisable = config.noticeDisable.merge();
        this.noticeTimeout = config.noticeTimeout.merge();
        this.noticePayment = economy == null ? ignore -> {} : config.noticePayment.merge();
    }

    private boolean isNotifyEnabled() {
        return notify > 0;
    }

    private FlightStatus remove(Player player, boolean notice) {
        var r = flying.remove(player.getUniqueId());
        if (r != null) {
            r.cancelTimer();
        }

        // 引数を使わない事でラムダのインスタンスが無駄に作られないように
        final Consumer<Player> f = p -> {
            switch (p.getGameMode()) {
                case SURVIVAL, ADVENTURE -> p.setAllowFlight(false);
            }
        };
        final Consumer<Player> m = notice ? p -> { // IFスリカエ
            message.get(p).flyDisable.apply().send(p);
            noticeDisable.accept(p.getEyeLocation());
        } : p -> {};
        // 無駄な最適化かも

        // スレッド切り替え
        if (Bukkit.isPrimaryThread()) {
            // Player渡して呼ぶだけで済む、ペナルティはほぼゼロのはず
            f.accept(player);
            m.accept(player);
        } else {
            syncCall.accept(() -> {
                f.accept(player);
                m.accept(player);
            });
        }

        return r;
    }

    private void persistTimer(Player player) {
        var v = flying.get(player.getUniqueId());
        if (v == null) {
            // 飛行してないのにタイマーだけ動いてる、スレッド競合？
            throw new IllegalStateException("Player doesn't flying, Thread conflict?"); // 絶妙なタイミングで飛行停止すると正常でも出るかも
        }

        // 周期タイマーのはずなので特に気にせずクレジットを減らす
        v.lastConsume.set(System.currentTimeMillis());
        var cs = v.credit; // TODO: このpollして処理して戻すって処理は定型なので共通化できるのでは？
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
            message.get(player).payment.persist.accept(player, ComponentVariable.init().put("price",
                economy.format(price)));
            noticePayment.accept(player.getEyeLocation()); // TODO: 非同期呼び出し出来ないかも
            cs.addLast(new Credit(price, 1, player));
            return;
        }

        // お金ないならｵﾁﾛｰ!
        message.get(player).payment.insufficient.accept(player, ComponentVariable.init().put("price",
            economy.format(price)));
        remove(player, true);
    }

    private void stopTimer(Player player, boolean notify) {
        var v = flying.get(player.getUniqueId());
        if (v == null) {
            throw new IllegalStateException("Player doesn't flying, Thread conflict?");
        }

        // 後の簡略化のためにクレジットを消費させる
        long time, use;
        while (true) { // TODO: これを関数で切り出す。-1ならクレジット切れ、+nなら未徴収分のミリ秒(それか時間そのもの？)。みたいな？
            long old = v.lastConsume.get();
            time = System.currentTimeMillis() - old; // 経過時間
            use = TimeUnit.MILLISECONDS.toMinutes(time);
            if (v.lastConsume.compareAndSet(old, old + TimeUnit.MINUTES.toMillis(use))) { // 消費した分だけ進めておく
                break;
            }
        }
        //TimeUnit.MILLISECONDS.toMinutes(time); TODO: TimeUnitを使う
        useCredit(v.credit, (int) use);

        // クレジット残数チェック
        int c = v.credit.stream().mapToInt(m -> Math.max(m.minute().get(), 0)).sum();

        // 後からクレジットが追加されてる
        if (c > 1) {
            // 警告タイマーの入れ直し
            // TODO: CASの段階でメモリに取っておく方が良いか？
            // TODO: これやってること再スケジュールと同じ
            long delay = TimeUnit.MINUTES.toMillis(c) + v.lastConsume.get(); // (残クレジット*60*1000)+最終徴収時刻 == 終了予定時刻
            delay -= System.currentTimeMillis(); // 終了予定時刻 - 現時刻 == 終了予定までの時間
            delay -= this.notify;
            v.setTimer(executor.schedule(() -> stopTimer(player, isNotifyEnabled()), delay, TimeUnit.MILLISECONDS));
        } else if (notify) { // 停止タイマー入れ直す
            v.setTimer(executor.schedule(() -> stopTimer(player, false), this.notify, TimeUnit.SECONDS));
            noticeTimeout.accept(player.getEyeLocation());
            message.get(player).flyTimeout.accept(player, ComponentVariable.init().put("time", this.notify));
            // ↑ その気になれば事前にapplyしておける
        } else { // 停止
            // ズレが大きければコッソリ待つ的な処理があった方が良いかも？ ScheduledExecutorServiceの精度次第
            remove(player, true);
        }
    }

    @Override
    public boolean isFlying(Player player) {
        return flying.containsKey(player.getUniqueId());
    }

    @Override
    public boolean persist(Player player) {
        var v = flying.get(player.getUniqueId());
        if (v == null || v.credit.isEmpty()) {
            // 飛んでないか、なぜかクレジットがない
            return false;
        }

        // タイマー変更
        v.reSchedule(delay -> executor.scheduleAtFixedRate(() -> persistTimer(player),
            delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS));
        v.persist = true;
        return true;
    }

    @Override
    public boolean addCredit(Player player, double price, int minute, OfflinePlayer payer) {
        var v = flying.get(player.getUniqueId());
        var r = v == null;
        if (r) {
            v = flying.computeIfAbsent(player.getUniqueId(), u -> new FlightStatus());
        }
        v.credit.addLast(new Credit(price, minute, payer));
        player.setAllowFlight(true);

        // 停止タイマー入れ直し
        v.reSchedule(delay -> executor.schedule(
            () -> stopTimer(player, isNotifyEnabled()),
            delay + TimeUnit.MINUTES.toMillis(minute) - TimeUnit.SECONDS.toMillis(notify),
            TimeUnit.MILLISECONDS
        ));
        return r;
    }

    // 飛んでなければ-1、十分なら0、不足していたら足りない数
    @Override
    public int useCredit(Player player, int minute) {
        var v = flying.get(player.getUniqueId());
        if (v == null) {
            return -1;
        }

        v.lastConsume.set(System.currentTimeMillis());
        return useCredit(v.credit, minute);
    }

    @Override
    public Map<OfflinePlayer, Double> stop(Player player) {
        var v = remove(player, false);
        if (v == null) {
            return Collections.emptyMap();
        }

        long time = v.getElapsed(); // 経過時間
        long min = TimeUnit.MILLISECONDS.toMinutes(time); // 消費クレジット
        long sec = time % (60 * 1000); // 消費秒(ミリ単位) TODO: これ、moduloを先にやってからなら減算1発で済むのでは？

        // クレジット消費
        var d = v.credit;
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
                return Collections.emptyMap(); // TODO: returnして大丈夫？
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

    private static final class FlightStatus {
        private final AtomicLong lastConsume;
        private final Deque<Credit> credit;

        private final Object lock = new Object();
        private Future<?> timer = null;
        private volatile boolean persist = false;

        private FlightStatus(AtomicLong lastConsume, Deque<Credit> credit) {
            this.lastConsume = lastConsume;
            this.credit = credit;
        }

        private FlightStatus() {
            this(new AtomicLong(System.currentTimeMillis()), new ConcurrentLinkedDeque<>());
        }

        private long getElapsed() {
            return System.currentTimeMillis() - lastConsume.get();
            // TODO: millisじゃなくてnanoTimeにした方が良い、経過時間の計算なので(NTPでズレない)
            // t1 < t0ではなくt1 - t0 < 0を使用すべきですが、それは、数値のオーバーフローが発生する可能性があるからです。
        }

        private void cancelTimer() {
            synchronized (lock) {
                if (timer != null) {
                    timer.cancel(false);
                }
            }
        }

        private void setTimer(Future<?> timer) {
            synchronized (lock) {
                if (this.timer != null) {
                    this.timer.cancel(false);
                }
                this.timer = timer;
            }
        }

        private void reSchedule(LongFunction<Future<?>> scheduler) {
            long last, delay;
            do {
                last = lastConsume.get();
                // クレジット全数での飛行時間を取得
                delay = TimeUnit.MINUTES.toMillis(credit.stream().mapToInt(m -> Math.max(m.minute.get(), 0)).sum());
                // 経過時間分引く
                delay -= System.currentTimeMillis() - last;
            } while (last != lastConsume.get()); // Compare And Loop TODO: こんな慎重なカウント処理要る？
            // ScheduledFuture#getDelayを使う方法だとnotifyの時間分ずつズレるのでこうする
            setTimer(scheduler.apply(delay));
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
