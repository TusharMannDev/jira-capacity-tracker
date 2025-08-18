package com.paytm.jiradashboard.repository;

import com.paytm.jiradashboard.model.IssueStatus;
import com.paytm.jiradashboard.model.IssueType;
import com.paytm.jiradashboard.model.JiraIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JiraIssueRepository extends JpaRepository<JiraIssue, String> {
    
    List<JiraIssue> findByStatus(IssueStatus status);
    
    List<JiraIssue> findByAssignee(String assignee);
    
    List<JiraIssue> findByProjectKey(String projectKey);
    
    List<JiraIssue> findByIssueType(IssueType issueType);
    
    List<JiraIssue> findByStatusIn(List<IssueStatus> statuses);
    
    List<JiraIssue> findByAssigneeAndStatusIn(String assignee, List<IssueStatus> statuses);
    
    List<JiraIssue> findByProjectKeyAndStatusIn(String projectKey, List<IssueStatus> statuses);
    
    List<JiraIssue> findBySprint(String sprint);
    
    List<JiraIssue> findByEpicLink(String epicLink);
    
    @Query("SELECT j FROM JiraIssue j WHERE j.updated >= :since")
    List<JiraIssue> findRecentlyUpdated(@Param("since") LocalDateTime since);
    
    @Query("SELECT j FROM JiraIssue j WHERE j.dueDate <= :dueDate AND j.status NOT IN ('DONE', 'CLOSED')")
    List<JiraIssue> findOverdueIssues(@Param("dueDate") LocalDateTime dueDate);
    
    @Query("SELECT j FROM JiraIssue j WHERE j.assignee = :assignee AND j.dueDate <= :dueDate AND j.status NOT IN ('DONE', 'CLOSED')")
    List<JiraIssue> findOverdueIssuesByAssignee(@Param("assignee") String assignee, @Param("dueDate") LocalDateTime dueDate);
    
    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.status = :status")
    long countByStatus(@Param("status") IssueStatus status);
    
    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.assignee = :assignee AND j.status = :status")
    long countByAssigneeAndStatus(@Param("assignee") String assignee, @Param("status") IssueStatus status);
    
    @Query("SELECT j.assignee, COUNT(j) FROM JiraIssue j WHERE j.status IN :statuses GROUP BY j.assignee")
    List<Object[]> countByAssigneeAndStatusIn(@Param("statuses") List<IssueStatus> statuses);
    
    @Query("SELECT j.projectKey, COUNT(j) FROM JiraIssue j WHERE j.status IN :statuses GROUP BY j.projectKey")
    List<Object[]> countByProjectAndStatusIn(@Param("statuses") List<IssueStatus> statuses);
    
    @Query("SELECT j.status, COUNT(j) FROM JiraIssue j GROUP BY j.status")
    List<Object[]> countByStatusGroup();
    
    @Query("SELECT j FROM JiraIssue j WHERE j.lastSyncTime < :syncTime OR j.lastSyncTime IS NULL")
    List<JiraIssue> findIssuesNeedingSync(@Param("syncTime") LocalDateTime syncTime);
} 