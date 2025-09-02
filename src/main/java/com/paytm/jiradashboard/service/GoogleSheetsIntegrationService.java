package com.paytm.jiradashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsIntegrationService {

    private final GoogleSheetsExportService exportService;
    private final RestTemplate restTemplate = new RestTemplate();

    public String createCapacityTrackingSheet() {
        try {
            log.info("Creating new Google Sheets capacity tracker...");
            
            // Generate the capacity data
            GoogleSheetsExportService.CapacityTrackingSheet capacitySheet = 
                    exportService.generateCapacityTrackingSheet();
            
            // Convert to Google Sheets format
            List<List<Object>> sheetsData = exportService.convertToSheetsData(capacitySheet);
            
            // Create new Google Sheet using z-one API (placeholder for when z-one works)
            String sheetTitle = capacitySheet.getTitle() + " - " + capacitySheet.getGeneratedDate();
            
            try {
                // This would use the z-one Google Sheets API when it's working
                String spreadsheetId = createGoogleSheet(sheetTitle);
                
                // Populate the sheet with data
                populateSheetData(spreadsheetId, sheetsData);
                
                // Apply formatting
                applySheetFormatting(spreadsheetId, capacitySheet);
                
                log.info("Created capacity tracking sheet: {}", spreadsheetId);
                return spreadsheetId;
                
            } catch (Exception e) {
                log.warn("Google Sheets API not available, returning mock data: {}", e.getMessage());
                return generateMockSheetResponse(capacitySheet, sheetsData);
            }
            
        } catch (Exception e) {
            log.error("Error creating capacity tracking sheet", e);
            throw new RuntimeException("Failed to create capacity tracking sheet", e);
        }
    }

    private String createGoogleSheet(String title) {
        // This would use z-one's Google Sheets API when available
        // For now, we'll simulate the response
        
        Map<String, Object> createRequest = Map.of(
                "title", title
        );
        
        try {
            // Placeholder for z-one Google Sheets API call
            // ResponseEntity<Map> response = restTemplate.postForEntity(
            //     "/api/z-one/sheets/create", createRequest, Map.class);
            
            // Simulate sheet creation
            String mockSheetId = "1" + System.currentTimeMillis(); // Mock spreadsheet ID
            log.info("Mock sheet created with ID: {}", mockSheetId);
            return mockSheetId;
            
        } catch (Exception e) {
            log.error("Error creating Google Sheet", e);
            throw new RuntimeException("Failed to create Google Sheet", e);
        }
    }

    private void populateSheetData(String spreadsheetId, List<List<Object>> data) {
        try {
            // Calculate range based on data size
            String range = "A1:" + getColumnLetter(data.get(0).size()) + data.size();
            
            Map<String, Object> updateRequest = Map.of(
                    "range", range,
                    "values", data
            );
            
            // This would use z-one's Google Sheets API when available
            // restTemplate.put("/api/z-one/sheets/" + spreadsheetId + "/values", updateRequest);
            
            log.info("Mock data populated for sheet: {} with {} rows", spreadsheetId, data.size());
            
        } catch (Exception e) {
            log.error("Error populating sheet data", e);
            throw new RuntimeException("Failed to populate sheet data", e);
        }
    }

    private void applySheetFormatting(String spreadsheetId, GoogleSheetsExportService.CapacityTrackingSheet sheet) {
        try {
            // Apply formatting similar to the screenshot:
            // 1. Header row formatting
            // 2. Color coding for status indicators (Y = Green, N = Red)
            // 3. Freeze header row
            // 4. Set column widths
            
            List<Map<String, Object>> formatRequests = new ArrayList<>();
            
            // Header formatting
            formatRequests.add(createHeaderFormat());
            
            // Status column formatting (Y/N indicators)
            formatRequests.add(createStatusFormat(sheet));
            
            // Freeze first row
            formatRequests.add(createFreezeFormat());
            
            // Column width adjustments
            formatRequests.add(createColumnWidthFormat());
            
            Map<String, Object> batchUpdateRequest = Map.of(
                    "requests", formatRequests
            );
            
            // This would use z-one's Google Sheets API when available
            // restTemplate.postForEntity("/api/z-one/sheets/" + spreadsheetId + "/batchUpdate", 
            //                           batchUpdateRequest, Map.class);
            
            log.info("Mock formatting applied to sheet: {}", spreadsheetId);
            
        } catch (Exception e) {
            log.error("Error applying sheet formatting", e);
        }
    }

    private Map<String, Object> createHeaderFormat() {
        return Map.of(
                "repeatCell", Map.of(
                        "range", Map.of(
                                "startRowIndex", 0,
                                "endRowIndex", 1
                        ),
                        "cell", Map.of(
                                "userEnteredFormat", Map.of(
                                        "backgroundColor", Map.of(
                                                "red", 0.9,
                                                "green", 0.9,
                                                "blue", 0.9
                                        ),
                                        "textFormat", Map.of(
                                                "bold", true
                                        )
                                )
                        ),
                        "fields", "userEnteredFormat(backgroundColor,textFormat)"
                )
        );
    }

    private Map<String, Object> createStatusFormat(GoogleSheetsExportService.CapacityTrackingSheet sheet) {
        // This would create conditional formatting for Y (Green) and N (Red) cells
        // Similar to the color coding in your screenshot
        
        return Map.of(
                "addConditionalFormatRule", Map.of(
                        "rule", Map.of(
                                "ranges", Arrays.asList(
                                        Map.of("startColumnIndex", 5, "endColumnIndex", 5 + sheet.getTimelineWeeks().size() * 2)
                                ),
                                "booleanRule", Map.of(
                                        "condition", Map.of(
                                                "type", "TEXT_EQ",
                                                "values", Arrays.asList(Map.of("userEnteredValue", "Y"))
                                        ),
                                        "format", Map.of(
                                                "backgroundColor", Map.of(
                                                        "red", 0.8,
                                                        "green", 1.0,
                                                        "blue", 0.8
                                                )
                                        )
                                )
                        ),
                        "index", 0
                )
        );
    }

    private Map<String, Object> createFreezeFormat() {
        return Map.of(
                "updateSheetProperties", Map.of(
                        "properties", Map.of(
                                "gridProperties", Map.of(
                                        "frozenRowCount", 1
                                )
                        ),
                        "fields", "gridProperties.frozenRowCount"
                )
        );
    }

    private Map<String, Object> createColumnWidthFormat() {
        // Set specific column widths similar to your screenshot
        return Map.of(
                "updateDimensionProperties", Map.of(
                        "range", Map.of(
                                "dimension", "COLUMNS",
                                "startIndex", 0,
                                "endIndex", 15
                        ),
                        "properties", Map.of(
                                "pixelSize", 120
                        ),
                        "fields", "pixelSize"
                )
        );
    }

    private String getColumnLetter(int columnNumber) {
        String result = "";
        while (columnNumber > 0) {
            columnNumber--;
            result = (char) ('A' + columnNumber % 26) + result;
            columnNumber /= 26;
        }
        return result;
    }

    private String generateMockSheetResponse(GoogleSheetsExportService.CapacityTrackingSheet sheet, 
                                           List<List<Object>> data) {
        // Generate a mock response with the data structure for testing
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("spreadsheetId", "mock_" + System.currentTimeMillis());
        mockResponse.put("title", sheet.getTitle());
        mockResponse.put("generatedDate", sheet.getGeneratedDate().toString());
        mockResponse.put("rowCount", data.size());
        mockResponse.put("columnCount", data.get(0).size());
        mockResponse.put("url", "https://docs.google.com/spreadsheets/d/mock_" + System.currentTimeMillis());
        
        // Add summary statistics
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTeams", sheet.getTeamData().size());
        summary.put("totalMembers", sheet.getTeamData().values().stream()
                .mapToInt(List::size)
                .sum());
        summary.put("timelineWeeks", sheet.getTimelineWeeks().size());
        
        mockResponse.put("summary", summary);
        
        // Add sample data preview (first 5 rows)
        List<List<Object>> preview = data.stream()
                .limit(5)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        mockResponse.put("dataPreview", preview);
        
        log.info("Generated mock sheet response: {}", mockResponse.get("spreadsheetId"));
        return mockResponse.toString();
    }

    public Map<String, Object> getSheetSummary() {
        try {
            GoogleSheetsExportService.CapacityTrackingSheet sheet = exportService.generateCapacityTrackingSheet();
            List<List<Object>> data = exportService.convertToSheetsData(sheet);
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("title", sheet.getTitle());
            summary.put("generatedDate", sheet.getGeneratedDate());
            summary.put("totalTeams", sheet.getTeamData().size());
            summary.put("totalRows", data.size());
            summary.put("totalColumns", data.get(0).size());
            summary.put("timelineWeeks", sheet.getTimelineWeeks().size());
            
            // Team breakdown
            Map<String, Integer> teamBreakdown = new HashMap<>();
            for (Map.Entry<String, List<GoogleSheetsExportService.CapacityRow>> entry : sheet.getTeamData().entrySet()) {
                teamBreakdown.put(entry.getKey(), entry.getValue().size());
            }
            summary.put("teamBreakdown", teamBreakdown);
            
            // Timeline info
            List<String> timeline = sheet.getTimelineWeeks().stream()
                    .map(week -> week.getMonthName() + " " + week.getWeekNumber())
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            summary.put("timeline", timeline);
            
            return summary;
            
        } catch (Exception e) {
            log.error("Error generating sheet summary", e);
            throw new RuntimeException("Failed to generate sheet summary", e);
        }
    }

    public List<List<Object>> exportCapacityData() {
        try {
            GoogleSheetsExportService.CapacityTrackingSheet sheet = exportService.generateCapacityTrackingSheet();
            return exportService.convertToSheetsData(sheet);
        } catch (Exception e) {
            log.error("Error exporting capacity data", e);
            throw new RuntimeException("Failed to export capacity data", e);
        }
    }
    
    public List<List<Object>> exportCapacityDataByDateRange(LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Exporting capacity data for date range: {} to {}", startDate, endDate);
            GoogleSheetsExportService.CapacityTrackingSheet sheet = exportService.generateCapacityTrackingSheetByDateRange(startDate, endDate);
            return exportService.convertToSheetsData(sheet);
        } catch (Exception e) {
            log.error("Error exporting capacity data by date range", e);
            throw new RuntimeException("Failed to export capacity data by date range", e);
        }
    }
    
    public Map<String, Object> getSheetSummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Getting sheet summary for date range: {} to {}", startDate, endDate);
            GoogleSheetsExportService.CapacityTrackingSheet sheet = exportService.generateCapacityTrackingSheetByDateRange(startDate, endDate);
            
            int totalRows = 0;
            int totalTeams = sheet.getTeamData().size();
            int totalMembers = sheet.getTeamData().values().stream()
                    .mapToInt(List::size)
                    .sum();
            
            for (List<GoogleSheetsExportService.CapacityRow> teamRows : sheet.getTeamData().values()) {
                totalRows += teamRows.size();
            }
            
            return Map.of(
                    "title", sheet.getTitle(),
                    "generatedDate", sheet.getGeneratedDate().toString(),
                    "totalRows", totalRows + 1, // +1 for header
                    "totalTeams", totalTeams,
                    "totalMembers", totalMembers,
                    "timelineWeeks", sheet.getTimelineWeeks().size(),
                    "dateRange", Map.of("startDate", startDate.toString(), "endDate", endDate.toString()),
                    "status", "Generated with date filter"
            );
        } catch (Exception e) {
            log.error("Error getting sheet summary by date range", e);
            throw new RuntimeException("Failed to get sheet summary by date range", e);
        }
    }
} 