package com.paytm.jiradashboard.repository;

import com.paytm.jiradashboard.model.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    
    List<TaskAssignment> findByAssigneeName(String assigneeName);
    
    List<TaskAssignment> findByIssueKey(String issueKey);
    
    Optional<TaskAssignment> findByIssueKeyAndAssigneeName(String issueKey, String assigneeName);
    
    @Query("SELECT ta FROM TaskAssignment ta WHERE ta.assigneeName = :assignee AND " +
           "ta.taskStatus IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED')")
    List<TaskAssignment> findActiveTasksByAssignee(@Param("assignee") String assigneeName);
    
    @Query("SELECT ta FROM TaskAssignment ta WHERE ta.estimatedCompletionDate <= :date AND " +
           "ta.taskStatus IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED')")
    List<TaskAssignment> findOverdueTasksByDate(@Param("date") LocalDate date);
    
    @Query("SELECT ta FROM TaskAssignment ta WHERE ta.estimatedCompletionDate BETWEEN :startDate AND :endDate")
    List<TaskAssignment> findTasksByCompletionDateRange(@Param("startDate") LocalDate startDate, 
                                                       @Param("endDate") LocalDate endDate);
    
    @Query("SELECT SUM(ta.remainingHours) FROM TaskAssignment ta WHERE ta.assigneeName = :assignee AND " +
           "ta.taskStatus IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED')")
    Integer getTotalRemainingHoursByAssignee(@Param("assignee") String assigneeName);
    
    @Query("SELECT ta FROM TaskAssignment ta WHERE ta.assigneeName = :assignee AND " +
           "ta.estimatedCompletionDate <= :date AND " +
           "ta.taskStatus IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED')")
    List<TaskAssignment> findWorkloadByAssigneeAndDate(@Param("assignee") String assigneeName, 
                                                      @Param("date") LocalDate date);
    
    List<TaskAssignment> findByIsBlockedTrue();
} 