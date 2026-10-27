package mod.kelvinlby.xaeroenhance;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import xaero.lib.client.level.block.BlockTextureColorUtils;
import xaero.lib.platform.ClientOnlyServices;
import xaero.map.MapProcessor;
import xaero.map.biome.BiomeColorCalculator;
import xaero.map.biome.BlockTintProvider;
import xaero.map.cache.BrokenBlockTintCache;
import xaero.map.region.MapRegion;
import xaero.map.region.MapTile;
import xaero.map.region.MapTileChunk;
import xaero.map.region.MapUpdateFastConfig;
import xaero.map.region.texture.LeafRegionTexture;
import xaero.map.world.MapDimension;

/**
 * Builds leaf texture pixel buffers ({@code MapTileChunk.updateBuffers}) on worker threads instead of the
 * render thread. The render thread only submits jobs and copies finished pixels into the texture buffer.
 */
public final class AsyncBuffers {
	private static final int THREADS = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
	private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS, new ThreadFactory());
	private static final ThreadLocal<WorkerContext> CONTEXT = new ThreadLocal<>();

	// xaerolib's colour caches are not thread-safe, so workers read these copies and fill misses on the render thread.
	private static final Map<BlockState, Integer> TEXTURE_COLORS = new ConcurrentHashMap<>();
	private static final Map<BlockState, Integer> TINT_INDICES = new ConcurrentHashMap<>();
	private static final BrokenBlockTintCache BROKEN_TINTS = new BrokenBlockTintCache(ConcurrentHashMap.newKeySet());

	private AsyncBuffers() {
	}

	public static boolean isWorker() {
		return CONTEXT.get() != null;
	}

	/**
	 * Render thread, from {@code LeafRegionTexture.preUpload}/{@code postUpload}.
	 *
	 * @param commit whether a finished job may be applied now (only within the upload budget)
	 * @return true if Xaero's synchronous buffer update must be skipped
	 */
	public static boolean handle(MapTileChunk chunk, MapProcessor mapProcessor, boolean commit) {
		AsyncTileChunk async = (AsyncTileChunk) chunk;
		Job job = async.xaeroEnhance$job();
		if (job == null) {
			if (async.xaeroEnhance$syncFallback()) {
				async.xaeroEnhance$setSyncFallback(!commit);
				return false;
			}
			// Regions being written around the player keep Xaero's immediate path; they change every few ticks.
			if (!chunk.getToUpdateBuffers() || mapProcessor.isWritingPaused() || chunk.getInRegion().isBeingWritten()
					|| mapProcessor.getWorld() == null) {
				return false;
			}
			job = new Job(chunk, mapProcessor, new MapUpdateFastConfig(mapProcessor), async.xaeroEnhance$generation());
			async.xaeroEnhance$setJob(job);
			POOL.execute(job);
			return true;
		}
		if (!job.done || !commit || mapProcessor.isWritingPaused()) {
			return true;
		}
		async.xaeroEnhance$setJob(null);
		if (job.failed) {
			async.xaeroEnhance$setSyncFallback(true);
		} else if (job.generation == async.xaeroEnhance$generation() && chunk.getToUpdateBuffers()) {
			commit(chunk, job);
		}
		// Otherwise the chunk changed meanwhile and is still flagged for an update, so it gets resubmitted.
		return true;
	}

	/** Mirrors the bookkeeping of {@code MapTileChunk.updateBuffers}, with the pixels already computed. */
	private static void commit(MapTileChunk chunk, Job job) {
		LeafRegionTexture texture = chunk.getLeafTexture();
		MapRegion region = chunk.getInRegion();
		texture.resetTimer();
		synchronized (region) {
			texture.setCachePrepared(false);
			texture.setShouldDownloadFromPBO(false);
			region.setAllCachePrepared(false);
		}
		texture.prepareBuffer();
		ByteBuffer buffer = texture.getDirectColorBuffer();
		int[] pixels = job.pixels;
		for (int tile = 0; tile < 16; tile++) {
			if ((job.tileMask & 1 << tile) == 0) {
				continue;
			}
			int offX = (tile >> 2) * 16;
			int offZ = (tile & 3) * 16;
			for (int z = 0; z < 16; z++) {
				int index = (offZ + z) * 64 + offX;
				for (int x = 0; x < 16; x++, index++) {
					buffer.putInt(index * 4, pixels[index]);
				}
			}
		}
		texture.postBufferUpdate(job.hasLight);
		chunk.setToUpdateBuffers(false);
		texture.setToUpload(true);
	}

	/** Worker thread: same pixel loop as {@code MapTileChunk.updateBuffers}, writing into a private array. */
	private static void build(Job job, WorkerContext context) {
		MapTileChunk chunk = job.chunk;
		MapProcessor mapProcessor = job.mapProcessor;
		MapUpdateFastConfig config = job.config;
		MapRegion region = chunk.getInRegion();
		World world = mapProcessor.getWorld();
		if (world == null) {
			throw new IllegalStateException("No world");
		}
		Registry<Block> blockRegistry = mapProcessor.getWorldBlockRegistry();
		Registry<Biome> biomeRegistry = mapProcessor.worldBiomeRegistry;
		Registry<DimensionType> dimensionTypes = mapProcessor.getWorldDimensionTypeRegistry();
		BlockTintProvider tintProvider = context.tintProvider(mapProcessor, biomeRegistry, config.biomeBlending);
		BlockTextureColorUtils textureColors = ClientOnlyServices.PLATFORM.getBlockUtils().getTextureColorUtils();
		LeafRegionTexture texture = chunk.getLeafTexture();
		MapTileChunk prev = chunk.getNeighbourTileChunk(0, -1, mapProcessor, false);
		MapTileChunk prevDiagonal = chunk.getNeighbourTileChunk(-1, -1, mapProcessor, false);
		MapTileChunk prevHorizontal = chunk.getNeighbourTileChunk(-1, 0, mapProcessor, false);
		// The region's own dimension, which is what XaeroPlus also substitutes when writing while dimension-switched.
		MapDimension dim = region.getDim();
		float shadowR = dim.getShadowR();
		float shadowG = dim.getShadowG();
		float shadowB = dim.getShadowB();
		int[] result = context.result;
		int[] pixels = job.pixels;
		AsyncTileChunk async = (AsyncTileChunk) chunk;

		for (int o = 0; o < 4; o++) {
			for (int p = 0; p < 4; p++) {
				if (async.xaeroEnhance$generation() != job.generation) {
					return; // stale and discarded on commit; avoid touching tiles that may already be recycled
				}
				MapTile tile = chunk.getTile(o, p);
				if (tile == null || !tile.isLoaded()) {
					continue;
				}
				int caveStart = tile.getWrittenCaveStart();
				int caveDepth = tile.getWrittenCaveDepth();
				for (int z = 0; z < 16; z++) {
					int pixelZ = p * 16 + z;
					for (int x = 0; x < 16; x++) {
						int pixelX = o * 16 + x;
						tile.getBlock(x, z).getPixelColour(
								result, textureColors, world, dim, blockRegistry, chunk, prev, prevDiagonal, prevHorizontal, tile, x, z,
								caveStart, caveDepth, context.pos, biomeRegistry, dimensionTypes, shadowR, shadowG, shadowB,
								tintProvider, mapProcessor, mapProcessor.getOverlayManager(),
								texture.getHeight(pixelX, pixelZ), texture.getTopHeight(pixelX, pixelZ),
								mapProcessor.getBlockStateShortShapeCache(), config);
						pixels[pixelZ * 64 + pixelX] = result[2] << 24 | result[1] << 16 | result[0] << 8 | result[3];
						if (result[3] != 0) {
							job.hasLight = true;
						}
					}
				}
				job.tileMask |= 1 << (o * 4 + p);
			}
		}
	}

	/** Worker replacement for {@code BlockTextureColorUtils.getBlockTextureColor}. */
	public static int textureColor(BlockTextureColorUtils utils, BlockState state, IntSupplier compute) {
		Integer color = TEXTURE_COLORS.get(state);
		if (color != null) {
			return color;
		}
		return onRenderThread(() -> {
			int computed = compute.getAsInt();
			TEXTURE_COLORS.put(state, computed);
			TINT_INDICES.put(state, utils.getBlockTintIndex(state));
			return computed;
		});
	}

	/** Worker replacement for {@code BlockTextureColorUtils.getBlockTintIndex}. */
	public static int tintIndex(BlockState state, IntSupplier compute) {
		Integer tint = TINT_INDICES.get(state);
		if (tint != null) {
			return tint;
		}
		return onRenderThread(() -> {
			int computed = compute.getAsInt();
			TINT_INDICES.putIfAbsent(state, computed);
			return computed;
		});
	}

	public static void clearColorCaches() {
		TEXTURE_COLORS.clear();
		TINT_INDICES.clear();
	}

	private static int onRenderThread(IntSupplier task) {
		try {
			return MinecraftClient.getInstance().submit(task::getAsInt).get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("Render thread colour lookup failed", e);
		}
	}

	public static final class Job implements Runnable {
		final MapTileChunk chunk;
		final MapProcessor mapProcessor;
		final MapUpdateFastConfig config;
		final int generation;
		int[] pixels;
		int tileMask;
		boolean hasLight;
		boolean failed;
		volatile boolean done;

		Job(MapTileChunk chunk, MapProcessor mapProcessor, MapUpdateFastConfig config, int generation) {
			this.chunk = chunk;
			this.mapProcessor = mapProcessor;
			this.config = config;
			this.generation = generation;
		}

		@Override
		public void run() {
			MapRegion region = chunk.getInRegion();
			// Keeps MapWriter off this region's tiles while they are being read.
			region.pushWriterPause();
			try {
				pixels = new int[64 * 64];
				build(this, CONTEXT.get());
			} catch (Throwable t) {
				failed = true;
				XaeroEnhance.LOGGER.debug("Async buffer build failed for chunk {}", chunk, t);
			} finally {
				region.popWriterPause();
				done = true;
			}
		}
	}

	private static final class WorkerContext {
		final int[] result = new int[4];
		final BlockPos.Mutable pos = new BlockPos.Mutable();
		final BiomeColorCalculator calculator = new BiomeColorCalculator();
		BlockTintProvider source;
		BlockTintProvider tintProvider;

		/** A private tint provider per worker, rebuilt whenever Xaero replaces its own (world change). */
		BlockTintProvider tintProvider(MapProcessor mapProcessor, Registry<Biome> biomeRegistry, boolean biomeBlending) {
			BlockTintProvider current = mapProcessor.getWorldBlockTintProvider();
			if (current != source || tintProvider == null) {
				source = current;
				tintProvider = new BlockTintProvider(biomeRegistry, calculator, mapProcessor, BROKEN_TINTS, mapProcessor.getMapWriter());
			}
			calculator.prepare(biomeBlending);
			return tintProvider;
		}
	}

	private static final class ThreadFactory implements java.util.concurrent.ThreadFactory {
		private final AtomicInteger count = new AtomicInteger();

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(() -> {
				CONTEXT.set(new WorkerContext());
				runnable.run();
			}, "XaeroEnhance Buffer Worker #" + count.incrementAndGet());
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 2);
			return thread;
		}
	}
}
