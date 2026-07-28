# Architectural Rule: Tenant Data Isolation Enforcement

## Overview
Unc Gateway employs a **shared-schema pool multi-tenancy model**. All tenant data co-exists within a unified PostgreSQL database (`unc_db`), isolated using the `tenant_id` discriminator column present on all tenant-owned tables (`services`, `routes`, `consumers`, `plugin_configs`, `request_logs`).

---

## 🔒 Mandatory Isolation Rules

### 1. Read Operations (SELECT)
- **Rule**: Every query, count, fetch, or join against `services`, `routes`, `consumers`, `plugin_configs`, or `request_logs` **MUST** include an explicit `tenant_id` predicate in the `WHERE` clause:
  ```sql
  SELECT * FROM services WHERE tenant_id = :tenantId AND id = :serviceId;
  ```
- **Prohibition**: Queries without a `tenant_id` filter (e.g. `SELECT * FROM services WHERE id = :serviceId`) are strictly prohibited in user-facing APIs. Cross-tenant leakage is treated as a critical security vulnerability.

### 2. Write Operations (INSERT / UPDATE / DELETE)
- **Insert Rule**: Every new row written to `services`, `routes`, `consumers`, `plugin_configs`, or `request_logs` **MUST** explicitly populate the `tenant_id` column with the authenticated caller's verified `tenant_id`:
  ```sql
  INSERT INTO services (id, tenant_id, name, url) VALUES (:id, :tenantId, :name, :url);
  ```
- **Mutation & Deletion Rule**: Every `UPDATE` or `DELETE` statement **MUST** include the `tenant_id` predicate:
  ```sql
  DELETE FROM routes WHERE tenant_id = :tenantId AND id = :routeId;
  ```

### 3. Context Propagation
- The `tenant_id` must be extracted from the authenticated user/API key context (e.g. via security context / request header) at the gateway controller boundary.
- Controller endpoints must pass `tenant_id` to services, repositories, and reactive streams.
