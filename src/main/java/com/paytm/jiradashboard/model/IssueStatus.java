package com.paytm.jiradashboard.model;

public enum IssueStatus {
    TO_DO("To Do"),
    IN_PROGRESS("In Progress"),
    IN_REVIEW("In Review"),
    IN_QA("In QA"),
    QA_PASSED("QA Passed"),
    QA_FAILED("QA Failed"),
    IN_UAT("In UAT"),
    UAT_PASSED("UAT Passed"),
    UAT_FAILED("UAT Failed"),
    DONE("Done"),
    CLOSED("Closed"),
    BLOCKED("Blocked"),
    ON_HOLD("On Hold");
    
    private final String displayName;
    
    IssueStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static IssueStatus fromJiraStatus(String jiraStatus) {
        if (jiraStatus == null) return TO_DO;
        
        String normalizedStatus = jiraStatus.toLowerCase().trim();
        
        switch (normalizedStatus) {
            case "to do":
            case "open":
            case "new":
                return TO_DO;
            case "in progress":
            case "development":
            case "dev":
                return IN_PROGRESS;
            case "in review":
            case "code review":
            case "review":
                return IN_REVIEW;
            case "in qa":
            case "qa":
            case "testing":
                return IN_QA;
            case "qa passed":
            case "tested":
                return QA_PASSED;
            case "qa failed":
            case "testing failed":
                return QA_FAILED;
            case "in uat":
            case "uat":
            case "user acceptance testing":
                return IN_UAT;
            case "uat passed":
            case "uat approved":
                return UAT_PASSED;
            case "uat failed":
            case "uat rejected":
                return UAT_FAILED;
            case "done":
            case "resolved":
            case "complete":
                return DONE;
            case "closed":
                return CLOSED;
            case "blocked":
                return BLOCKED;
            case "on hold":
            case "waiting":
                return ON_HOLD;
            default:
                return TO_DO;
        }
    }
} 