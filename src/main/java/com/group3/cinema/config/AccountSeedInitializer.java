/**
 * Lớp khởi tạo dữ liệu mặc định cho tài khoản người dùng (AccountSeedInitializer).
 * 
 * Tự động chạy khi ứng dụng khởi động (`@PostConstruct`):
 * - Kiểm tra và cập nhật cấu trúc bảng `account` (thêm cột `dob`, xóa cột `age` cũ).
 * - Khởi tạo các tài khoản mặc định (Admin, Manager, Customer) nếu chưa tồn tại trong CSDL.
 */
package com.group3.cinema.config;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.entity.Role;
import com.group3.cinema.repository.AccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountSeedInitializer {

    private final AccountRepository accountRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final boolean seedDefaultAccountsEnabled;

    /**
     * Constructor tiêm phụ thuộc AccountRepository và JdbcTemplate.
     * 
     * @param accountRepository Repository truy vấn thông tin tài khoản.
     * @param jdbcTemplate Công cụ thực thi SQL trực tiếp lên SQL Server.
     */
    public AccountSeedInitializer(AccountRepository accountRepository,
                                  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                  @Value("${app.seed.default-accounts:false}") boolean seedDefaultAccountsEnabled) {
        this.accountRepository = accountRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.seedDefaultAccountsEnabled = seedDefaultAccountsEnabled;
    }

    /**
     * Phương thức chính thực thi sau khi Spring Beans được khởi tạo thành công (`@PostConstruct`).
     * 
     * Quy trình:
     * 1. Gọi `ensureAccountSchema()` để đảm bảo cột `dob` tồn tại trong DB.
     * 2. Thực thi SQL xóa cột `age` cũ nếu còn ràng buộc constraint trên SQL Server.
     * 3. Gọi `createAccountIfMissing()` để seed 3 tài khoản mẫu:
     *    - Admin: `admin@group03.com`
     *    - Manager: `manager@group03.com`
     *    - Customer: `customer@group03.com`
     */
    @PostConstruct
    public void seedAccounts() {
        ensureAccountSchema();
        try {
            String sql = "DECLARE @ConstraintName nvarchar(200)\n" +
                         "SELECT @ConstraintName = Name FROM sys.default_constraints WHERE PARENT_OBJECT_ID = OBJECT_ID('account') AND PARENT_COLUMN_ID = (SELECT column_id FROM sys.columns WHERE NAME = N'age' AND object_id = OBJECT_ID(N'account'))\n" +
                         "IF @ConstraintName IS NOT NULL\n" +
                         "    EXEC('ALTER TABLE account DROP CONSTRAINT ' + @ConstraintName)\n" +
                         "SELECT @ConstraintName = Name FROM sys.check_constraints WHERE PARENT_OBJECT_ID = OBJECT_ID('account') AND parent_column_id = (SELECT column_id FROM sys.columns WHERE NAME = N'age' AND object_id = OBJECT_ID(N'account'))\n" +
                         "IF @ConstraintName IS NOT NULL\n" +
                         "    EXEC('ALTER TABLE account DROP CONSTRAINT ' + @ConstraintName)\n" +
                         "ALTER TABLE account DROP COLUMN age";
            jdbcTemplate.execute(sql);
            System.out.println("Đã tự động xóa cột age cũ trong bảng account thành công.");
        } catch (Exception e) {
            System.err.println("Không thể xóa cột age: " + e.getMessage());
        }
        if (!seedDefaultAccountsEnabled) {
            return;
        }
        createAccountIfMissing(
                "Admin",
                "admin@group03.com",
                "admin123",
                "0900000001",
                Role.ADMIN
        );
        createAccountIfMissing(
                "Manager",
                "manager@group03.com",
                "manager123",
                "0900000002",
                Role.MANAGER
        );
        createAccountIfMissing(
                "Nguyễn Văn Khách",
                "customer@group03.com",
                "customer123",
                "0900000003",
                Role.CUSTOMER
        );
    }

    /**
     * Tạo tài khoản mới nếu chưa tồn tại theo Email, hoặc cập nhật thông tin chuẩn hóa nếu tài khoản đã tồn tại.
     * 
     * Gọi các hàm:
     * - `accountRepository.findByEmail(email)`: Tìm tài khoản theo email.
     * - `accountRepository.findFirstByPhoneNum(phoneNum)`: Kiểm tra trùng số điện thoại.
     * - `accountRepository.save(account)`: Lưu thông tin tài khoản xuống DB.
     * 
     * @param name Tên người dùng.
     * @param email Địa chỉ Email.
     * @param password Mật khẩu gốc.
     * @param phoneNum Số điện thoại.
     * @param role Vai trò (ADMIN, MANAGER, CUSTOMER).
     */
    private void createAccountIfMissing(String name, String email, String password, String phoneNum, Role role) {
        Account account = accountRepository.findByEmail(email);
        if (account != null) {
            boolean changed = false;
            if (!name.equals(account.getName())) {
                account.setName(name);
                changed = true;
            }
            if (!password.equals(account.getPassword())) {
                account.setPassword(password);
                changed = true;
            }
            if (account.getRole() != role) {
                account.setRole(role);
                changed = true;
            }
            if (!account.isStatus()) {
                account.setStatus(true);
                changed = true;
            }
            if (account.getDob() == null) {
                account.setDob(java.time.LocalDate.of(2000, 1, 1));
                changed = true;
            }
            if (account.getMembershipLevel() == null) {
                account.setMembershipLevel(MembershipLevel.BRONZE);
                changed = true;
            }
            if (changed) {
                accountRepository.save(account);
            }
            return;
        }

        Account accountWithPhone = accountRepository.findFirstByPhoneNum(phoneNum).orElse(null);
        if (accountWithPhone != null) {
            System.err.println("Khong the tao tai khoan seed " + email + " vi so dien thoai " + phoneNum + " da ton tai.");
            return;
        }

        account = new Account();
        account.setName(name);
        account.setEmail(email);
        account.setPassword(password);
        account.setPhoneNum(phoneNum);
        account.setDob(java.time.LocalDate.of(2000, 1, 1));
        account.setGender("Nam");
        account.setAddress("CinemaBook");
        account.setLoyaltyPoint(0);
        account.setMembershipLevel(MembershipLevel.BRONZE);
        account.setStatus(true);
        account.setRole(role);

        accountRepository.save(account);
    }

    /**
     * Tự động kiểm tra và thêm cột `dob` (ngày sinh) cho bảng `account` nếu CSDL chưa có.
     * Gọi hàm `tableExists()` và `columnExists()` để truy vấn `INFORMATION_SCHEMA`.
     */
    private void ensureAccountSchema() {
        try {
            if (!tableExists("account") || columnExists("account", "dob")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE account ADD dob DATE NULL");
            System.out.println("Da tu dong them cot dob cho bang account.");
        } catch (Exception e) {
            System.err.println("Khong the them cot dob cho bang account: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra xem một bảng có tồn tại trong CSDL SQL Server hay không.
     * 
     * @param tableName Tên bảng cần kiểm tra.
     * @return true nếu tồn tại, false nếu chưa.
     */
    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * Kiểm tra xem một cột có tồn tại trong một bảng cụ thể của CSDL hay không.
     * 
     * @param tableName Tên bảng.
     * @param columnName Tên cột.
     * @return true nếu cột tồn tại, false nếu chưa.
     */
    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}

