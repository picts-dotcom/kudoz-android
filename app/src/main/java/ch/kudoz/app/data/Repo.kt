package ch.kudoz.app.data

import ch.kudoz.app.KudozApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
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
            // Gesperrte Folgeaufgaben erscheinen wie im Web nur in ihrer Vorgänger-Karte.
            filter { eq("project_id", projectId); exact("archived_at", null); eq("follow_locked", false) }
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

    // ---------- Mein Tag ----------

    suspend fun myOpenCards(): List<MyCard> {
        val uid = userId ?: return emptyList()
        val cols = Columns.raw("cards(id, title, done, due_date, archived_at, follow_locked, project_id, projects(id, name, color, workspace_id))")
        val a = db.from("card_assignees").select(cols) { filter { eq("user_id", uid) } }.decodeList<MyCardRow>()
        val h = runCatching { db.from("card_helpers").select(cols) { filter { eq("user_id", uid) } }.decodeList<MyCardRow>() }.getOrDefault(emptyList())
        return (a + h).mapNotNull { it.cards }.filter { !it.done && it.archivedAt == null && !it.followLocked }.distinctBy { it.id }
    }

    suspend fun plans(): List<Plan> {
        val uid = userId ?: return emptyList()
        return db.from("personal_plans").select(Columns.raw("id, card_id, scope, planned_date")) {
            filter { eq("user_id", uid) }
        }.decodeList()
    }

    suspend fun savePlan(cardId: String, existing: Plan?, target: Pair<String, String>?) {
        val uid = userId ?: return
        when {
            target == null -> existing?.let { p -> db.from("personal_plans").delete { filter { eq("id", p.id) } } }
            existing != null -> db.from("personal_plans").update({
                set("scope", target.first); set("planned_date", target.second); set("updated_at", Instant.now().toString())
            }) { filter { eq("id", existing.id) } }
            else -> db.from("personal_plans").insert(NewPlan(uid, cardId, target.first, target.second))
        }
    }

    // ---------- Bounties ----------

    suspend fun bounties(workspaceId: String): List<Bounty> =
        db.from("bounties").select(Columns.raw("id, title, description, kind, status, reward, reward_image, claimed_by, created_by, card_id, project_id, done_at, created_at, bounty_helpers(user_id)")) {
            filter { eq("workspace_id", workspaceId) }
            order("created_at", Order.DESCENDING)
            limit(300)
        }.decodeList()

    suspend fun bountyClaim(id: String, claim: Boolean) = rpc("bounty_claim") { put("_id", id); put("_claim", claim) }
    suspend fun bountyComplete(id: String) = rpc("bounty_complete") { put("_id", id) }

    // ---------- Shoutouts & Mitglieder ----------

    suspend fun shoutouts(workspaceId: String): List<Shoutout> =
        db.from("shoutouts").select(Columns.raw("id, emoji, message, from_id, to_id, created_at")) {
            filter { eq("workspace_id", workspaceId) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList()

    suspend fun sendShoutout(workspaceId: String, to: String, emoji: String, message: String) {
        val uid = userId ?: return
        db.from("shoutouts").insert(NewShoutout(workspaceId, uid, to, emoji, message.trim().ifBlank { null }))
    }

    suspend fun deleteShoutout(id: String) { db.from("shoutouts").delete { filter { eq("id", id) } } }

    suspend fun members(workspaceId: String): List<MemberRow> =
        db.from("workspace_members").select(Columns.raw("user_id, role")) {
            filter { eq("workspace_id", workspaceId) }
        }.decodeList()

    // ---------- Schatzkammer ----------

    /** Anzahl Exemplare je Bild (Summe der Buchungen, nur > 0) – wie src/lib/treasury.ts. */
    suspend fun treasury(): Map<Int, Int> {
        val uid = userId ?: return emptyMap()
        val all = mutableListOf<LedgerEntry>()
        var offset = 0L
        while (true) {
            val page = db.from("reward_ledger").select(Columns.raw("id, image, delta")) {
                filter { eq("user_id", uid) }
                order("id", Order.ASCENDING)
                range(offset, offset + 999)
            }.decodeList<LedgerEntry>()
            all += page
            if (page.size < 1000) break
            offset += 1000
        }
        return all.groupBy { it.image }.mapValues { (_, l) -> l.sumOf { it.delta } }.filterValues { it > 0 }
    }

    // ---------- Eingang ----------

    suspend fun inbox(workspaceId: String): List<InboxItem> =
        db.postgrest.rpc("ws_inbox", buildJsonObject { put("_ws", workspaceId) }).decodeList()

    suspend fun transferAccept(id: String, projectId: String, columnId: String?) =
        rpc("transfer_accept") {
            put("_id", id); put("_project", projectId)
            if (columnId != null) put("_column", columnId) else put("_column", kotlinx.serialization.json.JsonNull)
            put("_assignee", kotlinx.serialization.json.JsonNull)
        }

    suspend fun transferDecline(id: String, reason: String) = rpc("transfer_decline") { put("_id", id); put("_reason", reason) }
    suspend fun transferAsk(id: String, question: String) = rpc("transfer_ask") { put("_id", id); put("_question", question) }

    // ---------- Mitglieder ----------

    suspend fun memberList(workspaceId: String): List<Member> =
        db.from("workspace_members").select(Columns.raw("id, user_id, role")) {
            filter { eq("workspace_id", workspaceId) }
        }.decodeList()

    suspend fun setRole(memberId: String, role: String) {
        db.from("workspace_members").update({ set("role", role) }) { filter { eq("id", memberId) } }
    }

    // ---------- Archiv ----------

    suspend fun archived(projectId: String): List<ArchivedCard> =
        db.from("cards").select(Columns.raw("id, title, project_id, done_at, archived_at")) {
            filter { eq("project_id", projectId); filterNot("archived_at", FilterOperator.IS, "null") }
            order("archived_at", Order.DESCENDING)
            limit(500)
        }.decodeList()

    suspend fun setArchived(cardIds: List<String>, archive: Boolean) {
        db.postgrest.rpc("cards_archive", buildJsonObject {
            put("_cards", kotlinx.serialization.json.JsonArray(cardIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            put("_archive", archive)
        })
    }

    // ---------- Meilensteine ----------

    suspend fun milestones(projectId: String): List<Milestone> =
        db.from("milestones").select(Columns.raw("id, title, description, due_date, done, position")) {
            filter { eq("project_id", projectId) }
            order("position", Order.ASCENDING)
        }.decodeList()

    suspend fun milestoneCards(projectId: String): List<MilestoneCard> =
        db.from("cards").select(Columns.raw("id, done, milestone_id")) {
            filter { eq("project_id", projectId); filterNot("milestone_id", FilterOperator.IS, "null") }
        }.decodeList()

    suspend fun setMilestoneDone(id: String, done: Boolean) {
        db.from("milestones").update({ set("done", done) }) { filter { eq("id", id) } }
    }

    // ---------- Aufgaben senden ----------

    suspend fun transferTargets(sourceWs: String): List<TransferTarget> =
        db.postgrest.rpc("transfer_targets", buildJsonObject { put("_source_ws", sourceWs) }).decodeList()

    suspend fun transferSend(
        sourceWs: String, card: Card, targetWs: String, title: String, description: String, message: String,
        withChecklist: Boolean, withDue: Boolean, withComments: Boolean, keepMode: String, feedback: Boolean,
    ) = rpc("transfer_send") {
        put("_source_ws", sourceWs); put("_card", card.id); put("_target_ws", targetWs)
        put("_title", title); put("_description", description); put("_message", message)
        put("_with_checklist", withChecklist); put("_with_due", withDue); put("_with_comments", withComments)
        put("_keep_mode", keepMode); put("_feedback", feedback && keepMode == "copy")
        put("_project", kotlinx.serialization.json.JsonNull); put("_column", kotlinx.serialization.json.JsonNull)
    }

    // ---------- Flotte ----------

    suspend fun inboxCounts(): List<InboxCount> =
        runCatching { db.postgrest.rpc("my_inbox_counts").decodeList<InboxCount>() }.getOrDefault(emptyList())

    suspend fun openBounties(workspaceIds: List<String>): List<FleetBounty> {
        if (workspaceIds.isEmpty()) return emptyList()
        return db.from("bounties").select(Columns.raw("id, title, kind, reward_image, workspace_id")) {
            filter { isIn("workspace_id", workspaceIds); eq("status", "open") }
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList()
    }

    // ---------- Profil ----------

    suspend fun myProfile(): Profile? {
        val uid = userId ?: return null
        profileCache.remove(uid)
        return profiles(listOf(uid))[uid]
    }

    suspend fun updateProfile(name: String, initials: String?, bg: String?) {
        val uid = userId ?: return
        db.from("profiles").update({
            set("display_name", name.trim()); set("initials", initials); set("initials_bg", bg); set("initials_ink", null as String?)
        }) { filter { eq("id", uid) } }
        profileCache.remove(uid)
    }

    // ---------- Karten bearbeiten ----------

    suspend fun updateCard(id: String, title: String, description: String?, dueDate: String?) {
        db.from("cards").update({
            set("title", title.trim()); set("description", description?.trim()?.ifBlank { null }); set("due_date", dueDate)
        }) { filter { eq("id", id) } }
    }

    suspend fun addAssignee(card: Card, userId: String) {
        db.from("card_assignees").insert(NewAssignee(card.id, userId, card.projectId))
    }

    suspend fun removeAssignee(card: Card, userId: String) {
        db.from("card_assignees").delete { filter { eq("card_id", card.id); eq("user_id", userId) } }
    }

    suspend fun editComment(id: String, body: String) {
        db.from("card_comments").update({ set("body", body.trim()) }) { filter { eq("id", id) } }
    }

    suspend fun deleteComment(id: String) {
        db.from("card_comments").delete { filter { eq("id", id) } }
    }

    // ---------- Bounties ausschreiben ----------

    suspend fun createBounty(workspaceId: String, projectId: String?, cardId: String?, kind: String, title: String, description: String) {
        db.from("bounties").insert(NewBounty(workspaceId, projectId, cardId, kind, title = title.trim(), description = description.trim().ifBlank { null }))
    }

    // ---------- Reaktionen ----------

    suspend fun reactions(cardId: String): List<Reaction> =
        db.from("card_reactions").select(Columns.raw("user_id, emoji")) { filter { eq("card_id", cardId) } }.decodeList()

    suspend fun toggleReaction(card: Card, emoji: String, on: Boolean) {
        val uid = userId ?: return
        if (on) db.from("card_reactions").insert(NewReaction(card.id, card.projectId, uid, emoji))
        else db.from("card_reactions").delete { filter { eq("card_id", card.id); eq("user_id", uid); eq("emoji", emoji) } }
    }

    // ---------- Erwähnungen ----------

    suspend fun addComment(cardId: String, body: String, mentions: List<String>) {
        val uid = userId ?: return
        db.from("card_comments").insert(NewComment(cardId, uid, body.trim(), mentions.distinct()))
    }

    // ---------- Checkliste zuweisen ----------

    suspend fun assignChecklist(itemId: String, person: String?) = rpc("checklist_assign") {
        put("_item", itemId)
        if (person != null) put("_person", person) else put("_person", kotlinx.serialization.json.JsonNull)
    }

    // ---------- Folgeaufgaben ----------

    suspend fun followUps(cardId: String): List<FollowCard> =
        db.from("cards").select(Columns.raw("id, title, done, follow_locked")) {
            filter { eq("follows_id", cardId); exact("archived_at", null) }
            order("position", Order.ASCENDING)
        }.decodeList()

    suspend fun cardTitle(cardId: String): FollowCard? =
        runCatching { db.from("cards").select(Columns.raw("id, title, done, follow_locked")) { filter { eq("id", cardId) } }.decodeSingle<FollowCard>() }.getOrNull()

    suspend fun addFollowUp(card: Card, title: String) {
        val uid = userId ?: return
        val cols = columns(card.projectId).filter { !it.isDone }
        val col = cols.firstOrNull { it.kind == "open" } ?: cols.firstOrNull { it.kind != "idea" } ?: cols.firstOrNull()
        db.from("cards").insert(NewFollowCard(card.projectId, col?.id ?: card.columnId, title.trim(), System.currentTimeMillis() / 1e12 + 1000, card.id, uid))
    }

    suspend fun unlockFollow(cardId: String) = rpc("card_follow_unlock") { put("_card", cardId) }

    // ---------- Wiederkehrend ----------

    suspend fun recurrence(templateId: String): Recurrence? =
        db.from("card_recurrences").select(Columns.raw("kind, every, weekdays, month_day, nth, start_date, end_date, paused, rotation, rotation_idx")) {
            filter { eq("card_id", templateId) }
        }.decodeList<Recurrence>().firstOrNull()

    suspend fun setRecurrence(cardId: String, r: Recurrence) = rpc("recurrence_set") {
        put("_card", cardId); put("_kind", r.kind); put("_every", r.every)
        put("_weekdays", kotlinx.serialization.json.JsonArray(r.weekdays.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        if (r.monthDay != null) put("_month_day", r.monthDay) else put("_month_day", kotlinx.serialization.json.JsonNull)
        if (r.nth != null) put("_nth", r.nth) else put("_nth", kotlinx.serialization.json.JsonNull)
        put("_start", r.startDate)
        if (r.endDate != null) put("_end", r.endDate) else put("_end", kotlinx.serialization.json.JsonNull)
        put("_rotation", kotlinx.serialization.json.JsonArray(r.rotation.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        put("_rotation_idx", if (r.rotation.isEmpty()) 0 else r.rotationIdx % r.rotation.size)
        put("_paused", r.paused)
    }

    suspend fun deleteRecurrence(cardId: String) = rpc("recurrence_delete") { put("_card", cardId) }

    // ---------- Einstellungen der Umgebung ----------

    suspend fun workspaceRow(id: String): WorkspaceRow =
        db.from("workspaces").select(Columns.raw("id, name, color, icon, auto_archive_days")) { filter { eq("id", id) } }.decodeSingle()

    suspend fun updateWorkspace(id: String, name: String, color: String, autoArchiveDays: Int) {
        db.from("workspaces").update({ set("name", name.trim()); set("color", color); set("auto_archive_days", autoArchiveDays) }) {
            filter { eq("id", id) }
        }
    }

    /** Rechte-Tabelle (Gäste oder Kern) als Schalter-Map lesen. */
    suspend fun permissions(table: String, workspaceId: String): Map<String, Boolean> {
        val rows = db.from(table).select { filter { eq("workspace_id", workspaceId) } }
            .decodeList<kotlinx.serialization.json.JsonObject>()
        val row = rows.firstOrNull() ?: return emptyMap()
        return row.filterKeys { it != "workspace_id" && it != "updated_at" }
            .mapNotNull { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()?.let { k to it } }
            .toMap()
    }

    suspend fun setPermissions(table: String, workspaceId: String, values: Map<String, Boolean>) {
        val obj = buildJsonObject {
            put("workspace_id", workspaceId)
            values.forEach { (k, v) -> put(k, v) }
        }
        db.from(table).upsert(obj)
    }

    private suspend fun rpc(name: String, args: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        db.postgrest.rpc(name, buildJsonObject(args))
    }
}
