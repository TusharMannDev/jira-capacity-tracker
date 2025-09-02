package com.paytm.jiradashboard.controller;

import com.paytm.jiradashboard.service.GoogleSheetsIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/sheets")
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsController {

    private final GoogleSheetsIntegrationService sheetsService;

    @PostMapping("/create-capacity-tracker")
    public ResponseEntity<Map<String, Object>> createCapacityTracker() {
        try {
            log.info("Creating capacity tracking sheet...");
            String result = sheetsService.createCapacityTrackingSheet();
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Capacity tracking sheet created successfully",
                    "result", result
            ));
        } catch (Exception e) {
            log.error("Error creating capacity tracker", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to create capacity tracking sheet: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/capacity-summary")
    public ResponseEntity<Map<String, Object>> getCapacitySummary() {
        try {
            Map<String, Object> summary = sheetsService.getSheetSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting capacity summary", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to get capacity summary: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/export-capacity-data")
    public ResponseEntity<List<List<Object>>> exportCapacityData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<List<Object>> data;
            if (startDate != null && endDate != null) {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                data = sheetsService.exportCapacityDataByDateRange(start, end);
            } else {
                data = sheetsService.exportCapacityData();
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error exporting capacity data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export-capacity-csv")
    public ResponseEntity<String> exportCapacityDataAsCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<List<Object>> data;
            if (startDate != null && endDate != null) {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                data = sheetsService.exportCapacityDataByDateRange(start, end);
            } else {
                data = sheetsService.exportCapacityData();
            }
            String csvContent = convertToCsv(data);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("Content-Disposition", "attachment; filename=capacity-tracker.csv");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);
                    
        } catch (Exception e) {
            log.error("Error exporting capacity data as CSV", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/preview-capacity-data")
    public ResponseEntity<Map<String, Object>> previewCapacityData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<List<Object>> data;
            Map<String, Object> summary;
            
            if (startDate != null && endDate != null) {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                data = sheetsService.exportCapacityDataByDateRange(start, end);
                summary = sheetsService.getSheetSummaryByDateRange(start, end);
            } else {
                data = sheetsService.exportCapacityData();
                summary = sheetsService.getSheetSummary();
            }
            
            // Return first 50 rows for preview
            List<List<Object>> preview = data.stream()
                    .limit(50)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            Map<String, Object> response = Map.of(
                    "summary", summary,
                    "preview", preview,
                    "totalRows", data.size(),
                    "previewRows", preview.size(),
                    "dateRange", startDate != null && endDate != null ? 
                        Map.of("startDate", startDate, "endDate", endDate) : "All data"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error previewing capacity data", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to preview capacity data: " + e.getMessage()
            ));
        }
    }

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
} 