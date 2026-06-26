package com.group3.cinema.entity;

import jakarta.validation.constraints.*;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity đại diện cho bảng tài khoản (Account) trong hệ thống.
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int accountID;

    @NotBlank(message = "Name is required")
    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 20, message = "Password must be 8-20 characters")
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\d{10}", message = "Phone number must be exactly 10 digits")
    private String phoneNum;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String address;

    @Min(value = 17, message = "Age must be greater than 16")
    private int age;

    @Column(columnDefinition = "NVARCHAR(20)")
    private String gender;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String avatar;

    private int loyaltyPoint;

    @Enumerated(EnumType.STRING)
    private MembershipLevel membershipLevel;

    private boolean status;

    @Enumerated(EnumType.STRING)
    private Role role;

    // Quan hệ Many-to-Many: Mỗi tài khoản lưu được nhiều Voucher
    @ManyToMany
    @JoinTable(
            name = "account_vouchers",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "voucher_id")
    )
    private Set<Voucher> savedVouchers = new HashSet<>();

    public Account() {
    }

    // Getters and Setters
    public int getAccountID() { return accountID; }
    public void setAccountID(int accountID) { this.accountID = accountID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhoneNum() { return phoneNum; }
    public void setPhoneNum(String phoneNum) { this.phoneNum = phoneNum; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public int getLoyaltyPoint() { return loyaltyPoint; }
    public void setLoyaltyPoint(int loyaltyPoint) { this.loyaltyPoint = loyaltyPoint; }

    public MembershipLevel getMembershipLevel() { return membershipLevel; }
    public void setMembershipLevel(MembershipLevel membershipLevel) { this.membershipLevel = membershipLevel; }

    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    // Phương thức Getter/Setter cho savedVouchers (Fix lỗi Cannot resolve method)
    public Set<Voucher> getSavedVouchers() { return savedVouchers; }
    public void setSavedVouchers(Set<Voucher> savedVouchers) { this.savedVouchers = savedVouchers; }

    @Override
    public String toString() {
        return "Account{" +
                "accountID=" + accountID +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                '}';
    }
}