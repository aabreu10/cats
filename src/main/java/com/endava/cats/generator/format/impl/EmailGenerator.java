package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * A generator class implementing various interfaces for generating valid and invalid email data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class EmailGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    private static final String EMAIL = "email";

    @Override
    public Object generate(Schema<?> schema) {
        String hero = CatsUtil.faker().ancient().hero().toLowerCase(Locale.ROOT);
        String color = CatsUtil.faker().color().name().toLowerCase(Locale.ROOT);

        return "%s.%s@cats.io".formatted(hero, color).replace(" ", "-");
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return propertyName.toLowerCase(Locale.ROOT).endsWith(EMAIL) ||
                PropertySanitizer.sanitize(propertyName).endsWith("emailaddress") ||
                EMAIL.equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "email@bubu.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "bubulina";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of(EMAIL);
    }
}
