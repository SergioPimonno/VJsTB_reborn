package com.vjstb.ledscheme.web;

import com.vjstb.ledscheme.dto.SceneDtos.CreateSceneRequest;
import com.vjstb.ledscheme.dto.SceneDtos.SceneSummaryDto;
import com.vjstb.ledscheme.dto.SceneDtos.UpdateSceneRequest;
import com.vjstb.ledscheme.service.SceneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @PostMapping("/api/projects/{projectId}/scenes")
    @ResponseStatus(HttpStatus.CREATED)
    public SceneSummaryDto create(@PathVariable Long projectId, @Valid @RequestBody CreateSceneRequest request) {
        return sceneService.create(projectId, request);
    }

    @PutMapping("/api/scenes/{id}")
    public SceneSummaryDto update(@PathVariable Long id, @Valid @RequestBody UpdateSceneRequest request) {
        return sceneService.update(id, request);
    }

    @DeleteMapping("/api/scenes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sceneService.delete(id);
    }
}
