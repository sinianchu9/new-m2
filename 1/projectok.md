# Project Status and Structure

## Overview
This repository contains an Android BLE SDK distribution with demo app, documentation, binary artifacts, and work instructions. The current state is primarily documentation and task-definition oriented; no implementation changes are present in this workspace beyond logs and task files.

## Structure Details (top level)
- apidoc/ : Generated Javadoc (entry at index.html per readme).
- Demo/ : Sample/demo app project used to integrate and validate the SDK.
- jar_core/ : Primary SDK deliverable packages (versioned jars/aar).
- jar_base/ : Base dependency package required by the SDK.
- jniLibs/ : JNI native libs (used by ECG feature).
- sdkdoc/ : SDK documentation and usage notes.
- yuanma/ : Source code dump (referenced as core technical reference).
- _tmp_vpprotocol/ : Temporary protocol work area (name suggests interim or extracted protocol artifacts).
- readme.md : SDK version + directory explanation + Kotlin build requirements + 1.x to 2.x enum migration notes.
- readme.txt : Development notes; points to develop-rule.txt and bug-rule.txt; defines demo as the core working directory and task1-5 as required steps.
- task1.md .. task5.md : Product-level UI/behavior specifications and issues list.
- develop-rule.txt : Product/UX-driven development principles and required output checklist.
- bug-rule.txt : Bug-fix scope/causality rules and minimal-change constraints.
- 555.txt : Bluetooth log capture (connection churn and timeouts).
- er.txt : Additional BLE log snippets.
- app-1.6.2.apk : Prebuilt application package.

## Progress Details
- Task definitions exist for four main UI pages:
  - task1.md: Entry/connection page spec (status card, device list, scan/connect/stop buttons, state-driven UI rules).
  - task2.md: Reminder settings page spec (global toggle, time window, vibration modes, interval control, and explicit apply/save rules).
  - task3.md: Data display page spec (sync action, activity/sleep cards, basic physiology cards, strict no-analysis/no-advice policy).
  - task4.md: Feature settings hub and subpages (screen-off time, find device, event reminders, alarms; strict state + write rules).

  - Raise-to-wake returns "feature not supported".
  - Screen-off time UI shows a value that does not match device state (function works, UI value wrong).
  - Find-device triggers a crash: AndroidRuntime IllegalStateException "Reply already submitted" from Flutter MethodChannel.
- Development and bugfix rules are captured in develop-rule.txt and bug-rule.txt; these mandate state-first design, explicit user-visible states, and minimal-change bugfixes with clear causality.
- BLE connection logs are present (555.txt, er.txt) but no code changes reflecting fixes are visible in this workspace.

## Notes / Constraints
- Kotlin build tooling is required for jar_core versions >= 2.1.38.15.
- jar_core may be AAR (>= 2.1.56.15) and jar_base version changes are noted in readme.md.
