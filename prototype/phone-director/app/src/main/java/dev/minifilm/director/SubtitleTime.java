package dev.minifilm.director;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact millisecond display and decimal-seconds input for editable subtitles/cuts. */
public final class SubtitleTime {
    private SubtitleTime() {}

    /** Locale-independent US decimal point and exactly three fractional digits. */
    public static String format(long milliseconds) {
        return BigDecimal.valueOf(milliseconds, 3).setScale(3).toPlainString();
    }

    /** Round sub-millisecond input HALF_UP. Negative input is allowed;
     * the caller validates nonnegative source bounds and start/end ordering.
     */
    public static long parse(String seconds) {
        if (seconds == null || seconds.trim().isEmpty() || seconds.trim().length() > 64)
            throw new IllegalArgumentException("Enter decimal seconds.");
        try {
            BigDecimal value = new BigDecimal(seconds.trim());
            // Bound scale/exponents before rounding can expand attacker-sized decimals.
            if (value.abs().compareTo(new BigDecimal("9223372036854776")) >= 0
                    || value.scale() < -20 || value.scale() > 100)
                throw new IllegalArgumentException("Seconds are outside the supported range.");
            return value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Enter finite decimal seconds within the supported range.", invalid);
        }
    }
}
