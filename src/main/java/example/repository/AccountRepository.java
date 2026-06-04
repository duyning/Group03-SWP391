package example.repository;

import example.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {

    Account findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNum(String phoneNum);
}
