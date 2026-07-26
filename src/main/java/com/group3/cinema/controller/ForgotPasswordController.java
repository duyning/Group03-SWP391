// Khai bao package chua cac controller cua ung dung cinema.
package com.group3.cinema.controller;

// Import entity Account de controller co the tim va cap nhat tai khoan can reset password.
import com.group3.cinema.entity.Account;
// Import AccountService de goi logic tim account, gui OTP va doi password.
import com.group3.cinema.service.AccountService;
// Import HttpSession de luu tam email/OTP/trang thai verify giua nhieu request.
import jakarta.servlet.http.HttpSession;
// Import Autowired de Spring tu dong inject AccountService vao controller.
import org.springframework.beans.factory.annotation.Autowired;
// Import Controller de danh dau class nay la Spring MVC controller tra ve view.
import org.springframework.stereotype.Controller;
// Import Model de dua bien step/email sang template forgot-password.html.
import org.springframework.ui.Model;
// Import GetMapping de map cac request GET nhu mo form email/OTP/password moi.
import org.springframework.web.bind.annotation.GetMapping;
// Import PostMapping de map cac request POST tu form submit.
import org.springframework.web.bind.annotation.PostMapping;
// Import RequestMapping de dat prefix chung /forgot-password cho ca controller.
import org.springframework.web.bind.annotation.RequestMapping;
// Import RequestParam de lay gia tri input tu form theo name.
import org.springframework.web.bind.annotation.RequestParam;
// Import RedirectAttributes de gui flash message sau khi redirect.
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Import LocalDateTime de tinh va so sanh thoi diem OTP het han.
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
// Annotation nay bao Spring tao bean controller va cho phep return ten view Thymeleaf.
@Controller
// Tat ca route trong controller nay deu bat dau bang /forgot-password.
@RequestMapping("/forgot-password")
// Class nay gom toan bo endpoint cua flow Forgot Password.
public class ForgotPasswordController {

    // Key luu email dang thuc hien quen mat khau trong session.
    // Gia tri "forgotEmail" la ten attribute dung de set/get trong HttpSession.
    private static final String FORGOT_EMAIL = "forgotEmail";

    // Key luu ma OTP hien tai trong session.
    // OTP nay dung de so sanh voi ma nguoi dung nhap o buoc verify.
    private static final String FORGOT_OTP = "forgotOtp";

    // Key luu thoi diem het han cua OTP trong session.
    // Attribute nay giup controller khong chap nhan OTP qua han.
    private static final String FORGOT_OTP_EXPIRES_AT = "forgotOtpExpiresAt";

    // Key danh dau email/OTP da duoc xac thuc thanh cong.
    // Khi gia tri nay la true thi user moi duoc vao form tao password moi.
    private static final String OTP_VERIFIED = "otpVerified";

    // Thoi gian OTP co hieu luc, tinh theo phut.
    // Hien tai OTP forgot password chi hop le trong 5 phut.
    private static final int OTP_VALID_MINUTES = 5;

    // Service xu ly truy van account, gui OTP va cap nhat mat khau.
    // Spring se inject bean AccountService vao bien nay khi chay ung dung.
    @Autowired
    private AccountService accountService;

    /**
     * Buoc 1: Hien thi form nhap email.
     */
    // Map GET /forgot-password vao method hien thi man hinh nhap email.
    @GetMapping
    // HttpSession dung de xoa flow cu; Model dung de bao template dang o step 1.
    public String showEmailForm(HttpSession session, Model model) {
        // Xoa session cu de moi lan vao /forgot-password la mot flow moi sach se.
        clearForgotPasswordSession(session);

        // step = 1 de template hien thi form nhap email.
        model.addAttribute("step", 1);

        // Tra ve template forgot-password.html.
        // ViewResolver se tim file src/main/resources/templates/forgot-password.html.
        return "forgot-password";
    }

    /**
     * Buoc 1 submit: Nhan email, kiem tra email ton tai, tao va gui OTP.
     */
    // Map POST /forgot-password/send-otp khi user submit form nhap email.
    @PostMapping("/send-otp")
    public String processEmail(
            // Lay gia tri input name="email" tu forgot-password.html.
            @RequestParam("email") String emailInput,
            // Session dung de luu email, OTP, thoi gian het han va trang thai verify.
            HttpSession session,
            // RedirectAttributes dung de gui message sau khi redirect sang step tiep theo.
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
            // Flash errorMessage se hien thi sau khi redirect ve form nhap email.
            redirectAttributes.addFlashAttribute("errorMessage", "Email không tồn tại trong hệ thống.");
            // Redirect ve GET /forgot-password de user nhap lai email.
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
            // Khong tiep tuc flow neu OTP khong gui duoc.
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
        // Browser se goi GET /forgot-password/otp sau redirect nay.
        return "redirect:/forgot-password/otp";
    }

