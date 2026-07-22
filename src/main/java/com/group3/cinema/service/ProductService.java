/**
 * Service quản lý các sản phẩm bán lẻ bắp nước (`ProductService`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `ProductController` phục vụ quản lý danh mục sản phẩm snack/đồ uống của rạp.
 * - Tương tác với `ProductRepository` để tìm kiếm (`searchProducts`) và lưu bản ghi sản phẩm.
 */
package com.group3.cinema.service;

import com.group3.cinema.entity.Product;
import com.group3.cinema.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Lấy danh sách tất cả sản phẩm. */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /** Tìm kiếm sản phẩm theo từ khóa và trạng thái. */
    public List<Product> searchProducts(String keyword, String status) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : null;

        return productRepository.searchProducts(cleanKeyword, cleanStatus);
    }

    /** Tìm sản phẩm theo ID. */
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElse(null);
    }

    /** Lưu tạo mới/cập nhật thông tin sản phẩm. */
    @Transactional
    public Product save(Product product) {
        return productRepository.save(product);
    }

    /** Xóa sản phẩm theo ID. */
    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}