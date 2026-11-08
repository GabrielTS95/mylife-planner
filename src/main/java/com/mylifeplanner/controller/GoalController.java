package com.mylifeplanner.controller;

import com.mylifeplanner.model.Goal;
import com.mylifeplanner.service.GoalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/goals")
public class GoalController {
    private final GoalService service;
    public GoalController(GoalService service) { this.service = service; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("activePage", "goals");
        model.addAttribute("goals", service.findAll());
        model.addAttribute("goal", new Goal());
        return "goals/list";
    }

    @PostMapping
    public String save(@ModelAttribute Goal goal) {
        service.save(goal);
        return "redirect:/goals";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/goals";
    }
}
