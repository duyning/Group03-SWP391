// Khai bao package chua cac controller cua ung dung cinema.
package com.group3.cinema.controller;

// Import entity Account de lay thong tin user dang dang nhap va account trong database.
import com.group3.cinema.entity.Account;
// Import ActionType de ghi log loai hanh dong PASSWORD_CHANGE.
import com.group3.cinema.entity.ActivityLog.ActionType;
// Import AccountService de goi logic tim account va reset password.
import com.group3.cinema.service.AccountService;
// Import ActivityLogService de ghi nhat ky khi user doi mat khau thanh cong.
import com.group3.cinema.service.ActivityLogService;
// Import HttpSession de lay user dang dang nhap tu session.
import jakarta.servlet.http.HttpSession;
// Import Autowired de Spring tu dong inject service vao controller.
import org.springframework.beans.factory.annotation.Autowired;
// Import Controller de danh dau class nay la Spring MVC controller tra ve view.
import org.springframework.stereotype.Controller;
// Import Model de dua du lieu/error/success message sang template reset-password.html.
import org.springframework.ui.Model;
// Import GetMapping de map request GET mo form reset password.
import org.springframework.web.bind.annotation.GetMapping;
// Import PostMapping de map request POST submit form reset password.
import org.springframework.web.bind.annotation.PostMapping;
// Import RequestParam de lay tung input password tu form HTML.
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller xử lý tính năng đổi mật khẩu (Reset Password).
 */
// Annotation nay bao Spring tao bean controller va cho phep method return ten view Thymeleaf.
@Controller
// Class nay xu ly flow Reset Password cho user da dang nhap.
public class ResetPasswordController {

    // Spring inject AccountService de controller goi logic doi mat khau.
    @Autowired
    private AccountService accountService;

    // Spring inject ActivityLogService de ghi log sau khi doi password thanh cong.
    @Autowired
    private ActivityLogService activityLogService;

    // Map GET /reset-password khi user mo trang doi mat khau.
    @GetMapping("/reset-password")
    // HttpSession dung de kiem tra user da dang nhap; Model dua gia tri rong sang form.
    public String showResetPasswordForm(HttpSession session, Model model) {
        // Lay account dang dang nhap tu session do LoginController da luu truoc do.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Neu chua dang nhap thi khong cho mo form reset password.
        if (loggedInUser == null) {
            // Redirect ve login de user dang nhap truoc khi doi mat khau.
            return "redirect:/login";
        }

        // Set oldPassword rong de input oldPassword tren form co gia tri mac dinh.
        model.addAttribute("oldPassword", "");
        // Set newPassword rong de input newPassword tren form co gia tri mac dinh.
        model.addAttribute("newPassword", "");
        // Set confirmPassword rong de input confirmPassword tren form co gia tri mac dinh.
        model.addAttribute("confirmPassword", "");
        // Tra ve template src/main/resources/templates/reset-password.html.
        return "reset-password";
    }

