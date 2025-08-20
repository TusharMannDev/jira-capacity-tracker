package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.*;
import com.paytm.jiradashboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityPlanningService {

    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final JiraIssueRepository jiraIssueRepository;

    public List<TeamCapacitySummary> getTeamCapacitySummary() {
        List<TeamMember> activeMembers = teamMemberRepository.findByIsActiveTrue();
        
        return activeMembers.stream()
                .map(this::calculateMemberCapacity)
                .collect(Collectors.toList());
    }

    public TeamCapacitySummary calculateMemberCapacity(TeamMember member) {
        List<TaskAssignment> activeTasks = taskAssignmentRepository.findActiveTasksByAssignee(member.getName());
        
        int totalRemainingHours = activeTasks.stream()
                .mapToInt(task -> task.getRemainingHours() != null ? task.getRemainingHours() : 0)
                .sum();
        
        double dailyCapacity = member.getHoursPerDay() * member.getCapacityMultiplier();
        int daysToComplete = totalRemainingHours > 0 ? (int) Math.ceil(totalRemainingHours / dailyCapacity) : 0;
        LocalDate availableFrom = LocalDate.now().plusDays(daysToComplete);
        
        // Get upcoming deadlines
        List<TaskAssignment> upcomingDeadlines = activeTasks.stream()
                .filter(task -> task.getEstimatedCompletionDate() != null && 
                               task.getEstimatedCompletionDate().isAfter(LocalDate.now()))
                .sorted(Comparator.comparing(TaskAssignment::getEstimatedCompletionDate))
                .collect(Collectors.toList());

        return TeamCapacitySummary.builder()
                .memberName(member.getName())
                .role(member.getRole())
                .team(member.getTeam())
                .dailyCapacityHours(dailyCapacity)
                .totalRemainingHours(totalRemainingHours)
                .activeTasks(activeTasks.size())
                .estimatedAvailableDate(availableFrom)
                .upcomingDeadlines(upcomingDeadlines)
                .isOverloaded(isOverloaded(member, activeTasks))
                .utilizationPercentage(calculateUtilization(member, activeTasks))
                .build();
    }

    public List<ResourceAvailability> getResourceAvailabilityForecast(int daysAhead) {
        LocalDate endDate = LocalDate.now().plusDays(daysAhead);
        List<TeamMember> activeMembers = teamMemberRepository.findByIsActiveTrue();
        
        return activeMembers.stream()
                .map(member -> calculateResourceAvailability(member, endDate))
                .collect(Collectors.toList());
    }

    private ResourceAvailability calculateResourceAvailability(TeamMember member, LocalDate endDate) {
        List<TaskAssignment> workload = taskAssignmentRepository.findWorkloadByAssigneeAndDate(
                member.getName(), endDate);
        
        Map<LocalDate, Double> dailyWorkload = new HashMap<>();
        
        // Calculate daily workload distribution
        for (TaskAssignment task : workload) {
            if (task.getRemainingHours() != null && task.getEstimatedCompletionDate() != null) {
                LocalDate startDate = task.getStartDate() != null ? task.getStartDate() : LocalDate.now();
                long daysToComplete = ChronoUnit.DAYS.between(startDate, task.getEstimatedCompletionDate()) + 1;
                
                if (daysToComplete > 0) {
                    double hoursPerDay = (double) task.getRemainingHours() / daysToComplete;
                    
                    for (LocalDate date = startDate; !date.isAfter(task.getEstimatedCompletionDate()); date = date.plusDays(1)) {
                        dailyWorkload.merge(date, hoursPerDay, Double::sum);
                    }
                }
            }
        }
        
        return ResourceAvailability.builder()
                .memberName(member.getName())
                .role(member.getRole())
                .team(member.getTeam())
                .dailyCapacity(member.getHoursPerDay() * member.getCapacityMultiplier())
                .workloadForecast(dailyWorkload)
                .build();
    }

    private boolean isOverloaded(TeamMember member, List<TaskAssignment> activeTasks) {
        double dailyCapacity = member.getHoursPerDay() * member.getCapacityMultiplier();
        
        // Check if any tasks have tight deadlines
        return activeTasks.stream().anyMatch(task -> {
            if (task.getRemainingHours() != null && task.getEstimatedCompletionDate() != null) {
                long daysAvailable = ChronoUnit.DAYS.between(LocalDate.now(), task.getEstimatedCompletionDate()) + 1;
                double requiredDailyHours = daysAvailable > 0 ? (double) task.getRemainingHours() / daysAvailable : 0;
                return requiredDailyHours > dailyCapacity;
            }
            return false;
        });
    }

    private double calculateUtilization(TeamMember member, List<TaskAssignment> activeTasks) {
        if (activeTasks.isEmpty()) return 0.0;
        
        double dailyCapacity = member.getHoursPerDay() * member.getCapacityMultiplier();
        
        // Calculate average daily workload for next 30 days
        Map<LocalDate, Double> dailyWorkload = new HashMap<>();
        LocalDate endDate = LocalDate.now().plusDays(30);
        
        for (TaskAssignment task : activeTasks) {
            if (task.getRemainingHours() != null && task.getEstimatedCompletionDate() != null) {
                LocalDate startDate = task.getStartDate() != null ? task.getStartDate() : LocalDate.now();
                long daysToComplete = ChronoUnit.DAYS.between(startDate, task.getEstimatedCompletionDate()) + 1;
                
                if (daysToComplete > 0) {
                    double hoursPerDay = (double) task.getRemainingHours() / daysToComplete;
                    
                    for (LocalDate date = startDate; !date.isAfter(task.getEstimatedCompletionDate()) && !date.isAfter(endDate); date = date.plusDays(1)) {
                        dailyWorkload.merge(date, hoursPerDay, Double::sum);
                    }
                }
            }
        }
        
        double averageWorkload = dailyWorkload.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        
        return Math.min(100.0, (averageWorkload / dailyCapacity) * 100.0);
    }

    public void syncJiraAssignments() {
        log.info("Syncing Jira assignments with capacity planning...");
        
        List<JiraIssue> activeIssues = jiraIssueRepository.findByStatusIn(
                Arrays.asList(IssueStatus.TO_DO, IssueStatus.IN_PROGRESS, IssueStatus.IN_REVIEW));
        
        // First, ensure all assignees exist as team members
        Set<String> assignees = activeIssues.stream()
                .map(JiraIssue::getAssignee)
                .filter(assignee -> assignee != null && !assignee.equals("Unassigned"))
                .collect(Collectors.toSet());
        
        for (String assignee : assignees) {
            ensureTeamMemberExists(assignee);
        }
        
        // Then sync task assignments
        for (JiraIssue issue : activeIssues) {
            if (!issue.getAssignee().equals("Unassigned")) {
                syncTaskAssignment(issue);
            }
        }
        
        log.info("Synced {} issues with {} unique assignees", activeIssues.size(), assignees.size());
    }

    private void ensureTeamMemberExists(String assigneeName) {
        Optional<TeamMember> existingMember = teamMemberRepository.findByName(assigneeName);
        
        if (existingMember.isEmpty()) {
            // Create a new team member with default values
            TeamMember newMember = TeamMember.builder()
                    .name(assigneeName)
                    .email(assigneeName.toLowerCase().replace(" ", ".") + "@paytm.com") // Generate email
                    .role("Developer") // Default role
                    .team("Development") // Default team
                    .hoursPerDay(8) // Standard 8 hours
                    .capacityMultiplier(1.0) // Full capacity
                    .isActive(true)
                    .startDate(LocalDate.now())
                    .skills("Java, Spring Boot") // Default skills
                    .notes("Auto-created from Jira assignee")
                    .build();
            
            teamMemberRepository.save(newMember);
            log.info("Created new team member for Jira assignee: {}", assigneeName);
        }
    }

    private void syncTaskAssignment(JiraIssue issue) {
        Optional<TaskAssignment> existingAssignment = taskAssignmentRepository
                .findByIssueKeyAndAssigneeName(issue.getIssueKey(), issue.getAssignee());
        
        if (existingAssignment.isEmpty()) {
            // Create new assignment with estimated hours based on story points
            int estimatedHours = estimateHoursFromStoryPoints(issue.getStoryPoints());
            
            TaskAssignment newAssignment = TaskAssignment.builder()
                    .issueKey(issue.getIssueKey())
                    .assigneeName(issue.getAssignee())
                    .estimatedHours(estimatedHours)
                    .remainingHours(estimatedHours)
                    .startDate(LocalDate.now())
                    .estimatedCompletionDate(issue.getDueDate() != null ? 
                                           issue.getDueDate().toLocalDate() : 
                                           calculateEstimatedCompletion(issue.getAssignee(), estimatedHours))
                    .taskStatus(mapJiraStatusToTaskStatus(issue.getStatus()))
                    .build();
            
            taskAssignmentRepository.save(newAssignment);
            log.debug("Created new task assignment for issue {} assigned to {}", 
                     issue.getIssueKey(), issue.getAssignee());
        }
    }

    private int estimateHoursFromStoryPoints(Integer storyPoints) {
        if (storyPoints == null) return 8; // Default 1 day
        
        // Convert story points to hours (this is configurable)
        Map<Integer, Integer> storyPointToHours = Map.of(
                1, 4,   // 0.5 day
                2, 8,   // 1 day
                3, 16,  // 2 days
                5, 32,  // 4 days
                8, 64,  // 8 days
                13, 104 // 13 days
        );
        
        return storyPointToHours.getOrDefault(storyPoints, storyPoints * 8); // Fallback: 1 day per point
    }

    private LocalDate calculateEstimatedCompletion(String assigneeName, int estimatedHours) {
        Optional<TeamMember> member = teamMemberRepository.findByName(assigneeName);
        if (member.isPresent()) {
            double dailyCapacity = member.get().getHoursPerDay() * member.get().getCapacityMultiplier();
            int daysNeeded = (int) Math.ceil(estimatedHours / dailyCapacity);
            return LocalDate.now().plusDays(daysNeeded);
        }
        return LocalDate.now().plusDays(estimatedHours / 8); // Default 8 hours per day
    }

    private TaskAssignment.TaskStatus mapJiraStatusToTaskStatus(IssueStatus jiraStatus) {
        return switch (jiraStatus) {
            case TO_DO -> TaskAssignment.TaskStatus.NOT_STARTED;
            case IN_PROGRESS -> TaskAssignment.TaskStatus.IN_PROGRESS;
            case IN_REVIEW -> TaskAssignment.TaskStatus.IN_PROGRESS;
            case DONE -> TaskAssignment.TaskStatus.COMPLETED;
            case BLOCKED -> TaskAssignment.TaskStatus.BLOCKED;
            default -> TaskAssignment.TaskStatus.NOT_STARTED;
        };
    }

    // DTOs for API responses
    public static class TeamCapacitySummary {
        public String memberName;
        public String role;
        public String team;
        public double dailyCapacityHours;
        public int totalRemainingHours;
        public int activeTasks;
        public LocalDate estimatedAvailableDate;
        public List<TaskAssignment> upcomingDeadlines;
        public boolean isOverloaded;
        public double utilizationPercentage;

        public static TeamCapacitySummaryBuilder builder() {
            return new TeamCapacitySummaryBuilder();
        }

        public static class TeamCapacitySummaryBuilder {
            private TeamCapacitySummary summary = new TeamCapacitySummary();

            public TeamCapacitySummaryBuilder memberName(String memberName) {
                summary.memberName = memberName;
                return this;
            }

            public TeamCapacitySummaryBuilder role(String role) {
                summary.role = role;
                return this;
            }

            public TeamCapacitySummaryBuilder team(String team) {
                summary.team = team;
                return this;
            }

            public TeamCapacitySummaryBuilder dailyCapacityHours(double dailyCapacityHours) {
                summary.dailyCapacityHours = dailyCapacityHours;
                return this;
            }

            public TeamCapacitySummaryBuilder totalRemainingHours(int totalRemainingHours) {
                summary.totalRemainingHours = totalRemainingHours;
                return this;
            }

            public TeamCapacitySummaryBuilder activeTasks(int activeTasks) {
                summary.activeTasks = activeTasks;
                return this;
            }

            public TeamCapacitySummaryBuilder estimatedAvailableDate(LocalDate estimatedAvailableDate) {
                summary.estimatedAvailableDate = estimatedAvailableDate;
                return this;
            }

            public TeamCapacitySummaryBuilder upcomingDeadlines(List<TaskAssignment> upcomingDeadlines) {
                summary.upcomingDeadlines = upcomingDeadlines;
                return this;
            }

            public TeamCapacitySummaryBuilder isOverloaded(boolean isOverloaded) {
                summary.isOverloaded = isOverloaded;
                return this;
            }

            public TeamCapacitySummaryBuilder utilizationPercentage(double utilizationPercentage) {
                summary.utilizationPercentage = utilizationPercentage;
                return this;
            }

            public TeamCapacitySummary build() {
                return summary;
            }
        }
    }

    public static class ResourceAvailability {
        public String memberName;
        public String role;
        public String team;
        public double dailyCapacity;
        public Map<LocalDate, Double> workloadForecast;

        public static ResourceAvailabilityBuilder builder() {
            return new ResourceAvailabilityBuilder();
        }

        public static class ResourceAvailabilityBuilder {
            private ResourceAvailability availability = new ResourceAvailability();

            public ResourceAvailabilityBuilder memberName(String memberName) {
                availability.memberName = memberName;
                return this;
            }

            public ResourceAvailabilityBuilder role(String role) {
                availability.role = role;
                return this;
            }

            public ResourceAvailabilityBuilder team(String team) {
                availability.team = team;
                return this;
            }

            public ResourceAvailabilityBuilder dailyCapacity(double dailyCapacity) {
                availability.dailyCapacity = dailyCapacity;
                return this;
            }

            public ResourceAvailabilityBuilder workloadForecast(Map<LocalDate, Double> workloadForecast) {
                availability.workloadForecast = workloadForecast;
                return this;
            }

            public ResourceAvailability build() {
                return availability;
            }
        }
    }
} 