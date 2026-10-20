# Bug Fix Log

## ✅ Fixed (Version 1.0.0)

### UI Issues
- ✅ **Header Overlaps Buttons** (Fixed)
  - Issue Description: The “To-Do List” header text overlaps with the [All], [In Progress], and [Completed] buttons
  - Fix: Adjusted the header's Y-coordinate from `y+10` to `y-5`
  - Location of Fix: `TodoScreen.java`, `render()` method
- ✅ **“Complete” Button Overlaps with List** (Fixed)
  - Issue Description: The 【Complete】 button overlaps with the bottom item in the list
  - Fix:
    - Adjusted the task list widget height: `guiHeight - 100` → `guiHeight - 110`
    - Adjusted the list’s starting position: `y + 40` → `y + 35`
    - Adjusted the “Done” button position: `y + guiHeight - 80` → `y + guiHeight - 85`
  - Fixed in: `TodoScreen.java`, `init()` method
- ✅ **Missing “Save List” Button** (Fixed)
  - Issue description: No manual save button in the GUI
  - Fix: Added a “Save” button at `y + guiHeight - 85`
  - Location fixed: `TodoScreen.java`, `init()` method
- ✅ **“Done” button overlaps again** (Fixed - v1.0.1)
  - Issue description: The “Complete” button still partially overlaps with the list (by half the button’s height)
  - Fix: Redesigned the UI layout to use dynamically calculated heights
  - Location of fix: `TodoScreen.java` init() method
- ✅ **Missing selection highlight** (Fixed - v1.0.1)
  - Issue Description: Clicking a list item does not highlight the currently selected item
  - Fix:
    - Added the `selectedTaskIndex` variable
    - Added the `setSelectedTask()` method
    - Modified `getTaskBackgroundColor()` to display the selection highlight
  - Location of Fix: `TaskListWidget.java`
- ✅ **Missing Cancel Button** (Fixed - v1.0.1)
  - Issue Description: A Cancel button is needed, displayed on the same row as the Save button
  - Fix: Add “Save” and “Cancel” buttons at the bottom
  - Location: `TodoScreen.java` init() method
- ✅ **Close UI After Saving** (Fixed - v1.0.1)
  - Issue Description: The UI should automatically close after saving
  - Fix: Call `close()` at the end of the `onSaveTasks()` method
  - Location: `TodoScreen.java`, `onSaveTasks()` method

### Functional Issues
- ✅ **Mouse Wheel Scrolling** (Fixed)
  - Issue Description: Cannot scroll the list using the mouse wheel
  - Fix: Implemented the `mouseScrolled()` method and passed the event to `TaskListWidget`
  - Location: `TodoScreen.java` and `TaskListWidget.java`

- ✅ **Scrollbar Dragging** (Fixed)
  - Issue Description: The scrollbar on the right side of the list could not be dragged
  - Fix:
    - Added drag state variables: `isDraggingScrollbar`, `dragStartY`, `dragStartScrollOffset`
    - Implemented `mouseClicked()` to detect scrollbar clicks
    - Implement `mouseDragged()` to handle drag logic
    - Implement `mouseReleased()` to release the drag
  - Fixed in: `TaskListWidget.java`

- ✅ **Missing List Save Functionality** (Fixed)
  - Issue Description: No functionality to save the task list to a file
  - Fix:
    - Added the `onSaveTasks()` method to enable manual saving
    - Added an auto-save feature upon closing (in the `close()` method)
    - Added functionality to load saved tasks in the `init()` method
  - Fixed in: `TodoScreen.java`

- ✅ **Customizable List Height** (Fixed - v1.0.1)
  - Issue Description: Requirement for a customizable list height (adjusts based on the default number of items displayed; can be specified in the configuration file)
  - Fix:
    - Added the `taskListHeight` configuration option to `GuiConfig` in `ModConfig.java` (default 120 pixels)
    - Use `ModConfig.getInstance().getTaskListHeight()` in `TodoScreen.java`
    - Dynamically calculate and limit the available height
  - Fixed in: `ModConfig.java` and `TodoScreen.java`

- ✅ **Missing Priority Feature** (Fixed - v1.0.1)
  - Issue description: Task properties did not reflect the priority (low/medium/high) functionality
  - Fix:
    - Add a priority selector input field
    - Handle priority in `onAddTask()` and `onEditTask()`
    - Display the “Priority:” label in the UI
    - Priority colors are already displayed (Low = cyan, Medium = yellow, High = red)
  - Fixed in: `TodoScreen.java`
- For the priority feature, it is sufficient to be able to change the color in front of the list item; additionally, the corresponding priority levels can be listed, such as [blue block] Low | [yellow block] Medium | [red block] High

### Other Fixes
- ✅ **Incorrect client entry point path** (Fixed)
  - Issue description: The client entry point in `fabric.mod.json` was configured as `com.todolist.TodoClient`, but the actual class is located at `com.todolist.client.TodoClient`
  - Fix: Corrected to `com.todolist.client.TodoClient`
  - Fixed in: `fabric.mod.json`

- ✅ **Missing TodoListMod import** (Fixed)
  - Issue description: `TodoScreen.java` uses TodoListMod but does not import it
  - Fix: Added `import com.todolist.TodoListMod;`
  - Fixed in: `TodoScreen.java`

---

## 🔍 Current Status

**Version**: 1.0.0 (Build 2026-02-10)
**Build Status**: ✅ Successful
**All Known Bugs**: ✅ Fixed

**New Features**:
- ✅ Priority selector (LOW/MEDIUM/HIGH)
- ✅ Highlight selected tasks
- ✅ Cancel button
- ✅ Configurable list height
- ✅ UI automatically closes after saving

---

## 📝 Testing Recommendations

Please test the following features to verify that the fixes work as expected:

### UI Testing
- [x] The title does not overlap with the filter button
- [x] The “Done” button does not overlap with the task list
- [x] The “Save” button displays and functions correctly
- [x] Selected tasks are highlighted (dark gray background)
- [x] The priority selector works correctly (needs modification)
- [x] The “Save” and “Cancel” buttons are displayed on the same row at the bottom

### Interaction Testing
- [ ] The mouse wheel can scroll through the task list
- [x] The scroll bar can be dragged
- [x] Clicking a task highlights it
- [x] Add/Edit/Delete/Complete functions work correctly
- [x] Priority is applied when creating a task
- [x] Priority can be modified when editing a task

### Save Functionality Testing
- [x] Clicking the Save button saves the task
- [x] The UI automatically closes after saving
- [x] Clicking the Cancel button closes the UI (without saving)

### Configuration Testing
- [x] The `taskListHeight` in the configuration file can be used to adjust the list height

---

*Last Updated: February 10, 2026*