    // Map POST /reset-password khi user submit form doi mat khau.
    @PostMapping("/reset-password")
    public String processResetPassword(
            // Lay input name="oldPassword"; required=false de controller tu xu ly khi field rong.
            @RequestParam(value = "oldPassword", required = false) String oldPassword,
            // Lay input name="newPassword"; required=false tranh loi request khi user submit rong.
            @RequestParam(value = "newPassword", required = false) String newPassword,
            // Lay input name="confirmPassword" de so sanh voi password moi.
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            // Session dung de lay loggedInUser va cap nhat lai sau khi doi password.
            HttpSession session,
            // Model dung de dua loi field/success message ve reset-password.html.
            Model model) {

        // Lay user dang dang nhap tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Neu session khong co user thi bat dang nhap lai.
        if (loggedInUser == null) {
            // Redirect ve login vi reset password chi danh cho user da dang nhap.
            return "redirect:/login";
        }

        // Lay account moi nhat tu database bang accountID trong session.
        Account account = accountService.findById(loggedInUser.getAccountID());
        // Neu khong tim thay account thi khong the doi password.
        if (account == null) {
            // Gan loi tong cho form de hien thong bao account khong ton tai.
            model.addAttribute("formError", "Tài khoản không tồn tại.");
            // Tra lai form va giu cac gia tri user da nhap.
            return prepareForm(model, oldPassword, newPassword, confirmPassword);
        }

        try {
            // Goi service validate old/new/confirm password va save password moi neu hop le.
            accountService.resetPassword(account, oldPassword, newPassword, confirmPassword);
            // Cap nhat account trong session de session co password/trang thai moi nhat.
            session.setAttribute("loggedInUser", account);
            // Ghi activity log de he thong luu lai hanh dong doi mat khau.
            activityLogService.log(account.getAccountID(), ActionType.PASSWORD_CHANGE, "Đổi mật khẩu thành công");
            // Gui successMessage sang view de thong bao user doi mat khau thanh cong.
            model.addAttribute("successMessage", "Đổi mật khẩu thành công!");
            // Reset 3 field password ve rong sau khi thanh cong.
            return prepareForm(model, "", "", "");
        } catch (IllegalArgumentException e) {
            // Neu service nem loi validate thi map message vao dung field loi.
            addPasswordFieldError(model, e.getMessage());
            // Tra lai form va giu gia tri user da nhap de user sua tiep.
            return prepareForm(model, oldPassword, newPassword, confirmPassword);
        }
    }

    // Helper nay chuan bi lai model truoc khi render reset-password.html.
    private String prepareForm(Model model, String oldPassword, String newPassword, String confirmPassword) {
        // Neu oldPassword null thi day chuoi rong de input khong bi null.
        model.addAttribute("oldPassword", oldPassword == null ? "" : oldPassword);
        // Neu newPassword null thi day chuoi rong de input khong bi null.
        model.addAttribute("newPassword", newPassword == null ? "" : newPassword);
        // Neu confirmPassword null thi day chuoi rong de input khong bi null.
        model.addAttribute("confirmPassword", confirmPassword == null ? "" : confirmPassword);
        // Tra ve template reset-password.html sau khi model da san sang.
        return "reset-password";
    }

    // Helper nay chuyen message loi tu service thanh loi dung field tren form.
    private void addPasswordFieldError(Model model, String message) {
        // Neu message null/rong thi hien loi chung thay vi loi field.
        if (message == null || message.isBlank()) {
            // formError la loi tong nam tren form.
            model.addAttribute("formError", "Không đổi được mật khẩu. Vui lòng thử lại.");
            // return de khong chay tiep switch ben duoi.
            return;
        }

        // Phan loai message de gan loi vao dung input tren HTML.
        switch (message) {
            // Cac loi lien quan password cu se hien duoi input oldPassword.
            case "Old password is incorrect", "Mật khẩu cũ không được để trống" ->
                    // Neu service tra message tieng Anh thi doi sang message tieng Viet cho UI.
                    model.addAttribute("oldPasswordError", message.equals("Old password is incorrect")
                            ? "Mật khẩu cũ không đúng"
                            : message);
            case "Mật khẩu mới không được để trống", "New password must be different from old password", "New password must be 8-20 characters" ->
                    // Cac loi lien quan password moi se hien duoi input newPassword.
                    // switch expression nay doi cac message rule tieng Anh sang tieng Viet.
                    model.addAttribute("newPasswordError", switch (message) {
                        case "New password must be different from old password" -> "Mật khẩu mới phải khác mật khẩu cũ";
                        case "New password must be 8-20 characters" -> "Mật khẩu mới phải từ 8 đến 20 ký tự";
                        default -> message;
                    });
            case "Xác nhận mật khẩu không được để trống", "Confirm password does not match" ->
                    // Cac loi lien quan confirm password se hien duoi input confirmPassword.
                    // Neu confirm khong khop thi doi message sang tieng Viet de hien tren UI.
                    model.addAttribute("confirmPasswordError", message.equals("Confirm password does not match")
                            ? "Mật khẩu xác nhận không khớp"
                            : message);
            // Neu message khong thuoc field nao o tren thi hien loi chung.
            default -> model.addAttribute("formError", message);
        }
    }
}
