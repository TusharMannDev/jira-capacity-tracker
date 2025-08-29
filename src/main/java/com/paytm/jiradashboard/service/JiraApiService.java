package com.paytm.jiradashboard.service;

import com.paytm.jiradashboard.model.IssueStatus;
import com.paytm.jiradashboard.model.IssueType;
import com.paytm.jiradashboard.model.JiraIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JiraApiService {
    
    @Value("${jira.base-url}")
    private String jiraBaseUrl;
    
    // Username not needed for token-based auth
    
    @Value("${jira.api-token}")
    private String jiraApiToken;
    
    @Value("${jira.jql-filter}")
    private String jqlFilter;
    
    private final RestTemplate restTemplate;
    
    public JiraApiService() {
        this.restTemplate = new RestTemplate();
    }
    
    public List<JiraIssue> fetchIssues() {
        try {
            log.info("Fetching issues from Jira with JQL: {}", jqlFilter);
            
            String url = UriComponentsBuilder
                    .fromHttpUrl(jiraBaseUrl + "/rest/api/2/search")
                    .queryParam("jql", jqlFilter)
                    .queryParam("maxResults", 1000)
                    .queryParam("fields", "summary,description,status,issuetype,assignee,reporter,project,priority,created,updated,resolutiondate,duedate,customfield_10016,labels,components,customfield_10020,customfield_10014")
                    .build()
                    .toUriString();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> issues = (List<Map<String, Object>>) responseBody.get("issues");
                
                return issues.stream()
                        .map(this::mapToJiraIssue)
                        .collect(Collectors.toList());
            }
            
        } catch (Exception e) {
            log.error("Error fetching issues from Jira", e);
        }
        
        return new ArrayList<>();
    }
    
    public List<JiraIssue> fetchIssuesByJQL(String customJql) {
        try {
            log.info("Fetching issues from Jira with custom JQL: {}", customJql);
            
            String url = UriComponentsBuilder
                    .fromHttpUrl(jiraBaseUrl + "/rest/api/2/search")
                    .queryParam("jql", customJql)
                    .queryParam("maxResults", 1000)
                    .queryParam("fields", "summary,description,status,issuetype,assignee,reporter,project,priority,created,updated,resolutiondate,duedate,customfield_10016,labels,components,customfield_10020,customfield_10014")
                    .build()
                    .toUriString();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> issues = (List<Map<String, Object>>) responseBody.get("issues");
                
                return issues.stream()
                        .map(this::mapToJiraIssue)
                        .collect(Collectors.toList());
            }
            
        } catch (Exception e) {
            log.error("Error fetching issues from Jira with custom JQL", e);
        }
        
        return new ArrayList<>();
    }
    
    private JiraIssue mapToJiraIssue(Map<String, Object> issueData) {
        Map<String, Object> fields = (Map<String, Object>) issueData.get("fields");
        
        return JiraIssue.builder()
                .issueKey((String) issueData.get("key"))
                .summary((String) fields.get("summary"))
                .description(truncateDescription((String) fields.get("description")))
                .status(mapStatus(fields))
                .issueType(mapIssueType(fields))
                .assignee(mapAssignee(fields))
                .reporter(mapReporter(fields))
                .projectKey(mapProjectKey(fields))
                .projectName(mapProjectName(fields))
                .priority(mapPriority(fields))
                .created(parseDateTime((String) fields.get("created")))
                .updated(parseDateTime((String) fields.get("updated")))
                .resolved(parseDateTime((String) fields.get("resolutiondate")))
                .dueDate(parseDateTime((String) fields.get("duedate")))
                .storyPoints(mapStoryPoints(fields))
                .labels(mapLabels(fields))
                .components(mapComponents(fields))
                .lastSyncTime(LocalDateTime.now())
                .sprint(mapSprint(fields))
                .epicLink(mapEpicLink(fields))
                .epicName(mapEpicName(fields))
                .build();
    }
    
    private IssueStatus mapStatus(Map<String, Object> fields) {
        Map<String, Object> status = (Map<String, Object>) fields.get("status");
        if (status != null) {
            String statusName = (String) status.get("name");
            return IssueStatus.fromJiraStatus(statusName);
        }
        return IssueStatus.TO_DO;
    }
    
    private IssueType mapIssueType(Map<String, Object> fields) {
        Map<String, Object> issueType = (Map<String, Object>) fields.get("issuetype");
        if (issueType != null) {
            String typeName = (String) issueType.get("name");
            return IssueType.fromJiraType(typeName);
        }
        return IssueType.TASK;
    }
    
    private String mapAssignee(Map<String, Object> fields) {
        Map<String, Object> assignee = (Map<String, Object>) fields.get("assignee");
        return assignee != null ? (String) assignee.get("displayName") : "Unassigned";
    }
    
    private String mapReporter(Map<String, Object> fields) {
        Map<String, Object> reporter = (Map<String, Object>) fields.get("reporter");
        return reporter != null ? (String) reporter.get("displayName") : "Unknown";
    }
    
    private String mapProjectKey(Map<String, Object> fields) {
        Map<String, Object> project = (Map<String, Object>) fields.get("project");
        return project != null ? (String) project.get("key") : "Unknown";
    }
    
    private String mapProjectName(Map<String, Object> fields) {
        Map<String, Object> project = (Map<String, Object>) fields.get("project");
        return project != null ? (String) project.get("name") : "Unknown";
    }
    
    private String mapPriority(Map<String, Object> fields) {
        Map<String, Object> priority = (Map<String, Object>) fields.get("priority");
        return priority != null ? (String) priority.get("name") : "Medium";
    }
    
    private Integer mapStoryPoints(Map<String, Object> fields) {
        Object storyPoints = fields.get("customfield_10016"); // Common field for story points
        if (storyPoints instanceof Number) {
            return ((Number) storyPoints).intValue();
        }
        return null;
    }
    
    private String mapLabels(Map<String, Object> fields) {
        List<String> labels = (List<String>) fields.get("labels");
        return labels != null ? String.join(", ", labels) : "";
    }
    
    private String mapComponents(Map<String, Object> fields) {
        List<Map<String, Object>> components = (List<Map<String, Object>>) fields.get("components");
        if (components != null) {
            return components.stream()
                    .map(comp -> (String) comp.get("name"))
                    .collect(Collectors.joining(", "));
        }
        return "";
    }
    
    private String mapSprint(Map<String, Object> fields) {
        List<Map<String, Object>> sprints = (List<Map<String, Object>>) fields.get("customfield_10020");
        if (sprints != null && !sprints.isEmpty()) {
            return (String) sprints.get(0).get("name");
        }
        return "";
    }
    
    private String mapEpicLink(Map<String, Object> fields) {
        return (String) fields.get("customfield_10014");
    }
    
    private String mapEpicName(Map<String, Object> fields) {
        // This would require an additional API call to get epic name
        // For now, return empty string
        return "";
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception e2) {
                log.warn("Could not parse date: {}", dateTimeStr);
                return null;
            }
        }
    }
    
    private String truncateDescription(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }
        
        // Truncate to 4500 characters to be safe (leaving room for encoding differences)
        if (description.length() > 4500) {
            return description.substring(0, 4500) + "...";
        }
        
        return description;
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // Use only API token as Bearer token (similar to z-one)
        headers.set("Authorization", "Bearer " + jiraApiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
} 