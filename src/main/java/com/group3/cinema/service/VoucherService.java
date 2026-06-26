package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Voucher;
import com.group3.cinema.repository.AccountRepository;
import com.group3.cinema.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final AccountRepository accountRepository;

    // ==========================================
    // 1. DÀNH CHO ADMIN (Quản lý)
    // ==========================================

    @Transactional(readOnly = true)
    public List<Voucher> getAllVouchers() {
        // Tự động ẩn voucher xóa mềm, tự động ẩn voucher quá hạn và xếp mới nhất lên đầu
        return voucherRepository.findByIsDeletedFalseAndEndDateAfterOrderByIdDesc(java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Voucher getVoucherById(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã Voucher có ID: " + id));
    }

    @Transactional
    public void saveVoucher(Voucher voucher) {
        if (voucher.getCode() != null) voucher.setCode(voucher.getCode().trim().toUpperCase());
        if (voucherRepository.existsByCode(voucher.getCode())) {
            throw new IllegalArgumentException("Mã Voucher '" + voucher.getCode() + "' đã tồn tại!");
        }
        validateVoucherLogic(voucher);
        voucherRepository.save(voucher);
    }

    @Transactional
    public void updateVoucher(Long id, Voucher updatedVoucher) {
        Voucher existingVoucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Voucher cần cập nhật!"));

        if (updatedVoucher.getCode() != null) updatedVoucher.setCode(updatedVoucher.getCode().trim().toUpperCase());

        if (voucherRepository.existsByCodeAndIdNot(updatedVoucher.getCode(), id)) {
            throw new IllegalArgumentException("Mã Voucher đã được sử dụng cho chương trình khác!");
        }

        // 1. GÁN DỮ LIỆU MỚI VÀO EXISTING VOUCHER TRƯỚC
        existingVoucher.setCode(updatedVoucher.getCode());
        existingVoucher.setTitle(updatedVoucher.getTitle());
        existingVoucher.setDiscountType(updatedVoucher.getDiscountType());
        existingVoucher.setDiscountValue(updatedVoucher.getDiscountValue());
        existingVoucher.setMaxDiscountAmount(updatedVoucher.getMaxDiscountAmount());
        existingVoucher.setStartDate(updatedVoucher.getStartDate());
        existingVoucher.setEndDate(updatedVoucher.getEndDate()); // Gán ngày đóng mới
        existingVoucher.setMinOrderValue(updatedVoucher.getMinOrderValue());
        existingVoucher.setTotalQuantity(updatedVoucher.getTotalQuantity());
        existingVoucher.setServiceScope(updatedVoucher.getServiceScope());
        existingVoucher.setApplicableSeats(updatedVoucher.getApplicableSeats());
        existingVoucher.setApplicableDays(updatedVoucher.getApplicableDays());
        existingVoucher.setIsHolidayApplicable(updatedVoucher.getIsHolidayApplicable());
        existingVoucher.setLimitPerUser(updatedVoucher.getLimitPerUser());

        // 2. BÂY GIỜ MỚI CHẠY VALIDATE TRÊN ĐỐI TƯỢNG SẮP LƯU NÀY
        validateVoucherLogic(existingVoucher);

        // 3. LƯU XUỐNG DB
        voucherRepository.save(existingVoucher);
    }

    // ==========================================
    // 2. TÌM KIẾM & XỬ LÝ CHO KHÁCH HÀNG
    // ==========================================
    @Transactional(readOnly = true)
    public List<Voucher> searchVouchers(String keyword, String discountTypeStr, String serviceScopeStr) {

        // 1. Chuyển đổi kiểu dữ liệu String từ ô Select sang đúng Enum Object, nếu để trống thì gán null
        Voucher.DiscountType discountType = null;
        if (discountTypeStr != null && !discountTypeStr.trim().isEmpty()) {
            discountType = Voucher.DiscountType.valueOf(discountTypeStr.trim());
        }

        Voucher.ServiceScope serviceScope = null;
        if (serviceScopeStr != null && !serviceScopeStr.trim().isEmpty()) {
            serviceScope = Voucher.ServiceScope.valueOf(serviceScopeStr.trim());
        }

        // Làm sạch từ khóa tìm kiếm
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        // 2. Gọi hàm Repository đã được gắn cứng điều kiện loại bỏ IsDeleted = true
        return voucherRepository.searchVouchersForAdmin(cleanKeyword, discountType, serviceScope);
    }


    /**
     * Logic Lưu Voucher (Collect)
     */
    @Transactional
    public void collectVoucher(int accountId, Long voucherId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new IllegalArgumentException("Voucher không tồn tại"));

        if (voucher.getEndDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Voucher này đã hết hạn!");
        }

        // Thêm vào Set savedVouchers (Set tự động xử lý trùng lặp)
        if (account.getSavedVouchers().add(voucher)) {
            accountRepository.save(account);
        } else {
            throw new IllegalArgumentException("Bạn đã lưu voucher này rồi!");
        }
    }

    // ==========================================
    // 3. HÀM BỔ TRỢ (Validation)
    // ==========================================

    private void validateVoucherLogic(Voucher voucher) {
        // 1. Kiểm tra ngày bắt đầu và ngày kết thúc
        if (voucher.getStartDate().isAfter(voucher.getEndDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc!");
        }

        // 2. THÊM LẠI LOGIC CŨ: Ngày đóng (endDate) không được bé hơn thời gian hiện tại (ngày hôm nay)
        if (voucher.getEndDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Ngày đóng chương trình Voucher không được là một ngày trong quá khứ!");
        }

        // 3. Kiểm tra mức giảm giá phần trăm
        if ("PERCENTAGE".equalsIgnoreCase(String.valueOf(voucher.getDiscountType()))) {
            if (voucher.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Giảm giá phần trăm không được quá 100%!");
            }
        }

        if ("WATER".equalsIgnoreCase(String.valueOf(voucher.getServiceScope()))) {
            voucher.setApplicableSeats("");
        }
    }

    // Trong file com.group3.cinema.service.VoucherService.java (hoặc VoucherServiceImpl.java)
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã voucher này!"));

        // Thực hiện xóa mềm bằng cách đổi trạng thái
        // Đổi từ setDeleted sang setIsDeleted
        voucher.setIsDeleted(true);
        voucherRepository.save(voucher);
    }




}