# Google Sheets Integration for Capacity Tracking

## Overview

This feature enables automatic generation of Google Sheets capacity tracking sheets directly from your Jira data, similar to the format shown in your screenshot. It creates professional, formatted spreadsheets with timeline views, resource allocation, and color-coded status indicators.

## 🎯 Features Implemented

### ✅ **Sheet Structure (Matching Your Screenshot)**
- **POD/Team Organization** - Groups tasks by teams (Retail, Backend, Frontend, etc.)
- **Lead Assignment** - Shows team leads and task owners
- **Task Type Classification** - Tech, Support, Business categories
- **Timeline View** - Weekly columns (May W3, May W4, June W1, etc.)
- **Status Indicators** - Y (Green) for active/completed, N (Red) for blocked
- **Resource Allocation** - Shows who's working on what
- **Duration Tracking** - Man-days, start/end dates, QA drop dates
- **Pipeline Management** - Tasks in pipeline for each resource

### ✅ **Export Options**
1. **Google Sheets Export** - Creates formatted Google Sheets (when z-one is working)
2. **CSV Export** - Downloads CSV file for offline use
3. **Data Preview** - Modal view with color-coded data
4. **Summary Statistics** - Team breakdown and timeline information

### ✅ **Color Coding & Formatting**
- **Header Formatting** - Bold, gray background
- **Status Colors** - Green for Y (Yes/Active), Red for N (No/Blocked)
- **Frozen Headers** - First row stays visible when scrolling
- **Column Widths** - Optimized for readability
- **Conditional Formatting** - Automatic color coding based on values

## 🏗️ Technical Architecture

### Services Created

1. **GoogleSheetsExportService**
   - Generates capacity tracking data structure
   - Creates timeline weeks (next 12 weeks)
   - Maps Jira issues to sheet format
   - Converts to Google Sheets-compatible format

2. **GoogleSheetsIntegrationService**
   - Handles Google Sheets API integration
   - Applies formatting and color coding
   - Manages sheet creation and updates
   - Provides mock responses when API unavailable

### Data Models

```java
CapacityTrackingSheet {
    - String title
    - LocalDate generatedDate
    - List<WeekColumn> timelineWeeks
    - Map<String, List<CapacityRow>> teamData
}

CapacityRow {
    - String pod (team)
    - String lead
    - String taskType (Tech/Support/Business)
    - String currentTask
    - String resource
    - int manDays
    - LocalDate startDate, endDate, qaDrop
    - String tasksInPipeline
    - Map<String, TaskStatus> weeklyStatus
}

WeekColumn {
    - String monthName
    - String weekNumber
    - LocalDate startDate, endDate
}
```

## 🚀 API Endpoints

### Google Sheets Operations

```bash
# Create Google Sheets capacity tracker
POST /api/sheets/create-capacity-tracker
Response: { "status": "success", "result": "sheet_id", "message": "..." }

# Get capacity summary
GET /api/sheets/capacity-summary
Response: { "title": "...", "totalTeams": 3, "timelineWeeks": 12, ... }

# Export raw data
GET /api/sheets/export-capacity-data
Response: [[header1, header2, ...], [row1col1, row1col2, ...], ...]

# Export as CSV download
GET /api/sheets/export-capacity-csv
Response: CSV file download

# Preview data with summary
GET /api/sheets/preview-capacity-data
Response: { "summary": {...}, "preview": [...], "totalRows": 25 }
```

## 🎨 UI Integration

### New Dashboard Buttons
- **🟢 Export to Google Sheets** - Creates formatted Google Sheets
- **📊 Export as CSV** - Downloads CSV file
- **👁️ Preview Data** - Shows data modal with color coding
- **🔄 Sync with Jira** - Updates data from Jira

### Data Preview Modal
- **Color-coded table** - Y (Green), N (Red) indicators
- **Responsive design** - Works on all screen sizes
- **Summary statistics** - Team counts, timeline info
- **Scrollable content** - Handles large datasets

## 📊 Data Mapping Logic

### Jira to Sheet Conversion

| Jira Field | Sheet Column | Logic |
|------------|--------------|-------|
| Project Team | POD | From TeamMember.team |
| Assignee | Lead/Resource | From JiraIssue.assignee |
| Issue Type | Task Type | Story→Business, Task→Tech, Bug→Support |
| Summary | Current Tasks | Direct mapping |
| Story Points | M Days | Conversion: 1SP=0.5day, 2SP=1day, etc. |
| Due Date | End Date | From TaskAssignment.estimatedCompletionDate |
| Status | Weekly Y/N | IN_PROGRESS→Y, BLOCKED→N, etc. |

### Timeline Generation

```java
// Generates 12 weeks starting from current week
LocalDate startDate = LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1);
for (int i = 0; i < 12; i++) {
    WeekColumn week = WeekColumn.builder()
        .monthName(weekStart.format("MMMM"))
        .weekNumber("W" + weekStart.format("w"))
        .startDate(weekStart)
        .endDate(weekStart.plusDays(6))
        .build();
}
```

