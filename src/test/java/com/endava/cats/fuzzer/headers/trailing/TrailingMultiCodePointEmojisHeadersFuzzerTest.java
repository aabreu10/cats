package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingMultiCodePointEmojisHeadersFuzzerTest {
    private TrailingMultiCodePointEmojisHeadersFuzzer trailingMultiCodePointEmojisHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingMultiCodePointEmojisHeadersFuzzer = new TrailingMultiCodePointEmojisHeadersFuzzer(null);
    }

    @Test
    void shouldReturnTrailFuzzingStrategy() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.getFuzzerContext().getFuzzStrategy().get(1).getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldNotMatchResponseSchema() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isFalse();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(trailingMultiCodePointEmojisHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
