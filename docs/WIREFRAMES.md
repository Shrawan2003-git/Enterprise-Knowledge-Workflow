# Wireframes — KnowFlow UI

> [!NOTE]
> These ASCII wireframes define the **layout and information hierarchy** for each major screen.
> They are intentionally lo-fi — visual polish (colors, spacing, icons) is deferred to the
> Tailwind/React implementation phase.

---

## 1. Login Page

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                                                                      │
│                                                                      │
│                     ┌──────────────────────────┐                     │
│                     │                          │                     │
│                     │       ◆ KnowFlow         │                     │
│                     │   Enterprise Knowledge   │                     │
│                     │                          │                     │
│                     │  ┌──────────────────────┐│                     │
│                     │  │ Email                ││                     │
│                     │  └──────────────────────┘│                     │
│                     │                          │                     │
│                     │  ┌──────────────────────┐│                     │
│                     │  │ Password         👁  ││                     │
│                     │  └──────────────────────┘│                     │
│                     │                          │                     │
│                     │  ┌──────────────────────┐│                     │
│                     │  │      Sign In         ││                     │
│                     │  └──────────────────────┘│                     │
│                     │                          │                     │
│                     │  ┌──────────────────────┐│                     │
│                     │  │ ⚠ Invalid email or   ││                     │
│                     │  │   password.          ││                     │
│                     │  └──────────────────────┘│                     │
│                     │                          │                     │
│                     │  Forgot password?        │                     │
│                     │                          │                     │
│                     └──────────────────────────┘                     │
│                                                                      │
│                     © 2024 KnowFlow — v1.0                           │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Centered card layout, max-width 400px
- Error message area (red banner) appears only on failed login attempt
- Password field has show/hide toggle (👁)
- "Forgot password?" link (placeholder for v1 — not implemented)
- After 5 failed attempts in 15 minutes → show rate-limit message (429)
- Successful login → redirect to Dashboard

---

## 2. Dashboard

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Welcome back, Jane Doe                                             Wed Oct 28   │
│                                                                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                │
│  │  📄 42      │ │  ✅ 3       │ │  📋 7       │ │  ⚠ 2        │                │
│  │  Documents  │ │  Pending    │ │  Open       │ │  Overdue    │                │
│  │             │ │  Approvals  │ │  Tasks      │ │  Items      │                │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘                │
│                                                                                  │
│  ┌──────────────────────────────────────────┐ ┌─────────────────────────────┐    │
│  │ Recent Documents                    See all│ │ My Tasks                See all│    │
│  │                                          │ │                             │    │
│  │  📄 Security Policy v2.1                 │ │  □ Review Q3 compliance doc │    │
│  │     APPROVED · INTERNAL · Oct 27         │ │    HIGH · Due Oct 30        │    │
│  │                                          │ │                             │    │
│  │  📄 Onboarding Guide v1.0               │ │  □ Update storage policy     │    │
│  │     DRAFT · PUBLIC · Oct 26              │ │    MEDIUM · Due Nov 5       │    │
│  │                                          │ │                             │    │
│  │  📄 Architecture Decision Log            │ │  ☑ Tag legacy documents     │    │
│  │     APPROVED · CONFIDENTIAL · Oct 25     │ │    LOW · Done Oct 24        │    │
│  │                                          │ │                             │    │
│  │  📄 HR Benefits Summary                  │ │  □ Assign approvers for Q4  │    │
│  │     ACTIVE · INTERNAL · Oct 24           │ │    URGENT · Due Oct 29 ⚠    │    │
│  │                                          │ │                             │    │
│  │  📄 Data Retention Policy v3.0           │ │  □ Upload department charter │    │
│  │     APPROVED · RESTRICTED · Oct 23       │ │    MEDIUM · Due Nov 10      │    │
│  │                                          │ │                             │    │
│  └──────────────────────────────────────────┘ └─────────────────────────────┘    │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ Notifications                                                    See all│    │
│  │                                                                        │    │
│  │  🔔  Your document "Security Policy v2.1" was approved.     2 hrs ago  │    │
│  │  🔔  New task assigned: "Review Q3 compliance doc"          5 hrs ago  │    │
│  │  🔔  Approval requested for "Onboarding Guide v1.0"       yesterday   │    │
│  │                                                                        │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Top nav bar: logo, main navigation links, notification bell with unread count, user avatar + dropdown
- Stats cards are clickable — navigate to filtered views
- "Overdue Items" card highlights in red/orange when count > 0
- Recent Documents shows last 5 documents the user accessed or owns
- My Tasks shows up to 5 tasks sorted by due date ascending
- Notification summary shows 3 most recent; "See all" links to Notification Inbox

---

