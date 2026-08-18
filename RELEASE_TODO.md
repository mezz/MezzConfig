# MezzConfig Release TODO

This checklist contains only work owned by the MezzConfig repository. Consumer
reviews from MezzConfigGui and JEI are used as evidence, but changes belonging
to those projects are intentionally excluded.

Every task records why it is needed so that an API-removal suggestion is not
mistaken for a requirement without supporting evidence.

## Decisions Already Made

- Client schemas may use an explicit complete file location, including a global
  location outside the Minecraft installation. Explicit locations are selected
  before a complete builder is returned and cannot be combined with automatic
  per-world placement.
- Both schema-level and value-scoped batch listeners are useful and will remain.
  They serve different subscription scopes described below.
- Sorting will keep an easy string path and gain a supported generic path with
  explicit serialization, rather than exposing an unusable generic interface.
- Existing historical `@since 0.1.0` and `@since 0.2.0` tags will remain even if
  `0.3.0` is the first broadly published release.
- Built-in scalar and list builder conveniences will remain. Removing them
  would require publishing an equivalent serializer-factory surface and would
  not materially reduce the supported API.

## P0 — Release Blockers

### Keep Server Schemas Synchronizable

- [x] Prevent every public operation from creating an authoritative server
      snapshot that the protocol cannot encode.

  **Reason:** The protocol rejects more than 4,096 values, serialized values
  larger than 256 KiB, payloads larger than 1 MiB, and payloads that cannot fit
  within 64 fragments. These limits are currently enforced only during
  encoding, after authoritative state may already have changed. An oversized
  schema can therefore fail to activate on clients, a reload can leave clients
  stale, and a local update future can report success even though broadcasting
  failed.

  - [x] Centralize validation of a complete prospective snapshot using the
        existing internal protocol limits.
  - [x] Validate a server-owned world schema before registration commits.
  - [x] Validate prospective state before an initial file load or watcher
        reload commits it.
  - [x] Validate prospective state before local, integrated-server, or remote
        batch updates commit it.
  - [x] Reject the entire operation and retain the previous authoritative state
        when validation fails.
  - [x] Reject an unsynchronizable request before sending; an integrated-server
        race completes its already-created future exceptionally.
  - [x] Keep the numeric limits internal while returning actionable failure
        messages.
  - [x] Test excessive value count, excessive individual values, excessive
        total size, fragment boundaries, registration, reloads, local edits,
        remote edits, and successful boundary-sized snapshots through schema
        APIs rather than only through codec tests.

### Make Custom Serializers Safe at Trust Boundaries

- [x] Define and enforce the complete `IConfigValueSerializer` contract.

  **Reason:** Custom deserializers are called directly while reading files and
  network state. A serializer can currently throw or return a successful value
  that fails `isValid`, allowing malformed external input to abort schema
  loading instead of participating in documented recovery.

  - [x] Document `deserialize` as total and non-throwing for arbitrary input.
  - [x] Require success and partial-success results to contain values accepted
        by `isValid`.
  - [x] Document deterministic serialization, stable equality, immutability,
        and round-trip expectations.
  - [x] Convert serializer exceptions at file and network decoding boundaries
        into bounded diagnostics.
  - [x] Reject invalid successful results without applying them.
  - [x] Define how serialization failures during saving and snapshot validation
        are reported.
  - [x] Test throwing serializers, invalid success and partial-success results,
        serialization failures, malformed remote values, and recovery that
        preserves valid neighboring values.

### Reserve Every File Path Once

- [x] Add one path-reservation mechanism shared by schemas and sorting configs.

  **Reason:** Client installation schemas and sorting configs can resolve to the
  same `<root>/<mod-id>/client/<file-name>` path. Schema registration detects
  duplicate schemas, but sorting configs are not registered, so two incompatible
  formats can silently overwrite one another.

  - [x] Reject duplicate schema paths by normalized absolute identity.
  - [x] Reject duplicate sorting-config paths.
  - [x] Reject schema and sorting-config collisions before reading or writing
        the path.
  - [x] Apply the same identity rules when conventional and explicit roots
        resolve to the same location.
  - [x] Keep dedicated-server in-memory sorting configs out of file-path
        reservation.
  - [x] Test every collision combination and verify that rejection happens
        before a file is created or modified.

