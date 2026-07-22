/**
 * Lớp khởi tạo dữ liệu danh mục phòng chiếu và định dạng rạp (CatalogInitializer).
 * 
 * Tự động kích hoạt khi ứng dụng khởi chạy (`@PostConstruct`), gọi đến CatalogService
 * để đồng bộ dữ liệu danh mục từ danh sách phòng chiếu hiện có trong CSDL.
 * 
 * Ngày cập nhật: 04/06/2026
 * Khởi tạo bởi: NinhDD - HE186113
 */
package com.group3.cinema.config;

import com.group3.cinema.service.CatalogService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class CatalogInitializer {

    private final CatalogService catalogService;

    /**
     * Constructor tiêm phụ thuộc CatalogService.
     * 
     * @param catalogService Dịch vụ quản lý danh mục phòng chiếu và định dạng rạp.
     */
    public CatalogInitializer(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Phương thức thực thi sau khi khởi tạo Bean (`@PostConstruct`).
     * 
     * Gọi đến phương thức `catalogService.seedFromExistingRooms()` để quét các phòng chiếu
     * hiện có và tự động sinh danh mục tương ứng.
     */
    @PostConstruct
    public void initializeCatalogs() {
        catalogService.seedFromExistingRooms();
    }
}