## 3. Knowledge Library

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Knowledge Library                                          [+ Upload Document]  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ 🔍  Search documents...                                      [Search]     │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  Filters: [All Status ▾] [All Categories ▾] [All Classifications ▾] [Clear All] │
│                                                                                  │
│  Tags:  [ policy × ] [ compliance × ]  [+ Add Tag]                              │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ Title              │ Category     │ Class.     │ Status   │ Owner   │ Updated│  │
│  ├────────────────────┼──────────────┼────────────┼──────────┼─────────┼────────┤  │
│  │ Security Policy    │ Governance   │ 🔴 RESTR  │ ✅ APPR  │ J. Doe  │ Oct 27 │  │
│  │ Onboarding Guide   │ HR           │ 🟢 PUBLIC │ 📝 DRAFT │ A. Smith│ Oct 26 │  │
│  │ Architecture Log   │ Engineering  │ 🟡 CONF   │ ✅ APPR  │ B. Lee  │ Oct 25 │  │
│  │ HR Benefits        │ HR           │ 🔵 INTRNL │ 🟢 ACTVE │ M. Park │ Oct 24 │  │
│  │ Data Retention Pol │ Compliance   │ 🔴 RESTR  │ ✅ APPR  │ J. Doe  │ Oct 23 │  │
│  │ API Standards v2   │ Engineering  │ 🔵 INTRNL │ ✅ APPR  │ C. Wang │ Oct 22 │  │
│  │ Travel Policy      │ Operations   │ 🟢 PUBLIC │ ✅ APPR  │ D. Kim  │ Oct 21 │  │
│  │ Brand Guidelines   │ Marketing    │ 🟡 CONF   │ 📝 DRAFT │ E. Ruiz │ Oct 20 │  │
│  │                    │              │            │          │         │        │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  Showing 1–8 of 42 documents           [< Prev]  1  2  3  4  5  [Next >]        │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Search uses PostgreSQL `tsvector` full-text search (works without AI)
- Filter chips are dropdowns with multi-select
- Classification badges are color-coded: PUBLIC (green), INTERNAL (blue), CONFIDENTIAL (yellow), RESTRICTED (red)
- Status badges: DRAFT (pencil), ACTIVE (green circle), APPROVED (checkmark), ARCHIVED (gray)
- Columns are sortable (click header to toggle asc/desc)
- Row click navigates to Document Detail
- "Upload Document" button visible to EMPLOYEE, KNOWLEDGE_MANAGER, ADMIN
- Pagination: default 20 per page, max 100

---

## 4. Document Detail

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ← Back to Library                                                               │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────┐ ┌────────────────────┐  │
│  │                                                     │ │ Metadata           │  │
│  │  Security Policy v2.1                               │ │                    │  │
│  │  ✅ APPROVED   🔴 RESTRICTED                        │ │ Owner: Jane Doe    │  │
│  │                                                     │ │ Dept: Governance   │  │
│  │  [📥 Download]  [✏️ New Version]  [📤 Submit for    │ │ Category: Policy   │  │
│  │                                    Approval]        │ │ Created: Oct 15    │  │
│  │  [🗄 Archive]  [⋯ More]                             │ │ Updated: Oct 27    │  │
│  │                                                     │ │ Expiry: Dec 31     │  │
│  │                                                     │ │ Version: 3         │  │
│  │ ┌──────────┬──────────┬──────────────┬──────────┐   │ │ Checksum: a3f2...  │  │
│  │ │ Overview │ Versions │ Permissions  │ Activity │   │ │ Size: 2.4 MB       │  │
│  │ ├──────────┴──────────┴──────────────┴──────────┤   │ │                    │  │
│  │ │                                               │   │ │ Tags               │  │
│  │ │  Description:                                 │   │ │ [policy] [security]│  │
│  │ │  This document defines the organization's     │   │ │ [compliance]       │  │
│  │ │  security policies, including access control,  │   │ │                    │  │
│  │ │  data handling, and incident response.         │   │ │ Classification     │  │
│  │ │                                               │   │ │ 🔴 RESTRICTED      │  │
│  │ │  Processing Status: ✅ READY                   │   │ │ Only MANAGE + ADMIN│  │
│  │ │  Pages: 24 | Chunks: 47                        │   │ │                    │  │
│  │ │                                               │   │ └────────────────────┘  │
│  │ └───────────────────────────────────────────────┘   │                         │
│  │                                                     │                         │
│  └─────────────────────────────────────────────────────┘                         │
│                                                                                  │
│  ── Versions Tab ──────────────────────────────────────────────────────────────  │
│                                                                                  │
│  │ v3  │ Oct 27 │ J. Doe  │ ✅ READY   │ "Updated access controls" │ 📥 │ ★   │  │
│  │ v2  │ Oct 20 │ J. Doe  │ ✅ READY   │ "Added incident response" │ 📥 │     │  │
│  │ v1  │ Oct 15 │ J. Doe  │ ✅ READY   │ "Initial upload"          │ 📥 │     │  │
│                                                                                  │
│  ── Permissions Tab ───────────────────────────────────────────────────────────  │
│                                                                                  │
│  │ Principal         │ Type       │ Level  │ Action │                             │
│  │ Jane Doe          │ USER       │ MANAGE │ [Remove]│                             │
│  │ Governance Dept   │ DEPARTMENT │ VIEW   │ [Remove]│                             │
│  │ Bob Lee           │ USER       │ EDIT   │ [Remove]│                             │
│                                                                                  │
│  [+ Add Permission]                                                              │
│                                                                                  │
│  ── Activity Tab ──────────────────────────────────────────────────────────────  │
│                                                                                  │
│  │ Oct 27 14:30 │ J. Doe approved version 3                                │    │
│  │ Oct 27 10:15 │ A. Smith submitted version 3 for approval                │    │
│  │ Oct 27 09:00 │ J. Doe uploaded version 3                                │    │
│  │ Oct 20 16:45 │ J. Doe approved version 2                                │    │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Header shows title, status badge, classification badge
- Action buttons are role/permission-gated:
  - Download: requires VIEW+ on document
  - New Version: requires EDIT+ or owner
  - Submit for Approval: requires EDIT+ and document has DRAFT version
  - Archive: requires MANAGE or owner
