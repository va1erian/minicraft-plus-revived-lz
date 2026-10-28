package minicraft.screen;

import minicraft.core.Game;
import minicraft.core.Renderer;
import minicraft.core.World;
import minicraft.core.io.InputHandler;
import minicraft.core.io.Localization;
import minicraft.gfx.Color;
import minicraft.gfx.Ellipsis;
import minicraft.gfx.Ellipsis.DotUpdater.TimeUpdater;
import minicraft.gfx.Ellipsis.SmoothEllipsis;
import minicraft.gfx.Font;
import minicraft.gfx.FontStyle;
import minicraft.gfx.Screen;
import minicraft.saveload.Save;

public class LoadingDisplay extends Display {

	private static float percentage = 0;
	private static String progressType = "";
	private static boolean localizeProgressType = true;

	// LAZYVM-GAP: upstream started a Swing Timer here whose action ran World.initWorld() on a new
	// "World Initialization Thread". LazyVM has neither javax.swing nor Thread.start(), so tick()
	// builds the world on the game thread once the screen has shown for the same 500 ms. The
	// screen then stands still until the world is ready.
	private long initTime;
	private final Ellipsis ellipsis = new SmoothEllipsis(new TimeUpdater());

	private String msg = "";

	public LoadingDisplay() {
		super(true, false);
	}

	@Override
	public void tick(InputHandler input) {
		super.tick(input);
		if (initTime == 0 || System.currentTimeMillis() - initTime < 500) return;
		initTime = 0; // LAZYVM: runs once per init(), as the non-repeating Timer did.
		try {
			World.initWorld();
			Game.setDisplay(null);
		} catch (RuntimeException ex) {
			Throwable t = ex.getCause();
			if (t instanceof InterruptedException) {
				World.onWorldExits();
				Game.exitDisplay(); // Exits the loading display and returns to world select display.
				Game.setDisplay(new PopupDisplay(null, "minicraft.displays.loading.regeneration_cancellation_popup.display"));
			} else
				throw ex;
		}
	}

	@Override
	public void init(Display parent) {
		if (parent != null && parent.getParent() == this) return; // Undefined behaviour
		super.init(parent);
		percentage = 0;
		progressType = "minicraft.displays.loading.message.world";
		localizeProgressType = true;
		if (WorldSelectDisplay.hasLoadedWorld())
			msg = "minicraft.displays.loading.message.loading";
		else
			msg = "minicraft.displays.loading.message.generating";
		initTime = System.currentTimeMillis(); // LAZYVM: was t.start().
	}

	@Override
	public void onExit() {
		percentage = 0;
		if (!WorldSelectDisplay.hasLoadedWorld()) {
			msg = "minicraft.displays.loading.message.saving";
			progressType = "minicraft.displays.loading.message.world";
			localizeProgressType = true;
			new Save(WorldSelectDisplay.getWorldName());
			Game.notifications.clear();
		}
	}

	public static void setPercentage(float percent) {
		percentage = percent;
	}

	public static float getPercentage() {
		return percentage;
	}

	public static void setMessage(String progressType) {
		setMessage(progressType, true);
	}

	public static void setMessage(String progressType, boolean localize) {
		LoadingDisplay.progressType = progressType;
		localizeProgressType = localize;
		redraw();
	}

	public static void progress(float amt) {
		percentage = Math.min(100, percentage + amt);
		redraw();
	}

	/**
	 * LAZYVM-GAP: the world is built on the game thread (see tick), so the game loop cannot draw
	 * meanwhile. Drawing a frame here when the progress changes shows it, and presenting lets
	 * the window handle its own events between steps.
	 */
	private static void redraw() {
		if (Game.getDisplay() instanceof LoadingDisplay) Renderer.render();
	}

	@Override
	public void render(Screen screen) {
		super.render(screen);
		int percent = Math.round(percentage);
		Font.drawParagraph(screen, new FontStyle(Color.RED), 6,
			Localization.getLocalized(msg) + (progressType.length() > 0 ? " " + (localizeProgressType ? Localization.getLocalized(progressType) : progressType) : "")
				+ ellipsis.updateAndGet(),
			percent + "%"
		);
	}
}
