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

-- Thêm cột format vào bảng movie nếu chưa tồn tại.
-- Dùng COL_LENGTH + EXEC để Spring SQL init không bị split sai trên dấu ; bên trong BEGIN...END
IF COL_LENGTH('[dbo].[movie]', 'format') IS NULL EXEC('ALTER TABLE [dbo].[movie] ADD [format] NVARCHAR(50) NULL');

-- Tạo bảng movie_person_suggestions nếu chưa tồn tại
IF OBJECT_ID(N'[dbo].[movie_person_suggestions]', N'U') IS NULL EXEC('CREATE TABLE [dbo].[movie_person_suggestions] ([id] INT IDENTITY(1,1) PRIMARY KEY, [name] NVARCHAR(255) NOT NULL, [type] NVARCHAR(50) NOT NULL, CONSTRAINT [UC_MoviePersonSuggestion] UNIQUE ([name], [type]))');
