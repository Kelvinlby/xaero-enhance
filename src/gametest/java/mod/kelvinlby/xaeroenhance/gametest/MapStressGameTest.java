package mod.kelvinlby.xaeroenhance.gametest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xaero.map.controls.ControlsRegister;
import xaero.map.gui.GuiMap;
import xaeroplus.feature.highlights.ChunkHighlightCache;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.PaletteNewChunks;

/**
 * Explores some terrain, adds many XaeroPlus highlights, then opens, zooms, pans and closes the world map over and over
 * while logging heap, RSS, threads, VRAM and this mod's outstanding work, to catch crashes and leaks.
 */
public final class MapStressGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("XE-GAMETEST");
	private static final int CYCLES = 25;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(8);
			mc.options.pauseOnLostFocus = false;
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
			sp.getServer().runCommand("gamemode creative @a");
			settle(ctx, sp);

			// Explore a 3x3 grid so Xaero has a sizeable area mapped.
			for (int gx = 0; gx < 3; gx++) {
				for (int gz = 0; gz < 3; gz++) {
					int x = (gx - 1) * 256;
					int z = (gz - 1) * 256;
					sp.getServer().runCommand("tp @a " + x + " 150 " + z);
					settle(ctx, sp);
				}
			}
			sp.getServer().runCommand("tp @a 0 150 0");
			settle(ctx, sp);
			ctx.waitTicks(40);

			// Many synthetic highlights over a wide area exercise the highlight mesh, window flush and window load mixins.
			int added = ctx.computeOnClient(mc -> {
				PaletteNewChunks module = ModuleManager.getModule(PaletteNewChunks.class);
				module.enable();
				ChunkHighlightCache cache = module.newChunksCache.get();
				Random random = new Random(1);
				int count = 0;
				for (int x = -300; x < 300; x++) {
					for (int z = -300; z < 300; z++) {
						if (random.nextInt(2) == 0) {
							cache.addHighlight(x, z);
							count++;
						}
					}
				}
				return count;
			});
			LOGGER.info("Added {} synthetic highlights", added);
			ctx.waitTicks(40);

			Stats base = Stats.sample(ctx, "baseline");
			Stats first = null;
			Stats last = null;
			for (int cycle = 1; cycle <= CYCLES; cycle++) {
				ctx.getInput().pressKey(ControlsRegister.keyOpenMap);
				ctx.waitForScreen(GuiMap.class);
				ctx.waitTicks(20);
				ctx.getInput().setCursorPos(400, 300);
				for (int i = 0; i < 8; i++) {
					ctx.getInput().scroll(-1);
					ctx.waitTicks(5);
				}
				if (cycle == 1 || cycle == CYCLES) {
					ctx.waitTicks(60);
					ctx.takeScreenshot("xe-zoomed-out-" + cycle);
				}
				// Drag the map far in a direction that rotates each cycle.
				double angle = cycle * Math.PI / 4;
				ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
				for (int i = 0; i < 20; i++) {
					ctx.getInput().moveCursor(Math.cos(angle) * 30, Math.sin(angle) * 30);
					ctx.waitTick();
				}
				ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
				ctx.waitTicks(10);
				for (int i = 0; i < 8; i++) {
					ctx.getInput().scroll(1);
					ctx.waitTicks(3);
				}
				ctx.waitTicks(20);
				if (cycle == 1 || cycle == CYCLES) {
					ctx.takeScreenshot("xe-zoomed-in-" + cycle);
				}
				ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
				ctx.waitFor(mc -> mc.gui.screen() == null);
				ctx.waitTicks(10);
				Stats stats = Stats.sample(ctx, "cycle " + cycle);
				if (cycle == 1) {
					first = stats;
				}
				last = stats;
			}
			LOGGER.info("SUMMARY baseline: {}", base);
			LOGGER.info("SUMMARY after cycle 1: {}", first);
			LOGGER.info("SUMMARY after cycle {}: {}", CYCLES, last);
			if (last.liveJobs != 0 || last.frameWork != 0) {
				throw new AssertionError("Background work left after closing the map: " + last);
			}
		}
	}

	/** Gives nearby chunks time to generate and get mapped; waiting for all of them is slow and not what this test checks. */
	private static void settle(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		ctx.waitTicks(300);
	}

	private record Stats(long heapMb, long rssMb, int threads, long vramMb, int liveJobs, int frameWork) {
		static Stats sample(ClientGameTestContext ctx, String label) {
			Stats stats = ctx.computeOnClient(mc -> {
				System.gc();
				System.gc();
				Runtime rt = Runtime.getRuntime();
				return new Stats((rt.totalMemory() - rt.freeMemory()) >> 20, readRss(), ManagementFactory.getThreadMXBean().getThreadCount(),
						readVram(), size("mod.kelvinlby.xaeroenhance.AsyncBuffers", "LIVE"), size("mod.kelvinlby.xaeroenhance.FrameWork", "QUEUE"));
			});
			LOGGER.info("STATS {}: {}", label, stats);
			return stats;
		}

		private static long readRss() {
			try {
				for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
					if (line.startsWith("VmRSS:")) {
						return Long.parseLong(line.replaceAll("\\D", "")) >> 10;
					}
				}
			} catch (Exception ignored) {
			}
			return -1;
		}

		private static long readVram() {
			try {
				Process process = new ProcessBuilder("nvidia-smi", "--query-compute-apps=pid,used_memory", "--format=csv,noheader,nounits").start();
				String pid = Long.toString(ProcessHandle.current().pid());
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");
						if (parts[0].trim().equals(pid)) {
							return Long.parseLong(parts[1].trim());
						}
					}
				}
			} catch (Exception ignored) {
			}
			return -1;
		}

		private static int size(String className, String fieldName) {
			try {
				Field field = Class.forName(className).getDeclaredField(fieldName);
				field.setAccessible(true);
				Object value = field.get(null);
				return value instanceof Collection<?> c ? c.size() : ((ArrayDeque<?>) value).size();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