    /**
     * Buoc 2: Hien thi form nhap OTP.
     */
    // Map GET /forgot-password/otp de hien thi form nhap ma OTP.
    @GetMapping("/otp")
    // Session chua email/OTP; Model dua step va email sang template.
    public String showOtpForm(HttpSession session, Model model) {
        // Lay email dang reset trong session.
        String email = (String) session.getAttribute(FORGOT_EMAIL);

        // Neu khong co email hoac OTP trong session thi flow khong hop le, quay lai
        // buoc nhap email.
        if (email == null || session.getAttribute(FORGOT_OTP) == null) {
            // Redirect ve dau flow vi user vao /otp truc tiep hoac session da mat.
            return "redirect:/forgot-password";
        }

        // Neu OTP da het han thi xoa session va bat nguoi dung yeu cau OTP moi.
        if (isOtpExpired(session)) {
            // Don du lieu cu de OTP het han khong con nam trong session.
            clearForgotPasswordSession(session);
            // Quay lai buoc nhap email de gui OTP moi.
            return "redirect:/forgot-password";
        }

        // step = 2 de template hien thi form nhap OTP.
        model.addAttribute("step", 2);

        // Dua email ra view de nguoi dung biet OTP da duoc gui ve email nao.
        model.addAttribute("email", email);

        // Tra ve template forgot-password.html.
        // Template dua vao step=2 de chi hien block form OTP.
        return "forgot-password";
    }

    /**
     * Buoc 2 submit: Kiem tra OTP nguoi dung nhap.
     */
    // Map POST /forgot-password/verify-otp khi user submit ma OTP.
    @PostMapping("/verify-otp")
    public String verifyOtp(
            // Lay gia tri input name="otp" ma user vua nhap.
            @RequestParam("otp") String userOtp,
            // Session chua OTP goc va email dang reset.
            HttpSession session,
            // Dung de gui loi/thanh cong sau redirect.
            RedirectAttributes redirectAttributes) {
        // Lay email dang reset tu session.
        String email = (String) session.getAttribute(FORGOT_EMAIL);

        // Lay OTP da gui tu session.
        String sessionOtp = (String) session.getAttribute(FORGOT_OTP);

        // Neu mat email hoac OTP trong session thi phien lay lai mat khau khong con hop
        // le.
        if (email == null || sessionOtp == null) {
            // Tao flash message de bao user phien forgot password khong con hop le.
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Phiên lấy lại mật khẩu đã hết hạn. Vui lòng nhập email lại.");
            // Bat dau lai tu buoc nhap email.
            return "redirect:/forgot-password";
        }

        // Neu OTP het han thi xoa session va yeu cau nguoi dung bat dau lai.
        if (isOtpExpired(session)) {
            // Xoa het du lieu tam neu OTP da qua thoi gian hop le.
            clearForgotPasswordSession(session);
            // Bao loi het han OTP cho request tiep theo.
            redirectAttributes.addFlashAttribute("errorMessage", "Mã xác nhận đã hết hạn. Vui lòng yêu cầu mã mới.");
            // Quay lai buoc nhap email de user xin OTP moi.
            return "redirect:/forgot-password";
        }

        // So sanh OTP trong session voi OTP nguoi dung nhap, co trim de tranh loi
        // khoang trang.
        if (!sessionOtp.equals(userOtp == null ? "" : userOtp.trim())) {
            // OTP sai thi giu flow hien tai va hien loi tren trang nhap OTP.
            redirectAttributes.addFlashAttribute("errorMessage", "Mã xác nhận không đúng hoặc đã hết hạn.");
            // Redirect lai form OTP de user nhap lai ma.
            return "redirect:/forgot-password/otp";
        }

        // OTP dung: danh dau flow da xac thuc thanh cong.
        session.setAttribute(OTP_VERIFIED, true);

        // Xoa OTP sau khi xac thuc thanh cong de OTP khong bi dung lai.
        session.removeAttribute(FORGOT_OTP);

        // Xoa thoi gian het han vi khong can kiem tra OTP nua sau khi da verified.
        session.removeAttribute(FORGOT_OTP_EXPIRES_AT);

        // Chuyen sang buoc tao mat khau moi.
        // Browser se goi GET /forgot-password/new-password.
        return "redirect:/forgot-password/new-password";
    }

    /**
     * Buoc 3: Hien thi form tao mat khau moi.
     */
    // Map GET /forgot-password/new-password de hien thi form dat mat khau moi.
    @GetMapping("/new-password")
    // Session dung de kiem tra da verify OTP; Model dung de hien step 3 va email.
    public String showNewPasswordForm(HttpSession session, Model model) {
        // Chi cho vao form doi mat khau neu OTP da verified va con email trong session.
        if (!isOtpVerified(session) || session.getAttribute(FORGOT_EMAIL) == null) {
            // Neu user chua qua buoc OTP thi khong cho mo truc tiep form password moi.
            return "redirect:/forgot-password";
        }

        // step = 3 de template hien thi form mat khau moi.
        model.addAttribute("step", 3);

        // Dua email ra view de user biet dang doi mat khau cho tai khoan nao.
        model.addAttribute("email", session.getAttribute(FORGOT_EMAIL));

        // Tra ve template forgot-password.html.
        // Template dua vao step=3 de hien form newPassword/confirmPassword.
        return "forgot-password";
    }

