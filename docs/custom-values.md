# Custom config values

Prefer the built-in value helpers whenever they describe the setting correctly.
Create a serializer only when a mod needs to store and validate its own
effectively immutable value type.

[Back to the API guide](API.md)

## Built-in values

`IConfigCategoryBuilder` provides scalar and list helpers for:

| Type | Notes |
| --- | --- |
| `boolean` and `String` | Unrestricted scalar and list values. |
| `int`, `long`, and `double` | Optional inclusive minimum and maximum. Doubles must be finite. |
| `PackedColor` | RGB (`0xRRGGBB`) or ARGB (`0xAARRGGBB`). |
| enums | All constants, or an explicit non-empty subset. |
| lists | Typed lists of every built-in value type. |

Built-in lists are copied when declared, loaded, or updated and are returned as
unmodifiable snapshots.

## Implement a serializer

`IConfigValueSerializer<T>` defines storage, validation, and the metadata a
config editor needs for one type.

This example stores a positive `java.time.Duration`:

```java
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigValueSerializer;

import java.time.Duration;
import java.time.format.DateTimeParseException;

public final class DurationSerializer
	implements IConfigValueSerializer<Duration> {

	@Override
	public String serialize(Duration value) {
		return value.toString();
	}

	@Override
	public IDeserializeResult<Duration> deserialize(String text) {
		try {
			Duration value = Duration.parse(text);
			if (!isValid(value)) {
				return IDeserializeResult.failure(
					"Duration must be greater than zero."
				);
			}
			return IDeserializeResult.success(value);
		} catch (DateTimeParseException e) {
			return IDeserializeResult.failure(
				"Expected an ISO-8601 duration such as PT30S."
			);
		}
	}

	@Override
	public boolean isValid(Duration value) {
		return !value.isNegative() && !value.isZero();
	}

	@Override
	public String getValidValuesDescription() {
		return "a positive ISO-8601 duration";
	}
}
```

Pass the serializer directly to `addValue`:

```java
IConfigValueSerializer<Duration> durations = new DurationSerializer();

IConfigValue<Duration> timeout = general.addValue(
	"timeout",
	Duration.ofSeconds(30),
	durations
).build();
```

There is no global serializer registration. The value builder owns the
serializer supplied for that setting.

## Serializer contract

A serializer must satisfy all of these rules:

- accepted values are effectively immutable and have stable equality;
- `serialize` is deterministic;
- every accepted value serializes and deserializes back to an equal value
  without diagnostics;
- `deserialize` reports bad input through `IDeserializeResult` instead of
  throwing;
- every deserialized result passes `isValid`;
- shared serializer instances are thread-safe.

For a sorting config, serialized text is also the value's persistent identity:
equal values must serialize identically, and unequal values must not serialize
to the same text.

## Deserialization outcomes

Return one of the factory outcomes on `IDeserializeResult`:

```java
IDeserializeResult.success(value);
IDeserializeResult.partialSuccess(repairedValue, "Ignored unknown field.");
IDeserializeResult.failure("Expected a positive duration.");
```

A partial success preserves a usable value while reporting why the stored input
needs correction. A failure has diagnostics but no value. MezzConfig can use
the diagnostics when repairing a config file.

## Ranges and fixed choices

Serializers can provide optional editor metadata:

- `getRange()` describes an inclusive bounded control;
- `getAllValidValues()` describes a fixed selection;
- `getValidValuesDescription()` explains accepted input.

The metadata does not replace validation. `isValid` remains authoritative for
every update and loaded value.

## Lists of custom values

Use `addList` when every element uses the same serializer:

```java
IConfigValue<List<Duration>> retryDelays = general.addList(
	"retryDelays",
	List.of(Duration.ofSeconds(1), Duration.ofSeconds(5)),
	durations
).build();
```

MezzConfig stores and validates each element with the supplied serializer. The
resulting list serializer implements `IConfigListValueSerializer`, allowing an
editor integration to inspect the element serializer.

Lists are ordered by default. If order does not change the setting's meaning,
declare it explicitly:

```java
IConfigValue<List<Duration>> ignoredDurations = general.addList(
	"ignoredDurations",
	List.of(),
	durations,
	ConfigListOrdering.UNORDERED
).build();
```

MezzConfig still preserves physical file order. The ordering flag tells editor
integrations whether reordering controls are meaningful.

Implement `IConfigListValueSerializer<T>` directly only when the complete list
needs custom validation or metadata beyond an element serializer.

## Key-value entries

An entry type can implement `IConfigKeyValueSerializer<T, K, V>` so config
editors can expose separate key and value controls while the mod keeps an
ordered list or domain-specific entry type.

The serializer supplies:

- serializers for the key and value components;
- functions to read both components from an entry;
- a function to rebuild an entry from edited components.

Every accepted entry must split into valid components, and rebuilding those
components must produce an equal entry. Pass the entry serializer to `addList`
to create a structured list suitable for map-style editing without changing the
mod's value type.
