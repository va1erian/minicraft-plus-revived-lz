package com.studiohartman.jamepad;

import com.badlogic.gdx.utils.SharedLibraryLoadRuntimeException;

/**
 * LAZYVM: stand-in for Jamepad's SDL gamepad manager. LazyVM cannot load SDL, so
 * {@link #initSDLGamepad} fails the way a missing native library does, and the game runs with
 * the keyboard only.
 */
public class ControllerManager {
	public void initSDLGamepad() {
		throw new SharedLibraryLoadRuntimeException("no SDL gamepad library on LazyVM");
	}

	public void update() {
	}

	public int getNumControllers() {
		return 0;
	}

	public ControllerIndex getControllerIndex(int index) {
		return new ControllerIndex();
	}
}
