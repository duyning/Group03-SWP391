package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "combo_details")
public class ComboDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết ngược về Combo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    // Liên kết tới Menu món lẻ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // ===== GETTERS AND SETTERS =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Combo getCombo() { return combo; }
    public void setCombo(Combo combo) { this.combo = combo; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}