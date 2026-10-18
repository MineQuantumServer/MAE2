package stone.mae2.util;

import java.math.BigInteger;

/** Exact shared accounting: merging valid networks must not overflow their combined buffer. */
public final class SharedEnergy {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private BigInteger amount = BigInteger.ZERO;
    public BigInteger exact() { return amount; }
    public long stored() { return amount.min(MAX).longValueExact(); }
    public long room() { return Long.MAX_VALUE - stored(); }
    public double asDouble() { return amount.doubleValue(); }
    public boolean isEmpty() { return amount.signum() == 0; }
    public void add(long value) { add(BigInteger.valueOf(value)); }
    public void add(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative shared energy");
        amount = amount.add(value);
    }
    public void remove(long value) { remove(BigInteger.valueOf(value)); }
    public void remove(BigInteger value) {
        if (value.signum() < 0 || value.compareTo(amount) > 0) throw new IllegalArgumentException("Invalid energy withdrawal");
        amount = amount.subtract(value);
    }
    public void clear() { amount = BigInteger.ZERO; }
    public long packets(long voltage) {
        if (voltage <= 0) return 0;
        return amount.divide(BigInteger.valueOf(voltage)).min(MAX).longValueExact();
    }
}
