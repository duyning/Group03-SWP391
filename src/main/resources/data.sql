-- Updated on 2026-06-04: Added project file ownership metadata.
-- Created by: NinhDD - HE186113
-- ================================================
-- SEED DATA - chạy mỗi lần khởi động nhưng an toàn
-- Dùng IF NOT EXISTS để không insert trùng dữ liệu
-- Password hash = "admin123" (BCrypt)
-- ================================================

-- Tạo tài khoản ADMIN
IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin')
    INSERT INTO users (username, email, password, full_name, role, enabled)
    VALUES ('admin', 'admin@group03.com',
            '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFkFEMfBJw/u62LVBrgDNe2',
            'Administrator', 'ADMIN', 1);

-- Tạo tài khoản USER mẫu 1
IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'user1')
    INSERT INTO users (username, email, password, full_name, role, enabled)
    VALUES ('user1', 'user1@group03.com',
            '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFkFEMfBJw/u62LVBrgDNe2',
            'Nguyen Van A', 'USER', 1);

-- Ghi chú: Password mặc định là "admin123"
-- Hash được tạo bởi: new BCryptPasswordEncoder().encode("admin123")

