package com.vjstb.ledscheme;

import static org.assertj.core.api.Assertions.assertThat;

import com.vjstb.ledscheme.dto.CabinetTypeDtos.CabinetTypeDto;
import com.vjstb.ledscheme.dto.CabinetTypeDtos.UpsertCabinetTypeRequest;
import com.vjstb.ledscheme.dto.ProjectDtos.CreateProjectRequest;
import com.vjstb.ledscheme.dto.ProjectDtos.ProjectDetailDto;
import com.vjstb.ledscheme.dto.SceneDtos.CreateSceneRequest;
import com.vjstb.ledscheme.dto.SceneDtos.SceneSummaryDto;
import com.vjstb.ledscheme.dto.ScreenDtos.CreateScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.PowerChainRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplacePowerChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ScreenDetailDto;
import com.vjstb.ledscheme.service.CabinetLibraryService;
import com.vjstb.ledscheme.service.ProjectService;
import com.vjstb.ledscheme.service.SceneService;
import com.vjstb.ledscheme.service.ScreenService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Проверяет базовый сценарий: проект -> сцена -> экран из кабинетов библиотеки
 * -> цепочка расключения питания, с пересчётом характеристик экрана.
 */
@SpringBootTest
class ScreenWiringFlowTests {

    @Autowired
    private CabinetLibraryService cabinetLibraryService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private SceneService sceneService;
    @Autowired
    private ScreenService screenService;

    @Test
    void createsProjectSceneScreenAndWiresPowerChain() {
        CabinetTypeDto cabinet = cabinetLibraryService.create(
                new UpsertCabinetTypeRequest("Test P3 500x500", 500, 500, 80.0, 128, 128, 150, 12));

        ProjectDetailDto project = projectService.create(new CreateProjectRequest("Тестовый проект", null));
        SceneSummaryDto scene = sceneService.create(project.id(), new CreateSceneRequest("Сцена 1"));
        ScreenDetailDto screen = screenService.create(scene.id(),
                new CreateScreenRequest("Экран A", cabinet.id(), 2, 3, 0, 0));

        assertThat(screen.cabinets()).hasSize(6);
        assertThat(screen.resolutionWidthPx()).isEqualTo(3 * 128);
        assertThat(screen.resolutionHeightPx()).isEqualTo(2 * 128);
        assertThat(screen.totalPowerW()).isEqualTo(6 * 150.0);

        List<Long> chainIds = screen.cabinets().stream().map(c -> c.id()).limit(3).toList();
        ScreenDetailDto updated = screenService.replacePowerChains(screen.id(),
                new ReplacePowerChainsRequest(List.of(new PowerChainRequest(1, chainIds))));

        assertThat(updated.powerChains()).hasSize(1);
        assertThat(updated.powerChains().get(0).cabinetInstanceIds()).containsExactlyElementsOf(chainIds);
    }
}
