# Todo List Mod for Minecraft

A simple yet powerful Minecraft to-do list mod that supports both single-player and multiplayer modes.

A simple and powerful to-do list mod for Minecraft, supporting both single-player and multiplayer.

## 🌟 Features

### v1.4.1 Capability Overview
- ✅ Minecraft 1.21.1 multi-loader support (Fabric / Forge / NeoForge), covering both client and server
- ✅ In-game GUI task management: CRUD, priority, tags, filter & search, drag-and-drop sorting of unfinished tasks, cleanup of completed tasks in the current project, and optional auto-save of details / In-game GUI task management: supports create, read, update, and delete (CRUD), priority, tags, filtering and searching, drag-and-drop sorting of unfinished tasks, one-click cleanup of completed tasks in the current project, and optional auto-save of details
- ✅ Personal and team projects: project hints, member management, join approval, and all-player team mode / Personal and team projects: supports project hints, member management, join approval, and an optional all-server team task mode
- ✅ Expanded command system: `/todo` covers tasks, projects, HUD, join flow, admin operations, and H2 maintenance / Command system enhancements: `/todo` now covers tasks, projects, HUD, join flow, admin operations, and H2 maintenance access
- ✅ H2 storage backend: NBT/H2 switching, migration, SQL-backed query optimization, online backup, health checks, and TCP external access / H2 storage backend: Supports NBT/H2 switching, migration, SQL query optimization, online backup, health checks, and TCP external access
- ✅ HUD improvements: expand/collapse, visibility toggle, synchronized priority colors, hidden-count summary, and current-view sync / HUD enhancements: support for expand/collapse, visibility toggle, priority color blocks, hidden-count summary, and current-view sync
- ✅ Safer persistence and recovery: backup-aware persistence for tasks, projects, player project state, and configuration data / Safer persistence and recovery: tasks, projects, player project state, and configuration data now uniformly use a backup-protected persistence chain

More details / Further information:
- [FEATURES_EN.md](FEATURES_EN.md)
- [ROADMAP_EN.md](ROADMAP_EN.md)
- [CHANGELOG_EN.md](CHANGELOG_EN.md)
- [docs/storage-backend-h2-guide.md](docs/storage-backend-h2-guide.md)
- [docs/h2-external-client-guide.md](docs/h2-external-client-guide.md)

## 📸 Screenshots

### Singleplayer / Single-Player

