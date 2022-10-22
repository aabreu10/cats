package com.endava.cats.strategy;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates various fuzzing strategies:
 * <ul>
 * <li>REPLACE - when the fuzzed value replaces the one generated by the OpenAPIModelGenerator</li>
 * <li>TRAIL - trails the current value with the given string</li>
 * <li>PREFIX - prefixes the current value with the given string</li>
 * <li>SKIP - doesn't do anything to the current value</li>
 * <li>NOOP - returns the given string</li>
 * </ul>
 */
public abstract class FuzzingStrategy {
    private static final Pattern ALL = Pattern.compile("^[\\p{C}\\p{Z}\\p{So}\\p{Sk}\\p{M}]+[\\p{C}\\p{Z}\\p{So}\\p{Sk}\\p{M}]*$");
    private static final Pattern WITHIN = Pattern.compile("([\\p{C}\\p{Z}\\p{So}\\p{Sk}\\p{M}]+|జ్ఞ\u200Cా|স্র\u200Cু)");

    protected Object data;

    public static FuzzingStrategy prefix() {
        return new PrefixFuzzingStrategy();
    }

    public static FuzzingStrategy noop() {
        return new NoopFuzzingStrategy();
    }

    public static FuzzingStrategy replace() {
        return new ReplaceFuzzingStrategy();
    }

    public static FuzzingStrategy skip() {
        return new SkipFuzzingStrategy();
    }

    public static FuzzingStrategy trail() {
        return new TrailFuzzingStrategy();
    }

    public static FuzzingStrategy insert() {
        return new InsertFuzzingStrategy();
    }

    public static Object mergeFuzzing(Object fuzzedValue, Object suppliedValue) {
        FuzzingStrategy currentStrategy = fromValue(fuzzedValue);

        return currentStrategy.process(suppliedValue);
    }

    public static FuzzingStrategy fromValue(Object valueObject) {
        String valueAsString = String.valueOf(valueObject);
        if (StringUtils.isBlank(valueAsString) || ALL.matcher(valueAsString).matches()) {
            return replace().withData(valueObject);
        }
        if (isUnicodeControlChar(valueAsString.charAt(0)) || isUnicodeWhitespace(valueAsString.charAt(0)) || isUnicodeOtherSymbol(valueAsString.charAt(0))) {
            return prefix().withData(replaceSpecialCharsWithEmpty(valueAsString));
        }
        if (isUnicodeControlChar(valueAsString.charAt(valueAsString.length() - 1)) || isUnicodeWhitespace(valueAsString.charAt(valueAsString.length() - 1)) || isUnicodeOtherSymbol(valueAsString.charAt(valueAsString.length() - 1))) {
            return trail().withData(replaceSpecialCharsWithEmpty(valueAsString));
        }
        if (isLargeString(valueAsString)) {
            return replace().withData(valueObject);
        }
        Matcher withinMatcher = WITHIN.matcher(valueAsString);
        if (withinMatcher.find()) {
            return insert().withData(withinMatcher.group());
        }

        return replace().withData(valueObject);
    }

    private static String replaceSpecialCharsWithEmpty(String value) {
        return value.replaceAll("[^\\p{Z}\\p{C}\\p{So}\\p{Sk}\\p{M}]+", "");
    }

    public static String formatValue(String data) {
        if (data == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (isUnicodeWhitespace(c) || isUnicodeControlChar(c) || isUnicodeOtherSymbol(c)) {
                builder.append(String.format("\\u%04x", (int) c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static boolean isUnicodeControlChar(char c) {
        return Character.getType(c) == Character.CONTROL || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.PRIVATE_USE || Character.getType(c) == Character.SURROGATE;
    }

    private static boolean isUnicodeWhitespace(char c) {
        return Character.getType(c) == Character.LINE_SEPARATOR ||
                Character.getType(c) == Character.PARAGRAPH_SEPARATOR || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    private static boolean isUnicodeOtherSymbol(char c) {
        return Character.getType(c) == Character.OTHER_SYMBOL || Character.getType(c) == Character.MODIFIER_SYMBOL;
    }

    public static boolean isLargeString(String data) {
        return data.startsWith("ca") && data.endsWith("ts");
    }

    public FuzzingStrategy withData(Object inner) {
        this.data = inner;
        return this;
    }

    public Object getData() {
        return this.data;
    }

    public boolean isSkip() {
        return this.getClass().isAssignableFrom(SkipFuzzingStrategy.class);
    }

    @Override
    public String toString() {
        return this.truncatedValue();
    }

    public String truncatedValue() {
        if (data != null) {
            String toPrint = String.valueOf(data);
            if (toPrint.length() > 30) {
                toPrint = toPrint.substring(0, 30) + "...";
            }
            return this.name() + " with " + formatValue(toPrint);
        }
        return this.name();
    }

    public static FuzzingStrategy getFuzzStrategyWithRepeatedCharacterReplacingValidValue(FuzzingData data, String fuzzedField, String characterToRepeat) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);
        String spaceValue = characterToRepeat;
        if (schema != null && schema.getMinLength() != null) {
            spaceValue = StringUtils.repeat(spaceValue, (schema.getMinLength() / spaceValue.length()) + 1);
        }
        return FuzzingStrategy.replace().withData(spaceValue);
    }

    public static List<FuzzingStrategy> getLargeValuesStrategy(int largeStringsSize) {
        String generatedValue = StringGenerator.generateRandomUnicode();
        int payloadSize = largeStringsSize / generatedValue.length();
        if (payloadSize == 0) {
            return Collections.singletonList(FuzzingStrategy.replace().withData(markLargeString(generatedValue.substring(0, largeStringsSize))));
        }
        return Collections.singletonList(FuzzingStrategy.replace().withData(markLargeString(StringUtils.repeat(generatedValue, payloadSize + 1))));
    }

    public static String markLargeString(String input) {
        return "ca" + input + "ts";
    }


    public abstract Object process(Object value);

    public abstract String name();
}
