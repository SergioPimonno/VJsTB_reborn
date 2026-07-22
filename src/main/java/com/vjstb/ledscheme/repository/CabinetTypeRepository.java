package com.vjstb.ledscheme.repository;

import com.vjstb.ledscheme.domain.CabinetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CabinetTypeRepository extends JpaRepository<CabinetType, Long> {

    boolean existsByNameIgnoreCase(String name);
}
