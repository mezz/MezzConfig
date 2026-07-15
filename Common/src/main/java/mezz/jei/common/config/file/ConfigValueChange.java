package mezz.jei.common.config.file;

import mezz.jei.api.runtime.config.IJeiConfigValue;

public record ConfigValueChange<T>(IJeiConfigValue<T> configValue, T value) {

}
