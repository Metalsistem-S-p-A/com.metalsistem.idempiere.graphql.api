# iDempiere GraphQL API

OSGi plugin that exposes [iDempiere ERP](https://www.idempiere.org) functionality through a single **GraphQL** endpoint, deployed as a Jetty web-application bundle at `/graphql`.

## Overview

| | |
|---|---|
| **Bundle** | `com.metalsistem.idempiere.graphql.api` |
| **Version** | 13.0.0 |
| **Java** | 17+ |
| **Context Path** | `/graphql` |
| **Auth** | JWT Bearer tokens (HMAC / RSA) |
| **License** | GPL v2 |

The plugin adopts a **modular contributor architecture**: each functional area (models, processes, cache, workflow, …) is a self-contained `IGraphQLSchemaContributor` that registers its own query/mutation fields and data-fetchers.  
The resulting schema is assembled at runtime by `CompositeGraphQLSchemaBuilder` and cached (with automatic invalidation).

---

## Features

### Queries

| Field | Contributor | Description |
|-------|-------------|-------------|
| `health` | Default | Liveness probe — returns `"OK"` |
| `version` | Default | API version string |
| `healthStatus` | Health | Extended health check with optional DB connectivity test |
| `model` | Model | **Generic SQL-like query builder** for any iDempiere table: `SELECT`, `JOIN`, `WHERE`, `ORDER BY`, pagination. Returns `results`, `totalRecords`, `totalPages`, `page`, `pageSize`. |
| `caches` | Cache | List cache entries (admin-only), filterable by table or name |
| `chartImage` | Chart | Render an `MChart` as a base64 PNG image |
| `file` | File | Return file content as base64 (admin-only, cluster-aware) |
| `menu` | MenuTree | Menu tree hierarchy by tree ID or UUID |
| `nodes` / `nodeInfo` / `nodeLogs` | Node | Cluster node listing, system info, and log access |
| `referenceList` | Reference | AD_Reference value lookup by ID, UUID, or name |
| `servers` / `server` / `serverLogs` / `scheduler` | Server | iDempiere server/scheduler management |
| `statusLines` / `statusLineValue` | StatusLine | AD_StatusLine records and parsed messages |
| `tasks` / `task` | Task | OS task listing and details |
| `workflowNodes` | Workflow | Pending workflow activities for the current user |

### Mutations

| Field | Contributor | Description |
|-------|-------------|-------------|
| `increment` | Default | Demo mutation — increments a number by 1 |
| `beginTransaction` | Default | Start a named transaction, returns `trxId` |
| `commitTransaction` | Default | Commit an open transaction |
| `rollbackTransaction` | Default | Rollback an open transaction |
| `create` | Model | Create a record in any iDempiere table |
| `update` | Model | Update records matching a WHERE clause |
| `delete` | Model | Delete records matching a WHERE clause |
| `execute` | Process | Execute a process/report (by ID, value, or name) with parameters |
| `refreshToken` | Auth | Exchange a refresh token for a new access + refresh token pair |
| `resetCache` | Cache | Reset cache entries by table/record |
| `deleteNodeLogs` / `rotateNodeLogs` / `updateNodeLogLevel` | Node | Cluster node log management |
| `changeServerState` / `runServer` / `reloadServers` | Server | Start/stop/reload iDempiere servers |
| `addScheduler` / `removeScheduler` | Server | Add/remove schedulers |
| `runTask` | Task | Execute an OS task |
| `approveWorkflowNode` / `rejectWorkflowNode` / `setWorkflowUserChoice` / `acknowledgeWorkflowNode` / `forwardWorkflowNode` | Workflow | Workflow activity actions |

---

## Architecture

```
GraphQLResource (JAX-RS POST /graphql)
  └─ GraphQLSchemaProvider
       └─ CompositeGraphQLSchemaBuilder
            ├─ DefaultGraphQLContributor   (health, version, transactions)
            ├─ AuthContributor             (token refresh)
            ├─ ModelContributor            (CRUD + query)
            ├─ ProcessContributor          (process execution)
            ├─ CacheContributor            (cache management)
            ├─ ChartContributor            (chart rendering)
            ├─ FileContributor             (file access)
            ├─ HealthContributor           (health status)
            ├─ ReferenceContributor        (AD_Reference lookup)
            ├─ MenuTreeContributor         (menu tree)
            ├─ StatusLineContributor       (status lines)
            ├─ TaskContributor             (OS tasks)
            ├─ NodeContributor             (cluster nodes)
            ├─ ServerContributor           (server management)
            └─ WorkflowContributor         (workflow activities)
```

### Key Packages

| Package | Purpose |
|---------|---------|
| `api.application` | OSGi activator, JAX-RS Application |
| `api.auth` | JWT authentication (RequestFilter, TokenUtils), token secret providers |
| `api.converters` | Type converters (`ITypeConverter`, `DateTypeConverter`) |
| `api.model` | Table models (GraphQL_AuthToken, GraphQL_RefreshToken), event handler |
| `api.process` | Administrative processes (token activation, expiration, server secret rotation) |
| `api.util` | Utilities — error builder, transaction manager, paging helpers, cluster utils |
| `query` | `GraphQLQueryBuilder` (core query/mutation engine), `GraphQLInputTypes` (schema input types) |
| `resource` | Schema contributors (one per functional area), process runner |
| `schemaprovider` | Schema assembly, caching, contributor interface |

---

## Authentication

All requests (except `refreshToken`) require a `Bearer` JWT token in the `Authorization` header.

```
Authorization: Bearer <token>
```

### Token Configuration (SysConfig)

| Key | Default | Description |
|-----|---------|-------------|
| `GRAPHQL_TOKEN_EXPIRE_IN_MINUTES` | 60 | Access token expiry (minutes) |
| `GRAPHQL_TOKEN_ABSOLUTE_EXPIRE_IN_MINUTES` | 10080 | Absolute session timeout (1 week) |
| `GRAPHQL_REFRESH_TOKEN_EXPIRE_IN_MINUTES` | 1440 | Refresh token inactivity timeout (1 day) |

Token secrets can be customized by registering an `ITokenSecretProvider` OSGi service; the default implementation reads from `SysConfig`.

---

## Usage Examples

### Health Check

```graphql
query {
  health
}
```

### Query Records (SQL-like)

```graphql
query {
  model(
    table: "C_BPartner"
    select: "C_BPartner_ID, Name, Value, IsActive"
    where: {
      and: [
        { field: "IsActive", operator: EQ, value: true }
        { field: "Name", operator: CONTAINS, value: "Metal" }
      ]
    }
    orderBy: "Name ASC"
    pageSize: 20
    page: 0
  )
}
```

Response:

```json
{
  "data": {
    "model": {
      "results": [ { "c_bpartner_id": 123, "name": "Name", "value": "MS", "isactive": true } ],
      "totalRecords": 1,
      "totalPages": 1,
      "page": 0,
      "pageSize": 20
    }
  }
}
```

### Query with JOINs

```graphql
query {
  model(
    table: "M_Product"
    select: "M_Product.Name, asi.Description"
    join: [
      {
        table: "M_AttributeSetInstance"
        alias: "asi"
        type: LEFT
        on: "M_Product.M_AttributeSetInstance_ID = asi.M_AttributeSetInstance_ID"
      }
    ]
    where: { field: "M_Product.IsActive", operator: EQ, value: true }
  )
}
```

### Create Record

```graphql
mutation {
  create(model: {
    table: "C_BPartner"
    values: [
      { column: "Value", value: "BP001" }
      { column: "Name", value: "New Partner" }
      { column: "IsCustomer", value: true }
    ]
  })
}
```

### Update Records

```graphql
mutation {
  update(model: {
    table: "C_BPartner"
    values: [
      { column: "Name", value: "Updated Name" }
    ]
    where: { field: "Value", operator: EQ, value: "BP001" }
  })
}
```

### Delete Records

```graphql
mutation {
  delete(model: {
    table: "C_BPartner"
    where: { field: "Value", operator: EQ, value: "BP001" }
  })
}
```

### Execute a Process

```graphql
mutation {
  execute(process: {
    value: "AD_Synchronize"
    parameters: [
      { name: "IsAllLanguages", value: true }
    ]
  })
}
```

### Transaction Control

```graphql
# Start a transaction
mutation { beginTransaction(prefix: "myTrx") }

# Use it in mutations
mutation { create(model: { table: "C_Order", values: [...] }, trxId: "myTrx_abc123") }

# Commit or rollback
mutation { commitTransaction(trxId: "myTrx_abc123") }
mutation { rollbackTransaction(trxId: "myTrx_abc123") }
```

### Workflow Actions

```graphql
# List pending workflow activities
query {
  workflowNodes
}

# Approve a workflow node
mutation {
  approveWorkflowNode(nodeId: 12345)
}
```

---

## WHERE Clause Syntax

The `where` input supports nested logical operators:

```graphql
where: {
  or: [
    { field: "Status", operator: EQ, value: "CO" }
    {
      and: [
        { field: "DocStatus", operator: EQ, value: "DR" }
        { field: "TotalLines", operator: GT, value: 1000 }
      ]
    }
  ]
}
```

### Supported Operators

| Operator | SQL Equivalent | Notes |
|----------|---------------|-------|
| `EQ` | `=` | Equal |
| `NEQ` | `!=` | Not equal |
| `GT` | `>` | Greater than |
| `GE` | `>=` | Greater or equal |
| `LT` | `<` | Less than |
| `LE` | `<=` | Less or equal |
| `IN` | `IN (...)` | Value must be an array |
| `CONTAINS` | `LIKE '%val%'` | Case-insensitive substring |
| `STARTSWITH` | `LIKE 'val%'` | Starts with |
| `ENDSWITH` | `LIKE '%val'` | Ends with |

Values accept native JSON types (string, number, boolean, null, array) — type conversion to the target column's database type is automatic.

---

## JOIN Syntax

```graphql
join: [
  {
    table: "TableName"       # Required: table to join
    type: INNER              # Optional: INNER (default), LEFT, RIGHT, FULL, CROSS
    alias: "t"               # Optional: alias (defaults to lowercase table name)
    on: "A.FK_ID = t.PK_ID" # Required (except CROSS): join condition
  }
]
```

---

## Building

The plugin builds as an Eclipse/Tycho plugin bundled in the iDempiere target platform.

```bash
mvn clean verify
```

### Dependencies

| Library | Version |
|---------|---------|
| graphql-java | 25.0 |
| graphql-java-extended-scalars | 24.0 |
| java-dataloader | 6.0.0 |
| java-jwt (Auth0) | 4.5.0 |
| jwks-rsa (Auth0) | 0.22.0 |
| reactive-streams | 1.0.4 |
| Jersey (JAX-RS) | 2.34 |
| Jackson | 2.15.4 |

---

## Deployment

1. Build the plugin with Maven.
2. Copy the bundle to the iDempiere `plugins/` directory (or deploy via the OSGi console).
3. The endpoint is available at `https://<host>/graphql`.
4. A `GET` to `/graphql` returns a plain-text health message; all GraphQL operations use `POST`.

---

## Extending the API

To add new queries/mutations:

1. Create a class implementing `IGraphQLSchemaContributor`.
2. Implement `getQueryFields()`, `getMutationFields()`, and `registerDataFetchers()`.
3. Register the contributor in `CompositeGraphQLSchemaBuilder`.

```java
public class MyContributor implements IGraphQLSchemaContributor {

    @Override
    public String getContributorName() { return "my-feature"; }

    @Override
    public GraphQLFieldDefinition[] getQueryFields() {
        return new GraphQLFieldDefinition[] {
            newFieldDefinition()
                .name("myQuery")
                .type(ExtendedScalars.Json)
                .argument(newArgument().name("param").type(GraphQLString).build())
                .build()
        };
    }

    @Override
    public GraphQLFieldDefinition[] getMutationFields() {
        return new GraphQLFieldDefinition[0];
    }

    @Override
    public void registerDataFetchers(GraphQLCodeRegistry.Builder registry) {
        registry.dataFetcher(
            FieldCoordinates.coordinates("Query", "myQuery"),
            (DataFetcher<Object>) env -> { /* ... */ }
        );
    }
}
```

---

## License

GNU General Public License v2.0 — see the license headers in source files.

## Credits

- **Metalsistem S.p.A.** — plugin development and maintenance
- Based on concepts from the iDempiere REST API by **Trek Global Corporation** (Heng Sin Low)

