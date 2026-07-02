package com.group3.cinema.service;

import com.group3.cinema.entity.Product; // Đã sửa: dùng đúng Entity Product
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

    // Đã sửa kiểu trả về: List<Product>
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Đã sửa kiểu trả về: List<Product>
    public List<Product> searchProducts(String keyword, String status) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : null;

        return productRepository.searchProducts(cleanKeyword, cleanStatus);
    }

    // Đã sửa kiểu trả về: Product
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElse(null);
    }

    @Transactional
    public Product save(Product product) { // Đã sửa tham số: Product
        return productRepository.save(product);
    }

    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}