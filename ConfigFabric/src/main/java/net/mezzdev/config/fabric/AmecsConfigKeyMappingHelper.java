package net.mezzdev.config.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import de.siphalor.amecs.api.KeyBindingUtils;
import de.siphalor.amecs.api.KeyModifier;
import de.siphalor.amecs.api.KeyModifiers;
import net.mezzdev.config.keybindings.ConfigKeyBinding;
import net.mezzdev.config.keybindings.ConfigKeyModifier;
import net.mezzdev.config.keybindings.ConfigKeyBindingUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class AmecsConfigKeyMappingHelper {
	private AmecsConfigKeyMappingHelper() {

	}

	public static ConfigKeyModifier getBoundModifier(KeyMapping keyMapping) {
		return getOnlyModifier(KeyBindingUtils.getBoundModifiers(keyMapping));
	}

	public static ConfigKeyModifier getDefaultModifier(KeyMapping keyMapping) {
		return getOnlyModifier(KeyBindingUtils.getDefaultModifiers(keyMapping));
	}

	public static void setModifier(KeyMapping keyMapping, ConfigKeyModifier modifier) {
		KeyModifiers modifiers = KeyBindingUtils.getBoundModifiers(keyMapping);
		modifiers.unset();
		modifiers.set(toAmecs(modifier), true);
		modifiers.cleanup(keyMapping);
	}

	public static Component getDisplayName(ConfigKeyBinding value) {
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(value.keyName());
		KeyModifiers modifiers = new KeyModifiers();
		modifiers.set(toAmecs(value.modifier()), true);
		Component component = ConfigKeyBindingUtil.getKeyDisplayName(key);
		for (ConfigKeyModifier modifier : fromAmecs(modifiers)) {
			component = ConfigKeyBindingUtil.getCombinedName(modifier, component);
		}
		return component;
	}

	public static ConfigKeyModifier getKeyModifier(InputConstants.Key key) {
		return fromAmecs(KeyModifier.fromKey(key));
	}

	private static ConfigKeyModifier getOnlyModifier(KeyModifiers modifiers) {
		List<ConfigKeyModifier> configModifiers = fromAmecs(modifiers);
		for (ConfigKeyModifier modifier : configModifiers) {
			if (modifier != ConfigKeyModifier.NONE) {
				return modifier;
			}
		}
		return ConfigKeyModifier.NONE;
	}

	private static List<ConfigKeyModifier> fromAmecs(KeyModifiers modifiers) {
		if (modifiers.isUnset()) {
			return List.of(ConfigKeyModifier.NONE);
		}
		List<ConfigKeyModifier> modifiersList = new ArrayList<>();
		if (modifiers.getAlt()) {
			modifiersList.add(ConfigKeyModifier.ALT);
		}
		if (modifiers.getControl()) {
			modifiersList.add(ConfigKeyModifier.CONTROL_OR_COMMAND);
		}
		if (modifiers.getShift()) {
			modifiersList.add(ConfigKeyModifier.SHIFT);
		}
		return modifiersList;
	}

	private static ConfigKeyModifier fromAmecs(KeyModifier modifier) {
		return switch (modifier) {
			case CONTROL -> ConfigKeyModifier.CONTROL_OR_COMMAND;
			case SHIFT -> ConfigKeyModifier.SHIFT;
			case ALT -> ConfigKeyModifier.ALT;
			case NONE -> ConfigKeyModifier.NONE;
		};
	}

	private static KeyModifier toAmecs(ConfigKeyModifier modifier) {
		return switch (modifier) {
			case CONTROL_OR_COMMAND -> KeyModifier.CONTROL;
			case SHIFT -> KeyModifier.SHIFT;
			case ALT -> KeyModifier.ALT;
			case NONE -> KeyModifier.NONE;
		};
	}
}
