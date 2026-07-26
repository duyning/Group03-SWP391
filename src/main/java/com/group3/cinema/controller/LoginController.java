package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.entity.Role;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xu ly dang nhap va dang xuat.
 * Nhiem vu chinh:
 * 1. Hien thi form login neu nguoi dung chua dang nhap.
 * 2. Xu ly email/password, kiem tra trang thai tai khoan va luu user vao
 * session.
 * 3. Dieu huong user sau khi dang nhap dua tren role hoac URL dang bi chan
 * truoc do.
 * 4. Xu ly dang xuat va ghi nhat ky hoat dong neu co the.
 */
@Controller
public class LoginController {

    // Service chua logic lien quan den tai khoan: login, tim account, validate mat
    // khau.
    @Autowired
    private AccountService accountService;

    // Service ghi log hoat dong cua user, vi du LOGIN/LOGOUT.
    @Autowired
    private ActivityLogService activityLogService;

    /**
     * Hien thi trang login.
     * Neu user da dang nhap roi thi khong hien lai form login nua,
     * ma chuyen user ve trang phu hop voi role hien tai.
     */
    @GetMapping("/login")
    public String showLoginForm(HttpSession session, Model model) {
        // Lay account dang dang nhap tu session neu co.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Neu session da co user, dieu huong theo role de tranh login lai.
        if (loggedInUser != null) {
            return redirectByRole(loggedInUser);
        }

        // Neu chua dang nhap, tra ve template login.html.
        return "login";
    }

    /**
     * Xu ly submit form login.
     * Nhan email/password tu form, kiem tra account, luu vao session va dieu huong.
     */
    @PostMapping("/login")
    public String processLogin(@RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        // Goi service de kiem tra email/password. Neu sai, service tra ve null.
        Account account = accountService.login(email, password);

        // Khong tim thay account hoac mat khau sai thi quay lai login va hien thong bao
        // loi.
        if (account == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email hoặc mật khẩu không đúng");
            return "redirect:/login?error";
        }

        // Neu account bi khoa/inactive thi khong cho dang nhap.
        if (!account.isStatus()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản của bạn đã bị khóa");
            return "redirect:/login?error";
        }

        // Dang nhap hop le: luu account vao session de cac request sau biet user da
        // login.
        session.setAttribute("loggedInUser", account);

        // Ghi nhat ky dang nhap. Neu ghi log loi thi chi in ra console, khong chan dang
        // nhap.
        try {
            activityLogService.log(account.getAccountID(), ActionType.LOGIN, "Dang nhap he thong", request);
        } catch (RuntimeException exception) {
            System.err.println("Khong the ghi nhat ky dang nhap: " + exception.getMessage());
        }

        // xu li truong hop user chua login nhap url de vao
        // Lay URL ma user muon vao truoc khi bi yeu cau dang nhap.
        Object redirectTarget = session.getAttribute("redirectAfterLogin");

        // Xoa URL tam sau khi lay ra de tranh redirect lai nhieu lan.
        session.removeAttribute("redirectAfterLogin");

        // Chi chap nhan redirect noi bo bat dau bang "/" va khong phai "//" de tranh
        // open redirect.
        if (redirectTarget instanceof String target
                && target.startsWith("/")
                && !target.startsWith("//")) {
            return "redirect:" + target;
        }

        // Neu khong co URL truoc do, dieu huong mac dinh theo role cua account.
        return redirectByRole(account);
    }

    /**
     * Xu ly dang xuat.
     * Ghi log logout neu user dang ton tai trong session, sau do huy session.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        // Lay user hien tai truoc khi huy session de con ghi log dang xuat.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");

        // Neu co user dang dang nhap thi ghi nhat ky logout.
        if (loggedInUser != null) {
            try {
                activityLogService.log(loggedInUser.getAccountID(), ActionType.LOGOUT, "Dang xuat he thong");
            } catch (RuntimeException exception) {
                // Loi ghi log khong duoc lam hong flow logout.
                System.err.println("Khong the ghi nhat ky dang xuat: " + exception.getMessage());
            }
        }

        // Huy toan bo session de xoa thong tin dang nhap va cac du lieu tam.
        session.invalidate();

        // Flash message chi ton tai qua lan redirect tiep theo de hien thong bao dang
        // xuat thanh cong.
        redirectAttributes.addFlashAttribute("successMessage", "Đăng xuất thành công!");

        // Redirect ve login kem query logout de frontend co the nhan biet trang thai
        // neu can.
        return "redirect:/login?logout";
    }

    /**
     * Dieu huong user sau khi dang nhap dua tren role.
     * ADMIN va MANAGER vao dashboard quan tri, CUSTOMER ve trang home.
     */
    private String redirectByRole(Account account) {
        // Admin/Manager dung chung dashboard quan tri.
        if (account.getRole() == Role.ADMIN || account.getRole() == Role.MANAGER) {
            return "redirect:/admin/dashboard";
        }

        // Cac role con lai, mac dinh la CUSTOMER, ve trang chu khach hang.
        return "redirect:/home";
    }
}
