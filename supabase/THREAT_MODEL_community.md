# Threat Model — Community Barcode-/Mix-Datenbank

Scope: `community_drinks.sql`, `community_mixes.sql`, `city_drink_trends.sql` und die
Community-relevanten Teile von `admin_security.sql` (`admin_blocked_voters`, `submit_report`,
`admin_locked`, Moderations-RPCs).

## Assets & warum sie schützenswert sind
| Asset | Warum kritisch |
|---|---|
| `community_drinks.abv/volume/calories` (status='approved') | Fliesst **direkt in die Widmark-BAC-Rechnung** aller Nutzer → **safety-critical**. Falsche Werte = falsche Fahrtüchtigkeits-/SOS-Einschätzung. |
| `community_mixes` (approved) | Gleiche BAC-Relevanz über Rezept-Gesamt-ABV. |
| `*_votes`, `confirmed_count` | Steuern Auto-Approve → Integrität entscheidet, was approved wird. |
| `admin_blocked_voters` | Missbrauchs-Abwehr; nur wirksam, wenn Voter-Identität vertrauenswürdig. |
| `admin_reports` | Melde-Kanal; Ziel von Spam & Zensur-Missbrauch. |
| `city_drink_pings` | Nur kosmetischer Trend-Feed (geringer Impact). |

## Trust Boundary (das Kernproblem)
Der **anon-Key liegt in der IPA**. Damit kann *jeder* ohne Account `contribute_drink`,
`contribute_mix`, `ping_city_drink`, `city_drink_trends` aufrufen und approved Rows lesen.
Ein *eingeloggter* Nutzer hat zusätzlich eine echte `auth.uid()` (Gratis-Account, aber nicht
fälschbar). Die einzige belastbare Vertrauensanker ist damit **`auth.uid()`** — **nicht** die
IP/`x-forwarded-for` und **nicht** irgendein Client-String.

## Threat-Actors
1. **Anon-Angreifer** (extrahierter anon-Key, kein Account).
2. **Signed-in-Nutzer** (echte `auth.uid()`, aber beliebig viele Gratis-Accounts = Sybil-Kosten niedrig).
3. **Bot/Bulk-Operator** (Automatisierung, Header-/IP-Rotation).
4. **Böswilliger Admin** (out of scope für Auto-Approve, aber Audit-relevant).

---

## Threats & Gegenmaßnahmen (STRIDE)

### A · Spoofing (Identität)
**A1 — Voter-ID-Spoofing [S].** Anon-Voter = `community_voter_id()` = `auth.uid()` **else erster
`x-forwarded-for`-Hop**. Ist die Edge nicht gehärtet, ist XFF client-kontrolliert → beliebig viele
„distinkte" Voter.
- *Ist:* server-abgeleitete Identität (Intention korrekt).
- *Lücke:* XFF ist nur vertrauenswürdig, wenn der Reverse-Proxy ihn **setzt/überschreibt**.
- **Gegenmaßnahme:** (1) Für alles, was *Vertrauen erzeugt* (Auto-Approve, Block-Key) **nur
  `auth.uid()`** zählen. (2) Für reines Anon-Rate-Limiting die **echte Connection-IP** eines
  vertrauenswürdigen GUC nutzen, nie den client-gelieferten linken XFF-Hop; eingehenden XFF an der
  Edge strippen. (3) Optional **App Attest / DeviceCheck** als Attestierung für Anon-Writes.

**A2 — First-Writer-Squatting [S/T].** `insert … on conflict (barcode) do nothing` behält die Werte
des **ersten** Contributors. Ein Angreifer registriert einen Barcode zuerst mit falschem ABV;
spätere ehrliche Scans **voten diesen Junk nur noch Richtung Approve**.
- **Gegenmaßnahme:** Approve auf **Konsens** statt First-Writer stützen: pro Vote den übermittelten
  ABV/Volume mitspeichern und beim Approve den **Median/Modalwert** übernehmen, nicht den ersten.

### B · Spam / Flooding [D]
**B1 — Volumen-Flut.** Caps: 40/h (contribute), 120/h (ping) — **keyed auf den spoofbaren Voter**.
- **Gegenmaßnahme:** Cap auf `auth.uid()` **und** vertrauenswürdige IP; globales Token-Bucket-Limit;
  Attestierung für Anon.

