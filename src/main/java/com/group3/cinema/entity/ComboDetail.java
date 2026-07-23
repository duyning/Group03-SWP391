/**
 * Entity bảng trung gian định nghĩa số lượng sản phẩm lẻ (`Product`) cấu thành nên một `Combo` (`combo_details`).
 * 
 * Ví dụ: Combo 1 bao gồm 1 Bỏng ngô phô mai (Product ID 1) + 2 Nước ngọt Coca (Product ID 2).
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "combo_details")
public class ComboDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Combo getCombo() { return combo; }
    public void setCombo(Combo combo) { this.combo = combo; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}