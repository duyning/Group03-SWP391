package com.group3.cinema.repository;

import com.group3.cinema.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Load account kÃ¨m theo savedVouchers (JOIN FETCH) Ä‘á»ƒ trÃ¡nh LazyInitializationException
     * khi Account Ä‘Æ°á»£c lÆ°u vÃ o HTTP session sau khi Hibernate session Ä‘Ã£ Ä‘Ã³ng.
     */
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.savedVouchers WHERE a.email = :email")
    Account findByEmailWithVouchers(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNum(String phoneNum);
}
