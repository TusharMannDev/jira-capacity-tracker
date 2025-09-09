package com.paytm.jiradashboard.controller;

import com.paytm.jiradashboard.service.SingleSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/single-snapshot")
@RequiredArgsConstructor
@Slf4j
public class SingleSnapshotController {

    private final SingleSnapshotService snapshotService;

    /**
     * Get available labels for selection
     */
    @GetMapping("/available-labels")
    public ResponseEntity<Map<String, Object>> getAvailableLabels() {
        try {
            List<String> labels = snapshotService.getAvailableLabels();
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "labels", labels,
                    "totalLabels", labels.size()
            ));
        } catch (Exception e) {
            log.error("Error getting available labels", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to get available labels: " + e.getMessage()
            ));
        }
    }

    /**
     * Generate Single Snapshot for selected labels (start and end dates are now mandatory)
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateSingleSnapshot(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> selectedLabels = (List<String>) request.getOrDefault("labels", new ArrayList<>());
            String startDateStr = (String) request.get("startDate");
            String endDateStr = (String) request.get("endDate");
            
            // Validate mandatory date range
            if (startDateStr == null || startDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Start date is mandatory for performance reasons"
                ));
            }
            
            if (endDateStr == null || endDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "End date is mandatory for performance reasons"
                ));
            }
            
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);
            
            // Validate date range
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Start date must be before or equal to end date"
                ));
            }
            
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 180) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Date range cannot exceed 6 months (180 days) for performance reasons. Current range: " + daysBetween + " days"
                ));
            }
            
            log.info("Generating Single Snapshot for labels: {} and date range: {} to {} ({} days)", 
                    selectedLabels, startDate, endDate, daysBetween);
            
            SingleSnapshotService.SingleSnapshotSheet snapshot = 
                    snapshotService.generateSingleSnapshotByDateRange(selectedLabels, startDate, endDate);
            
            List<List<Object>> sheetsData = snapshotService.convertToSheetsData(snapshot);
            
            Map<String, Object> snapshotInfo = Map.of(
                    "title", snapshot.getTitle(),
                    "generatedDate", snapshot.getGeneratedDate().toString(),
                    "selectedLabels", snapshot.getSelectedLabels(),
                    "totalTasks", snapshot.getTotalTasks(),
                    "totalPods", snapshot.getPodData().size(),
                    "dateRange", snapshot.getDateRange() != null ? snapshot.getDateRange() : Map.of("startDate", startDate.toString(), "endDate", endDate.toString()),
                    "daysInRange", daysBetween
            );
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Single Snapshot generated successfully",
                    "snapshot", snapshotInfo,
                    "data", sheetsData
            ));
        } catch (Exception e) {
            log.error("Error generating Single Snapshot", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to generate Single Snapshot: " + e.getMessage()
            ));
        }
    }

    /**
     * Preview Single Snapshot data (start and end dates are now mandatory)
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewSingleSnapshot(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> selectedLabels = (List<String>) request.getOrDefault("labels", new ArrayList<>());
            String startDateStr = (String) request.get("startDate");
            String endDateStr = (String) request.get("endDate");
            
            // Validate mandatory date range
            if (startDateStr == null || startDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Start date is mandatory for performance reasons"
                ));
            }
            
            if (endDateStr == null || endDateStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "End date is mandatory for performance reasons"
                ));
            }
            
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);
            
            // Validate date range
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Start date must be before or equal to end date"
                ));
            }
            
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 180) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Date range cannot exceed 6 months (180 days) for performance reasons. Current range: " + daysBetween + " days"
                ));
            }
            
            SingleSnapshotService.SingleSnapshotSheet snapshot = 
                    snapshotService.generateSingleSnapshotByDateRange(selectedLabels, startDate, endDate);
            
            // Use horizontal layout for preview (Google Sheets style)
            List<List<Object>> horizontalData = snapshotService.convertToHorizontalLayout(snapshot);
            
            // Return first 100 rows for preview
            List<List<Object>> preview = horizontalData.stream()
                    .limit(100)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "snapshot", Map.of(
                            "title", snapshot.getTitle(),
                            "generatedDate", snapshot.getGeneratedDate().toString(),
                            "selectedLabels", snapshot.getSelectedLabels(),
                            "totalTasks", snapshot.getTotalTasks(),
                            "totalPods", snapshot.getPodData().size(),
                            "dateRange", snapshot.getDateRange() != null ? snapshot.getDateRange() : "All data"
                    ),
                    "preview", preview,
                    "totalRows", horizontalData.size(),
                    "previewRows", preview.size()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error previewing Single Snapshot", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to preview Single Snapshot: " + e.getMessage()
            ));
        }
    }

    /**
     * Export Single Snapshot as CSV
     */
    @PostMapping("/export-csv")
    public ResponseEntity<String> exportSingleSnapshotAsCsv(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> selectedLabels = (List<String>) request.getOrDefault("labels", new ArrayList<>());
            String startDateStr = (String) request.get("startDate");
            String endDateStr = (String) request.get("endDate");
            
            SingleSnapshotService.SingleSnapshotSheet snapshot;
            if (startDateStr != null && endDateStr != null) {
                LocalDate startDate = LocalDate.parse(startDateStr);
                LocalDate endDate = LocalDate.parse(endDateStr);
                snapshot = snapshotService.generateSingleSnapshotByDateRange(selectedLabels, startDate, endDate);
            } else {
                snapshot = snapshotService.generateSingleSnapshot(selectedLabels);
            }
            
            // Use horizontal layout for CSV export (Google Sheets style)
            List<List<Object>> horizontalData = snapshotService.convertToHorizontalLayout(snapshot);
            String csvContent = convertToCsv(horizontalData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("Content-Disposition", "attachment; filename=single-snapshot-" + 
                    snapshot.getGeneratedDate() + ".csv");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);
                    
        } catch (Exception e) {
            log.error("Error exporting Single Snapshot as CSV", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get Single Snapshot summary statistics
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSingleSnapshotSummary(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> selectedLabels = (List<String>) request.getOrDefault("labels", new ArrayList<>());
            String startDateStr = (String) request.get("startDate");
            String endDateStr = (String) request.get("endDate");
            
            SingleSnapshotService.SingleSnapshotSheet snapshot;
            if (startDateStr != null && endDateStr != null) {
                LocalDate startDate = LocalDate.parse(startDateStr);
                LocalDate endDate = LocalDate.parse(endDateStr);
                snapshot = snapshotService.generateSingleSnapshotByDateRange(selectedLabels, startDate, endDate);
            } else {
                snapshot = snapshotService.generateSingleSnapshot(selectedLabels);
            }
            
            // Calculate statistics
            Map<String, Long> statusCounts = snapshot.getPodData().values().stream()
                    .flatMap(List::stream)
                    .collect(java.util.stream.Collectors.groupingBy(
                            SingleSnapshotService.PodTaskRow::getStatus,
                            java.util.stream.Collectors.counting()));
            
            Map<String, Long> podCounts = snapshot.getPodData().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> (long) entry.getValue().size()));
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "summary", Map.of(
                            "title", snapshot.getTitle(),
                            "generatedDate", snapshot.getGeneratedDate().toString(),
                            "selectedLabels", snapshot.getSelectedLabels(),
                            "totalTasks", snapshot.getTotalTasks(),
                            "totalPods", snapshot.getPodData().size(),
                            "dateRange", snapshot.getDateRange() != null ? snapshot.getDateRange() : "All data",
                            "statusBreakdown", statusCounts,
                            "podBreakdown", podCounts
                    )
            ));
        } catch (Exception e) {
            log.error("Error getting Single Snapshot summary", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to get Single Snapshot summary: " + e.getMessage()
            ));
        }
    }

    /**
     * Convert data to CSV format
     */
    private String convertToCsv(List<List<Object>> data) {
        StringBuilder csv = new StringBuilder();
        
        for (List<Object> row : data) {
            StringBuilder rowBuilder = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    rowBuilder.append(",");
                }
                
                String value = row.get(i) != null ? row.get(i).toString() : "";
                
                // Escape quotes and wrap in quotes if contains comma or quote
                if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                    value = "\"" + value.replace("\"", "\"\"") + "\"";
                }
                
                rowBuilder.append(value);
            }
            csv.append(rowBuilder.toString()).append("\n");
        }
        
        return csv.toString();
    }

    /**
     * Debug endpoint to check team resources for a specific label
     */
    @PostMapping("/debug-team-resources")
    public ResponseEntity<Map<String, Object>> debugTeamResources(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> selectedLabels = (List<String>) request.getOrDefault("labels", new ArrayList<>());
            String startDateStr = (String) request.get("startDate");
            String endDateStr = (String) request.get("endDate");
            
            if (startDateStr == null || endDateStr == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Start date and end date are required"
                ));
            }
            
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);
            
            // Get the snapshot data
            SingleSnapshotService.SingleSnapshotSheet snapshot = 
                    snapshotService.generateSingleSnapshotByDateRange(selectedLabels, startDate, endDate);
            
            Map<String, Object> debugInfo = new LinkedHashMap<>();
            
            for (Map.Entry<String, List<SingleSnapshotService.PodTaskRow>> entry : snapshot.getPodData().entrySet()) {
                String label = entry.getKey();
                List<SingleSnapshotService.PodTaskRow> tasks = entry.getValue();
                
                // Get all assignees
                List<String> allAssignees = tasks.stream()
                        .map(SingleSnapshotService.PodTaskRow::getAssignee)
                        .collect(Collectors.toList());
                
                // Get unique assignees
                Set<String> uniqueAssignees = tasks.stream()
                        .map(SingleSnapshotService.PodTaskRow::getAssignee)
                        .filter(assignee -> assignee != null && !assignee.trim().isEmpty() && !assignee.equals("Unassigned"))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                
                debugInfo.put(label, Map.of(
                        "totalTasks", tasks.size(),
                        "allAssignees", allAssignees,
                        "uniqueAssignees", new ArrayList<>(uniqueAssignees),
                        "uniqueCount", uniqueAssignees.size()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "debugInfo", debugInfo
            ));
            
        } catch (Exception e) {
            log.error("Error in debug team resources", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Debug failed: " + e.getMessage()
            ));
        }
    }
}
