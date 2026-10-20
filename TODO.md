# Project To-Do Overview

This document tracks the overall status of changes in the current project. It is organized into four categories: `In Progress`, `Pending Sync`, `Planned`, and `Completed`, to facilitate future scheduling, branch synchronization, and regression testing.

Last Updated: `2026-09-11`

## In Progress

- None

## Pending Sync

- None

## Planned

- [feat] In-game event-triggered quests
- [feat] Support for linked projection material lists
- [feat] Support for linked mechanical power material lists
- [feat] Support for FTB team integration
- [feat] Support for AI-generated to-do items
- [feat] Support for setting team project tasks to be repeatedly claimed or completed
- [feat] Support for syncing personal projects across save files

## Completed

- [feat] Support for subtasks
- [fix] Fixed an issue where, in team tasks, the parent task’s assignment status was not aggregated based on its direct subtasks in the “Pending” / “My” views and claim operations
- [fix] Optimized HUD subtask display; added a subtask show/hide toggle and corrected bottom statistics that were not included in subtask counts
- [fix] Fixed list scroll positioning offset when switching tasks after expanding a parent task
- [fix] Fixed the parent task name in the subtask details area overflowing the right panel when it was too long
- [feat] Added support for one-click clearing of completed items
- [feat] Added an auto-save toggle for GUI editing
- [fix] Fixed an issue in H2 mode where failed connections were not reused after a TCP lifecycle switch, preventing storage unavailability when “exiting a published LAN session and then re-entering an unpublished single-player session”
- [fix] Optimized HUD display by replacing the expanded-state bottom text “...x more entries” with a fixed “To-Do x | Done x” count
- [fix] Optimized text and prompts in the To-Do interface input fields to clarify the meanings of “Quick Add,” “Title Edit,” and “Tag Input”
- [feat] Completed the H2 storage migration workflow, covering tasks, projects, player project statuses, and compatibility with legacy data migration
- [feat] Completed H2 TCP external access capabilities, supporting basic configuration, port fallback, and account permission control
- [feat] Completed H2 maintenance capabilities, including health checks, backups, maintenance locks, and schema upgrades
- [feat] Completed the H2 query optimization phase, adding query services and corresponding offline validation
- [fix] Fixed the flickering issue during quick HUD switching in H2 mode
- [fix] Restored performance optimization for saving personal tasks and switching back to personal projects from team projects in H2 mode
- [fix] Optimized performance to resolve stuttering when switching back to a personal project after saving a team project in H2 mode
- [fix] Fixed an issue where the database became unavailable due to a missing driver at runtime after switching to H2 mode in NeoForge
- [fix] Added multilingual documentation comments for `storageBackend` and `h2BackupOnStart`
- [chore] Added ignore rules for local build scripts and end-of-line rules for the repository
- [feat] Added support for parent tasks to batch-complete or cancel all direct subtasks with a single click
- [feat] Added support for parent tasks in team projects to assign or claim all direct subtasks with a single click (processes only remaining unassigned items)
- [fix] Fixed the display of batch operation scope and completed subtasks in the “My View” section of team projects
- [refactor] Enforced the use of the H2 storage backend; deprecated and disabled the NBT storage mode to reduce maintenance costs associated with dual modes
- [chore] Removed the H2 storage mode prompt and command prompt sent to administrators upon entering the world

## Maintenance Conventions

- New requests are set to `Planned` by default
- Move to `In Progress` once actual implementation begins
- After development is complete and verified on the current branch, move to `Pending Sync`
- Move from `Pending Sync` to `Completed` after synchronization with other maintenance branches is finished
- If priority needs to be assigned later, prefix the item with `P0 / P1 / P2`