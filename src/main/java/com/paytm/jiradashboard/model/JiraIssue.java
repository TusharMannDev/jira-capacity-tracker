package com.paytm.jiradashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "jira_issues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraIssue {
    
    @Id
    private String issueKey;
    
    @Column(nullable = false)
    private String summary;
    
    @Column(length = 5000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueType issueType;
    
    @Column(nullable = false)
    private String assignee;
    
    @Column(nullable = false)
    private String reporter;
    
    @Column(nullable = false)
    private String projectKey;
    
    @Column(nullable = false)
    private String projectName;
    
    @Column
    private String priority;
    
    @Column
    private LocalDateTime created;
    
    @Column
    private LocalDateTime updated;
    
    @Column
    private LocalDateTime resolved;
    
    @Column
    private LocalDateTime dueDate;
    
    @Column
    private Integer storyPoints;
    
    @Column
    private String labels;
    
    @Column
    private String components;
    
    @Column
    private LocalDateTime lastSyncTime;
    
    @Column
    private String sprint;
    
    @Column
    private String epicLink;
    
    @Column
    private String epicName;
} 