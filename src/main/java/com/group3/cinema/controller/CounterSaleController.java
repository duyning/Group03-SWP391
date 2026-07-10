package com.group3.cinema.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CounterSaleController {

    @GetMapping("/admin/counter-sales")
    public String counterSalesPage() {
        return "counter-sales";
    }

    @GetMapping("/admin/counter-sales/checkout")
    public String counterSalesCheckoutPage() {
        return "counter-sales-checkout";
    }
}