**B2 — Katalog-Pollution.** Beliebig viele neue Barcodes/Mix-Namen als `pending`.
- *Ist:* Reads sind approved-only → pending-Spam ist für Nutzer unsichtbar (gut), füllt aber Queue
  und Votes-Tabellen.
- **Gegenmaßnahme:** Cap „neue distinkte Items pro Identität/Tag"; ein Item erscheint erst in der
  Queue, wenn **≥2 unabhängige `auth.uid()`-Submitter** es gemeldet haben; **stale pending** nach
  z. B. 30 Tagen auto-expiren.

**B3 — Mix-name_key-Kollision.** Dedupe per `lower(trim(name))`: Angreifer registriert einen
beliebten Namen mit Junk-Zutaten zuerst; das echte Rezept kollidiert und wird verworfen
(`on conflict do nothing`).
- **Gegenmaßnahme:** Dedupe auf `name_key` **+ Zutaten-Signatur** (Varianten erlauben); Admin-Merge.

### C · Falsche ABV-Werte (Daten-Integrität, safety-critical) [T]
**C1 — In-Range-Fälschung.** Validierung begrenzt nur 0–100 %. Ein Bier mit 40 % ist „gültig" und
kann per Auto-Approve **vor** jedem menschlichen Blick live gehen → verfälscht die BAC-Rechnung aller.
- **Gegenmaßnahmen (nach Wirkung/Kosten):**
  1. **Kategorie-Plausibilitätsbänder** (billigste, wirksamste Safety-Kontrolle): Bier 0–12,
     Wein 8–22, Sparkling 5–16, Spirits 15–80, Liqueur 10–55, Shot 15–60, Cider 1–12 … Ausreisser
     hart ablehnen.
  2. **Open-Food-Facts-Cross-Check** bei bekanntem Barcode: Divergenz > X % → nicht auto-approven,
     zur Handprüfung flaggen.
  3. **Höhere/`auth.uid()`-basierte** Approve-Schwelle für safety-relevante Kategorien; ggf.
     Auto-Approve für Spirits ganz aussetzen (immer Handprüfung).
  4. **Konsens-Wert** statt First-Writer (siehe A2).

**C2 — Post-Approval-Tampering.** `on conflict do nothing` + `confirmed_count`-Update mit
`status <> 'rejected'` verhindern Überschreiben approved/rejected Rows durch contribute. Nur
`admin_update_drink` ändert approved Werte. **Kein Gap** — solide.

### D · Auto-Approve-Missbrauch [E/T]
**D1 — Sybil-Auto-Approve.** 3 distinkte Voter (drinks) / 5 (mixes); Voter spoofbar → Selbst-Approve.
- **Gegenmaßnahme:** Nur **distinkte `auth.uid()`-Voter** zählen (Anon-IP-Votes erhöhen
  `confirmed_count` fürs Anzeigen, promoten aber **nicht**); Schwellen anheben; **zeitliche
  Streuung** verlangen (Votes über > N Stunden / verschiedene Tage) gegen Burst-Sybil.

**D2 — admin_locked-Bypass.** Auto-Approve prüft `admin_locked = false`;
`admin_set_moderation_status` setzt `admin_locked = (p_status <> 'approved')`,
`admin_update_drink` cleared es. Resurrection eines manuell auf pending/rejected gesetzten Items ist
damit geschlossen. **Kein Gap** — solide.

### E · Reports [D/Repudiation]
**E1 — Report-Spam.** `submit_report`: sign-in-Pflicht (`auth.uid()`), 10/h pro Reporter,
`item_type`-Whitelist, `reason` ≤240, `details` ≤4096 — gut.
- *Lücken:* keine Dedupe (derselbe Reporter kann dasselbe Item über Stunden mehrfach melden);
  `item_id` wird nicht auf Existenz geprüft (Noise).
- **Gegenmaßnahme:** `unique (reporter_id, item_type, item_id)` per Upsert; Item-Existenz prüfen;
  Reporter-Trust gewichten.

**E2 — Report-as-Censorship.** Massen-Falschmeldungen, um gute Inhalte rejecten zu lassen.
- *Ist:* Reports lösen **keine** Auto-Aktion aus, Admin resolved → Human-in-the-Loop (gut).
- **Gegenmaßnahme:** so lassen; Reporter-Falsch-Positiv-Rate tracken.

