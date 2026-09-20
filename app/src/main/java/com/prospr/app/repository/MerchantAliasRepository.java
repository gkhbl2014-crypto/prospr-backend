package com.prospr.app.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MerchantAlias;

@Repository
public interface MerchantAliasRepository extends JpaRepository<MerchantAlias, UUID> {

    /** Small, rarely-changing table; the normalizer loads it once per run and matches in memory. */
    List<MerchantAlias> findAll();
}