- Tabs: Overview (default), Versions, Permissions, Activity
- Versions tab: each row shows version number, date, uploader, processing status, change note, download link
- ★ indicates the current live version (`current_version_id`)
- Permissions tab: only visible to users with MANAGE permission or ADMIN
- Activity tab: sourced from `audit_logs` filtered by entity

---

## 5. Upload Flow

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Upload Document                                                                 │
│                                                                                  │
│  Step 1: Select File                                                             │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                            │  │
│  │              ┌─────────────────────────────────┐                           │  │
│  │              │                                 │                           │  │
│  │              │     📁 Drag & drop your PDF     │                           │  │
│  │              │     or click to browse           │                           │  │
│  │              │                                 │                           │  │
│  │              │     Max size: 50 MB              │                           │  │
│  │              │     Accepted: .pdf               │                           │  │
│  │              │                                 │                           │  │
│  │              └─────────────────────────────────┘                           │  │
│  │                                                                            │  │
│  │  ✅ security-policy-v3.pdf (2.4 MB)                           [✕ Remove]   │  │
│  │  ████████████████████████████████████████████████░░  92%                   │  │
│  │                                                                            │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  Step 2: Document Metadata                                                       │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                            │  │
│  │  Title *                                                                   │  │
│  │  ┌──────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ Security Policy                                                     │  │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                            │  │
│  │  Description                                                               │  │
│  │  ┌──────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ Organization security policies for access control and data          │  │  │
│  │  │ handling procedures.                                                │  │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                            │  │
│  │  Category *                          Classification *                      │  │
│  │  ┌─────────────────────────┐         ┌─────────────────────────┐          │  │
│  │  │ Governance          ▾  │         │ 🔴 RESTRICTED        ▾  │          │  │
│  │  └─────────────────────────┘         └─────────────────────────┘          │  │
│  │                                                                            │  │
│  │  Tags                                                                      │  │
│  │  ┌──────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ [policy ×] [security ×] [compliance ×]  type to add...              │  │  │
│  │  └──────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                            │  │
│  │  Expiry Date (optional)              Change Note                           │  │
│  │  ┌─────────────────────────┐         ┌─────────────────────────────────┐  │  │
│  │  │ 2024-12-31          📅 │         │ Updated access control section  │  │  │
│  │  └─────────────────────────┘         └─────────────────────────────────┘  │  │
│  │                                                                            │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ ⚠ Validation Errors:                                                      │  │
│  │   • Title is required                                                      │  │
│  │   • Category must be selected                                              │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  [Cancel]                                                      [Upload Document] │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Drag-and-drop zone accepts `.pdf` only; rejects other file types with inline error
- File size validated client-side (50 MB) before upload starts
- Progress bar shows upload percentage
- Title is auto-populated from filename (editable)
- Category dropdown populated from `categories` table
- Classification defaults to `INTERNAL`
- Tags: autocomplete from existing tags, free-text to create new (lowercase normalized)
- Expiry date: optional date picker
- Validation errors shown in red box above submit button
- On success: redirect to Document Detail with success toast

---

