package com.ezeebit.wallet.adapter.out.serialization;

import com.ezeebit.wallet.application.port.out.Serializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Serializer port backed by the Spring-configured Jackson {@link ObjectMapper}, so
 * idempotency response snapshots round-trip with the same modules (Java time, records)
 * as the web layer.
 */
@Component
class JacksonSerializer implements Serializer {

    private final ObjectMapper objectMapper;

    JacksonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize idempotency response", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize idempotency response", e);
        }
    }
}
