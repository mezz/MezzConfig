# Initial Release TODO

- [x] Use the structured config-value codec for server synchronization so strings with commas, empty strings, whitespace, and nested lists round-trip without corruption.
- [ ] Make structured list persistence enforce the complete `IConfigListValueSerializer` contract, including whole-list validation, and document the canonical storage behavior.
- [ ] Mark every implementation package `@ApiStatus.Internal`, including the server package, and remove `@ApiStatus.Internal` from individual types so the annotation is package-only.
- [ ] Minimize the registration and discovery API: expose schemas directly from `Configs`, keep the explicit custom-root overload, and resolve the conventional config root inside the runtime provider.
- [ ] Remove the redundant `addKeyValueList` builder overloads; `addList` already accepts `IConfigKeyValueSerializer` and preserves the complete feature set.
- [ ] Replace dedicated listener interfaces with standard `Consumer` callbacks and add value- and schema-level pending-change listeners.
- [ ] Mark `ISortingConfig` `@ApiStatus.NonExtendable` and document it as a runtime-owned type.
- [ ] Compare `CommonApi` against the latest released API, resolve that baseline from the release repository, and fail when a post-initial-release baseline is missing.
- [ ] Run the NeoForge server GameTests in CI so authoritative config lifecycle and synchronization behavior are exercised before release.
- [ ] Add concise repository and dependency instructions for API consumers to the project README.
