# TodoList GUI Redesign Concept Diagram and Development Plan

## 1. Objectives
This round of updates will not be a pixel-for-pixel replica of Microsoft To Do, but rather will align with its core strengths in information organization: calm, focused, seamless, and low-stress.

This redesign will focus on addressing the following issues:
- Clearly distinguish between “Personal Space” and “Team Space”
- Ensure more consistent functionality for the “Save,” “Cancel,” “Settings,” “Filter,” and “Details” drawers on the main interface
- Revert task priority labeling to align with existing product conventions, rather than temporarily using `P1 / P2` text
- Unify the visual language across the main interface, pop-ups, settings pages, and HUD
- Provide a stable baseline for future GUI refactoring and testing

## 2. Design Positioning

### 2.1 Experience Keywords
- Lightness: Minimize heavy outlines and excessive functional clutter
- Focus: The central task area always takes center stage
- Continuity: Create a seamless flow for adding, checking off, switching, and viewing details
- Familiarity: Model the reading rhythm after Microsoft To Do
- Consistency: The main interface, pop-ups, and HUD share a unified product language

### 2.2 Visual Tone
- The outer layer retains the feel of a floating workspace overlaid on the game screen
- The left-side navigation, central main area, and right-side drawer form three tiers of brightness
- Accent colors are used only for selected states, primary buttons, and priority indicators
- Clear typographic hierarchy: four levels—headings, groups, body text, and supplementary information
- The HUD maintains the same color semantics as the main interface but features higher transparency and a more compact layout

## 3. Core Structural Decisions

### 3.1 Space Switching
The space switcher is located below the title on the left side of the main interface:
- `Personal Space | Team Space`
- The view area updates immediately after switching spaces
- Personal space displays only `My`
- Team space displays only `Unassigned / All / My`

### 3.2 Three-Pane Structure of the Main Interface
- Left navigation pane: Space, View, Project
- Central main task area: Title, Search, Filter, Task List, Quick Add
- Right-side details drawer: Task context editing

### 3.3 Fixed Area Rules
- Project action buttons on the left are fixed at the bottom of the sidebar
- The Quick Add input field in the center is fixed at the bottom of the main content area
- `Save`, `Cancel`, and `Configure` are permanently displayed in the top-right corner of the main interface

### 3.4 Scroll Area Rules
Vertical scrollbars must appear in the following areas when content extends beyond the visible range:
- Left-side item list
- Main interface task list
- Right-side details drawer content area
- Member list in Project Settings
- Member list in “Assign to Others”
- Member list in “Add Members”
- Configuration list on the Configuration page

Scrollbar Requirements:
- Use narrow vertical scrollbars
- Keep the track subtle; the slider should be visible but not overly conspicuous
- Clearly separate fixed areas from scrollable areas to prevent bottom buttons or top controls from being scrolled away

## 4. Detailed Rules for the Main Interface

### 4.1 Left Navigation Area
Structure:
- Top: App name
- Below the top: Space switcher
- Middle: View list
- Lower middle: Project search and project list
- Bottom fixed area: `New Project`, `Edit`, `Delete`

Rules:
- The three project buttons must have a distinct button style; they must not be presented as plain text
- `New Project` is the primary call-to-action button
- `Delete` uses a warning color
- When the project list is scrolling, the position of the bottom button area remains fixed

### 4.2 Central Main Task Area
Structure:
- Top left: Current list title + status summary
- Top right: `Save`, `Cancel`, `Configure`
- Search and filter area: Search box + single dropdown filter button
- List area: Unfinished items at the top, completed items collapsed at the bottom
- Fixed input area at the bottom: Quick-add input field with a `+` icon

Rules:
- Currently, only one filter option is retained: `By Priority v`
- The filter option should be designed as an expandable dropdown to allow for the addition of more conditions in the future
- The quick-add field is always fixed at the bottom
- The task list itself is a scrollable area; a vertical scrollbar appears when the list is too tall

### 4.3 Task Entries
The priority display for task entries reverts to the project’s existing style; the text `P1 / P2 / P3` is no longer used.

Each task includes:
- Priority color block
- Checkbox
- Task title
- Additional information: tags, projects, assignees, etc.

Priority color block rules:
- High priority: red
- Medium priority: yellow
- Low priority: green
- The color block is positioned in front of the checkbox
- Completed tasks retain their priority color block, but the overall contrast is reduced

Display rules:
- The currently selected item uses a more prominent background color
- Text contrast is reduced for completed items
- Long titles may be truncated in the list but are displayed in full in the details view

### 4.4 Right-Side Details Drawer
Structure:
- First row: Dedicated row for the close button
- Second row: Task title area
- Starting from the third row: Status, project information, description, tags, and action buttons

Rules:
- The close button occupies its own row and does not share a row with the title
- The title is clickable to select and enter edit mode; this behavior must be preserved in both documentation and implementation
- The save button is removed from the drawer and uniformly placed in the top-right corner of the main interface
- Tags are treated as independent, editable items and appear on a separate line or as a standalone card
- In Team Spaces, the `Claim Task`, `Abandon Task`, and `Assign to Another User` buttons use a single vertical button per line to prevent them from being squeezed out on smaller screens
- These three team action buttons are not displayed in Personal Spaces
- The content area of the details drawer is a scrollable region; the title and close button do not scroll along with the description, tags, and button area

### 4.5 Completed Group
Rules:
- Collapsed by default: `> N items completed`
- When expanded: `v N items completed`
- Completed items share the same task entry structure as uncompleted items and retain priority color blocks

## 5. Main Interface Diagram

### 5.1 Personal Space (Default State)
```text
+------- -----------------------------------------------------------------------------------------------------------+
| TodoList                                          My                                                   [Save][Cancel][Settings] |
|----------------------------------------- -------------------------------------------------------------------------|
| Navigation Area                                  | Main Task Area                                                                  |
|----------------------------------------|------------ ---------------------------------------------------------------|
| Space                                    | My                                                                      |
| [Personal Space] [Team Space]                   | Today’s Recommendation: Complete 3 High-Priority Tasks First                                             |
|--------------------------- -------------| [Search Tasks.................................................] [By Priority v]    |
| View                                    |---------------------------------------------------------------------------|
| [●] My                                | Unfinished                                                                     |
|----------------------------------------| [Red] [ ] Unify HUD styles                                 [UI]           |
| Item                                    | [Yellow] [ ] Handle HUD synchronization boundaries in single-player mode                        [Bug]          |
| [Search Tasks............................]   | [Green] [ ] Adjust Default Empty State Text                                [UX]           |
| [★] Default Tasks                       12  |--------------------------------------------------- ------------------------|
| [ ] Construction Plans                         4  | > 3 items completed                                                              |
| [ ] Survival Organization                         7  |                                                                           |
|----------------------------------------|------------------------------------- --------------------------------------|
| [New Project]      [Edit]      [Delete]     | [+] Quickly add a task...                                                        |
+------------------------------------------------------------------- -----------------------------------------------+
```

### 5.2 Team Space Default State
```text
+------------------------------------------------------------------------------------------ ------------------------+
| TodoList                                          Development Team                                                 [Save][Cancel][Configure] |
|----------------------------------------------------------------------------------------------------------------- -|
| Navigation Area                                  | Main Task Area                                                                  |
|----------------------------------------|---------------------------------------------------------------------------|
| Space                                    | Development Team                                                                     |
| [Personal Space
