package com.group3.cinema.controller;

import com.group3.cinema.entity.Product;
import com.group3.cinema.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/admin/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 1. Hiển thị danh sách món lẻ (Có tích hợp bộ lọc theo tên & trạng thái)
     */
    @GetMapping
    public String listProducts(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "status", required = false) String status,
                               Model model) {

        List<Product> products = productService.searchProducts(keyword, status);
        model.addAttribute("products", products);
        return "product-list"; // Thêm lại tiền tố admin/ cho đồng bộ với return form dưới của cậu
    }

    /**
     * 2. Mở form thêm món lẻ mới
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("product", new Product());
        return "product-create";
    }

    /**
     * 3. Xử lý lưu món lẻ mới khi submit form (Không chứa file)
     */
    @PostMapping("/save")
    public String saveProduct(@Valid @ModelAttribute("product") Product product,
                              BindingResult result) {

        // Nếu có lỗi validation từ các annotation (@NotBlank, @NotNull...) thì trả về form tạo
        if (result.hasErrors()) {
            return "product-create";
        }

        productService.save(product);
        return "redirect:/admin/products";
    }

    /**
     * 4. Mở form chỉnh sửa món lẻ đã có
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        Product product = productService.findById(id);
        if (product == null) {
            return "redirect:/admin/products";
        }
        model.addAttribute("product", product);
        return "product-edit";
    }

    /**
     * 5. Xử lý cập nhật thông tin món lẻ (Không chứa file)
     */
    @PostMapping("/update")
    public String updateProduct(@Valid @ModelAttribute("product") Product product,
                                BindingResult result) {

        if (result.hasErrors()) {
            return "product-edit";
        }

        Product existingProduct = productService.findById(product.getId());
        if (existingProduct == null) {
            return "redirect:/admin/products";
        }

        // JPA tự hiểu hành động update nhờ ID có sẵn trong đối tượng product
        productService.save(product);
        return "redirect:/admin/products";
    }

    /**
     * 6. Xử lý xóa món lẻ khỏi cơ sở dữ liệu
     */
    /**
     * 6. Thay đổi trạng thái sang INACTIVE (Xóa mềm) thay vì xóa hoàn toàn khỏi DB
     */
    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id) {
        Product product = productService.findById(id);

        if (product != null) {
            // Chuyển trạng thái sang ngừng kinh doanh
            product.setStatus("INACTIVE");
            productService.save(product);
        }

        return "redirect:/admin/products";
    }
}