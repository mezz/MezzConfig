package net.mezzdev.config.neoforge;

import net.mezzdev.config.api.plugin.ConfigPlugin;
import net.mezzdev.config.api.plugin.IConfigPlugin;
import net.mezzdev.config.api.plugin.IServerConfigPlugin;
import net.mezzdev.config.api.plugin.ServerConfigPlugin;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConfigNeoForgePluginFinder {
	private static final Logger LOGGER = LogManager.getLogger();

	private ConfigNeoForgePluginFinder() {

	}

	public static List<IConfigPlugin> getPlugins() {
		return getInstances(ConfigPlugin.class, IConfigPlugin.class, "config plugin");
	}

	public static List<IServerConfigPlugin> getServerPlugins() {
		return getInstances(ServerConfigPlugin.class, IServerConfigPlugin.class, "server config plugin");
	}

	private static <T> List<T> getInstances(Class<?> annotationClass, Class<T> instanceClass, String pluginName) {
		Type annotationType = Type.getType(annotationClass);
		List<ModFileScanData> allScanData = ModList.get().getAllScanData();
		Set<String> pluginClassNames = new LinkedHashSet<>();
		for (ModFileScanData scanData : allScanData) {
			Iterable<ModFileScanData.AnnotationData> annotations = scanData.getAnnotations();
			for (ModFileScanData.AnnotationData annotation : annotations) {
				if (Objects.equals(annotation.annotationType(), annotationType)) {
					String memberName = annotation.memberName();
					pluginClassNames.add(memberName);
				}
			}
		}
		return getInstances(pluginClassNames, instanceClass, pluginName);
	}

	private static <T> List<T> getInstances(Iterable<String> pluginClassNames, Class<T> instanceClass, String pluginName) {
		List<T> instances = new ArrayList<>();
		for (String className : pluginClassNames) {
			try {
				Class<?> asmClass = Class.forName(className);
				Class<? extends T> asmInstanceClass = asmClass.asSubclass(instanceClass);
				Constructor<? extends T> constructor = asmInstanceClass.getConstructor();
				T instance = constructor.newInstance();
				instances.add(instance);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				LOGGER.error("Failed to load {}: {}", pluginName, className, e);
			}
		}
		return instances;
	}
}
