package minicraft.screen.entry;

import minicraft.core.Game;
import minicraft.core.io.Localization;
import minicraft.screen.Menu;
import minicraft.screen.PopupDisplay;
import minicraft.screen.RelPos;
import minicraft.screen.TempDisplay;

public class LinkEntry extends SelectEntry {

	private final int color;

	// note that if the failMsg should be localized, such must be done before passing them as parameters, for this class will not do it since, by default, the failMsg contains a url.

	public LinkEntry(int color, String urlText) {
		this(color, urlText, urlText, false);
	}

	public LinkEntry(int color, String text, String url) {
		this(color, text, url, true);
	}

	public LinkEntry(int color, String text, String url, String failMsg) {
		this(color, text, url, failMsg, true);
	}

	public LinkEntry(int color, String text, String url, boolean localize) {
		this(color, text, url, Localization.getLocalized("Go to") + ": " + url, localize);
	}

	public LinkEntry(int color, String text, String url, String failMsg, boolean localize) {
		// LAZYVM: there is no java.awt.Desktop to open a browser with, so the entry shows the URL,
		// which upstream does when the desktop cannot browse.
		super(text, () -> Game.setDisplay(new PopupDisplay(null, failMsg)), localize);

		this.color = color;
	}

	@Override
	public int getColor(boolean isSelected) {
		return color;
	}
}
