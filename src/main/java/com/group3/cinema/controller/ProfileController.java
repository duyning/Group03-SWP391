package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.ActivityLog;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.ActivityLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;

/**
 * Controller xử lý hiển thị hồ sơ cá nhân (Profile).
 * Tải thông tin mới nhất của người dùng đã đăng nhập từ cơ sở dữ liệu để hiển thị.
 *
 * Ngày thực hiện: 04/06/2026
 * Ngày cập nhật: 25/06/2026 - Thêm chức năng Edit Profile và validate dữ liệu.
 * Tạo bởi: DuongND_HE186619
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private ActivityLogService activityLogService;

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

    /**
     * Hiển thị trang chỉnh sửa hồ sơ cá nhân.
     * Yêu cầu người dùng phải đăng nhập. Lấy thông tin tài khoản hiện tại từ database để đưa vào form.
     */
    @GetMapping("/edit")
    public String showEditProfile(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Account account = accountService.findById(loggedInUser.getAccountID());
        if (account == null) {
            return "redirect:/login";
        }

        model.addAttribute("account", account);
        return "edit-profile";
    }

    /**
     * Xử lý lưu thông tin hồ sơ sau khi người dùng chỉnh sửa.
     * Thực hiện validate (kiểm tra) dữ liệu đầu vào:
     * - Tên không được để trống.
     * - Ngày sinh không được để trống, không ở tương lai, và tuổi phải từ 13 đến 100.
     *
     * Nếu có lỗi: Trả về trang edit cùng thông báo lỗi, giữ nguyên dữ liệu người dùng vừa nhập.
     * Nếu hợp lệ: Cập nhật thông tin vào database và session, sau đó chuyển hướng về trang profile.
     */
    @org.springframework.web.bind.annotation.PostMapping("/edit")
    public String processEditProfile(
            @org.springframework.web.bind.annotation.RequestParam("name") String name,
            @org.springframework.web.bind.annotation.RequestParam(value = "dob", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate dob,
            @org.springframework.web.bind.annotation.RequestParam("gender") String gender,
            @org.springframework.web.bind.annotation.RequestParam("address") String address,
            @org.springframework.web.bind.annotation.RequestParam(value = "phoneNum", required = false) String phoneNum,
            HttpSession session,
            Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Account account = accountService.findById(loggedInUser.getAccountID());
        if (account == null) {
            return "redirect:/login";
        }

        boolean hasError = false;

        // Validate Họ và tên: Phải có dữ liệu và không chỉ chứa khoảng trắng
        if (name == null || name.trim().isEmpty()) {
            model.addAttribute("nameError", "Vui lòng nhập họ và tên");
            hasError = true;
        }

        // Validate Ngày sinh: Phải có dữ liệu, không ở tương lai và đảm bảo tuổi từ 13 đến 100
        if (dob == null) {
            model.addAttribute("dobError", "Vui lòng nhập ngày sinh");
            hasError = true;
        } else if (dob.isAfter(java.time.LocalDate.now())) {
            model.addAttribute("dobError", "Ngày sinh không thể ở tương lai");
            hasError = true;
        } else {
            int calculatedAge = java.time.Period.between(dob, java.time.LocalDate.now()).getYears();
            if (calculatedAge < 13 || calculatedAge > 100) {
                model.addAttribute("dobError", "Tuổi không hợp lệ (phải từ 13 đến 100 tuổi).");
                hasError = true;
            }
        }
        
        // Validate Số điện thoại
        if (phoneNum != null && !phoneNum.trim().isEmpty()) {
            if (!phoneNum.matches("^\\d{10}$")) {
                model.addAttribute("phoneError", "Số điện thoại không hợp lệ (phải gồm 10 chữ số).");
                hasError = true;
            } else if (accountService.isPhoneNumTakenByOther(phoneNum, account.getAccountID())) {
                model.addAttribute("phoneError", "Số điện thoại này đã được sử dụng bởi người dùng khác.");
                hasError = true;
            }
        }

        if (hasError) {
            // Nếu có lỗi, cập nhật lại object account với dữ liệu vừa nhập
            // để trả về form (giúp người dùng không phải nhập lại từ đầu - retain user input)
            account.setName(name);
            account.setDob(dob);
            account.setGender(gender);
            account.setAddress(address);
            if (phoneNum != null && !phoneNum.trim().isEmpty()) {
                account.setPhoneNum(phoneNum.trim());
            }
            model.addAttribute("account", account);
            return "edit-profile";
        }

        // Nếu dữ liệu hợp lệ, gọi service để lưu vào database
        accountService.updateProfile(account, name, dob, gender, address, phoneNum);

        // Ghi nhật ký cập nhật hồ sơ
        activityLogService.log(account.getAccountID(), ActionType.PROFILE_UPDATE,
                "Cập nhật thông tin hồ sơ cá nhân");

        // Cập nhật lại thông tin tài khoản mới trong session để các chức năng khác hoạt động chính xác
        session.setAttribute("loggedInUser", account);

        // Truyền thông báo thành công sang trang tiếp theo thông qua Flash Attribute
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin thành công!");

        return "redirect:/profile";
    }

    /**
     * Hiển thị trang Nhật ký hoạt động (Activity Log)
     * GET /profile/activity-log
     */
    @GetMapping("/activity-log")
    public String viewActivityLog(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        List<ActivityLog> logs = activityLogService.getLogsForAccount(loggedInUser.getAccountID());
        model.addAttribute("logs", logs);
        model.addAttribute("user", loggedInUser);
        return "activity-log";
    }
}
