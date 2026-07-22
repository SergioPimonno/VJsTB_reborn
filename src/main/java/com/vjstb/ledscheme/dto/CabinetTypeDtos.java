package com.vjstb.ledscheme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public final class CabinetTypeDtos {

    private CabinetTypeDtos() {
    }

    public record CabinetTypeDto(
            Long id,
            String name,
            double widthMm,
            double heightMm,
            Double depthMm,
            int resolutionWidth,
            int resolutionHeight,
            double powerConsumptionW,
            double weightKg
    ) {
    }

    public record UpsertCabinetTypeRequest(
            @NotBlank String name,
            @Positive double widthMm,
            @Positive double heightMm,
            Double depthMm,
            @Positive int resolutionWidth,
            @Positive int resolutionHeight,
            @PositiveOrZero double powerConsumptionW,
            @PositiveOrZero double weightKg
    ) {
    }

    public record ImportCabinetTypesRequest(
            @NotNull @Valid List<UpsertCabinetTypeRequest> cabinetTypes
    ) {
    }
}
