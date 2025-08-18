package com.paytm.jiradashboard.repository;

import com.paytm.jiradashboard.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    
    Optional<TeamMember> findByName(String name);
    
    List<TeamMember> findByIsActiveTrue();
    
    List<TeamMember> findByTeam(String team);
    
    List<TeamMember> findByRole(String role);
    
    @Query("SELECT tm FROM TeamMember tm WHERE tm.isActive = true AND " +
           "(tm.endDate IS NULL OR tm.endDate >= :date)")
    List<TeamMember> findAvailableMembers(@Param("date") LocalDate date);
    
    @Query("SELECT tm FROM TeamMember tm WHERE tm.isActive = true AND " +
           "tm.skills LIKE %:skill%")
    List<TeamMember> findBySkillsContaining(@Param("skill") String skill);
} 