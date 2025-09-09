package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.*;
import com.paytm.jiradashboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SingleSnapshotService {

    private final JiraApiService jiraApiService;
    private final JiraIssueRepository jiraIssueRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;

    /**
     * Generate Single Snapshot for all PODs based on selected labels (dates are now mandatory)
     * @deprecated Use generateSingleSnapshotByDateRange instead - dates are now mandatory for performance
     */
    @Deprecated
    public SingleSnapshotSheet generateSingleSnapshot(List<String> selectedLabels) {
        log.warn("Using deprecated method without date range - performance may be poor");
        
        // Default to last 30 days for performance
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        
        return generateSingleSnapshotByDateRange(selectedLabels, startDate, endDate);
    }

    /**
     * Generate Single Snapshot for all PODs based on selected labels and date range
     * Fetches data directly from Jira API (no database dependency)
     */
    public SingleSnapshotSheet generateSingleSnapshotByDateRange(List<String> selectedLabels, LocalDate startDate, LocalDate endDate) {
        log.info("Generating Single Snapshot for labels: {} and date range: {} to {} - FETCHING DIRECTLY FROM JIRA API", selectedLabels, startDate, endDate);
        
        // Fetch fresh data directly from Jira API
        List<JiraIssue> allIssues = fetchIssuesFromApiByDateRange(selectedLabels, startDate, endDate);
        
        // Group issues by POD/Category
        Map<String, List<PodTaskRow>> podData = generatePodData(allIssues, selectedLabels);
        
        return SingleSnapshotSheet.builder()
                .title("Merchant Onboarding Single View - Date Filtered (" + startDate + " to " + endDate + ") - LIVE DATA")
                .generatedDate(LocalDate.now())
                .selectedLabels(selectedLabels)
                .dateRange(Map.of("startDate", startDate.toString(), "endDate", endDate.toString()))
                .podData(podData)
                .totalTasks(allIssues.size())
                .build();
    }

    /**
     * Fetch issues directly from Jira API by date range (bypasses database completely)
     */
    private List<JiraIssue> fetchIssuesFromApiByDateRange(List<String> selectedLabels, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching issues directly from Jira API - Labels: {}, Date range: {} to {}", selectedLabels, startDate, endDate);
        
        // Validate date range (max 6 months for performance)
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > 180) {
            throw new IllegalArgumentException("Date range cannot exceed 6 months for performance reasons");
        }
        
        try {
            // Fetch fresh data from Jira API
            List<JiraIssue> allIssues = jiraApiService.fetchIssuesByDateRange(startDate, endDate);
            log.info("Fetched {} issues from Jira API", allIssues.size());
            
            // If no labels selected, return all issues
            if (selectedLabels.isEmpty()) {
                return allIssues;
            }
            
            // Filter by selected labels
            List<JiraIssue> filteredIssues = allIssues.stream()
                    .filter(issue -> selectedLabels.stream().anyMatch(label -> 
                            issue.getLabels() != null && issue.getLabels().contains(label)))
                    .collect(Collectors.toList());
            
            log.info("Filtered to {} issues matching labels: {}", filteredIssues.size(), selectedLabels);
            return filteredIssues;
            
        } catch (Exception e) {
            log.error("Error fetching issues from Jira API", e);
            throw new RuntimeException("Failed to fetch fresh data from Jira API: " + e.getMessage(), e);
        }
    }

    /**
     * Efficiently get filtered issues with mandatory date range for optimal performance
     * @deprecated Use fetchIssuesFromApiByDateRange for fresh data
     */
    private List<JiraIssue> getFilteredIssues(List<String> selectedLabels, LocalDate startDate, LocalDate endDate) {
        log.debug("Filtering issues - Labels: {}, Date range: {} to {}", selectedLabels, startDate, endDate);
        
        // Date range is now mandatory for performance
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are mandatory for performance reasons");
        }
        
        // Validate date range (max 6 months for performance)
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > 180) {
            throw new IllegalArgumentException("Date range cannot exceed 6 months for performance reasons");
        }
        
        log.info("Processing issues for {} days ({} to {})", daysBetween, startDate, endDate);
        
        // First filter by date range to reduce dataset size significantly
        List<JiraIssue> dateFilteredIssues = jiraIssueRepository.findAll().parallelStream()
                .filter(issue -> {
                    LocalDate createdDate = issue.getCreated().toLocalDate();
                    return !createdDate.isBefore(startDate) && !createdDate.isAfter(endDate);
                })
                .collect(Collectors.toList());
        
        log.debug("Date filtering reduced {} issues to {}", jiraIssueRepository.count(), dateFilteredIssues.size());
        
        // If no labels selected, return all date-filtered issues
        if (selectedLabels.isEmpty()) {
            return dateFilteredIssues;
        }
        
        // Apply label filtering on the already reduced dataset
        List<JiraIssue> finalFiltered = dateFilteredIssues.parallelStream()
                .filter(issue -> selectedLabels.stream().anyMatch(label -> 
                    issue.getLabels() != null && issue.getLabels().contains(label)))
                .collect(Collectors.toList());
        
        log.debug("Label filtering further reduced to {} issues", finalFiltered.size());
        return finalFiltered;
    }

    /**
     * Generate POD data grouped by actual labels
     */
    private Map<String, List<PodTaskRow>> generatePodData(List<JiraIssue> issues, List<String> selectedLabels) {
        Map<String, List<PodTaskRow>> podData = new LinkedHashMap<>();
        
        // If no labels selected, group by all available labels in the issues
        Set<String> labelsToProcess = selectedLabels.isEmpty() ? 
            issues.stream()
                .filter(issue -> issue.getLabels() != null)
                .flatMap(issue -> Arrays.stream(issue.getLabels().split(",")))
                .map(String::trim)
                .collect(Collectors.toSet()) :
            new HashSet<>(selectedLabels);
        
        // Group issues by actual label names
        for (String label : labelsToProcess) {
            List<JiraIssue> labelIssues = issues.stream()
                    .filter(issue -> issue.getLabels() != null && 
                            issue.getLabels().contains(label))
                    .collect(Collectors.toList());
            
            List<PodTaskRow> labelTasks = labelIssues.stream()
                    .map(this::createPodTaskRow)
                    .collect(Collectors.toList());
            
            if (!labelTasks.isEmpty()) {
                podData.put(label, labelTasks);
            }
        }
        
        return podData;
    }

    /**
     * Create a POD task row from Jira issue
     */
    private PodTaskRow createPodTaskRow(JiraIssue issue) {
        return PodTaskRow.builder()
                .taskName(issue.getSummary())
                .jiraKey(issue.getIssueKey())
                .status(mapJiraStatusToPodStatus(issue.getStatus()))
                .assignee(issue.getAssignee())
                .priority(issue.getPriority() != null ? issue.getPriority().toString() : "Medium")
                .labels(issue.getLabels())
                .issueType(issue.getIssueType().toString())
                .created(issue.getCreated().toLocalDate())
                .updated(issue.getUpdated().toLocalDate())
                .build();
    }

    /**
     * Map Jira status to POD status format
     */
    private String mapJiraStatusToPodStatus(IssueStatus jiraStatus) {
        return switch (jiraStatus) {
            case TO_DO -> "Dev";
            case IN_PROGRESS -> "Live";
            case IN_REVIEW -> "QA";
            case DONE -> "Live";
            case BLOCKED -> "UAT";
            default -> "Dev";
        };
    }

    /**
     * Get available labels for selection - fetches directly from Jira API
     */
    public List<String> getAvailableLabels() {
        log.info("Getting available labels directly from Jira API...");
        
        try {
            // Fetch all issues from Jira API (last 6 months for performance)
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(6);
            
            List<JiraIssue> allIssues = jiraApiService.fetchIssuesByDateRange(startDate, endDate);
            log.info("Fetched {} issues from Jira API for label extraction", allIssues.size());
            
            // Use a Set for faster deduplication
            Set<String> labelSet = new HashSet<>();
            
            // Extract labels from all issues
            allIssues.parallelStream()
                    .filter(issue -> issue.getLabels() != null && !issue.getLabels().isEmpty())
                    .forEach(issue -> {
                        String[] labels = issue.getLabels().split(", ");
                        synchronized (labelSet) {
                            labelSet.addAll(Arrays.asList(labels));
                        }
                    });
            
            List<String> result = new ArrayList<>(labelSet);
            result.sort(String::compareToIgnoreCase);
            
            log.info("Found {} unique labels from Jira API", result.size());
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching labels from Jira API", e);
            throw new RuntimeException("Failed to fetch labels from Jira API: " + e.getMessage(), e);
        }
    }

    /**
     * Convert Single Snapshot to Google Sheets format (New Layout)
     */
    public List<List<Object>> convertToSheetsData(SingleSnapshotSheet snapshot) {
        List<List<Object>> sheetsData = new ArrayList<>();
        
        // Process each Label separately
        for (Map.Entry<String, List<PodTaskRow>> labelEntry : snapshot.getPodData().entrySet()) {
            String labelName = labelEntry.getKey();
            List<PodTaskRow> tasks = labelEntry.getValue();
            
            if (tasks.isEmpty()) continue;
            
            // Add Label Name Header (actual label like "OE-OfflinePayments")
            sheetsData.add(Arrays.asList(labelName, "", "", "", "", "", ""));
            
            // Get unique team resources (assignees) who have tasks under this label
            Set<String> uniqueAssignees = tasks.stream()
                    .map(PodTaskRow::getAssignee)
                    .filter(assignee -> assignee != null && !assignee.trim().isEmpty() && !assignee.equals("Unassigned"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            // Debug: Show all assignees before filtering
            Set<String> allAssignees = tasks.stream()
                    .map(PodTaskRow::getAssignee)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            log.info("Label: {} - Total tasks: {}, All assignees (including null/empty): {}, Filtered assignees: {}", 
                    labelName, tasks.size(), allAssignees.size(), uniqueAssignees.size());
            log.debug("All assignees for label {}: {}", labelName, allAssignees);
            log.debug("Filtered assignees for label {}: {}", labelName, uniqueAssignees);
            
            // Add Team Resources Header
            sheetsData.add(Arrays.asList("Team Resources", "Role", "Team Resources", "Role", "Team Resources", "Role", "Team Resources"));
            
            // Add Team Resources Data (people who have tasks under this label)
            List<String> assigneeList = new ArrayList<>(uniqueAssignees);
            for (int i = 0; i < assigneeList.size(); i += 4) {
                List<Object> resourceRow = new ArrayList<>();
                for (int j = 0; j < 4; j++) {
                    if (i + j < assigneeList.size()) {
                        resourceRow.add(assigneeList.get(i + j));
                        resourceRow.add(getAssigneeRole(assigneeList.get(i + j)));
                    } else {
                        resourceRow.add("");
                        resourceRow.add("");
                    }
                }
                sheetsData.add(resourceRow);
            }
            
            // Add Tasks Header
            sheetsData.add(Arrays.asList("Tasks", "Status", "Tasks", "Status", "Tasks", "Status", "Tasks"));
            
            // Add Tasks Data (all tasks under this label)
            for (int i = 0; i < tasks.size(); i += 4) {
                List<Object> taskRow = new ArrayList<>();
                for (int j = 0; j < 4; j++) {
                    if (i + j < tasks.size()) {
                        PodTaskRow task = tasks.get(i + j);
                        taskRow.add(task.getTaskName());
                        taskRow.add(task.getStatus());
                    } else {
                        taskRow.add("");
                        taskRow.add("");
                    }
                }
                sheetsData.add(taskRow);
            }
            
            // Add separator between different labels
            sheetsData.add(Arrays.asList("", "", "", "", "", "", ""));
        }
        
        return sheetsData;
    }
    
    /**
     * Convert Single Snapshot to Horizontal Layout for CSV/Preview (Google Sheets style)
     * Each label is displayed in columns side by side
     */
    public List<List<Object>> convertToHorizontalLayout(SingleSnapshotSheet snapshot) {
        List<List<Object>> horizontalData = new ArrayList<>();
        
        // Get all labels and their data
        List<String> labels = new ArrayList<>(snapshot.getPodData().keySet());
        if (labels.isEmpty()) {
            return horizontalData;
        }
        
        // Calculate max columns needed (each label needs 2 columns: Name and Role/Status)
        int totalColumns = labels.size() * 2;
        
        // Add label headers row
        List<Object> labelHeaderRow = new ArrayList<>();
        for (String label : labels) {
            labelHeaderRow.add(label);
            labelHeaderRow.add(""); // Empty column for spacing
        }
        horizontalData.add(labelHeaderRow);
        
        // Add empty row for spacing
        List<Object> emptyRow = new ArrayList<>(Collections.nCopies(totalColumns, ""));
        horizontalData.add(emptyRow);
        
        // Add Team Resources header row
        List<Object> teamResourcesHeaderRow = new ArrayList<>();
        for (String label : labels) {
            teamResourcesHeaderRow.add("Team Resources");
            teamResourcesHeaderRow.add("Role");
        }
        horizontalData.add(teamResourcesHeaderRow);
        
        // Get team resources for each label
        Map<String, List<String>> labelResources = new HashMap<>();
        Map<String, List<String>> labelResourceRoles = new HashMap<>();
        int maxResourceRows = 0;
        
        for (String label : labels) {
            List<PodTaskRow> tasks = snapshot.getPodData().get(label);
            Set<String> uniqueAssignees = tasks.stream()
                    .map(PodTaskRow::getAssignee)
                    .filter(assignee -> assignee != null && !assignee.trim().isEmpty() && !assignee.equals("Unassigned"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            List<String> resources = new ArrayList<>(uniqueAssignees);
            List<String> roles = resources.stream()
                    .map(this::getAssigneeRole)
                    .collect(Collectors.toList());
            
            labelResources.put(label, resources);
            labelResourceRoles.put(label, roles);
            maxResourceRows = Math.max(maxResourceRows, resources.size());
        }
        
        // Add team resources data rows
        for (int i = 0; i < maxResourceRows; i++) {
            List<Object> resourceRow = new ArrayList<>();
            for (String label : labels) {
                List<String> resources = labelResources.get(label);
                List<String> roles = labelResourceRoles.get(label);
                
                if (i < resources.size()) {
                    resourceRow.add(resources.get(i));
                    resourceRow.add(roles.get(i));
                } else {
                    resourceRow.add("");
                    resourceRow.add("");
                }
            }
            horizontalData.add(resourceRow);
        }
        
        // Add empty row for spacing
        horizontalData.add(new ArrayList<>(Collections.nCopies(totalColumns, "")));
        
        // Add Tasks header row
        List<Object> tasksHeaderRow = new ArrayList<>();
        for (String label : labels) {
            tasksHeaderRow.add("Tasks");
            tasksHeaderRow.add("Status");
        }
        horizontalData.add(tasksHeaderRow);
        
        // Get tasks for each label
        Map<String, List<String>> labelTasks = new HashMap<>();
        Map<String, List<String>> labelTaskStatuses = new HashMap<>();
        int maxTaskRows = 0;
        
        for (String label : labels) {
            List<PodTaskRow> tasks = snapshot.getPodData().get(label);
            List<String> taskNames = tasks.stream()
                    .map(PodTaskRow::getTaskName)
                    .collect(Collectors.toList());
            List<String> taskStatuses = tasks.stream()
                    .map(PodTaskRow::getStatus)
                    .collect(Collectors.toList());
            
            labelTasks.put(label, taskNames);
            labelTaskStatuses.put(label, taskStatuses);
            maxTaskRows = Math.max(maxTaskRows, taskNames.size());
        }
        
        // Add tasks data rows
        for (int i = 0; i < maxTaskRows; i++) {
            List<Object> taskRow = new ArrayList<>();
            for (String label : labels) {
                List<String> tasks = labelTasks.get(label);
                List<String> statuses = labelTaskStatuses.get(label);
                
                if (i < tasks.size()) {
                    taskRow.add(tasks.get(i));
                    taskRow.add(statuses.get(i));
                } else {
                    taskRow.add("");
                    taskRow.add("");
                }
            }
            horizontalData.add(taskRow);
        }
        
        return horizontalData;
    }
    
    /**
     * Convert Single Snapshot to Vertical Layout for CSV/Preview
     * Each label is shown vertically with its team resources and tasks
     * @deprecated Use convertToHorizontalLayout for Google Sheets style layout
     */
    @Deprecated
    public List<List<Object>> convertToVerticalLayout(SingleSnapshotSheet snapshot) {
        List<List<Object>> verticalData = new ArrayList<>();
        
        // Add header
        verticalData.add(Arrays.asList("Label", "Type", "Name", "Details"));
        verticalData.add(Arrays.asList("", "", "", "")); // Empty separator
        
        // Process each Label vertically
        for (Map.Entry<String, List<PodTaskRow>> labelEntry : snapshot.getPodData().entrySet()) {
            String labelName = labelEntry.getKey();
            List<PodTaskRow> tasks = labelEntry.getValue();
            
            if (tasks.isEmpty()) continue;
            
            // Add Label Header
            verticalData.add(Arrays.asList(labelName, "LABEL", labelName, ""));
            
            // Get unique team resources (assignees) who have tasks under this label
            Set<String> uniqueAssignees = tasks.stream()
                    .map(PodTaskRow::getAssignee)
                    .filter(assignee -> assignee != null && !assignee.trim().isEmpty() && !assignee.equals("Unassigned"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            // Add Team Resources Section
            if (!uniqueAssignees.isEmpty()) {
                verticalData.add(Arrays.asList("", "TEAM_RESOURCES", "Team Resources", ""));
                for (String assignee : uniqueAssignees) {
                    String role = getAssigneeRole(assignee);
                    verticalData.add(Arrays.asList("", "RESOURCE", assignee, role));
                }
            }
            
            // Add Tasks Section
            verticalData.add(Arrays.asList("", "TASKS", "Tasks", ""));
            for (PodTaskRow task : tasks) {
                verticalData.add(Arrays.asList("", "TASK", task.getTaskName(), task.getStatus()));
            }
            
            // Add separator between labels
            verticalData.add(Arrays.asList("", "", "", ""));
        }
        
        return verticalData;
    }
    
    /**
     * Get role based on assignee name (simplified mapping)
     */
    private String getAssigneeRole(String assignee) {
        if (assignee == null || assignee.trim().isEmpty()) {
            return "DEV";
        }
        
        // Simple role mapping based on common patterns
        String name = assignee.toLowerCase();
        if (name.contains("tushar") || name.contains("mann")) {
            return "TL";
        } else if (name.contains("syed") || name.contains("kashif")) {
            return "SSE";
        } else if (name.contains("navjot") || name.contains("singh")) {
            return "SSE";
        } else if (name.contains("praharsh")) {
            return "EM";
        } else if (name.contains("prince") || name.contains("puri")) {
            return "TL";
        } else if (name.contains("swati") || name.contains("verma")) {
            return "SSE";
        } else if (name.contains("siddharth") || name.contains("bhardwaj")) {
            return "Intern";
        } else if (name.contains("ashutosh") || name.contains("jaiswal")) {
            return "SSE";
        } else if (name.contains("oshi")) {
            return "SSE";
        } else if (name.contains("tanish")) {
            return "SSE";
        } else {
            return "DEV"; // Default role
        }
    }

    // Data classes for Single Snapshot
    public static class SingleSnapshotSheet {
        public String title;
        public LocalDate generatedDate;
        public List<String> selectedLabels;
        public Map<String, Object> dateRange;
        public Map<String, List<PodTaskRow>> podData;
        public int totalTasks;

        public static SingleSnapshotSheetBuilder builder() {
            return new SingleSnapshotSheetBuilder();
        }

        public static class SingleSnapshotSheetBuilder {
            private SingleSnapshotSheet sheet = new SingleSnapshotSheet();

            public SingleSnapshotSheetBuilder title(String title) {
                sheet.title = title;
                return this;
            }

            public SingleSnapshotSheetBuilder generatedDate(LocalDate generatedDate) {
                sheet.generatedDate = generatedDate;
                return this;
            }

            public SingleSnapshotSheetBuilder selectedLabels(List<String> selectedLabels) {
                sheet.selectedLabels = selectedLabels;
                return this;
            }

            public SingleSnapshotSheetBuilder dateRange(Map<String, Object> dateRange) {
                sheet.dateRange = dateRange;
                return this;
            }

            public SingleSnapshotSheetBuilder podData(Map<String, List<PodTaskRow>> podData) {
                sheet.podData = podData;
                return this;
            }

            public SingleSnapshotSheetBuilder totalTasks(int totalTasks) {
                sheet.totalTasks = totalTasks;
                return this;
            }

            public SingleSnapshotSheet build() {
                return sheet;
            }
        }

        // Getters
        public String getTitle() { return title; }
        public LocalDate getGeneratedDate() { return generatedDate; }
        public List<String> getSelectedLabels() { return selectedLabels; }
        public Map<String, Object> getDateRange() { return dateRange; }
        public Map<String, List<PodTaskRow>> getPodData() { return podData; }
        public int getTotalTasks() { return totalTasks; }
    }

    public static class PodTaskRow {
        public String taskName;
        public String jiraKey;
        public String status;
        public String assignee;
        public String priority;
        public String labels;
        public String issueType;
        public LocalDate created;
        public LocalDate updated;

        public static PodTaskRowBuilder builder() {
            return new PodTaskRowBuilder();
        }

        public static class PodTaskRowBuilder {
            private PodTaskRow row = new PodTaskRow();

            public PodTaskRowBuilder taskName(String taskName) {
                row.taskName = taskName;
                return this;
            }

            public PodTaskRowBuilder jiraKey(String jiraKey) {
                row.jiraKey = jiraKey;
                return this;
            }

            public PodTaskRowBuilder status(String status) {
                row.status = status;
                return this;
            }

            public PodTaskRowBuilder assignee(String assignee) {
                row.assignee = assignee;
                return this;
            }

            public PodTaskRowBuilder priority(String priority) {
                row.priority = priority;
                return this;
            }

            public PodTaskRowBuilder labels(String labels) {
                row.labels = labels;
                return this;
            }

            public PodTaskRowBuilder issueType(String issueType) {
                row.issueType = issueType;
                return this;
            }

            public PodTaskRowBuilder created(LocalDate created) {
                row.created = created;
                return this;
            }

            public PodTaskRowBuilder updated(LocalDate updated) {
                row.updated = updated;
                return this;
            }

            public PodTaskRow build() {
                return row;
            }
        }

        // Getters
        public String getTaskName() { return taskName; }
        public String getJiraKey() { return jiraKey; }
        public String getStatus() { return status; }
        public String getAssignee() { return assignee; }
        public String getPriority() { return priority; }
        public String getLabels() { return labels; }
        public String getIssueType() { return issueType; }
        public LocalDate getCreated() { return created; }
        public LocalDate getUpdated() { return updated; }
    }
}
