package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.entity.Role;
import com.group3.cinema.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

/**
 * Lớp dịch vụ (Service) xử lý các nghiệp vụ logic liên quan đến tài khoản (Account).
 * Bao gồm đăng ký, đăng nhập, tìm kiếm tài khoản, đổi mật khẩu và xử lý gửi OTP xác thực.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Service
public class AccountService {

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    /**
     * Register a new customer account.
     * Sets default values for role, status, membership level, and loyalty points.
     *
     * @param account the account to register
     * @return the saved account
     */
    public Account register(Account account) {
        account.setRole(Role.CUSTOMER);
        account.setStatus(true);
        account.setMembershipLevel(MembershipLevel.BRONZE);
        account.setLoyaltyPoint(0);
        return accountRepository.save(account);
    }

    /**
     * Create a new MANAGER account (Used by Admin).
     */
    public Account createManagerAccount(Account account) {
        // Bat buoc role la MANAGER, khong tin role gui tu form.
        account.setRole(Role.MANAGER);
        // Manager moi duoc kich hoat de co the dang nhap ngay.
        account.setStatus(true);
        // Manager khong tham gia chuong trinh hang thanh vien customer.
        account.setMembershipLevel(null);
        // Khoi tao diem ve 0.
        account.setLoyaltyPoint(0);
        // INSERT Account va tra ve entity da duoc gan ID.
        return accountRepository.save(account);
    }

    /**
     * Authenticate a user by email and password.
     * Returns the Account if credentials match, null otherwise.
     *
     * @param email    the email to look up
     * @param password the plain-text password to verify
     * @return the matching Account, or null if not found / wrong password
     */
    public Account login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedPassword = password == null ? "" : password.trim();
        // Dùng findByEmailWithVouchers để load savedVouchers eager (JOIN FETCH),
        // tránh LazyInitializationException khi Account được lưu vào HTTP session.
        Account account = accountRepository.findByEmailWithVouchers(normalizedEmail);
        if (account != null && account.getPassword().equals(normalizedPassword)) {
            return account;
        }
        return null;
    }

    /**
     * Check if an email already exists in the database.
     */
    public boolean isEmailExist(String email) {
        return accountRepository.existsByEmail(email);
    }

    /**
     * Check if a phone number already exists in the database.
     */
    public boolean isPhoneNumExist(String phoneNum) {
        return accountRepository.existsByPhoneNum(phoneNum);
    }

    /**
     * Find an account by email.
     */
    public Account findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    /**
     * Find an account by ID.
     */
    public Account findById(int id) {
        // JpaRepository tra Optional; orElse(null) giup controller kiem tra bang null.
        return accountRepository.findById(id).orElse(null);
    }

    /**
     * Lấy tất cả tài khoản, sắp xếp theo tên (Admin dùng).
     */
    public java.util.List<Account> getAllAccounts() {
        // Sort.by("name") yeu cau database sap xep tang dan theo name.
        return accountRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    /**
     * Vô hiệu hóa hoặc kích hoạt tài khoản theo ID.
     * Trả về tài khoản đã cập nhật, hoặc null nếu không tìm thấy.
     *
     * @param targetId  ID tài khoản cần thay đổi trạng thái
     * @param adminId   ID admin đang thực hiện thao tác (để ngăn tự vô hiệu hóa chính mình)
     */
    public Account toggleAccountStatus(int targetId, int adminId) {
        // Ngan admin tu khoa chinh tai khoan dang dang nhap.
        if (targetId == adminId) {
            throw new IllegalArgumentException("Bạn không thể vô hiệu hóa chính tài khoản của mình.");
        }
        // Tim tai khoan muc tieu theo primary key.
        Account account = accountRepository.findById(targetId).orElse(null);
        // Bao loi nghiep vu neu URL chua ID khong ton tai.
        if (account == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        }
        // Không cho phép vô hiệu hóa ADMIN khác
        if (account.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Không thể thay đổi trạng thái tài khoản Admin khác.");
        }
        // Dao true thanh false hoac false thanh true.
        account.setStatus(!account.isStatus());
        // UPDATE account va tra ve trang thai sau cap nhat.
        return accountRepository.save(account);
    }

    /**
     * Cập nhật thông tin hồ sơ cá nhân.
     */
    public void updateProfile(Account account, String name, java.time.LocalDate dob, String gender, String address, String phoneNum) {
        // Ghi ten da duoc controller validate vao entity.
        account.setName(name);
        // Ghi ngay sinh.
        account.setDob(dob);
        // Ghi gioi tinh.
        account.setGender(gender);
        // Ghi dia chi.
        account.setAddress(address);
        // Khong ghi de so dien thoai cu neu field moi null/rong.
        if (phoneNum != null && !phoneNum.trim().isEmpty()) {
            // trim bo khoang trang hai dau truoc khi luu.
            account.setPhoneNum(phoneNum.trim());
        }
        // UPDATE ban ghi account trong database.
        accountRepository.save(account);
    }

    /**
     * Kiểm tra số điện thoại đã tồn tại trong DB hay chưa (trừ chính tài khoản hiện tại).
     */
    public boolean isPhoneNumTakenByOther(String phoneNum, Integer accountId) {
        // Tim so dien thoai trung nhung loai tru accountID hien tai.
        return accountRepository.existsByPhoneNumAndAccountIDNot(phoneNum, accountId);
    }

    /**
     * Reset (change) the password for a given account.
     * Validates all 6 cases and throws IllegalArgumentException with message on failure.
     *
     * @param account         the account whose password will be changed
     * @param oldPassword     the current password entered by the user
     * @param newPassword     the new password entered by the user
     * @param confirmPassword the confirmation of the new password
     */
    public void resetPassword(Account account, String oldPassword, String newPassword, String confirmPassword) {

        // Case 5: Empty / null fields
        // Kiem tra tung field rieng de controller gan loi vao dung input.
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu cũ không được để trống");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu mới không được để trống");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("Xác nhận mật khẩu không được để trống");
        }

        // Case 1: Old password incorrect
        // So sanh mat khau hien tai trong Entity voi gia tri user nhap.
        if (!account.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        // Case 4: New password same as old
        // Mat khau moi phai khac mat khau dang su dung.
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        // Case 2: New password length invalid (must be 8-20 characters)
        // Gioi han do dai mat khau moi tu 8 den 20 ky tu.
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("New password must be 8-20 characters");
        }

        // Case 3: Confirm password mismatch
        // Confirm phai trung chinh xac voi mat khau moi.
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Confirm password does not match");
        }

        // Case 5: All validations passed — update password
        // Sau khi tat ca quy tac dat, thay password tren entity.
        account.setPassword(newPassword);
        // UPDATE password xuong database.
        accountRepository.save(account);
    }

    /**
     * Update password for forgot password flow (no old password check).
     */
    public void updatePassword(Account account, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu mới không được để trống");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("Xác nhận mật khẩu không được để trống");
        }
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("Mật khẩu mới phải từ 8 đến 20 ký tự");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp");
        }

        account.setPassword(newPassword);
        accountRepository.save(account);
    }

    /**
     * Generate a 6-digit OTP and send it via email for forgot password flow.
     */
    public String generateAndSendOTP(String email) {
        return generateAndSendOTP(
                email,
                "Mã xác nhận khôi phục mật khẩu",
                "Chào bạn,\n\nMã OTP để khôi phục mật khẩu của bạn là: %s\n\nMã này sẽ hết hạn trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng"
        );
    }

    /**
     * Generate a 6-digit OTP and send it via email for register flow.
     */
    public String generateAndSendRegisterOTP(String email) {
        return generateAndSendOTP(
                email,
                "Mã xác nhận đăng ký tài khoản",
                "Chào bạn,\n\nMã OTP để xác thực đăng ký tài khoản của bạn là: %s\n\nMã này sẽ hết hạn trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng"
        );
    }

    private String generateAndSendOTP(String email, String subject, String textTemplate) {
        // Generate 6-digit OTP
        String otp = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));

        if (mailUsername == null || mailUsername.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình email gửi OTP. Vui lòng kiểm tra MAIL_USERNAME và MAIL_PASSWORD.");
        }
        
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(String.format(textTemplate, otp));
            
            mailSender.send(message);
            System.out.println("Đã gửi OTP " + otp + " tới email " + email);
        } catch (MailException e) {
            System.err.println("Lỗi khi gửi email: " + e.getMessage());
            throw new IllegalStateException("Không gửi được OTP qua email. Vui lòng kiểm tra cấu hình SMTP hoặc thử lại sau.");
        }

        return otp;
    }
    public List<Account> findAll() {
        return accountRepository.findAll();
    }
}
