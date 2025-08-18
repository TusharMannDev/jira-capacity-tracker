# Jira Capacity Tracker

A comprehensive Spring Boot application that provides daily scrum summaries, real-time tracking of Jira issues, team capacity planning, and Google Sheets integration for your team. Perfect for daily standups, resource planning, and project management.

## Features

### 📊 **Dashboard & Tracking**
- **Real-time Jira Integration**: Fetches data from your Jira instance via REST API
- **Daily Scrum Dashboard**: Beautiful, responsive dashboard showing issue status breakdown
- **Employee-wise Scrum Views**: Individual daily standup summaries for each team member
- **Team Member Views**: Individual dashboards for each team member
- **Project Views**: Project-specific dashboards and metrics
- **Overdue Issue Tracking**: Highlights overdue issues and blockers
- **Status Tracking**: Tracks issues across Dev, QA, UAT, and Done stages
- **Sprint Management**: Sprint-specific views and metrics
- **Story Point Tracking**: Track completed story points and velocity

### 🎯 **Capacity Planning** (NEW!)
- **Team Capacity Overview**: Visual representation of team member workload and utilization
- **Resource Availability Forecasting**: 30-day forecast showing when team members will be free
- **Overload Detection**: Automatic identification of overloaded team members
- **Timeline Planning**: Weekly timeline view with task allocation
- **Utilization Tracking**: Individual capacity percentages and availability dates
- **Task Assignment Management**: Link Jira issues to team members with time estimates

### 📈 **Google Sheets Integration** (NEW!)
- **Automated Sheet Generation**: Create professional capacity tracking Google Sheets
- **Timeline View**: Weekly columns with Y/N status indicators (like your existing sheets)
- **Color Coding**: Green (Y) for active tasks, Red (N) for blocked tasks
- **CSV Export**: Download capacity data for offline analysis
- **Data Preview**: Interactive modal with color-coded capacity data
- **Professional Formatting**: Headers, frozen rows, conditional formatting

