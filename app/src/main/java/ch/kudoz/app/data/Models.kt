package ch.kudoz.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val color: String = "lavender",
    val icon: String = "sparkles",
    @SerialName("private_owner") val privateOwner: String? = null,
)

@Serializable
data class Membership(
    val role: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val workspaces: Workspace? = null,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val color: String = "mint",
    val icon: String = "sparkles",
    val description: String? = null,
    val position: Double = 0.0,
    val archived: Boolean = false,
    @SerialName("workspace_id") val workspaceId: String,
)

@Serializable
data class BoardColumn(
    val id: String,
    val name: String,
    val color: String? = null,
    val kind: String = "",
    val position: Double = 0.0,
    @SerialName("is_done") val isDone: Boolean = false,
    @SerialName("project_id") val projectId: String,
)

@Serializable
data class Card(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("column_id") val columnId: String,
    @SerialName("project_id") val projectId: String,
    val position: Double = 0.0,
    val done: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("waiting_text") val waitingText: String? = null,
    @SerialName("follow_locked") val followLocked: Boolean = false,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("follows_id") val followsId: String? = null,
    @SerialName("milestone_id") val milestoneId: String? = null,
    @SerialName("waiting_for") val waitingFor: String? = null,
    @SerialName("recurrence_of") val recurrenceOf: String? = null,
    @SerialName("card_assignees") val assignees: List<AssigneeRef> = emptyList(),
)

@Serializable
data class AssigneeRef(@SerialName("user_id") val userId: String)

