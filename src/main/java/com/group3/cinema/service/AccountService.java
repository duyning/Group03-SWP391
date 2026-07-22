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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

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
        account.setRole(Role.MANAGER);
        account.setStatus(true);
        account.setMembershipLevel(null);
        account.setLoyaltyPoint(0);
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
        return accountRepository.findById(id).orElse(null);
    }

    /**
     * Lấy danh sách tất cả tài khoản sắp xếp theo Tên cho Admin quản lý.
     */
    public java.util.List<Account> getAllAccounts() {
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
        if (targetId == adminId) {
            throw new IllegalArgumentException("Bạn không thể vô hiệu hóa chính tài khoản của mình.");
        }
        Account account = accountRepository.findById(targetId).orElse(null);
        if (account == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        }
        if (account.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Không thể thay đổi trạng thái tài khoản Admin khác.");
        }
        account.setStatus(!account.isStatus());
        return accountRepository.save(account);
    }

    /**
     * Cập nhật thông tin chi tiết hồ sơ cá nhân của người dùng.
     */
    public void updateProfile(Account account, String name, java.time.LocalDate dob, String gender, String address, String phoneNum) {
        account.setName(name);
        account.setDob(dob);
        account.setGender(gender);
        account.setAddress(address);
        if (phoneNum != null && !phoneNum.trim().isEmpty()) {
            account.setPhoneNum(phoneNum.trim());
        }
        accountRepository.save(account);
    }

    /**
     * Kiểm tra số điện thoại có bị trùng với tài khoản khác hay không khi chỉnh sửa thông tin.
     */
    public boolean isPhoneNumTakenByOther(String phoneNum, Integer accountId) {
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
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu cũ không được để trống");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu mới không được để trống");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("Xác nhận mật khẩu không được để trống");
        }

        if (!account.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("New password must be 8-20 characters");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Confirm password does not match");
        }

        account.setPassword(newPassword);
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
        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setTo(email);
            message.setSubject(subject);
            message.setText(String.format(textTemplate, otp));
            
            mailSender.send(message);
            System.out.println("Đã gửi OTP " + otp + " tới email " + email);
        } catch (Exception e) {
            System.err.println("Lỗi khi gửi email: " + e.getMessage());
            System.out.println("FALLBACK OTP (do lỗi gửi email): " + otp);
        }

        return otp;
    }

    /** Lấy tất cả danh sách tài khoản. */
    public List<Account> findAll() {
        return accountRepository.findAll();
    }
}

