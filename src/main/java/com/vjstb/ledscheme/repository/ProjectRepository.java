package com.vjstb.ledscheme.repository;

import com.vjstb.ledscheme.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
