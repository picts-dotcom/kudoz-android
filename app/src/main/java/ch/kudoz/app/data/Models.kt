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
