/**
 * Lớp dịch vụ (Service) xử lý các nghiệp vụ logic liên quan đến tài khoản (`AccountService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `AccountController`, `CustomerController`, `AdminController`, `AuthInterceptor`.
 * - Tương tác với `AccountRepository` để thao tác CSDL (`findByEmailWithVouchers`, `existsByEmail`, `save`).
 * - Tương tác với `JavaMailSender` để gửi thư điện tử chứa mã OTP xác thực đăng ký và quên mật khẩu.
 * 
 * Chức năng chính:
 * - Đăng ký khách hàng (`register`), đăng nhập (`login`), tạo tài khoản quản lý (`createManagerAccount`).
 * - Khóa/mở khóa tài khoản (`toggleAccountStatus`), đổi mật khẩu (`resetPassword`, `updatePassword`).
 * - Gửi mã OTP xác nhận qua Email (`generateAndSendOTP`, `generateAndSendRegisterOTP`).
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
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
     * Đăng ký tài khoản khách hàng mới.
     * Mặc định đặt vai trò `CUSTOMER`, trạng thái `true`, hạng thành viên `BRONZE` và điểm thưởng `0`.
     * 
     * @param account Thông tin tài khoản cần đăng ký.
     * @return Đối tượng Account đã lưu CSDL.
     */
    public Account register(Account account) {
        account.setRole(Role.CUSTOMER);
        account.setStatus(true);
        account.setMembershipLevel(MembershipLevel.BRONZE);
        account.setLoyaltyPoint(0);
        return accountRepository.save(account);
    }

    /**
     * Tạo tài khoản Quản lý rạp (`MANAGER`) bởi Quản trị viên (`ADMIN`).
     * 
     * @param account Thông tin tài khoản quản lý.
     * @return Account vừa tạo.
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
     * Xác thực thông tin đăng nhập theo Email và Mật khẩu.
     * Nạp kèm thông tin Voucher bằng `findByEmailWithVouchers` để tránh lỗi Lazy loading trong Session.
     * 
     * @param email Email đăng nhập.
     * @param password Mật khẩu nguyên bản.
     * @return Account nếu khớp thông tin, null nếu không tồn tại hoặc sai mật khẩu.
     */
    public Account login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedPassword = password == null ? "" : password.trim();
        Account account = accountRepository.findByEmailWithVouchers(normalizedEmail);
        if (account != null && account.getPassword().equals(normalizedPassword)) {
            return account;
        }
        return null;
    }

    /**
     * Kiểm tra xem địa chỉ email đã được sử dụng hay chưa.
     */
    public boolean isEmailExist(String email) {
        return accountRepository.existsByEmail(email);
    }

    /**
     * Kiểm tra xem số điện thoại đã được sử dụng hay chưa.
     */
    public boolean isPhoneNumExist(String phoneNum) {
        return accountRepository.existsByPhoneNum(phoneNum);
    }

    /**
     * Tìm tài khoản theo email.
     */
    public Account findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    /**
     * Tìm tài khoản theo ID.
     */
    public Account findById(int id) {
        // JpaRepository tra Optional; orElse(null) giup controller kiem tra bang null.
        return accountRepository.findById(id).orElse(null);
    }

    /**
     * Lấy danh sách tất cả tài khoản sắp xếp theo Tên cho Admin quản lý.
     */
    public java.util.List<Account> getAllAccounts() {
        // Sort.by("name") yeu cau database sap xep tang dan theo name.
        return accountRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    /**
     * Bật/tắt trạng thái vô hiệu hóa của một tài khoản (`status`).
     * Ngăn không cho phép Admin tự vô hiệu hóa tài khoản của chính mình hoặc các tài khoản Admin khác.
     * 
     * @param targetId ID tài khoản bị thao tác.
     * @param adminId ID Admin đang thực hiện thao tác.
     * @return Account sau khi chuyển trạng thái.
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
        if (account.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Không thể thay đổi trạng thái tài khoản Admin khác.");
        }
        // Dao true thanh false hoac false thanh true.
        account.setStatus(!account.isStatus());
        // UPDATE account va tra ve trang thai sau cap nhat.
        return accountRepository.save(account);
    }

    /**
     * Cập nhật thông tin chi tiết hồ sơ cá nhân của người dùng.
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
     * Kiểm tra số điện thoại có bị trùng với tài khoản khác hay không khi chỉnh sửa thông tin.
     */
    public boolean isPhoneNumTakenByOther(String phoneNum, Integer accountId) {
        // Tim so dien thoai trung nhung loai tru accountID hien tai.
        return accountRepository.existsByPhoneNumAndAccountIDNot(phoneNum, accountId);
    }

    /**
     * Đổi mật khẩu tài khoản người dùng từ giao diện Hồ sơ cá nhân.
     * Thực hiện kiểm tra đầy đủ các ràng buộc: mật khẩu cũ chính xác, mật khẩu mới khác mật khẩu cũ, độ dài 8-20 ký tự, mật khẩu xác nhận khớp.
     * 
     * @param account Tài khoản cần đổi mật khẩu.
     * @param oldPassword Mật khẩu hiện tại.
     * @param newPassword Mật khẩu mới.
     * @param confirmPassword Nhập lại mật khẩu mới.
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
     * Đặt lại mật khẩu mới cho quy trình Quên mật khẩu (không yêu cầu nhập mật khẩu cũ).
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
     * Sinh mã OTP 6 chữ số ngẫu nhiên và gửi tới Email người dùng phục vụ Quên mật khẩu.
     */
    public String generateAndSendOTP(String email) {
        return generateAndSendOTP(
                email,
                "Mã xác nhận khôi phục mật khẩu",
                "Chào bạn,\n\nMã OTP để khôi phục mật khẩu của bạn là: %s\n\nMã này sẽ hết hạn trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng"
        );
    }

    /**
     * Sinh mã OTP 6 chữ số ngẫu nhiên và gửi tới Email người dùng phục vụ Đăng ký tài khoản.
     */
    public String generateAndSendRegisterOTP(String email) {
        return generateAndSendOTP(
                email,
                "Mã xác nhận đăng ký tài khoản",
                "Chào bạn,\n\nMã OTP để xác thực đăng ký tài khoản của bạn là: %s\n\nMã này sẽ hết hạn trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng"
        );
    }

    /**
     * Hàm dùng chung gửi Email OTP qua JavaMailSender.
     */
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

    /** Lấy tất cả danh sách tài khoản. */
    public List<Account> findAll() {
        return accountRepository.findAll();
    }
}

