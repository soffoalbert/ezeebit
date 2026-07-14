package com.ezeebit.wallet.domain.model;

/**
 * The currencies the platform supports. Each currency carries its own kind
 * (fiat vs stablecoin) and the number of decimal places money is tracked to.
 * Keeping scale on the currency is what lets {@link Money} refuse to silently
 * mix or mis-round two different currencies.
 */
public enum Currency {
    ZAR(Kind.FIAT, 2),
    NGN(Kind.FIAT, 2),
    KES(Kind.FIAT, 2),
    USDT(Kind.STABLECOIN, 6),
    USDC(Kind.STABLECOIN, 6);

    public enum Kind { FIAT, STABLECOIN }

    private final Kind kind;
    private final int scale;

    Currency(Kind kind, int scale) {
        this.kind = kind;
        this.scale = scale;
    }

    public Kind kind() {
        return kind;
    }

    public int scale() {
        return scale;
    }

    public boolean isStablecoin() {
        return kind == Kind.STABLECOIN;
    }

    public boolean isFiat() {
        return kind == Kind.FIAT;
    }
}
