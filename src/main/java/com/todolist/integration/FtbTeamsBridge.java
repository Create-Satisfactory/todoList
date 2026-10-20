package com.todolist.integration;

import com.todolist.TodoListCommon;
import com.todolist.TodoListNeoForge;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Directly integrates FTB Teams with TodoList Team Projects.
 * Every FTB Team automatically gets a synchronized "FICSIT Team" project with live membership & rank updates.
 */
public final class FtbTeamsBridge {

    public static final String FTB_TEAM_PROJECT_PREFIX = "ftbteam_";
    private static boolean eventsRegistered = false;

    private FtbTeamsBridge() {}

    /**
     * Call once during TodoListNeoForge constructor to listen for live in-game FTB Team changes.
     */
    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        TeamEvent.CREATED.register(event -> syncTeamObject(event.getTeam()));
        TeamEvent.PROPERTIES_CHANGED.register(event -> syncTeamObject(event.getTeam()));
        TeamEvent.OWNERSHIP_TRANSFERRED.register(event -> syncTeamObject(event.getTeam()));
        TeamEvent.PLAYER_JOINED_PARTY.register(event -> syncTeamObject(event.getTeam()));
        TeamEvent.PLAYER_LEFT_PARTY.register(event -> {
            syncTeamObject(event.getTeam());
            if (event.getPlayer() != null && event.getPlayer().getServer() != null) {
                syncPlayerFtbTeamProject(event.getPlayer().getServer(), event.getPlayer());
            }
        });
    }

    public static boolean isAvailable() {
        return true;
    }

    /**
     * Ensures the player's current FTB Team has an up-to-date shared TEAM Project and returns its Project ID.
     */
    public static String syncPlayerFtbTeamProject(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
        return teamOpt.map(team -> syncTeamWithServer(server, team)).orElse(null);
    }

    private static void syncTeamObject(Team team) {
        if (team == null || !FTBTeamsAPI.api().isManagerLoaded()) return;
        TeamManager mgr = FTBTeamsAPI.api().getManager();
        MinecraftServer server = mgr.getServer();
        if (server != null) {
            syncTeamWithServer(server, team);
        }
    }

    public static String syncTeamWithServer(MinecraftServer server, Team team) {
        if (server == null || team == null) return null;

        try {
            UUID teamId = team.getId();
            String cleanName = team.getShortName();
            Set<UUID> members = team.getMembers();

            String projectId = FTB_TEAM_PROJECT_PREFIX + teamId;
            ProjectManager projectManager = TodoListNeoForge.getProjectManager();
            Project teamProject = projectManager.getProject(projectId);

            boolean isNew = false;
            if (teamProject == null) {
                UUID ownerId = team.getOwner();
                teamProject = new Project(
                        "FICSIT Team: " + cleanName,
                        Project.Scope.TEAM,
                        ownerId != null ? ownerId.toString() : null
                );
                teamProject.setId(projectId);
                teamProject.setAllowMemberCreate(true);
                isNew = true;
            } else {
                teamProject.setName("FICSIT Team: " + cleanName);
                teamProject.setAllowMemberCreate(true);
            }

            Set<String> validMemberUuids = new HashSet<>();
            for (UUID memberUuid : members) {
                String uuidStr = memberUuid.toString();
                validMemberUuids.add(uuidStr);

                TeamRank rank = team.getRankForPlayer(memberUuid);
                Project.ProjectRole role = mapRankToRole(rank);
                String memberName = resolvePlayerName(server, memberUuid);

                if (role == Project.ProjectRole.PROJECT_MANAGER) {
                    teamProject.setOwnerUuid(uuidStr);
                }
                teamProject.addMember(uuidStr, role, memberName);
            }

            // Remove members who left the FTB Party/Team
            List<String> staleMembers = new ArrayList<>();
            for (String existingUuid : teamProject.getMembers().keySet()) {
                if (!validMemberUuids.contains(existingUuid)) {
                    staleMembers.add(existingUuid);
                }
            }
            for (String stale : staleMembers) {
                teamProject.removeMember(stale);
            }

            if (isNew) {
                projectManager.addProject(teamProject);
            } else {
                projectManager.updateProject(teamProject);
            }

            TodoListCommon.getProjectStorage().saveTeamProjects(
                    projectManager.getProjectsByScope(Project.Scope.TEAM)
            );

            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (members.contains(online.getUUID())) {
                    ProjectPackets.syncProjectsToPlayer(online);
                }
            }

            return projectId;
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.error("Failed to sync FTB Team project", e);
            return null;
        }
    }

    private static Project.ProjectRole mapRankToRole(TeamRank rank) {
        if (rank == null) return Project.ProjectRole.MEMBER;
        if (rank.isAtLeast(TeamRank.OWNER)) {
            return Project.ProjectRole.PROJECT_MANAGER;
        }
        if (rank.isAtLeast(TeamRank.OFFICER)) {
            return Project.ProjectRole.LEAD;
        }
        return Project.ProjectRole.MEMBER;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID memberUuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(memberUuid);
        if (online != null) {
            return online.getName().getString();
        }
        if (server.getProfileCache() != null) {
            var profileOpt = server.getProfileCache().get(memberUuid);
            if (profileOpt.isPresent()) {
                return profileOpt.get().getName();
            }
        }
        return memberUuid.toString().substring(0, 8);
    }
}