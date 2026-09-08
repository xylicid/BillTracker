# BillTracker

Updated BillTracker Android app.

## Changes
- Full due dates stored as dates instead of day-only values.
- Due-date field supports `MM/DD/YY` typing and a Material calendar picker.
- Existing v2 bills are migrated automatically; their old due day is mapped into the current month.
- Monthly billing-period reset now advances recurring due dates into the new billing period.
- Bills show useful status text such as Due today, Due tomorrow, Due in N days, or Overdue.
- Due-date sorting uses the full date.
- Existing bill names, amounts, paid status, positions, income, reset-day setting, and sort setting are preserved.

## Build
Open the project in Android Studio and sync/build normally. The included GitHub Actions workflow can also build a debug APK and upload it as an artifact; it installs Gradle 8.7 directly so the project does not depend on a checked-in Gradle wrapper.
