package com.group3.cinema.controller;

// Entity Account dai dien cho bang/tai khoan nguoi dung trong database.
import com.group3.cinema.entity.Account;
// AccountService chua cac logic dang ky, kiem tra trung email/phone va gui OTP.
import com.group3.cinema.service.AccountService;
// HttpSession dung de luu tam account, email va OTP giua cac request.
import jakarta.servlet.http.HttpSession;
// @Valid kich hoat validate annotation trong entity Account.
import jakarta.validation.Valid;
// @Autowired cho phep Spring inject bean AccountService vao controller.
import org.springframework.beans.factory.annotation.Autowired;
// @Controller danh dau day la Spring MVC Controller tra ve view Thymeleaf.
import org.springframework.stereotype.Controller;
// Model dung de dua du lieu tu controller sang template HTML.
import org.springframework.ui.Model;
// BindingResult chua danh sach loi validate cua @Valid.
import org.springframework.validation.BindingResult;
// @GetMapping map HTTP GET request vao method.
import org.springframework.web.bind.annotation.GetMapping;
// @ModelAttribute bind du lieu form vao object Account.
import org.springframework.web.bind.annotation.ModelAttribute;
// @PostMapping map HTTP POST request vao method.
import org.springframework.web.bind.annotation.PostMapping;
// @RequestParam lay gia tri parameter tu form/request.
import org.springframework.web.bind.annotation.RequestParam;
// RedirectAttributes dung de gui flash message sau khi redirect.
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// LocalDateTime dung de luu va so sanh thoi diem OTP het han.
import java.time.LocalDateTime;

/**
 * Controller xu ly chuc nang dang ky tai khoan.
 * Luong dang ky gom 2 buoc:
 * 1. Nguoi dung nhap thong tin dang ky, he thong validate va gui OTP qua email.
 * 2. Nguoi dung nhap OTP dung thi tai khoan moi duoc luu chinh thuc vao database.
 * 
 * Ngay thuc hien: 04/06/2026
 * Tao boi: DuongND_HE186619
 */
// Bao cho Spring biet class nay xu ly request va tra ve ten view.
@Controller
public class RegisterController {

    // Cac key dung de luu tam thong tin dang ky trong session trong luc cho xac thuc OTP.
    // Key nay luu object Account chua duoc ghi vao database.
    private static final String PENDING_REGISTER_ACCOUNT = "pendingRegisterAccount";
    // Key nay luu OTP vua duoc gui den email nguoi dung.
    private static final String PENDING_REGISTER_OTP = "pendingRegisterOtp";
    // Key nay luu email dang dang ky de hien thi o trang nhap OTP.
    private static final String PENDING_REGISTER_EMAIL = "pendingRegisterEmail";
    // Key luu thoi diem het han cua OTP dang ky.
    private static final String PENDING_REGISTER_OTP_EXPIRES_AT = "pendingRegisterOtpExpiresAt";

    // Thoi gian OTP dang ky co hieu luc, tinh theo phut.
    private static final int REGISTER_OTP_VALID_MINUTES = 5;

    // Spring inject AccountService de controller goi cac ham xu ly nghiep vu.
    @Autowired
    private AccountService accountService;

    /**
     * Hien thi form dang ky.
     * Khi nguoi dung quay lai trang dang ky, xoa du lieu dang ky/OTP cu trong session
     * de tranh dung nham OTP hoac thong tin cua lan dang ky truoc.
     */
    // Map GET /register vao method hien thi form dang ky.
    @GetMapping("/register")
    // Model: gui object account rong sang view. Session: xoa cac du lieu OTP cu.
    public String showRegisterForm(Model model, HttpSession session) {
        // Xoa account/OTP/email/expiresAt cua lan dang ky truoc neu con trong session.
        clearPendingRegisterSession(session);
        // Tao Account rong de Thymeleaf bind vao form th:object="${account}".
        model.addAttribute("account", new Account());
        // Tra ve template register.html.
        return "register";
    }

