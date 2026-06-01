package example.service;

import example.entity.Account;
import example.entity.MembershipLevel;
import example.entity.Role;
import example.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

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
}

