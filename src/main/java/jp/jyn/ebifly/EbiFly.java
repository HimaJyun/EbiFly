package jp.jyn.ebifly;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

public interface EbiFly {
    /**
     * Check fly mode
     *
     * @param player target player
     * @return true if flying, false if not flying.
     */
    boolean isFlying(Player player);

    /**
     * Enabling persistent fly mode.
     *
     * @param player target player
     * @return true if success, false if not flying or credit not enough.
     */
    boolean persist(Player player); // TODO: 飛んでない時に呼び出す方式に変える？

    /**
     * Add credit, enable fly if not flying.
     *
     * @param player target player
     * @param price  credit price (per minute)
     * @param minute credit quantity
     * @param payer  credit payer. no refund if null.
     * @return true if start flying, false if already flying.
     */
    boolean addCredit(Player player, double price, int minute, OfflinePlayer payer);

    /**
     * Add credit, enable fly if not flying.
     *
     * @param player target player
     * @param minute credit quantity
     * @return true if start flying, false if already flying.
     */
    default boolean addCredit(Player player, int minute) {
        return addCredit(player, 0.0, minute, null);
    }

    /**
     * @param player target player
     * @param minute credit quantity
     * @return less than 0 if not flying, 0 if enough, required quantity if not enough.
     */
    int useCredit(Player player, int minute); // TODO: autostopバージョン？

    //default int useCredit(Player player, int minute, boolean autostop);

    default boolean useCredit(Player player) {
        return useCredit(player, 1) == 0;
    }

    Map<OfflinePlayer, Double> stop(Player player);
}
