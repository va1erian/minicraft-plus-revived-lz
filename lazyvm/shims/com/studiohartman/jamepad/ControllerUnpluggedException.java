package com.studiohartman.jamepad;

/** LAZYVM: thrown by every {@link ControllerIndex} query, since no controller is ever present. */
public class ControllerUnpluggedException extends Exception {
	public ControllerUnpluggedException() {
		super("no controller on LazyVM");
	}
}
