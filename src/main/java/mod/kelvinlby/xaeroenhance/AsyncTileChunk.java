package mod.kelvinlby.xaeroenhance;

/**
 * Per-chunk async state, mixed into {@code MapTileChunk}.
 */
public interface AsyncTileChunk {
	/** Bumped whenever the chunk's source data may have changed; a job built for an older value is stale. */
	int xaeroEnhance$generation();

	AsyncBuffers.Job xaeroEnhance$job();

	void xaeroEnhance$setJob(AsyncBuffers.Job job);

	/** Set when a job failed, so the next build runs through Xaero's own synchronous path once. */
	boolean xaeroEnhance$syncFallback();

	void xaeroEnhance$setSyncFallback(boolean syncFallback);
}
