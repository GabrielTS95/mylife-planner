package com.mylifeplanner.controller;

import com.mylifeplanner.model.*;
import com.mylifeplanner.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Controller
@RequestMapping("/tasks")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }

    @GetMapping
    public String list(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        model.addAttribute("activePage", "tasks");
        model.addAttribute("tasks", service.findAll());
        model.addAttribute("tasksByStatus", service.findAllGroupedByStatus());
        model.addAttribute("task", new Task());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekStart.plusDays(6));
        model.addAttribute("today", today);
        return "tasks/list";
    }

    @PostMapping
    public String save(@ModelAttribute Task task) {
        service.save(task);
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id) {
        changeStatus(id, TaskStatus.FINALIZADO);
        return "redirect:/tasks";
    }

    @PostMapping(value = "/{id}/status/{status}", params = "!inline")
    public String updateStatus(@PathVariable Long id, @PathVariable TaskStatus status) {
        changeStatus(id, status);
        return "redirect:/tasks";
    }

    @PostMapping(value = "/{id}/status/{status}", params = "inline")
    @ResponseBody
    public ResponseEntity<Void> updateStatusInline(@PathVariable Long id, @PathVariable TaskStatus status) {
        changeStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    private void changeStatus(Long id, TaskStatus status) {
        Task task = service.findById(id);
        task.setStatus(status);
        service.save(task);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/tasks";
    }
}