### 🔄 **Automation & Sync**
- **Automated Sync**: Scheduled synchronization with Jira every 5 minutes
- **Email Notifications**: Daily summary emails sent automatically
- **Manual Sync**: On-demand sync buttons for immediate updates
- **Environment Variables**: Secure configuration without hardcoded credentials

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Access to Jira instance (https://jira.mypaytm.com/)
- Jira API token (for authentication)

## Setup Instructions

### 1. Clone and Build

```bash
git clone <repository-url>
cd jira-dashboard-tracker
mvn clean install
```

### 2. Configure Environment Variables

Create a `.env` file in the project root or set these environment variables:

```bash
# Jira Configuration
JIRA_USERNAME=your-jira-username
JIRA_API_TOKEN=your-jira-api-token
JIRA_PROJECT_KEYS=PROJ1,PROJ2,PROJ3
JIRA_JQL_FILTER="project in (PROJ1, PROJ2, PROJ3) AND status != Closed"

# Email Configuration (Optional)
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password
DAILY_SUMMARY_RECIPIENTS=team@paytm.com
```

### 3. Jira API Token Setup

1. Go to your Jira instance (https://jira.mypaytm.com/)
2. Click on your profile picture → **Profile Settings**
3. Go to **Security** → **Create API token**
4. Give it a name (e.g., "Dashboard Tracker")
5. Copy the generated token and use it as `JIRA_API_TOKEN`

### 4. Configure Project Keys

Update the `JIRA_PROJECT_KEYS` environment variable with your actual project keys. You can find these in your Jira projects.

### 5. Customize JQL Filter (Optional)

Modify the `JIRA_JQL_FILTER` to match your team's workflow. Examples:

```sql
# All active issues
"project in (PROJ1, PROJ2) AND status != Closed"

# Current sprint issues
"project in (PROJ1, PROJ2) AND sprint in openSprints()"

# Issues assigned to specific team
"project in (PROJ1, PROJ2) AND assignee in (user1, user2, user3)"
```

## Running the Application

### Development Mode

```bash
mvn spring-boot:run
```

### Production Mode

```bash
mvn clean package
java -jar target/jira-dashboard-tracker-0.0.1-SNAPSHOT.jar
```

The application will be available at: http://localhost:8080

## Usage

### Main Dashboard

- **URL**: http://localhost:8080/dashboard
- Shows overall team status, overdue issues, and recent updates
- Auto-refreshes every 5 minutes
- Manual sync button to fetch latest data from Jira

### Daily Scrum Dashboard

- **URL**: http://localhost:8080/scrum
- Shows employee-wise daily standup summaries
- Perfect for daily scrum meetings
- Tracks what each person worked on, completed, and plans to work on
- Highlights blockers and overdue issues

### Individual Employee Scrum View

- **URL**: http://localhost:8080/scrum/{assignee}
- Replace `{assignee}` with the team member's name
- Detailed daily standup information for individual employees
- Shows recent activity, completed work, and planned tasks

### Team Member Dashboard

- **URL**: http://localhost:8080/team/{assignee}
- Replace `{assignee}` with the team member's name
- Shows individual workload and active issues

### Project Dashboard

- **URL**: http://localhost:8080/project/{projectKey}
- Replace `{projectKey}` with the project key
- Shows project-specific metrics and issues

### API Endpoints

- `GET /api/summary` - Get dashboard summary data
- `GET /api/scrum` - Get all employees scrum summary
- `GET /api/scrum/{assignee}` - Get individual employee scrum summary
- `GET /api/team/{assignee}` - Get team member summary
- `GET /api/project/{projectKey}` - Get project summary
- `POST /api/sync` - Manually sync data from Jira

## Configuration

### Application Properties

Key configuration options in `application.properties`:

```properties
# Jira Configuration
jira.base-url=https://jira.mypaytm.com
jira.username=${JIRA_USERNAME}
jira.api-token=${JIRA_API_TOKEN}
jira.project-keys=${JIRA_PROJECT_KEYS}
jira.jql-filter=${JIRA_JQL_FILTER}

# Scheduling
app.daily-summary.cron=0 0 9 * * ?  # Daily at 9 AM
app.scheduling.enabled=true

# Database (H2 for development)
spring.datasource.url=jdbc:h2:file:./data/jira-dashboard
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

### Customizing Status Mapping

The application maps Jira statuses to internal statuses. You can customize this in:

- `IssueStatus.java` - Add new statuses or modify mappings
- `IssueType.java` - Add new issue types

## Database

The application uses H2 database for development. Data is stored in `./data/jira-dashboard.mv.db`.

### H2 Console

Access the database console at: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/jira-dashboard`
- Username: `sa`
- Password: `password`

## Email Notifications

### Setup Gmail App Password

1. Enable 2-factor authentication on your Gmail account
2. Generate an App Password: Google Account → Security → App Passwords
3. Use this password as `EMAIL_PASSWORD`

### Customizing Email Content

Modify `EmailService.java` to customize email content and recipients.

## Troubleshooting

### Common Issues

1. **Authentication Error**
   - Verify Jira username and API token
   - Check if the user has access to the specified projects

2. **No Data Displayed**
   - Check JQL filter syntax
   - Verify project keys are correct
   - Check application logs for API errors

3. **Email Not Sending**
   - Verify Gmail credentials
   - Check if 2FA is enabled and app password is used
   - Review email configuration

### Logs

Check application logs for detailed error information:

```bash
tail -f logs/application.log
```

## Development

### Project Structure

```
src/main/java/com/paytm/jiradashboard/
├── controller/          # REST controllers
├── model/              # Data models and entities
├── repository/         # Data access layer
├── service/            # Business logic
└── JiraDashboardTrackerApplication.java
```

### Adding New Features

1. **New Status**: Add to `IssueStatus.java` and update mapping logic
2. **New Metrics**: Extend `DashboardService.java` with new methods
3. **New Views**: Create new templates in `templates/` directory
4. **New API**: Add endpoints in `DashboardController.java`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review application logs
3. Create an issue in the repository

---

**Happy Scrumming! 🚀** 