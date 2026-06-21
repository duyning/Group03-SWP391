/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RoomUnicodeMigration {

    private static final Logger log = LoggerFactory.getLogger(RoomUnicodeMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public RoomUnicodeMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrateRoomUnicodeColumns() {
        try {
            createBannerTableIfMissing();
            addMovieColumnsIfMissing();
            createComboCatalogTablesIfMissing();
            migrateComboPricingColumns();
            seedDefaultFoodItems();
            migrateTextColumns();
            normalizeVietnameseValues();
        } catch (Exception e) {
            log.warn("Không thể tự động chuyển cột tiếng Việt sang NVARCHAR: {}", e.getMessage());
        }
    }

    private void addMovieColumnsIfMissing() {
        if (!tableExists("movie")) {
            return;
        }
        if (!columnExists("movie", "release_year")) {
            try {
                jdbcTemplate.execute("ALTER TABLE movie ADD release_year INT NULL");
                log.info("Đã bổ sung cột release_year cho bảng movie");
            } catch (Exception e) {
                log.warn("Không thể bổ sung cột release_year: {}", e.getMessage());
            }
        }
        if (!columnExists("movie", "producer")) {
            try {
                jdbcTemplate.execute("ALTER TABLE movie ADD producer NVARCHAR(255) NULL");
                log.info("Đã bổ sung cột producer cho bảng movie");
            } catch (Exception e) {
                log.warn("Không thể bổ sung cột producer: {}", e.getMessage());
            }
        }
    }

    private void createBannerTableIfMissing() {
        if (tableExists("banners")) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE banners (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    title NVARCHAR(150) NOT NULL,
                    subtitle NVARCHAR(500) NULL,
                    image_url NVARCHAR(500) NOT NULL,
                    link_url NVARCHAR(500) NULL,
                    page NVARCHAR(20) NOT NULL,
                    display_order INT NOT NULL DEFAULT 1,
                    active BIT NOT NULL DEFAULT 1,
                    created_at DATETIME2 NULL,
                    updated_at DATETIME2 NULL
                )
                """);
    }

    private void createComboCatalogTablesIfMissing() {
        if (!tableExists("food_items")) {
            jdbcTemplate.execute("""
                    CREATE TABLE food_items (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        name NVARCHAR(150) NOT NULL,
                        category NVARCHAR(80) NOT NULL,
                        unit_price DECIMAL(18,2) NOT NULL DEFAULT 0,
                        cost_price DECIMAL(18,2) NOT NULL DEFAULT 0,
                        description NVARCHAR(255) NULL,
                        status NVARCHAR(20) NOT NULL DEFAULT N'ACTIVE'
                    )
                    """);
        }

        if (!tableExists("combo_items") && tableExists("combos") && tableExists("food_items")) {
            jdbcTemplate.execute("""
                    CREATE TABLE combo_items (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        combo_id BIGINT NOT NULL,
                        food_item_id BIGINT NOT NULL,
                        quantity INT NOT NULL DEFAULT 1,
                        unit_price DECIMAL(18,2) NOT NULL DEFAULT 0,
                        cost_price DECIMAL(18,2) NOT NULL DEFAULT 0,
                        CONSTRAINT fk_combo_items_combo FOREIGN KEY (combo_id) REFERENCES combos(id) ON DELETE CASCADE,
                        CONSTRAINT fk_combo_items_food_item FOREIGN KEY (food_item_id) REFERENCES food_items(id)
                    )
                    """);
        }
    }

    private void migrateComboPricingColumns() {
        addColumnIfMissing("combos", "original_price", "DECIMAL(18,2) NULL");
        addColumnIfMissing("combos", "discount_percent", "DECIMAL(5,2) NULL");
        addColumnIfMissing("combos", "discount_amount", "DECIMAL(18,2) NULL");
        addColumnIfMissing("combos", "cost_price", "DECIMAL(18,2) NULL");

        updateIfTableExists("combos", """
                UPDATE combos
                SET original_price = ISNULL(original_price, price),
                    discount_percent = ISNULL(discount_percent, 0),
                    discount_amount = ISNULL(discount_amount, 0),
                    cost_price = ISNULL(cost_price, 0)
                """);
    }

    private void seedDefaultFoodItems() {
        if (!tableExists("food_items")) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM food_items", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO food_items (name, category, unit_price, cost_price, description, status)
                VALUES
                (N'Bắp rang bơ lớn', N'Bắp nước', 55000, 30000, N'Bắp rang bơ cỡ lớn', N'ACTIVE'),
                (N'Bắp rang caramel', N'Bắp nước', 65000, 36000, N'Bắp rang vị caramel', N'NEW'),
                (N'Nước ngọt lớn', N'Bắp nước', 35000, 16000, N'Ly nước ngọt cỡ lớn', N'ACTIVE'),
                (N'Nước suối', N'Đồ uống', 20000, 8000, N'Nước suối đóng chai', N'ACTIVE'),
                (N'Nachos phô mai', N'Đồ ăn nhanh', 59000, 32000, N'Nachos ăn kèm sốt phô mai', N'NEW')
                """);
    }

    private void migrateTextColumns() {
        alterColumn("rooms", "room_name", "NVARCHAR(100) NOT NULL");
        alterColumn("rooms", "room_type", "NVARCHAR(255) NULL");
        alterColumn("rooms", "audio_tech", "NVARCHAR(80) NULL");
        alterColumn("rooms", "status", "NVARCHAR(20) NULL");

        alterColumn("room_types", "name", "NVARCHAR(50) NOT NULL");
        alterColumn("room_types", "description", "NVARCHAR(255) NULL");
        alterColumn("audio_technologies", "name", "NVARCHAR(80) NOT NULL");
        alterColumn("audio_technologies", "description", "NVARCHAR(255) NULL");
        alterColumn("seat_types", "code", "NVARCHAR(20) NOT NULL");
        alterColumn("seat_types", "display_name", "NVARCHAR(80) NOT NULL");
        alterColumn("seats", "seat_label", "NVARCHAR(20) NULL");
        alterColumn("seats", "seat_type", "NVARCHAR(30) NOT NULL");

        alterColumn("account", "name", "NVARCHAR(255) NOT NULL");
        alterColumn("account", "address", "NVARCHAR(MAX) NULL");
        alterColumn("account", "gender", "NVARCHAR(20) NULL");
        alterColumn("account", "avatar", "NVARCHAR(500) NULL");

        alterColumn("movie", "title", "NVARCHAR(255) NOT NULL");
        alterColumn("movie", "genre", "NVARCHAR(100) NULL");
        alterColumn("movie", "poster_url", "NVARCHAR(500) NULL");
        alterColumn("movie", "banner_url", "NVARCHAR(500) NULL");
        alterColumn("movie", "trailer_url", "NVARCHAR(500) NULL");
        alterColumn("movie", "description", "NVARCHAR(MAX) NULL");
        alterColumn("movie", "director", "NVARCHAR(255) NULL");
        alterColumn("movie", "movie_cast", "NVARCHAR(MAX) NULL");
        alterColumn("movie", "language", "NVARCHAR(100) NULL");
        alterColumn("movie", "age_rating", "NVARCHAR(50) NULL");
        alterColumn("movie", "status", "NVARCHAR(50) NULL");

        alterColumn("posts", "title", "NVARCHAR(255) NOT NULL");
        alterColumn("posts", "category", "NVARCHAR(100) NOT NULL");
        alterColumn("posts", "author", "NVARCHAR(100) NOT NULL");
        alterColumn("posts", "summary", "NVARCHAR(1000) NOT NULL");
        alterColumn("posts", "content", "NVARCHAR(MAX) NOT NULL");
        alterColumn("posts", "thumbnail", "NVARCHAR(500) NULL");
        alterColumn("posts", "tags", "NVARCHAR(500) NULL");
        alterColumn("posts", "status", "NVARCHAR(20) NOT NULL");

        alterColumn("combos", "name", "NVARCHAR(150) NOT NULL");
        alterColumn("combos", "description", "NVARCHAR(500) NULL");
        alterColumn("combos", "image", "NVARCHAR(255) NULL");
        alterColumn("combos", "status", "NVARCHAR(20) NOT NULL");

        alterColumn("food_items", "name", "NVARCHAR(150) NOT NULL");
        alterColumn("food_items", "category", "NVARCHAR(80) NOT NULL");
        alterColumn("food_items", "description", "NVARCHAR(255) NULL");
        alterColumn("food_items", "status", "NVARCHAR(20) NOT NULL");

        alterColumn("showtimes", "room", "NVARCHAR(100) NOT NULL");
        alterColumn("showtimes", "day_type", "NVARCHAR(20) NULL");

        alterColumn("banners", "title", "NVARCHAR(150) NOT NULL");
        alterColumn("banners", "subtitle", "NVARCHAR(500) NULL");
        alterColumn("banners", "image_url", "NVARCHAR(500) NOT NULL");
        alterColumn("banners", "link_url", "NVARCHAR(500) NULL");
        alterColumn("banners", "page", "NVARCHAR(20) NOT NULL");
    }

    private void normalizeVietnameseValues() {
        updateIfTableExists("rooms", """
                UPDATE rooms
                SET status = N'Hoạt động'
                WHERE status IN (N'Ho?t d?ng', N'Ho?t ??ng', N'Hoat dong', N'Hoáº¡t Ä‘á»™ng')
                """);
        updateIfTableExists("rooms", """
                UPDATE rooms
                SET status = N'Bảo trì'
                WHERE status IN (N'B?o tr?', N'Bao tri', N'Báº£o trÃ¬')
                """);
        updateIfTableExists("rooms", """
                UPDATE rooms
                SET status = N'Tạm ngưng'
                WHERE status IN (N'T?m ng?ng', N'Tam ngung', N'Táº¡m ngÆ°ng')
                """);
        updateIfTableExists("seat_types", """
                UPDATE seat_types SET display_name = N'Ghế thường'
                WHERE display_name IN (N'Gháº¿ thÆ°á»ng', N'Ghe thuong')
                """);
        updateIfTableExists("seat_types", """
                UPDATE seat_types SET display_name = N'Ghế hỏng'
                WHERE display_name IN (N'Gháº¿ há»ng', N'Ghe hong')
                """);
        updateIfTableExists("seat_types", """
                UPDATE seat_types SET display_name = N'Lối đi / Trống'
                WHERE display_name IN (N'Lá»‘i Ä‘i / Trá»‘ng', N'Loi di / Trong')
                """);
        updateIfTableExists("showtimes", """
                UPDATE showtimes SET day_type = N'Trong tuần'
                WHERE day_type IN (N'Trong tuan', N'Trong tuáº§n')
                """);
        updateIfTableExists("showtimes", """
                UPDATE showtimes SET day_type = N'Cuối tuần'
                WHERE day_type IN (N'Cuoi tuan', N'Cuá»‘i tuáº§n')
                """);
        updateIfTableExists("showtimes", """
                UPDATE showtimes SET day_type = N'Ngày lễ'
                WHERE day_type IN (N'Ngay le', N'NgÃ y lá»…')
                """);
    }

    private void alterColumn(String tableName, String columnName, String definition) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " " + definition);
        } catch (Exception e) {
            log.warn("Không thể chuyển {}.{} sang {}: {}", tableName, columnName, definition, e.getMessage());
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD " + columnName + " " + definition);
        } catch (Exception e) {
            log.warn("Không thể thêm cột {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private void updateIfTableExists(String tableName, String sql) {
        if (tableExists(tableName)) {
            jdbcTemplate.update(sql);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
