package jp.jyn.ebifly;

import jp.jyn.jbukkitlib.util.PackagePrivate;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

@PackagePrivate
final class FlyDelegate implements EbiFly {
    private EbiFly instance;

    @PackagePrivate
    FlyDelegate() {
        this.instance = new UnsupportedFly();
    }

    @PackagePrivate
    void setInstance(EbiFly instance) {
        this.instance = instance == null ? new UnsupportedFly() : instance;
    }

    @Override
    public boolean isFlying(Player player) {
        return instance.isFlying(player);
    }

    @Override
    public boolean isPersist(Player player) {
        return instance.isPersist(player);
    }

    @Override
    public boolean persist(Player player, boolean enable) {
        return instance.persist(player, enable);
    }

    @Override
    public boolean persist(Player player) {
        return instance.persist(player);
    }

    @Override
    public boolean addCredit(Player player, double price, int minute, OfflinePlayer payer, boolean persist) {
        return instance.addCredit(player, price, minute, payer, persist);
    }

    @Override
    public boolean addCredit(Player player, double price, int minute, OfflinePlayer payer) {
        return instance.addCredit(player, price, minute, payer);
    }

    @Override
    public boolean addCredit(Player player, int minute) {
        return instance.addCredit(player, minute);
    }

    @Override
    public boolean addCredit(Player player, int minute, boolean persist) {
        return instance.addCredit(player, minute, persist);
    }

    @Override
    public int useCredit(Player player, int minute, boolean notice) {
        return instance.useCredit(player, minute, notice);
    }

    @Override
    public int useCredit(Player player, int minute) {
        return instance.useCredit(player, minute);
    }

    @Override
    public int useCredit(Player player, boolean notice) {
        return instance.useCredit(player, notice);
    }

    @Override
    public int useCredit(Player player) {
        return instance.useCredit(player);
    }

    @Override
    public Map<OfflinePlayer, Double> stop(Player player) {
        return instance.stop(player);
    }

    private final static class UnsupportedFly implements EbiFly {
        private UnsupportedFly() {}

        @Override
        public boolean isFlying(Player player) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean isPersist(Player player) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean persist(Player player, boolean enable) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean persist(Player player) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean addCredit(Player player, double price, int minute, OfflinePlayer payer, boolean persist) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean addCredit(Player player, double price, int minute, OfflinePlayer payer) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean addCredit(Player player, int minute) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public boolean addCredit(Player player, int minute, boolean persist) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public int useCredit(Player player, int minute, boolean notice) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public int useCredit(Player player, int minute) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public int useCredit(Player player, boolean notice) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public int useCredit(Player player) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }

        @Override
        public Map<OfflinePlayer, Double> stop(Player player) {
            throw new UnsupportedOperationException("EbiFly is not initialized");
        }
    }
}