@Serializable
data class Profile(
    val id: String,
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val initials: String? = null,
    @SerialName("initials_bg") val initialsBg: String? = null,
    @SerialName("initials_ink") val initialsInk: String? = null,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: fullName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
    val shortLabel: String get() = initials?.takeIf { it.isNotBlank() }
        ?: label.split(" ", ".", "-").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

@Serializable
data class ChecklistItem(
    val id: String,
    val body: String,
    val done: Boolean = false,
    val position: Double = 0.0,
    @SerialName("assigned_to") val assignedTo: String? = null,
)

@Serializable
data class Comment(
    val id: String,
    val body: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NewComment(
    @SerialName("card_id") val cardId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    val mentions: List<String> = emptyList(),
)

@Serializable
data class NewCard(
    @SerialName("project_id") val projectId: String,
    @SerialName("column_id") val columnId: String,
    val title: String,
    val position: Double,
    val done: Boolean,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class NewChecklistItem(
    @SerialName("card_id") val cardId: String,
    @SerialName("project_id") val projectId: String,
    val body: String,
    val position: Double,
)

@Serializable
data class Notification(
    val id: String,
    val title: String,
    val kind: String = "",
    val link: String? = null,
    @SerialName("workspace_id") val workspaceId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Plan(
    val id: String,
    @SerialName("card_id") val cardId: String? = null,
    val scope: String,
    @SerialName("planned_date") val plannedDate: String,
)

@Serializable
data class NewPlan(
    @SerialName("user_id") val userId: String,
    @SerialName("card_id") val cardId: String,
    val scope: String,
    @SerialName("planned_date") val plannedDate: String,
)

@Serializable
data class ProjectRef(
    val id: String,
    val name: String,
    val color: String = "mint",
    @SerialName("workspace_id") val workspaceId: String,
)

/** Eigene offene Aufgabe (über card_assignees oder card_helpers) mit Projekt. */
@Serializable
data class MyCardRow(val cards: MyCard? = null)

@Serializable
data class MyCard(
    val id: String,
    val title: String,
    val done: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("follow_locked") val followLocked: Boolean = false,
    @SerialName("project_id") val projectId: String,
    val projects: ProjectRef? = null,
)

@Serializable
data class Bounty(
    val id: String,
    val title: String,
    val description: String? = null,
    val kind: String = "bounty",
    val status: String = "open",
    val reward: Int? = null,
    @SerialName("reward_image") val rewardImage: Int? = null,
    @SerialName("claimed_by") val claimedBy: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("bounty_helpers") val helpers: List<AssigneeRef> = emptyList(),
)

@Serializable
data class Shoutout(
    val id: String,
    val emoji: String,
    val message: String? = null,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NewShoutout(
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val emoji: String,
    val message: String?,
)

@Serializable
data class MemberRow(@SerialName("user_id") val userId: String, val role: String)

@Serializable
data class LedgerEntry(val id: String, val image: Int, val delta: Int)

@Serializable
data class InboxItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val message: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_initials") val senderInitials: String? = null,
    @SerialName("sender_bg") val senderBg: String? = null,
    @SerialName("sender_ink") val senderInk: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("source_color") val sourceColor: String? = null,
    val checklist: kotlinx.serialization.json.JsonElement? = null,
    val comments: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class Member(
    val id: String,
    @SerialName("user_id") val userId: String,
    val role: String,
)

@Serializable
data class ArchivedCard(
    val id: String,
    val title: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class Milestone(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val done: Boolean = false,
    val position: Double = 0.0,
)

@Serializable
data class MilestoneCard(val id: String, val done: Boolean = false, @SerialName("milestone_id") val milestoneId: String? = null)

@Serializable
data class TransferTarget(
    val id: String,
    val name: String,
    val color: String = "sky",
    @SerialName("can_place") val canPlace: Boolean = false,
)

@Serializable
data class InboxCount(@SerialName("workspace_id") val workspaceId: String, val waiting: Int)

@Serializable
data class FleetBounty(
    val id: String,
    val title: String,
    val kind: String = "bounty",
    @SerialName("reward_image") val rewardImage: Int? = null,
    @SerialName("workspace_id") val workspaceId: String,
)

@Serializable
data class NewAssignee(
    @SerialName("card_id") val cardId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("project_id") val projectId: String,
)

@Serializable
data class NewBounty(
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("project_id") val projectId: String?,
    @SerialName("card_id") val cardId: String?,
    val kind: String,
    @SerialName("reward_source") val rewardSource: String = "random",
    val title: String,
    val description: String?,
)


@Serializable
data class Reaction(@SerialName("user_id") val userId: String, val emoji: String)

@Serializable
data class NewReaction(
    @SerialName("card_id") val cardId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String,
)

@Serializable
data class FollowCard(
    val id: String,
    val title: String,
    val done: Boolean = false,
    @SerialName("follow_locked") val followLocked: Boolean = false,
)

@Serializable
data class NewFollowCard(
    @SerialName("project_id") val projectId: String,
    @SerialName("column_id") val columnId: String,
    val title: String,
    val position: Double,
    @SerialName("follows_id") val followsId: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class Recurrence(
    val kind: String,
    val every: Int = 1,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month_day") val monthDay: Int? = null,
    val nth: Int? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    val paused: Boolean = false,
    val rotation: List<String> = emptyList(),
    @SerialName("rotation_idx") val rotationIdx: Int = 0,
)

@Serializable
data class WorkspaceRow(
    val id: String,
    val name: String,
    val color: String = "lavender",
    val icon: String = "sparkles",
    @SerialName("auto_archive_days") val autoArchiveDays: Int = 14,
)

@Serializable
data class CardDocument(
    val id: String,
    val url: String,
    val title: String? = null,
    val note: String? = null,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class NewDocument(
    @SerialName("card_id") val cardId: String,
    val url: String,
    val title: String?,
    val note: String?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class HelperRow(@SerialName("user_id") val userId: String)

@Serializable
data class ProjectFull(
    val id: String,
    val name: String,
    val color: String = "mint",
    val icon: String = "sparkles",
    val description: String? = null,
    val status: String = "active",
    val archived: Boolean = false,
    val position: Double = 0.0,
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("is_board_duties") val isBoardDuties: Boolean = false,
)

@Serializable
data class NewColumn(@SerialName("project_id") val projectId: String, val name: String, val position: Double, val kind: String = "open")

@Serializable
data class NewMilestone(
    @SerialName("project_id") val projectId: String,
    val title: String,
    @SerialName("due_date") val dueDate: String?,
    val description: String?,
    val position: Double,
)

@Serializable
data class Invitation(
    val id: String,
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("workspace_name") val workspaceName: String,
    val color: String = "lavender",
    val role: String,
)

@Serializable
data class PendingInvite(val id: String, val email: String, val role: String)

@Serializable
data class NewInvite(
    @SerialName("workspace_id") val workspaceId: String,
    val email: String,
    val role: String,
    @SerialName("invited_by") val invitedBy: String,
)

@Serializable
data class DirectoryShip(
    val id: String,
    val name: String,
    val color: String = "sky",
    val description: String? = null,
    @SerialName("allow_join") val allowJoin: Boolean = false,
    @SerialName("request_status") val requestStatus: String? = null,
)

@Serializable
data class JoinRequest(val id: String, @SerialName("user_id") val userId: String, val message: String? = null, @SerialName("created_at") val createdAt: String)

@Serializable
data class Visibility(
    val visible: Boolean = false,
    val description: String? = null,
    @SerialName("allow_join") val allowJoin: Boolean = false,
    @SerialName("show_roles") val showRoles: List<String> = emptyList(),
    @SerialName("inbox_user_ids") val inboxUserIds: List<String> = emptyList(),
)

@Serializable
data class DutySettings(val enabled: Boolean = false, @SerialName("guests_see") val guestsSee: Boolean = false)

@Serializable
data class SearchHit(val id: String, val title: String, @SerialName("project_id") val projectId: String, val done: Boolean = false)

@Serializable
data class SuperWs(
    val id: String,
    val name: String,
    val color: String = "lavender",
    @SerialName("member_count") val memberCount: Int = 0,
    val admins: List<String> = emptyList(),
    @SerialName("is_demo") val isDemo: Boolean = false,
)

@Serializable
data class SuperAdminRow(val email: String, val permanent: Boolean = false)
