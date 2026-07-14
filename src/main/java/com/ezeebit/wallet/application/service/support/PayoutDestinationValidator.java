package com.ezeebit.wallet.application.service.support;

import com.ezeebit.wallet.application.port.out.Serializer;
import com.ezeebit.wallet.domain.exception.InvalidDestinationException;
import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a payout destination against the shape expected for the currency: a blockchain
 * address for stablecoins, bank details for fiat. A malformed destination is rejected up
 * front rather than being handed to a payout rail that would reject it later.
 */
@Component
public class PayoutDestinationValidator {

    // Deliberately permissive: enough to catch obvious mistakes without hard-coding one chain.
    private static final Pattern CHAIN_ADDRESS = Pattern.compile("^[A-Za-z0-9:_-]{16,120}$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{6,34}$");

    private final Serializer serializer;

    public PayoutDestinationValidator(Serializer serializer) {
        this.serializer = serializer;
    }

    public void validate(Currency currency, String destinationJson) {
        Map<?, ?> fields = parse(destinationJson);
        if (currency.isStablecoin()) {
            String address = stringField(fields, "address");
            if (!CHAIN_ADDRESS.matcher(address).matches()) {
                throw new InvalidDestinationException(
                        "destination 'address' is not a valid blockchain address for " + currency);
            }
        } else {
            String account = stringField(fields, "accountNumber");
            if (!ACCOUNT_NUMBER.matcher(account).matches()) {
                throw new InvalidDestinationException(
                        "destination 'accountNumber' must be 6-34 digits for " + currency);
            }
            String bankCode = stringField(fields, "bankCode");
            if (bankCode.isBlank()) {
                throw new InvalidDestinationException("destination 'bankCode' is required for " + currency);
            }
        }
    }

    private Map<?, ?> parse(String json) {
        try {
            return serializer.fromJson(json, Map.class);
        } catch (RuntimeException e) {
            throw new InvalidDestinationException("destination is not a valid JSON object");
        }
    }

    private String stringField(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new InvalidDestinationException("destination is missing required field '" + key + "'");
        }
        return value.toString();
    }
}
