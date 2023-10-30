package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseRandomHeadersFuzzer;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.function.Function;

@Singleton
@HeaderFuzzer
public class LargeNumberOfRandomAlphanumericHeadersFuzzer extends BaseRandomHeadersFuzzer {


    public LargeNumberOfRandomAlphanumericHeadersFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, ProcessingArguments processingArguments) {
        super(simpleExecutor, testCaseListener, processingArguments);
    }

    @Override
    public String description() {
        return "send a 'happy' flow request with 10 000 extra random alphanumeric headers";
    }

    @Override
    protected Function<Integer, String> randomHeadersValueFunction() {
        return RandomStringUtils::randomAlphanumeric;
    }
}
