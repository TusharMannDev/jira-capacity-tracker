package com.paytm.jiradashboard.controller;

import com.paytm.jiradashboard.model.*;
import com.paytm.jiradashboard.repository.*;
import com.paytm.jiradashboard.service.CapacityPlanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/capacity")
@RequiredArgsConstructor
@Slf4j
public class CapacityController {
    
    private final CapacityPlanningService capacityPlanningService;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    
    @GetMapping("/team-summary")
    public ResponseEntity<List<CapacityPlanningService.TeamCapacitySummary>> getTeamCapacitySummary() {
        try {
            List<CapacityPlanningService.TeamCapacitySummary> summary = capacityPlanningService.getTeamCapacitySummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting team capacity summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/resource-availability")
    public ResponseEntity<List<CapacityPlanningService.ResourceAvailability>> getResourceAvailability(
            @RequestParam(defaultValue = "30") int daysAhead) {
        try {
            List<CapacityPlanningService.ResourceAvailability> availability = 
                capacityPlanningService.getResourceAvailabilityForecast(daysAhead);
            return ResponseEntity.ok(availability);
        } catch (Exception e) {
            log.error("Error getting resource availability", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/team-members")
    public ResponseEntity<List<TeamMember>> getAllTeamMembers() {
        try {
            List<TeamMember> members = teamMemberRepository.findAll();
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            log.error("Error getting team members", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/team-members")
    public ResponseEntity<TeamMember> createTeamMember(@RequestBody TeamMember teamMember) {
        try {
            TeamMember saved = teamMemberRepository.save(teamMember);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error creating team member", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PutMapping("/team-members/{id}")
    public ResponseEntity<TeamMember> updateTeamMember(@PathVariable Long id, @RequestBody TeamMember teamMember) {
        try {
            Optional<TeamMember> existing = teamMemberRepository.findById(id);
            if (existing.isPresent()) {
                teamMember.setId(id);
                TeamMember updated = teamMemberRepository.save(teamMember);
                return ResponseEntity.ok(updated);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error updating team member", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/assignments")
    public ResponseEntity<List<TaskAssignment>> getAllAssignments() {
        try {
            List<TaskAssignment> assignments = taskAssignmentRepository.findAll();
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            log.error("Error getting assignments", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/assignments/assignee/{assigneeName}")
    public ResponseEntity<List<TaskAssignment>> getAssignmentsByAssignee(@PathVariable String assigneeName) {
        try {
            List<TaskAssignment> assignments = taskAssignmentRepository.findByAssigneeName(assigneeName);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            log.error("Error getting assignments for assignee: " + assigneeName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/assignments")
    public ResponseEntity<TaskAssignment> createAssignment(@RequestBody TaskAssignment assignment) {
        try {
            TaskAssignment saved = taskAssignmentRepository.save(assignment);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error creating assignment", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PutMapping("/assignments/{id}")
    public ResponseEntity<TaskAssignment> updateAssignment(@PathVariable Long id, @RequestBody TaskAssignment assignment) {
        try {
            Optional<TaskAssignment> existing = taskAssignmentRepository.findById(id);
            if (existing.isPresent()) {
                assignment.setId(id);
                TaskAssignment updated = taskAssignmentRepository.save(assignment);
                return ResponseEntity.ok(updated);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error updating assignment", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/sync-jira")
    public ResponseEntity<Map<String, String>> syncJiraAssignments() {
        try {
            capacityPlanningService.syncJiraAssignments();
            return ResponseEntity.ok(Map.of("status", "success", "message", "Jira assignments synced successfully"));
        } catch (Exception e) {
            log.error("Error syncing Jira assignments", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", "Failed to sync: " + e.getMessage()));
        }
    }
    
    @GetMapping("/overdue-tasks")
    public ResponseEntity<List<TaskAssignment>> getOverdueTasks() {
        try {
            List<TaskAssignment> overdueTasks = taskAssignmentRepository.findOverdueTasksByDate(LocalDate.now());
            return ResponseEntity.ok(overdueTasks);
        } catch (Exception e) {
            log.error("Error getting overdue tasks", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/blocked-tasks")
    public ResponseEntity<List<TaskAssignment>> getBlockedTasks() {
        try {
            List<TaskAssignment> blockedTasks = taskAssignmentRepository.findByIsBlockedTrue();
            return ResponseEntity.ok(blockedTasks);
        } catch (Exception e) {
            log.error("Error getting blocked tasks", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/workload-forecast/{assigneeName}")
    public ResponseEntity<Map<String, Object>> getWorkloadForecast(
            @PathVariable String assigneeName,
            @RequestParam(defaultValue = "14") int daysAhead) {
        try {
            LocalDate endDate = LocalDate.now().plusDays(daysAhead);
            List<TaskAssignment> workload = taskAssignmentRepository.findWorkloadByAssigneeAndDate(assigneeName, endDate);
            Integer totalHours = taskAssignmentRepository.getTotalRemainingHoursByAssignee(assigneeName);
            
            return ResponseEntity.ok(Map.of(
                "assigneeName", assigneeName,
                "forecastDays", daysAhead,
                "workload", workload,
                "totalRemainingHours", totalHours != null ? totalHours : 0
            ));
        } catch (Exception e) {
            log.error("Error getting workload forecast for: " + assigneeName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/team-stats")
    public ResponseEntity<Map<String, Object>> getTeamStats() {
        try {
            long totalMembers = teamMemberRepository.count();
            long activeMembers = teamMemberRepository.findByIsActiveTrue().size();
            long totalAssignments = taskAssignmentRepository.count();
            long overdueCount = taskAssignmentRepository.findOverdueTasksByDate(LocalDate.now()).size();
            long blockedCount = taskAssignmentRepository.findByIsBlockedTrue().size();
            
            return ResponseEntity.ok(Map.of(
                "totalMembers", totalMembers,
                "activeMembers", activeMembers,
                "totalAssignments", totalAssignments,
                "overdueTasks", overdueCount,
                "blockedTasks", blockedCount
            ));
        } catch (Exception e) {
            log.error("Error getting team stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