    /**
     * Xu ly submit form dang ky.
     * Tai khoan chua duoc luu ngay tai buoc nay; he thong chi luu tam vao session
     * va gui OTP de nguoi dung xac thuc email truoc.
     */
    // Map POST /register khi nguoi dung bam nut Dang ky.
    @PostMapping("/register")
    public String processRegister(
            // @Valid validate object Account bang cac annotation trong entity Account.
            @Valid @ModelAttribute("account") Account account,
            // BindingResult phai dat ngay sau @ModelAttribute de nhan loi validate.
            BindingResult bindingResult,
            // Model dung de tra loi ve cung trang register neu gui OTP loi.
            Model model,
            // RedirectAttributes dung de gui thong bao sang trang /register/otp sau redirect.
            RedirectAttributes redirectAttributes,
            // Session dung de luu tam account va OTP trong luc cho xac thuc.
            HttpSession session) {

        // Kiem tra email da ton tai de tranh tao nhieu tai khoan dung chung mot email.
        // account.getEmail() lay email nguoi dung nhap tu form.
        if (accountService.isEmailExist(account.getEmail())) {
            // rejectValue gan loi vao field email de hien thi ngay duoi o email.
            bindingResult.rejectValue("email", "error.account", "Email đã được sử dụng");
        }

        // Kiem tra so dien thoai da ton tai neu nguoi dung co nhap so dien thoai.
        // Dieu kien != null tranh NullPointerException khi field phoneNum rong.
        if (account.getPhoneNum() != null && !account.getPhoneNum().isEmpty()
                // Goi service kiem tra phoneNum da ton tai trong database hay chua.
                && accountService.isPhoneNumExist(account.getPhoneNum())) {
            // Gan loi vao field phoneNum de form hien thi dung vi tri.
            bindingResult.rejectValue("phoneNum", "error.account", "Số điện thoại đã được sử dụng");
        }

        // Map loi tuoi ve field dob de Thymeleaf hien thi loi ngay duoi o ngay sinh.
        // Chi check tuoi khi dob khac null, con dob null da duoc validation annotation xu ly.
        if (account.getDob() != null && !account.isValidAge()) {
            // Gan loi vao field dob neu tuoi khong nam trong khoang hop le.
            bindingResult.rejectValue("dob", "error.account", "Tuổi không hợp lệ (phải từ 13 đến 100 tuổi).");
        }

        // Neu co loi validate, tra ve lai form de nguoi dung sua thong tin.
        // hasErrors gom ca loi annotation va loi custom rejectValue o tren.
        if (bindingResult.hasErrors()) {
            // Khong gui OTP khi form con loi validate.
            return "register";
        }

        // Tao va gui OTP dang ky. Neu SMTP loi/timeout thi hien thi loi ngay tren form.
        // Bien otp se chua ma OTP random do AccountService tao ra.
        String otp;
        try {
            // Gui OTP ve email trong form dang ky.
            otp = accountService.generateAndSendRegisterOTP(account.getEmail());
        } catch (IllegalStateException e) {
            // Neu gui mail that bai, dua message loi sang view register.html.
            model.addAttribute("errorMessage", e.getMessage());
            // Tra ve form dang ky de user co the thu lai.
            return "register";
        }

        // Luu tam thong tin dang ky va OTP trong session.
        // Chi khi OTP dung thi account moi duoc ghi xuong database.
        // Luu Account tam thoi, chua save database.
        session.setAttribute(PENDING_REGISTER_ACCOUNT, account);
        // Luu OTP de lat nua so sanh voi ma user nhap.
        session.setAttribute(PENDING_REGISTER_OTP, otp);
        // Luu email de hien thi tren trang register-otp.html.
        session.setAttribute(PENDING_REGISTER_EMAIL, account.getEmail());
        // Luu thoi diem OTP het han = hien tai + REGISTER_OTP_VALID_MINUTES.
        session.setAttribute(PENDING_REGISTER_OTP_EXPIRES_AT, LocalDateTime.now().plusMinutes(REGISTER_OTP_VALID_MINUTES));

        // Flash message chi ton tai qua mot lan redirect sang trang OTP.
        redirectAttributes.addFlashAttribute("successMessage",
                "Mã OTP đã được gửi đến email của bạn. Vui lòng nhập mã để hoàn tất đăng ký.");
        // Redirect sang trang nhap OTP.
        return "redirect:/register/otp";
    }

