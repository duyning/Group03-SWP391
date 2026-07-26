/**
 * Entity đại diện cho bảng tài khoản người dùng (`account`) trong CSDL.
 * 
 * Quản lý thông tin đăng nhập, hồ sơ cá nhân, cấp độ thành viên (MembershipLevel),
 * điểm tích lũy (LoyaltyPoint), vai trò (Role: ADMIN, MANAGER, CUSTOMER) và ví voucher cá nhân (`savedVouchers`).
 * 
 * Khởi tạo bởi: DuongND_HE186619
 * Cập nhật bởi: NinhDD - HE186113, TuanPM
 */
package com.group3.cinema.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
// Map class Account vao bang account trong database.
@Table(name = "account")
public class Account {

    // Primary key tu tang, dung trong session va cac truy van quyen so huu.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int accountID;

    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    // Ten hien tren profile va danh sach account.
    private String name;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    @Column(nullable = false, unique = true)
    // Email dung de dang nhap, tim kiem va phai duy nhat.
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 8, max = 20, message = "Mật khẩu phải từ 8 đến 20 ký tự")
    @Column(nullable = false)
    // Mat khau duoc dung cho Login va Secure Settings.
    private String password;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "\\d{10}", message = "Số điện thoại phải gồm đúng 10 chữ số")
    // So dien thoai bat buoc gom 10 chu so.
    private String phoneNum;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    // Dia chi ca nhan co the duoc sua tren Edit Profile.
    private String address;

    @NotNull(message = "Vui lòng nhập ngày sinh")
    @Past(message = "Ngày sinh không thể ở tương lai")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(nullable = true)
    // Ngay sinh dung de validate tuoi customer/manager.
    private LocalDate dob;

    @Column(columnDefinition = "NVARCHAR(20)")
    // Gioi tinh do form profile/manager gui len.
    private String gender;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String avatar;

    // Tong diem tich luy dung de tinh hang tren profile.
    private int loyaltyPoint;

    @Enumerated(EnumType.STRING)
    // Hang thanh vien cua customer.
    private MembershipLevel membershipLevel;

    // true cho phep dang nhap; false la account bi vo hieu hoa.
    private boolean status;

    @Enumerated(EnumType.STRING)
    // Role quyet dinh quyen CUSTOMER, MANAGER hoac ADMIN.
    private Role role;

    @ManyToMany
    @JoinTable(
            name = "account_vouchers",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "voucher_id")
    )
    // Tap voucher da luu trong vi cua account.
    private Set<Voucher> savedVouchers = new HashSet<>();

    public Account() {
    }

    public int getAccountID() {
        return accountID;
    }

    public void setAccountID(int accountID) {
        this.accountID = accountID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Phương thức kiểm tra độ tuổi hợp lệ của người dùng (từ 13 đến 100 tuổi).
     * 
     * Gọi `Period.between(dob, LocalDate.now()).getYears()` để tính số tuổi từ ngày sinh.
     * Được gọi bởi `AccountService` hoặc `RegisterController` khi validate form đăng ký.
     * 
     * @return true nếu tuổi nằm trong khoảng 13 - 100, ngược lại trả về false.
     */
    @JsonIgnore
    @Transient
    public boolean isValidAge() {
        if (dob == null) {
            return false;
        }
        int calculatedAge = java.time.Period.between(dob, LocalDate.now()).getYears();
        return calculatedAge >= 13 && calculatedAge <= 100;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public int getLoyaltyPoint() {
        return loyaltyPoint;
    }

    public void setLoyaltyPoint(int loyaltyPoint) {
        this.loyaltyPoint = loyaltyPoint;
    }

    public MembershipLevel getMembershipLevel() {
        return membershipLevel;
    }

    public void setMembershipLevel(MembershipLevel membershipLevel) {
        this.membershipLevel = membershipLevel;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Set<Voucher> getSavedVouchers() {
        return savedVouchers;
    }

    public void setSavedVouchers(Set<Voucher> savedVouchers) {
        this.savedVouchers = savedVouchers;
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountID=" + accountID +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phoneNum='" + phoneNum + '\'' +
                ", address='" + address + '\'' +
                ", dob=" + dob +
                ", gender='" + gender + '\'' +
                ", avatar=" + avatar +
                ", loyaltyPoint=" + loyaltyPoint +
                ", membershipLevel=" + membershipLevel +
                ", status=" + status +
                ", role=" + role +
                '}';
    }
}

