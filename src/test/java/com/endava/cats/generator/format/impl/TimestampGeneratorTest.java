package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class TimestampGeneratorTest {

    @Test
    void shouldGenerate() {
        TimestampGenerator timestampGenerator = new TimestampGenerator();
        Object generated = timestampGenerator.generate(new Schema<>());
        Assertions.assertThat(generated).asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,6}Z");
    }

    @ParameterizedTest
    @CsvSource({"timestamp,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        TimestampGenerator timestampGenerator = new TimestampGenerator();
        Assertions.assertThat(timestampGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
