package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.IssueStatus;
import com.paytm.jiradashboard.model.JiraIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardService {
    
    @Autowired
    private JiraApiService jiraApiService;
    
    // ===== REAL-TIME METHODS (Direct from Jira API) =====
    
    /**
     * Get real-time daily summary directly from Jira
     */
    public Map<String, Object> getDailySummaryRealTime() {
        log.info("=== STARTING REAL-TIME DAILY SUMMARY ===");
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Use the injected JQL filter instead of System.getenv()
            String currentJqlFilter = jiraApiService.getJqlFilter();
            log.info("Using injected JQL filter for real-time: '{}'", currentJqlFilter);
            
            // Test direct Jira API call first
            log.info("Testing direct Jira API call with JQL: {}", currentJqlFilter);
            List<JiraIssue> testIssues = jiraApiService.fetchIssuesByJQL(currentJqlFilter);
            log.info("Direct API call returned {} issues", testIssues.size());
            
            if (!testIssues.isEmpty()) {
                log.info("Sample issue from direct call: {}", testIssues.get(0).getIssueKey());
            }
            
            // Now proceed with the real-time summary
            List<JiraIssue> issues = testIssues; // Use the test results
            log.info("Using {} issues for real-time summary", issues.size());
            
            // Get current date
            LocalDateTime today = LocalDateTime.now();
            LocalDateTime yesterday = today.minusDays(1);
            
            // Status breakdown
            Map<IssueStatus, Long> statusCounts = issues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getStatus, Collectors.counting()));
            summary.put("statusBreakdown", statusCounts);
            log.info("Status breakdown: {}", statusCounts);
            
            // Issues by assignee
            Map<String, Long> assigneeCounts = issues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getAssignee, Collectors.counting()));
            summary.put("assigneeBreakdown", assigneeCounts);
            log.info("Assignee breakdown: {}", assigneeCounts);
            
            // Recently updated issues
            List<JiraIssue> recentlyUpdated = issues.stream()
                    .filter(issue -> issue.getUpdated() != null && issue.getUpdated().isAfter(yesterday))
                    .sorted(Comparator.comparing(JiraIssue::getUpdated).reversed())
                    .collect(Collectors.toList());
            summary.put("recentlyUpdated", recentlyUpdated);
            log.info("Recently updated issues: {}", recentlyUpdated.size());
            
            // Overdue issues
            List<JiraIssue> overdueIssues = issues.stream()
                    .filter(issue -> issue.getDueDate() != null && issue.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
            summary.put("overdueIssues", overdueIssues);
            log.info("Overdue issues: {}", overdueIssues.size());
            
            // Project breakdown
            Map<String, Long> projectCounts = issues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getProjectKey, Collectors.counting()));
            summary.put("projectBreakdown", projectCounts);
            log.info("Project breakdown: {}", projectCounts);
            
            // Sprint summary
            Map<String, Object> sprintSummary = getSprintSummaryRealTime(issues);
            summary.put("sprintSummary", sprintSummary);
            
            summary.put("lastSyncTime", LocalDateTime.now());
            summary.put("totalIssues", issues.size());
            summary.put("dataSource", "Real-time from Jira");
            summary.put("jqlFilter", currentJqlFilter);
            
            log.info("=== REAL-TIME DAILY SUMMARY COMPLETED ===");
            return summary;
            
        } catch (Exception e) {
            log.error("Error in real-time daily summary", e);
            throw new RuntimeException("Failed to get real-time daily summary", e);
        }
    }
    
    /**
     * Get real-time team member summary directly from Jira
     */
    public Map<String, Object> getTeamMemberSummaryRealTime(String assignee) {
        log.info("=== STARTING REAL-TIME TEAM MEMBER SUMMARY FOR: {} ===", assignee);
        Map<String, Object> summary = new HashMap<>();
        
        try {
            String currentJqlFilter = jiraApiService.getJqlFilter();
            log.info("Using injected JQL filter for real-time team member: '{}'", currentJqlFilter);
            
            List<JiraIssue> allIssues = jiraApiService.fetchIssuesByJQL(currentJqlFilter);
            log.info("Fetched {} total issues from Jira API for team member summary", allIssues.size());
            
            // Filter issues for the specific assignee
            List<JiraIssue> assigneeIssues = allIssues.stream()
                    .filter(issue -> assignee.equals(issue.getAssignee()))
                    .collect(Collectors.toList());
            
            log.info("Found {} issues for assignee: {}", assigneeIssues.size(), assignee);
            
            // Status breakdown for this assignee
            Map<IssueStatus, Long> statusCounts = assigneeIssues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getStatus, Collectors.counting()));
            summary.put("statusBreakdown", statusCounts);
            
            // Project breakdown for this assignee
            Map<String, Long> projectCounts = assigneeIssues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getProjectKey, Collectors.counting()));
            summary.put("projectBreakdown", projectCounts);
            
            // Recently updated issues for this assignee
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            List<JiraIssue> recentlyUpdated = assigneeIssues.stream()
                    .filter(issue -> issue.getUpdated() != null && issue.getUpdated().isAfter(yesterday))
                    .sorted(Comparator.comparing(JiraIssue::getUpdated).reversed())
                    .collect(Collectors.toList());
            summary.put("recentlyUpdated", recentlyUpdated);
            
            // Overdue issues for this assignee
            LocalDateTime today = LocalDateTime.now();
            List<JiraIssue> overdueIssues = assigneeIssues.stream()
                    .filter(issue -> issue.getDueDate() != null && issue.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
            summary.put("overdueIssues", overdueIssues);
            
            summary.put("assignee", assignee);
            summary.put("totalIssues", assigneeIssues.size());
            summary.put("lastSyncTime", LocalDateTime.now());
            summary.put("dataSource", "Real-time from Jira");
            summary.put("jqlFilter", currentJqlFilter);
            
            log.info("=== REAL-TIME TEAM MEMBER SUMMARY COMPLETED FOR: {} ===", assignee);
            return summary;
            
        } catch (Exception e) {
            log.error("Error in real-time team member summary for: {}", assignee, e);
            throw new RuntimeException("Failed to get real-time team member summary for: " + assignee, e);
        }
    }
    
    /**
     * Get real-time project summary directly from Jira
     */
    public Map<String, Object> getProjectSummaryRealTime(String projectKey) {
        log.info("=== STARTING REAL-TIME PROJECT SUMMARY FOR: {} ===", projectKey);
        Map<String, Object> summary = new HashMap<>();
        
        try {
            String currentJqlFilter = jiraApiService.getJqlFilter();
            log.info("Using injected JQL filter for real-time project: '{}'", currentJqlFilter);
            
            List<JiraIssue> allIssues = jiraApiService.fetchIssuesByJQL(currentJqlFilter);
            log.info("Fetched {} total issues from Jira API for project summary", allIssues.size());
            
            // Filter issues for the specific project
            List<JiraIssue> projectIssues = allIssues.stream()
                    .filter(issue -> projectKey.equals(issue.getProjectKey()))
                    .collect(Collectors.toList());
            
            log.info("Found {} issues for project: {}", projectIssues.size(), projectKey);
            
            // Status breakdown for this project
            Map<IssueStatus, Long> statusCounts = projectIssues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getStatus, Collectors.counting()));
            summary.put("statusBreakdown", statusCounts);
            
            // Assignee breakdown for this project
            Map<String, Long> assigneeCounts = projectIssues.stream()
                    .collect(Collectors.groupingBy(JiraIssue::getAssignee, Collectors.counting()));
            summary.put("assigneeBreakdown", assigneeCounts);
            
            // Recently updated issues for this project
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            List<JiraIssue> recentlyUpdated = projectIssues.stream()
                    .filter(issue -> issue.getUpdated() != null && issue.getUpdated().isAfter(yesterday))
                    .sorted(Comparator.comparing(JiraIssue::getUpdated).reversed())
                    .collect(Collectors.toList());
            summary.put("recentlyUpdated", recentlyUpdated);
            
            // Overdue issues for this project
            LocalDateTime today = LocalDateTime.now();
            List<JiraIssue> overdueIssues = projectIssues.stream()
                    .filter(issue -> issue.getDueDate() != null && issue.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
            summary.put("overdueIssues", overdueIssues);
            
            summary.put("projectKey", projectKey);
            summary.put("totalIssues", projectIssues.size());
            summary.put("lastSyncTime", LocalDateTime.now());
            summary.put("dataSource", "Real-time from Jira");
            summary.put("jqlFilter", currentJqlFilter);
            
            log.info("=== REAL-TIME PROJECT SUMMARY COMPLETED FOR: {} ===", projectKey);
            return summary;
            
        } catch (Exception e) {
            log.error("Error in real-time project summary for: {}", projectKey, e);
            throw new RuntimeException("Failed to get real-time project summary for: " + projectKey, e);
        }
    }
    
    /**
     * Get real-time sprint summary directly from Jira
     */
    private Map<String, Object> getSprintSummaryRealTime(List<JiraIssue> issues) {
        Map<String, Object> sprintSummary = new HashMap<>();
        
        try {
            // Group issues by sprint
            Map<String, List<JiraIssue>> sprintIssues = issues.stream()
                    .filter(issue -> issue.getSprint() != null && !issue.getSprint().isEmpty())
                    .collect(Collectors.groupingBy(JiraIssue::getSprint));
            
            // Calculate sprint metrics
            Map<String, Object> sprintMetrics = new HashMap<>();
            for (Map.Entry<String, List<JiraIssue>> entry : sprintIssues.entrySet()) {
                String sprintName = entry.getKey();
                List<JiraIssue> sprintIssuesList = entry.getValue();
                
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("totalIssues", sprintIssuesList.size());
                metrics.put("toDoIssues", sprintIssuesList.stream()
                        .filter(issue -> IssueStatus.TO_DO.equals(issue.getStatus()))
                        .count());
                metrics.put("inProgressIssues", sprintIssuesList.stream()
                        .filter(issue -> IssueStatus.IN_PROGRESS.equals(issue.getStatus()))
                        .count());
                metrics.put("doneIssues", sprintIssuesList.stream()
                        .filter(issue -> IssueStatus.DONE.equals(issue.getStatus()))
                        .count());
                
                sprintMetrics.put(sprintName, metrics);
            }
            
            sprintSummary.put("sprintIssues", sprintIssues);
            sprintSummary.put("sprintMetrics", sprintMetrics);
            
        } catch (Exception e) {
            log.error("Error calculating sprint summary", e);
            sprintSummary.put("error", "Failed to calculate sprint summary: " + e.getMessage());
        }
        
        return sprintSummary;
    }
    
    /**
     * Get real-time employee scrum summary directly from Jira
     */
    public Map<String, Object> getEmployeeScrumSummaryRealTime(String assignee) {
        log.info("=== STARTING REAL-TIME EMPLOYEE SCRUM SUMMARY FOR: {} ===", assignee);
        Map<String, Object> summary = new HashMap<>();
        
        try {
            String currentJqlFilter = jiraApiService.getJqlFilter();
            log.info("Using injected JQL filter for real-time employee scrum: '{}'", currentJqlFilter);
            
            List<JiraIssue> allIssues = jiraApiService.fetchIssuesByJQL(currentJqlFilter);
            log.info("Fetched {} total issues from Jira API for employee scrum summary", allIssues.size());
            
            // Filter issues for the specific assignee
            List<JiraIssue> assigneeIssues = allIssues.stream()
                    .filter(issue -> assignee.equals(issue.getAssignee()))
                    .collect(Collectors.toList());
            
            log.info("Found {} issues for assignee: {}", assigneeIssues.size(), assignee);
            
            // Calculate scrum metrics
            long totalIssues = assigneeIssues.size();
            long toDoIssues = assigneeIssues.stream()
                    .filter(issue -> IssueStatus.TO_DO.equals(issue.getStatus()))
                    .count();
            long inProgressIssues = assigneeIssues.stream()
                    .filter(issue -> IssueStatus.IN_PROGRESS.equals(issue.getStatus()))
                    .count();
            long doneIssues = assigneeIssues.stream()
                    .filter(issue -> IssueStatus.DONE.equals(issue.getStatus()))
                    .count();
            long blockedIssues = assigneeIssues.stream()
                    .filter(issue -> IssueStatus.BLOCKED.equals(issue.getStatus()))
                    .count();
            
            // Calculate completion percentage
            double completionPercentage = totalIssues > 0 ? (double) doneIssues / totalIssues * 100 : 0;
            
            summary.put("assignee", assignee);
            summary.put("totalIssues", totalIssues);
            summary.put("toDoIssues", toDoIssues);
            summary.put("inProgressIssues", inProgressIssues);
            summary.put("doneIssues", doneIssues);
            summary.put("blockedIssues", blockedIssues);
            summary.put("completionPercentage", Math.round(completionPercentage * 100.0) / 100.0);
            summary.put("lastSyncTime", LocalDateTime.now());
            summary.put("dataSource", "Real-time from Jira");
            summary.put("jqlFilter", currentJqlFilter);
            
            log.info("=== REAL-TIME EMPLOYEE SCRUM SUMMARY COMPLETED FOR: {} ===", assignee);
            return summary;
            
        } catch (Exception e) {
            log.error("Error in real-time employee scrum summary for: {}", assignee, e);
            throw new RuntimeException("Failed to get real-time employee scrum summary for: " + assignee, e);
        }
    }
    
    /**
     * Get real-time all employees scrum summary directly from Jira
     */
    public Map<String, Object> getAllEmployeesScrumSummaryRealTime() {
        log.info("=== STARTING REAL-TIME ALL EMPLOYEES SCRUM SUMMARY ===");
        Map<String, Object> summary = new HashMap<>();
        
        try {
            String currentJqlFilter = jiraApiService.getJqlFilter();
            log.info("Using injected JQL filter for real-time all employees scrum: '{}'", currentJqlFilter);
            
            List<JiraIssue> allIssues = jiraApiService.fetchIssuesByJQL(currentJqlFilter);
            log.info("Fetched {} total issues from Jira API for all employees scrum summary", allIssues.size());
            
            // Get unique assignees
            Set<String> assignees = allIssues.stream()
                    .map(JiraIssue::getAssignee)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            log.info("Found {} unique assignees", assignees.size());
            
            // Calculate scrum summary for each assignee
            Map<String, Object> employeeSummaries = new HashMap<>();
            for (String assignee : assignees) {
                Map<String, Object> employeeSummary = getEmployeeScrumSummaryRealTime(assignee);
                employeeSummaries.put(assignee, employeeSummary);
            }
            
            summary.put("employeeSummaries", employeeSummaries);
            summary.put("totalEmployees", assignees.size());
            summary.put("totalIssues", allIssues.size());
            summary.put("lastSyncTime", LocalDateTime.now());
            summary.put("dataSource", "Real-time from Jira");
            summary.put("jqlFilter", currentJqlFilter);
            
            log.info("=== REAL-TIME ALL EMPLOYEES SCRUM SUMMARY COMPLETED ===");
            return summary;
            
        } catch (Exception e) {
            log.error("Error in real-time all employees scrum summary", e);
            throw new RuntimeException("Failed to get real-time all employees scrum summary", e);
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Get JiraApiService instance
     */
    public JiraApiService getJiraApiService() {
        return jiraApiService;
    }
} 