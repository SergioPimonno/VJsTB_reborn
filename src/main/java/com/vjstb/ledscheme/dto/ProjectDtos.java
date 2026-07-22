package com.vjstb.ledscheme.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectSummaryDto(
            Long id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            int sceneCount
    ) {
    }

    public record ProjectDetailDto(
            Long id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            List<SceneDtos.SceneSummaryDto> scenes
    ) {
    }

    public record CreateProjectRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank String name,
            String description
    ) {
    }
}
