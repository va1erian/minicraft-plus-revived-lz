package minicraft.core.io;

// LAZYVM: lazy.gfx has no clipboard yet, so copying does nothing and pasting yields "".
public final class ClipboardHandler {

	/**
	 * Give the system clipboard data.
	 */
	public void setClipboardContents(String string) {
	}

	/**
	 * Get the string from the system clipboard data.
	 * @return A string with the contents.
	 */
	public String getClipboardContents() {
		return "";
	}
}
