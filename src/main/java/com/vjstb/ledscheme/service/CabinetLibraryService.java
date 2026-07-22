package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.domain.CabinetType;
import com.vjstb.ledscheme.dto.CabinetTypeDtos.CabinetTypeDto;
import com.vjstb.ledscheme.dto.CabinetTypeDtos.UpsertCabinetTypeRequest;
import com.vjstb.ledscheme.exception.ConflictException;
import com.vjstb.ledscheme.exception.NotFoundException;
import com.vjstb.ledscheme.repository.CabinetTypeRepository;
import com.vjstb.ledscheme.repository.ScreenRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Библиотека LED cabinets: общая для всех проектов, с импортом/экспортом в JSON.
 */
@Service
@Transactional
public class CabinetLibraryService {

    private final CabinetTypeRepository cabinetTypeRepository;
    private final ScreenRepository screenRepository;

    public CabinetLibraryService(CabinetTypeRepository cabinetTypeRepository, ScreenRepository screenRepository) {
        this.cabinetTypeRepository = cabinetTypeRepository;
        this.screenRepository = screenRepository;
    }

    @Transactional(readOnly = true)
    public List<CabinetTypeDto> findAll() {
        return cabinetTypeRepository.findAll().stream().map(CabinetLibraryService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CabinetTypeDto getById(Long id) {
        return toDto(getEntity(id));
    }

    @Transactional(readOnly = true)
    public CabinetType getEntity(Long id) {
        return cabinetTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Кабинет с id=" + id + " не найден в библиотеке"));
    }

    public CabinetTypeDto create(UpsertCabinetTypeRequest request) {
        if (cabinetTypeRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Кабинет с именем \"" + request.name() + "\" уже есть в библиотеке");
        }
        CabinetType entity = new CabinetType();
        applyRequest(entity, request);
        return toDto(cabinetTypeRepository.save(entity));
    }

    public CabinetTypeDto update(Long id, UpsertCabinetTypeRequest request) {
        CabinetType entity = getEntity(id);
        if (!entity.getName().equalsIgnoreCase(request.name())
                && cabinetTypeRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Кабинет с именем \"" + request.name() + "\" уже есть в библиотеке");
        }
        applyRequest(entity, request);
        return toDto(cabinetTypeRepository.save(entity));
    }

    public void delete(Long id) {
        CabinetType entity = getEntity(id);
        if (screenRepository.existsByCabinetType_Id(id)) {
            throw new ConflictException("Кабинет \"" + entity.getName()
                    + "\" используется на одном из экранов и не может быть удалён из библиотеки");
        }
        cabinetTypeRepository.delete(entity);
    }

    /** Импорт библиотеки: существующие по имени записи обновляются, новые создаются. */
    public List<CabinetTypeDto> importAll(List<UpsertCabinetTypeRequest> requests) {
        Map<String, Long> idsByName = new HashMap<>();
        for (CabinetType ct : cabinetTypeRepository.findAll()) {
            idsByName.put(ct.getName().toLowerCase(), ct.getId());
        }
        List<CabinetTypeDto> result = new ArrayList<>();
        for (UpsertCabinetTypeRequest req : requests) {
            Long existingId = idsByName.get(req.name().toLowerCase());
            CabinetTypeDto saved = existingId != null ? update(existingId, req) : create(req);
            idsByName.put(req.name().toLowerCase(), saved.id());
            result.add(saved);
        }
        return result;
    }

    private void applyRequest(CabinetType entity, UpsertCabinetTypeRequest request) {
        entity.setName(request.name());
        entity.setWidthMm(request.widthMm());
        entity.setHeightMm(request.heightMm());
        entity.setDepthMm(request.depthMm());
        entity.setResolutionWidth(request.resolutionWidth());
        entity.setResolutionHeight(request.resolutionHeight());
        entity.setPowerConsumptionW(request.powerConsumptionW());
        entity.setWeightKg(request.weightKg());
    }

    static CabinetTypeDto toDto(CabinetType c) {
        return new CabinetTypeDto(
                c.getId(),
                c.getName(),
                c.getWidthMm(),
                c.getHeightMm(),
                c.getDepthMm(),
                c.getResolutionWidth(),
                c.getResolutionHeight(),
                c.getPowerConsumptionW(),
                c.getWeightKg()
        );
    }
}