    /**
     * Hien thi trang nhap OTP dang ky.
     * Neu khong co du lieu dang ky dang cho trong session thi bat nguoi dung dang ky lai.
     */
    // Map GET /register/otp khi user duoc chuyen sang trang nhap OTP.
    @GetMapping("/register/otp")
    // RedirectAttributes dung de gui loi neu OTP het han va can redirect ve /register.
    public String showRegisterOtpForm(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        // Lay Account dang ky tam thoi tu session.
        Account pendingAccount = (Account) session.getAttribute(PENDING_REGISTER_ACCOUNT);
        // Lay email dang ky tu session de hien thi cho user.
        String email = (String) session.getAttribute(PENDING_REGISTER_EMAIL);

        // Neu khong co account hoac email trong session thi user khong di dung flow dang ky.
        if (pendingAccount == null || email == null) {
            // Quay lai form dang ky de bat dau lai.
            return "redirect:/register";
        }

        // Neu OTP da het han khi nguoi dung mo trang nhap OTP, xoa session va yeu cau dang ky lai.
        if (isRegisterOtpExpired(session)) {
            // Xoa account/OTP/email/expiresAt cu de khong dung lai ma het han.
            clearPendingRegisterSession(session);
            // Gui thong bao OTP het han ve trang register sau redirect.
            redirectAttributes.addFlashAttribute("errorMessage", "Mã OTP đã hết hạn. Vui lòng đăng ký lại để nhận mã mới.");
            // Redirect ve form dang ky.
            return "redirect:/register";
        }

        // Dua email sang view de user biet OTP da duoc gui den email nao.
        model.addAttribute("email", email);
        // Tra ve template register-otp.html.
        return "register-otp";
    }

    /**
     * Xac thuc OTP dang ky.
     * OTP dung thi luu tai khoan vao database, xoa session tam va chuyen sang trang thanh cong.
     */
    // Map POST /register/otp khi user submit ma OTP.
    @PostMapping("/register/otp")
    public String verifyRegisterOtp(
            // Lay gia tri input name="otp" tu form OTP.
            @RequestParam("otp") String userOtp,
            // Session chua OTP/account tam thoi can xac thuc.
            HttpSession session,
            // Flash message dung sau redirect khi OTP sai/het han/thanh cong.
            RedirectAttributes redirectAttributes) {

        // Lay OTP da luu trong session luc gui mail.
        String sessionOtp = (String) session.getAttribute(PENDING_REGISTER_OTP);
        // Lay Account tam thoi trong session, account nay chua luu vao database.
        Account pendingAccount = (Account) session.getAttribute(PENDING_REGISTER_ACCOUNT);

        // Khong con account/OTP trong session nghia la phien dang ky da het han hoac bi reset.
        if (pendingAccount == null || sessionOtp == null) {
            // Gui thong bao phien dang ky khong hop le ve form register.
            redirectAttributes.addFlashAttribute("errorMessage", "Phiên đăng ký đã hết hạn. Vui lòng đăng ký lại.");
            // Bat user dang ky lai tu dau.
            return "redirect:/register";
        }

        // Neu OTP het han thi xoa thong tin dang ky tam va bat dau lai tu form dang ky.
        if (isRegisterOtpExpired(session)) {
            // Xoa toan bo thong tin tam vi OTP da het han.
            clearPendingRegisterSession(session);
            // Bao user phai dang ky lai de nhan OTP moi.
            redirectAttributes.addFlashAttribute("errorMessage", "Mã OTP đã hết hạn. Vui lòng đăng ký lại để nhận mã mới.");
            // Quay ve form register.
            return "redirect:/register";
        }

        // So sanh OTP nguoi dung nhap voi OTP da gui qua email.
        // Neu userOtp null thi doi thanh chuoi rong, trim de bo khoang trang vo tinh.
        if (!sessionOtp.equals(userOtp == null ? "" : userOtp.trim())) {
            // OTP sai thi thong bao loi va giu user o trang nhap OTP.
            redirectAttributes.addFlashAttribute("errorMessage", "Mã OTP không đúng. Vui lòng thử lại.");
            // Quay lai trang OTP de nhap lai.
            return "redirect:/register/otp";
        }

        // OTP hop le: luu tai khoan chinh thuc.
        // Luc nay AccountService moi save account vao database voi role/status mac dinh.
        accountService.register(pendingAccount);

        // Don du lieu tam de OTP cu khong the dung lai.
        // Sau khi save thanh cong, session khong can giu pending account nua.
        clearPendingRegisterSession(session);

        // Gui thong bao thanh cong sang /register?success.
        redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
        // Chuyen ve trang register success, template se dem nguoc sang login.
        return "redirect:/register?success";
    }

