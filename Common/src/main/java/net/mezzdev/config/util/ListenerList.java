package net.mezzdev.config.util;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ListenerList<T> {
	private final List<Registration<T>> registrations = new CopyOnWriteArrayList<>();

	public Runnable add(T listener) {
		Objects.requireNonNull(listener, "listener");
		Registration<T> registration = new Registration<>(this, listener);
		registrations.add(registration);
		return registration::remove;
	}

	public List<T> snapshot() {
		return registrations.stream()
			.map(Registration::listener)
			.toList();
	}

	private void remove(Registration<T> registration) {
		registrations.remove(registration);
	}

	private static final class Registration<T> {
		private final ListenerList<T> owner;
		private final T listener;
		private final AtomicBoolean removed = new AtomicBoolean();

		private Registration(ListenerList<T> owner, T listener) {
			this.owner = owner;
			this.listener = listener;
		}

		private T listener() {
			return listener;
		}

		private void remove() {
			if (removed.compareAndSet(false, true)) {
				owner.remove(this);
			}
		}
	}
}
