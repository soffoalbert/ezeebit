package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.MerchantDirectory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin JDBC read of the merchant's country. No {@code MerchantEntity} is mapped: the wallet
 * treats the merchant table as reference data owned elsewhere, and only needs the country for
 * payout routing.
 */
@Component
class MerchantDirectoryAdapter implements MerchantDirectory {

    private final JdbcTemplate jdbc;

    MerchantDirectoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String countryOf(long merchantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT country FROM merchant WHERE id = ?", String.class, merchantId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