![image-20260426004104159](https://qiniuyun.emptycity.top/typora-img/image-20260426004104159.png)

![image-20260426004123960](https://qiniuyun.emptycity.top/typora-img/image-20260426004123960.png)

### Multiplayer / Multiplayer

#### Op View / Administrator View

![image-20260426004230993](https://qiniuyun.emptycity.top/typora-img/image-20260426004230993.png)

![image-20260426004306031](https://qiniuyun.emptycity.top/typora-img/image-20260426004306031.png)

![image-20260426004324754](https://qiniuyun.emptycity.top/typora-img/image-20260426004324754.png)

![image-20260426004442973](https://qiniuyun.emptycity.top/typora-img/image-20260426004442973.png)

#### Player View / Standard Player Perspective

![image-20260426004411293](https://qiniuyun.emptycity.top/typora-img/image-20260426004411293.png)

![image-20260426004508553](https://qiniuyun.emptycity.top/typora-img/image-20260426004508553.png)

![image-20260426004535554](https://qiniuyun.emptycity.top/typora-img/image-20260426004535554.png)

![image-20260426004556405](https://qiniuyun.emptycity.top/typora-img/image-20260426004556405.png)

## 🚀 Installation

### Requirements
- Minecraft 1.21.1
- Java 21+
- **Fabric**: Fabric Loader `0.18.1+` & Fabric API `0.116.9+1.21.1`
- **Forge**: Forge `52.1.10+`
- **NeoForge**: NeoForge `21.1.219+`

### Steps / Steps

1. Download the latest mod JAR file that matches your loader / Download the latest mod JAR file that matches your loader
2. Place it in your `mods` folder / Place the file in the `mods` folder
3. Launch Minecraft / Launch Minecraft
4. Press the **K** key in-game to open the to-do list / Press the **K** key in-game to open the to-do list

## 🎮 Usage / How to Use

### Key Bindings / Key Bindings
- **K** - Open To-Do List / Open To-Do List
- **H** - Toggle HUD (expand/collapse) / Expand or collapse the HUD
- **J** - Toggle HUD visibility / Show or hide the HUD

### Projects / Projects
- The todo GUI is project-based: tasks you create belong to the currently selected project in the left sidebar. / The to-do interface is organized by “projects”: tasks you create are assigned to the project currently selected in the left sidebar.
- Use the top button in the sidebar to switch between **Personal Projects** and **Team Projects** (team projects require server support). / The top button in the sidebar lets you switch between **Personal Projects** and **Team Projects** (team projects require server support).
- Click a project to switch context; starred projects are sorted to the top. / Click a project to switch to that project; starred projects are automatically sorted to the top.
- Click ★/☆ to star/unstar a project (used by the HUD “Starred Projects” source). / Click ★/☆ to star or unstar a project (used by the HUD “Starred Projects” source).
- Use search prefixes and dropdown hints to locate projects faster in large lists. / You can use search prefixes and dropdown hints to find projects more quickly in large lists.
- Use the Add/Edit/Delete buttons in the sidebar to manage projects (deletion requires confirmation). / Use the Add/Edit/Delete buttons in the sidebar to manage projects (deletion requires confirmation).

### Creating Tasks / Creating Tasks
1. Open the to-do list with the **K** key / Press the **K** key to open the to-do list
2. Select a project in the left sidebar / Select a project from the left sidebar
3. Enter the task title and description / Enter the task title and description
4. Click the **Add** button (or press Enter when the title field is focused) / Click the **Add** button (or press Enter when the title field is focused)

### Managing Tasks / Managing Tasks
- Click on a task to select it / Click on a task to select it
- **Complete** - Mark as completed by clicking the checkbox / Click the checkbox to mark it as completed
- **Drag Sort** - Unfinished tasks can be reordered directly by dragging / **Drag Sort** - Unfinished tasks can be reordered directly by dragging
- **Right-Click Menu** - Right-click a task to open the context menu for more actions: / **Right-Click Menu** - Right-click a task to open the context menu and perform additional actions:
  - **Edit** - Modify the task
  - **Delete** - Remove the task
  - **Priority** - Change task priority (High/Medium/Low)
- Use filter buttons to view specific tasks
- Use the search box to filter by title, description, or tags
- Use the priority buttons (High/Medium/Low) to filter quickly
- Completed tasks in the current project can be cleared with a single click after a second confirmation / Completed tasks in the current project can be cleared with a single click after a second confirmation
- GUI detail editing can optionally auto-save when the focus is lost, when switching tasks or projects, when using quick add, and when closing the window / GUI detail editing can optionally auto-save when the focus is lost, when switching tasks or projects, when using quick add, and when closing the window

### Team Tasks / Team Tasks
- Use the view buttons at the top of the GUI to switch between Personal and Team views: **Unassigned**, **All Assigned**, **Assigned to Me** / Use the view buttons at the top of the GUI to switch between Personal and Team views: **Unassigned**, **All Assigned**, **Assigned to Me**
- In team views, changes are applied only after clicking **Save**; the server will validate permissions and resolve conflicts
- After clicking **Save**, **Cancel**, or closing with **Esc** in team views, the client always resyncs team tasks from the server so that local unsaved edits are discarded and the list matches the server state / In team views, clicking **Save**, **Cancel**, or pressing **Esc** to close the interface causes the client to resync team tasks from the server; local unsaved changes are discarded, and the list remains consistent with the server
- Project members with the **MEMBER** role can only claim, abandon, and complete their own team tasks; **PROJECT_MANAGER/LEAD/OP** roles can fully manage team tasks. The “Assign Others” button is displayed only when you have the necessary permissions. / Project members with the **MEMBER** role can only claim, abandon, and complete their own team tasks; **Project Manager/Lead/OP** can fully manage team tasks. The “Assign Others” button is displayed only when you have permission.
- In team projects, adding new tasks is only enabled in the **Unassigned** view; other team views disable the Add button and the Enter-to-add function. / In team projects, only the **Unassigned** view allows adding new tasks; other team views disable the Add button and the Enter-to-add function.
- Team projects can optionally enable an all-player task mode for broader shared workflows. / Team projects can optionally enable an all-player task mode to accommodate more flexible shared collaboration workflows.

### HUD Config / HUD Configuration
- Open the todo GUI with **K**, then click the top-right **Config** button to open the configuration screen / Press **K** to open the todo interface, then click the **Config** button in the top-right corner to access the configuration screen
- Configure HUD width, maximum height, todo/done limits (0–30), default expanded state, whether to show when empty, default list view, list item source, and opacity / You can configure the HUD width, maximum height, number of todo/done items displayed (0–30), default expanded state, whether to display when empty, default list view, list item source, and opacity
- **Real-time View Sync** - The HUD task list automatically syncs with the current view filter in the main GUI (e.g., Personal/Team views, Priority filters) / **Real-time View Sync** - The HUD task list synchronizes its display in real time based on the current view filter in the main interface (e.g., Personal/Team views, Priority filters)
- HUD priority color blocks and hidden-count summaries are aligned with the current GUI semantics. / The HUD’s priority color blocks and hidden-count summaries are aligned with the current GUI semantics.
- In true single-player worlds, the HUD default list view is locked to **Personal** and cannot be changed; HUD team views are hidden / In true single-player worlds, the HUD’s default list view is fixed to **Personal** and cannot be changed; team views are not displayed in the HUD
- Drag the HUD preview rectangle to set a custom position; changes take effect after clicking **Save & Apply** / Drag the HUD preview rectangle in the configuration screen to set a custom position; changes take effect after clicking **Save & Apply**
- If you use Mod Menu, you can also open this configuration screen from the mod’s entry / If Mod Menu is installed, you can also open this configuration screen from Mod Menu

### Persistence / Persistence
- Tasks, projects, player project state, and config files now use a safer temporary-write-plus-backup-recovery workflow. / Tasks, projects, player project state, and config files now use a safer temporary-write-plus-backup-recovery workflow.
- When a main data file is corrupted, the mod can attempt to recover from the latest backup automatically. / When a main data file is corrupted, the mod will automatically attempt to recover from the latest backup.
- Local single-player personal-task files now keep local and player-specific copies in sync to reduce accidental rollback-like restores. / Local single-player personal-task files now synchronize local and player-specific copies to reduce issues similar to accidental rollbacks.
- The storage backend can switch between `NBT` and `H2`; H2 mode adds migration, TCP access, backup, health checks, and external tooling support. / The storage backend can switch between `NBT` and `H2`; H2 mode provides additional support for migration, TCP access, backup, health checks, and external tools.

### Commands
- `/todo` is the main command entry, with `/todolist` retained as an alias.
- Task commands support listing, pagination, quick add, done, remove, assign, claim, abandon, and batch cleanup. / Task commands support listing, pagination, quick add, done, remove, assign, claim, abandon, and batch cleanup.
- Project commands support create, remove, select, star, rename, and member management. / Project commands support create, remove, select, star, rename, and member management.
- H2 commands support status, backup, health checks, TCP restart, database reload, and password reset. / H2 commands support status checks, online backup, health checks, TCP restart, database reload, and password reset.


## 🛡️ Permission System

### Permission Center
The mod implements a unified server-side Permission Center that evaluates operations in team projects based on role, view scope, task status/assignment, and whether the player is a member of the project. OP always bypasses checks.
The mod implements a unified server-side Permission Center that evaluates operations in team projects based on role, current view scope, task status/assignment, and whether the player is a member of the project. OP always has full permissions.

### Roles
- **OP**: Server operators (OP)
- **PROJECT_MANAGER**: Project owner/manager, full project control
- **LEAD**: Project lead, elevated permissions
- **MEMBER**: Regular project member 

### View Scopes
- **PERSONAL**: Personal tasks
- **TEAM_UNASSIGNED**: Team tasks - Unassigned
- **TEAM_ALL**: Team tasks - All assigned
- **TEAM_ASSIGNED**: Team tasks - Assigned to me

### Key Rules
- Team operations require you to be a member of the project (non-members have read-only access).
- In team projects, **PROJECT_MANAGER/LEAD** can add, edit, delete, and assign tasks, and can toggle the completion status in all team views. / In team projects, **Project Manager/Lead** can add, edit, delete, and assign tasks, and can toggle the completion status in all team views.
- **MEMBER** can claim tasks in the **Unassigned** view and can abandon or complete tasks only when they are assigned to themselves (typically in the **Assigned to Me** view). / **Members** can only claim tasks in the **Unassigned** view; they can abandon or complete tasks only when those tasks are assigned to themselves (typically in the **Assigned to Me** view).
- Project editing and deletion are restricted to **PROJECT_MANAGER** (OP is always allowed). / Project editing and deletion are available only to **Project Managers** (OP is an exception).
- Member management: **PROJECT_MANAGER** can manage members and roles; **LEAD** can manage members except the project manager and themselves (OP is always allowed). / Member management: **Project Manager** can manage members and roles; **Lead** can manage members (but cannot manage the Project Manager or themselves) (OP is always allowed).

#### 1. PERSONAL (Personal View)
- All players can add, edit, delete, and mark personal tasks as complete. / All players can add, edit, delete, and mark personal tasks as complete.

#### 2. TEAM_UNASSIGNED (Team - Unassigned)
- PROJECT_MANAGER / LEAD: add/edit/delete tasks, assign others, toggle completion / Project Manager/Lead: can add, edit, delete, assign tasks to others, and toggle completion status
- MEMBER: claim unassigned tasks / Member: can claim unassigned tasks

#### 3. TEAM_ALL (Team - All Assigned)
- PROJECT_MANAGER / LEAD: edit/delete/reassign/toggle completion / Project Manager/Lead: Can edit, delete, reassign, and toggle completion
- MEMBER: view only / Member: View-only access

#### 4. TEAM_ASSIGNED (Team - Assigned to Me)
- PROJECT_MANAGER / LEAD: edit/delete/reassign/toggle completion / Project Manager/Lead: Can edit, delete, reassign, and mark as complete
- MEMBER: toggle completion and abandon for own assigned tasks / Member: Can mark “assigned to me” tasks as complete or abandon them

## 📜 Operation Logs / Operation Logs
- All valid team task operations (toggle completion, claim, abandon, assign to others, save with changes) are validated by the Permission Center on the server and then recorded in the server console logs in a unified `[TEAM_OP]` format that includes the user’s name, operation type, task ID, title, and change details. / All valid team task operations (toggling completion status, claiming, abandoning, assigning to others, and saving actual changes) are first validated by the Permission Center on the server and then recorded in the server logs in a unified `[TEAM_OP]` format, which includes the player’s name, operation type, task ID, title, and change details.

## 🛠️ Development

### Build from Source

Requires Java 21+ (The repository provides `build-with-java21.bat` to facilitate building on Windows)

```bash
# Clone the repository
git clone https://github.com/spacehouse/todoList.git
cd todoList

# Build the mod
./build-with-java21.bat clean build

# The JAR will be in build/libs/
```

### Release

- Releases are published by pushing a Git tag that starts with `v`, for example `v1.4.1`.
- Release names include the Minecraft version suffix, for example `TodoList-v1.4.1-mc1.21.1-release`. / Release names include the Minecraft version suffix, such as `TodoList-v1.4.1-mc1.21.1-release`
- The release workflow extracts notes from `CHANGELOG.md` based on the tag version. / The release workflow extracts release notes from `CHANGELOG.md` based on the tag version

### Project Structure / Project Structure

```text
todoList/
├── common/          # Shared game logic, GUI, data models, permissions / Shared game logic, GUI, data models, and permissions
├── fabric/          # Fabric bootstrap and networking adapter / Fabric bootstrap and networking adapter
├── forge/           # Forge bootstrap and networking adapter / Forge bootstrap and networking adapter
├── neoforge/        # NeoForge bootstrap and networking adapter / NeoForge bootstrap and networking adapter
├── build.gradle     # Multi-loader build entry / Multi-loader build entry
└── gradle.properties
```

## 📝 Roadmap / Development Roadmap

See:
- [ROADMAP.md](ROADMAP.md) / [ROADMAP_EN.md](ROADMAP_EN.md)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m ‘Add some AmazingFeature’`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License—see the [LICENSE](LICENSE) file for details.
This project is licensed under the MIT License—see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- The Fabric team for their excellent modding tools
- The Minecraft community for inspiration

## 📧 Contact

- GitHub Issues: [Report bugs](https://github.com/spacehouse/todoList/issues)
- Discord: [Join our server](https://discord.gg/uYHw9MHNe)

---

Made with ❤️ by spacehouse