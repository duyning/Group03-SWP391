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

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service quản lý toàn bộ hệ thống Mã giảm giá / Voucher (Voucher Management).
 * Đảm nhận các chức năng: Quản lý chương trình khuyến mãi (Admin), tìm kiếm/lọc voucher,
 * thu thập mã giảm giá (Khách hàng), kiểm tra quy tắc nghiệp vụ thời gian & mức giảm,
 * và thực hiện xóa mềm mã giảm giá.
 *
 * @author Group 3 - Cinema Management System
 */
@Service
public class VoucherService {

    /** Repository thao tác dữ liệu với bảng Voucher trong CSDL */
    private final VoucherRepository voucherRepository;

    /** Repository thao tác dữ liệu với bảng Account trong CSDL */
    private final AccountRepository accountRepository;

    /**
     * Constructor Injection tiêm các phụ thuộc Repository cần thiết.
     *
     * @param voucherRepository Repository quản lý voucher
     * @param accountRepository Repository quản lý tài khoản
     */
    @Autowired
    public VoucherService(VoucherRepository voucherRepository, AccountRepository accountRepository) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
    }

    // ==========================================
    // 1. DÀNH CHO ADMIN (Quản lý)
    // ==========================================

    /**
     * Lấy danh sách tất cả mã giảm giá khả dụng dành cho trang quản trị Admin.
     * Tự động loại bỏ các voucher đã bị xóa mềm (isDeleted = true) và các voucher đã hết hạn,
     * đồng thời sắp xếp các voucher mới tạo lên đầu.
     *
     * @return Danh sách các Voucher hợp lệ
     */
    @Transactional(readOnly = true)
    public List<Voucher> getAllVouchers() {
        // Tự động ẩn voucher xóa mềm, tự động ẩn voucher quá hạn và xếp mới nhất lên
        // đầu
        return voucherRepository.findByIsDeletedFalseAndEndDateAfterOrderByIdDesc(java.time.LocalDateTime.now());
    }

    /**
     * Lấy thông tin chi tiết một Voucher theo ID.
     *
     * @param id ID của Voucher cần tìm
     * @return Đối tượng Voucher
     * @throws IllegalArgumentException nếu không tìm thấy Voucher với ID tương ứng
     */
    @Transactional(readOnly = true)
    public Voucher getVoucherById(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã Voucher có ID: " + id));
    }

    /**
     * Tạo mới chương trình Voucher.
     * Tự động chuẩn hóa mã Voucher (viết hoa, xóa khoảng trắng thừa),
     * kiểm tra trùng lặp mã và xác thực tính hợp lệ của logic khuyến mãi trước khi lưu.
     *
     * @param voucher Đối tượng Voucher mới
     * @throws IllegalArgumentException nếu mã bị trùng hoặc dữ liệu không hợp lệ
     */
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

    /**
     * Cập nhật thông tin chương trình Voucher đã tồn tại.
     * Kiểm tra mã không trùng với các Voucher khác, cập nhật các thuộc tính cấu hình,
     * xác thực lại logic nghiệp vụ và lưu xuống CSDL.
     *
     * @param id ID của Voucher cần cập nhật
     * @param updatedVoucher Đối tượng chứa thông tin cập nhật mới
     * @throws IllegalArgumentException nếu mã bị trùng với chương trình khác hoặc dữ liệu sai quy tắc
     */
    @Transactional
    public void updateVoucher(Long id, Voucher updatedVoucher) {
        Voucher existingVoucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Voucher cần cập nhật!"));

        if (updatedVoucher.getCode() != null)
            updatedVoucher.setCode(updatedVoucher.getCode().trim().toUpperCase());

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

    /**
     * Tìm kiếm và lọc danh sách Voucher trong trang quản trị.
     * Tự động chuyển đổi chuỗi truyền vào từ giao diện sang Enum tương ứng (DiscountType, ServiceScope).
     *
     * @param keyword Từ khóa tìm kiếm theo mã hoặc tiêu đề
     * @param discountTypeStr Chuỗi loại giảm giá (FIXED_AMOUNT, PERCENTAGE...)
     * @param serviceScopeStr Chuỗi phạm vi áp dụng (TICKET, WATER, ALL...)
     * @return Danh sách các Voucher phù hợp điều kiện tìm kiếm
     */
    @Transactional(readOnly = true)
    public List<Voucher> searchVouchers(String keyword, String discountTypeStr, String serviceScopeStr) {

        // 1. Chuyển đổi kiểu dữ liệu String từ ô Select sang đúng Enum Object, nếu để
        // trống thì gán null
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
     * Xử lý cho Khách hàng lưu/thu thập Voucher vào ví voucher cá nhân (Collect Voucher).
     * Kiểm tra voucher còn hạn hay không và chặn trường hợp người dùng lưu trùng lặp.
     *
     * @param accountId ID tài khoản người dùng
     * @param voucherId ID của Voucher cần lưu
     * @throws IllegalArgumentException nếu không tìm thấy tài khoản/voucher, hết hạn, hoặc đã lưu từ trước
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

    /**
     * Helper Method: Kiểm tra các quy tắc logic nghiệp vụ đối với dữ liệu Voucher.
     * Bao gồm: Thời gian bắt đầu/kết thúc hợp lệ, thời hạn không nằm trong quá khứ,
     * chiết khấu phần trăm không vượt quá 100%, và tự động reset loại ghế nếu áp dụng cho dịch vụ Bắp nước.
     *
     * @param voucher Đối tượng Voucher cần kiểm tra
     * @throws IllegalArgumentException nếu vi phạm bất kỳ quy tắc nghiệp vụ nào
     */
    private void validateVoucherLogic(Voucher voucher) {
        // 1. Kiểm tra ngày bắt đầu và ngày kết thúc
        if (voucher.getStartDate().isAfter(voucher.getEndDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc!");
        }

        // 2. THÊM LẠI LOGIC CŨ: Ngày đóng (endDate) không được bé hơn thời gian hiện
        // tại (ngày hôm nay)
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

    // Trong file com.group3.cinema.service.VoucherService.java (hoặc
    // VoucherServiceImpl.java)
    /**
     * Thực hiện Xóa mềm (Soft Delete) chương trình Voucher bằng cách chuyển cờ trạng thái `isDeleted = true`.
     * Giúp bảo toàn lịch sử các đơn hàng / hóa đơn cũ đã từng áp dụng mã voucher này.
     *
     * @param id ID của Voucher cần xóa
     * @throws IllegalArgumentException nếu không tìm thấy mã voucher
     */
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