**E3 — Fehlende Report→Aktion-Kopplung.** Ein Report **quarantänt** das Item nicht.
- **Gegenmaßnahme (optional):** bei **N distinkten** Reportern approved→pending soft-hiden
  (`admin_locked = true`, damit die Crowd es nicht sofort re-approved), bis Admin entscheidet.

### F · Blocklist [E]
**F1 — Block-Umgehung.** `admin_blocked_voters` keyt auf den **spoofbaren** Voter → Anon umgeht per
XFF-/IP-Rotation oder Ausloggen.
- **Gegenmaßnahme:** Auf `auth.uid()` blocken (durabel); für Anon auf **vertrauenswürdige IP/Subnetz
  + Device-Attest**. Zusätzlich **Content-Blocks** (Barcode/`name_key`), nicht nur Voter.

**F2 — Block nicht retroaktiv.** Ein Block entfernt weder bereits gecastete Votes noch bereits
approved Items des Voters.
- **Gegenmaßnahme:** Beim Blocken `confirmed_count` **ohne** geblockte Voter neu berechnen und Items
  unter Schwelle demoten.

**F3 — Tabellen-Exposure.** `admin_blocked_voters` ist von anon/authenticated `revoke`t, nur DEFINER-
Funktionen greifen zu. **Kein Gap.**

### G · Rate Limits (querschnittlich) [D]
- *Ist:* 40/h contribute, 120/h ping, 10/h reports.
- *Lücken:* Key spoofbar (Anon); kein globales/Per-IP-Ceiling; keine Per-Barcode-Vote-Velocity; das
  `count(*)`-Fenster ist ein leichtes TOCTOU (zwei parallele Inserts können knapp unter dem Cap beide
  durchgehen — geringfügig).
- **Gegenmaßnahme:** Composite-Key `auth.uid()` + trusted IP; globaler Token-Bucket; Per-Barcode-Vote-
  Velocity-Cap; Attest-Gate für Anon-Writes.

---

## Risiko-Matrix (Rest-Risiko heute)
| Threat | Likelihood | Impact | Rest-Risiko |
|---|---|---|---|
| C1 In-Range-ABV + D1 Sybil-Approve | Mittel–Hoch* | **Hoch (Safety)** | **Hoch** |
| A2 First-Writer-Squatting | Mittel | Hoch (Safety) | Mittel–Hoch |
| B2/B3 Katalog-/Namens-Pollution | Mittel | Niedrig–Mittel | Mittel |
| F1/F2 Block-Umgehung/nicht-retroaktiv | Mittel | Mittel | Mittel |
| E1 Report-Spam | Mittel | Niedrig | Niedrig–Mittel |
| G TOCTOU im Rate-Limit | Niedrig | Niedrig | Niedrig |
| Q city trends XFF-Poisoning | Mittel | Niedrig (kosmetisch) | Niedrig |

\* hängt am Edge-XFF-Verhalten — **zuerst verifizieren** (s. u.).

## Priorisierte Gegenmaßnahmen
**P0 (Safety, sofort)**
1. **Kategorie-Plausibilitätsbänder** für ABV in `contribute_drink` (billig, killt „Bier @ 40 %").
2. **Auto-Approve nur über distinkte `auth.uid()`-Voter** (killt Anon-IP-Sybil) — Anon-Votes zählen
   fürs Anzeigen, aber nicht fürs Promoten.

**P1**
3. **Client-XFF nicht mehr vertrauen**: an der Edge strippen; Rate-Limit/Block auf `auth.uid()` +
   echte Connection-IP.
4. **Konsens-Wert (Median)** statt First-Writer für ABV/Volume.
5. **Report → Soft-Hide** bei N distinkten Reportern; `unique(reporter,item)`.

**P2**
6. `name_key`-Kollisionshandling; stale-pending-Expiry; `confirmed_count`-Neuberechnung beim Block;
   OFF-Cross-Check; Per-Barcode-Vote-Velocity.

## Zuerst verifizieren (entscheidet P0-Schweregrad)
Ruf eine Anon-RPC über die **echte Edge** mit gefälschtem `x-forwarded-for: 1.2.3.4` auf und prüfe,
ob `city_drink_pings.voter` `1.2.3.4` (client-kontrolliert → Sybil real) oder die echte IP speichert.
Ergebnis entscheidet, ob C1/D1 effektiv **P0** oder **P1** ist.
