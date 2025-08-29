package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.IssueStatus;
import com.paytm.jiradashboard.model.JiraIssue;
import com.paytm.jiradashboard.repository.JiraIssueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardService {
    
    @Autowired
    private JiraApiService jiraApiService;
    
    @Autowired
    private JiraIssueRepository jiraIssueRepository;
    
    @Autowired
    private EmailService emailService;
    
    public void syncIssuesFromJira() {
        log.info("Starting Jira sync...");
        try {
            List<JiraIssue> issues = jiraApiService.fetchIssues();
            log.info("Fetched {} issues from Jira", issues.size());
            
            for (JiraIssue issue : issues) {
                jiraIssueRepository.save(issue);
            }
            
            log.info("Successfully synced {} issues to database", issues.size());
        } catch (Exception e) {
            log.error("Error syncing issues from Jira", e);
        }
    }
    
    public Map<String, Object> getDailySummary() {
        Map<String, Object> summary = new HashMap<>();
        
        // Get current date
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime yesterday = today.minusDays(1);
        
        // Status breakdown
        Map<IssueStatus, Long> statusCounts = getStatusBreakdown();
        summary.put("statusBreakdown", statusCounts);
        
        // Issues by assignee
        Map<String, Long> assigneeCounts = getAssigneeBreakdown();
        summary.put("assigneeBreakdown", assigneeCounts);
        
        // Recently updated issues
        List<JiraIssue> recentlyUpdated = jiraIssueRepository.findRecentlyUpdated(yesterday);
        summary.put("recentlyUpdated", recentlyUpdated);
        
        // Overdue issues
        List<JiraIssue> overdueIssues = jiraIssueRepository.findOverdueIssues(today);
        summary.put("overdueIssues", overdueIssues);
        
        // Issues moved to different statuses
        Map<String, Object> statusChanges = getStatusChanges(yesterday);
        summary.put("statusChanges", statusChanges);
        
        // Project breakdown
        Map<String, Long> projectCounts = getProjectBreakdown();
        summary.put("projectBreakdown", projectCounts);
        
        // Sprint summary
        Map<String, Object> sprintSummary = getSprintSummary();
        summary.put("sprintSummary", sprintSummary);
        
        summary.put("lastSyncTime", LocalDateTime.now());
        summary.put("totalIssues", jiraIssueRepository.count());
        
        return summary;
    }
    
    public Map<String, Object> getTeamMemberSummary(String assignee) {
        Map<String, Object> summary = new HashMap<>();
        
        List<IssueStatus> activeStatuses = Arrays.asList(
            IssueStatus.IN_PROGRESS, 
            IssueStatus.IN_REVIEW, 
            IssueStatus.IN_QA, 
            IssueStatus.QA_PASSED,
            IssueStatus.IN_UAT
        );
        
        List<JiraIssue> activeIssues = jiraIssueRepository.findByAssigneeAndStatusIn(assignee, activeStatuses);
        summary.put("activeIssues", activeIssues);
        
        Map<IssueStatus, Long> statusCounts = activeIssues.stream()
                .collect(Collectors.groupingBy(JiraIssue::getStatus, Collectors.counting()));
        summary.put("statusCounts", statusCounts);
        
        List<JiraIssue> overdueIssues = jiraIssueRepository.findOverdueIssuesByAssignee(assignee, LocalDateTime.now());
        summary.put("overdueIssues", overdueIssues);
        
        return summary;
    }
    
    public Map<String, Object> getProjectSummary(String projectKey) {
        Map<String, Object> summary = new HashMap<>();
        
        List<IssueStatus> activeStatuses = Arrays.asList(
            IssueStatus.IN_PROGRESS, 
            IssueStatus.IN_REVIEW, 
            IssueStatus.IN_QA, 
            IssueStatus.QA_PASSED,
            IssueStatus.IN_UAT
        );
        
        List<JiraIssue> projectIssues = jiraIssueRepository.findByProjectKeyAndStatusIn(projectKey, activeStatuses);
        summary.put("projectIssues", projectIssues);
        
        Map<IssueStatus, Long> statusCounts = projectIssues.stream()
                .collect(Collectors.groupingBy(JiraIssue::getStatus, Collectors.counting()));
        summary.put("statusCounts", statusCounts);
        
        Map<String, Long> assigneeCounts = projectIssues.stream()
                .collect(Collectors.groupingBy(JiraIssue::getAssignee, Collectors.counting()));
        summary.put("assigneeCounts", assigneeCounts);
        
        return summary;
    }
    
    private Map<IssueStatus, Long> getStatusBreakdown() {
        return jiraIssueRepository.findAll().stream()
                .collect(Collectors.groupingBy(JiraIssue::getStatus, Collectors.counting()));
    }
    
    private Map<String, Long> getAssigneeBreakdown() {
        return jiraIssueRepository.findAll().stream()
                .collect(Collectors.groupingBy(JiraIssue::getAssignee, Collectors.counting()));
    }
    
    private Map<String, Long> getProjectBreakdown() {
        return jiraIssueRepository.findAll().stream()
                .collect(Collectors.groupingBy(JiraIssue::getProjectKey, Collectors.counting()));
    }
    
    private Map<String, Object> getStatusChanges(LocalDateTime since) {
        Map<String, Object> changes = new HashMap<>();
        
        // This would require tracking status changes over time
        // For now, return empty map
        changes.put("movedToInProgress", 0);
        changes.put("movedToQA", 0);
        changes.put("movedToUAT", 0);
        changes.put("completed", 0);
        
        return changes;
    }
    
    private Map<String, Object> getSprintSummary() {
        Map<String, Object> sprintSummary = new HashMap<>();
        
        // Group issues by sprint
        Map<String, List<JiraIssue>> sprintIssues = jiraIssueRepository.findAll().stream()
                .filter(issue -> issue.getSprint() != null && !issue.getSprint().isEmpty())
                .collect(Collectors.groupingBy(JiraIssue::getSprint));
        
        sprintSummary.put("sprintIssues", sprintIssues);
        
        // Calculate sprint metrics
        Map<String, Map<String, Object>> sprintMetrics = new HashMap<>();
        for (Map.Entry<String, List<JiraIssue>> entry : sprintIssues.entrySet()) {
            String sprint = entry.getKey();
            List<JiraIssue> issues = entry.getValue();
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("totalIssues", issues.size());
            metrics.put("completedIssues", issues.stream()
                    .filter(issue -> issue.getStatus() == IssueStatus.DONE || issue.getStatus() == IssueStatus.CLOSED)
                    .count());
            metrics.put("inProgressIssues", issues.stream()
                    .filter(issue -> issue.getStatus() == IssueStatus.IN_PROGRESS)
                    .count());
            metrics.put("qaIssues", issues.stream()
                    .filter(issue -> issue.getStatus() == IssueStatus.IN_QA || issue.getStatus() == IssueStatus.QA_PASSED)
                    .count());
            
            sprintMetrics.put(sprint, metrics);
        }
        
        sprintSummary.put("sprintMetrics", sprintMetrics);
        return sprintSummary;
    }
    
    public Map<String, Object> getEmployeeScrumSummary(String assignee) {
        Map<String, Object> summary = new HashMap<>();
        
        // Get all issues for this employee
        List<JiraIssue> allIssues = jiraIssueRepository.findByAssignee(assignee);
        
        // Group by status
        Map<IssueStatus, List<JiraIssue>> issuesByStatus = allIssues.stream()
                .collect(Collectors.groupingBy(JiraIssue::getStatus));
        
        // What I worked on yesterday (issues updated in last 24 hours)
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<JiraIssue> recentlyWorkedOn = allIssues.stream()
                .filter(issue -> issue.getUpdated() != null && issue.getUpdated().isAfter(yesterday))
                .sorted(Comparator.comparing(JiraIssue::getUpdated).reversed())
                .collect(Collectors.toList());
        
        // What I'm working on today (in progress issues)
        List<JiraIssue> workingOnToday = issuesByStatus.getOrDefault(IssueStatus.IN_PROGRESS, new ArrayList<>());
        
        // What I plan to work on next (to do issues)
        List<JiraIssue> plannedForNext = issuesByStatus.getOrDefault(IssueStatus.TO_DO, new ArrayList<>());
        
        // Blockers/Issues
        List<JiraIssue> blockers = allIssues.stream()
                .filter(issue -> issue.getStatus() == IssueStatus.BLOCKED || issue.getStatus() == IssueStatus.ON_HOLD)
                .collect(Collectors.toList());
        
        // Completed today (moved to done in last 24 hours)
        List<JiraIssue> completedToday = allIssues.stream()
                .filter(issue -> (issue.getStatus() == IssueStatus.DONE || issue.getStatus() == IssueStatus.CLOSED) &&
                               issue.getUpdated() != null && issue.getUpdated().isAfter(yesterday))
                .collect(Collectors.toList());
        
        // Overdue issues
        List<JiraIssue> overdueIssues = jiraIssueRepository.findOverdueIssuesByAssignee(assignee, LocalDateTime.now());
        
        // Story points summary
        int totalStoryPoints = allIssues.stream()
                .filter(issue -> issue.getStoryPoints() != null)
                .mapToInt(JiraIssue::getStoryPoints)
                .sum();
        
        int completedStoryPoints = completedToday.stream()
                .filter(issue -> issue.getStoryPoints() != null)
                .mapToInt(JiraIssue::getStoryPoints)
                .sum();
        
        summary.put("assignee", assignee);
        summary.put("allIssues", allIssues);
        summary.put("issuesByStatus", issuesByStatus);
        summary.put("recentlyWorkedOn", recentlyWorkedOn);
        summary.put("workingOnToday", workingOnToday);
        summary.put("plannedForNext", plannedForNext);
        summary.put("blockers", blockers);
        summary.put("completedToday", completedToday);
        summary.put("overdueIssues", overdueIssues);
        summary.put("totalStoryPoints", totalStoryPoints);
        summary.put("completedStoryPoints", completedStoryPoints);
        summary.put("totalIssues", allIssues.size());
        summary.put("completedIssues", completedToday.size());
        
        return summary;
    }
    
    public Map<String, Object> getAllEmployeesScrumSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        // Get all unique assignees
        List<String> allAssignees = jiraIssueRepository.findAll().stream()
                .map(JiraIssue::getAssignee)
                .filter(assignee -> !assignee.equals("Unassigned"))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        // Get summary for each employee
        Map<String, Map<String, Object>> employeeSummaries = new HashMap<>();
        for (String assignee : allAssignees) {
            employeeSummaries.put(assignee, getEmployeeScrumSummary(assignee));
        }
        
        summary.put("employees", employeeSummaries);
        summary.put("totalEmployees", allAssignees.size());
        summary.put("employeeList", allAssignees);
        
        return summary;
    }
    
    public JiraIssueRepository getJiraIssueRepository() {
        return jiraIssueRepository;
    }

    @Scheduled(cron = "${app.daily-summary.cron}")
    public void sendDailySummary() {
        log.info("Sending daily summary...");
        try {
            Map<String, Object> summary = getDailySummary();
            emailService.sendDailySummary(summary);
            log.info("Daily summary sent successfully");
        } catch (Exception e) {
            log.error("Error sending daily summary", e);
        }
    }
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void autoSyncIssues() {
        log.info("Auto-syncing issues from Jira...");
        syncIssuesFromJira();
    }
} 