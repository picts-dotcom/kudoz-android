# Kudoz für Android

Native Android-App (Kotlin, Jetpack Compose) für [kudoz.ch](https://kudoz.ch). Nutzt dasselbe Backend
(Lovable Cloud / Supabase) wie die Web-App; alle Rechte prüft Postgres (RLS und RPCs).

## Anmeldung
Die App öffnet `https://kudoz.ch/app-login?state=…` (Web-Repo `src/routes/app-login.tsx`). Nach dem
Google-Login gibt die Seite eine eigene Sitzung per `intent://` nur an das Paket `ch.kudoz.app` zurück.

## Bauen
GitHub Actions baut bei jedem Push auf `main` eine signierte APK (Reiter *Actions* und *Releases*).
Nötige Repository-Secrets:

| Secret | Inhalt |
|---|---|
| `KUDOZ_KEYSTORE_B64` | Signaturschlüssel `kudoz-release.jks`, base64-kodiert |
| `KUDOZ_KEYSTORE_PASSWORD` | Passwort des Schlüssels |

Den Schlüssel sicher aufbewahren: Ohne ihn lassen sich installierte Apps nicht mehr aktualisieren.

## Stand
- Etappe 1: Login, Umgebungen, Projekte, Board (Spalten wischen, Karten verschieben/erledigen/anlegen),
  Karte (Übernehmen, Erledigt, Warten, Checkliste, Kommentare), Mitteilungen mit Sprung zur Karte.
- Etappe 2: Push über Firebase (ohne Personendaten in der Nachricht).
- Etappe 3: Mein Tag (eigener Reiter, Fächer wie im Web), Bounties und Shoutouts je Umgebung, «Mein Plan» in der Karte, K-Logo.
- Etappe 4: Schatzkammer (200 Bilder lokal, 10 Kategorien), Eingang (annehmen, Rückfrage, ablehnen), Mitglieder (Rollen für Admins), Archiv je Projekt, Belohnungsbild bei Bounties.
- Etappe 5: Flotte, Meilensteine, Aufgaben an andere Umgebungen senden, persönliche Einstellungen.
- Etappe 6: Aufgaben bearbeiten (Titel, Beschreibung, Fälligkeit), Verantwortliche ändern, Bounty/Hilfe ausschreiben, eigene Kommentare bearbeiten/löschen.
