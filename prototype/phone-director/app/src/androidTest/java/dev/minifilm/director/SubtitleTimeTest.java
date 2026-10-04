package dev.minifilm.director;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure timestamp checks; no camera, UI, fixture media or permissions. */
public final class SubtitleTimeTest {
    @Test public void sourceEnd5746RetainsEveryMillisecondDuringDefaultReviewSave() {
        assertEquals("5.746", SubtitleTime.format(5746));
        assertEquals(5746, SubtitleTime.parse(SubtitleTime.format(5746)));
        assertTrue(SubtitleTime.parse(SubtitleTime.format(5746)) <= 5746);
    }

    @Test public void fractionalAndTrimBoundsRoundTripExactly() {
        assertEquals("1.001", SubtitleTime.format(1001));
        assertEquals("179.999", SubtitleTime.format(179999));
        for (long milliseconds : new long[] {0, 1, 9, 10, 99, 100, 999, 1000, 1001, 179999,
                Long.MAX_VALUE, Long.MIN_VALUE})
            assertEquals(milliseconds, SubtitleTime.parse(SubtitleTime.format(milliseconds)));
        assertEquals(5750, SubtitleTime.parse("5.75"));
        assertEquals(1000, SubtitleTime.parse(" 1 "));
    }

    @Test public void extraPrecisionRoundsHalfUpAndNegativeValuesRemainForCallerValidation() {
        assertEquals(1001, SubtitleTime.parse("1.0005"));
        assertEquals(1000, SubtitleTime.parse("1.0004"));
        assertEquals(-1001, SubtitleTime.parse("-1.0005"));
        assertEquals(-1, SubtitleTime.parse("-0.001"));
        assertEquals("-0.001", SubtitleTime.format(-1));
    }

    @Test public void invalidNonfiniteAndOverflowInputsAreRejected() {
        for (String input : new String[] {null, "", " ", "NaN", "Infinity", "-Infinity", "five", "1,001", "1.2.3",
                "9223372036854775.808", "-9223372036854775.809", "1e100000000", "1e-100000000"}) {
            try { SubtitleTime.parse(input); fail("Invalid timestamp was accepted: " + input); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
