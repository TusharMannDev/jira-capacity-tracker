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
     */
    public SingleSnapshotSheet generateSingleSnapshotByDateRange(List<String> selectedLabels, LocalDate startDate, LocalDate endDate) {
        log.info("Generating Single Snapshot for labels: {} and date range: {} to {}", selectedLabels, startDate, endDate);
        
        // Get filtered issues more efficiently
        List<JiraIssue> allIssues = getFilteredIssues(selectedLabels, startDate, endDate);
        
        // Group issues by POD/Category
        Map<String, List<PodTaskRow>> podData = generatePodData(allIssues, selectedLabels);
        
        return SingleSnapshotSheet.builder()
                .title("Merchant Onboarding Single View - Date Filtered (" + startDate + " to " + endDate + ")")
                .generatedDate(LocalDate.now())
                .selectedLabels(selectedLabels)
                .dateRange(Map.of("startDate", startDate.toString(), "endDate", endDate.toString()))
                .podData(podData)
                .totalTasks(allIssues.size())
                .build();
    }

    /**
     * Efficiently get filtered issues with mandatory date range for optimal performance
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
     * Get available labels for selection (optimized)
     */
    @Cacheable(value = "availableLabels", unless = "#result.isEmpty()")
    public List<String> getAvailableLabels() {
        log.debug("Getting available labels...");
        
        // Use a Set for faster deduplication
        Set<String> labelSet = new HashSet<>();
        
        // Process in batches to avoid memory issues
        List<JiraIssue> allIssues = jiraIssueRepository.findAll();
        int batchSize = 1000;
        
        for (int i = 0; i < allIssues.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allIssues.size());
            List<JiraIssue> batch = allIssues.subList(i, endIndex);
            
            batch.parallelStream()
                    .filter(issue -> issue.getLabels() != null && !issue.getLabels().isEmpty())
                    .forEach(issue -> {
                        String[] labels = issue.getLabels().split(", ");
                        synchronized (labelSet) {
                            labelSet.addAll(Arrays.asList(labels));
                        }
                    });
        }
        
        List<String> result = new ArrayList<>(labelSet);
        result.sort(String::compareToIgnoreCase);
        
        log.debug("Found {} unique labels", result.size());
        return result;
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
