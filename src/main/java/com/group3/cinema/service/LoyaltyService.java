/**
 * Service quản lý Điểm thưởng tích lũy, Hạng thành viên (BRONZE, SILVER, GOLD) và Thưởng tự động Voucher thăng hạng (`LoyaltyService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `PaymentService`, `CustomerBookingService`, `InvoiceService` sau khi khách hàng hoàn tất giao dịch bán vé/bắp nước thành công.
 * - Tương tác với:
 *   + `AccountRepository`: Cập nhật `loyaltyPoint` và `membershipLevel` của tài khoản.
 *   + `VoucherRepository`: Tạo và cấp trực tiếp voucher thăng hạng/voucher hàng tháng vào ví khách hàng (`save`, `addToWallet`).
 *   + `NotificationService`: Gửi thông báo cộng điểm và nhận voucher tới khách hàng.
 * 
 * Quy tắc cộng điểm & Hạng thành viên:
 * - 1.000 VNĐ chi tiêu = 1 điểm thưởng (`pointsEarned`).
 * - Từ 1.000 điểm: Hạng Bạc (`SILVER`) -> Thưởng 1 Voucher giảm 15% tối đa 40k.
 * - Từ 5.000 điểm: Hạng Vàng (`GOLD`) -> Thưởng 1 Voucher giảm 25% tối đa 60k và tự động cấp lại mỗi tháng (`checkAndGrantGoldMonthlyVoucher`).
 */
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
     * Tích lũy điểm thưởng dựa trên số tiền chi tiêu hợp lệ và nâng hạng thành viên tự động.
     * 
     * @param accountId ID tài khoản nhận điểm.
     * @param amount Số tiền giao dịch đã thanh toán thành công.
     */
    @Transactional
    public void addLoyaltyPoints(int accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        try {
            accountRepository.findById(accountId).ifPresent(account -> {
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

                MembershipLevel newLevel = oldLevel;
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
            });
        } catch (Exception ex) {
            System.err.println("Warning: Failed to add loyalty points for account " + accountId + ": " + ex.getMessage());
        }
    }

    /**
     * Kiểm tra thăng hạng thành viên và phát voucher tri ân tương ứng vào ví tài khoản.
     */
    private void handleRankUpRewards(Account account, MembershipLevel oldLevel, MembershipLevel newLevel) {
        int accountId = account.getAccountID();

        if (oldLevel == MembershipLevel.BRONZE && (newLevel == MembershipLevel.SILVER || newLevel == MembershipLevel.GOLD)) {
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

        if ((oldLevel == MembershipLevel.BRONZE || oldLevel == MembershipLevel.SILVER) && newLevel == MembershipLevel.GOLD) {
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
     * Kiểm tra và tự động cấp Voucher Vàng định kỳ 30 ngày một lần cho Khách hàng đạt hạng Vàng.
     */
    @Transactional
    public void checkAndGrantGoldMonthlyVoucher(Account account) {
        if (account == null || account.getMembershipLevel() != MembershipLevel.GOLD) {
            return;
        }

        try {
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
     * Tạo đối tượng Voucher dành riêng cho khách hàng và liên kết trực tiếp vào ví `customer_vouchers`.
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
        if (v.getUsedQuantity() == null) v.setUsedQuantity(0);
        if (v.getLimitPerUser() == null) v.setLimitPerUser(1);
        if (v.getIsHolidayApplicable() == null) v.setIsHolidayApplicable(true);
        if (v.getIsDeleted() == null) v.setIsDeleted(false);
        return v;
    }
}

