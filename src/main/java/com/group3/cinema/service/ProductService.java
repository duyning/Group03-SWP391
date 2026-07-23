package com.group3.cinema.service;

import com.group3.cinema.entity.Product; // Đã sửa: dùng đúng Entity Product
import com.group3.cinema.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service quản lý danh mục Sản phẩm / Món ăn & Đồ uống lẻ (Product Management).
 * Cung cấp các thao tác CRUD cơ bản và tìm kiếm, lọc sản phẩm phục vụ cho trang quản trị Admin và Menu bán hàng.
 *
 * @author Group 3 - Cinema Management System
 */
@Service
public class ProductService {

    /** Repository thao tác dữ liệu với bảng Product trong CSDL */
    private final ProductRepository productRepository;

    /**
     * Constructor Injection để tiêm phụ thuộc ProductRepository.
     *
     * @param productRepository Repository quản lý sản phẩm
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Đã sửa kiểu trả về: List<Product>
    /**
     * Lấy toàn bộ danh sách các sản phẩm/món lẻ hiện có trong hệ thống CSDL.
     *
     * @return Danh sách tất cả đối tượng Product
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Đã sửa kiểu trả về: List<Product>
    /**
     * Tìm kiếm và lọc sản phẩm theo từ khóa tên và trạng thái kinh doanh.
     * Tự động làm sạch chuỗi (xóa khoảng trắng thừa) trước khi thực thi truy vấn.
     *
     * @param keyword Từ khóa tìm kiếm theo tên sản phẩm (cho phép null/rỗng)
     * @param status Trạng thái kinh doanh của sản phẩm (cho phép null/rỗng)
     * @return Danh sách sản phẩm thỏa mãn điều kiện lọc
     */
    public List<Product> searchProducts(String keyword, String status) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : null;

        return productRepository.searchProducts(cleanKeyword, cleanStatus);
    }

    // Đã sửa kiểu trả về: Product
    /**
     * Tìm kiếm thông tin chi tiết một sản phẩm theo ID.
     *
     * @param id ID của sản phẩm cần tìm
     * @return Đối tượng Product nếu tìm thấy, hoặc null nếu không tồn tại
     */
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElse(null);
    }

    /**
     * Lưu thông tin sản phẩm (Thêm mới hoặc Cập nhật) vào CSDL.
     * Thao tác được thực thi trong một Transaction.
     *
     * @param product Đối tượng sản phẩm cần lưu
     * @return Đối tượng Product đã lưu thành công kèm ID
     */
    @Transactional
    public Product save(Product product) { // Đã sửa tham số: Product
        return productRepository.save(product);
    }

    /**
     * Xóa một sản phẩm khỏi CSDL theo ID chỉ định.
     *
     * @param id ID của sản phẩm cần xóa
     */
    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}