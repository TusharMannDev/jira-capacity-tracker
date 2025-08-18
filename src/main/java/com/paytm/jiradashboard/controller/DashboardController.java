package com.paytm.jiradashboard.controller;

import com.paytm.jiradashboard.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@Slf4j
public class DashboardController {
    
    @Autowired
    private DashboardService dashboardService;
    
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        try {
            Map<String, Object> summary = dashboardService.getDailySummary();
            model.addAttribute("summary", summary);
            return "dashboard";
        } catch (Exception e) {
            log.error("Error loading dashboard", e);
            model.addAttribute("error", "Error loading dashboard data");
            return "error";
        }
    }
    
    @GetMapping("/api/summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSummary() {
        try {
            Map<String, Object> summary = dashboardService.getDailySummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/api/team/{assignee}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTeamMemberSummary(@PathVariable String assignee) {
        try {
            Map<String, Object> summary = dashboardService.getTeamMemberSummary(assignee);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting team member summary for: {}", assignee, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/api/project/{projectKey}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProjectSummary(@PathVariable String projectKey) {
        try {
            Map<String, Object> summary = dashboardService.getProjectSummary(projectKey);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting project summary for: {}", projectKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/api/sync")
    @ResponseBody
    public ResponseEntity<String> syncIssues() {
        try {
            dashboardService.syncIssuesFromJira();
            return ResponseEntity.ok("Sync completed successfully");
        } catch (Exception e) {
            log.error("Error syncing issues", e);
            return ResponseEntity.internalServerError().body("Error syncing issues: " + e.getMessage());
        }
    }
    
    @GetMapping("/team/{assignee}")
    public String teamMemberDashboard(@PathVariable String assignee, Model model) {
        try {
            Map<String, Object> summary = dashboardService.getTeamMemberSummary(assignee);
            model.addAttribute("summary", summary);
            model.addAttribute("assignee", assignee);
            return "team-member";
        } catch (Exception e) {
            log.error("Error loading team member dashboard for: {}", assignee, e);
            model.addAttribute("error", "Error loading team member data");
            return "error";
        }
    }
    
    @GetMapping("/project/{projectKey}")
    public String projectDashboard(@PathVariable String projectKey, Model model) {
        try {
            Map<String, Object> summary = dashboardService.getProjectSummary(projectKey);
            model.addAttribute("summary", summary);
            model.addAttribute("projectKey", projectKey);
            return "project";
        } catch (Exception e) {
            log.error("Error loading project dashboard for: {}", projectKey, e);
            model.addAttribute("error", "Error loading project data");
            return "error";
        }
    }
} 