## 6. Approval Inbox

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Approval Inbox                                             Filter: [All ▾]      │
│  3 pending reviews                                                               │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │  📄 Onboarding Guide v1.0                                                │  │
│  │  Submitted by: Alice Smith · Oct 26 10:30                                 │  │
│  │  Category: HR · Classification: 🟢 PUBLIC                                │  │
│  │  Change Note: "Initial version for new hire onboarding"                   │  │
│  │                                                                           │  │
│  │  [📥 Download v1.0]   [📄 View Document]                                 │  │
│  │                                                                           │  │
│  │  Comment:                                                                 │  │
│  │  ┌────────────────────────────────────────────────────────────────────┐   │  │
│  │  │                                                                    │   │  │
│  │  └────────────────────────────────────────────────────────────────────┘   │  │
│  │                                                                           │  │
│  │  [✅ Approve]   [❌ Reject]   [🔄 Request Changes]                       │  │
│  │                                                                           │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │  📄 Data Classification Standard v2.0                                     │  │
│  │  Submitted by: Charlie Wang · Oct 25 14:00                                │  │
│  │  Category: Compliance · Classification: 🔵 INTERNAL                      │  │
│  │  Change Note: "Added RESTRICTED tier definitions"                         │  │
│  │                                                                           │  │
│  │  [📥 Download v2.0]   [📄 View Document]                                 │  │
│  │                                                                           │  │
│  │  Comment:                                                                 │  │
│  │  ┌────────────────────────────────────────────────────────────────────┐   │  │
│  │  │                                                                    │   │  │
│  │  └────────────────────────────────────────────────────────────────────┘   │  │
│  │                                                                           │  │
│  │  [✅ Approve]   [❌ Reject]   [🔄 Request Changes]                       │  │
│  │                                                                           │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │  📄 Remote Work Policy v1.2                                               │  │
│  │  Submitted by: Diana Kim · Oct 24 09:15                                   │  │
│  │  Category: Operations · Classification: 🟢 PUBLIC                        │  │
│  │  Change Note: "Updated home office equipment allowance"                   │  │
│  │                                                                           │  │
│  │  [📥 Download v1.2]   [📄 View Document]                                 │  │
│  │                                                                           │  │
│  │  Previous Decision History:                                               │  │
│  │  │ Oct 22 │ Changes Requested by J. Doe: "Clarify expense limits"   │   │  │
│  │  │ Oct 20 │ Submitted by D. Kim                                     │   │  │
│  │                                                                           │  │
│  │  Comment:                                                                 │  │
│  │  ┌────────────────────────────────────────────────────────────────────┐   │  │
│  │  │                                                                    │   │  │
│  │  └────────────────────────────────────────────────────────────────────┘   │  │
│  │                                                                           │  │
│  │  [✅ Approve]   [❌ Reject]   [🔄 Request Changes]                       │  │
│  │                                                                           │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ── Completed Reviews ─────────────────────────────────────────────────────────  │
│  │ Security Policy v2.1  │ ✅ Approved │ Oct 27 │ "LGTM"                     │  │
│  │ Travel Policy v1.0    │ ❌ Rejected │ Oct 19 │ "Missing budget section"   │  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Shows approvals where current user is the assigned `approver_id`
- Filter dropdown: All, Pending, Approved, Rejected, Changes Requested
- Each card shows: document title + version, submitter, timestamp, category, classification, change note
- Comment is required for Reject and Request Changes, optional for Approve
- Approve/Reject/Request Changes buttons are disabled for the submitter (self-approval blocked — T-13)
- Download button allows reviewing the specific version under review
- Previous decision history shown when re-submitted after changes
- Completed Reviews section shows recent past decisions (collapsed by default)

---

