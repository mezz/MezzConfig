package net.mezzdev.config;

public interface IJeiClientConfigs {
	IClientConfig getClientConfig();

	IIngredientFilterConfig getIngredientFilterConfig();

	IIngredientGridConfig getIngredientListConfig();

	IIngredientGridConfig getBookmarkListConfig();

	void onRuntimeStopped();
}