### Define the Threading Model

- [x] Specify and implement one threading contract for reads, mutations,
      listeners, file reloads, and remote updates.

  **Reason:** File watchers, delayed saves, and network updates could race with
  normal access despite the API's synchronous callbacks.

  **Resolution:** Built runtime objects are thread-safe, batches are atomic, and
  concurrent operations have no defined order. Listeners remain synchronous on
  the applying thread; update futures have no guaranteed completion thread.

  - [x] Synchronize schema, value, sorting, reload, and remote-update state.
  - [x] Make listener registration and removal thread-safe.
  - [x] Cover concurrent batches and runtime callback threads with tests.

## P1 — Complete and Freeze the Public API

### Support Explicit Client Config Locations Coherently

- [x] Make explicit client config locations a first-class builder factory.

  **Reason:** A registration-wide custom root combined independently with
  ownership and scope, creating unsupported or surprising combinations. A
  complete explicit file location is now one concrete alternative to every
  automatically placed schema type.

  - [x] Replace mutable ownership/scope selection with concrete client,
        client-world, and server builder factories.
  - [x] Accept the complete file path in
        `createClientSchemaBuilderAtLocation(Path, String)` without appending an
        owned directory or file name.
  - [x] Capture relative explicit locations as normalized absolute paths before
        returning the builder.
  - [x] Apply the same absolute path identity and collision checks to automatic
        and explicit locations.
  - [x] Preserve owned-directory traversal checks for automatically placed mod
        ids and config file names while allowing explicit locations anywhere.
  - [x] Test absolute and relative explicit locations, malformed and bounded
        file recovery, unavailable files, and path collisions.
  - [x] Document later watcher/save failures for read-only, disconnected, or
        otherwise temporarily unavailable explicit locations.

### Complete Generic Sorting Without Burdening String Callers

- [x] Add a supported generic sorting factory that accepts an
      `IConfigValueSerializer<T>`, while retaining a string convenience
      overload.

  **Reason:** `ISortingConfig<T>` is generic and runtime-owned, but the only
  public producer currently returns `ISortingConfig<String>`. Callers cannot
  obtain the generic feature the interface promises, and cannot implement the
  non-extendable interface themselves.

  - [x] Keep `createSortingConfig` for strings without requiring callers to
        supply a serializer.
  - [x] Add one generic factory taking a serializer, default comparator, and
        removal policy.
  - [x] Refactor the runtime implementation to use the supplied serializer for
        persistence and validation.
  - [x] Require serialized identities to round-trip and unambiguously identify
        sortable values.
  - [x] Preserve reconciliation of new, removed, visible, and hidden runtime
        values for generic types.
  - [x] Keep implementations runtime-owned and non-extendable.
  - [x] Test strings through the convenience path and at least one non-string
        type through the generic path, including malformed files and hidden
        values.

### Promise Deterministic Value Ordering

- [x] Change `IConfigCategory.getConfigValues()` to return an immutable
      `List<? extends IConfigValue<?>>` in builder insertion order.

  **Reason:** The implementation already preserves declaration order and config
  screens use it for layout, but the public `Collection` return type does not
  promise ordering. Changing `Collection` to `List` after release would be
  binary incompatible.

  - [x] Update the interface return type and Javadoc.
  - [x] Update the implementation to return an immutable list snapshot.
  - [x] Test declaration order and immutability.

### Make Schema Activation Semantics Truthful

- [x] Define `isActive`, `canEdit`, and `getPath` for every supported schema
      type, runtime context, and physical side.

  **Reason:** Registration correctly says client schemas are inert and pathless
  on a dedicated server, while `IConfigSchema` currently says every
  installation-scoped schema is active and path-backed. Both statements cannot
  be true without qualification.

  - [x] Correct the `IConfigSchema` Javadocs for inert client declarations on a
        dedicated server.
  - [x] Add a compact behavior table to the API guide.
  - [x] Replace the invalid ownership/scope cross-product with concrete schema
        types.
  - [x] Test active, editable, and path state for each supported schema type.

