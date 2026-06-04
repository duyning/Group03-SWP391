package example.controller;

import example.entity.Account;
import example.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller xử lý hiển thị hồ sơ cá nhân (Profile).
 * Tải thông tin mới nhất của người dùng đã đăng nhập từ cơ sở dữ liệu để hiển thị.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private AccountService accountService;

    @GetMapping
    public String viewProfile(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Reload fresh from DB
        Account account = accountService.findById(loggedInUser.getAccountID());
        if (account == null) {
            return "redirect:/login";
        }

        model.addAttribute("account", account);
        return "profile";
    }
}
