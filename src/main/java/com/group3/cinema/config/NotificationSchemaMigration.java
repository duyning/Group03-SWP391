package com.group3.cinema.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void allowCurrentNotificationTypes() {
        try {
            jdbcTemplate.execute("""
                    IF OBJECT_ID('notification', 'U') IS NOT NULL
                    BEGIN
                        IF COL_LENGTH('notification', 'image_url') IS NULL
                            ALTER TABLE notification ADD image_url NVARCHAR(500) NULL;

                        IF COL_LENGTH('notification', 'action_url') IS NULL
                            ALTER TABLE notification ADD action_url NVARCHAR(500) NULL;

                        DECLARE @constraintName NVARCHAR(256);

                        DECLARE notification_type_constraints CURSOR LOCAL FAST_FORWARD FOR
                        SELECT cc.name
                        FROM sys.check_constraints cc
                        WHERE cc.parent_object_id = OBJECT_ID('notification')
                          AND (
                                cc.definition LIKE '%[type]%'
                             OR cc.definition LIKE '%type%'
                             OR cc.definition LIKE '%BOOKING%'
                             OR cc.definition LIKE '%PROMOTION%'
                             OR cc.definition LIKE '%VOUCHER%'
                          );

                        OPEN notification_type_constraints;
                        FETCH NEXT FROM notification_type_constraints INTO @constraintName;
                        WHILE @@FETCH_STATUS = 0
                        BEGIN
                            EXEC('ALTER TABLE notification DROP CONSTRAINT [' + @constraintName + ']');
                            FETCH NEXT FROM notification_type_constraints INTO @constraintName;
                        END
                        CLOSE notification_type_constraints;
                        DEALLOCATE notification_type_constraints;

                        ALTER TABLE notification ADD CONSTRAINT CK_notification_type
                        CHECK ([type] IN ('BOOKING', 'PAYMENT', 'PROMOTION', 'VOUCHER', 'MOVIE', 'NEWS', 'SYSTEM'));
                    END
                    """);
            log.info("Đã đồng bộ CHECK constraint cho notification.type");
        } catch (Exception exception) {
            log.warn("Không thể đồng bộ CHECK constraint notification.type: {}", exception.getMessage());
        }
    }
}