### Keep Both Listener Scopes and Make Them Discoverable

- [ ] Retain schema-level and value-scoped batch listeners, clarify their names,
      and lock down their notification contracts.

  **Reason:** A schema listener observes every atomic schema batch exactly once,
  which is useful for config screens, persistence adapters, and derived state.
  Emulating it by subscribing to every value can produce duplicate callbacks
  for multi-value batches. A value-scoped batch listener instead runs only when
  its value participated while still exposing the complete batch, avoiding
  repeated filtering in value-specific integrations. Neither scope fully
  replaces the other.

  - [ ] Keep `IConfigValue.addListener` and `addPendingListener` for single-value
        changes.
  - [ ] Keep `IConfigValue.addBatchListener` and `addPendingBatchListener` for
        full-batch context scoped to one participating value.
  - [ ] Keep schema-level effective and pending batch listeners.
  - [ ] Before release, rename schema `addListener` and `addPendingListener` to
        `addBatchListener` and `addPendingBatchListener` for symmetry and
        discoverability; do not retain duplicate aliases on the initial API.
  - [ ] Document exactly-once behavior, which changes trigger each listener,
        callback ordering, synchronous execution, failure isolation,
        unsubscription, and reentrancy.
  - [ ] Test that a schema listener runs once per batch and that a value-scoped
        batch listener runs only when its value is present.

### Remove Redundant Deserialization State

- [ ] Remove `DeserializeResultState` and `IDeserializeResult.getState()`.

  **Reason:** Success, partial success, and failure are already fully determined
  by result presence and diagnostics. The public enum adds a permanent type and
  encourages exhaustive switches that would be fragile if states ever grow,
  without adding information.

  - [ ] Preserve the static result factories and their invariant checks.
  - [ ] Update runtime code to use result presence and diagnostics.
  - [ ] Update tests and documentation.

### Expose Only the Needed Asynchronous Contract

- [ ] Resolve cancellation semantics for `requestBatchUpdate` and return
      `CompletionStage<Void>` unless caller cancellation is intentionally
      supported.

  **Reason:** The method currently exposes a mutable, cancellable
  `CompletableFuture`, while its documented contract only promises eventual
  completion or failure. Local cancellation can stop observation without
  necessarily stopping a queued or already-sent authoritative update.

  - [ ] Decide whether cancellation must prevent an update, merely stop waiting,
        or is unsupported.
  - [ ] If cancellation is unsupported, return `CompletionStage<Void>` and keep
        the implementation's future private.
  - [ ] If cancellation remains public, document and test local, integrated,
        remote, timeout, disconnect, and already-sent behavior.
  - [x] Document completion-thread behavior as part of the threading contract.

## P1 — Packaging and Release Gates

### Make the Standalone API Artifact Self-Describing

- [ ] Remove undeclared annotation dependencies from the API jar or publish
      them correctly.

  **Reason:** API `package-info` classes reference JSR-305 and Minecraft
  nullness annotations, while the API POM declares only JetBrains annotations.
  A shared compile-only consumer should not need undeclared classes merely to
  interpret the API's nullness contract.

  - [ ] Prefer a self-contained package nullness annotation such as JSpecify
        `@NullMarked`, or explicitly declare every required dependency.
  - [ ] Run `jdeps --missing-deps` with the declared dependency classpath and
        require no unexplained missing API signature types.
  - [ ] Validate the generated API POM and a small standalone consumer build.

### Make the Publishing Pipeline Run the Full Release Gate

- [ ] Bring the Jenkins publishing path up to the same validation level as
      GitHub CI.

  **Reason:** GitHub CI runs publication validation and NeoForge server
  GameTests, while Jenkins can publish after the narrower `clean spotlessCheck
  build javadoc` command.

  - [ ] Run `validatePublishing` before Jenkins publication.
  - [ ] Run NeoForge server GameTests before publication, or require a matching
        green GitHub CI commit.
  - [ ] Keep publish, signing, and deployment tasks separate from ordinary
        validation.

