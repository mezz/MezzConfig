package net.mezzdev.config.keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.mezzdev.config.ConfigKeyBinding;
import net.mezzdev.config.ConfigKeyModifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;

import java.util.function.Function;

@ApiStatus.Internal
public final class ConfigKeyBindingUtil {
	private ConfigKeyBindingUtil() {

	}

	public static ConfigKeyBinding create(InputConstants.Key key, ConfigKeyModifier modifier) {
		return new ConfigKeyBinding(key.getName(), modifier);
	}

	public static InputConstants.Key getKey(String keyName) {
		try {
			return InputConstants.getKey(keyName);
		} catch (IllegalArgumentException ignored) {
			return InputConstants.UNKNOWN;
		}
	}

	public static InputConstants.Key getKey(KeyMapping keyMapping) {
		return getKey(keyMapping.saveString());
	}

	public static ConfigKeyBinding normalize(
		ConfigKeyBinding value,
		Function<InputConstants.Key, ConfigKeyModifier> getKeyModifier
	) {
		InputConstants.Key key = getKey(value.keyName());
		if (value.isUnbound() || value.modifier() == getKeyModifier.apply(key)) {
			return new ConfigKeyBinding(value.keyName(), ConfigKeyModifier.NONE);
		}
		return value;
	}

	public static Component getDisplayName(
		ConfigKeyBinding value,
		Function<InputConstants.Key, ConfigKeyModifier> getKeyModifier
	) {
		InputConstants.Key key = getKey(value.keyName());
		if (value.modifier() == ConfigKeyModifier.NONE) {
			ConfigKeyModifier keyModifier = getKeyModifier.apply(key);
			if (keyModifier != ConfigKeyModifier.NONE) {
				return getDisplayName(keyModifier);
			}
		}
		return getCombinedName(value.modifier(), getKeyDisplayName(key));
	}

	public static Component getKeyDisplayName(InputConstants.Key key) {
		if (key.getType() == InputConstants.Type.MOUSE) {
			int value = key.getValue();
			if (value == InputConstants.MOUSE_BUTTON_LEFT) {
				return Component.translatable("mezz_config.key.mouse.left");
			} else if (value == InputConstants.MOUSE_BUTTON_RIGHT) {
				return Component.translatable("mezz_config.key.mouse.right");
			}
		}
		if (key.getType() == InputConstants.Type.KEYSYM) {
			int value = key.getValue();
			if (Minecraft.ON_OSX && (value == GLFW.GLFW_KEY_LEFT_SUPER || value == GLFW.GLFW_KEY_RIGHT_SUPER)) {
				return Component.translatable("mezz_config.key.modifier.command");
			}
			return switch (value) {
				case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> Component.translatable("mezz_config.key.modifier.shift");
				case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> Component.translatable("mezz_config.key.modifier.alt");
				case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> Component.translatable("mezz_config.key.modifier.control");
				default -> key.getDisplayName();
			};
		}
		return key.getDisplayName();
	}

	public static ConfigKeyModifier getKeyModifier(InputConstants.Key key) {
		if (!key.getType().equals(InputConstants.Type.KEYSYM)) {
			return ConfigKeyModifier.NONE;
		}
		int keyCode = key.getValue();
		return switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> ConfigKeyModifier.SHIFT;
			case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> ConfigKeyModifier.ALT;
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> ConfigKeyModifier.CONTROL_OR_COMMAND;
			case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> Minecraft.ON_OSX ? ConfigKeyModifier.CONTROL_OR_COMMAND : ConfigKeyModifier.NONE;
			default -> ConfigKeyModifier.NONE;
		};
	}

	public static Component getCombinedName(ConfigKeyModifier modifier, Component component) {
		return switch (modifier) {
			case CONTROL_OR_COMMAND -> Minecraft.ON_OSX ?
				Component.translatable("mezz_config.key.combo.command", component) :
				Component.translatable("mezz_config.key.combo.control", component);
			case SHIFT -> Component.translatable("mezz_config.key.combo.shift", component);
			case ALT -> Component.translatable("mezz_config.key.combo.alt", component);
			case NONE -> component;
		};
	}

	private static Component getDisplayName(ConfigKeyModifier modifier) {
		return switch (modifier) {
			case CONTROL_OR_COMMAND -> Minecraft.ON_OSX ?
				Component.translatable("mezz_config.key.modifier.command") :
				Component.translatable("mezz_config.key.modifier.control");
			case SHIFT -> Component.translatable("mezz_config.key.modifier.shift");
			case ALT -> Component.translatable("mezz_config.key.modifier.alt");
			case NONE -> Component.empty();
		};
	}
}
