package com.vjstb.ledscheme.web;

import com.vjstb.ledscheme.dto.ProjectDtos.CreateProjectRequest;
import com.vjstb.ledscheme.dto.ProjectDtos.ProjectDetailDto;
import com.vjstb.ledscheme.dto.ProjectDtos.ProjectSummaryDto;
import com.vjstb.ledscheme.dto.ProjectDtos.UpdateProjectRequest;
import com.vjstb.ledscheme.service.ProjectService;
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

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectSummaryDto> findAll() {
        return projectService.findAll();
    }

    @GetMapping("/{id}")
    public ProjectDetailDto getDetail(@PathVariable Long id) {
        return projectService.getDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetailDto create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/{id}")
    public ProjectDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }
}