### Status Color Logic

```java
// Y (Green) for active work
if (taskOverlapsWeek && taskActive) return TaskStatus.IN_PROGRESS; // → Y (Green)

// N (Red) for blocked tasks
if (taskBlocked) return TaskStatus.BLOCKED; // → N (Red)

// Empty for non-applicable weeks
if (!taskOverlapsWeek) return TaskStatus.NOT_APPLICABLE; // → Empty
```

## 🔧 Configuration

### Story Point to Hours Mapping
Modify in `GoogleSheetsExportService.estimateHoursFromStoryPoints()`:

```java
Map<Integer, Integer> storyPointToHours = Map.of(
    1, 4,   // 0.5 day
    2, 8,   // 1 day
    3, 16,  // 2 days
    5, 32,  // 4 days
    8, 64,  // 8 days
    13, 104 // 13 days
);
```

### Timeline Range
Change the number of weeks in `generateTimelineWeeks(12)` call.

### Task Type Mapping
Update in `mapIssueTypeToTaskType()`:

```java
return switch (issueType) {
    case STORY -> "Business";
    case TASK -> "Tech";
    case BUG -> "Support";
    // Add more mappings as needed
};
```

## 🧪 Testing the Integration

### 1. Start the Application
```bash
mvn spring-boot:run
```

### 2. Access Capacity Dashboard
```
http://localhost:8080/capacity
```

### 3. Test Export Features

#### A. Preview Data
1. Click **"Preview Data"** button
2. Modal shows formatted table with:
   - Color-coded Y/N indicators
   - Team breakdown
   - Timeline columns
   - Summary statistics

#### B. CSV Export
1. Click **"Export as CSV"** button
2. Downloads file: `capacity-tracker-YYYY-MM-DD.csv`
3. Open in Excel/Google Sheets for verification

#### C. Google Sheets Export
1. Click **"Export to Google Sheets"** button
2. **Current behavior**: Shows mock response with data structure
3. **When z-one works**: Will create actual Google Sheet with formatting

### 4. API Testing
```bash
# Test capacity summary
curl http://localhost:8080/api/sheets/capacity-summary

# Test data preview
curl http://localhost:8080/api/sheets/preview-capacity-data

# Test CSV export
curl -o capacity.csv http://localhost:8080/api/sheets/export-capacity-csv
```

## 📋 Sample Output Structure

### Generated Sheet Headers
```
POD | Lead | Task Type | Current Tasks (In Dev) | Resource | May | W20 | May | W21 | June | W22 | M Days | Start Date | End Date | QA Drop | Tasks in pipeline
```

### Sample Data Row
```
Backend | John Smith | Tech | COP-1001: API development | John Smith | Y | | Y | | | | 5 | 2024-08-10 | 2024-08-15 | 2024-08-15 | COP-1006, COP-1010
```

## 🔄 Z-One Integration Points

### When z-one Google Sheets API Works

The system is designed to seamlessly integrate with z-one. Replace the mock implementations in `GoogleSheetsIntegrationService`:

```java
// Current mock implementation
String mockSheetId = "1" + System.currentTimeMillis();

// Replace with actual z-one call
ResponseEntity<Map> response = restTemplate.postForEntity(
    "/api/z-one/sheets/create", createRequest, Map.class);
String realSheetId = (String) response.getBody().get("spreadsheetId");
```

### Required z-one Endpoints
- `POST /api/z-one/sheets/create` - Create new sheet
- `PUT /api/z-one/sheets/{id}/values` - Update sheet data
- `POST /api/z-one/sheets/{id}/batchUpdate` - Apply formatting

## 🎯 Business Benefits

### For Project Managers
- **Visual Timeline Planning** - See resource allocation across weeks
- **Capacity Forecasting** - Identify when team members will be free
- **Bottleneck Detection** - Spot overloaded resources (red indicators)
- **Stakeholder Reporting** - Professional Google Sheets for sharing

### For Team Leads
- **Resource Optimization** - Balance workload across team members
- **Sprint Planning** - See capacity for upcoming sprints
- **Dependency Management** - Track tasks in pipeline
- **Progress Tracking** - Visual Y/N indicators for task status

### For Stakeholders
- **Executive Dashboards** - High-level capacity overview
- **Collaborative Planning** - Shared Google Sheets for team input
- **Data-Driven Decisions** - Accurate timelines based on current workload
- **Transparency** - Clear visibility into team capacity and commitments

## 🚀 Future Enhancements

1. **Real-time Sync** - Automatic sheet updates when Jira changes
2. **Custom Templates** - Different sheet formats for different teams
3. **Resource Planning** - Vacation/leave integration
4. **Capacity Alerts** - Email notifications for overloads
5. **Historical Tracking** - Archive sheets for trend analysis

This integration provides a complete solution for generating professional capacity tracking sheets that match your existing format while adding powerful automation and real-time data integration. 