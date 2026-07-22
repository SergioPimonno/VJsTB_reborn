package com.vjstb.ledscheme.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class SceneDtos {

    private SceneDtos() {
    }

    public record SceneSummaryDto(
            Long id,
            String name,
            int orderIndex,
            List<ScreenSummaryDto> screens
    ) {
    }

    public record ScreenSummaryDto(
            Long id,
            String name,
            Long cabinetTypeId,
            String cabinetTypeName,
            int rows,
            int cols,
            double posXMm,
            double posYMm
    ) {
    }

    public record CreateSceneRequest(
            @NotBlank String name
    ) {
    }

    public record UpdateSceneRequest(
            @NotBlank String name,
            int orderIndex
    ) {
    }
}