    /**
     * Buoc 3 submit: Cap nhat mat khau moi cho account da xac thuc OTP.
     */
    // Map POST /forgot-password/update-password khi user submit password moi.
    @PostMapping("/update-password")
    public String updatePassword(
            // Lay input name="newPassword" tu form.
            @RequestParam("newPassword") String newPassword,
            // Lay input name="confirmPassword" de so sanh voi password moi.
            @RequestParam("confirmPassword") String confirmPassword,
            // Session giu email da verify OTP.
            HttpSession session,
            // RedirectAttributes dung de tra loi hoac message thanh cong sau redirect.
            RedirectAttributes redirectAttributes) {

        // Neu user chua verify OTP hoac session khong con email thi khong cho doi mat
        // khau.
        if (!isOtpVerified(session) || session.getAttribute(FORGOT_EMAIL) == null) {
            // Chan request POST neu user khong di qua buoc verify OTP.
            return "redirect:/forgot-password";
        }

        // Lay email da duoc xac thuc OTP tu session.
        String email = (String) session.getAttribute(FORGOT_EMAIL);

        // Tim account theo email do de cap nhat mat khau.
        Account account = accountService.findByEmail(email);

        // Neu account bi xoa trong luc reset password thi xoa session va bao loi.
        if (account == null) {
            // Neu account khong con ton tai thi xoa flow forgot password dang treo.
            clearForgotPasswordSession(session);
            // Bao loi cho user ve trang nhap email.
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản không còn tồn tại. Vui lòng thử lại.");
            // Quay lai dau flow de user thu lai voi email khac/lan moi.
            return "redirect:/forgot-password";
        }

        try {
            // Goi service validate mat khau moi va luu vao database.
            accountService.updatePassword(account, newPassword, confirmPassword);

            // Doi mat khau thanh cong thi xoa toan bo session tam cua forgot password.
            clearForgotPasswordSession(session);

            // Chuyen sang trang thanh cong co dem nguoc ve login.
            // Browser se goi GET /forgot-password/success.
            return "redirect:/forgot-password/success";
        } catch (IllegalArgumentException e) {
            // Neu mat khau moi khong hop le, dua message ve lai form mat khau moi.
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // Redirect lai step 3 de user sua newPassword hoac confirmPassword.
            return "redirect:/forgot-password/new-password";
        }
    }

    /**
     * Buoc 4: Hien thi man hinh doi mat khau thanh cong.
     */
    // Map GET /forgot-password/success sau khi update password thanh cong.
    @GetMapping("/success")
    // Model dung de gui step=4 sang template.
    public String showResetSuccess(Model model) {
        // step = 4 de template hien thi panel thanh cong.
        model.addAttribute("step", 4);

        // Tra ve cung template forgot-password.html.
        // Template dua vao step=4 de hien panel thanh cong.
        return "forgot-password";
    }

    /**
     * Kiem tra session da verify OTP hay chua.
     */
    // Helper nay gom logic doc session OTP_VERIFIED ve mot noi de cac endpoint dung lai.
    private boolean isOtpVerified(HttpSession session) {
        // Boolean.TRUE.equals giup tranh NullPointerException neu session chua co
        // OTP_VERIFIED.
        // Chi tra true khi attribute OTP_VERIFIED thuc su la Boolean.TRUE.
        return Boolean.TRUE.equals(session.getAttribute(OTP_VERIFIED));
    }

    /**
     * Kiem tra OTP da het han hay chua.
     */
    // Helper nay duoc goi truoc khi hien form OTP va truoc khi verify OTP.
    private boolean isOtpExpired(HttpSession session) {
        // Lay thoi diem het han OTP tu session.
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(FORGOT_OTP_EXPIRES_AT);

        // Neu khong co expiresAt thi coi nhu het han; neu thoi gian hien tai vuot qua
        // expiresAt thi het han.
        // LocalDateTime.now().isAfter(expiresAt) nghia la hien tai da qua moc het han.
        return expiresAt == null || LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Xoa toan bo du lieu tam cua flow quen mat khau trong session.
     */
    // Helper gom cac removeAttribute de tranh lap code va tranh quen xoa key nao do.
    private void clearForgotPasswordSession(HttpSession session) {
        // Xoa email dang reset.
        // Sau dong nay controller khong con biet user dang reset account nao.
        session.removeAttribute(FORGOT_EMAIL);

        // Xoa OTP da gui.
        // Sau dong nay OTP cu khong the duoc dung lai.
        session.removeAttribute(FORGOT_OTP);

        // Xoa thoi diem het han OTP.
        // Xoa expiresAt di kem OTP de session quay ve trang thai sach.
        session.removeAttribute(FORGOT_OTP_EXPIRES_AT);

        // Xoa trang thai da xac thuc OTP.
        // Sau dong nay user phai verify OTP lai neu muon dat password moi.
        session.removeAttribute(OTP_VERIFIED);
    }
}
