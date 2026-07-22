/**
 * Service quản lý Mã giảm giá (Voucher), Thêm mã vào ví cá nhân và Kiểm duyệt luật áp dụng (`VoucherService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `VoucherController` (Admin quản lý voucher), `CustomerBookingService`, `PublicController` (Khách lưu mã vào ví).
 * - Tương tác với:
 *   + `VoucherRepository`: Tra cứu danh sách voucher khả dụng (`findByIsDeletedFalseAndEndDateAfterOrderByIdDesc`), tìm kiếm lọc (`searchVouchersForAdmin`), lưu/cập nhật voucher (`save`).
 *   + `AccountRepository`: Thêm mã giảm giá vào danh sách `savedVouchers` của tài khoản khách hàng.
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public VoucherService(VoucherRepository voucherRepository, AccountRepository accountRepository) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
    }

    /** Lấy danh sách các voucher active còn hiệu lực thời gian. */
    @Transactional(readOnly = true)
    public List<Voucher> getAllVouchers() {
        return voucherRepository.findByIsDeletedFalseAndEndDateAfterOrderByIdDesc(java.time.LocalDateTime.now());
    }

    /** Lấy chi tiết thông tin voucher theo ID. */
    @Transactional(readOnly = true)
    public Voucher getVoucherById(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã Voucher có ID: " + id));
    }

    /** Tạo mới một mã Voucher và kiểm tra mã code không bị trùng lặp. */
    @Transactional
    public void saveVoucher(Voucher voucher) {
        if (voucher.getCode() != null)
            voucher.setCode(voucher.getCode().trim().toUpperCase());
        if (voucherRepository.existsByCode(voucher.getCode())) {
            throw new IllegalArgumentException("Mã Voucher '" + voucher.getCode() + "' đã tồn tại!");
        }
        validateVoucherLogic(voucher);
        voucherRepository.save(voucher);
    }

    /** Cập nhật nội dung mã Voucher hiện tại. */
    @Transactional
    public void updateVoucher(Long id, Voucher updatedVoucher) {
        Voucher existingVoucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Voucher cần cập nhật!"));

        if (updatedVoucher.getCode() != null)
            updatedVoucher.setCode(updatedVoucher.getCode().trim().toUpperCase());

        if (voucherRepository.existsByCodeAndIdNot(updatedVoucher.getCode(), id)) {
            throw new IllegalArgumentException("Mã Voucher đã được sử dụng cho chương trình khác!");
        }

        existingVoucher.setCode(updatedVoucher.getCode());
        existingVoucher.setTitle(updatedVoucher.getTitle());
        existingVoucher.setDiscountType(updatedVoucher.getDiscountType());
        existingVoucher.setDiscountValue(updatedVoucher.getDiscountValue());
        existingVoucher.setMaxDiscountAmount(updatedVoucher.getMaxDiscountAmount());
        existingVoucher.setStartDate(updatedVoucher.getStartDate());
        existingVoucher.setEndDate(updatedVoucher.getEndDate());
        existingVoucher.setMinOrderValue(updatedVoucher.getMinOrderValue());
        existingVoucher.setTotalQuantity(updatedVoucher.getTotalQuantity());
        existingVoucher.setServiceScope(updatedVoucher.getServiceScope());
        existingVoucher.setApplicableSeats(updatedVoucher.getApplicableSeats());
        existingVoucher.setApplicableDays(updatedVoucher.getApplicableDays());
        existingVoucher.setIsHolidayApplicable(updatedVoucher.getIsHolidayApplicable());
        existingVoucher.setLimitPerUser(updatedVoucher.getLimitPerUser());

        validateVoucherLogic(existingVoucher);
        voucherRepository.save(existingVoucher);
    }

    /** Tìm kiếm và lọc danh sách voucher theo từ khóa, loại chiết khấu, phạm vi dịch vụ áp dụng. */
    @Transactional(readOnly = true)
    public List<Voucher> searchVouchers(String keyword, String discountTypeStr, String serviceScopeStr) {

        Voucher.DiscountType discountType = null;
        if (discountTypeStr != null && !discountTypeStr.trim().isEmpty()) {
            discountType = Voucher.DiscountType.valueOf(discountTypeStr.trim());
        }

        Voucher.ServiceScope serviceScope = null;
        if (serviceScopeStr != null && !serviceScopeStr.trim().isEmpty()) {
            serviceScope = Voucher.ServiceScope.valueOf(serviceScopeStr.trim());
        }

        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return voucherRepository.searchVouchersForAdmin(cleanKeyword, discountType, serviceScope);
    }

    /** Khách hàng lưu (Lưu về ví) một mã Voucher vào bộ sưu tập cá nhân. */
    @Transactional
    public void collectVoucher(int accountId, Long voucherId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));

        if (voucher.getEndDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Voucher này đã hết hạn!");
        }

        if (account.getSavedVouchers().add(voucher)) {
            accountRepository.save(account);
        } else {
            throw new IllegalArgumentException("Bạn đã lưu voucher này rồi!");
        }
    }

    /** Kiểm tra tính hợp lệ quy tắc logic cấu hình của voucher. */
    private void validateVoucherLogic(Voucher voucher) {
        if (voucher.getStartDate().isAfter(voucher.getEndDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc!");
        }

        if (voucher.getEndDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Ngày đóng chương trình Voucher không được là một ngày trong quá khứ!");
        }

        if ("PERCENTAGE".equalsIgnoreCase(String.valueOf(voucher.getDiscountType()))) {
            if (voucher.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Giảm giá phần trăm không được quá 100%!");
            }
        }

        if ("WATER".equalsIgnoreCase(String.valueOf(voucher.getServiceScope()))) {
            voucher.setApplicableSeats("");
        }
    }

    /** Thực hiện xóa mềm một mã Voucher (`isDeleted = true`). */
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã voucher này!"));

        voucher.setIsDeleted(true);
        voucherRepository.save(voucher);
    }
}