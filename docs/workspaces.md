# Organizations and workspace-scoped knowledge

An organization contains workspaces. Users join organizations and receive separate workspace memberships. Organization membership, including organization Admin, does **not** automatically grant access to workspace knowledge. Creating a workspace grants its creator workspace Admin access.

Each document and conversation belongs to exactly one workspace. Documents optionally belong to one folder. Folders may have a parent folder in the same workspace; selecting a folder in chat includes its descendants. Folder names are unique across a workspace. Conversations are private to their creator, and reading them also requires current workspace membership. Shared chat transcripts are not implemented.

## Permissions

| Operation | Admin | Editor | Viewer |
| --- | --- | --- | --- |
| Read workspace knowledge and ask questions | Yes | Yes | Yes |
| Upload, move or delete documents | Yes | Yes | No |
| Create/edit/nest folders | Yes | Yes | No |
| Manage workspace members and roles | Yes | No | No |
| Edit settings or delete workspace | Yes | No | No |

Only organization Admins create workspaces and add organization members. Workspace Admins can assign roles only to existing, active members of that organization. The last workspace Admin cannot be removed/demoted. Personal workspaces cannot be shared or deleted.

The membership UI currently adds **registered accounts**, first at organization level and then at workspace level. It does not send email, create accounts, or implement invitation acceptance. Organization role delegation/removal, expiring invitations, per-document grants, SSO and row-level security policies remain future work. There are no separate folder permissions: all workspace members can read all knowledge within their workspace.

## UI and scope

The Workspaces page lists only accessible workspaces, with real counts and an organization filter. A workspace has Overview, Knowledge, Assistant, Members and Settings tabs. The sidebar switches the active workspace. Knowledge includes folder parent selection, descendant-aware folder filters and file-type/name filters. The Assistant keeps individual-file selection and folder selection separate; they form a union without duplicate chunks.

Every document/folder/chat request sends `X-Workspace-Id`. Query cache keys include the workspace ID, and components reset drafts/selections when switching. The server validates the ID against the authenticated session; the client header is not trusted authorization. Omitting it selects only the current user's private Personal workspace for compatibility, never an arbitrary shared workspace. Malformed IDs return 400, unknown/inaccessible IDs return 404, and insufficient write roles return 403.

## Server enforcement

- `WorkspaceAccess` resolves the authenticated user's active workspace and organization memberships. Services authorize reads and mutations independently of React.
- Vector retrieval SQL includes workspace ID, organization consistency, active workspace membership, active organization membership and non-deleted workspace conditions **before** ranking and the passage limit. Selected file/folder predicates cannot override these conditions.
- Nested folder expansion is restricted to the requested workspace. Parent writes lock the workspace and reject self/descendant cycles.
- No database lock is held during extraction, embedding or LLM calls. Uploads and chat revalidate membership/role versions in the final transaction. Revocation or role changes during generation prevent persistence and return an error instead of the generated text.
- Membership changes and final writes take the same workspace row lock. Access to stored source excerpts requires both current workspace membership and conversation authorship.

## Schema and migration

V8 adds `organizations`, `organization_members`, `workspaces`, `workspace_members`, workspace/organization IDs on knowledge and conversations, and folder parent links. The existing `owner_id` columns retain creator provenance and are exposed as `createdBy`; they are no longer the shared-document permission predicate. Conversations retain this field for private authorship checks.

Composite foreign keys enforce workspace/organization consistency for membership, folder parents, document-folder links, conversation-message links and source selections. Creators reference organization membership records, which remain present when access is revoked. A persistent `workspace_resources` registry allows deleted-document source snapshots to retain tenant-safe references without retaining the live indexed document.

Existing users each receive a Personal organization and Personal workspace, containing their existing documents, folders, conversations and source snapshots. New users are provisioned lazily, with concurrency-safe locking. Anonymous legacy conversations whose creator is unknown remain in an inaccessible, deleted quarantine workspace; attribution is not fabricated.

Workspace deletion is a **soft deletion**: access is revoked for everyone, while stored data and references remain for retention. There is no restoration or permanent purge UI in this version. Deleted workspace names remain reserved in that organization.

V8 is a Java Flyway migration with SQL in `db/schema/workspaces.sql`, to remove the old automatically named account-level folder uniqueness constraint portably. Do not edit this migration after it has been applied; use another migration for future changes.

## Rollout and verification

No running services are restarted by implementation. Back up the existing PostgreSQL database before applying this schema migration. From the backend directory:

```sh
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > /tmp/contextiq-before-workspaces.dump
# After the backup succeeds:
docker compose up -d --build --force-recreate --no-deps backend
```

No database recreation or re-embedding is required. Refresh the frontend after startup; its workspace provider requires the updated endpoints.

```sh
./mvnw test
cd ../ContextIQ-frontend
npm run build
```

Tests cover migration of existing records, private conversations, role enforcement, cross-workspace source rejection, cross-organization foreign-key violations, nested-folder cycles and membership revocation during generation. Retrieval SQL tests substitute only vector-distance arithmetic under H2 to exercise authorization/filtering/order/limits. They do not replace a live PostgreSQL/pgvector end-to-end check.

Manual acceptance: create HR and Finance under an organization; add a registered employee to the organization and HR only. Verify that HR viewers can ask questions but cannot upload/move/delete. Finance files and source IDs must fail when requested under HR, even with modified headers or JSON. Remove HR membership and confirm history/source access is denied. Switch repeatedly and reload to check that UI results remain workspace-scoped.
