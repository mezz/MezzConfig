package mezz.jei.common.config;

import mezz.jei.api.constants.ModIds;
import net.mezzdev.config.api.value.ConfigValueEditorType;

/**
 * JEI-specific config value editor types.
 */
public final class JeiConfigValueEditorTypes {
	public static final ConfigValueEditorType<Alignment> ALIGNMENT = ConfigValueEditorType.create(ModIds.JEI_ID, "alignment");

	private JeiConfigValueEditorTypes() {

	}
}
