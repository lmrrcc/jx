package jx;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Browser scalar spelling for keys. Kept outside rendering so Children also works on the JVM.
 * The decimal algorithm follows ECMAScript Number::toString's shortest/nearest/even selection. */
final class ChildKeys {
    private ChildKeys() {}

    static String scalar(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer)
            return value.toString();
        if (value instanceof Long number) {
            if (number < -9_007_199_254_740_991L || number > 9_007_199_254_740_991L)
                throw new IllegalArgumentException("Long exceeds JavaScript exact integer range; use a String binding");
            return number.toString();
        }
        if (value instanceof Float || value instanceof Double) return number(((Number) value).doubleValue());
        throw new IllegalArgumentException("Key must be a browser scalar");
    }

    private static String number(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (value == Double.POSITIVE_INFINITY) return "Infinity";
        if (value == Double.NEGATIVE_INFINITY) return "-Infinity";
        if (value == 0) return "0";
        boolean negative = value < 0;
        double magnitude = Math.abs(value);
        // Find the shortest decimal that rounds to this binary64 value. BigDecimal(double)
        // retains the exact value, so ties can use the even decimal significand. Java's own
        // Double.toString differs for subnormals and uses different exponent thresholds.
        BigDecimal exact = new BigDecimal(magnitude);
        BigDecimal shortest = null;
        for (int precision = 1; precision <= 17; precision++) {
            BigDecimal candidate = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
            if (candidate.doubleValue() == magnitude) { shortest = candidate; break; }
            // At a binary exponent boundary the rounding interval is asymmetric. If the
            // closest decimal misses its narrow side, the adjacent decimal can still fit.
            BigDecimal adjacent = candidate.compareTo(exact) < 0 ? candidate.add(candidate.ulp()) : candidate.subtract(candidate.ulp());
            if (adjacent.doubleValue() == magnitude) { shortest = adjacent; break; }
        }
        if (shortest == null) throw new IllegalStateException("No round-tripping decimal for finite browser key");
        shortest = shortest.stripTrailingZeros();
        int exponent = shortest.precision() - shortest.scale() - 1;
        String text;
        if (exponent >= -6 && exponent < 21) text = shortest.toPlainString();
        else {
            String digits = shortest.unscaledValue().toString();
            text = digits.substring(0, 1) + (digits.length() == 1 ? "" : "." + digits.substring(1))
                    + "e" + (exponent < 0 ? "" : "+") + exponent;
        }
        return negative ? "-" + text : text;
    }
}
