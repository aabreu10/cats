package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class OnlySingleCodePointEmojisInHeadersFuzzer extends BaseHeadersFuzzer {

    public OnlySingleCodePointEmojisInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext getFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("values replaced by single code point emojis")
                .fuzzStrategy(UnicodeGenerator.getSingleCodePointEmojis()
                        .stream().map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}