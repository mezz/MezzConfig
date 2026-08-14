package net.mezzdev.config.ini;

import java.util.List;

public sealed interface IniValue {
	static IniValue scalar(String value) {
		return new Scalar(value);
	}

	static IniValue array(List<IniValue> values) {
		return new Array(values);
	}

	record Scalar(String value) implements IniValue {
		public Scalar {
			if (value == null) {
				throw new NullPointerException("value");
			}
		}
	}

	record Array(List<IniValue> values) implements IniValue {
		public Array {
			values = List.copyOf(values);
		}
	}
}
