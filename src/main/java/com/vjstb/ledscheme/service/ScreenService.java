package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.domain.CabinetInstance;
import com.vjstb.ledscheme.domain.CabinetType;
import com.vjstb.ledscheme.domain.PowerChain;
import com.vjstb.ledscheme.domain.Scene;
import com.vjstb.ledscheme.domain.Screen;
import com.vjstb.ledscheme.domain.SignalChain;
import com.vjstb.ledscheme.dto.ScreenDtos.CabinetInstanceDto;
import com.vjstb.ledscheme.dto.ScreenDtos.CabinetStateDto;
import com.vjstb.ledscheme.dto.ScreenDtos.CreateScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.RestoreScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.PowerChainDto;
import com.vjstb.ledscheme.dto.ScreenDtos.PowerChainRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplacePowerChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplaceSignalChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ScreenDetailDto;
import com.vjstb.ledscheme.dto.ScreenDtos.SignalChainDto;
import com.vjstb.ledscheme.dto.ScreenDtos.SignalChainRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.UpdateCabinetRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.UpdateScreenPositionRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.UpdateScreenRequest;
import com.vjstb.ledscheme.exception.NotFoundException;
import com.vjstb.ledscheme.repository.CabinetInstanceRepository;
import com.vjstb.ledscheme.repository.ScreenRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScreenService {

    private final ScreenRepository screenRepository;
    private final CabinetInstanceRepository cabinetInstanceRepository;
    private final SceneService sceneService;
    private final CabinetLibraryService cabinetLibraryService;

    public ScreenService(ScreenRepository screenRepository,
                          CabinetInstanceRepository cabinetInstanceRepository,
                          SceneService sceneService,
                          CabinetLibraryService cabinetLibraryService) {
        this.screenRepository = screenRepository;
        this.cabinetInstanceRepository = cabinetInstanceRepository;
        this.sceneService = sceneService;
        this.cabinetLibraryService = cabinetLibraryService;
    }

    @Transactional(readOnly = true)
    public Screen getEntity(Long id) {
        return screenRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Экран с id=" + id + " не найден"));
    }

    @Transactional(readOnly = true)
    public ScreenDetailDto getDetail(Long id) {
        return toDetail(getEntity(id));
    }

    public ScreenDetailDto create(Long sceneId, CreateScreenRequest request) {
        Scene scene = sceneService.getEntity(sceneId);
        CabinetType cabinetType = cabinetLibraryService.getEntity(request.cabinetTypeId());

        Screen screen = new Screen();
        screen.setName(request.name());
        screen.setScene(scene);
        screen.setCabinetType(cabinetType);
        screen.setRows(request.rows());
        screen.setCols(request.cols());
        screen.setPosXMm(request.posXMm());
        screen.setPosYMm(request.posYMm());
        buildGrid(screen, request.rows(), request.cols());
        scene.getScreens().add(screen);
        return toDetail(screenRepository.save(screen));
    }

    public ScreenDetailDto updateGrid(Long id, UpdateScreenRequest request) {
        Screen screen = getEntity(id);
        CabinetType cabinetType = cabinetLibraryService.getEntity(request.cabinetTypeId());
        screen.setName(request.name());
        screen.setCabinetType(cabinetType);
        resizeGrid(screen, request.rows(), request.cols());
        screen.setRows(request.rows());
        screen.setCols(request.cols());
        return toDetail(screenRepository.save(screen));
    }

    public ScreenDetailDto updatePosition(Long id, UpdateScreenPositionRequest request) {
        Screen screen = getEntity(id);
        screen.setPosXMm(request.posXMm());
        screen.setPosYMm(request.posYMm());
        return toDetail(screenRepository.save(screen));
    }

    public void delete(Long id) {
        Screen screen = getEntity(id);
        screen.getScene().getScreens().remove(screen);
        screenRepository.delete(screen);
    }

    public ScreenDetailDto updateCabinet(Long screenId, Long cabinetId, UpdateCabinetRequest request) {
        Screen screen = getEntity(screenId);
        CabinetInstance cabinet = screen.getCabinets().stream()
                .filter(c -> c.getId().equals(cabinetId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Кабинет с id=" + cabinetId + " не найден на экране"));
        if (request.phase() != null) {
            if (request.phase() < 0 || request.phase() > 3) {
                throw new IllegalArgumentException("phase должен быть от 0 до 3");
            }
            cabinet.setPhase(request.phase());
        }
        if (request.hidden() != null) {
            cabinet.setHidden(request.hidden());
        }
        cabinetInstanceRepository.save(cabinet);
        return toDetail(screen);
    }

    public ScreenDetailDto replacePowerChains(Long screenId, ReplacePowerChainsRequest request) {
        Screen screen = getEntity(screenId);
        Set<Long> validIds = cabinetIds(screen);
        applyPowerChains(screen, request.chains(), validIds);
        return toDetail(screenRepository.save(screen));
    }

    public ScreenDetailDto replaceSignalChains(Long screenId, ReplaceSignalChainsRequest request) {
        Screen screen = getEntity(screenId);
        Set<Long> validIds = cabinetIds(screen);
        applySignalChains(screen, request.chains(), validIds);
        return toDetail(screenRepository.save(screen));
    }

    /**
     * Атомарно восстанавливает снимок редактируемого состояния экрана (undo):
     * имя, позиция, состояние кабинетов и цепочки — за одну транзакцию.
     */
    public ScreenDetailDto restore(Long screenId, RestoreScreenRequest request) {
        Screen screen = getEntity(screenId);
        Set<Long> validIds = cabinetIds(screen);

        screen.setName(request.name());
        screen.setPosXMm(request.posXMm());
        screen.setPosYMm(request.posYMm());

        Map<Long, CabinetInstance> byId = new HashMap<>();
        for (CabinetInstance c : screen.getCabinets()) {
            byId.put(c.getId(), c);
        }
        for (CabinetStateDto cs : request.cabinets()) {
            CabinetInstance cab = byId.get(cs.id());
            if (cab == null) {
                throw new IllegalArgumentException("Кабинет с id=" + cs.id() + " не принадлежит этому экрану");
            }
            if (cs.phase() < 0 || cs.phase() > 3) {
                throw new IllegalArgumentException("phase должен быть от 0 до 3");
            }
            cab.setPhase(cs.phase());
            cab.setHidden(cs.hidden());
        }

        applyPowerChains(screen, request.powerChains(), validIds);
        applySignalChains(screen, request.signalChains(), validIds);
        return toDetail(screenRepository.save(screen));
    }

    private Set<Long> cabinetIds(Screen screen) {
        return screen.getCabinets().stream().map(CabinetInstance::getId).collect(Collectors.toSet());
    }

    private void applyPowerChains(Screen screen, List<PowerChainRequest> chains, Set<Long> validIds) {
        screen.getPowerChains().clear();
        for (PowerChainRequest cr : chains) {
            if (cr.phase() < 1 || cr.phase() > 3) {
                throw new IllegalArgumentException("phase цепочки питания должен быть 1, 2 или 3");
            }
            validateCabinetIds(cr.cabinetInstanceIds(), validIds);
            PowerChain chain = new PowerChain();
            chain.setScreen(screen);
            chain.setPhase(cr.phase());
            chain.setCabinetInstanceIds(cr.cabinetInstanceIds());
            screen.getPowerChains().add(chain);
        }
    }

    private void applySignalChains(Screen screen, List<SignalChainRequest> chains, Set<Long> validIds) {
        screen.getSignalChains().clear();
        for (SignalChainRequest cr : chains) {
            validateCabinetIds(cr.cabinetInstanceIds(), validIds);
            SignalChain chain = new SignalChain();
            chain.setScreen(screen);
            chain.setPortNumber(cr.portNumber());
            chain.setBackup(cr.backup());
            chain.setCabinetInstanceIds(cr.cabinetInstanceIds());
            screen.getSignalChains().add(chain);
        }
    }

    private void validateCabinetIds(List<Long> ids, Set<Long> validIds) {
        for (Long cabId : ids) {
            if (!validIds.contains(cabId)) {
                throw new IllegalArgumentException("Кабинет с id=" + cabId + " не принадлежит этому экрану");
            }
        }
    }

    private void buildGrid(Screen screen, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                CabinetInstance cab = new CabinetInstance();
                cab.setScreen(screen);
                cab.setRowIndex(r);
                cab.setColIndex(c);
                screen.getCabinets().add(cab);
            }
        }
    }

    /** Изменяет размер сетки, сохраняя существующие кабинеты в пределах новых границ. */
    private void resizeGrid(Screen screen, int newRows, int newCols) {
        Map<String, CabinetInstance> existing = new HashMap<>();
        for (CabinetInstance c : screen.getCabinets()) {
            existing.put(c.getRowIndex() + ":" + c.getColIndex(), c);
        }

        Set<Long> removedIds = new HashSet<>();
        screen.getCabinets().removeIf(c -> {
            boolean outOfBounds = c.getRowIndex() >= newRows || c.getColIndex() >= newCols;
            if (outOfBounds) {
                removedIds.add(c.getId());
            }
            return outOfBounds;
        });

        for (int r = 0; r < newRows; r++) {
            for (int c = 0; c < newCols; c++) {
                if (!existing.containsKey(r + ":" + c)) {
                    CabinetInstance cab = new CabinetInstance();
                    cab.setScreen(screen);
                    cab.setRowIndex(r);
                    cab.setColIndex(c);
                    screen.getCabinets().add(cab);
                }
            }
        }

        if (!removedIds.isEmpty()) {
            screen.getPowerChains().forEach(chain -> chain.getCabinetInstanceIds().removeIf(removedIds::contains));
            screen.getPowerChains().removeIf(chain -> chain.getCabinetInstanceIds().isEmpty());
            screen.getSignalChains().forEach(chain -> chain.getCabinetInstanceIds().removeIf(removedIds::contains));
            screen.getSignalChains().removeIf(chain -> chain.getCabinetInstanceIds().isEmpty());
        }
    }

    static ScreenDetailDto toDetail(Screen s) {
        List<CabinetInstanceDto> cabinets = s.getCabinets().stream()
                .map(c -> new CabinetInstanceDto(c.getId(), c.getRowIndex(), c.getColIndex(), c.isHidden(), c.getPhase()))
                .toList();
        List<PowerChainDto> powerChains = s.getPowerChains().stream()
                .map(pc -> new PowerChainDto(pc.getId(), pc.getPhase(), List.copyOf(pc.getCabinetInstanceIds())))
                .toList();
        List<SignalChainDto> signalChains = s.getSignalChains().stream()
                .map(sc -> new SignalChainDto(sc.getId(), sc.getPortNumber(), sc.isBackup(), List.copyOf(sc.getCabinetInstanceIds())))
                .toList();
        return new ScreenDetailDto(
                s.getId(), s.getName(), s.getScene().getId(),
                s.getCabinetType().getId(), s.getCabinetType().getName(),
                s.getRows(), s.getCols(), s.getPosXMm(), s.getPosYMm(),
                s.getPhysicalWidthMm(), s.getPhysicalHeightMm(),
                s.getResolutionWidthPx(), s.getResolutionHeightPx(),
                s.getTotalPowerW(), s.getTotalWeightKg(), s.getActiveCabinetCount(),
                cabinets, powerChains, signalChains
        );
    }
}
