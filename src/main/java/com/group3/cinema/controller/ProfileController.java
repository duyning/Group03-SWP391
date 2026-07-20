package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.ActivityLog;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.ActivityLogService;
import com.group3.cinema.service.LoyaltyService;
import com.group3.cinema.repository.VoucherRepository;
import com.group3.cinema.repository.AccountRepository;
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

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private LoyaltyService loyaltyService;

    @Autowired
    private AccountRepository accountRepository;

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

        // Tự động kiểm tra và sửa đổi hạng thành viên nếu không đúng với số điểm hiện có (Self-healing)
        int points = account.getLoyaltyPoint();
        com.group3.cinema.entity.MembershipLevel correctLevel = com.group3.cinema.entity.MembershipLevel.BRONZE;
        if (points >= 5000) {
            correctLevel = com.group3.cinema.entity.MembershipLevel.GOLD;
        } else if (points >= 1000) {
            correctLevel = com.group3.cinema.entity.MembershipLevel.SILVER;
        }
        if (account.getMembershipLevel() != correctLevel) {
            account.setMembershipLevel(correctLevel);
            accountRepository.save(account);
        }

        // Tự động kiểm tra và cấp voucher định kỳ hàng tháng cho hạng Vàng
        loyaltyService.checkAndGrantGoldMonthlyVoucher(account);

        // Nạp lại thông tin mới nhất
        account = accountService.findById(loggedInUser.getAccountID());
        points = account.getLoyaltyPoint();
        com.group3.cinema.entity.MembershipLevel level = account.getMembershipLevel() != null
                ? account.getMembershipLevel() : com.group3.cinema.entity.MembershipLevel.BRONZE;

        String tierName = "Đồng";
        String nextTierName = "Bạc";
        int pointsNeeded = 0;
        int progressPercent = 0;
        int threshold = 0;

        if (level == com.group3.cinema.entity.MembershipLevel.BRONZE) {
            tierName = "Đồng";
            nextTierName = "Bạc";
            threshold = 1000;
            pointsNeeded = Math.max(0, 1000 - points);
            progressPercent = Math.min(100, (points * 100) / 1000);
        } else if (level == com.group3.cinema.entity.MembershipLevel.SILVER) {
            tierName = "Bạc";
            nextTierName = "Vàng";
            threshold = 5000;
            pointsNeeded = Math.max(0, 5000 - points);
            progressPercent = Math.min(100, ((points - 1000) * 100) / 4000);
        } else if (level == com.group3.cinema.entity.MembershipLevel.GOLD || level == com.group3.cinema.entity.MembershipLevel.PLAT) {
            tierName = "Vàng";
            nextTierName = "Đã đạt cấp tối đa";
            threshold = 5000;
            pointsNeeded = 0;
            progressPercent = 100;
        }

        // Lấy danh sách ví voucher (lịch sử nhận thưởng)
        List<com.group3.cinema.entity.Voucher> walletVouchers = voucherRepository.findWalletVouchers(account.getAccountID());
        List<java.util.Map<String, Object>> formattedVouchers = new java.util.ArrayList<>();
        for (com.group3.cinema.entity.Voucher v : walletVouchers) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("code", v.getCode());
            map.put("title", v.getTitle());
            
            String discountText = "";
            if (v.getDiscountType() == com.group3.cinema.entity.Voucher.DiscountType.PERCENTAGE) {
                discountText = "Giảm " + v.getDiscountValue().stripTrailingZeros().toPlainString() + "%" 
                        + (v.getMaxDiscountAmount() != null ? " (Tối đa " + String.format("%,.0f", v.getMaxDiscountAmount().doubleValue()) + "đ)" : "");
            } else {
                discountText = "Giảm " + String.format("%,.0f", v.getDiscountValue().doubleValue()) + "đ";
            }
            map.put("discountText", discountText);
            map.put("minOrderText", "Đơn tối thiểu " + String.format("%,.0f", v.getMinOrderValue().doubleValue()) + "đ");
            map.put("expiryText", v.getEndDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            
            String statusText = "Chưa dùng";
            String statusClass = "badge-available";
            if (v.getUsedQuantity() != null && v.getUsedQuantity() > 0) {
                statusText = "Đã dùng";
                statusClass = "badge-used";
            } else if (v.getEndDate().isBefore(java.time.LocalDateTime.now())) {
                statusText = "Hết hạn";
                statusClass = "badge-expired";
            }
            map.put("statusText", statusText);
            map.put("statusClass", statusClass);
            
            formattedVouchers.add(map);
        }

        model.addAttribute("account", account);
        model.addAttribute("tierName", tierName);
        model.addAttribute("nextTierName", nextTierName);
        model.addAttribute("pointsNeeded", pointsNeeded);
        model.addAttribute("progressPercent", progressPercent);
        model.addAttribute("threshold", threshold);
        model.addAttribute("formattedVouchers", formattedVouchers);
        model.addAttribute("active", "profile");
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
