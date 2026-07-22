package com.vjstb.ledscheme.web;

import com.vjstb.ledscheme.dto.CabinetTypeDtos.CabinetTypeDto;
import com.vjstb.ledscheme.dto.CabinetTypeDtos.ImportCabinetTypesRequest;
import com.vjstb.ledscheme.dto.CabinetTypeDtos.UpsertCabinetTypeRequest;
import com.vjstb.ledscheme.service.CabinetLibraryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Библиотека LED cabinets: CRUD + импорт/экспорт JSON. */
@RestController
@RequestMapping("/api/cabinet-types")
public class CabinetTypeController {

    private final CabinetLibraryService cabinetLibraryService;

    public CabinetTypeController(CabinetLibraryService cabinetLibraryService) {
        this.cabinetLibraryService = cabinetLibraryService;
    }

    @GetMapping
    public List<CabinetTypeDto> findAll() {
        return cabinetLibraryService.findAll();
    }

    @GetMapping("/{id}")
    public CabinetTypeDto getById(@PathVariable Long id) {
        return cabinetLibraryService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CabinetTypeDto create(@Valid @RequestBody UpsertCabinetTypeRequest request) {
        return cabinetLibraryService.create(request);
    }

    @PutMapping("/{id}")
    public CabinetTypeDto update(@PathVariable Long id, @Valid @RequestBody UpsertCabinetTypeRequest request) {
        return cabinetLibraryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        cabinetLibraryService.delete(id);
    }

    /** Экспорт всей библиотеки одним JSON-массивом. */
    @GetMapping("/export")
    public List<CabinetTypeDto> export() {
        return cabinetLibraryService.findAll();
    }

    /** Импорт библиотеки: существующие по имени записи обновляются, новые создаются. */
    @PostMapping("/import")
    public List<CabinetTypeDto> importAll(@Valid @RequestBody ImportCabinetTypesRequest request) {
        return cabinetLibraryService.importAll(request.cabinetTypes());
    }
}
