package example.service;

import example.entity.Account;
import example.entity.MembershipLevel;
import example.entity.Role;
import example.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Lớp dịch vụ (Service) xử lý các nghiệp vụ logic liên quan đến tài khoản (Account).
 * Bao gồm đăng ký, đăng nhập, tìm kiếm tài khoản, đổi mật khẩu và xử lý gửi OTP xác thực.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

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
        account.setMembershipLevel(MembershipLevel.SILVER);
        account.setLoyaltyPoint(0);
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
        Account account = accountRepository.findByEmail(email);
        if (account != null && account.getPassword().equals(password)) {
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
        return accountRepository.findById(id).orElse(null);
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
        if (!account.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        // Case 4: New password same as old
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        // Case 2: New password length invalid (must be 8-20 characters)
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("New password must be 8-20 characters");
        }

        // Case 3: Confirm password mismatch
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Confirm password does not match");
        }

        // Case 5: All validations passed — update password
        account.setPassword(newPassword);
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
     * Generate a 6-digit OTP and send it via email.
     */
    public String generateAndSendOTP(String email) {
        // Generate 6-digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Mã xác nhận khôi phục mật khẩu - CinemaBook");
            message.setText("Chào bạn,\n\nMã OTP để khôi phục mật khẩu của bạn là: " + otp + "\n\nMã này sẽ hết hạn trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng,\nĐội ngũ CinemaBook.");
            
            mailSender.send(message);
            System.out.println("Đã gửi OTP " + otp + " tới email " + email);
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi email: " + e.getMessage());
            // Fallback for testing when email fails
            System.out.println("FALLBACK OTP (do lỗi gửi email): " + otp);
        }

        return otp;
    }
}


