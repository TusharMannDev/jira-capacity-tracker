package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.*;
import com.paytm.jiradashboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsExportService {

    private final JiraIssueRepository jiraIssueRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;

    public CapacityTrackingSheet generateCapacityTrackingSheet() {
        log.info("Generating capacity tracking sheet from Jira data...");
        
        // Get all active issues and team members
        List<JiraIssue> activeIssues = jiraIssueRepository.findByStatusIn(
                Arrays.asList(IssueStatus.TO_DO, IssueStatus.IN_PROGRESS, IssueStatus.IN_REVIEW));
        List<TeamMember> teamMembers = teamMemberRepository.findByIsActiveTrue();
        List<TaskAssignment> assignments = taskAssignmentRepository.findAll();
        
        // Generate timeline weeks
        List<WeekColumn> timelineWeeks = generateTimelineWeeks(12); // Next 12 weeks
        
        // Group by POD/Team and Lead
        Map<String, List<CapacityRow>> groupedData = generateCapacityRows(activeIssues, assignments, teamMembers, timelineWeeks);
        
        return CapacityTrackingSheet.builder()
                .title("OE_Payments_Task_Tracker - Ongoing")
                .generatedDate(LocalDate.now())
                .timelineWeeks(timelineWeeks)
                .teamData(groupedData)
                .build();
    }

    private List<WeekColumn> generateTimelineWeeks(int weekCount) {
        List<WeekColumn> weeks = new ArrayList<>();
        LocalDate startDate = LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1); // Start of current week
        
        for (int i = 0; i < weekCount; i++) {
            LocalDate weekStart = startDate.plusWeeks(i);
            WeekColumn week = WeekColumn.builder()
                    .monthName(weekStart.format(DateTimeFormatter.ofPattern("MMMM")))
                    .weekNumber("W" + weekStart.format(DateTimeFormatter.ofPattern("w")))
                    .startDate(weekStart)
                    .endDate(weekStart.plusDays(6))
                    .build();
            weeks.add(week);
        }
        
        return weeks;
    }

    private Map<String, List<CapacityRow>> generateCapacityRows(
            List<JiraIssue> issues, 
            List<TaskAssignment> assignments, 
            List<TeamMember> teamMembers,
            List<WeekColumn> timelineWeeks) {
        
        Map<String, List<CapacityRow>> teamData = new LinkedHashMap<>();
        
        // Group team members by team
        Map<String, List<TeamMember>> membersByTeam = teamMembers.stream()
                .collect(Collectors.groupingBy(tm -> tm.getTeam() != null ? tm.getTeam() : "Unassigned"));
        
        for (Map.Entry<String, List<TeamMember>> teamEntry : membersByTeam.entrySet()) {
            String teamName = teamEntry.getKey();
            List<TeamMember> teamMembersList = teamEntry.getValue();
            
            List<CapacityRow> teamRows = new ArrayList<>();
            
            for (TeamMember member : teamMembersList) {
                // Get tasks for this team member
                List<JiraIssue> memberIssues = issues.stream()
                        .filter(issue -> issue.getAssignee().equals(member.getName()))
                        .collect(Collectors.toList());
                
                List<TaskAssignment> memberAssignments = assignments.stream()
                        .filter(assignment -> assignment.getAssigneeName().equals(member.getName()))
                        .collect(Collectors.toList());
                
                // Create rows for each task
                for (JiraIssue issue : memberIssues) {
                    TaskAssignment assignment = memberAssignments.stream()
                            .filter(a -> a.getIssueKey().equals(issue.getIssueKey()))
                            .findFirst()
                            .orElse(null);
                    
                    CapacityRow row = createCapacityRow(issue, assignment, member, timelineWeeks);
                    teamRows.add(row);
                }
                
                // If member has no issues, create a placeholder row
                if (memberIssues.isEmpty()) {
                    CapacityRow emptyRow = CapacityRow.builder()
                            .pod(teamName)
                            .lead(member.getName())
                            .taskType("Available")
                            .currentTask("Available for new assignments")
                            .resource(member.getName())
                            .manDays(0)
                            .startDate(null)
                            .endDate(null)
                            .qaDrop(null)
                            .tasksInPipeline("")
                            .weeklyStatus(createAvailableWeeklyStatus(timelineWeeks))
                            .build();
                    teamRows.add(emptyRow);
                }
            }
            
            teamData.put(teamName, teamRows);
        }
        
        return teamData;
    }

    private CapacityRow createCapacityRow(JiraIssue issue, TaskAssignment assignment, TeamMember member, List<WeekColumn> timelineWeeks) {
        // Calculate task duration
        LocalDate startDate = assignment != null && assignment.getStartDate() != null ? 
                assignment.getStartDate() : LocalDate.now();
        LocalDate endDate = assignment != null && assignment.getEstimatedCompletionDate() != null ? 
                assignment.getEstimatedCompletionDate() : startDate.plusDays(5);
        
        int manDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        
        // Generate weekly status
        Map<String, TaskStatus> weeklyStatus = generateWeeklyStatus(startDate, endDate, issue.getStatus(), timelineWeeks);
        
        return CapacityRow.builder()
                .pod(member.getTeam() != null ? member.getTeam() : "Unassigned")
                .lead(member.getName())
                .taskType(mapIssueTypeToTaskType(issue.getIssueType()))
                .currentTask(issue.getSummary())
                .resource(issue.getAssignee())
                .manDays(manDays)
                .startDate(startDate)
                .endDate(endDate)
                .qaDrop(endDate) // Assume QA drop same as end date
                .tasksInPipeline(generateTasksPipeline(member.getName()))
                .weeklyStatus(weeklyStatus)
                .build();
    }

    private Map<String, TaskStatus> generateWeeklyStatus(LocalDate taskStart, LocalDate taskEnd, IssueStatus issueStatus, List<WeekColumn> timelineWeeks) {
        Map<String, TaskStatus> weeklyStatus = new HashMap<>();
        
        for (WeekColumn week : timelineWeeks) {
            String weekKey = week.getMonthName() + "_" + week.getWeekNumber();
            
            // Check if task overlaps with this week
            boolean overlaps = !(taskEnd.isBefore(week.getStartDate()) || taskStart.isAfter(week.getEndDate()));
            
            if (overlaps) {
                // Determine status based on task progress and current date
                if (week.getEndDate().isBefore(LocalDate.now())) {
                    // Past weeks - assume completed if task is done
                    weeklyStatus.put(weekKey, issueStatus == IssueStatus.DONE ? TaskStatus.COMPLETED : TaskStatus.IN_PROGRESS);
                } else if (week.getStartDate().isAfter(LocalDate.now().plusDays(7))) {
                    // Future weeks - planned
                    weeklyStatus.put(weekKey, TaskStatus.PLANNED);
                } else {
                    // Current/near future weeks - in progress
                    weeklyStatus.put(weekKey, mapIssueStatusToTaskStatus(issueStatus));
                }
            } else {
                weeklyStatus.put(weekKey, TaskStatus.NOT_APPLICABLE);
            }
        }
        
        return weeklyStatus;
    }

    private Map<String, TaskStatus> createAvailableWeeklyStatus(List<WeekColumn> timelineWeeks) {
        Map<String, TaskStatus> weeklyStatus = new HashMap<>();
        for (WeekColumn week : timelineWeeks) {
            String weekKey = week.getMonthName() + "_" + week.getWeekNumber();
            weeklyStatus.put(weekKey, TaskStatus.AVAILABLE);
        }
        return weeklyStatus;
    }

    private String mapIssueTypeToTaskType(IssueType issueType) {
        return switch (issueType) {
            case STORY -> "Business";
            case TASK -> "Tech";
            case BUG -> "Support";
            case EPIC -> "Business";
            case SUBTASK -> "Tech";
            case FEATURE -> "Business";
            case IMPROVEMENT -> "Tech";
            case DOCUMENTATION -> "Support";
            default -> "Tech";
        };
    }

    private TaskStatus mapIssueStatusToTaskStatus(IssueStatus issueStatus) {
        return switch (issueStatus) {
            case TO_DO -> TaskStatus.PLANNED;
            case IN_PROGRESS -> TaskStatus.IN_PROGRESS;
            case IN_REVIEW -> TaskStatus.IN_PROGRESS;
            case DONE -> TaskStatus.COMPLETED;
            case BLOCKED -> TaskStatus.BLOCKED;
            default -> TaskStatus.NOT_APPLICABLE;
        };
    }

    private String generateTasksPipeline(String memberName) {
        // Get upcoming tasks for this member
        List<TaskAssignment> upcomingTasks = taskAssignmentRepository.findActiveTasksByAssignee(memberName)
                .stream()
                .filter(task -> task.getStartDate() != null && task.getStartDate().isAfter(LocalDate.now()))
                .limit(3)
                .collect(Collectors.toList());
        
        if (upcomingTasks.isEmpty()) {
            return "";
        }
        
        return upcomingTasks.stream()
                .map(task -> task.getIssueKey())
                .collect(Collectors.joining(", "));
    }

    // Method to convert to Google Sheets format
    public List<List<Object>> convertToSheetsData(CapacityTrackingSheet sheet) {
        List<List<Object>> sheetsData = new ArrayList<>();
        
        // Add header row
        List<Object> headerRow = new ArrayList<>();
        headerRow.addAll(Arrays.asList("POD", "Lead", "Task Type", "Current Tasks (In Dev)", "Resource"));
        
        // Add timeline columns
        for (WeekColumn week : sheet.getTimelineWeeks()) {
            headerRow.add(week.getMonthName());
            headerRow.add(week.getWeekNumber());
        }
        
        headerRow.addAll(Arrays.asList("M Days", "Start Date", "End Date", "QA Drop", "Tasks in pipeline"));
        sheetsData.add(headerRow);
        
        // Add data rows
        for (Map.Entry<String, List<CapacityRow>> teamEntry : sheet.getTeamData().entrySet()) {
            for (CapacityRow row : teamEntry.getValue()) {
                List<Object> dataRow = new ArrayList<>();
                dataRow.add(row.getPod());
                dataRow.add(row.getLead());
                dataRow.add(row.getTaskType());
                dataRow.add(row.getCurrentTask());
                dataRow.add(row.getResource());
                
                // Add weekly status
                for (WeekColumn week : sheet.getTimelineWeeks()) {
                    String weekKey = week.getMonthName() + "_" + week.getWeekNumber();
                    TaskStatus status = row.getWeeklyStatus().get(weekKey);
                    String statusDisplay = convertTaskStatusToDisplay(status);
                    dataRow.add(statusDisplay);
                    dataRow.add(""); // Empty cell for week number column
                }
                
                dataRow.add(row.getManDays());
                dataRow.add(row.getStartDate() != null ? row.getStartDate().toString() : "");
                dataRow.add(row.getEndDate() != null ? row.getEndDate().toString() : "");
                dataRow.add(row.getQaDrop() != null ? row.getQaDrop().toString() : "");
                dataRow.add(row.getTasksInPipeline());
                
                sheetsData.add(dataRow);
            }
        }
        
        return sheetsData;
    }

    private String convertTaskStatusToDisplay(TaskStatus status) {
        if (status == null) return "";
        return switch (status) {
            case COMPLETED -> "Y";
            case IN_PROGRESS -> "Y";
            case PLANNED -> "Y";
            case BLOCKED -> "N";
            case NOT_APPLICABLE -> "";
            case AVAILABLE -> "Available";
        };
    }

    // Data classes for the sheet structure
    public static class CapacityTrackingSheet {
        public String title;
        public LocalDate generatedDate;
        public List<WeekColumn> timelineWeeks;
        public Map<String, List<CapacityRow>> teamData;

        public static CapacityTrackingSheetBuilder builder() {
            return new CapacityTrackingSheetBuilder();
        }

        public static class CapacityTrackingSheetBuilder {
            private CapacityTrackingSheet sheet = new CapacityTrackingSheet();

            public CapacityTrackingSheetBuilder title(String title) {
                sheet.title = title;
                return this;
            }

            public CapacityTrackingSheetBuilder generatedDate(LocalDate generatedDate) {
                sheet.generatedDate = generatedDate;
                return this;
            }

            public CapacityTrackingSheetBuilder timelineWeeks(List<WeekColumn> timelineWeeks) {
                sheet.timelineWeeks = timelineWeeks;
                return this;
            }

            public CapacityTrackingSheetBuilder teamData(Map<String, List<CapacityRow>> teamData) {
                sheet.teamData = teamData;
                return this;
            }

            public CapacityTrackingSheet build() {
                return sheet;
            }
        }

        // Getters
        public String getTitle() { return title; }
        public LocalDate getGeneratedDate() { return generatedDate; }
        public List<WeekColumn> getTimelineWeeks() { return timelineWeeks; }
        public Map<String, List<CapacityRow>> getTeamData() { return teamData; }
    }

    public static class WeekColumn {
        public String monthName;
        public String weekNumber;
        public LocalDate startDate;
        public LocalDate endDate;

        public static WeekColumnBuilder builder() {
            return new WeekColumnBuilder();
        }

        public static class WeekColumnBuilder {
            private WeekColumn week = new WeekColumn();

            public WeekColumnBuilder monthName(String monthName) {
                week.monthName = monthName;
                return this;
            }

            public WeekColumnBuilder weekNumber(String weekNumber) {
                week.weekNumber = weekNumber;
                return this;
            }

            public WeekColumnBuilder startDate(LocalDate startDate) {
                week.startDate = startDate;
                return this;
            }

            public WeekColumnBuilder endDate(LocalDate endDate) {
                week.endDate = endDate;
                return this;
            }

            public WeekColumn build() {
                return week;
            }
        }

        // Getters
        public String getMonthName() { return monthName; }
        public String getWeekNumber() { return weekNumber; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
    }

    public static class CapacityRow {
        public String pod;
        public String lead;
        public String taskType;
        public String currentTask;
        public String resource;
        public int manDays;
        public LocalDate startDate;
        public LocalDate endDate;
        public LocalDate qaDrop;
        public String tasksInPipeline;
        public Map<String, TaskStatus> weeklyStatus;

        public static CapacityRowBuilder builder() {
            return new CapacityRowBuilder();
        }

        public static class CapacityRowBuilder {
            private CapacityRow row = new CapacityRow();

            public CapacityRowBuilder pod(String pod) {
                row.pod = pod;
                return this;
            }

            public CapacityRowBuilder lead(String lead) {
                row.lead = lead;
                return this;
            }

            public CapacityRowBuilder taskType(String taskType) {
                row.taskType = taskType;
                return this;
            }

            public CapacityRowBuilder currentTask(String currentTask) {
                row.currentTask = currentTask;
                return this;
            }

            public CapacityRowBuilder resource(String resource) {
                row.resource = resource;
                return this;
            }

            public CapacityRowBuilder manDays(int manDays) {
                row.manDays = manDays;
                return this;
            }

            public CapacityRowBuilder startDate(LocalDate startDate) {
                row.startDate = startDate;
                return this;
            }

            public CapacityRowBuilder endDate(LocalDate endDate) {
                row.endDate = endDate;
                return this;
            }

            public CapacityRowBuilder qaDrop(LocalDate qaDrop) {
                row.qaDrop = qaDrop;
                return this;
            }

            public CapacityRowBuilder tasksInPipeline(String tasksInPipeline) {
                row.tasksInPipeline = tasksInPipeline;
                return this;
            }

            public CapacityRowBuilder weeklyStatus(Map<String, TaskStatus> weeklyStatus) {
                row.weeklyStatus = weeklyStatus;
                return this;
            }

            public CapacityRow build() {
                return row;
            }
        }

        // Getters
        public String getPod() { return pod; }
        public String getLead() { return lead; }
        public String getTaskType() { return taskType; }
        public String getCurrentTask() { return currentTask; }
        public String getResource() { return resource; }
        public int getManDays() { return manDays; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public LocalDate getQaDrop() { return qaDrop; }
        public String getTasksInPipeline() { return tasksInPipeline; }
        public Map<String, TaskStatus> getWeeklyStatus() { return weeklyStatus; }
    }

    public enum TaskStatus {
        COMPLETED,     // Y (Green)
        IN_PROGRESS,   // Y (Green)
        PLANNED,       // Y (Green)
        BLOCKED,       // N (Red)
        NOT_APPLICABLE, // Empty
        AVAILABLE      // Available text
    }
} 