package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.ActivityLog;
import com.group3.cinema.entity.ActivityLog.ActionType;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.ActivityLogService;
import com.group3.cinema.service.LoyaltyService;
import com.group3.cinema.repository.VoucherRepository;
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
// Tat ca URL trong controller nay deu bat dau bang /profile.
@RequestMapping("/profile")
public class ProfileController {

    // Service xu ly doc va cap nhat du lieu Account.
    @Autowired
    private AccountService accountService;

    // Service ghi va truy van lich su hoat dong cua tai khoan.
    @Autowired
    private ActivityLogService activityLogService;

    // Repository doc cac voucher trong vi cua tai khoan.
    @Autowired
    private VoucherRepository voucherRepository;

    // Service xu ly hang thanh vien va voucher dinh ky.
    @Autowired
    private LoyaltyService loyaltyService;

    // GET /profile: hien thi profile cua user dang dang nhap.
    @GetMapping
    public String viewProfile(HttpSession session, Model model) {
        // Lay Account da duoc luu trong session khi dang nhap thanh cong.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Neu session khong co user thi yeu cau dang nhap.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Reload fresh from DB
        // Dung ID trong session de tai ban ghi moi nhat tu database.
        Account account = accountService.findById(loggedInUser.getAccountID());
        // Account co the da bi xoa sau khi session duoc tao.
        if (account == null) {
            return "redirect:/login";
        }

        // Hạng hiển thị luôn được suy ra từ điểm hiện tại. Không ghi DB trong một GET request:
        // schema cũ có thể còn CHECK constraint không chứa BRONZE và làm /profile lỗi 500.
        // Doc tong diem tich luy hien tai cua tai khoan.
        int points = account.getLoyaltyPoint();
        // Quy doi diem thanh hang BRONZE, SILVER hoac GOLD.
        MembershipLevel effectiveLevel = resolveMembershipLevel(points);
        // Gan hang vua tinh vao object de template co the hien thi.
        account.setMembershipLevel(effectiveLevel);

        // Tự động kiểm tra và cấp voucher định kỳ hàng tháng cho hạng Vàng
        // Service chi cap voucher neu account dat hang GOLD va chua nhan gan day.
        loyaltyService.checkAndGrantGoldMonthlyVoucher(account);

        // Nạp lại thông tin mới nhất
        // Tai lai vi service o tren co the vua thay doi du lieu voucher trong DB.
        account = accountService.findById(loggedInUser.getAccountID());
        // Doc lai diem tu ban ghi moi nhat.
        points = account.getLoyaltyPoint();
        // Tinh lai hang thanh vien tu diem moi nhat.
        MembershipLevel level = resolveMembershipLevel(points);
        // Dua hang thanh vien vao object gui sang view.
        account.setMembershipLevel(level);

        String tierName = "Đồng";
        String nextTierName = "Bạc";
        // So diem con thieu de len hang; se duoc tinh theo tung nhanh.
        int pointsNeeded = 0;
        // Phan tram dung de dat do rong thanh tien do tren HTML.
        int progressPercent = 0;
        // Moc diem cua hang tiep theo.
        int threshold = 0;

        // Chon ten hang, moc diem va tien do dua tren hang hien tai.
        if (level == MembershipLevel.BRONZE) {
            tierName = "Đồng";
            nextTierName = "Bạc";
            // Hang Bac bat dau tu 1.000 diem.
            threshold = 1000;
            // Math.max dam bao so diem con thieu khong bi am.
            pointsNeeded = Math.max(0, 1000 - points);
            // Math.min gioi han tien do toi da 100%.
            progressPercent = Math.min(100, (points * 100) / 1000);
        } else if (level == MembershipLevel.SILVER) {
            tierName = "Bạc";
            nextTierName = "Vàng";
            // Hang Vang bat dau tu 5.000 diem.
            threshold = 5000;
            // Tinh so diem con thieu den moc 5.000.
            pointsNeeded = Math.max(0, 5000 - points);
            // Tinh tien do trong khoang tu 1.000 den 5.000 diem.
            progressPercent = Math.min(100, ((points - 1000) * 100) / 4000);
        } else if (level == MembershipLevel.GOLD || level == MembershipLevel.PLAT) {
            tierName = "Vàng";
            nextTierName = "Đã đạt cấp tối đa";
            // Giu moc 5.000 de model co du thong tin.
            threshold = 5000;
            // Hang cao nhat khong con thieu diem.
            pointsNeeded = 0;
            // Thanh tien do luon day.
            progressPercent = 100;
        }

        // Lấy danh sách ví voucher (lịch sử nhận thưởng)
        // Query tat ca voucher trong vi cua account, sap xep theo han su dung.
        List<com.group3.cinema.entity.Voucher> walletVouchers = voucherRepository.findWalletVouchers(account.getAccountID());
        // Chuyen Entity Voucher thanh Map de template chi doc du lieu da format.
        List<java.util.Map<String, Object>> formattedVouchers = new java.util.ArrayList<>();
        // Xu ly tung voucher thanh mot dong du lieu cho bang tren profile.html.
        for (com.group3.cinema.entity.Voucher v : walletVouchers) {
            // Moi Map chua cac gia tri ma mot dong HTML can su dung.
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            // Luu ma voucher.
            map.put("code", v.getCode());
            // Luu ten phan thuong.
            map.put("title", v.getTitle());
            
            // Bien chua muc giam da format thanh chuoi.
            String discountText = "";
            // Tach cach hien thi voucher phan tram va voucher giam tien co dinh.
            if (v.getDiscountType() == com.group3.cinema.entity.Voucher.DiscountType.PERCENTAGE) {
                discountText = "Giảm " + v.getDiscountValue().stripTrailingZeros().toPlainString() + "%" 
                        + (v.getMaxDiscountAmount() != null ? " (Tối đa " + String.format("%,.0f", v.getMaxDiscountAmount().doubleValue()) + "đ)" : "");
            } else {
                discountText = "Giảm " + String.format("%,.0f", v.getDiscountValue().doubleValue()) + "đ";
            }
            // Dua muc giam da format vao Map.
            map.put("discountText", discountText);
            map.put("minOrderText", "Đơn tối thiểu " + String.format("%,.0f", v.getMinOrderValue().doubleValue()) + "đ");
            map.put("expiryText", v.getEndDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            
            String statusText = "Chưa dùng";
            // CSS class mac dinh cua voucher chua dung.
            String statusClass = "badge-available";
            // Uu tien trang thai da dung, sau do moi kiem tra het han.
            if (v.getUsedQuantity() != null && v.getUsedQuantity() > 0) {
                statusText = "Đã dùng";
                statusClass = "badge-used";
            } else if (v.getEndDate().isBefore(java.time.LocalDateTime.now())) {
                statusText = "Hết hạn";
                statusClass = "badge-expired";
            }
            // Dua nhan trang thai sang view.
            map.put("statusText", statusText);
            // Dua CSS class tuong ung sang view.
            map.put("statusClass", statusClass);
            
            // Them dong voucher da format vao danh sach ket qua.
            formattedVouchers.add(map);
        }

        // Entity account cung cap thong tin ca nhan cho profile.html.
        model.addAttribute("account", account);
        // Ten hang thanh vien hien tai.
        model.addAttribute("tierName", tierName);
        // Ten hang muc tieu tiep theo.
        model.addAttribute("nextTierName", nextTierName);
        // So diem con thieu den hang tiep theo.
        model.addAttribute("pointsNeeded", pointsNeeded);
        // Phan tram cua thanh tien do.
        model.addAttribute("progressPercent", progressPercent);
        // Moc diem de hien dang diem-hien-tai/moc-diem.
        model.addAttribute("threshold", threshold);
        // Danh sach voucher da format.
        model.addAttribute("formattedVouchers", formattedVouchers);
        // Danh dau menu profile dang duoc chon.
        model.addAttribute("active", "profile");
        // Render src/main/resources/templates/profile.html.
        return "profile";
    }

    // Ham noi bo quy doi diem tich luy thanh hang thanh vien.
    private MembershipLevel resolveMembershipLevel(int points) {
        // Tu 5.000 diem tro len la hang Vang.
        if (points >= 5000) {
            return MembershipLevel.GOLD;
        }
        // Tu 1.000 den duoi 5.000 diem la hang Bac.
        if (points >= 1000) {
            return MembershipLevel.SILVER;
        }
        // Duoi 1.000 diem la hang Dong.
        return MembershipLevel.BRONZE;
    }

    /**
     * Hiển thị trang chỉnh sửa hồ sơ cá nhân.
     * Yêu cầu người dùng phải đăng nhập. Lấy thông tin tài khoản hiện tại từ database để đưa vào form.
     */
    // GET /profile/edit: mo form sua thong tin ca nhan.
    @GetMapping("/edit")
    public String showEditProfile(HttpSession session, Model model) {
        // Doc user dang dang nhap tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Chan truy cap khi user chua dang nhap.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Tai account moi nhat de form khong hien du lieu cu trong session.
        Account account = accountService.findById(loggedInUser.getAccountID());
        // Neu khong tim thay account thi yeu cau dang nhap lai.
        if (account == null) {
            return "redirect:/login";
        }

        // Dua account sang form de cac th:value/th:text dien gia tri hien tai.
        model.addAttribute("account", account);
        // Render src/main/resources/templates/edit-profile.html.
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
    // POST /profile/edit: nhan va xu ly du lieu user submit tu form.
    @org.springframework.web.bind.annotation.PostMapping("/edit")
    public String processEditProfile(
            // Lay input name="name" tu form.
            @org.springframework.web.bind.annotation.RequestParam("name") String name,
            // Parse input dob theo yyyy-MM-dd; cho phep rong de controller tu validate.
            @org.springframework.web.bind.annotation.RequestParam(value = "dob", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate dob,
            // Lay gia tri gioi tinh.
            @org.springframework.web.bind.annotation.RequestParam("gender") String gender,
            // Lay dia chi.
            @org.springframework.web.bind.annotation.RequestParam("address") String address,
            // Lay so dien thoai; required=false de tu xu ly gia tri rong.
            @org.springframework.web.bind.annotation.RequestParam(value = "phoneNum", required = false) String phoneNum,
            // Session xac dinh tai khoan dang thao tac.
            HttpSession session,
            // Model chua loi validate va du lieu khi render lai form.
            Model model,
            // RedirectAttributes mang thong bao qua lan redirect.
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        // Lay user dang dang nhap tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Khong cho cap nhat khi chua dang nhap.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Tai ban ghi account moi nhat tu database.
        Account account = accountService.findById(loggedInUser.getAccountID());
        // Bao ve truong hop account da bi xoa.
        if (account == null) {
            return "redirect:/login";
        }

        // Co tong hop; chi luu DB khi tat ca quy tac validate deu dat.
        boolean hasError = false;

        // Validate Họ và tên: Phải có dữ liệu và không chỉ chứa khoảng trắng
        if (name == null || name.trim().isEmpty()) {
            // Gan nameError cho template va danh dau form dang co loi.
            model.addAttribute("nameError", "Vui lòng nhập họ và tên");
            hasError = true;
        }

        // Validate Ngày sinh: Phải có dữ liệu, không ở tương lai và đảm bảo tuổi từ 13 đến 100
        if (dob == null) {
            // Dob la truong bat buoc.
            model.addAttribute("dobError", "Vui lòng nhập ngày sinh");
            hasError = true;
        } else if (dob.isAfter(java.time.LocalDate.now())) {
            // Khong chap nhan ngay sinh sau ngay hien tai.
            model.addAttribute("dobError", "Ngày sinh không thể ở tương lai");
            hasError = true;
        } else {
            // Period tinh so tuoi tron tu ngay sinh den hom nay.
            int calculatedAge = java.time.Period.between(dob, java.time.LocalDate.now()).getYears();
            // Gioi han tuoi hop le cua customer tu 13 den 100.
            if (calculatedAge < 13 || calculatedAge > 100) {
                model.addAttribute("dobError", "Tuổi không hợp lệ (phải từ 13 đến 100 tuổi).");
                hasError = true;
            }
        }
        
        // Validate Số điện thoại
        if (phoneNum != null && !phoneNum.trim().isEmpty()) {
            // Neu user co nhap so dien thoai thi phai dung 10 chu so.
            if (!phoneNum.matches("^\\d{10}$")) {
                model.addAttribute("phoneError", "Số điện thoại không hợp lệ (phải gồm 10 chữ số).");
                hasError = true;
            } else if (accountService.isPhoneNumTakenByOther(phoneNum, account.getAccountID())) {
                // Khong cho hai account khac nhau dung chung mot so dien thoai.
                model.addAttribute("phoneError", "Số điện thoại này đã được sử dụng bởi người dùng khác.");
                hasError = true;
            }
        }

        if (hasError) {
            // Nếu có lỗi, cập nhật lại object account với dữ liệu vừa nhập
            // để trả về form (giúp người dùng không phải nhập lại từ đầu - retain user input)
            // Ghi gia tri vua submit vao account tam de form khong bi mat du lieu.
            account.setName(name);
            account.setDob(dob);
            account.setGender(gender);
            account.setAddress(address);
            // Chi ghi de so dien thoai tam khi user co nhap.
            if (phoneNum != null && !phoneNum.trim().isEmpty()) {
                account.setPhoneNum(phoneNum.trim());
            }
            // Gui account tam va cac error attribute ve lai template.
            model.addAttribute("account", account);
            return "edit-profile";
        }

        // Nếu dữ liệu hợp lệ, gọi service để lưu vào database
        // Service cap nhat Entity va save thay doi xuong database.
        accountService.updateProfile(account, name, dob, gender, address, phoneNum);

        // Ghi nhật ký cập nhật hồ sơ
        // Ghi PROFILE_UPDATE de trang Activity Log hien hanh dong nay.
        activityLogService.log(account.getAccountID(), ActionType.PROFILE_UPDATE,
                "Cập nhật thông tin hồ sơ cá nhân");

        // Cập nhật lại thông tin tài khoản mới trong session để các chức năng khác hoạt động chính xác
        // Thay account cu trong session bang account vua cap nhat.
        session.setAttribute("loggedInUser", account);

        // Truyền thông báo thành công sang trang tiếp theo thông qua Flash Attribute
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin thành công!");

        // Redirect de tranh submit lai form khi user refresh trang.
        return "redirect:/profile";
    }

    /**
     * Hiển thị trang Nhật ký hoạt động (Activity Log)
     * GET /profile/activity-log
     */
    // GET /profile/activity-log: hien lich su hoat dong cua tai khoan.
    @GetMapping("/activity-log")
    public String viewActivityLog(HttpSession session, Model model) {
        // Lay user hien tai tu session.
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        // Nhat ky la du lieu rieng tu, chi user da dang nhap moi duoc xem.
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Query log cua dung account, sap xep moi nhat truoc.
        List<ActivityLog> logs = activityLogService.getLogsForAccount(loggedInUser.getAccountID());
        // Dua danh sach log sang activity-log.html.
        model.addAttribute("logs", logs);
        // Dua user sang view cho cac thanh phan header neu can.
        model.addAttribute("user", loggedInUser);
        // Render src/main/resources/templates/activity-log.html.
        return "activity-log";
    }
}
