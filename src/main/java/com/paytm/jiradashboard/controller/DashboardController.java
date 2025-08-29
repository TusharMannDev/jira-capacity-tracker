package com.paytm.jiradashboard.controller;

import com.paytm.jiradashboard.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytm.jiradashboard.model.JiraIssue;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
            Map<String, Object> summary = dashboardService.getDailySummaryRealTime();
            model.addAttribute("summary", summary);
            return "dashboard";
        } catch (Exception e) {
            log.error("Error loading dashboard", e);
            model.addAttribute("error", "Error loading dashboard data");
            return "error";
        }
    }
    
    @GetMapping("/capacity")
    public String capacityDashboard() {
        return "capacity-dashboard";
    }
    
    // ===== REAL-TIME API ENDPOINTS =====
    
    @GetMapping("/api/summary/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSummaryRealTime() {
        try {
            log.info("Fetching real-time summary from Jira...");
            Map<String, Object> summary = dashboardService.getDailySummaryRealTime();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting real-time summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/api/team/{assignee}/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTeamMemberSummaryRealTime(@PathVariable String assignee) {
        try {
            log.info("Fetching real-time team member summary for: {} from Jira", assignee);
            Map<String, Object> summary = dashboardService.getTeamMemberSummaryRealTime(assignee);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting real-time team member summary for: {}", assignee, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/api/project/{projectKey}/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProjectSummaryRealTime(@PathVariable String projectKey) {
        try {
            log.info("Fetching real-time project summary for: {} from Jira", projectKey);
            Map<String, Object> summary = dashboardService.getProjectSummaryRealTime(projectKey);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting real-time project summary for: {}", projectKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/api/capacity/team-summary/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTeamCapacitySummaryRealTime() {
        try {
            log.info("Fetching real-time team capacity summary from Jira...");
            Map<String, Object> summary = dashboardService.getAllEmployeesScrumSummaryRealTime();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting real-time team capacity summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/api/employee/{assignee}/scrum/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEmployeeScrumSummaryRealTime(@PathVariable String assignee) {
        try {
            log.info("Fetching real-time employee scrum summary for: {} from Jira", assignee);
            Map<String, Object> summary = dashboardService.getEmployeeScrumSummaryRealTime(assignee);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting real-time employee scrum summary for: {}", assignee, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // ===== CSV EXPORT ENDPOINT =====
    
    @GetMapping("/api/team-members/csv")
    @ResponseBody
    public ResponseEntity<String> getTeamMembersDataCSV() {
        try {
            log.info("Generating CSV data for team members...");
            
            // Define the team members
            String[] teamMembers = {
                "Srivastava, Praharsh", "Acharya, Ashutosh", "Puri, Prince1", 
                "Agrawal, Oshi", "Agarwal, Amit Kumar", "Sharma, Abhay6", 
                "Singh, Navjot6", "Mittal, Pratham", "Verma, Swati1", 
                "Jaiswal, Anand1", "Yadav Dhruvkant", "Kalsi, Sanya", 
                "Kashif, Syed Mohd", "Verma, Tanish", "Garg, Ankit", 
                "Sharma, siddharth5", "Jain, Mayank5", "Tiiwari, Vineet2", 
                "Jain, Atishay2", "Mann, Tushar", "Kumar, Puneet6"
            };
            
            // Use the proper JQL filter from the service
            String jql = dashboardService.getJiraApiService().getJqlFilter();
            log.info("Fetching issues with JQL: {}", jql);
            List<JiraIssue> allIssues = dashboardService.getJiraApiService().fetchIssuesByJQL(jql);
            
            // Build CSV content
            StringBuilder csv = new StringBuilder();
            
            // CSV Header with time filter info
            csv.append("Team Member,Issue Key,Issue Type,Status,Priority,Summary,Assignee,Reporter,Created Date,Updated Date,Labels,Components,Epic Link,Story Points,Due Date,Resolved Date,Sprint\n");
            csv.append("# Data filtered for last 6 months (updated >= -6m)\n");
            
            // Process each team member
            for (String member : teamMembers) {
                List<JiraIssue> memberIssues = allIssues.stream()
                    .filter(issue -> member.equals(issue.getAssignee()))
                    .collect(Collectors.toList());
                
                if (memberIssues.isEmpty()) {
                    // Add empty row for team members with no issues
                    csv.append(String.format("\"%s\",\"\",\"\",\"\",\"\",\"No Issues Assigned\",\"%s\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n", 
                        member, member));
                } else {
                    // Add rows for each issue
                    for (JiraIssue issue : memberIssues) {
                        csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            member,
                            issue.getIssueKey() != null ? issue.getIssueKey() : "",
                            issue.getIssueType() != null ? issue.getIssueType() : "",
                            issue.getStatus() != null ? issue.getStatus() : "",
                            issue.getPriority() != null ? issue.getPriority() : "",
                            escapeCsvField(issue.getSummary()),
                            issue.getAssignee() != null ? issue.getAssignee() : "",
                            issue.getReporter() != null ? issue.getReporter() : "",
                            issue.getCreated() != null ? issue.getCreated() : "",
                            issue.getUpdated() != null ? issue.getUpdated() : "",
                            issue.getLabels() != null ? issue.getLabels() : "",
                            issue.getComponents() != null ? issue.getComponents() : "",
                            issue.getEpicLink() != null ? issue.getEpicLink() : "",
                            issue.getStoryPoints() != null ? issue.getStoryPoints() : "",
                            issue.getDueDate() != null ? issue.getDueDate() : "",
                            issue.getResolved() != null ? issue.getResolved() : "",
                            issue.getSprint() != null ? issue.getSprint() : ""
                        ));
                    }
                }
            }
            
            // Add summary information at the end
            csv.append("\n# Summary Information\n");
            csv.append("# Total Issues in Project COP: ").append(allIssues.size()).append("\n");
            long totalTeamIssues = allIssues.stream()
                .filter(issue -> {
                    for (String member : teamMembers) {
                        if (member.equals(issue.getAssignee())) return true;
                    }
                    return false;
                })
                .count();
            csv.append("# Total Issues for Team Members: ").append(totalTeamIssues).append("\n");
            csv.append("# Time Filter: Last 6 months (updated >= -6m)\n");
            csv.append("# Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            
            // Set response headers for CSV download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "team-members-capacity-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".csv");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
                
        } catch (Exception e) {
            log.error("Error generating CSV for team members", e);
            return ResponseEntity.internalServerError()
                .body("Error generating CSV: " + e.getMessage());
        }
    }
    
    private String escapeCsvField(String field) {
        if (field == null) return "";
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        String escaped = field.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
    
    // ===== DASHBOARD VIEWS =====
    
    @GetMapping("/team/{assignee}")
    public String teamMemberDashboard(@PathVariable String assignee, Model model) {
        try {
            Map<String, Object> summary = dashboardService.getTeamMemberSummaryRealTime(assignee);
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
            Map<String, Object> summary = dashboardService.getProjectSummaryRealTime(projectKey);
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