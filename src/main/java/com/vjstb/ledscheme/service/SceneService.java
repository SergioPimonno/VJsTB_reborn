package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.domain.Project;
import com.vjstb.ledscheme.domain.Scene;
import com.vjstb.ledscheme.dto.SceneDtos.CreateSceneRequest;
import com.vjstb.ledscheme.dto.SceneDtos.SceneSummaryDto;
import com.vjstb.ledscheme.dto.SceneDtos.UpdateSceneRequest;
import com.vjstb.ledscheme.exception.NotFoundException;
import com.vjstb.ledscheme.repository.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SceneService {

    private final SceneRepository sceneRepository;
    private final ProjectService projectService;

    public SceneService(SceneRepository sceneRepository, ProjectService projectService) {
        this.sceneRepository = sceneRepository;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public Scene getEntity(Long id) {
        return sceneRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Сцена с id=" + id + " не найдена"));
    }

    public SceneSummaryDto create(Long projectId, CreateSceneRequest request) {
        Project project = projectService.getEntity(projectId);
        Scene scene = new Scene();
        scene.setName(request.name());
        scene.setOrderIndex(project.getScenes().size());
        scene.setProject(project);
        project.getScenes().add(scene);
        sceneRepository.save(scene);
        return ProjectService.toSceneSummary(scene);
    }

    public SceneSummaryDto update(Long id, UpdateSceneRequest request) {
        Scene scene = getEntity(id);
        scene.setName(request.name());
        scene.setOrderIndex(request.orderIndex());
        return ProjectService.toSceneSummary(sceneRepository.save(scene));
    }

    public void delete(Long id) {
        Scene scene = getEntity(id);
        scene.getProject().getScenes().remove(scene);
        sceneRepository.delete(scene);
    }
}