    /**
     * Gui lai OTP cho phien dang ky dang cho.
     * Chi cho gui lai neu session van con account dang ky tam.
     */
    // Map GET /register/resend-otp khi user bam gui lai ma OTP.
    @GetMapping("/register/resend-otp")
    public String resendRegisterOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        // Lay account dang ky tam tu session.
        Account pendingAccount = (Account) session.getAttribute(PENDING_REGISTER_ACCOUNT);
        // Neu khong co account tam thi khong biet gui OTP cho ai.
        if (pendingAccount == null) {
            // Bat user quay lai form dang ky.
            return "redirect:/register";
        }

        // Tao OTP moi va thay the OTP cu trong session.
        // Bien otp se chua ma moi sau khi gui email thanh cong.
        String otp;
        try {
            // Gui OTP moi den email cua account dang ky tam.
            otp = accountService.generateAndSendRegisterOTP(pendingAccount.getEmail());
        } catch (IllegalStateException e) {
            // Neu gui mail loi thi thong bao o trang OTP.
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // Quay lai trang OTP hien tai.
            return "redirect:/register/otp";
        }
        // Ghi de OTP cu bang OTP moi.
        session.setAttribute(PENDING_REGISTER_OTP, otp);
        // Reset lai thoi gian het han cho OTP moi.
        session.setAttribute(PENDING_REGISTER_OTP_EXPIRES_AT, LocalDateTime.now().plusMinutes(REGISTER_OTP_VALID_MINUTES));

        // Thong bao da gui lai OTP.
        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lại mã OTP đến email của bạn.");
        // Quay lai trang nhap OTP.
        return "redirect:/register/otp";
    }

    /**
     * Kiem tra OTP dang ky da het han hay chua.
     */
    private boolean isRegisterOtpExpired(HttpSession session) {
        // Lay thoi diem het han da luu trong session.
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(PENDING_REGISTER_OTP_EXPIRES_AT);
        // Neu khong co expiresAt thi coi nhu het han; neu hien tai sau expiresAt thi het han.
        return expiresAt == null || LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Xoa toan bo du lieu tam cua flow dang ky trong session.
     */
    private void clearPendingRegisterSession(HttpSession session) {
        // Xoa account dang ky tam.
        session.removeAttribute(PENDING_REGISTER_ACCOUNT);
        // Xoa OTP dang ky tam.
        session.removeAttribute(PENDING_REGISTER_OTP);
        // Xoa email dang ky tam.
        session.removeAttribute(PENDING_REGISTER_EMAIL);
        // Xoa thoi diem het han OTP.
        session.removeAttribute(PENDING_REGISTER_OTP_EXPIRES_AT);
    }
}