### Establish the Post-Release Compatibility Baseline

- [ ] Make the first published `0.3.0` CommonApi jar the required rolling
      baseline for subsequent development.

  **Reason:** Compatibility checking is correctly configured but intentionally
  skips while the initial `0.3.0` artifact is absent. That exemption must not
  silently continue once compatible releases exist.

  - [ ] After publishing `0.3.0`, verify that the checker resolves it from the
        release repository.
  - [ ] On the next development version, keep `apiBaselineVersion=0.3.0` while
        advancing `specificationVersion` so a missing baseline fails the build.
  - [ ] Advance the baseline to the latest released API after each compatible
        release.
  - [ ] Require an explicit versioning decision for intentional compatibility
        breaks instead of disabling or broadly ignoring the check.

### Add Loader Runtime Smoke Coverage

- [ ] Add minimal Fabric and Forge runtime smoke tests for registration and
      startup behavior.

  **Reason:** Shared unit tests and NeoForge GameTests cover most behavior, but
  Fabric- and Forge-specific initialization, services, and networking adapters
  currently receive compile-time validation only.

  - [ ] Verify the runtime provider is discoverable.
  - [ ] Build and load one client schema and one server schema where the loader
        supports the scenario.
  - [ ] Verify a dedicated server keeps client declarations inert.
  - [ ] Keep the smoke tests small; shared behavior remains covered in Common.

## P2 — Documentation and Supported Boundaries

### Correct the Protocol Documentation

- [ ] Change the API guide's protocol version from 2 to 3 and verify all
      documented limits against the implementation.

  **Reason:** The chunk envelope and Forge and NeoForge channels use protocol
  version 3, so the current version-2 statement is factually wrong during
  compatibility troubleshooting.

### State the Stable Package Boundary Explicitly

- [ ] Document that supported API consists of non-internal packages in
      `CommonApi`; public implementation classes in Common and loader artifacts
      are not stable API.

  **Reason:** Java visibility still allows consumers to link public classes from
  package-level `@ApiStatus.Internal` packages. A written boundary prevents
  accidental compatibility obligations outside CommonApi.

  - [ ] Keep implementation and loader packages annotated
        `@ApiStatus.Internal`.
  - [ ] Keep runtime-owned implementation interfaces `@NonExtendable` where
        appropriate.
  - [ ] Ensure examples and tests consume only supported API unless explicitly
        testing internals.

### Complete API Contract Documentation

- [ ] Update Javadocs and the API guide after the contracts above are settled.

  **Reason:** Threading, explicit-location storage, serializer failures, listener
  scopes, asynchronous completion, and server snapshot rejection affect how
  callers safely use the API and must not be left as implementation details.

  - [ ] Document explicit-location failure behavior.
  - [ ] Document serializer requirements and diagnostics.
  - [x] Document thread and listener behavior.
  - [ ] Document update-stage completion and cancellation behavior.
  - [ ] Document server snapshot validation without exposing configurable
        protocol limits.

## Final MezzConfig Verification

The release gate must exercise packaged artifacts and the high-risk behaviors
identified above, not only internal helpers.

- [ ] Run `spotlessCheck`, `build`, CommonApi Javadocs, API compatibility
      checking, and `validatePublishing` from a clean checkout.
- [ ] Run NeoForge server GameTests and the new Fabric and Forge smoke tests.
- [ ] Test dedicated-server, integrated-server, and remote-client config flows.
- [ ] Test oversized snapshot rejection and confirm authoritative state never
      changes on failure.
- [ ] Test throwing custom serializers and malformed-file recovery.
- [ ] Test conventional and explicit locations across every supported schema type.
- [ ] Test schema, sorting, and cross-location path-collision rejection.
- [ ] Test category order and all listener scopes through public APIs.
- [ ] Inspect generated jars and POMs and compile a standalone CommonApi
      consumer against the validated publication repository.
- [ ] Confirm the worktree is clean before tagging the release.
