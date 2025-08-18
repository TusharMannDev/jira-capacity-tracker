package com.paytm.jiradashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Entity
@Table(name = "team_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMember {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false)
    private String email;
    
    @Column
    private String role; // Developer, QA, Designer, etc.
    
    @Column
    private String team; // Frontend, Backend, DevOps, etc.
    
    @Column(nullable = false)
    private Integer hoursPerDay = 8; // Standard work hours per day
    
    @Column(nullable = false)
    private Double capacityMultiplier = 1.0; // For adjusting capacity (part-time, etc.)
    
    @Column
    private LocalDate startDate;
    
    @Column
    private LocalDate endDate; // For contractors or temporary leaves
    
    @Column
    private Boolean isActive = true;
    
    @Column
    private String skills; // Comma-separated skills
    
    @Column
    private String notes; // Any additional notes about availability
} 