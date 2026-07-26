package com.group3.cinema.entity;

/**
 * Enum Ä‘áº¡i diá»‡n cho cÃ¡c cáº¥p Ä‘á»™ thÃ nh viÃªn (Membership Level) cá»§a tÃ i khoáº£n.
 * Bao gá»“m cÃ¡c háº¡ng: SILVER (Báº¡c), GOLD (VÃ ng), PLAT (Báº¡ch kim).
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
public enum MembershipLevel {
    // Hang Dong: duoi 1.000 diem.
    BRONZE,
    // Hang Bac: tu 1.000 den duoi 5.000 diem.
    SILVER,
    // Hang Vang: tu 5.000 diem tro len.
    GOLD,
    // Gia tri du lieu cu; ProfileController hien thi tuong duong hang Vang.
    PLAT
}
