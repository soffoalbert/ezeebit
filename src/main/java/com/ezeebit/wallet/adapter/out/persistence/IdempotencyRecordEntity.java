package com.ezeebit.wallet.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
class IdempotencyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 64)
    private String endpoint;

    @Column(name = "idem_key", nullable = false, length = 120)
    private String idemKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "json")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecordEntity() {}

    IdempotencyRecordEntity(Long merchantId, String endpoint, String idemKey,
                            String requestHash, String responseJson) {
        this.merchantId = merchantId;
        this.endpoint = endpoint;
        this.idemKey = idemKey;
        this.requestHash = requestHash;
        this.responseJson = responseJson;
    }

    String getRequestHash() {
        return requestHash;
    }

    String getResponseJson() {
        return responseJson;
    }

    @PrePersist
    void onInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
