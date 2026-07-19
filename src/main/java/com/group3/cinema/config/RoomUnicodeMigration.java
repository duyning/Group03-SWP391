/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 * Updated on 2026-06-23 by: TrienLX
 * Chi tiết thay đổi:
 * - [SỬA - TrienLX - 2026-06-23] Thêm bước fixMovieStatusConstraint() để xóa constraint cũ
 *   CK__movie__status__534D60F1 (chỉ cho phép NOW_SHOWING, COMING_SOON, SPECIAL_SCREENING)
 *   và tạo lại constraint mới bao gồm cả giá trị STOPPED.
 *   Lý do: Hibernate tự sinh constraint khi khởi tạo schema từ enum cũ (chỉ 3 giá trị).
 *   Khi thêm STOPPED vào enum Java mà không cập nhật constraint DB sẽ gây lỗi
 *   DataIntegrityViolationException mỗi khi scheduler tự động ẩn phim hết hạn.
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
        runMigrationStep("create banner table", this::createBannerTableIfMissing);
        runMigrationStep("add account dob column", this::addAccountDobColumnIfMissing);
        runMigrationStep("create account voucher wallet table", this::createAccountVoucherTableIfMissing);
        runMigrationStep("add movie columns", this::addMovieColumnsIfMissing);
        runMigrationStep("create combo catalog tables", this::createComboCatalogTablesIfMissing);
        runMigrationStep("migrate combo pricing columns", this::migrateComboPricingColumns);
        runMigrationStep("seed default food items", this::seedDefaultFoodItems);
        runMigrationStep("add showtime override column", this::addShowtimeOverrideColumnIfMissing);
        runMigrationStep("add showtime active column", this::addShowtimeActiveColumnIfMissing);
        runMigrationStep("add ticket base price column", this::addTicketBasePriceColumnIfMissing);
        runMigrationStep("migrate ticket columns", this::migrateTicketColumns);
        runMigrationStep("migrate ticket pricing config columns", this::migrateTicketPricingConfigColumns);
        runMigrationStep("cleanup ticket pricing unique constraints", this::cleanupTicketPricingUniqueConstraints);
        runMigrationStep("migrate text columns", this::migrateTextColumns);
        runMigrationStep("normalize Vietnamese values", this::normalizeVietnameseValues);
        runMigrationStep("fix movie status constraint", this::fixMovieStatusConstraint);
        runMigrationStep("fix membership level constraint", this::fixMembershipLevelConstraint);
        runMigrationStep("seed pricing configs", this::seedPricingConfigs);
    }

    private void runMigrationStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.warn("Không thể chạy migration '{}': {}", stepName, e.getMessage());
        }
    }

    /**
     * [SỬA - TrienLX - 2026-06-25]
     * Thêm cột base_price vào bảng tickets nếu chưa tồn tại.
     * Cột này lưu giá gốc (Adult, trước chiết khấu) để tính lại giá đúng khi sửa đối tượng khách hàng.
     */
    private void addTicketBasePriceColumnIfMissing() {
        if (!tableExists("tickets")) return;
        if (!columnExists("tickets", "base_price")) {
            try {
                jdbcTemplate.execute("ALTER TABLE tickets ADD base_price FLOAT NOT NULL DEFAULT 0.0");
                // Đồng bộ base_price = price cho vé hiện có (trường hợp vé gốc chưa có chiết khấu)
                jdbcTemplate.execute("UPDATE tickets SET base_price = price WHERE base_price = 0.0");
                log.info("[TrienLX - 2026-06-25] Đã bổ sung cột base_price cho bảng tickets");
            } catch (Exception e) {
                log.warn("[TrienLX - 2026-06-25] Không thể bổ sung cột base_price: {}", e.getMessage());
            }
        }
    }

    private void migrateTicketColumns() {
        if (!tableExists("tickets")) {
            return;
        }

        addColumnIfMissing("tickets", "showtime_id", "BIGINT NULL");
        addColumnIfMissing("tickets", "seat_id", "BIGINT NULL");
        addColumnIfMissing("tickets", "seat_number", "NVARCHAR(20) NULL");
        addColumnIfMissing("tickets", "customer_type", "NVARCHAR(30) NULL");
        addColumnIfMissing("tickets", "deleted", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("tickets", "seat_surcharge", "FLOAT NOT NULL DEFAULT 0.0");
        addColumnIfMissing("tickets", "format_surcharge", "FLOAT NOT NULL DEFAULT 0.0");
        addColumnIfMissing("tickets", "discount_amount", "FLOAT NOT NULL DEFAULT 0.0");
        addColumnIfMissing("tickets", "final_price", "FLOAT NOT NULL DEFAULT 0.0");
        addColumnIfMissing("tickets", "created_at", "DATETIME2 NULL");
        addColumnIfMissing("tickets", "customer_name", "NVARCHAR(255) NULL");
        addColumnIfMissing("tickets", "customer_phone", "NVARCHAR(50) NULL");

        alterColumn("tickets", "room_name", "NVARCHAR(100) NULL");
        alterColumn("tickets", "seat_label", "NVARCHAR(20) NULL");
        alterColumn("tickets", "seat_number", "NVARCHAR(20) NULL");
        alterColumn("tickets", "seat_type", "NVARCHAR(30) NULL");
        alterColumn("tickets", "status", "NVARCHAR(20) NOT NULL");
        alterColumn("tickets", "payment_method", "NVARCHAR(50) NULL");
        alterColumn("tickets", "booking_code", "NVARCHAR(50) NULL");
        alterColumn("tickets", "customer_type", "NVARCHAR(30) NULL");

        try {
            if (columnExists("tickets", "account_id")) {
                jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN account_id INT NULL");
            }
            if (columnExists("tickets", "movie_id")) {
                jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN movie_id INT NULL");
            }
            if (columnExists("tickets", "show_date")) {
                jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN show_date DATE NULL");
            }
            if (columnExists("tickets", "show_time")) {
                jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN show_time TIME NULL");
            }
            if (columnExists("tickets", "booking_time")) {
                jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN booking_time DATETIME2 NULL");
            }
        } catch (Exception e) {
            log.warn("Không thể nới lỏng ràng buộc null cho bảng tickets: {}", e.getMessage());
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
        addColumnIfMissing("movie", "deleted", "BIT NOT NULL DEFAULT 0");
    }

    private void addAccountDobColumnIfMissing() {
        addColumnIfMissing("account", "dob", "DATE NULL");
        updateIfTableExists("account", "UPDATE account SET dob = '2000-01-01' WHERE dob IS NULL");
    }

    private void createAccountVoucherTableIfMissing() {
        if (!tableExists("account") || !tableExists("vouchers")) {
            return;
        }

        if (!tableExists("account_vouchers")) {
            jdbcTemplate.execute("""
                    CREATE TABLE account_vouchers (
                        account_id INT NOT NULL,
                        voucher_id BIGINT NOT NULL,
                        CONSTRAINT PK_account_vouchers PRIMARY KEY (account_id, voucher_id),
                        CONSTRAINT FK_account_vouchers_account FOREIGN KEY (account_id) REFERENCES account(accountid),
                        CONSTRAINT FK_account_vouchers_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
                    )
                    """);
            return;
        }

        addColumnIfMissing("account_vouchers", "account_id", "INT NULL");
        addColumnIfMissing("account_vouchers", "voucher_id", "BIGINT NULL");
        try {
            jdbcTemplate.execute("""
                    IF NOT EXISTS (
                        SELECT 1
                        FROM sys.indexes
                        WHERE name = 'UX_account_vouchers_account_voucher'
                          AND object_id = OBJECT_ID('account_vouchers')
                    )
                    CREATE UNIQUE INDEX UX_account_vouchers_account_voucher
                    ON account_vouchers(account_id, voucher_id)
                    """);
        } catch (Exception e) {
            log.warn("Không thể tạo unique index cho account_vouchers: {}", e.getMessage());
        }
    }

    /**
     * [SỬA - TrienLX - 2026-06-23]
     * Thêm cột is_override vào bảng showtimes nếu chưa tồn tại.
     * Cột này dùng để đánh dấu các suất chiếu đã được Admin điều chỉnh riêng
     * so với lịch gốc (isOverride = true), giúp phân biệt trực quan trên giao diện
     * và tránh ghi đè khi cập nhật nhóm lịch chiếu.
     * DEFAULT = 0 (false) để tất cả suất chiếu cũ mặc định là "chưa điều chỉnh".
     */
    private void addShowtimeOverrideColumnIfMissing() {
        if (!tableExists("showtimes")) {
            return;
        }
        if (!columnExists("showtimes", "is_override")) {
            try {
                jdbcTemplate.execute("ALTER TABLE showtimes ADD is_override BIT NOT NULL DEFAULT 0");
                log.info("[TrienLX - 2026-06-23] Đã bổ sung cột is_override cho bảng showtimes");
            } catch (Exception e) {
                log.warn("[TrienLX - 2026-06-23] Không thể bổ sung cột is_override: {}", e.getMessage());
            }
        }
    }

    private void addShowtimeActiveColumnIfMissing() {
        addColumnIfMissing("showtimes", "active", "BIT NOT NULL DEFAULT 1");
    }

    private void migrateTicketPricingConfigColumns() {
        addColumnIfMissing("ticket_price_configs", "movie_id", "BIGINT NULL");
        addColumnIfMissing("ticket_price_configs", "note", "NVARCHAR(100) NULL");
        addColumnIfMissing("customer_discounts", "min_price_to_apply", "FLOAT NOT NULL DEFAULT 0.0");
        addColumnIfMissing("customer_discounts", "max_discount_amount", "FLOAT NOT NULL DEFAULT 999999.0");
    }

    private void cleanupTicketPricingUniqueConstraints() {
        if (!tableExists("ticket_price_configs")) {
            return;
        }

        try {
            jdbcTemplate.execute("""
                    DECLARE @constraintName NVARCHAR(256)
                    SELECT @constraintName = name
                    FROM sys.key_constraints
                    WHERE parent_object_id = OBJECT_ID('ticket_price_configs') AND type = 'UQ'
                    IF @constraintName IS NOT NULL
                        EXEC('ALTER TABLE ticket_price_configs DROP CONSTRAINT [' + @constraintName + ']')
                    """);
            jdbcTemplate.execute("""
                    DECLARE @indexName NVARCHAR(256)
                    SELECT @indexName = name
                    FROM sys.indexes
                    WHERE object_id = OBJECT_ID('ticket_price_configs')
                      AND is_unique = 1
                      AND is_primary_key = 0
                    IF @indexName IS NOT NULL
                        EXEC('DROP INDEX [' + @indexName + '] ON ticket_price_configs')
                    """);
        } catch (Exception e) {
            log.warn("Không thể xóa unique constraint/index trên ticket_price_configs: {}", e.getMessage());
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

        migrateColumnToNvarcharWithConstraints("room_types", "name", "NVARCHAR(50) NOT NULL", "UQ_room_types_name");
        alterColumn("room_types", "description", "NVARCHAR(255) NULL");
        migrateColumnToNvarcharWithConstraints("audio_technologies", "name", "NVARCHAR(80) NOT NULL", "UQ_audio_technologies_name");
        alterColumn("audio_technologies", "description", "NVARCHAR(255) NULL");
        migrateColumnToNvarcharWithConstraints("seat_types", "code", "NVARCHAR(20) NOT NULL", "UQ_seat_types_code");
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

        alterColumn("promotions", "title", "NVARCHAR(180) NOT NULL");
        alterColumn("promotions", "type", "NVARCHAR(40) NOT NULL");
        alterColumn("promotions", "target_group", "NVARCHAR(40) NOT NULL");
        alterColumn("promotions", "discount_rule", "NVARCHAR(1000) NOT NULL");
        alterColumn("promotions", "description", "NVARCHAR(1200) NOT NULL");
        alterColumn("promotions", "condition_text", "NVARCHAR(1200) NOT NULL");
        alterColumn("promotions", "how_to_join", "NVARCHAR(1000) NOT NULL");
        alterColumn("promotions", "banner_image", "NVARCHAR(500) NULL");
        alterColumn("promotions", "status", "NVARCHAR(20) NOT NULL");

        alterColumn("ticket_price_configs", "day_type", "NVARCHAR(30) NOT NULL");
        alterColumn("ticket_price_configs", "slot_name", "NVARCHAR(30) NOT NULL");
        alterColumn("ticket_price_configs", "note", "NVARCHAR(100) NULL");
        migrateColumnToNvarcharWithConstraints("customer_discounts", "customer_type", "NVARCHAR(50) NOT NULL", "UQ_customer_discounts_type");
        migrateColumnToNvarcharWithConstraints("seat_type_surcharges", "seat_type_code", "NVARCHAR(30) NOT NULL", "UQ_seat_type_surcharges_code");
        migrateColumnToNvarcharWithConstraints("format_surcharges", "format_code", "NVARCHAR(30) NOT NULL", "UQ_format_surcharges_code");
    }

    /**
     * [SỬA - TrienLX - 2026-06-23]
     * Xóa constraint CHECK cũ trên cột movie.status (do Hibernate tự sinh khi khởi tạo
     * schema lần đầu với enum chỉ có 3 giá trị: NOW_SHOWING, COMING_SOON, SPECIAL_SCREENING)
     * rồi tạo lại constraint mới có đầy đủ 4 giá trị bao gồm cả STOPPED.
     *
     * Nếu constraint cũ không tồn tại (đã bị xóa hoặc chưa bao giờ được tạo),
     * bước này sẽ bỏ qua mà không gây lỗi.
     * Nếu constraint mới đã tồn tại, bước tạo mới cũng sẽ bỏ qua.
     */
    private void fixMovieStatusConstraint() {
        if (!tableExists("movie")) {
            return;
        }
        try {
            // Kiểm tra xem constraint cũ có tồn tại không trước khi xóa
            Integer oldConstraintCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'CK__movie__status__534D60F1'",
                    Integer.class
            );
            if (oldConstraintCount != null && oldConstraintCount > 0) {
                jdbcTemplate.execute("ALTER TABLE movie DROP CONSTRAINT CK__movie__status__534D60F1");
                log.info("[TrienLX - 2026-06-23] Đã xóa constraint cũ CK__movie__status__534D60F1 khỏi bảng movie");
            }
        } catch (Exception e) {
            log.warn("[TrienLX - 2026-06-23] Không thể xóa constraint cũ CK__movie__status__534D60F1: {}", e.getMessage());
        }

        try {
            // Xóa toàn bộ constraint CHECK trên cột status của bảng movie
            // (trường hợp constraint có tên khác do tên DB khác nhau giữa các môi trường)
            String dropAllStatusConstraints = """
                    DECLARE @constraintName NVARCHAR(256)
                    SELECT @constraintName = name
                    FROM sys.check_constraints cc
                    JOIN sys.columns c ON cc.parent_object_id = c.object_id AND cc.parent_column_id = c.column_id
                    JOIN sys.tables t ON c.object_id = t.object_id
                    WHERE t.name = 'movie' AND c.name = 'status'
                    IF @constraintName IS NOT NULL
                        EXEC('ALTER TABLE movie DROP CONSTRAINT [' + @constraintName + ']')
                    """;
            jdbcTemplate.execute(dropAllStatusConstraints);
            log.info("[TrienLX - 2026-06-23] Đã xóa tất cả CHECK constraint trên cột movie.status (nếu có)");
        } catch (Exception e) {
            log.warn("[TrienLX - 2026-06-23] Không thể xóa CHECK constraint trên cột movie.status: {}", e.getMessage());
        }

        try {
            // Kiểm tra xem constraint mới đã tồn tại chưa
            Integer newConstraintCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'CK_movie_status_allowed'",
                    Integer.class
            );
            if (newConstraintCount == null || newConstraintCount == 0) {
                // Tạo constraint mới với đầy đủ 4 giá trị enum bao gồm STOPPED
                jdbcTemplate.execute(
                        "ALTER TABLE movie ADD CONSTRAINT CK_movie_status_allowed "
                        + "CHECK (status IN ('NOW_SHOWING', 'COMING_SOON', 'SPECIAL_SCREENING', 'STOPPED'))"
                );
                log.info("[TrienLX - 2026-06-23] Đã tạo constraint mới CK_movie_status_allowed cho bảng movie với đầy đủ 4 giá trị enum");
            } else {
                log.info("[TrienLX - 2026-06-23] Constraint CK_movie_status_allowed đã tồn tại, bỏ qua bước tạo mới");
            }
        } catch (Exception e) {
            log.warn("[TrienLX - 2026-06-23] Không thể tạo constraint mới CK_movie_status_allowed: {}", e.getMessage());
        }
    }

    private void fixMembershipLevelConstraint() {
        if (!tableExists("account")) {
            return;
        }
        try {
            // Kiểm tra xem constraint cũ CK__account__members__4BAC3F29 có tồn tại không trước khi xóa
            Integer oldConstraintCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'CK__account__members__4BAC3F29'",
                    Integer.class
            );
            if (oldConstraintCount != null && oldConstraintCount > 0) {
                jdbcTemplate.execute("ALTER TABLE account DROP CONSTRAINT CK__account__members__4BAC3F29");
                log.info("Đã xóa constraint cũ CK__account__members__4BAC3F29 khỏi bảng account");
            }
        } catch (Exception e) {
            log.warn("Không thể xóa constraint cũ CK__account__members__4BAC3F29: {}", e.getMessage());
        }

        try {
            // Xóa toàn bộ constraint CHECK trên cột membership_level của bảng account
            String dropAllConstraints = """
                    DECLARE @constraintName NVARCHAR(256)
                    SELECT @constraintName = name
                    FROM sys.check_constraints cc
                    JOIN sys.columns c ON cc.parent_object_id = c.object_id AND cc.parent_column_id = c.column_id
                    JOIN sys.tables t ON c.object_id = t.object_id
                    WHERE t.name = 'account' AND c.name = 'membership_level'
                    IF @constraintName IS NOT NULL
                        EXEC('ALTER TABLE account DROP CONSTRAINT [' + @constraintName + ']')
                    """;
            jdbcTemplate.execute(dropAllConstraints);
            log.info("Đã xóa tất cả CHECK constraint trên cột account.membership_level (nếu có)");
        } catch (Exception e) {
            log.warn("Không thể xóa CHECK constraint trên cột account.membership_level: {}", e.getMessage());
        }

        try {
            // Kiểm tra xem constraint mới đã tồn tại chưa
            Integer newConstraintCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'CK_account_membership_level_allowed'",
                    Integer.class
            );
            if (newConstraintCount == null || newConstraintCount == 0) {
                // Tạo constraint mới với đầy đủ 4 giá trị bao gồm BRONZE
                jdbcTemplate.execute(
                        "ALTER TABLE account ADD CONSTRAINT CK_account_membership_level_allowed "
                        + "CHECK (membership_level IN ('BRONZE', 'SILVER', 'GOLD', 'PLAT'))"
                );
                log.info("Đã tạo constraint mới CK_account_membership_level_allowed cho bảng account");
            }
        } catch (Exception e) {
            log.warn("Không thể tạo constraint mới CK_account_membership_level_allowed: {}", e.getMessage());
        }
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

    /**
     * [THÊM - TrienLX - 2026-06-25]
     * Nạp dữ liệu cấu hình mặc định cho ma trận tính giá vé (Base price, phụ thu, chiết khấu).
     */
    private void seedPricingConfigs() {
        try {
            if (tableExists("ticket_price_configs")) {
                jdbcTemplate.execute("DELETE FROM ticket_price_configs WHERE CHARINDEX('?', day_type) > 0 OR CHARINDEX('?', slot_name) > 0");
            }
            if (tableExists("customer_discounts")) {
                jdbcTemplate.execute("DELETE FROM customer_discounts WHERE CHARINDEX('?', customer_type) > 0");
            }
            if (tableExists("seat_type_surcharges")) {
                jdbcTemplate.execute("DELETE FROM seat_type_surcharges WHERE CHARINDEX('?', seat_type_code) > 0");
            }
            if (tableExists("format_surcharges")) {
                jdbcTemplate.execute("DELETE FROM format_surcharges WHERE CHARINDEX('?', format_code) > 0");
            }

            // 1. Seed ticket_price_configs
            if (tableExists("ticket_price_configs")) {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_price_configs", Integer.class);
                if (count == null || count == 0) {
                    // Trong tuần
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Trong tuần', N'Suất sớm', '00:00:00', '11:59:59', 50000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Trong tuần', N'Giờ thường', '12:00:00', '16:59:59', 60000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Trong tuần', N'Giờ vàng', '17:00:00', '21:59:59', 75000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Trong tuần', N'Suất khuya', '22:00:00', '23:59:59', 65000.0)");

                    // Cuối tuần
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Cuối tuần', N'Suất sớm', '00:00:00', '11:59:59', 65000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Cuối tuần', N'Giờ thường', '12:00:00', '16:59:59', 80000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Cuối tuần', N'Giờ vàng', '17:00:00', '21:59:59', 95000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Cuối tuần', N'Suất khuya', '22:00:00', '23:59:59', 85000.0)");

                    // Ngày lễ
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Ngày lễ', N'Suất sớm', '00:00:00', '11:59:59', 80000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Ngày lễ', N'Giờ thường', '12:00:00', '16:59:59', 95000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Ngày lễ', N'Giờ vàng', '17:00:00', '21:59:59', 110000.0)");
                    jdbcTemplate.execute("INSERT INTO ticket_price_configs (day_type, slot_name, start_time, end_time, base_price) VALUES (N'Ngày lễ', N'Suất khuya', '22:00:00', '23:59:59', 100000.0)");
                    log.info("[TrienLX - 2026-06-25] Đã seed dữ liệu mẫu cho ticket_price_configs");
                }
            }

            // 2. Seed seat_type_surcharges
            if (tableExists("seat_type_surcharges")) {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seat_type_surcharges", Integer.class);
                if (count == null || count == 0) {
                    jdbcTemplate.execute("INSERT INTO seat_type_surcharges (seat_type_code, surcharge_amount) VALUES ('std', 0.0)");
                    jdbcTemplate.execute("INSERT INTO seat_type_surcharges (seat_type_code, surcharge_amount) VALUES ('vip', 15000.0)");
                    jdbcTemplate.execute("INSERT INTO seat_type_surcharges (seat_type_code, surcharge_amount) VALUES ('couple', 30000.0)");
                    log.info("[TrienLX - 2026-06-25] Đã seed dữ liệu mẫu cho seat_type_surcharges");
                }
            }

            // 3. Seed format_surcharges
            if (tableExists("format_surcharges")) {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM format_surcharges", Integer.class);
                if (count == null || count == 0) {
                    jdbcTemplate.execute("INSERT INTO format_surcharges (format_code, surcharge_amount) VALUES ('2D', 0.0)");
                    jdbcTemplate.execute("INSERT INTO format_surcharges (format_code, surcharge_amount) VALUES ('3D', 25000.0)");
                    jdbcTemplate.execute("INSERT INTO format_surcharges (format_code, surcharge_amount) VALUES ('IMAX', 80000.0)");
                    jdbcTemplate.execute("INSERT INTO format_surcharges (format_code, surcharge_amount) VALUES ('Premium', 50000.0)");
                    log.info("[TrienLX - 2026-06-25] Đã seed dữ liệu mẫu cho format_surcharges");
                }
            }

            // 4. Seed customer_discounts
            if (tableExists("customer_discounts")) {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_discounts", Integer.class);
                if (count == null || count == 0) {
                    jdbcTemplate.execute("INSERT INTO customer_discounts (customer_type, discount_rate, fixed_price_weekday) VALUES ('ADULT', 0.0, NULL)");
                    jdbcTemplate.execute("INSERT INTO customer_discounts (customer_type, discount_rate, fixed_price_weekday) VALUES ('STUDENT', 0.20, 55000.0)");
                    jdbcTemplate.execute("INSERT INTO customer_discounts (customer_type, discount_rate, fixed_price_weekday) VALUES ('CHILD', 0.30, 45000.0)");
                    jdbcTemplate.execute("INSERT INTO customer_discounts (customer_type, discount_rate, fixed_price_weekday) VALUES ('ELDERLY', 0.30, NULL)");
                    log.info("[TrienLX - 2026-06-25] Đã seed dữ liệu mẫu cho customer_discounts");
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi nạp seed data cho ma trận giá vé: {}", e.getMessage());
        }
    }

    private void migrateColumnToNvarcharWithConstraints(String tableName, String columnName, String definition, String uniqueConstraintName) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return;
        }
        try {
            // Drop unique constraints
            java.util.List<String> constraints = jdbcTemplate.query(
                "SELECT name FROM sys.objects WHERE (type = 'UQ' OR type = 'F' OR type = 'PK') AND parent_object_id = OBJECT_ID('" + tableName + "')",
                (rs, rowNum) -> rs.getString("name")
            );
            for (String cn : constraints) {
                try {
                    jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT [" + cn + "]");
                    log.info("Dropped constraint {} on table {}", cn, tableName);
                } catch (Exception ignored) {}
            }

            // Drop unique indexes
            java.util.List<String> indexes = jdbcTemplate.query(
                "SELECT name FROM sys.indexes WHERE object_id = OBJECT_ID('" + tableName + "') AND is_unique = 1 AND name NOT LIKE 'PK_%'",
                (rs, rowNum) -> rs.getString("name")
            );
            for (String idx : indexes) {
                try {
                    jdbcTemplate.execute("DROP INDEX [" + idx + "] ON " + tableName);
                    log.info("Dropped index {} on table {}", idx, tableName);
                } catch (Exception ignored) {}
            }

            // Alter column
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " " + definition);
            log.info("Successfully altered column {}.{} to {}", tableName, columnName, definition);

            // Recreate unique constraint
            try {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD CONSTRAINT [" + uniqueConstraintName + "] UNIQUE (" + columnName + ")");
                log.info("Re-created unique constraint {} on {}({})", uniqueConstraintName, tableName, columnName);
            } catch (Exception e) {
                log.warn("Could not re-create unique constraint {} on {}: {}", uniqueConstraintName, tableName, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to migrate column {}.{} to NVARCHAR: {}", tableName, columnName, e.getMessage());
        }
    }
}
