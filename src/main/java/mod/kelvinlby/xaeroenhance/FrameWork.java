package mod.kelvinlby.xaeroenhance;

import java.util.ArrayDeque;

/**
 * Render-thread work that must stay on the render thread but is too large for one frame. Steps run in submission
 * order within a small time budget per frame while the map screen is open. Once it closes, they run to completion.
 * The budget is kept small on purpose: the map can take a little longer to fill in, but frames stay smooth.
 */
public final class FrameWork {
	private static final long BUDGET_NANOS = 500_000L;
	private static final ArrayDeque<Step> QUEUE = new ArrayDeque<>();

	private FrameWork() {
	}

	@FunctionalInterface
	public interface Step {
		/** Does work until {@code deadline} ({@link System#nanoTime()}); returns true once finished. */
		boolean run(long deadline);
	}

	/** Render thread only. */
	public static void submit(Step step) {
		QUEUE.add(step);
		if (!MapScreen.isOpen()) {
			drain();
		}
	}

	/** Render thread: finishes every queued step now. */
	public static void drain() {
		while (!QUEUE.isEmpty()) {
			run(QUEUE.peek(), Long.MAX_VALUE);
			QUEUE.poll();
		}
	}

	/** Render thread, once per frame. */
	public static void runFrame() {
		long deadline = System.nanoTime() + BUDGET_NANOS;
		while (!QUEUE.isEmpty() && System.nanoTime() < deadline) {
			if (run(QUEUE.peek(), deadline)) {
				QUEUE.poll();
			}
		}
	}

	private static boolean run(Step step, long deadline) {
		try {
			return step.run(deadline);
		} catch (Throwable t) {
			XaeroEnhance.LOGGER.error("Deferred render thread work failed", t);
			return true;
		}
	}

	/** True every 32 items once the deadline has passed. */
	public static boolean outOfTime(int item, long deadline) {
		return (item & 31) == 0 && System.nanoTime() >= deadline;
	}
}
