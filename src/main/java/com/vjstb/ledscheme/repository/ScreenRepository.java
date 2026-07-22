package com.vjstb.ledscheme.repository;

import com.vjstb.ledscheme.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    boolean existsByCabinetType_Id(Long cabinetTypeId);
}
