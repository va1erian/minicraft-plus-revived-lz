package minicraft.gfx;

import lazy.gfx.Surface;
import minicraft.core.Renderer;
import minicraft.core.Updater;
import minicraft.gfx.SpriteLinker.LinkedSprite;
import minicraft.gfx.SpriteLinker.SpriteType;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Screen {

	public static final int w = Renderer.WIDTH; // Width of the screen
	public static final int h = Renderer.HEIGHT; // Height of the screen
	public static final Point center = new Point(w / 2, h / 2);

	private static final int MAXDARK = 128;

	/// x and y offset of screen:
	private int xOffset;
	private int yOffset;

	// Used for mirroring an image:
	private static final int BIT_MIRROR_X = 0x01; // Written in hexadecimal; binary: 01
	private static final int BIT_MIRROR_Y = 0x02; // Binary: 10

	private final int[] pixels;
	private final Surface surface; // LAZYVM: draws the renderings below natively (lazy.gfx).
	private final int[] overlayScale = new int[256], overlayLift = new int[256]; // LAZYVM: see OverlayRendering.

	private final ArrayDeque<Rendering> renderings = new ArrayDeque<>();
	private final LightOverlay lightOverlay;
	private ClearRendering lastClearRendering = null;

	// Outdated Information:
	// Since each sheet is 256x256 pixels, each one has 1024 8x8 "tiles"
	// So 0 is the start of the item sheet 1024 the start of the tile sheet, 2048 the start of the entity sheet,
	// And 3072 the start of the gui sheet

	// LAZYVM: the screen draws into a plain w * h pixel array (0xRRGGBB) instead of a
	// BufferedImage, and every rendering below writes those pixels itself instead of using
	// java.awt.Graphics2D, which LazyVM does not have.
	public Screen(int[] pixels) {
		/// Screen width and height are determined by the actual game window size, meaning the screen is only as big as the window.buffer = new BufferedImage(Screen.w, Screen.h);
		this.pixels = pixels;
		surface = new Surface(w, h, pixels);
		lightOverlay = new LightOverlay();
	}

	private interface Rendering {
		/** Invoked by {@link Renderer#render()}. */
		void render();

		/**
		 * LAZYVM: the same, drawn by the Java pixel loops that lazy.gfx.Surface replaced. Only
		 * the debug check ({@link #drawChecked}) calls it.
		 */
		default void renderLoop() {
			render();
		}
	}

	/** LAZYVM: fills the part of a rectangle that lies on the screen, like Graphics.fillRect. */
	private void fill(int xp, int yp, int rw, int rh, int color) {
		int x0 = Math.max(xp, 0), x1 = Math.min(xp + rw, w);
		int y0 = Math.max(yp, 0), y1 = Math.min(yp + rh, h);
		for (int y = y0; y < y1; y++)
			for (int x = x0; x < x1; x++)
				pixels[x + y * w] = color & 0xFFFFFF;
	}

	/** LAZYVM: sets one pixel if it is on the screen. */
	private void plot(int x, int y, int color) {
		if (x >= 0 && y >= 0 && x < w && y < h) pixels[x + y * w] = color & 0xFFFFFF;
	}

	private void queue(Rendering rendering) {
		renderings.add(rendering);
	}

	private abstract class ClearRendering implements Rendering {}

	private class SolidClearRendering extends ClearRendering {
		private final int color;

		public SolidClearRendering(int color) {
			this.color = color;
		}

		@Override
		public void render() {
			surface.clear(color & 0xFFFFFF);
		}

		@Override
		public void renderLoop() {
			fill(0, 0, Screen.w, Screen.h, color);
		}
	}

	private class PlainClearRendering extends ClearRendering {
		@Override
		public void render() {
			surface.clear(0); // An image's Graphics clears to black.
		}

		@Override
		public void renderLoop() {
			fill(0, 0, Screen.w, Screen.h, 0);
		}
	}

	private class SpriteRendering implements Rendering {
		private final int xp, yp, xt, yt, tw, th, mirrors, whiteTint, color;
		private final boolean fullBright;
		private final MinicraftImage sheet;

		public SpriteRendering(int xp, int yp, int xt, int yt, int tw, int th,
		                       int mirrors, int whiteTint, boolean fullBright, int color, MinicraftImage sheet) {
			this.xp = xp;
			this.yp = yp;
			this.xt = xt;
			this.yt = yt;
			this.tw = tw;
			this.th = th;
			this.mirrors = mirrors;
			this.whiteTint = whiteTint;
			this.fullBright = fullBright;
			this.color = color;
			this.sheet = sheet;
		}

		/**
		 * LAZYVM: one native blit. The sheet keeps Minicraft's 25-bit pixels (alpha 1 is
		 * opaque), so ALPHA_TEST copies them as upstream did, and KEY finds its white.
		 */
		@Override
		public void render() {
			int flags = Surface.ALPHA_TEST;
			if ((mirrors & BIT_MIRROR_X) > 0) flags |= Surface.FLIP_X;
			if ((mirrors & BIT_MIRROR_Y) > 0) flags |= Surface.FLIP_Y;
			if (whiteTint != -1) flags |= Surface.KEY;
			if (fullBright || color != 0) flags |= Surface.SOLID;
			surface.blit(sheet.surface, xt, yt, tw, th, xp, yp, flags,
				0x1FFFFFF, Color.upgrade(whiteTint), fullBright ? Color.WHITE : color);
		}

		@Override
		public void renderLoop() {
			int toffs = xt + yt * sheet.width;
			// Determines if the image should be mirrored...
			boolean mirrorX = (mirrors & BIT_MIRROR_X) > 0; // Horizontally.
			boolean mirrorY = (mirrors & BIT_MIRROR_Y) > 0; // Vertically.
			for (int y = 0; y < th; ++y) { // Relative
				if (y + yp < 0) continue; // If the pixel is out of bounds, then skip the rest of the loop.
				if (y + yp >= h) break;
				int sy = mirrorY ? th - 1 - y : y; // Source relative; reverse if necessary
				for (int x = 0; x < tw; ++x) { // Relative
					if (x + xp < 0) continue; // Skip rest if out of bounds.
					if (x + xp >= w) break;
					int sx = mirrorX ? tw - 1 - x : x; // Source relative; reverse if necessary
					int col = sheet.pixels[toffs + sx + sy * sheet.width]; // Gets the color of the current pixel from the value stored in the sheet.
					if (col >> 24 != 0) { // if not transparent
						int index = (xp + x) + (yp + y) * w;
						if (whiteTint != -1 && col == 0x1FFFFFF) {
							// If this is white, write the whiteTint over it
							pixels[index] = Color.upgrade(whiteTint);
						} else {
							// Inserts the colors into the image
							if (fullBright) {
								pixels[index] = Color.WHITE;
							} else {
								if (color != 0) {
									pixels[index] = color;
								} else {
									pixels[index] = Color.upgrade(col);
								}
							}
						}
					}
				}
			}
		}
	}

	private class FillRectRendering implements Rendering {
		private final int xp, yp, w, h, color;

		public FillRectRendering(int xp, int yp, int w, int h, int color) {
			this.xp = xp;
			this.yp = yp;
			this.w = w;
			this.h = h;
			this.color = color;
		}

		@Override
		public void render() {
			surface.fill(xp, yp, w, h, 0xFF000000 | color);
		}

		@Override
		public void renderLoop() {
			fill(xp, yp, w, h, color);
		}
	}

	private class DrawRectRendering implements Rendering {
		private final int xp, yp, w, h, color;

		public DrawRectRendering(int xp, int yp, int w, int h, int color) {
			this.xp = xp;
			this.yp = yp;
			this.w = w;
			this.h = h;
			this.color = color;
		}

		@Override
		public void render() {
			// Like Graphics.drawRect, the outline covers w + 1 by h + 1 pixels.
			surface.rect(xp, yp, w + 1, h + 1, 0xFF000000 | color);
		}

		@Override
		public void renderLoop() {
			fill(xp, yp, w + 1, 1, color);
			fill(xp, yp + h, w + 1, 1, color);
			fill(xp, yp, 1, h + 1, color);
			fill(xp + w, yp, 1, h + 1, color);
		}
	}

	private class DrawLineRendering implements Rendering {
		private final int x0, y0, x1, y1, color;

		public DrawLineRendering(int x0, int y0, int x1, int y1, int color) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.color = color;
		}

		@Override
		public void render() {
			surface.line(x0, y0, x1, y1, 0xFF000000 | color); // Bresenham's pixels, like Graphics.drawLine.
		}

		@Override
		public void renderLoop() {
			int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
			int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
			int err = dx + dy, x = x0, y = y0;
			while (true) {
				plot(x, y, color);
				if (x == x1 && y == y1) break;
				int e2 = 2 * err;
				if (e2 >= dy) { err += dy; x += sx; }
				if (e2 <= dx) { err += dx; y += sy; }
			}
		}
	}

	/** Placeholder way, for Sign cursor rendering */
	private class DrawLineSpecialRendering implements Rendering {
		private final int x0, y0, l;
		private final @MagicConstant(intValues = {0, 1}) int axis; // 0: x-axis; 1: Y-axis

		public DrawLineSpecialRendering(int x0, int y0, int l, int axis) {
			this.x0 = x0;
			this.y0 = y0;
			this.l = l;
			this.axis = axis;
		}

		@Override
		public void render() {
			switch (axis) {
				case 0:
					for (int i = 0; i < l; i++) { // 1 pixel high and 8 pixel wide
						int idx = x0 + i + y0 * Screen.w;
						pixels[idx] = Color.getLightnessFromRGB(pixels[idx]) >= .5 ? Color.BLACK : Color.WHITE;
					}
					break;
				case 1:
					for (int i = 0; i < l; i++) { // 8 pixel high and 1 pixel wide
						int idx = x0 + (y0 + i) * Screen.w;
						pixels[idx] = Color.getLightnessFromRGB(pixels[idx]) >= .5 ? Color.BLACK : Color.WHITE;
					}
					break;
			}
		}
	}

	private class OverlayRendering implements Rendering {
		private final int currentLevel, xa, ya;
		private final double darkFactor;
		private boolean prepared;
		private double alpha;
		private int[] overlay; // The black's opacity per pixel, or null while it is invisible.

		private OverlayRendering(int currentLevel, int xa, int ya, double darkFactor) {
			this.currentLevel = currentLevel;
			this.xa = xa;
			this.ya = ya;
			this.darkFactor = darkFactor;
		}

		/** LAZYVM: works the overlay out once; building it uses up the frame's lights. */
		private void prepare() {
			if (prepared) return;
			prepared = true;
			alpha = lightOverlay.getOverlayOpacity(currentLevel, darkFactor);
			if (alpha == 0) lightOverlay.lights.clear(); // Daytime: the black is invisible.
			else overlay = lightOverlay.render(xa, ya);
		}

		/**
		 * LAZYVM: upstream's two AlphaComposite SRC_OVER passes, drawn natively: white at 2%
		 * opacity (lightening; 5/255 here), then the overlay's black at its own opacity times
		 * alpha (shaders).
		 */
		@Override
		public void render() {
			prepare();
			surface.fill(0, 0, w, h, 0x05FFFFFF);
			if (overlay != null) surface.blendMask(overlay, 0x000000, (int) Math.round(alpha * 255));
		}

		/** The same in one pass: for an overlay opacity o, c' = (c * scale[o] + lift[o]) >> 16. */
		@Override
		public void renderLoop() {
			prepare();
			for (int o = 0; o < 256; o++) {
				double shade = 1 - o / 255.0 * alpha;
				overlayScale[o] = (int) Math.round(65536 * (1 - .02) * shade);
				overlayLift[o] = (int) Math.round(65536 * 255 * .02 * shade) + 32768; // + 1/2: rounds.
			}
			for (int i = 0; i < pixels.length; i++) {
				int o = overlay == null ? 0 : overlay[i];
				int s = overlayScale[o], l = overlayLift[o], c = pixels[i];
				pixels[i] = ((c >> 16 & 0xFF) * s + l) >> 16 << 16 | ((c >> 8 & 0xFF) * s + l) >> 16 << 8 | ((c & 0xFF) * s + l) >> 16;
			}
		}
	}

	/**
	 * Clears all the colors on the screen
	 */
	public void clear(int color) {
		// Turns each pixel into a single color (clearing the screen!)
		if (color == 0) {
			queueClearRendering(new PlainClearRendering());
		} else {
			queueClearRendering(new SolidClearRendering(color));
		}
	}

	private void queueClearRendering(ClearRendering clearRendering) {
		lastClearRendering = clearRendering;
		queue(clearRendering);
	}

	public void flush() {
		Rendering rendering;
		do { // Skips until the latest clear rendering is obtained.
			rendering = renderings.poll(); // This can prevent redundant renderings operated.
			if (rendering == null) return;
		} while (rendering != lastClearRendering);
		if (DRAW_CHECK) {
			drawChecked(rendering);
			return;
		}
		do { // Renders all renderings until all are operated.
			if (LOOPS) rendering.renderLoop();
			else rendering.render();
		} while ((rendering = renderings.poll()) != null);
	}

	// LAZYVM: -Dminicraft.lazyvm.drawcheck=true draws every frame twice, by the old pixel loops
	// and by lazy.gfx, and prints how far apart they are every 10 frames (see drawChecked).
	// LAZYVM: -Dminicraft.lazyvm.loops=true draws by the old pixel loops, to time them.
	private static final boolean LOOPS = "true".equals(System.getProperty("minicraft.lazyvm.loops"));
	private static final boolean DRAW_CHECK = "true".equals(System.getProperty("minicraft.lazyvm.drawcheck"));
	private int[] checkFrame, checkBeforeOverlay;
	private long checkFrames, exactPixelsOff, blendFramesOff, blendPixelsOff;
	private int blendMaxDelta;

	/**
	 * LAZYVM: draws one frame by the loops, keeps it, draws it again by lazy.gfx, and compares
	 * the colours (the alpha byte is never shown). Up to the light overlay the two must match
	 * pixel for pixel. The overlay rounds differently (one pass of tables against two native
	 * blends), so whole frames with an overlay are compared by how far they differ.
	 */
	private void drawChecked(Rendering first) {
		List<Rendering> frame = new ArrayList<>();
		for (Rendering r = first; r != null; r = renderings.poll()) frame.add(r);
		int overlayAt = frame.size();
		for (int i = frame.size() - 1; i >= 0; i--) if (frame.get(i) instanceof OverlayRendering) overlayAt = i;
		if (checkFrame == null) {
			checkFrame = new int[pixels.length];
			checkBeforeOverlay = new int[pixels.length];
		}
		for (int i = 0; i < frame.size(); i++) {
			if (i == overlayAt) System.arraycopy(pixels, 0, checkBeforeOverlay, 0, pixels.length);
			frame.get(i).renderLoop();
		}
		System.arraycopy(pixels, 0, checkFrame, 0, pixels.length);
		if (overlayAt == frame.size()) System.arraycopy(pixels, 0, checkBeforeOverlay, 0, pixels.length);
		for (int i = 0; i < frame.size(); i++) {
			if (i == overlayAt) exactPixelsOff += differing(checkBeforeOverlay, false);
			frame.get(i).render();
		}
		if (overlayAt == frame.size()) exactPixelsOff += differing(checkBeforeOverlay, false);
		else {
			long off = differing(checkFrame, true);
			if (off > 0) blendFramesOff++;
			blendPixelsOff += off;
		}
		if (++checkFrames % 10 == 0)
			System.out.println("[LAZYVM] drawcheck: " + checkFrames + " frames; " + exactPixelsOff
				+ " pixels differ where they must match; " + blendFramesOff + " frames with an overlay differ in "
				+ blendPixelsOff + " pixels, by at most " + blendMaxDelta + " per channel");
	}

	/** Pixels whose colour differs from {@code ref}; {@code track} keeps the largest channel gap. */
	private long differing(int[] ref, boolean track) {
		long n = 0;
		for (int i = 0; i < pixels.length; i++) {
			int a = pixels[i], b = ref[i];
			if (((a ^ b) & 0xFFFFFF) == 0) continue;
			n++;
			for (int sh = 0; track && sh < 24; sh += 8)
				blendMaxDelta = Math.max(blendMaxDelta, Math.abs((a >> sh & 0xFF) - (b >> sh & 0xFF)));
		}
		return n;
	}

	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet) {
		render(xp, yp, xt, yt, bits, sheet, -1);
	}

	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint) {
		render(xp, yp, xt, yt, bits, sheet, whiteTint, false);
	}

	/**
	 * This method takes care of assigning the correct spritesheet to assign to the sheet variable
	 **/
	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint, boolean fullbright) {
		render(xp, yp, xt, yt, bits, sheet, whiteTint, fullbright, 0);
	}

	public void render(int xp, int yp, LinkedSprite sprite) {
		render(xp, yp, sprite.getSprite());
	}

	public void render(int xp, int yp, Sprite sprite) {
		render(xp, yp, sprite, false);
	}

	public void render(int xp, int yp, Sprite sprite, boolean fullbright) {
		render(xp, yp, sprite, 0, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite sprite, int mirror, boolean fullbright) {
		render(xp, yp, sprite, mirror, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite sprite, int mirror, boolean fullbright, int color) {
		boolean mirrorX = (mirror & BIT_MIRROR_X) > 0; // Horizontally.
		boolean mirrorY = (mirror & BIT_MIRROR_Y) > 0; // Vertically.
		for (int r = 0; r < sprite.spritePixels.length; r++) {
			int lr = mirrorY ? sprite.spritePixels.length - 1 - r : r;
			for (int c = 0; c < sprite.spritePixels[lr].length; c++) {
				Sprite.Px px = sprite.spritePixels[lr][mirrorX ? sprite.spritePixels[lr].length - 1 - c : c];
				render(xp + c * 8, yp + r * 8, px, mirror, sprite.color, fullbright, color);
			}
		}
	}

	public void render(int xp, int yp, Sprite.Px pixel) {
		render(xp, yp, pixel, -1);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int whiteTint) {
		render(xp, yp, pixel, 0, whiteTint);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint) {
		render(xp, yp, pixel, mirror, whiteTint, false);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint, boolean fullbright) {
		render(xp, yp, pixel, mirror, whiteTint, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint, boolean fullbright, int color) {
		render(xp, yp, pixel.x, pixel.y, pixel.mirror ^ mirror, pixel.sheet, whiteTint, fullbright, color);
	}

	/**
	 * Renders an object from the sprite sheet based on screen coordinates, tile (SpriteSheet location), colors, and bits (for mirroring). I believe that xp and yp refer to the desired position of the upper-left-most pixel.
	 */
	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint, boolean fullbright, int color) {
		if (sheet == null) return; // Verifying that sheet is not null.

		// xp and yp are originally in level coordinates, but offset turns them to screen coordinates.
		// xOffset and yOffset account for screen offset
		render(xp - xOffset, yp - yOffset, xt * 8, yt * 8, 8, 8, sheet, bits, whiteTint, fullbright, color);
	}

	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet) {
		render(xp, yp, xt, yt ,tw, th, sheet, 0);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors) {
		render(xp, yp, xt, yt ,tw, th, sheet, mirrors, -1);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint) {
		render(xp, yp, xt, yt, tw, th, sheet, mirrors, whiteTint, false);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint, boolean fullbright) {
		render(xp, yp, xt, yt, tw, th, sheet, mirrors, whiteTint, fullbright, 0);
	}
	// Any single pixel from the image can be rendered using this method.
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint, boolean fullBright, int color) {
		if (sheet == null) return; // Verifying that sheet is not null.

		// Validation check. LAZYVM: lazy.gfx refuses a source rectangle outside the sheet, where
		// upstream read on into the next rows; so either side out is enough, not both.
		if (xt < 0 || yt < 0 || xt + tw > sheet.width || yt + th > sheet.height) {
			if (DRAW_CHECK && !(xt + tw > sheet.width && yt + th > sheet.height))
				System.out.println("[LAZYVM] drawcheck: sprite " + xt + "," + yt + " " + tw + "x" + th + " outside its sheet");
			render(xp, yp, 0, 0, mirrors, Renderer.spriteLinker.missingSheet(SpriteType.Item));
			return;
		}

		queue(new SpriteRendering(xp, yp, xt, yt, tw, th, mirrors, whiteTint, fullBright, color, sheet));
	}

	public void fillRect(int xp, int yp, int w, int h, int color) {
		queue(new FillRectRendering(xp, yp, w, h, color));
	}

	public void drawRect(int xp, int yp, int w, int h, int color) {
		queue(new DrawRectRendering(xp, yp, w, h, color));
	}

	/**
	 * Draw a straight line along an axis.
	 * @param axis The axis to draw along: {@code 0} for x-axis; {@code 1} for y-axis
	 * @param l The length of the line
	 */
	public void drawAxisLine(int xp, int yp, @MagicConstant(intValues = {0, 1}) int axis, int l, int color) {
		switch (axis) {
			case 0: queue(new DrawLineRendering(xp, yp, xp + l, yp, color)); break;
			case 1: queue(new DrawLineRendering(xp, yp, xp, yp + l, color)); break;
		}
	}

	public void drawLine(int x0, int y0, int x1, int y1, int color) {
		queue(new DrawLineRendering(x0, y0, x1, y1, color));
	}

	/** Placeholder line drawing method specialized for sign cursor drawing */
	public void drawLineSpecial(int x0, int y0, @MagicConstant(intValues = {0, 1}) int axis, int l) {
		queue(new DrawLineSpecialRendering(x0, y0, l, axis));
	}

	/**
	 * Sets the offset of the screen
	 */
	public void setOffset(int xOffset, int yOffset) {
		// This is called in few places, one of which is level.renderBackground, right before all the tiles are rendered. The offset is determined by the Game class (this only place renderBackground is called), by using the screen's width and the player's position in the level.
		// In other words, the offset is a conversion factor from level coordinates to screen coordinates. It makes a certain coord in the level the upper left corner of the screen, when subtracted from the tile coord.

		this.xOffset = xOffset;
		this.yOffset = yOffset;
	}

	/* Used for the scattered dots at the edge of the light radius underground.

		These values represent the minimum light level, on a scale from 0 to 25 (255/10), 0 being no light, 25 being full light (which will be portrayed as transparent on the overlay lightScreen pixels) that a pixel must have in order to remain lit (not black).
		each row and column is repeated every 4 pixels in the proper direction, so the pixel lightness minimum varies. It's highly worth note that, as the rows progress and loop, there's two sets or rows (1,4 and 2,3) whose values in the same column add to 15. The exact same is true for columns (sets are also 1,4 and 2,3), execpt the sums of values in the same row and set differ for each row: 10, 18, 12, 20. Which... themselves... are another set... adding to 30... which makes sense, sort of, since each column totals 15+15=30.
		In the end, "every other every row", will need, for example in column 1, 15 light to be lit, then 0 light to be lit, then 12 light to be lit, then 3 light to be lit. So, the pixels of lower light levels will generally be lit every other pixel, while the brighter ones appear more often. The reason for the variance in values is to provide EVERY number between 0 and 15, so that all possible light levels (below 16) are represented fittingly with their own pattern of lit and not lit.
		16 is the minimum pixel lighness required to ensure that the pixel will always remain lit.
	*/

	/**
	 * Overlays the screen with pixels
	 */
	public void overlay(int currentLevel, int xa, int ya) {
		double darkFactor = 0;
		if (currentLevel >= 3 && currentLevel < 5) {
			int transTime = Updater.dayLength / 4;
			double relTime = (Updater.tickCount % transTime) * 1.0 / transTime;

			switch (Updater.getTime()) {
				case Morning:
					darkFactor = Updater.pastDay1 ? (1 - relTime) * MAXDARK : 0;
					break;
				case Day:
					darkFactor = 0;
					break;
				case Evening:
					darkFactor = relTime * MAXDARK;
					break;
				case Night:
					darkFactor = MAXDARK;
					break;
			}

			if (currentLevel > 3) darkFactor -= (darkFactor < 10 ? darkFactor : 10);
		} else if (currentLevel >= 5)
			darkFactor = MAXDARK;

		// The Integer array of pixels to overlay the screen with.
		queue(new OverlayRendering(currentLevel, xa, ya, darkFactor));
	}

	public void renderLight(int x, int y, int r) {
		// Applies offsets:
		lightOverlay.renderLight(x - xOffset, y - yOffset, r);
	}

	// LAZYVM: the light buffer and the overlay are plain arrays, and the light discs are
	// drawn by hand instead of with Graphics2D and RadialGradientPaint.
	private static class LightOverlay {
		private static final int[] dither = new int[] {
			0, 8, 2, 10,
			12, 4, 14, 6,
			3, 11, 1, 9,
			15, 7, 13, 5
		};

		public final int[] bufPixels = new int[w * h]; // Grade of lightness, 0 to 255.
		public final int[] olPixels = new int[w * h]; // Opacity of the black overlay, 0 to 255.
		public final HashMap<@NotNull Point, @NotNull Integer> lights = new HashMap<>();

		/**
		 * Gets the overlay light darkness opacity instantly.
		 * @param currentLevel the current level index of the target
		 * @param darkFactor the tint factor to darken, from {@code 1} to {@code 128}
		 * @return opacity of darkness from {@code 0} to {@code 1}
		 */
		public double getOverlayOpacity(int currentLevel, double darkFactor) {
			// The above if statement is simply comparing the light level stored in oPixels with the minimum light level stored in dither. if it is determined that the oPixels[i] is less than the minimum requirements, the pixel is considered "dark", and the below is executed...
			if (currentLevel < 3) { // if in caves...
				// in the caves, not being lit means being pitch black.
				return 1;
			} else {
				// Outside the caves, not being lit simply means being darker.
				return darkFactor / 160; // darkens the color one shade.
			}
		}

		public void renderLight(int x, int y, int r) {
			lights.put(new Point(x, y), r);
		}

		public int[] render(int xa, int ya) {
			Arrays.fill(bufPixels, 0);
			for (Map.Entry<Point, Integer> e : lights.entrySet()) {
				int x = e.getKey().x, y = e.getKey().y, r = e.getValue();
				boolean[] surrounds = new boolean[8];
				for (int xx = -16; xx < 17; ++xx) {
					for (int yy = -16; yy < 17; ++yy) {
						if (xx != 0 || yy != 0) {
							Point pp = new Point(x + xx, y + yy);
							if (lights.containsKey(pp) && r <= lights.get(pp)) {
								double theta = Math.atan2(yy, xx);
								if (theta < 0) theta += 2 * Math.PI; // Ensures it is positive.
								surrounds[(int) (theta * 4 / Math.PI)] = true;
							}
						}
					}
				}

				// Reduce lighting circles on screen
				boolean surrounded = true; // LAZYVM-GAP: was IntStream.allMatch; LazyVM has no java.util.stream.
				for (boolean s : surrounds) surrounded &= s;
				if (surrounded) {
					fillWhite(x - 8, y - 8, 16, 16);
				} else {
					fillLight(x, y, r);
				}
			}
			lights.clear();

			for (int x = 0; x < w; ++x) {
				for (int y = 0; y < h; ++y) {
					int i = x + y * w;
					// Grade of lightness
					int grade = bufPixels[i];
					// (a + b) & 3 acts like (a + b) % 4
					if (grade / 10 <= dither[((x + xa) & 3) + ((y + ya) & 3) * 4]) {
						olPixels[i] = 255 - grade;
					} else {
						olPixels[i] = 0;
					}
				}
			}
			return olPixels;
		}

		private void fillWhite(int x0, int y0, int rw, int rh) {
			for (int y = Math.max(y0, 0); y < Math.min(y0 + rh, h); y++)
				for (int x = Math.max(x0, 0); x < Math.min(x0 + rw, w); x++)
					bufPixels[x + y * w] = 255;
		}

		/**
		 * Paints a light disc of radius r: white whose opacity falls from full at the centre to
		 * none at the rim as 1 - t^4 (t = distance / r), blended over the buffer. Upstream built
		 * that falloff as a 51-stop RadialGradientPaint (with BigDecimal, also missing on
		 * LazyVM). Pixels are sampled at their centres, as Java2D does.
		 */
		private void fillLight(int cx, int cy, int r) {
			if (r <= 0) return;
			for (int y = Math.max(cy - r, 0); y < Math.min(cy + r, h); y++) {
				for (int x = Math.max(cx - r, 0); x < Math.min(cx + r, w); x++) {
					double dx = x + .5 - cx, dy = y + .5 - cy;
					double t2 = (dx * dx + dy * dy) / ((double) r * r);
					if (t2 > 1) continue;
					int i = x + y * w;
					bufPixels[i] += (int) ((255 - bufPixels[i]) * (1 - t2 * t2));
				}
			}
		}
	}
}
