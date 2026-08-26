package com.prospr.app.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MerchantCategory;

@Repository
public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, UUID> {

    /** Small, rarely-changing table; the categorizer loads it once per run and matches in memory. */
    List<MerchantCategory> findAll();
}
