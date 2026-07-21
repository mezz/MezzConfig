package net.mezzdev.config.file;

public interface IConfigListener<T> {
	void onConfigValueChanged(T configValue);
}
