package example.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomUnicodeMigration {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrateRoomUnicodeColumns() {
        try {
            if (!roomsTableExists()) {
                return;
            }

            jdbcTemplate.execute("ALTER TABLE rooms ALTER COLUMN room_name NVARCHAR(100) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE rooms ALTER COLUMN room_type NVARCHAR(20) NULL");
            jdbcTemplate.execute("ALTER TABLE rooms ALTER COLUMN audio_tech NVARCHAR(50) NULL");
            jdbcTemplate.execute("ALTER TABLE rooms ALTER COLUMN status NVARCHAR(20) NULL");

            jdbcTemplate.update("""
                    UPDATE rooms
                    SET status = N'Hoạt động'
                    WHERE status IN ('Ho?t d?ng', 'Ho?t ??ng', 'Hoat dong')
                    """);
            jdbcTemplate.update("""
                    UPDATE rooms
                    SET status = N'Bảo trì'
                    WHERE status IN ('B?o tr?', 'Bao tri')
                    """);
            jdbcTemplate.update("""
                    UPDATE rooms
                    SET status = N'Tạm ngưng'
                    WHERE status IN ('T?m ng?ng', 'Tam ngung')
                    """);
        } catch (Exception e) {
            log.warn("Không thể tự động chuyển cột rooms sang NVARCHAR: {}", e.getMessage());
        }
    }

    private boolean roomsTableExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'rooms'
                """, Integer.class);
        return count != null && count > 0;
    }
}
