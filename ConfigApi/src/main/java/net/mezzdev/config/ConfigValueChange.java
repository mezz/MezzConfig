package net.mezzdev.config;

public record ConfigValueChange<T>(IJeiConfigValue<T> configValue, T value) {

}
