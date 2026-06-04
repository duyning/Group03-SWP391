package com.group3.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "combos")
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    // DÃ¹ng BigDecimal hoáº·c Double Ä‘á»ƒ lÆ°u giÃ¡ tiá»n cho chuáº©n cáº¥u trÃºc sá»‘
    @Column(nullable = false)
    private BigDecimal price;

    @Column(length = 255)
    private String image;

    // ACTIVE (Äang bÃ¡n) | INACTIVE (Ngá»«ng bÃ¡n)
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    // ===== Getters and Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}