package com.group3.cinema.repository;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cung cáº¥p cÃ¡c phÆ°Æ¡ng thá»©c thao tÃ¡c dá»¯ liá»‡u vá»›i báº£ng account trong cÆ¡ sá»Ÿ dá»¯ liá»‡u.
 * Káº¿ thá»«a JpaRepository Ä‘á»ƒ cung cáº¥p cÃ¡c hÃ m CRUD cÆ¡ báº£n vÃ  cÃ¡c truy váº¥n tÃ¬m kiáº¿m/kiá»ƒm tra email, sá»‘ Ä‘iá»‡n thoáº¡i.
 * 
 * NgÃ y thá»±c hiá»‡n: 04/06/2026
 * Táº¡o bá»Ÿi: DuongND_HE186619
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {

    Account findByEmail(String email);

    Optional<Account> findFirstByPhoneNum(String phoneNum);

    boolean existsByEmail(String email);

    boolean existsByPhoneNum(String phoneNum);

    @Query("""
            SELECT a
            FROM Account a
            WHERE a.role = :role
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR a.phoneNum LIKE CONCAT('%', :keyword, '%'))
            ORDER BY a.name ASC
            """)
    List<Account> searchCustomers(@Param("role") Role role, @Param("keyword") String keyword);
}
