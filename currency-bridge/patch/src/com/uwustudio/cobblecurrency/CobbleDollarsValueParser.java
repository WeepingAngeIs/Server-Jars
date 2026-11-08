package com.uwustudio.cobblecurrency;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValueParser;
import java.math.BigInteger;

/** The supplied 1.0.0 parser, with foreign currencies correctly declined. */
public final class CobbleDollarsValueParser extends MoneyValueParser {
    public static final MoneyValueParser INSTANCE = new CobbleDollarsValueParser();

    private CobbleDollarsValueParser() {
        super("cd");
    }

    @Override
    protected MoneyValue parseValueArgument(StringReader reader) throws CommandSyntaxException {
        String raw = reader.getRemaining().replace(",", "").replace("_", "").trim();
        reader.setCursor(reader.getTotalLength());
        if (raw.isEmpty()) {
            throw NO_VALUE_EXCEPTION.createWithContext(reader);
        }
        try {
            return CobbleDollarsValue.of(new BigInteger(raw));
        } catch (NumberFormatException e) {
            throw NO_VALUE_EXCEPTION.createWithContext(reader);
        }
    }

    @Override
    protected String writeValueArgument(MoneyValue value) {
        if (value instanceof CobbleDollarsValue cd) {
            return cd.getAmount().toString();
        }
        // Lightman's tryWrite uses null to let the owning currency serialize its value.
        return null;
    }
}
