package com.paytm.jiradashboard.model;

public enum IssueType {
    STORY("Story"),
    BUG("Bug"),
    TASK("Task"),
    EPIC("Epic"),
    SUBTASK("Sub-task"),
    FEATURE("Feature"),
    IMPROVEMENT("Improvement"),
    DOCUMENTATION("Documentation");
    
    private final String displayName;
    
    IssueType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static IssueType fromJiraType(String jiraType) {
        if (jiraType == null) return TASK;
        
        String normalizedType = jiraType.toLowerCase().trim();
        
        switch (normalizedType) {
            case "story":
            case "user story":
                return STORY;
            case "bug":
            case "defect":
                return BUG;
            case "task":
                return TASK;
            case "epic":
                return EPIC;
            case "sub-task":
            case "subtask":
                return SUBTASK;
            case "feature":
                return FEATURE;
            case "improvement":
            case "enhancement":
                return IMPROVEMENT;
            case "documentation":
            case "docs":
                return DOCUMENTATION;
            default:
                return TASK;
        }
    }
} 