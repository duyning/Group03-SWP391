package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.NotificationService;
import com.group3.cinema.service.VoucherService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class VoucherPublicController {

    private static final Logger log = LoggerFactory.getLogger(VoucherPublicController.class);

    private final VoucherService voucherService;
    private final AccountService accountService;
    private final NotificationService notificationService;

    @Autowired
    public VoucherPublicController(VoucherService voucherService,
                                   AccountService accountService,
                                   NotificationService notificationService) {
        this.voucherService = voucherService;
        this.accountService = accountService;
        this.notificationService = notificationService;
    }

    /**
     * 1. TRANG KHO VOUCHER CÔNG KHAI
     * Hiển thị các voucher chưa xóa mềm và còn hạn dùng
     */
    @Transactional(readOnly = true)
    @GetMapping("/vouchers")
    public String showVouchers(Model model, HttpSession session) {
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");

        if (sessionAccount != null) {
            Account currentAccount = accountService.findById(sessionAccount.getAccountID());
            model.addAttribute("user", currentAccount);

            Set<Long> savedVoucherIds = new HashSet<>();
            // Duyệt danh sách voucher tài khoản đã lưu để đánh dấu trạng thái "Đã lưu" trên
            // giao diện
            if (currentAccount != null && currentAccount.getSavedVouchers() != null) {
                for (Voucher v : currentAccount.getSavedVouchers()) {
                    savedVoucherIds.add(v.getId());
                }
            }
            model.addAttribute("savedVoucherIds", savedVoucherIds);
        } else {
            model.addAttribute("user", null);
            model.addAttribute("savedVoucherIds", new HashSet<Long>());
        }

        // Đổ danh sách voucher đã lọc sạch (isDeleted = false & endDate > now) ra kho
        // voucher
        model.addAttribute("vouchers", voucherService.getAllVouchers());
        return "voucher-public-list";
    }

    /**
     * 2. XỬ LÝ LƯU VOUCHER VÀO VÍ
     */
    @PostMapping("/vouchers/collect/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> collectVoucher(@PathVariable Long id, HttpSession session) {
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "login", "message", "Vui lòng đăng nhập trước khi lưu mã!"));
        }

        try {
            // Lưu voucher vào database
            voucherService.collectVoucher(account.getAccountID(), id);

            // Gửi thông báo vào Notification Center của user
            notificationService.sendNotification(
                    account.getAccountID(),
                    "Lưu mã ưu đãi thành công \uD83C\uDF81",
                    "Bạn vừa lưu thành công 1 voucher vào ví. Hãy vào phần 'Ví Voucher' để xem chi tiết và sử dụng nhé!",
                    NotificationType.VOUCHER
            );

            return ResponseEntity.ok(Map.of("status", "success", "message", "Lưu voucher thành công!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Không thể lưu voucher {} cho account {}", id, account.getAccountID(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Không thể lưu voucher lúc này. Vui lòng thử lại sau."));
        }
    }

    /**
     * 3. TRANG VÍ VOUCHER CỦA TÔI (MY WALLET)
     * Đã đồng bộ: Chỉ hiện voucher chưa bị ẩn và còn hạn dùng trong ví của khách
     */
    @Transactional(readOnly = true)
    @GetMapping("/vouchers/my-wallet")
    public String showMyWallet(Model model, HttpSession session) {
        Account sessionAccount = (Account) session.getAttribute("loggedInUser");
        if (sessionAccount == null) {
            return "redirect:/login";
        }

        Account currentAccount = accountService.findById(sessionAccount.getAccountID());
        model.addAttribute("user", currentAccount);

        List<Voucher> activeSavedVouchers = new ArrayList<>();

        // Chỉ thêm vào ví các voucher: chưa bị ẩn (isDeleted == false) và chưa quá hạn
        if (currentAccount != null && currentAccount.getSavedVouchers() != null) {
            LocalDateTime now = LocalDateTime.now();
            for (Voucher v : currentAccount.getSavedVouchers()) {
                if (!v.getIsDeleted() && v.getEndDate() != null && v.getEndDate().isAfter(now)) {
                    activeSavedVouchers.add(v);
                }
            }
        }

        // Truyền danh sách List an toàn xuống Thymeleaf render
        model.addAttribute("myVouchers", activeSavedVouchers);

        return "voucher-my-wallet";
    }
}
