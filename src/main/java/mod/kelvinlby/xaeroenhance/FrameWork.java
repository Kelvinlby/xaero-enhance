package mod.kelvinlby.xaeroenhance;

import java.util.ArrayDeque;

/**
 * Render-thread work that must stay on the render thread but is too large for one frame. Steps run in submission
 * order within a small time budget per frame.
 */
public final class FrameWork {
	private static final long BUDGET_NANOS = 2_000_000L;
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
	}

	/** Render thread, once per frame. */
	public static void runFrame() {
		long deadline = System.nanoTime() + BUDGET_NANOS;
		while (!QUEUE.isEmpty() && System.nanoTime() < deadline) {
			Step step = QUEUE.peek();
			boolean done;
			try {
				done = step.run(deadline);
			} catch (Throwable t) {
				XaeroEnhance.LOGGER.error("Deferred render thread work failed", t);
				done = true;
			}
			if (done) {
				QUEUE.poll();
			}
		}
	}

	/** True every 256 items once the deadline has passed. */
	public static boolean outOfTime(int item, long deadline) {
		return (item & 255) == 0 && System.nanoTime() >= deadline;
	}
}
