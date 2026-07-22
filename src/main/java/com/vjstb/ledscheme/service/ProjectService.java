package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.domain.Project;
import com.vjstb.ledscheme.domain.Scene;
import com.vjstb.ledscheme.dto.ProjectDtos.CreateProjectRequest;
import com.vjstb.ledscheme.dto.ProjectDtos.ProjectDetailDto;
import com.vjstb.ledscheme.dto.ProjectDtos.ProjectSummaryDto;
import com.vjstb.ledscheme.dto.ProjectDtos.UpdateProjectRequest;
import com.vjstb.ledscheme.dto.SceneDtos.SceneSummaryDto;
import com.vjstb.ledscheme.dto.SceneDtos.ScreenSummaryDto;
import com.vjstb.ledscheme.exception.NotFoundException;
import com.vjstb.ledscheme.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> findAll() {
        return projectRepository.findAll().stream().map(ProjectService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailDto getDetail(Long id) {
        return toDetail(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Project getEntity(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Проект с id=" + id + " не найден"));
    }

    public ProjectDetailDto create(CreateProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        return toDetail(projectRepository.save(project));
    }

    public ProjectDetailDto update(Long id, UpdateProjectRequest request) {
        Project project = getEntity(id);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setUpdatedAt(Instant.now());
        return toDetail(projectRepository.save(project));
    }

    public void delete(Long id) {
        Project project = getEntity(id);
        projectRepository.delete(project);
    }

    static ProjectSummaryDto toSummary(Project p) {
        return new ProjectSummaryDto(p.getId(), p.getName(), p.getDescription(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getScenes().size());
    }

    static ProjectDetailDto toDetail(Project p) {
        List<SceneSummaryDto> scenes = p.getScenes().stream().map(ProjectService::toSceneSummary).toList();
        return new ProjectDetailDto(p.getId(), p.getName(), p.getDescription(),
                p.getCreatedAt(), p.getUpdatedAt(), scenes);
    }

    static SceneSummaryDto toSceneSummary(Scene s) {
        List<ScreenSummaryDto> screens = s.getScreens().stream()
                .map(scr -> new ScreenSummaryDto(scr.getId(), scr.getName(),
                        scr.getCabinetType().getId(), scr.getCabinetType().getName(),
                        scr.getRows(), scr.getCols(), scr.getPosXMm(), scr.getPosYMm()))
                .toList();
        return new SceneSummaryDto(s.getId(), s.getName(), s.getOrderIndex(), screens);
    }
}
