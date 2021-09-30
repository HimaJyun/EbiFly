package jp.jyn.ebifly;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

// Currently, not available API Maven repository. This plugin API is probably not needed by other plugins.
// You need API Maven repository? Please post issue to me! ( https://github.com/HimaJyun/EbiFly/issues )
public interface EbiFly {
    /**
     * Check fly mode.<br>
     * <b>Note</b>: This method uses the internal state of the plugin, It may not match the {@link Player#isFlying()} or
     * {@link Player#getAllowFlight()}.
     *
     * @param player target player
     * @return true if flying, false otherwise
     */
    boolean isFlying(Player player);

    /**
     * Check persist mode.<br>
     * <b>Note</b>: This method uses the internal state of the plugin, It may not match the {@link Player#isFlying()} or
     * {@link Player#getAllowFlight()}.
     *
     * @param player target player
     * @return true if persist flying, false otherwise
     */
    boolean isPersist(Player player);

    /**
     * Change persistent fly mode.<br>
     * Note: This method require credits. if credit empty, return false.
     *
     * @param player target player
     * @param enable true if persisted, false if timed.
     * @return true if mode switched, false otherwise. (eg: not flying, already specified mode)
     */
    boolean persist(Player player, boolean enable);

    /**
     * Switch persistent fly mode.<br>
     * Note: This method require credits. if credit empty, return false.
     *
     * @param player target player
     * @return true if mode switched, false if not flying.
     */
    default boolean persist(Player player) {
        return persist(player, !isPersist(player));
    }

    /**
     * Add credit, enable fly if not flying.
     *
     * @param player  target player
     * @param price   credit price (per minute)
     * @param minute  credit quantity (minute)
     * @param payer   credit payer. no refund if null.
     * @param persist persist mode. if true, per minute payment from player. (Note: doesn't use payer and price for
     *                per minute payment)
     * @return true if start flying, false if already flying.
     */
    boolean addCredit(Player player, double price, int minute, OfflinePlayer payer, boolean persist);

    /**
     * Add credit, enable fly if not flying.
     *
     * @param player target player
     * @param price  credit price (per minute)
     * @param minute credit quantity
     * @param payer  credit payer. no refund if null.
     * @return true if start flying, false if already flying.
     */
    default boolean addCredit(Player player, double price, int minute, OfflinePlayer payer) {
        return addCredit(player, price, minute, payer, false);
    }

    /**
     * Add credit, enable fly if not flying.
     *
     * @param player target player
     * @param minute credit quantity
     * @return true if start flying, false if already flying.
     */
    default boolean addCredit(Player player, int minute) {
        return addCredit(player, 0.0, minute, null, false);
    }

    /**
     * Add credit, enable fly if not flying.
     *
     * @param player  target player
     * @param minute  credit quantity
     * @param persist persist mode. if true, per minute payment from player. (Note: doesn't use payer and price for
     *                per minute payment)
     * @return true if start flying, false if already flying.
     */
    default boolean addCredit(Player player, int minute, boolean persist) {
        return addCredit(player, 0.0, minute, null, persist);
    }

    /**
     * Use credits.<br>
     * Note: If player doesn't have enough credits, flight will be stopped.
     *
     * @param player target player
     * @param minute credit quantity
     * @param notice show notice if flight stopped.
     * @return less than 0 if not flying, 0 if enough, required quantity if not enough.
     */
    int useCredit(Player player, int minute, boolean notice);

    /**
     * Use credits.<br>
     * Note: If player doesn't have enough credits, flight will be stopped.
     *
     * @param player target player
     * @param minute credit quantity
     * @return less than 0 if not flying, 0 if enough, required quantity if not enough.
     */
    default int useCredit(Player player, int minute) {
        return useCredit(player, minute, true);
    }

    /**
     * Use credits.<br>
     * Note: If player doesn't have enough credits, flight will be stopped.
     *
     * @param player target player
     * @param notice show notice if flight stopped.
     * @return less than 0 if not flying, 0 if enough, required quantity if not enough.
     */
    default int useCredit(Player player, boolean notice) {
        return useCredit(player, 1, notice);
    }

    /**
     * Use credits.<br>
     * Note: If player doesn't have enough credits, flight will be stopped.
     *
     * @param player target player
     * @return less than 0 if not flying, 0 if enough, required quantity if not enough.
     */
    default int useCredit(Player player) {
        return useCredit(player, 1);
    }

    Map<OfflinePlayer, Double> stop(Player player);
}
