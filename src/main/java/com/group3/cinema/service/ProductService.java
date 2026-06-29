package com.group3.cinema.service;

import com.group3.cinema.entity.Product;
import com.group3.cinema.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service // Đánh dấu Bean ở đây để Controller inject qua @Autowired mượt mà
public class ProductService {

    private final ProductRepository productRepository;

    // Sử dụng Constructor Injection giống hệt PostService mẫu của cậu
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> searchProducts(String keyword, String status) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : null;

        return productRepository.searchProducts(cleanKeyword, cleanStatus);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElse(null); // Hoặc .orElseThrow(() -> new RuntimeException("Khong tim thay mon le"));
    }

    @Transactional
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }
}