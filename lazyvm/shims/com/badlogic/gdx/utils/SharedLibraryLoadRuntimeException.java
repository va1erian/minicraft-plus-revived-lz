package com.badlogic.gdx.utils;

/** LAZYVM: thrown when a native library cannot be loaded; the game then disables controllers. */
public class SharedLibraryLoadRuntimeException extends RuntimeException {
	public SharedLibraryLoadRuntimeException(String message) {
		super(message);
	}
}
