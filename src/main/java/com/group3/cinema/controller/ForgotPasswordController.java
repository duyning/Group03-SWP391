package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * Controller xu ly chuc nang quen mat khau bang OTP gui qua email.
 * Luong chinh gom 4 buoc:
 * 1. Nguoi dung nhap email can lay lai mat khau.
 * 2. He thong gui OTP ve email neu email ton tai trong database.
 * 3. Nguoi dung nhap OTP de xac thuc quyen so huu email.
 * 4. Neu OTP dung va chua het han, nguoi dung duoc tao mat khau moi.
 * 
 * Ngay thuc hien: 04/06/2026
 * Tao boi: DuongND_HE186619
 */
@Controller
// Tat ca route trong controller nay deu bat dau bang /forgot-password.
@RequestMapping("/forgot-password")
public class ForgotPasswordController {

    // Key luu email dang thuc hien quen mat khau trong session.
    private static final String FORGOT_EMAIL = "forgotEmail";

    // Key luu ma OTP hien tai trong session.
    private static final String FORGOT_OTP = "forgotOtp";

    // Key luu thoi diem het han cua OTP trong session.
    private static final String FORGOT_OTP_EXPIRES_AT = "forgotOtpExpiresAt";

    // Key danh dau email/OTP da duoc xac thuc thanh cong.
    private static final String OTP_VERIFIED = "otpVerified";

    // Thoi gian OTP co hieu luc, tinh theo phut.
    private static final int OTP_VALID_MINUTES = 5;

    // Service xu ly truy van account, gui OTP va cap nhat mat khau.
    @Autowired
    private AccountService accountService;

    /**
     * Buoc 1: Hien thi form nhap email.
     */
    @GetMapping
    public String showEmailForm(HttpSession session, Model model) {
        // Xoa session cu de moi lan vao /forgot-password la mot flow moi sach se.
        clearForgotPasswordSession(session);

        // step = 1 de template hien thi form nhap email.
        model.addAttribute("step", 1);

        // Tra ve template forgot-password.html.
        return "forgot-password";
    }

    /**
     * Buoc 1 submit: Nhan email, kiem tra email ton tai, tao va gui OTP.
     */
    @PostMapping("/send-otp")
    public String processEmail(@RequestParam("email") String emailInput, HttpSession session,
            RedirectAttributes redirectAttributes) {
        // Xoa du lieu flow cu truoc khi tao OTP moi de tranh OTP cua email khac bi dung
        // lai.
        clearForgotPasswordSession(session);

        // Chuan hoa email dau vao: neu null thi doi thanh chuoi rong, neu co khoang
        // trang thi trim.
        String email = emailInput == null ? "" : emailInput.trim();

        // Tim account theo email da nhap.
        Account account = accountService.findByEmail(email);

        // Neu email khong ton tai trong he thong thi khong gui OTP.
        if (account == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email không tồn tại trong hệ thống.");
            return "redirect:/forgot-password";
        }

        // Bien luu OTP duoc sinh ra sau khi gui email thanh cong.
        String otp;
        try {
            // Tao OTP va gui den email cua account.
            otp = accountService.generateAndSendOTP(email);
        } catch (IllegalStateException e) {
            // Neu chua cau hinh mail hoac SMTP loi/timeout, gui thong bao loi ve UI.
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/forgot-password";
        }

        // Luu email vao session de cac buoc tiep theo biet dang reset account nao.
        session.setAttribute(FORGOT_EMAIL, email);

        // Luu OTP vao session de so sanh voi OTP nguoi dung nhap.
        session.setAttribute(FORGOT_OTP, otp);

        // Luu thoi diem het han OTP, mac dinh la sau 5 phut.
        session.setAttribute(FORGOT_OTP_EXPIRES_AT, LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));

        // Dat trang thai chua xac thuc OTP.
        session.setAttribute(OTP_VERIFIED, false);

        // Gui flash message sang request tiep theo de thong bao da gui OTP.
        redirectAttributes.addFlashAttribute("successMessage", "Mã xác nhận đã được gửi đến email " + email);

