package com.vjstb.ledscheme.web;

import com.vjstb.ledscheme.dto.ScreenDtos.CreateScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplacePowerChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplaceSignalChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.RestoreScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ScreenDetailDto;
import com.vjstb.ledscheme.dto.ScreenDtos.UpdateCabinetRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.UpdateScreenPositionRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.UpdateScreenRequest;
import com.vjstb.ledscheme.service.ScreenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScreenController {

    private final ScreenService screenService;

    public ScreenController(ScreenService screenService) {
        this.screenService = screenService;
    }

    @PostMapping("/api/scenes/{sceneId}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenDetailDto create(@PathVariable Long sceneId, @Valid @RequestBody CreateScreenRequest request) {
        return screenService.create(sceneId, request);
    }

    @GetMapping("/api/screens/{id}")
    public ScreenDetailDto getDetail(@PathVariable Long id) {
        return screenService.getDetail(id);
    }

    @PutMapping("/api/screens/{id}")
    public ScreenDetailDto updateGrid(@PathVariable Long id, @Valid @RequestBody UpdateScreenRequest request) {
        return screenService.updateGrid(id, request);
    }

    @PutMapping("/api/screens/{id}/position")
    public ScreenDetailDto updatePosition(@PathVariable Long id, @Valid @RequestBody UpdateScreenPositionRequest request) {
        return screenService.updatePosition(id, request);
    }

    @DeleteMapping("/api/screens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        screenService.delete(id);
    }

    @PatchMapping("/api/screens/{screenId}/cabinets/{cabinetId}")
    public ScreenDetailDto updateCabinet(@PathVariable Long screenId, @PathVariable Long cabinetId,
                                          @RequestBody UpdateCabinetRequest request) {
        return screenService.updateCabinet(screenId, cabinetId, request);
    }

    @PutMapping("/api/screens/{id}/power-chains")
    public ScreenDetailDto replacePowerChains(@PathVariable Long id, @Valid @RequestBody ReplacePowerChainsRequest request) {
        return screenService.replacePowerChains(id, request);
    }

    @PutMapping("/api/screens/{id}/signal-chains")
    public ScreenDetailDto replaceSignalChains(@PathVariable Long id, @Valid @RequestBody ReplaceSignalChainsRequest request) {
        return screenService.replaceSignalChains(id, request);
    }

    /** Атомарно восстанавливает снимок состояния экрана (используется для «отменить»). */
    @PutMapping("/api/screens/{id}/restore")
    public ScreenDetailDto restore(@PathVariable Long id, @Valid @RequestBody RestoreScreenRequest request) {
        return screenService.restore(id, request);
    }
}
