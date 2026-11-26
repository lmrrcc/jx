package jx.example.contract;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Explicit, bounded wire proof independent of Todo's UUID identifiers. Long and
 * decimal fields always travel as canonical strings, including zero and small values.
 * BigDecimal scale is preserved; this value has no implicit currency or rounding. */
public record ExactValues(long id, BigDecimal amount, String note, ExactValues.TextField extra) {
    public static final String PATH = "/api/contract-values";
    public static final String ID = "id";
    public static final String AMOUNT = "amount";
    public static final String NOTE = "note";
    public static final String EXTRA = "extra";

    /** Absent differs from a present JSON null. JSON has no undefined representation. */
    public record TextField(boolean present, String value) {
        public TextField {
            if (!present && value != null) throw new IllegalArgumentException("An absent field has no value");
            bounded(value);
        }
        public static TextField absent() { return new TextField(false, null); }
        public static TextField present(String value) { return new TextField(true, value); }
    }
    public ExactValues {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(extra, "extra");
        if (amount.precision() > 128 || amount.scale() < -128 || amount.scale() > 128) {
            throw new IllegalArgumentException("Decimal exceeds precision or scale limit");
        }
        bounded(note);
    }
    public String idText() { return Long.toString(id); }
    public String amountText() { return amount.toString(); }

    /** Decode textual numeric fields without a floating-point intermediate. Noncanonical
     * alternatives are errors, so representation never depends on a particular value. */
    public static ExactValues fromWire(String id, String amount, String note, TextField extra) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        if (id.length() > 20 || amount.length() > 256) throw new IllegalArgumentException("Numeric text exceeds limit");
        long parsedId = Long.parseLong(id);
        BigDecimal parsedAmount = new BigDecimal(amount);
        if (!Long.toString(parsedId).equals(id) || !parsedAmount.toString().equals(amount)) {
            throw new IllegalArgumentException("Numeric text must use its canonical representation");
        }
        return new ExactValues(parsedId, parsedAmount, note, extra);
    }
    private static void bounded(String value) {
        if (value != null && value.length() > 128) throw new IllegalArgumentException("Text exceeds limit");
    }

    /** The same immutable values run through JVM and compiled Java browser consumers. */
    public static List<ExactValues> vectors() {
        long[] ids = {Long.MIN_VALUE, -9_007_199_254_740_993L, -9_007_199_254_740_992L,
                -9_007_199_254_740_991L, -1L, 0L, 1L, 42L, 9_007_199_254_740_991L,
                9_007_199_254_740_992L, 9_007_199_254_740_993L, Long.MAX_VALUE};
        String[] amounts = {"123456789012345678901234567890.123456789", "0.00", "-0.00100", "1E+18", "1.2500"};
        var values = new java.util.ArrayList<ExactValues>();
        for (int index = 0; index < ids.length; index++) {
            TextField field = switch (index % 3) {
                case 0 -> TextField.absent();
                case 1 -> TextField.present(null);
                default -> TextField.present(index % 2 == 0 ? "" : "defined");
            };
            values.add(new ExactValues(ids[index], new BigDecimal(amounts[index % amounts.length]),
                    index % 2 == 0 ? null : "", field));
        }
        return List.copyOf(values);
    }
}
