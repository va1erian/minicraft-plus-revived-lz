package com.studiohartman.jamepad;

/** LAZYVM: a controller slot that is always empty (see {@link ControllerManager}). */
public class ControllerIndex {
	ControllerIndex() {
	}

	public String getName() throws ControllerUnpluggedException {
		throw new ControllerUnpluggedException();
	}

	public boolean isButtonPressed(ControllerButton button) throws ControllerUnpluggedException {
		throw new ControllerUnpluggedException();
	}

	public boolean isButtonJustPressed(ControllerButton button) throws ControllerUnpluggedException {
		throw new ControllerUnpluggedException();
	}

	public float getAxisState(ControllerAxis axis) throws ControllerUnpluggedException {
		throw new ControllerUnpluggedException();
	}

	public boolean doVibration(float left, float right, int durationMs) throws ControllerUnpluggedException {
		throw new ControllerUnpluggedException();
	}
}
