# Custom config values

A custom value lets the rest of your mod receive something meaningful, such as
a `Duration` or resource ID, instead of repeatedly parsing strings or numbers.

[Back to the API guide](API.md)

## Decide whether a custom value helps

Prefer the built-in helpers for booleans, strings, bounded numbers, packed
colors, enums, and lists of those types. They already provide validation and
enough information for config screens.

Create a custom serializer when the setting has a real type and rules that a
primitive cannot express clearly. A serializer converts between the text in the
config file and the type used by your mod. For example, a timeout may be a
positive `Duration`. Storing an arbitrary string requires parsing it at every
use, while storing a number leaves its unit unclear. With a serializer, the rest
of the mod receives a valid `Duration` directly.

Other good candidates include resource IDs, structured rules, or another small
value object that the feature already uses directly.

## Implement a serializer

`IConfigValueSerializer<T>` tells MezzConfig how to write, read, validate, and
describe one value type:

```java
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
			if (isValid(value)) {
				return IDeserializeResult.success(value);
			}
			return IDeserializeResult.failure(
				"Duration must be greater than zero."
			);
		} catch (DateTimeParseException e) {
			return IDeserializeResult.failure(
				"Expected a duration such as PT30S."
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

Bad file input should produce a failure with a useful message, not escape as an
exception.

## Add the typed setting

Pass the serializer to `addValue` and keep the returned typed value:

```java
IConfigValueSerializer<Duration> durations = new DurationSerializer();

IConfigValue<Duration> timeout = general.addValue(
	"timeout",
	Duration.ofSeconds(30),
	durations
).build();
```

Feature code can now use the value without parsing:

```java
Duration currentTimeout = timeout.getValue();
scheduleRetry(currentTimeout);
```

There is no global serializer registry. Reuse an instance where it makes sense,
or supply a serializer built specifically for one setting.

## Avoid surprising config changes

The same value should always be written the same way, and reading that text
should produce an equal value. Otherwise an unchanged config can appear to
change each time it is loaded or saved.

Use values with stable equality and do not mutate them after handing them to
MezzConfig. Make sure every value returned by `deserialize` also passes
`isValid`, and return a useful failure for bad input instead of throwing an
exception.

For a sorting config, the serialized text is also the value's saved identity.
Equal values must use the same text, and different values must not collide.

## Help config screens edit the value

The serializer can optionally expose an inclusive range or every valid choice.
Config-screen integrations can use that information for sliders and selection
controls. `getValidValuesDescription()` should briefly explain acceptable input
for editors and error messages.

These hints do not replace validation: `isValid` remains the final check for
loaded and edited values.

## Store lists and structured entries

Use `addList` to store a list whose elements use the custom serializer:

```java
IConfigValue<List<Duration>> retryDelays = general.addList(
	"retryDelays",
	List.of(Duration.ofSeconds(1), Duration.ofSeconds(5)),
	durations
).build();
```

Pass `ConfigListOrdering.UNORDERED` only when reordering entries does not change
the setting's meaning. This helps a config screen avoid presenting a useless
reorder control.

For an ordered list of structured rules, an element serializer can implement
`IConfigKeyValueSerializer`. A config screen can then edit the two components as
separate fields while feature code keeps its own entry type and entry order.

Use the published Javadocs for exact serializer contracts and advanced custom
list behavior.
