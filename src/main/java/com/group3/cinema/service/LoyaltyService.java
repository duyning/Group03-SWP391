package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.entity.NotificationType;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LoyaltyService {

    private final AccountRepository accountRepository;
    private final VoucherRepository voucherRepository;
    private final NotificationService notificationService;

    public LoyaltyService(AccountRepository accountRepository,
                          VoucherRepository voucherRepository,
                          NotificationService notificationService) {
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.notificationService = notificationService;
    }

    /**
     * Tích lũy điểm và thăng hạng dựa trên số tiền chi tiêu.
     */
    @Transactional
    public void addLoyaltyPoints(int accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        try {
            accountRepository.findById(accountId)
                    .ifPresent(account -> applyLoyaltyPoints(account, amount));
        } catch (Exception ex) {
            System.err.println("Warning: Failed to add loyalty points for account " + accountId + ": " + ex.getMessage());
        }
    }

    /**
     * Strict payment variant: failures must propagate so the enclosing payment transaction can roll back.
     */
    @Transactional
    public void addLoyaltyPointsStrict(int accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Cannot find account for loyalty points."));
        applyLoyaltyPoints(account, amount);
    }

    private void applyLoyaltyPoints(Account account, BigDecimal amount) {
        int accountId = account.getAccountID();
        int pointsEarned = amount.divide(new BigDecimal("1000"), 0, RoundingMode.DOWN).intValue();
        if (pointsEarned <= 0) {
            return;
        }

        int oldPoints = account.getLoyaltyPoint();
        int newPoints = oldPoints + pointsEarned;
        account.setLoyaltyPoint(newPoints);

        MembershipLevel oldLevel = account.getMembershipLevel();
        if (oldLevel == null) {
            oldLevel = MembershipLevel.BRONZE;
        }

        MembershipLevel newLevel;
        if (newPoints >= 5000) {
            newLevel = MembershipLevel.GOLD;
        } else if (newPoints >= 1000) {
            newLevel = MembershipLevel.SILVER;
        } else {
            newLevel = MembershipLevel.BRONZE;
        }

        account.setMembershipLevel(newLevel);
        accountRepository.save(account);
        notificationService.sendNotification(
                accountId,
                "Tích lũy điểm thành công! 🎟️",
                "Bạn đã tích lũy thêm " + pointsEarned + " điểm từ giao dịch trị giá " + amount.intValue() + "đ. Tổng điểm hiện tại: " + newPoints + " điểm.",
                NotificationType.VOUCHER
        );
        handleRankUpRewards(account, oldLevel, newLevel);
    }

    /**
     * Kiểm tra thăng hạng và tự động phát voucher tương ứng.
     */
    private void handleRankUpRewards(Account account, MembershipLevel oldLevel, MembershipLevel newLevel) {
        int accountId = account.getAccountID();

        // 1. Nhảy từ Bronze lên Silver (hoặc nhảy vọt thẳng lên Gold)
        if (oldLevel == MembershipLevel.BRONZE && (newLevel == MembershipLevel.SILVER || newLevel == MembershipLevel.GOLD)) {
            // Phát voucher thăng hạng Bạc: 15% tối đa 40.000đ, tối thiểu 150.000đ, hạn 15 ngày
            Voucher silverVoucher = createCustomerVoucher(
                    account, "SV", "Voucher Thăng Hạng Bạc", 15, 40000, 150000, 15
            );
            notificationService.sendNotification(
                    accountId,
                    "Thăng hạng Bạc thành công! 🎉",
                    "Chúc mừng bạn đã đạt hạng Bạc! Hệ thống đã gửi tặng bạn 1 Voucher thăng hạng Bạc (giảm 15%, tối đa 40.000đ cho đơn từ 150.000đ). Hạn sử dụng: 15 ngày.",
                    NotificationType.VOUCHER
            );
        }

        // 2. Nhảy lên Gold (từ Bronze hoặc Silver)
        if ((oldLevel == MembershipLevel.BRONZE || oldLevel == MembershipLevel.SILVER) && newLevel == MembershipLevel.GOLD) {
            // Phát voucher thăng hạng Vàng: 25% tối đa 60.000đ, tối thiểu 250.000đ, hạn 15 ngày
            Voucher goldVoucher = createCustomerVoucher(
                    account, "GV", "Voucher Thăng Hạng Vàng", 25, 60000, 250000, 15
            );
            notificationService.sendNotification(
                    accountId,
                    "Thăng hạng Vàng thành công! 🌟",
                    "Chúc mừng bạn đã đạt hạng Vàng! Hệ thống đã gửi tặng bạn 1 Voucher thăng hạng Vàng (giảm 25%, tối đa 60.000đ cho đơn từ 250.000đ). Hạn sử dụng: 15 ngày. Bạn cũng sẽ nhận được 1 voucher này tự động mỗi tháng!",
                    NotificationType.VOUCHER
            );
        }
    }

    /**
     * Tự động kiểm tra và cấp voucher Vàng định kỳ hàng tháng cho thành viên hạng Vàng khi truy cập profile.
     */
    @Transactional
    public void checkAndGrantGoldMonthlyVoucher(Account account) {
        if (account == null || account.getMembershipLevel() != MembershipLevel.GOLD) {
            return;
        }

        try {
            // Lấy danh sách ví voucher để kiểm tra xem đã nhận voucher Vàng trong vòng 30 ngày qua chưa
            List<Voucher> wallet = voucherRepository.findWalletVouchers(account.getAccountID());
            boolean receivedRecently = wallet.stream()
                    .anyMatch(v -> v.getCode().startsWith("GV-" + account.getAccountID())
                            && v.getCreatedAt() != null
                            && v.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)));

            if (!receivedRecently) {
                Voucher monthlyVoucher = createCustomerVoucher(
                        account, "GV", "Voucher Thành Viên Vàng Hàng Tháng", 25, 60000, 250000, 15
                );
                notificationService.sendNotification(
                        account.getAccountID(),
                        "Voucher Vàng Hàng Tháng! 🎁",
                        "Bạn vừa nhận được Voucher Vàng hàng tháng trị giá giảm 25% tối đa 60.000đ. Hãy kiểm tra ví voucher và sử dụng nhé!",
                        NotificationType.VOUCHER
                );
            }
        } catch (Exception ex) {
            System.err.println("Warning: Failed to check or grant monthly Gold voucher for account " + account.getAccountID() + ": " + ex.getMessage());
        }
    }

    /**
     * Tạo voucher riêng dành cho khách hàng.
     */
    private Voucher createCustomerVoucher(Account account, String prefix, String title, double discountPercent,
                                          double maxDiscount, double minOrder, int expiryDays) {
        Voucher v = new Voucher();
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        v.setCode(prefix + "-" + account.getAccountID() + "-" + unique);
        v.setTitle(title);
        v.setDiscountType(Voucher.DiscountType.PERCENTAGE);
        v.setDiscountValue(new BigDecimal(discountPercent));
        v.setMaxDiscountAmount(new BigDecimal(maxDiscount));
        v.setMinOrderValue(new BigDecimal(minOrder));
        v.setStartDate(LocalDateTime.now());
        v.setEndDate(LocalDateTime.now().plusDays(expiryDays));
        v.setTotalQuantity(1);
        v.setUsedQuantity(0);
        v.setLimitPerUser(1);
        v.setServiceScope(Voucher.ServiceScope.ALL);
        v.setApplicableDays(Voucher.ApplicableDay.ALL);
        v.setIsHolidayApplicable(true);
        v.setIsDeleted(false);

        Voucher saved = voucherRepository.save(savedVouchersFix(v));
        voucherRepository.addToWallet(account.getAccountID(), saved.getId());
        return saved;
    }

    private Voucher savedVouchersFix(Voucher v) {
        // Bảo đảm các trường null được khởi tạo an toàn
        if (v.getUsedQuantity() == null) v.setUsedQuantity(0);
        if (v.getLimitPerUser() == null) v.setLimitPerUser(1);
        if (v.getIsHolidayApplicable() == null) v.setIsHolidayApplicable(true);
        if (v.getIsDeleted() == null) v.setIsDeleted(false);
        return v;
    }
}
