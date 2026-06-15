package com.group3.cinema.config;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.entity.Role;
import com.group3.cinema.repository.AccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AccountSeedInitializer {

    private final AccountRepository accountRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public AccountSeedInitializer(AccountRepository accountRepository, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.accountRepository = accountRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void seedAccounts() {
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
    }

    private void createAccountIfMissing(String name, String email, String password, String phoneNum, Role role) {
        if (accountRepository.existsByEmail(email) || accountRepository.existsByPhoneNum(phoneNum)) {
            return;
        }

        Account account = new Account();
        account.setName(name);
        account.setEmail(email);
        account.setPassword(password);
        account.setPhoneNum(phoneNum);
        account.setDob(java.time.LocalDate.of(2000, 1, 1));
        account.setGender("Nam");
        account.setAddress("CinemaBook");
        account.setLoyaltyPoint(0);
        account.setMembershipLevel(MembershipLevel.SILVER);
        account.setStatus(true);
        account.setRole(role);

        accountRepository.save(account);
    }
}
