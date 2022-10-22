package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidFormatGenerator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class InvalidDateTimeFormatGenerator implements InvalidFormatGenerator {

    @Override
    public String getAlmostValidValue() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:'Z'").format(LocalDateTime.now(ZoneId.systemDefault()));
    }

    @Override
    public String getTotallyWrongValue() {
        return "1000-07-21T88:32:28Z";
    }
}
