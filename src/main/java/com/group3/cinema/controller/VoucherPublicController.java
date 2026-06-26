package com.group3.cinema.controller;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.service.AccountService;
import com.group3.cinema.service.VoucherService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class VoucherPublicController {

    private final VoucherService voucherService;
    private final AccountService accountService;

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
            // Duyệt danh sách voucher tài khoản đã lưu để đánh dấu trạng thái "Đã lưu" trên giao diện
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

        // Đổ danh sách voucher đã lọc sạch (isDeleted = false & endDate > now) ra kho voucher
        model.addAttribute("vouchers", voucherService.getAllVouchers());
        return "voucher-public-list";
    }

    /**
     * 2. XỬ LÝ LƯU VOUCHER VÀO VÍ
     */
    @PostMapping("/vouchers/collect/{id}")
    @ResponseBody
    public String collectVoucher(@PathVariable Long id, HttpSession session) {
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account == null) {
            return "error_login";
        }

        try {
            voucherService.collectVoucher(account.getAccountID(), id);
            return "success";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "error";
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