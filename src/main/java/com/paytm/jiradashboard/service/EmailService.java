package com.paytm.jiradashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
public class EmailService {
    
    @Value("${app.daily-summary.recipients}")
    private String recipients;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    private final JavaMailSender mailSender;
    
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    public void sendDailySummary(Map<String, Object> summary) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(recipients.split(","));
            message.setSubject("Daily Jira Dashboard Summary - " + 
                    java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            message.setText(buildEmailContent(summary));
            
            mailSender.send(message);
            log.info("Daily summary email sent successfully to: {}", recipients);
        } catch (Exception e) {
            log.error("Error sending daily summary email", e);
        }
    }
    
    private String buildEmailContent(Map<String, Object> summary) {
        StringBuilder content = new StringBuilder();
        content.append("Daily Jira Dashboard Summary\n");
        content.append("============================\n\n");
        
        // Total issues
        content.append("Total Issues: ").append(summary.get("totalIssues")).append("\n\n");
        
        // Status breakdown
        content.append("Status Breakdown:\n");
        Map<String, Long> statusBreakdown = (Map<String, Long>) summary.get("statusBreakdown");
        if (statusBreakdown != null) {
            statusBreakdown.forEach((status, count) -> 
                content.append("  ").append(status).append(": ").append(count).append("\n"));
        }
        content.append("\n");
        
        // Overdue issues
        content.append("Overdue Issues:\n");
        java.util.List<Object> overdueIssues = (java.util.List<Object>) summary.get("overdueIssues");
        if (overdueIssues != null && !overdueIssues.isEmpty()) {
            overdueIssues.forEach(issue -> 
                content.append("  - ").append(issue.toString()).append("\n"));
        } else {
            content.append("  No overdue issues\n");
        }
        content.append("\n");
        
        // Recently updated
        content.append("Recently Updated Issues:\n");
        java.util.List<Object> recentlyUpdated = (java.util.List<Object>) summary.get("recentlyUpdated");
        if (recentlyUpdated != null && !recentlyUpdated.isEmpty()) {
            recentlyUpdated.forEach(issue -> 
                content.append("  - ").append(issue.toString()).append("\n"));
        } else {
            content.append("  No recently updated issues\n");
        }
        
        content.append("\n\nDashboard URL: http://localhost:8080/dashboard");
        
        return content.toString();
    }
} 