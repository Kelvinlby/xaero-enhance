package mod.kelvinlby.xaeroenhance;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import mod.kelvinlby.xaeroenhance.mixin.LeveledRegionInvoker;
import xaero.map.region.LayeredRegionManager;
import xaero.map.region.LeveledRegion;

/**
 * The world map screen looks up every visible region every frame through {@code LayeredRegionManager.get}, which
 * takes three locks and two boxed-key hash map lookups to find the level 3 root region before walking down to the
 * requested level. That lookup is a large part of the frame when zoomed out. This caches the root lookup on the
 * render thread; the walk below the root stays live, exactly as in Xaero.
 * <p>
 * The cache is dropped whenever Xaero changes which roots exist ({@link #invalidate()}, called right after the change
 * is made). A lookup reads the version before asking Xaero and only caches the answer if no change happened
 * meanwhile, so it can never hold on to an answer that is older than what Xaero itself would return.
 */
public final class RegionIndex {
	private static final Object MISSING = new Object();
	private static volatile int version;

	// Render thread only.
	private static LayeredRegionManager cachedManager;
	private static int cachedLayer;
	private static int cachedVersion = -1;
	private static final Long2ObjectOpenHashMap<Object> ROOTS = new Long2ObjectOpenHashMap<>();

	private RegionIndex() {
	}

	/** Any thread, right after Xaero added or removed a root region or cleared a dimension's layers. */
	public static void invalidate() {
		version++;
	}

	/** Render thread, when the map screen closes: lets go of the cached regions. */
	static void reset() {
		ROOTS.clear();
		ROOTS.trim();
		cachedManager = null;
		cachedVersion = -1;
	}

	/** Render thread: same result as {@code manager.get(caveLayer, x, z, level)} for level 0 to 3. */
	public static LeveledRegion<?> get(LayeredRegionManager manager, int caveLayer, int x, int z, int level) {
		int current = version;
		if (manager != cachedManager || caveLayer != cachedLayer || current != cachedVersion) {
			ROOTS.clear();
			cachedManager = manager;
			cachedLayer = caveLayer;
			cachedVersion = current;
		}
		int shift = 3 - level;
		int rootX = x >> shift;
		int rootZ = z >> shift;
		long key = (long) rootX << 32 | rootZ & 0xFFFFFFFFL;
		Object cached = ROOTS.get(key);
		LeveledRegion<?> root;
		if (cached == null) {
			root = manager.getLayer(caveLayer).getMapRegions().get(rootX, rootZ, 3);
			if (version == current) {
				ROOTS.put(key, root == null ? MISSING : root);
			}
		} else {
			root = cached == MISSING ? null : (LeveledRegion<?>) cached;
		}
		if (root == null || level == 3) {
			return root;
		}
		return ((LeveledRegionInvoker) root).xaeroEnhance$get(x, z, level);
	}
}
