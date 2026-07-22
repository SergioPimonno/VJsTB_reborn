package com.vjstb.ledscheme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public final class ScreenDtos {

    private ScreenDtos() {
    }

    public record ScreenDetailDto(
            Long id,
            String name,
            Long sceneId,
            Long cabinetTypeId,
            String cabinetTypeName,
            int rows,
            int cols,
            double posXMm,
            double posYMm,
            double physicalWidthMm,
            double physicalHeightMm,
            int resolutionWidthPx,
            int resolutionHeightPx,
            double totalPowerW,
            double totalWeightKg,
            int activeCabinetCount,
            List<CabinetInstanceDto> cabinets,
            List<PowerChainDto> powerChains,
            List<SignalChainDto> signalChains
    ) {
    }

    public record CreateScreenRequest(
            @NotBlank String name,
            @NotNull Long cabinetTypeId,
            @Positive int rows,
            @Positive int cols,
            double posXMm,
            double posYMm
    ) {
    }

    public record UpdateScreenRequest(
            @NotBlank String name,
            @NotNull Long cabinetTypeId,
            @Positive int rows,
            @Positive int cols
    ) {
    }

    public record UpdateScreenPositionRequest(
            double posXMm,
            double posYMm
    ) {
    }

    public record CabinetInstanceDto(
            Long id,
            int rowIndex,
            int colIndex,
            boolean hidden,
            int phase
    ) {
    }

    public record UpdateCabinetRequest(
            Integer phase,
            Boolean hidden
    ) {
    }

    public record PowerChainDto(
            Long id,
            int phase,
            List<Long> cabinetInstanceIds
    ) {
    }

    public record PowerChainRequest(
            @NotNull Integer phase,
            @NotNull List<Long> cabinetInstanceIds
    ) {
    }

    public record ReplacePowerChainsRequest(
            @NotNull @Valid List<PowerChainRequest> chains
    ) {
    }

    public record SignalChainDto(
            Long id,
            Integer portNumber,
            boolean backup,
            List<Long> cabinetInstanceIds
    ) {
    }

    public record SignalChainRequest(
            Integer portNumber,
            boolean backup,
            @NotNull List<Long> cabinetInstanceIds
    ) {
    }

    public record ReplaceSignalChainsRequest(
            @NotNull @Valid List<SignalChainRequest> chains
    ) {
    }

    /** Состояние одного кабинета для восстановления снимка (undo). */
    public record CabinetStateDto(
            @NotNull Long id,
            int phase,
            boolean hidden
    ) {
    }

    /**
     * Полный снимок редактируемого состояния экрана для операции «отменить».
     * Применяется атомарно: имя, позиция, состояние кабинетов (фаза/скрытие),
     * цепочки питания и сигнала. Сетка (rows/cols/тип кабинета) не меняется —
     * снимок валиден только пока набор кабинетов тот же.
     */
    public record RestoreScreenRequest(
            @NotBlank String name,
            double posXMm,
            double posYMm,
            @NotNull @Valid List<CabinetStateDto> cabinets,
            @NotNull @Valid List<PowerChainRequest> powerChains,
            @NotNull @Valid List<SignalChainRequest> signalChains
    ) {
    }
}