## 7. Task Workspace

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Task Workspace                      [📋 List] [▦ Board]       [+ Create Task]  │
│                                                                                  │
│  ── Board View ────────────────────────────────────────────────────────────────  │
│                                                                                  │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌────────────┐  │
│  │ OPEN (3)         │ │ IN PROGRESS (2)  │ │ DONE (4)         │ │CANCELLED(1)│  │
│  │                  │ │                  │ │                  │ │            │  │
│  │ ┌──────────────┐ │ │ ┌──────────────┐ │ │ ┌──────────────┐ │ │ ┌────────┐ │  │
│  │ │🔴 URGENT     │ │ │ │🟡 MEDIUM     │ │ │ │🟢 LOW        │ │ │ │ LOW    │ │  │
│  │ │Assign Q4     │ │ │ │Review Q3     │ │ │ │Tag legacy    │ │ │ │Old rpt │ │  │
│  │ │approvers     │ │ │ │compliance    │ │ │ │documents     │ │ │ └────────┘ │  │
│  │ │              │ │ │ │doc           │ │ │ │              │ │ │            │  │
│  │ │👤 J. Doe     │ │ │ │👤 J. Doe     │ │ │ │👤 B. Lee     │ │ │            │  │
│  │ │📅 Oct 29 ⚠  │ │ │ │📅 Oct 30     │ │ │ │✅ Oct 24     │ │ │            │  │
│  │ │📄 —          │ │ │ │📄 Q3 Comp Doc│ │ │ │📄 —          │ │ │            │  │
│  │ └──────────────┘ │ │ └──────────────┘ │ │ └──────────────┘ │ │            │  │
│  │                  │ │                  │ │                  │ │            │  │
│  │ ┌──────────────┐ │ │ ┌──────────────┐ │ │ ┌──────────────┐ │ │            │  │
│  │ │🟡 MEDIUM     │ │ │ │🟠 HIGH       │ │ │ │🟡 MEDIUM     │ │ │            │  │
│  │ │Update storage│ │ │ │Finalize dept │ │ │ │Update FAQ    │ │ │            │  │
│  │ │policy        │ │ │ │charter       │ │ │ │section       │ │ │            │  │
│  │ │              │ │ │ │              │ │ │ │              │ │ │            │  │
│  │ │👤 A. Smith   │ │ │ │👤 C. Wang    │ │ │ │👤 D. Kim     │ │ │            │  │
│  │ │📅 Nov 5      │ │ │ │📅 Nov 1      │ │ │ │✅ Oct 23     │ │ │            │  │
│  │ │📄 Storage Pol│ │ │ │📄 Dept Chrtr │ │ │ │📄 FAQ Doc    │ │ │            │  │
│  │ └──────────────┘ │ │ └──────────────┘ │ │ └──────────────┘ │ │            │  │
│  │                  │ │                  │ │                  │ │            │  │
│  │ ┌──────────────┐ │ │                  │ │ ┌──────────────┐ │ │            │  │
│  │ │🟡 MEDIUM     │ │ │                  │ │ │🟠 HIGH       │ │ │            │  │
│  │ │Upload dept   │ │ │                  │ │ │Archive old   │ │ │            │  │
│  │ │charter       │ │ │                  │ │ │policies      │ │ │            │  │
│  │ │👤 E. Ruiz    │ │ │                  │ │ │👤 J. Doe     │ │ │            │  │
│  │ │📅 Nov 10     │ │ │                  │ │ │✅ Oct 22     │ │ │            │  │
│  │ └──────────────┘ │ │                  │ │ └──────────────┘ │ │            │  │
│  │                  │ │                  │ │                  │ │            │  │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘ └────────────┘  │
│                                                                                  │
│  ── List View (toggle) ────────────────────────────────────────────────────────  │
│                                                                                  │
│  │ Title              │ Priority │ Status      │ Assignee │ Due Date  │ Doc     │ │
│  │ Assign Q4 approvers│ 🔴 URG   │ OPEN        │ J. Doe   │ Oct 29 ⚠ │ —       │ │
│  │ Review Q3 compli.. │ 🟡 MED   │ IN_PROGRESS │ J. Doe   │ Oct 30   │ Q3 Comp │ │
│  │ Update storage pol │ 🟡 MED   │ OPEN        │ A. Smith │ Nov 5    │ Stor Pol│ │
│  │ Upload dept charter│ 🟡 MED   │ OPEN        │ E. Ruiz  │ Nov 10   │ —       │ │
│  │ Finalize dept chrtr│ 🟠 HIGH  │ IN_PROGRESS │ C. Wang  │ Nov 1    │ Dept Ch │ │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Toggle between List view and Kanban Board view (Board is default)
- Board columns: OPEN, IN_PROGRESS, DONE, CANCELLED
- Task cards show: priority badge (color-coded), title, assignee, due date, linked document
- ⚠ icon on overdue tasks (due date < today and status not DONE/CANCELLED)
- Cards are draggable between columns (updates task status via API)
- Clicking a card opens a detail modal with: full description, comments thread, linked document/approval, status change buttons
- "+ Create Task" opens a modal form: title, description, assignee (search), priority, due date, linked document (optional)
- Priority colors: LOW (green), MEDIUM (yellow), HIGH (orange), URGENT (red)

---

