package com.group3.cinema.repository;

/*
 * Created on 2026-06-25: Repository for customer contact requests.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerContactRepository extends JpaRepository<CustomerContact, Long> {

    List<CustomerContact> findAllByOrderByCreatedAtDesc();

    List<CustomerContact> findByStatusOrderByCreatedAtDesc(CustomerContact.ContactStatus status);

    long countByStatus(CustomerContact.ContactStatus status);
}
