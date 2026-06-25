package com.group3.cinema.config;

/*
 * Added on 2026-06-26: Booking/payment schema migration for customer booking flow.
 * Created by: HuyPB - HE191335
 */

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentSchemaMigration {
    private final JdbcTemplate jdbcTemplate;

    public PaymentSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void allowPayOsPaymentMethod() {
        try {
            jdbcTemplate.execute("""
                    DECLARE @constraintName nvarchar(128);
                    SELECT @constraintName = cc.name
                    FROM sys.check_constraints cc
                    JOIN sys.columns c ON cc.parent_object_id = c.object_id
                    WHERE cc.parent_object_id = OBJECT_ID('payments')
                      AND c.name = 'payment_method'
                      AND cc.definition LIKE '%VNPAY%'
                      AND cc.definition LIKE '%MOMO%';
                    IF @constraintName IS NOT NULL
                    BEGIN
                        EXEC('ALTER TABLE payments DROP CONSTRAINT [' + @constraintName + ']');
                    END
                    IF NOT EXISTS (
                        SELECT 1
                        FROM sys.check_constraints
                        WHERE parent_object_id = OBJECT_ID('payments')
                          AND name = 'CK_payments_payment_method'
                    )
                    BEGIN
                        ALTER TABLE payments ADD CONSTRAINT CK_payments_payment_method
                        CHECK (payment_method IN ('VNPAY', 'MOMO', 'PAYOS'));
                    END
                    """);
            jdbcTemplate.execute("""
                    IF OBJECT_ID('booking_seat_prices', 'U') IS NULL
                    BEGIN
                        CREATE TABLE booking_seat_prices (
                            seat_type nvarchar(30) NOT NULL PRIMARY KEY,
                            price decimal(18,2) NOT NULL,
                            active bit NOT NULL DEFAULT 1
                        );
                    END;
                    IF NOT EXISTS (SELECT 1 FROM booking_seat_prices WHERE seat_type = 'std')
                        INSERT INTO booking_seat_prices(seat_type, price, active) VALUES ('std', 90000, 1);
                    IF NOT EXISTS (SELECT 1 FROM booking_seat_prices WHERE seat_type = 'vip')
                        INSERT INTO booking_seat_prices(seat_type, price, active) VALUES ('vip', 120000, 1);
                    IF NOT EXISTS (SELECT 1 FROM booking_seat_prices WHERE seat_type = 'couple')
                        INSERT INTO booking_seat_prices(seat_type, price, active) VALUES ('couple', 180000, 1);
                    """);
            jdbcTemplate.execute("""
                    IF OBJECT_ID('booking_vouchers', 'U') IS NULL
                    BEGIN
                        CREATE TABLE booking_vouchers (
                            code nvarchar(50) NOT NULL PRIMARY KEY,
                            discount_percent decimal(5,2) NOT NULL,
                            max_discount decimal(18,2) NOT NULL,
                            active bit NOT NULL DEFAULT 1
                        );
                    END;
                    IF NOT EXISTS (SELECT 1 FROM booking_vouchers WHERE code = 'CINEFLOW10')
                        INSERT INTO booking_vouchers(code, discount_percent, max_discount, active)
                        VALUES ('CINEFLOW10', 10, 50000, 1);
                    """);
            jdbcTemplate.execute("""
                    IF OBJECT_ID('booking_settings', 'U') IS NULL
                    BEGIN
                        CREATE TABLE booking_settings (
                            setting_key nvarchar(80) NOT NULL PRIMARY KEY,
                            setting_value nvarchar(255) NOT NULL
                        );
                    END;
                    IF NOT EXISTS (SELECT 1 FROM booking_settings WHERE setting_key = 'cinema_name')
                        INSERT INTO booking_settings(setting_key, setting_value)
                        VALUES ('cinema_name', N'Rạp CineFlow Mỹ Đình');
                    """);
        } catch (Exception ignored) {
            // The app can still start; Hibernate may create the table on a fresh database.
        }
    }
}