## 8. Copilot Chat

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│ ┌───────────────┐ ┌──────────────────────────────────────┐ ┌──────────────────┐ │
│ │ Conversations │ │ Chat                                 │ │ Sources          │ │
│ │               │ │                                      │ │                  │ │
│ │ [+ New Chat]  │ │                                      │ │ Citations for    │ │
│ │               │ │  ┌──────────────────────────────┐    │ │ selected message │ │
│ │ Today         │ │  │ 👤 You              10:30 AM │    │ │                  │ │
│ │ ▸ Security    │ │  │                              │    │ │ ┌──────────────┐ │ │
│ │   policy Q&A  │ │  │ What are the password        │    │ │ │ [1] Security │ │ │
│ │               │ │  │ requirements in our           │    │ │ │ Policy v2.1  │ │ │
│ │ Yesterday     │ │  │ security policy?              │    │ │ │ pp. 8–9      │ │ │
│ │ ▸ Onboarding  │ │  │                              │    │ │ │              │ │ │
│ │   questions   │ │  └──────────────────────────────┘    │ │ │ "Passwords   │ │ │
│ │ ▸ Data        │ │                                      │ │ │ must be at   │ │ │
│ │   retention   │ │  ┌──────────────────────────────┐    │ │ │ least 12     │ │ │
│ │               │ │  │ 🤖 KnowFlow          10:31 AM│    │ │ │ characters   │ │ │
│ │ Last Week     │ │  │                              │    │ │ │ with mixed   │ │ │
│ │ ▸ Benefits    │ │  │ According to the Security    │    │ │ │ case..."     │ │ │
│ │   overview    │ │  │ Policy [1], passwords must   │    │ │ │              │ │ │
│ │ ▸ Travel      │ │  │ meet these requirements:     │    │ │ │ [📄 Open]    │ │ │
│ │   policy      │ │  │                              │    │ │ └──────────────┘ │ │
│ │               │ │  │ • Minimum 12 characters [1]  │    │ │                  │ │
│ │               │ │  │ • Mixed case, numbers, and   │    │ │ ┌──────────────┐ │ │
│ │               │ │  │   special characters [1]     │    │ │ │ [2] Security │ │ │
│ │               │ │  │ • Rotated every 90 days [2]  │    │ │ │ Policy v2.1  │ │ │
│ │               │ │  │ • Cannot reuse last 12       │    │ │ │ p. 12        │ │ │
│ │               │ │  │   passwords [2]              │    │ │ │              │ │ │
│ │               │ │  │                              │    │ │ │ "Password    │ │ │
│ │               │ │  │      [👍] [👎]               │    │ │ │ rotation     │ │ │
│ │               │ │  └──────────────────────────────┘    │ │ │ policy: 90   │ │ │
│ │               │ │                                      │ │ │ day cycle..."│ │ │
│ │               │ │  ┌──────────────────────────────┐    │ │ │              │ │ │
│ │               │ │  │ 👤 You              10:32 AM │    │ │ │ [📄 Open]    │ │ │
│ │               │ │  │                              │    │ │ └──────────────┘ │ │
│ │               │ │  │ Does this apply to service   │    │ │                  │ │
│ │               │ │  │ accounts too?                │    │ │                  │ │
│ │               │ │  └──────────────────────────────┘    │ │                  │ │
│ │               │ │                                      │ │                  │ │
│ │               │ │  ┌──────────────────────────────┐    │ │                  │ │
│ │               │ │  │ 🤖 KnowFlow                  │    │ │                  │ │
│ │               │ │  │ ●●● Thinking...               │    │ │                  │ │
│ │               │ │  └──────────────────────────────┘    │ │                  │ │
│ │               │ │                                      │ │                  │ │
│ │               │ │                                      │ │                  │ │
│ │               │ │ ┌──────────────────────────────────┐ │ │                  │ │
│ │               │ │ │ Ask a question...          [Send]│ │ │                  │ │
│ │               │ │ └──────────────────────────────────┘ │ │                  │ │
│ │               │ │                                      │ │                  │ │
│ │               │ │ ⚠ Answers are generated from        │ │                  │ │
│ │               │ │ documents you have access to.        │ │                  │ │
│ │               │ │ Always verify citations.             │ │                  │ │
│ │               │ │                                      │ │                  │ │
│ └───────────────┘ └──────────────────────────────────────┘ └──────────────────┘ │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Three-panel layout: Conversations sidebar (left), Chat area (center), Sources/Citations panel (right)
- Conversations sidebar lists past conversations grouped by date; "+ New Chat" creates a new conversation
- Chat area shows message bubbles with role icon (user/assistant), timestamp
- `●●● Thinking...` streaming indicator during model generation
- Citation references `[1]`, `[2]` in assistant messages are clickable — selecting one highlights the corresponding source in the right panel
- Each citation card shows: document title, version, page range, text snippet, and "Open" link to the document
- Feedback controls: 👍/👎 on each assistant message. Clicking 👎 opens a modal with reason codes (INCORRECT, INCOMPLETE, UNSUPPORTED_CITATION, OUTDATED_SOURCE, PERMISSION_ISSUE) and optional comment
- Disclaimer bar at bottom: "Answers are generated from documents you have access to. Always verify citations."
- Rate limit: if user exceeds 30 questions/hour, input is disabled with a message
- Conversation title auto-generated from first message, editable

---

