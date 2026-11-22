package ch.kudoz.app.data

import ch.kudoz.app.KudozApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Zugriff auf das Kudoz-Backend. Alle Rechte prüft Postgres (RLS und RPCs) –
 * die App nutzt dieselben Tabellen und RPCs wie kudoz.ch.
 */
object Repo {
    private val db get() = KudozApp.supabase

    val userId: String? get() = db.auth.currentUserOrNull()?.id
    val email: String? get() = db.auth.currentUserOrNull()?.email

    private val profileCache = mutableMapOf<String, Profile>()

    suspend fun workspaces(): List<Pair<Workspace, String>> {
        val uid = userId ?: return emptyList()
        return db.from("workspace_members")
            .select(Columns.raw("role, sort_order, workspaces(id, name, color, icon, private_owner)")) {
                filter { eq("user_id", uid) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<Membership>()
            .mapNotNull { m -> m.workspaces?.let { it to m.role } }
    }

    suspend fun projects(workspaceId: String): List<Project> =
        db.from("projects").select {
            filter { eq("workspace_id", workspaceId); eq("archived", false) }
            order("position", Order.ASCENDING)
        }.decodeList()

    suspend fun project(id: String): Project =
        db.from("projects").select { filter { eq("id", id) } }.decodeSingle()

    suspend fun workspace(id: String): Workspace =
        db.from("workspaces").select(Columns.raw("id, name, color, icon, private_owner")) { filter { eq("id", id) } }.decodeSingle()

    suspend fun columns(projectId: String): List<BoardColumn> =
        db.from("board_columns").select {
            filter { eq("project_id", projectId) }
            order("position", Order.ASCENDING)
        }.decodeList()

    suspend fun cards(projectId: String): List<Card> =
        db.from("cards").select(Columns.raw("*, card_assignees(user_id)")) {
            filter { eq("project_id", projectId); exact("archived_at", null) }
            order("position", Order.ASCENDING)
        }.decodeList()

    suspend fun card(cardId: String): Card =
        db.from("cards").select(Columns.raw("*, card_assignees(user_id)")) {
            filter { eq("id", cardId) }
        }.decodeSingle()

    suspend fun profiles(ids: Collection<String>): Map<String, Profile> {
        val missing = ids.filter { it !in profileCache }
        if (missing.isNotEmpty()) {
            db.from("profiles").select(Columns.raw("id, email, display_name, full_name, initials, initials_bg, initials_ink")) {
                filter { isIn("id", missing) }
            }.decodeList<Profile>().forEach { profileCache[it.id] = it }
        }
        return ids.mapNotNull { profileCache[it]?.let { p -> it to p } }.toMap()
    }

    suspend fun checklist(cardId: String): List<ChecklistItem> =
        db.from("card_checklist_items").select {
            filter { eq("card_id", cardId) }
            order("position", Order.ASCENDING)
        }.decodeList()

    suspend fun comments(cardId: String): List<Comment> =
        db.from("card_comments").select(Columns.raw("id, body, author_id, created_at")) {
            filter { eq("card_id", cardId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun addComment(cardId: String, body: String) {
        val uid = userId ?: return
        db.from("card_comments").insert(NewComment(cardId, uid, body.trim()))
    }

    suspend fun addCard(projectId: String, column: BoardColumn, title: String) {
        val uid = userId ?: return
        db.from("cards").insert(
            NewCard(projectId, column.id, title.trim(), System.currentTimeMillis().toDouble(), column.isDone, uid)
        )
    }

    suspend fun addChecklistItem(card: Card, body: String, position: Double) {
        db.from("card_checklist_items").insert(NewChecklistItem(card.id, card.projectId, body.trim(), position))
    }

    suspend fun claim(cardId: String, claim: Boolean) =
        rpc("card_claim") { put("_card", cardId); put("_claim", claim) }

    suspend fun setDone(cardId: String, done: Boolean) =
        rpc("card_set_done") { put("_card", cardId); put("_done", done) }

    suspend fun move(cardId: String, columnId: String, position: Double) =
        rpc("card_move") { put("_card", cardId); put("_column", columnId); put("_position", position) }

    suspend fun toggleChecklist(itemId: String, done: Boolean) =
        rpc("checklist_toggle") { put("_item", itemId); put("_done", done) }

    suspend fun setWaiting(cardId: String, text: String) =
        rpc("card_set_waiting") { put("_card", cardId); put("_text", text) }

    suspend fun notifications(): List<Notification> {
        val uid = userId ?: return emptyList()
        return db.from("notifications").select(Columns.raw("id, title, kind, link, workspace_id, read_at, created_at")) {
            filter { eq("user_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(200)
        }.decodeList()
    }

    suspend fun markRead(id: String) {
        db.from("notifications").update({ set("read_at", Instant.now().toString()) }) {
            filter { eq("id", id) }
        }
    }

    suspend fun markAllRead() {
        val uid = userId ?: return
        db.from("notifications").update({ set("read_at", Instant.now().toString()) }) {
            filter { eq("user_id", uid); exact("read_at", null) }
        }
    }

    private suspend fun rpc(name: String, args: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        db.postgrest.rpc(name, buildJsonObject(args))
    }
}
