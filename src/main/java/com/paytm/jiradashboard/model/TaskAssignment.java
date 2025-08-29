package com.paytm.jiradashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String issueKey; // Reference to JiraIssue
    
    @Column(nullable = false)
    private String assigneeName; // Reference to TeamMember
    
    @Column
    private Integer estimatedHours; // Time estimated for completion
    
    @Column
    private Integer actualHours; // Time actually spent (if tracked)
    
    @Column
    private Integer remainingHours; // Hours remaining for completion
    
    @Column
    private LocalDate startDate; // When work started
    
    @Column
    private LocalDate estimatedCompletionDate; // When expected to finish
    
    @Column
    private LocalDate actualCompletionDate; // When actually finished
    
    @Column
    private Double percentComplete = 0.0; // Progress percentage
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus taskStatus = TaskStatus.NOT_STARTED;
    
    @Column
    private String notes; // Any additional notes about the assignment
    
    @Column
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column
    private Boolean isBlocked = false; // If task is blocked
    
    @Column
    private String blockingReason; // Reason for blocking
    
    public enum TaskStatus {
        NOT_STARTED,
        IN_PROGRESS,
        ON_HOLD,
        BLOCKED,
        COMPLETED,
        CANCELLED
    }
} 