## 9. Admin Panel

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 AD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────┐ ┌──────────────────────────────────────────────────────────────┐│
│ │ Admin        │ │                                                              ││
│ │              │ │  Users                                          [+ Add User] ││
│ │ ▸ Users    ● │ │                                                              ││
│ │ ▸ Roles      │ │  ┌──────────────────────────────────────────────────────┐    ││
│ │ ▸ Departments│ │  │ 🔍  Search users...                                 │    ││
│ │ ▸ Categories │ │  └──────────────────────────────────────────────────────┘    ││
│ │ ▸ Tags       │ │                                                              ││
│ │ ▸ Audit Logs │ │  │ Name          │ Email             │ Dept       │ Roles    ││
│ │ ▸ Jobs       │ │  │               │                   │            │ Status   ││
│ │ ▸ System     │ │  ├───────────────┼───────────────────┼────────────┼──────────┤│
│ │              │ │  │ Jane Doe      │ jane@example.com  │ Governance │ KM, APPR ││
│ │              │ │  │               │                   │            │ ✅ Active ││
│ │              │ │  │ Alice Smith   │ alice@example.com │ HR         │ EMPLOYEE ││
│ │              │ │  │               │                   │            │ ✅ Active ││
│ │              │ │  │ Bob Lee       │ bob@example.com   │ Engineering│ KM       ││
│ │              │ │  │               │                   │            │ ✅ Active ││
│ │              │ │  │ Charlie Wang  │ charlie@example.. │ Compliance │ EMPLOYEE ││
│ │              │ │  │               │                   │            │ ⛔ Locked ││
│ │              │ │  │ Diana Kim     │ diana@example.com │ Operations │ EMPLOYEE ││
│ │              │ │  │               │                   │            │ ✅ Active ││
│ │              │ │                                                              ││
│ │              │ │  Showing 1–5 of 24 users        [< Prev] 1 2 3 4 [Next >]   ││
│ │              │ │                                                              ││
│ │              │ │  ── User Detail (inline expand) ──────────────────────────── ││
│ │              │ │                                                              ││
│ │              │ │  Name: Jane Doe                                              ││
│ │              │ │  Email: jane@example.com                                     ││
│ │              │ │  Department: [Governance ▾]                                  ││
│ │              │ │  Status: [Active ▾]                                          ││
│ │              │ │  Roles: [☑ EMPLOYEE] [☑ KNOWLEDGE_MANAGER] [☑ APPROVER]     ││
│ │              │ │         [☐ ADMIN]                                            ││
│ │              │ │  Created: Oct 1, 2024                                        ││
│ │              │ │  Last Login: Oct 28, 2024                                    ││
│ │              │ │                                                              ││
│ │              │ │  [Save Changes]  [Reset Password]  [Deactivate]              ││
│ │              │ │                                                              ││
│ └──────────────┘ └──────────────────────────────────────────────────────────────┘│
│                                                                                  │
│  ── Audit Logs (when selected) ────────────────────────────────────────────────  │
│                                                                                  │
│  Filters: [Date Range 📅] [Actor ▾] [Action ▾] [Entity Type ▾]  [Export CSV]    │
│                                                                                  │
│  │ Timestamp           │ Actor      │ Action         │ Entity      │ Detail     │ │
│  │ Oct 28 14:30:22 UTC │ jane@..    │ DOCUMENT_CREATE│ doc/abc-123 │ {title:..} │ │
│  │ Oct 28 14:29:10 UTC │ alice@..   │ LOGIN_SUCCESS  │ user/def-456│ {ip:..}    │ │
│  │ Oct 28 14:25:05 UTC │ system     │ INGESTION_DONE │ ver/ghi-789 │ {chunks:47}│ │
│  │ Oct 28 14:20:00 UTC │ bob@..     │ PERMISSION_ADD │ doc/abc-123 │ {level:VIEW}││
│  │ Oct 28 14:15:33 UTC │ jane@..    │ APPROVAL_SUBMIT│ apr/jkl-012 │ {version:3}│ │
│                                                                                  │
│  ── Jobs Monitor (when selected) ──────────────────────────────────────────────  │
│                                                                                  │
│  Active: 2/4 slots   │ Pending: 5  │ Failed: 1  │ Succeeded: 142               │
│                                                                                  │
│  │ Job ID     │ Document          │ Status   │ Attempts │ Started     │ Action  │ │
│  │ job-001    │ Security Policy v3│ 🟢 RUNNING│ 1/3      │ 14:28 UTC   │         │ │
│  │ job-002    │ HR Benefits v2    │ 🟢 RUNNING│ 1/3      │ 14:27 UTC   │         │ │
│  │ job-003    │ API Standards v2  │ 🟡 PENDING│ 0/3      │ —           │         │ │
│  │ job-004    │ Travel Policy v1  │ 🔴 FAILED │ 3/3      │ 14:10 UTC   │ [Retry] │ │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- Sidebar navigation for admin sections; active section indicated with ● marker
- **Users**: CRUD operations, role assignment via checkboxes, department assignment, status management
- **Roles**: read-only display of system roles (EMPLOYEE, KNOWLEDGE_MANAGER, APPROVER, ADMIN) — roles are seeded, not user-created
- **Departments**: CRUD with name + description
- **Categories**: CRUD with name + description
- **Tags**: list with usage count, merge/delete capabilities
- **Audit Logs**: filterable, sortable table with export. Detail column shows JSONB preview. No delete capability (append-only)
- **Jobs**: real-time ingestion pipeline monitor. Shows slot utilization, queue depth, failed jobs with retry button
- **System**: configuration overview (non-editable), health check status, version info
- Only accessible to users with `ADMIN` role — menu item hidden for non-admins

