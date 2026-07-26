package com.group3.cinema.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller trả về 2 màn hình HTML của chức năng bán vé tại quầy.
 *
 * Luồng giao diện tổng quan:
 * 1. Nhân viên/admin mở `/admin/counter-sales`.
 *    - View `counter-sales.html` hiển thị bước 1: chọn ngày bán, tìm phim, chọn suất chiếu và chọn ghế.
 *    - JavaScript trong view gọi các API `/api/counter-sales/showtimes`, `/seats`, `/hold`, `/release`.
 * 2. Sau khi giữ ghế thành công, view chuyển sang `/admin/counter-sales/checkout`.
 *    - View `counter-sales-checkout.html` hiển thị bước 2: thông tin khách, combo, voucher, tổng tiền,
 *      phương thức thanh toán tiền mặt hoặc payOS.
 *    - JavaScript trong view gọi `/api/counter-sales/preview`, `/payment-link`, `/complete`.
 *
 * Controller này không xử lý nghiệp vụ bán vé trực tiếp. Toàn bộ validate, giữ ghế,
 * tính tiền, áp voucher, tạo booking/payment được thực hiện trong `CounterSaleService`
 * thông qua `CounterSaleApiController`.
 */
@Controller
public class CounterSaleController {

    /**
     * GET /admin/counter-sales
     *
     * Trả về màn bước 1:
     * - Lọc suất chiếu theo ngày/phim.
     * - Tải sơ đồ ghế full theo phòng của suất chiếu.
     * - Giữ ghế bằng token 5 phút trước khi sang bước thanh toán.
     */
    @GetMapping("/admin/counter-sales")
    public String counterSalesPage() {
        return "counter-sales";
    }

    /**
     * GET /admin/counter-sales/checkout
     *
     * Trả về màn bước 2:
     * - Đọc dữ liệu phiên bán vé đang giữ ở trình duyệt.
     * - Cho nhân viên nhập/chọn khách hàng, chọn combo, nhập voucher.
     * - Preview hóa đơn và hoàn tất bằng CASH hoặc tạo link payOS.
     */
    @GetMapping("/admin/counter-sales/checkout")
    public String counterSalesCheckoutPage() {
        return "counter-sales-checkout";
    }
}
