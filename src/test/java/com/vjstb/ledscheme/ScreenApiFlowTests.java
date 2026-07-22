package com.vjstb.ledscheme;

import static org.assertj.core.api.Assertions.assertThat;

import com.vjstb.ledscheme.dto.CabinetTypeDtos.CabinetTypeDto;
import com.vjstb.ledscheme.dto.CabinetTypeDtos.UpsertCabinetTypeRequest;
import com.vjstb.ledscheme.dto.ProjectDtos.ProjectDetailDto;
import com.vjstb.ledscheme.dto.ProjectDtos.CreateProjectRequest;
import com.vjstb.ledscheme.dto.SceneDtos.CreateSceneRequest;
import com.vjstb.ledscheme.dto.SceneDtos.SceneSummaryDto;
import com.vjstb.ledscheme.dto.ScreenDtos.CreateScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.PowerChainRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.CabinetStateDto;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplacePowerChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ReplaceSignalChainsRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.RestoreScreenRequest;
import com.vjstb.ledscheme.dto.ScreenDtos.ScreenDetailDto;
import com.vjstb.ledscheme.dto.ScreenDtos.SignalChainRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Прогоняет сценарий через реальные HTTP-эндпоинты (а не напрямую через сервисы),
 * чтобы ловить проблемы, которые всплывают только при JSON-сериализации ответа
 * (например, ленивая загрузка коллекций Hibernate за пределами транзакции).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ScreenApiFlowTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void wiresPowerAndSignalChainsOverHttp() {
        CabinetTypeDto cabinet = rest.postForObject("/api/cabinet-types",
                new UpsertCabinetTypeRequest("HTTP P4 500x500", 500, 500, null, 128, 128, 150, 12),
                CabinetTypeDto.class);

        ProjectDetailDto project = rest.postForObject("/api/projects",
                new CreateProjectRequest("HTTP проект", null), ProjectDetailDto.class);

        SceneSummaryDto scene = rest.postForObject("/api/projects/" + project.id() + "/scenes",
                new CreateSceneRequest("HTTP сцена"), SceneSummaryDto.class);

        ScreenDetailDto screen = rest.postForObject("/api/scenes/" + scene.id() + "/screens",
                new CreateScreenRequest("HTTP экран", cabinet.id(), 2, 3, 0, 0), ScreenDetailDto.class);
        assertThat(screen.cabinets()).hasSize(6);

        List<Long> firstThree = screen.cabinets().stream().map(c -> c.id()).limit(3).toList();
        ResponseEntity<ScreenDetailDto> afterPower = rest.exchange(
                "/api/screens/" + screen.id() + "/power-chains", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new ReplacePowerChainsRequest(
                        List.of(new PowerChainRequest(1, firstThree)))),
                ScreenDetailDto.class);
        assertThat(afterPower.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterPower.getBody().powerChains()).hasSize(1);

        // Эта цепочка сигнала сохраняется вторым запросом — именно тут ранее падала
        // ленивая загрузка powerChains при сериализации ответа за пределами транзакции.
        List<Long> lastThree = screen.cabinets().stream().map(c -> c.id()).skip(3).toList();
        ResponseEntity<ScreenDetailDto> afterSignal = rest.exchange(
                "/api/screens/" + screen.id() + "/signal-chains", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new ReplaceSignalChainsRequest(
                        List.of(new SignalChainRequest(1, false, lastThree)))),
                ScreenDetailDto.class);
        assertThat(afterSignal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterSignal.getBody().signalChains()).hasSize(1);
        assertThat(afterSignal.getBody().powerChains()).hasSize(1);

        ResponseEntity<ScreenDetailDto> refetched = rest.getForEntity(
                "/api/screens/" + screen.id(), ScreenDetailDto.class);
        assertThat(refetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refetched.getBody().powerChains()).hasSize(1);
        assertThat(refetched.getBody().signalChains()).hasSize(1);
    }

    @Test
    void restoreRevertsScreenSnapshotAtomically() {
        CabinetTypeDto cabinet = rest.postForObject("/api/cabinet-types",
                new UpsertCabinetTypeRequest("HTTP restore 500x500", 500, 500, null, 128, 128, 150, 12),
                CabinetTypeDto.class);
        ProjectDetailDto project = rest.postForObject("/api/projects",
                new CreateProjectRequest("restore проект", null), ProjectDetailDto.class);
        SceneSummaryDto scene = rest.postForObject("/api/projects/" + project.id() + "/scenes",
                new CreateSceneRequest("restore сцена"), SceneSummaryDto.class);
        ScreenDetailDto screen = rest.postForObject("/api/scenes/" + scene.id() + "/screens",
                new CreateScreenRequest("restore экран", cabinet.id(), 2, 2, 0, 0), ScreenDetailDto.class);

        // Снимок «пустого» состояния сразу после создания экрана.
        RestoreScreenRequest emptySnapshot = new RestoreScreenRequest(
                screen.name(), screen.posXMm(), screen.posYMm(),
                screen.cabinets().stream().map(c -> new CabinetStateDto(c.id(), c.phase(), c.hidden())).toList(),
                List.of(), List.of());

        // Мутируем: добавляем цепочку питания на фазе 2.
        List<Long> ids = screen.cabinets().stream().map(c -> c.id()).limit(2).toList();
        ScreenDetailDto mutated = rest.exchange(
                "/api/screens/" + screen.id() + "/power-chains", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new ReplacePowerChainsRequest(
                        List.of(new PowerChainRequest(2, ids)))),
                ScreenDetailDto.class).getBody();
        assertThat(mutated.powerChains()).hasSize(1);

        // Отменяем через restore — состояние должно вернуться к пустому.
        ResponseEntity<ScreenDetailDto> restored = rest.exchange(
                "/api/screens/" + screen.id() + "/restore", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(emptySnapshot), ScreenDetailDto.class);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restored.getBody().powerChains()).isEmpty();
        assertThat(restored.getBody().signalChains()).isEmpty();
    }
}