---

## 10. Notification Inbox

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ ◆ KnowFlow    [Knowledge Library] [Approvals] [Tasks] [Copilot]   🔔 3  👤 JD ▾│
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Notifications                          [Mark All Read]   Filter: [All ▾]        │
│  3 unread                                                                        │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ ● ✅ Approval Granted                                          2 hrs ago  │  │
│  │   Your document "Security Policy v2.1" has been approved by              │  │
│  │   Admin User. The document is now live.                                   │  │
│  │   → View Document                                                         │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ ● 📋 Task Assigned                                              5 hrs ago │  │
│  │   You have been assigned a new task: "Review Q3 compliance doc"           │  │
│  │   Priority: HIGH · Due: Oct 30                                            │  │
│  │   → View Task                                                             │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │ ● 📤 Approval Requested                                       yesterday  │  │
│  │   Alice Smith has submitted "Onboarding Guide v1.0" for your              │  │
│  │   review and approval.                                                    │  │
│  │   → View Approval                                                         │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │   📄 Document Updated                                          2 days ago │  │
│  │   Bob Lee uploaded version 3 of "Architecture Decision Log".              │  │
│  │   → View Document                                                         │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │   ❌ Approval Rejected                                         3 days ago │  │
│  │   Your document "Brand Guidelines v1.0" was rejected by                   │  │
│  │   Jane Doe. Reason: "Missing accessibility section".                      │  │
│  │   → View Approval                                                         │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐  │
│  │   🔄 Ingestion Complete                                        3 days ago │  │
│  │   "Data Retention Policy v3.0" has been processed successfully.           │  │
│  │   47 chunks extracted and embedded.                                       │  │
│  │   → View Document                                                         │  │
│  └────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  Showing 1–6 of 18 notifications          [< Prev]  1  2  3  [Next >]           │
│                                                                                  │
│  ── Notification Preferences ──────────────── [⚙ Manage Preferences]            │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Behavior Notes:**
- ● (blue dot) indicates unread notifications; disappears when the notification is viewed
- Each notification shows: type icon, title, descriptive message, relative timestamp, link to related entity
- "Mark All Read" bulk-marks all notifications as read
- Filter dropdown: All, Unread, Approvals, Tasks, Documents, System
- Notification types: APPROVAL_GRANTED, APPROVAL_REJECTED, APPROVAL_REQUESTED, CHANGES_REQUESTED, TASK_ASSIGNED, TASK_DUE_SOON, DOCUMENT_UPDATED, INGESTION_COMPLETE, INGESTION_FAILED
- Clicking "→ View Document/Task/Approval" navigates to the linked entity
- Deduplication: `dedupe_key` prevents duplicate notifications (e.g., multiple edits to same document in short window)
- "Manage Preferences" links to settings where user can enable/disable notification types
- Pagination: default 20 per page
- Real-time updates via polling (every 30 seconds) or WebSocket in v2

---

## Responsive Behavior (General)

| Breakpoint | Behavior |
|------------|----------|
| Desktop (≥1280px) | Full layout as shown in wireframes |
| Tablet (768–1279px) | Copilot: sources panel collapses to bottom sheet. Admin: sidebar becomes hamburger menu. Board: horizontal scroll. |
| Mobile (<768px) | Single-column layout. Board view defaults to list view. Chat sources panel hidden behind a toggle. Sidebar navigation becomes a bottom tab bar. |

---

## Navigation Map

```
Login ──→ Dashboard
              │
              ├──→ Knowledge Library ──→ Document Detail ──→ Upload Flow
              │
              ├──→ Approval Inbox
              │
              ├──→ Task Workspace
              │
              ├──→ Copilot Chat
              │
              ├──→ Notification Inbox
              │
              └──→ Admin Panel (ADMIN only)
                    ├── Users
                    ├── Roles
                    ├── Departments
                    ├── Categories
                    ├── Tags
                    ├── Audit Logs
                    ├── Jobs
                    └── System
```