        // Chuyen sang trang nhap OTP.
        return "redirect:/forgot-password/otp";
    }

    /**
     * Buoc 2: Hien thi form nhap OTP.
     */
    @GetMapping("/otp")
    public String showOtpForm(HttpSession session, Model model) {
        // Lay email dang reset trong session.
        String email = (String) session.getAttribute(FORGOT_EMAIL);

        // Neu khong co email hoac OTP trong session thi flow khong hop le, quay lai
        // buoc nhap email.
        if (email == null || session.getAttribute(FORGOT_OTP) == null) {
            return "redirect:/forgot-password";
        }

        // Neu OTP da het han thi xoa session va bat nguoi dung yeu cau OTP moi.
        if (isOtpExpired(session)) {
            clearForgotPasswordSession(session);
            return "redirect:/forgot-password";
        }

        // step = 2 de template hien thi form nhap OTP.
        model.addAttribute("step", 2);

        // Dua email ra view de nguoi dung biet OTP da duoc gui ve email nao.
        model.addAttribute("email", email);

        // Tra ve template forgot-password.html.
        return "forgot-password";
    }

    /**
     * Buoc 2 submit: Kiem tra OTP nguoi dung nhap.
     */
    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("otp") String userOtp, HttpSession session,
            RedirectAttributes redirectAttributes) {
        // Lay email dang reset tu session.
        String email = (String) session.getAttribute(FORGOT_EMAIL);

        // Lay OTP da gui tu session.
        String sessionOtp = (String) session.getAttribute(FORGOT_OTP);

        // Neu mat email hoac OTP trong session thi phien lay lai mat khau khong con hop
        // le.
        if (email == null || sessionOtp == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Phiên lấy lại mật khẩu đã hết hạn. Vui lòng nhập email lại.");
            return "redirect:/forgot-password";
        }

        // Neu OTP het han thi xoa session va yeu cau nguoi dung bat dau lai.
        if (isOtpExpired(session)) {
            clearForgotPasswordSession(session);
            redirectAttributes.addFlashAttribute("errorMessage", "Mã xác nhận đã hết hạn. Vui lòng yêu cầu mã mới.");
            return "redirect:/forgot-password";
        }

        // So sanh OTP trong session voi OTP nguoi dung nhap, co trim de tranh loi
        // khoang trang.
        if (!sessionOtp.equals(userOtp == null ? "" : userOtp.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã xác nhận không đúng hoặc đã hết hạn.");
            return "redirect:/forgot-password/otp";
        }

        // OTP dung: danh dau flow da xac thuc thanh cong.
        session.setAttribute(OTP_VERIFIED, true);

        // Xoa OTP sau khi xac thuc thanh cong de OTP khong bi dung lai.
        session.removeAttribute(FORGOT_OTP);

        // Xoa thoi gian het han vi khong can kiem tra OTP nua sau khi da verified.
        session.removeAttribute(FORGOT_OTP_EXPIRES_AT);

        // Chuyen sang buoc tao mat khau moi.
        return "redirect:/forgot-password/new-password";
    }

    /**
     * Buoc 3: Hien thi form tao mat khau moi.
     */
    @GetMapping("/new-password")
    public String showNewPasswordForm(HttpSession session, Model model) {
        // Chi cho vao form doi mat khau neu OTP da verified va con email trong session.
        if (!isOtpVerified(session) || session.getAttribute(FORGOT_EMAIL) == null) {
            return "redirect:/forgot-password";
        }

        // step = 3 de template hien thi form mat khau moi.
        model.addAttribute("step", 3);

        // Dua email ra view de user biet dang doi mat khau cho tai khoan nao.
        model.addAttribute("email", session.getAttribute(FORGOT_EMAIL));

        // Tra ve template forgot-password.html.
        return "forgot-password";
    }

    /**
     * Buoc 3 submit: Cap nhat mat khau moi cho account da xac thuc OTP.
     */
    @PostMapping("/update-password")
    public String updatePassword(
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Neu user chua verify OTP hoac session khong con email thi khong cho doi mat
        // khau.
        if (!isOtpVerified(session) || session.getAttribute(FORGOT_EMAIL) == null) {
            return "redirect:/forgot-password";
        }

        // Lay email da duoc xac thuc OTP tu session.
        String email = (String) session.getAttribute(FORGOT_EMAIL);

        // Tim account theo email do de cap nhat mat khau.
        Account account = accountService.findByEmail(email);

        // Neu account bi xoa trong luc reset password thi xoa session va bao loi.
        if (account == null) {
            clearForgotPasswordSession(session);
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản không còn tồn tại. Vui lòng thử lại.");
            return "redirect:/forgot-password";
        }

        try {
            // Goi service validate mat khau moi va luu vao database.
            accountService.updatePassword(account, newPassword, confirmPassword);

            // Doi mat khau thanh cong thi xoa toan bo session tam cua forgot password.
            clearForgotPasswordSession(session);

            // Chuyen sang trang thanh cong co dem nguoc ve login.
            return "redirect:/forgot-password/success";
        } catch (IllegalArgumentException e) {
            // Neu mat khau moi khong hop le, dua message ve lai form mat khau moi.
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/forgot-password/new-password";
        }
    }

    /**
     * Buoc 4: Hien thi man hinh doi mat khau thanh cong.
     */
    @GetMapping("/success")
    public String showResetSuccess(Model model) {
        // step = 4 de template hien thi panel thanh cong.
        model.addAttribute("step", 4);

        // Tra ve cung template forgot-password.html.
        return "forgot-password";
    }

    /**
     * Kiem tra session da verify OTP hay chua.
     */
    private boolean isOtpVerified(HttpSession session) {
        // Boolean.TRUE.equals giup tranh NullPointerException neu session chua co
        // OTP_VERIFIED.
        return Boolean.TRUE.equals(session.getAttribute(OTP_VERIFIED));
    }

    /**
     * Kiem tra OTP da het han hay chua.
     */
    private boolean isOtpExpired(HttpSession session) {
        // Lay thoi diem het han OTP tu session.
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(FORGOT_OTP_EXPIRES_AT);

        // Neu khong co expiresAt thi coi nhu het han; neu thoi gian hien tai vuot qua
        // expiresAt thi het han.
        return expiresAt == null || LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Xoa toan bo du lieu tam cua flow quen mat khau trong session.
     */
    private void clearForgotPasswordSession(HttpSession session) {
        // Xoa email dang reset.
        session.removeAttribute(FORGOT_EMAIL);

        // Xoa OTP da gui.
        session.removeAttribute(FORGOT_OTP);

        // Xoa thoi diem het han OTP.
        session.removeAttribute(FORGOT_OTP_EXPIRES_AT);

        // Xoa trang thai da xac thuc OTP.
        session.removeAttribute(OTP_VERIFIED);
    }
}
