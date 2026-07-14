package com.ezeebit.wallet.application.port.out;

/**
 * Turns a use-case result into a stored string and back, so the idempotency store
 * can persist and replay responses without the application layer depending on a
 * concrete JSON library.
 */
public interface Serializer {
    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);
}
