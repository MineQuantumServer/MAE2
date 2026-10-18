package stone.mae2;

import org.junit.jupiter.api.Test;
import stone.mae2.util.TransferMath;
import stone.mae2.parts.p2p.PatternPushContext;
import java.math.BigInteger;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TransferMathTest {
    @Test void packetAccountingDoesNotOverflow() {
        var random = new Random(7211);
        for (int i = 0; i < 10000; i++) {
            long stored = random.nextLong() & Long.MAX_VALUE;
            long voltage = (random.nextLong() & Long.MAX_VALUE) | 1;
            long offered = random.nextLong() & Long.MAX_VALUE;
            long accepted = TransferMath.acceptedPackets(stored, voltage, offered);
            var resulting = BigInteger.valueOf(stored).add(BigInteger.valueOf(voltage).multiply(BigInteger.valueOf(accepted)));
            assertTrue(resulting.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0);
            assertTrue(accepted >= 0 && accepted <= offered);
            assertEquals(resulting.longValueExact(), stored + voltage * accepted);
        }
        assertEquals(0, TransferMath.acceptedPackets(Long.MAX_VALUE, 1, 10));
        assertEquals(0, TransferMath.acceptedPackets(0, 0, 10));
        assertEquals(0, TransferMath.acceptedPackets(0, 8, -1));
    }

    @Test void boundedSelectionIncludesBothCornersAndHandlesExtremeCoordinates() {
        assertTrue(TransferMath.volumeWithin(0, 0, 0, 31, 31, 31, 32768));
        assertFalse(TransferMath.volumeWithin(0, 0, 0, 32, 31, 31, 32768));
        assertFalse(TransferMath.volumeWithin(0, 0, 0, 32768, 0, 0, 32768));
        assertFalse(TransferMath.volumeWithin(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0, 32768));
        assertFalse(TransferMath.volumeWithin(0, 0, 0, 0, 0, 0, 0));
    }

    @Test void partialAndInvalidReceiverReportsAreBounded() {
        assertEquals(3, TransferMath.clampAccepted(10, 3));
        assertEquals(10, TransferMath.clampAccepted(10, Long.MAX_VALUE));
        assertEquals(0, TransferMath.clampAccepted(10, -1));
    }

    @Test void nestedAndFailedPatternCallsRestoreBlockingState() {
        assertFalse(PatternPushContext.isBlocking());
        PatternPushContext.push(true);
        try {
            assertTrue(PatternPushContext.isBlocking());
            assertThrows(IllegalArgumentException.class, () -> {
                PatternPushContext.push(false);
                try {
                    assertFalse(PatternPushContext.isBlocking());
                    throw new IllegalArgumentException();
                } finally { PatternPushContext.pop(); }
            });
            assertTrue(PatternPushContext.isBlocking());
        } finally { PatternPushContext.pop(); }
        assertFalse(PatternPushContext.isBlocking());
    }
    @Test void mergedBuffersRetainMoreThanOneLongOfEnergy() {
        var energy = new stone.mae2.util.SharedEnergy();
        energy.add(Long.MAX_VALUE); energy.add(Long.MAX_VALUE);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), energy.exact());
        assertEquals(Long.MAX_VALUE, energy.stored());
        assertEquals(0, energy.room());
        assertEquals(Long.MAX_VALUE, energy.packets(1));
        energy.remove(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        assertEquals(Long.MAX_VALUE - 1, energy.stored());
        assertEquals(1, energy.room());
        assertThrows(IllegalArgumentException.class, () -> energy.remove(Long.MAX_VALUE));
    }
}
