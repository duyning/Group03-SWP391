package com.group3.cinema.service;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.MembershipLevel;
import com.group3.cinema.entity.Role;
import com.group3.cinema.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Lá»›p dá»‹ch vá»¥ (Service) xá»­ lÃ½ cÃ¡c nghiá»‡p vá»¥ logic liÃªn quan Ä‘áº¿n tÃ i khoáº£n (Account).
 * Bao gá»“m Ä‘Äƒng kÃ½, Ä‘Äƒng nháº­p, tÃ¬m kiáº¿m tÃ i khoáº£n, Ä‘á»•i máº­t kháº©u vÃ  xá»­ lÃ½ gá»­i OTP xÃ¡c thá»±c.
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    /**
     * Register a new customer account.
     * Sets default values for role, status, membership level, and loyalty points.
     *
     * @param account the account to register
     * @return the saved account
     */
    public Account register(Account account) {
        account.setRole(Role.CUSTOMER);
        account.setStatus(true);
        account.setMembershipLevel(MembershipLevel.SILVER);
        account.setLoyaltyPoint(0);
        return accountRepository.save(account);
    }

    /**
     * Authenticate a user by email and password.
     * Returns the Account if credentials match, null otherwise.
     *
     * @param email    the email to look up
     * @param password the plain-text password to verify
     * @return the matching Account, or null if not found / wrong password
     */
    public Account login(String email, String password) {
        Account account = accountRepository.findByEmail(email);
        if (account != null && account.getPassword().equals(password)) {
            return account;
        }
        return null;
    }

    /**
     * Check if an email already exists in the database.
     */
    public boolean isEmailExist(String email) {
        return accountRepository.existsByEmail(email);
    }

    /**
     * Check if a phone number already exists in the database.
     */
    public boolean isPhoneNumExist(String phoneNum) {
        return accountRepository.existsByPhoneNum(phoneNum);
    }

    /**
     * Find an account by email.
     */
    public Account findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    /**
     * Find an account by ID.
     */
    public Account findById(int id) {
        return accountRepository.findById(id).orElse(null);
    }

    /**
     * Reset (change) the password for a given account.
     * Validates all 6 cases and throws IllegalArgumentException with message on failure.
     *
     * @param account         the account whose password will be changed
     * @param oldPassword     the current password entered by the user
     * @param newPassword     the new password entered by the user
     * @param confirmPassword the confirmation of the new password
     */
    public void resetPassword(Account account, String oldPassword, String newPassword, String confirmPassword) {

        // Case 5: Empty / null fields
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new IllegalArgumentException("Máº­t kháº©u cÅ© khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Máº­t kháº©u má»›i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("XÃ¡c nháº­n máº­t kháº©u khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }

        // Case 1: Old password incorrect
        if (!account.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        // Case 4: New password same as old
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        // Case 2: New password length invalid (must be 8-20 characters)
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("New password must be 8-20 characters");
        }

        // Case 3: Confirm password mismatch
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Confirm password does not match");
        }

        // Case 5: All validations passed â€” update password
        account.setPassword(newPassword);
        accountRepository.save(account);
    }

    /**
     * Update password for forgot password flow (no old password check).
     */
    public void updatePassword(Account account, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Máº­t kháº©u má»›i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("XÃ¡c nháº­n máº­t kháº©u khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("Máº­t kháº©u má»›i pháº£i tá»« 8 Ä‘áº¿n 20 kÃ½ tá»±");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Máº­t kháº©u xÃ¡c nháº­n khÃ´ng khá»›p");
        }

        account.setPassword(newPassword);
        accountRepository.save(account);
    }

    /**
     * Generate a 6-digit OTP and send it via email.
     */
    public String generateAndSendOTP(String email) {
        // Generate 6-digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setTo(email);
            message.setSubject("MÃ£ xÃ¡c nháº­n khÃ´i phá»¥c máº­t kháº©u - CinemaBook");
            message.setText("ChÃ o báº¡n,\n\nMÃ£ OTP Ä‘á»ƒ khÃ´i phá»¥c máº­t kháº©u cá»§a báº¡n lÃ : " + otp + "\n\nMÃ£ nÃ y sáº½ háº¿t háº¡n trong 5 phÃºt. Vui lÃ²ng khÃ´ng chia sáº» mÃ£ nÃ y cho báº¥t ká»³ ai.\n\nTrÃ¢n trá»ng,\nÄá»™i ngÅ© CinemaBook.");
            
            mailSender.send(message);
            System.out.println("ÄÃ£ gá»­i OTP " + otp + " tá»›i email " + email);
        } catch (Exception e) {
            System.err.println("Lá»—i khi gá»­i email: " + e.getMessage());
            // Fallback for testing when email fails
            System.out.println("FALLBACK OTP (do lá»—i gá»­i email): " + otp);
        }

        return otp;
    }
}


