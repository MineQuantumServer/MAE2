package stone.mae2.util;

/** Arithmetic shared by the real energy handlers and bounded world selection. */
public final class TransferMath {
    private TransferMath() {}
    public static long acceptedPackets(long stored, long voltage, long offered) {
        if (stored < 0 || voltage <= 0 || offered <= 0) return 0;
        return Math.min(offered, (Long.MAX_VALUE - stored) / voltage);
    }
    public static long clampAccepted(long offered, long accepted) {
        return Math.max(0, Math.min(offered, accepted));
    }
    public static boolean volumeWithin(int x1, int y1, int z1, int x2, int y2, int z2, long limit) {
        if (limit <= 0) return false;
        long x = Math.abs((long) x1 - x2) + 1;
        long y = Math.abs((long) y1 - y2) + 1;
        long z = Math.abs((long) z1 - z2) + 1;
        return x <= limit && y <= limit / x && z <= limit / x / y;
    }
}
