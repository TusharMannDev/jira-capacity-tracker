package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.*;
import com.paytm.jiradashboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService {
    
    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeSampleData() {
        log.info("Checking if sample data cleanup is needed...");
        
        // Check if we have real Jira-generated team members
        boolean hasRealJiraData = teamMemberRepository.findAll().stream()
                .anyMatch(member -> member.getNotes() != null && 
                          member.getNotes().contains("Auto-created from Jira assignee"));
        
        if (hasRealJiraData) {
            // Remove sample data if real Jira data exists
            cleanupSampleData();
            log.info("Sample data cleanup completed - using real Jira data only");
            return;
        }
        
        // Only initialize sample data if no data exists and no real Jira data
        if (teamMemberRepository.count() == 0) {
            initializeTeamMembers();
            log.info("Sample team members created");
        }
        
        if (taskAssignmentRepository.count() == 0) {
            initializeTaskAssignments();
            log.info("Sample task assignments created");
        }
        
        log.info("Sample data initialization completed");
    }
    
    private void cleanupSampleData() {
        // Remove sample team members (those without "Auto-created from Jira assignee" note)
        List<TeamMember> sampleMembers = teamMemberRepository.findAll().stream()
                .filter(member -> member.getNotes() == null || 
                                !member.getNotes().contains("Auto-created from Jira assignee"))
                .toList();
        
        if (!sampleMembers.isEmpty()) {
            log.info("Removing {} sample team members", sampleMembers.size());
            teamMemberRepository.deleteAll(sampleMembers);
        }
        
        // Remove sample task assignments (those with fake COP- keys)
        List<TaskAssignment> sampleAssignments = taskAssignmentRepository.findAll().stream()
                .filter(assignment -> assignment.getIssueKey().matches("COP-10[1-7]"))
                .toList();
        
        if (!sampleAssignments.isEmpty()) {
            log.info("Removing {} sample task assignments", sampleAssignments.size());
            taskAssignmentRepository.deleteAll(sampleAssignments);
        }
    }
    
    private void initializeTeamMembers() {
        List<TeamMember> members = List.of(
            TeamMember.builder()
                .name("Tushar Mann")
                .email("tushar.mann@paytm.com")
                .role("Senior Developer")
                .team("Offline Payments")
                .hoursPerDay(8)
                .capacityMultiplier(1.0)
                .startDate(LocalDate.now().minusMonths(6))
                .isActive(true)
                .skills("Java, Spring Boot, React, Microservices")
                .notes("Team lead for offline payments project")
                .build(),
                
            TeamMember.builder()
                .name("Priya Sharma")
                .email("priya.sharma@paytm.com")
                .role("Frontend Developer")
                .team("Offline Payments")
                .hoursPerDay(8)
                .capacityMultiplier(0.9)
                .startDate(LocalDate.now().minusMonths(3))
                .isActive(true)
                .skills("React, TypeScript, CSS, UX")
                .notes("Frontend specialist")
                .build(),
                
            TeamMember.builder()
                .name("Rahul Singh")
                .email("rahul.singh@paytm.com")
                .role("Backend Developer")
                .team("Offline Payments")
                .hoursPerDay(8)
                .capacityMultiplier(1.1)
                .startDate(LocalDate.now().minusMonths(8))
                .isActive(true)
                .skills("Java, Spring, Database, API Design")
                .notes("Backend architecture expert")
                .build(),
                
            TeamMember.builder()
                .name("Anita Kumari")
                .email("anita.kumari@paytm.com")
                .role("QA Engineer")
                .team("Offline Payments")
                .hoursPerDay(8)
                .capacityMultiplier(0.8)
                .startDate(LocalDate.now().minusMonths(4))
                .isActive(true)
                .skills("Testing, Automation, Selenium, API Testing")
                .notes("Quality assurance lead")
                .build(),
                
            TeamMember.builder()
                .name("Dev Kumar")
                .email("dev.kumar@paytm.com")
                .role("DevOps Engineer")
                .team("Infrastructure")
                .hoursPerDay(8)
                .capacityMultiplier(0.7)
                .startDate(LocalDate.now().minusMonths(12))
                .isActive(true)
                .skills("AWS, Docker, Kubernetes, CI/CD")
                .notes("Infrastructure and deployment specialist")
                .build()
        );
        
        teamMemberRepository.saveAll(members);
    }
    
    private void initializeTaskAssignments() {
        List<TaskAssignment> assignments = List.of(
            TaskAssignment.builder()
                .issueKey("COP-101")
                .assigneeName("Tushar Mann")
                .estimatedHours(40)
                .actualHours(25)
                .remainingHours(15)
                .startDate(LocalDate.now().minusDays(10))
                .estimatedCompletionDate(LocalDate.now().plusDays(5))
                .percentComplete(62.5)
                .taskStatus(TaskAssignment.TaskStatus.IN_PROGRESS)
                .notes("Payment integration API development")
                .isBlocked(false)
                .build(),
                
            TaskAssignment.builder()
                .issueKey("COP-102")
                .assigneeName("Priya Sharma")
                .estimatedHours(32)
                .actualHours(20)
                .remainingHours(12)
                .startDate(LocalDate.now().minusDays(8))
                .estimatedCompletionDate(LocalDate.now().plusDays(3))
                .percentComplete(62.5)
                .taskStatus(TaskAssignment.TaskStatus.IN_PROGRESS)
                .notes("Payment UI components")
                .isBlocked(false)
                .build(),
                
            TaskAssignment.builder()
                .issueKey("COP-103")
                .assigneeName("Rahul Singh")
                .estimatedHours(48)
                .actualHours(0)
                .remainingHours(48)
                .startDate(LocalDate.now().plusDays(2))
                .estimatedCompletionDate(LocalDate.now().plusDays(8))
                .percentComplete(0.0)
                .taskStatus(TaskAssignment.TaskStatus.NOT_STARTED)
                .notes("Database schema optimization")
                .isBlocked(false)
                .build(),
                
            TaskAssignment.builder()
                .issueKey("COP-104")
                .assigneeName("Anita Kumari")
                .estimatedHours(24)
                .actualHours(8)
                .remainingHours(16)
                .startDate(LocalDate.now().minusDays(5))
                .estimatedCompletionDate(LocalDate.now().plusDays(4))
                .percentComplete(33.3)
                .taskStatus(TaskAssignment.TaskStatus.IN_PROGRESS)
                .notes("End-to-end testing setup")
                .isBlocked(false)
                .build(),
                
            TaskAssignment.builder()
                .issueKey("COP-105")
                .assigneeName("Dev Kumar")
                .estimatedHours(16)
                .actualHours(10)
                .remainingHours(6)
                .startDate(LocalDate.now().minusDays(7))
                .estimatedCompletionDate(LocalDate.now().plusDays(2))
                .percentComplete(62.5)
                .taskStatus(TaskAssignment.TaskStatus.IN_PROGRESS)
                .notes("CI/CD pipeline setup")
                .isBlocked(false)
                .build(),
                
            TaskAssignment.builder()
                .issueKey("COP-106")
                .assigneeName("Tushar Mann")
                .estimatedHours(20)
                .actualHours(0)
                .remainingHours(20)
                .startDate(LocalDate.now().plusDays(6))
                .estimatedCompletionDate(LocalDate.now().plusDays(10))
                .percentComplete(0.0)
                .taskStatus(TaskAssignment.TaskStatus.NOT_STARTED)
                .notes("Security audit implementation")
                .isBlocked(false)
                .build(),
                
            TaskAssignment.builder()
                .issueKey("COP-107")
                .assigneeName("Priya Sharma")
                .estimatedHours(16)
                .actualHours(4)
                .remainingHours(12)
                .startDate(LocalDate.now().minusDays(2))
                .estimatedCompletionDate(LocalDate.now().minusDays(1)) // Overdue
                .percentComplete(25.0)
                .taskStatus(TaskAssignment.TaskStatus.BLOCKED)
                .notes("Mobile responsive design")
                .isBlocked(true)
                .blockingReason("Waiting for design approval from stakeholders")
                .build()
        );
        
        taskAssignmentRepository.saveAll(assignments);
    }
}
