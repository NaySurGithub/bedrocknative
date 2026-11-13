package net.bedrocknative.client.content;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.content.AtlasPadding;
import net.bedrocknative.content.FlipbookAnimation;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.world.block.custom.BlockItemDisplay;
import net.bedrocknative.world.block.custom.BlockMaterial;
import net.bedrocknative.world.block.custom.BlockProblems;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockLooks;
import net.bedrocknative.world.block.custom.CustomBlockModel;
import net.bedrocknative.world.block.custom.CustomBlockSet;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.block.custom.CustomBlocks;
import net.bedrocknative.world.block.custom.SlotMemory;
import net.bedrocknative.world.block.custom.SlotPlan;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.client.resources.model.geometry.QuadCollection.Builder;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class BedrockBlockModels {
   private static final boolean SLOT_PICTURES = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.slotPictures", "on").trim());
   private static final boolean BAKE_ON_CLIENT = "client".equalsIgnoreCase(System.getProperty("bedrocknative.blockBake", "").trim());
   private static int bakes;
   private static final int BIRCH = -8345771;
   private static final int SPRUCE = -10380959;
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
   private static volatile BlockStateModel @Nullable [] models;
   private static int @Nullable [] restitchAsked;
   private static @Nullable SlotMemory slotMemory;
   private static volatile @Nullable String sessionServer;
   private static volatile int @Nullable [] rememberedSlots;
   private static final Map<String, BedrockBlockModels.ItemLook> items = new ConcurrentHashMap<>();
   private static volatile BedrockBlockModels.@Nullable Prepared itemPrepared;
   private static volatile Map<String, BedrockBlockModels.Placed> itemPlaced = Map.of();
   private static volatile @Nullable TextureAtlasSprite itemMissing;
   private static BedrockBlockModels.@Nullable Prepared prepared;
   private static List<BedrockBlockModels.Animated> animated = List.of();
   private static long animationTick;
   private static int generation;
   private static boolean active;
   private static boolean tinted;
   private static final int BLEND_BUDGET = 8;
   private static int blendCursor;

   private BedrockBlockModels() {
   }

   public static @Nullable BlockStateModel model(BlockState state) {
      BlockStateModel[] current = models;
      if (current != null && state.getBlock() instanceof CarrierBlock) {
         int slot = state.getValue(CarrierBlock.SLOT);
         return slot < current.length ? current[slot] : null;
      } else {
         return null;
      }
   }

   public static BedrockBlockModels.@Nullable ItemLook item(String block) {
      BedrockBlockModels.ItemLook known = items.get(block);
      if (known != null) {
         return known;
      }

      BedrockBlockModels.Prepared content = itemPrepared;
      Map<String, BedrockBlockModels.Placed> placed = itemPlaced;
      TextureAtlasSprite missing = itemMissing;
      if (content != null && missing != null) {
         synchronized (items) {
            known = items.get(block);
            if (known != null) {
               return known;
            }

            int slot = content.set().firstSlot(block);
            if (slot < 0) {
               return null;
            }

            CustomBlockLooks.Look look = content.looks().bySlot()[slot];
            List<BakedQuad> quads = quads(look, placed, missing, false);
            look.releaseQuads();
            Vector3fc[] extents = CuboidItemModelWrapper.computeExtents(quads);
            BlockItemDisplay display = content.looks().itemDisplays().get(block);
            BedrockBlockModels.ItemLook built = new BedrockBlockModels.ItemLook(ItemQuads.split(quads), () -> extents, display, extents);
            items.put(block, built);
            return built;
         }
      } else {
         return null;
      }
   }

   public static List<String> animations() {
      List<String> lines = new ArrayList<>();

      for (BedrockBlockModels.Animated slot : animated) {
         lines.add(
            slot.key
               + ": frame "
               + slot.frame
               + " of "
               + Arrays.toString(slot.animation.order())
               + ", "
               + slot.animation.ticksPerFrame()
               + " ticks per frame"
               + (slot.animation.blend() ? ", blended" : "")
         );
      }

      return lines;
   }

   public static List<BlockProblems.Problem> problems() {
      BedrockBlockModels.Prepared current = prepared;
      return current == null ? List.of() : current.looks().problems().list();
   }

   public static int quadCount(BlockState state) {
      return model(state) instanceof CustomBlockStateModel custom ? custom.quadCount() : -1;
   }

   public static void sessionStarts(String server) {
      int[] remembered = slotMemory().get(server);
      sessionServer = server;
      rememberedSlots = remembered;
      if (remembered != null) {
         BlockSlotSource.request(remembered);
         BedrockNative.LOGGER
            .info(
               "Bedrock blocks: {} needed the block atlas slots {} last time; the first stitch reserves them",
               server,
               new SlotPlan.Plan(remembered, new int[0], 0)
            );
      }
   }

   public static void requestRememberedSlots() {
      int[] remembered = rememberedSlots;
      if (remembered != null) {
         BlockSlotSource.request(remembered);
      }
   }

   private static synchronized SlotMemory slotMemory() {
      if (slotMemory == null) {
         slotMemory = SlotMemory.load(FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("block-slots.json"));
      }

      return slotMemory;
   }

   private static void rememberSlots(@Nullable String server, int[] counts) {
      if (server != null) {
         Util.ioPool().execute(() -> slotMemory().put(server, counts));
      }
   }

   public static void start() {
      CustomBlocks.refreshStates();
      if (!tinted) {
         tinted = true;
         Minecraft.getInstance()
            .getBlockColors()
            .register(
               List.of(
                  BlockTintSources.grass(),
                  BlockTintSources.foliage(),
                  BlockTintSources.constant(-8345771),
                  BlockTintSources.constant(-10380959),
                  BlockTintSources.dryFoliage(),
                  BlockTintSources.water()
               ),
               CustomBlocks.CARRIER
            );
      }

      reload();
   }

   public static void packsChanged() {
      if (active) {
         reload();
      }
   }

   public static void release() {
      active = false;
      generation++;
      models = null;
      items.clear();
      itemPrepared = null;
      itemPlaced = Map.of();
      itemMissing = null;
      closeAnimations();
      BlockSlotSource.reset();
      restitchAsked = null;
      CustomBlocks.useSoundTypes(new String[0]);
      LOGGED.clear();
      if (prepared != null) {
         prepared.close();
         prepared = null;
      }
   }

   public static void atlasUploaded(TextureAtlas atlas) {
      if (active && prepared != null) {
         bake(atlas, prepared);
         if (BlockSlotSource.behind()) {
            restitch("the block atlas was stitched before the server's blocks asked for the slots " + prepared.plan());
         }
      }
   }

   public static void animate(TextureAtlas atlas) {
      List<BedrockBlockModels.Animated> current = animated;
      int count = current.size();
      if (count != 0) {
         long tick = ++animationTick;
         int budget = 8;
         int start = blendCursor % count;
         int firstSkipped = -1;
         List<BedrockBlockModels.Animated> failed = null;

         for (int step = 0; step < count; step++) {
            int index = (start + step) % count;
            BedrockBlockModels.Animated slot = current.get(index);
            if (budget != 0 || !slot.blendDue(tick)) {
               try {
                  if (slot.show(atlas, tick)) {
                     budget--;
                  }
               } catch (RuntimeException e) {
                  BedrockNative.LOGGER.warn("Bedrock blocks: flipbook {} stopped", slot.key, e);
                  if (failed == null) {
                     failed = new ArrayList<>();
                  }

                  failed.add(slot);
               }
            } else if (firstSkipped < 0) {
               firstSkipped = index;
            }
         }

         blendCursor = firstSkipped < 0 ? start : firstSkipped;
         if (failed != null) {
            List<BedrockBlockModels.Animated> gone = failed;
            animated = current.stream().filter(other -> !gone.contains(other)).toList();
            blendCursor = 0;
            gone.forEach(BedrockBlockModels.Animated::close);
         }
      }
   }

   public static void checkSlots() {
      Minecraft minecraft = Minecraft.getInstance();
      if (active && prepared != null && BlockSlotSource.behind() && minecraft.isRunning()) {
         if (!(minecraft.gui.overlay() instanceof LoadingOverlay) && !BedrockVanillaTextures.busy()) {
            restitch(prepared.images().size() + " textures need the slots " + prepared.plan());
         }
      }
   }

   public static void tick() {
      if (active && prepared != null && BlockSlotSource.behind() && !Arrays.equals(BlockSlotSource.requested(), restitchAsked)) {
         checkSlots();
      }
   }

   private static void restitch(String why) {
      int[] wanted = BlockSlotSource.requested();
      if (Arrays.equals(wanted, restitchAsked)) {
         BedrockNative.LOGGER.warn("Bedrock blocks: {}, but a restitch for these slots already ran; not asking again", why);
      } else {
         restitchAsked = wanted;
         BedrockNative.LOGGER.info("Bedrock blocks: {}; restitching the block atlas", why);
         AtlasReload.run(Minecraft.getInstance(), "server block slots");
      }
   }

   private static void reload() {
      active = true;
      int current = ++generation;
      CustomBlockSet set = CustomBlockSet.current();
      if (set.size() == 0) {
         models = null;
         items.clear();
         itemPrepared = null;
         itemPlaced = Map.of();
         itemMissing = null;
         closeAnimations();
      } else {
         PackStack stack = PackStack.active();
         Minecraft minecraft = Minecraft.getInstance();
         AtlasPacking packing = AtlasPacking.of(minecraft, AtlasIds.BLOCKS, true);
         String server = sessionServer;
         CompletableFuture.<BedrockBlockModels.Prepared>supplyAsync(() -> prepare(stack, set, packing, () -> current != generation), Util.backgroundExecutor())
            .whenCompleteAsync(
               (loaded, error) -> {
                  if (error != null) {
                     BedrockNative.LOGGER.warn("Couldn't load the server's block models", error);
                  } else if (loaded != null) {
                     if (current != generation) {
                        loaded.close();
                     } else {
                        long started = FrameTimes.start();
                        if (prepared != null) {
                           prepared.close();
                        }

                        prepared = loaded;
                        CustomBlocks.useSoundTypes(loaded.looks().soundTypes());
                        BlockSlotSource.request(loaded.plan().counts());
                        rememberSlots(server, loaded.plan().counts());
                        boolean deferred = AtlasReload.blockAtlasPending();
                        if (!deferred) {
                           bake(minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS), loaded);
                        }

                        int sections = !deferred && BAKE_ON_CLIENT ? redrawCustomBlockSections(minecraft) : 0;
                        checkSlots();
                        long nanos = System.nanoTime() - started;
                        FrameTimes.end(FrameWatch.Bucket.CUSTOM_BLOCKS, started);
                        BedrockNative.LOGGER
                           .info(
                              "Bedrock blocks: {} custom states {} in {} ms on the client thread, {} sections with them redrawn",
                              new Object[]{
                                 set.size(),
                                 deferred ? "ready, baked at the block atlas upload of the reload under way," : "baked",
                                 Math.round(nanos / 1000000.0),
                                 sections
                              }
                           );
                     }
                  }
               },
               minecraft
            );
      }
   }

   private static int redrawCustomBlockSections(Minecraft minecraft) {
      ClientLevel level = minecraft.level;
      if (level == null) {
         return 0;
      }

      int redrawn = 0;
      AtomicReferenceArray<LevelChunk> chunks = level.getChunkSource().storage.chunks;

      for (int i = 0; i < chunks.length(); i++) {
         LevelChunk chunk = chunks.get(i);
         if (chunk != null) {
            LevelChunkSection[] sections = chunk.getSections();

            for (int index = 0; index < sections.length; index++) {
               LevelChunkSection section = sections[index];
               if (!section.hasOnlyAir() && section.maybeHas(state -> state.is(CustomBlocks.CARRIER))) {
                  minecraft.levelExtractor.setSectionDirty(chunk.getPos().x(), level.getSectionYFromSectionIndex(index), chunk.getPos().z());
                  redrawn++;
               }
            }
         }
      }

      return redrawn;
   }

   private static BedrockBlockModels.@Nullable Prepared cancelled(Map<String, BedrockBlockModels.Image> images) {
      images.values().forEach(image -> image.image().close());
      return null;
   }

   private static BedrockBlockModels.Prepared prepare(PackStack stack, CustomBlockSet set, AtlasPacking packing, BooleanSupplier cancelled) {
      if (cancelled.getAsBoolean()) {
         return null;
      }

      CustomBlockLooks.Loaded looks = CustomBlockLooks.load(stack, set);
      Map<String, BedrockBlockModels.Image> images = new LinkedHashMap<>();
      int animatedCount = 0;

      for (CustomBlockLooks.Texture texture : looks.textures().values()) {
         if (cancelled.getAsBoolean()) {
            return cancelled(images);
         }

         BedrockBlockModels.Image image = read(stack, texture, looks);
         if (image != null) {
            images.put(texture.key(), image);
            animatedCount += image.animation() != null ? 1 : 0;
         }
      }

      if (cancelled.getAsBoolean()) {
         return cancelled(images);
      }

      List<int[]> sizes = new ArrayList<>(images.size());
      images.values().forEach(image -> sizes.add(new int[]{image.width(), image.height()}));
      SlotPlan.Plan plan = SlotPlan.plan(sizes, SlotPlan.budget(packing.maxSide()), packing.padding(), packing.granularity());
      BlockProblems problems = looks.problems();
      problems.log(LOGGED, BedrockNative.LOGGER::info);
      BedrockNative.LOGGER
         .info(
            "Bedrock blocks: {} custom states, {} textures ({} read, {} flipbooks) in slots {}, {} blocks with missing textures, problems {}",
            new Object[]{set.size(), looks.textures().size(), images.size(), animatedCount, plan, problems.blocksWithMissingTextures(), problems.summary()}
         );
      if (!looks.itemDisplays().isEmpty()) {
         List<String> named = new ArrayList<>(new TreeSet<>(looks.itemDisplays().keySet()));
         BedrockNative.LOGGER
            .info(
               "Bedrock block items: {} of {} custom blocks placed by their geometry's item_display_transforms {}",
               new Object[]{named.size(), set.blocks().size(), named.size() > 12 ? named.subList(0, 12) + " ..." : named}
            );
      }

      if (plan.unplaced() > 0) {
         BedrockNative.LOGGER
            .warn(
               "Bedrock blocks: {} of {} textures don't fit the block atlas even in 16 px slots (GPU limit {} px, padding {}); they show the missing texture",
               new Object[]{plan.unplaced(), images.size(), packing.maxSide(), packing.padding()}
            );
      }

      return new BedrockBlockModels.Prepared(set, looks, images, plan, pictures(images, plan, packing));
   }

   private static Map<String, BedrockBlockModels.SlotPicture> pictures(Map<String, BedrockBlockModels.Image> images, SlotPlan.Plan plan, AtlasPacking packing) {
      Map<String, BedrockBlockModels.SlotPicture> pictures = new HashMap<>();
      if (!SLOT_PICTURES) {
         return pictures;
      }

      int mipLevels = Integer.numberOfTrailingZeros(packing.granularity());
      int texture = -1;

      for (Entry<String, BedrockBlockModels.Image> entry : images.entrySet()) {
         texture++;
         BedrockBlockModels.Image image = entry.getValue();
         int planned = texture < plan.classes().length ? plan.classes()[texture] : -1;
         if (planned >= 0 && image.animation() == null) {
            int size = BlockSlotSource.SIZES[planned];
            NativeImage fitted = fit(image.image(), 0, image.width(), image.height(), size);

            try {
               int levels = Math.min(mipLevels + 1, Integer.numberOfTrailingZeros(size) + 1);
               pictures.put(
                  entry.getKey(),
                  new BedrockBlockModels.SlotPicture(
                     size,
                     packing.padding(),
                     fitted.getWidth(),
                     fitted.getHeight(),
                     slotPictures(BlockSlotSource.slot(size, 0), fitted, size, packing.padding(), levels)
                  )
               );
            } finally {
               if (fitted != image.image()) {
                  fitted.close();
               }
            }
         }
      }

      return pictures;
   }

   private static BedrockBlockModels.@Nullable Image read(PackStack stack, CustomBlockLooks.Texture texture, CustomBlockLooks.Loaded looks) {
      boolean fallback = texture.flipbook() != null && texture.still() != null && !texture.still().equals(texture.path());
      NativeImage image = decode(stack, texture.path(), texture, looks, !fallback);
      boolean strip = image != null && texture.flipbook() != null;
      if (image == null && fallback) {
         image = decode(stack, texture.still(), texture, looks, true);
      }

      if (image == null) {
         return null;
      }

      if (texture.opaque()) {
         forceOpaque(image);
      }

      int width = image.getWidth();
      if (strip) {
         int frames = image.getHeight() / Math.max(1, width);
         FlipbookAnimation animation = frames >= 2 ? FlipbookAnimation.of(texture.flipbook(), frames) : null;
         if (animation != null) {
            return new BedrockBlockModels.Image(image, animation);
         } else {
            return image.getHeight() <= width
               ? new BedrockBlockModels.Image(image, null)
               : new BedrockBlockModels.Image(frame(image, FlipbookAnimation.stillFrame(texture.flipbook(), frames)), null);
         }
      } else {
         return new BedrockBlockModels.Image(texture.firstFrame() && image.getHeight() > width ? frame(image, 0) : image, null);
      }
   }

   private static NativeImage frame(NativeImage strip, int index) {
      int width = strip.getWidth();
      NativeImage frame = new NativeImage(width, width, false);
      strip.copyRect(frame, 0, index * width, 0, 0, width, width, false, false);
      strip.close();
      return frame;
   }

   private static @Nullable NativeImage decode(PackStack stack, String path, CustomBlockLooks.Texture texture, CustomBlockLooks.Loaded looks, boolean report) {
      Optional<PackStack.Resource> resource = stack.texture(path);
      if (resource.isEmpty()) {
         if (report) {
            problem(looks, texture, BlockProblems.Reason.TEXTURE_FILE_MISSING, "file " + path + " in no pack");
         }

         return null;
      } else {
         try {
            return BedrockTextures.decode(resource.get().data());
         } catch (IOException | RuntimeException e) {
            if (report) {
               problem(looks, texture, BlockProblems.Reason.TEXTURE_UNREADABLE, resource.get().path() + ": " + e.getMessage());
            }

            BedrockNative.LOGGER.debug("Couldn't decode Bedrock block texture {}", resource.get().path(), e);
            return null;
         }
      }
   }

   private static void problem(CustomBlockLooks.Loaded looks, CustomBlockLooks.Texture texture, BlockProblems.Reason reason, String detail) {
      for (String block : looks.users().getOrDefault(texture.key(), Set.of())) {
         looks.problems().add(block, reason, detail);
      }
   }

   private static void forceOpaque(NativeImage image) {
      int count = image.getWidth() * image.getHeight();
      IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), count);

      for (int i = 0; i < count; i++) {
         pixels.put(i, pixels.get(i) | 0xFF000000);
      }
   }

   private static void bake(TextureAtlas atlas, BedrockBlockModels.Prepared content) {
      long started = System.nanoTime();
      closeAnimations();
      List<BedrockBlockModels.Animated> animations = new ArrayList<>();
      int[] made = new int[2];
      Map<String, BedrockBlockModels.Placed> placed = place(atlas, content, animations, made);
      String placing = String.format(
         Locale.ROOT,
         "%d textures into the block atlas (%d from pictures made off the client thread, %d made here) in %.1f ms on the client thread",
         placed.size(),
         made[0],
         made[1],
         (System.nanoTime() - started) / 1000000.0
      );
      animationTick = 0L;
      animated = List.copyOf(animations);
      TextureAtlasSprite missing = atlas.missingSprite();
      int bake = ++bakes;
      Minecraft minecraft = Minecraft.getInstance();
      if (BAKE_ON_CLIENT) {
         BedrockBlockModels.Baked baked = BedrockBlockModels.Baked.of(content, placed, missing);
         content.releaseQuads();
         models = baked.models();
         itemPrepared = content;
         itemPlaced = placed;
         itemMissing = missing;
         items.clear();
         BedrockNative.LOGGER
            .info(
               "Bedrock blocks: {}; {} models in {} ms on the client thread",
               new Object[]{placing, baked.models().length, String.format(Locale.ROOT, "%.1f", baked.nanos() / 1000000.0)}
            );
      } else {
         CompletableFuture.<BedrockBlockModels.Baked>supplyAsync(() -> BedrockBlockModels.Baked.of(content, placed, missing), Util.backgroundExecutor())
            .whenCompleteAsync(
               (bakedx, error) -> {
                  if (error != null) {
                     BedrockNative.LOGGER.warn("Couldn't build the server's block models", error);
                  } else if (bake == bakes && prepared == content && active) {
                     content.releaseQuads();
                     models = bakedx.models();
                     itemPrepared = content;
                     itemPlaced = placed;
                     itemMissing = missing;
                     items.clear();
                     int sections = redrawCustomBlockSections(minecraft);
                     BedrockNative.LOGGER
                        .info(
                           "Bedrock blocks: {}; {} models built in {} ms on a worker, {} sections with them redrawn",
                           new Object[]{placing, bakedx.models().length, String.format(Locale.ROOT, "%.1f", bakedx.nanos() / 1000000.0), sections}
                        );
                  }
               },
               minecraft
            );
      }
   }

   private static void closeAnimations() {
      List<BedrockBlockModels.Animated> current = animated;
      animated = List.of();
      current.forEach(BedrockBlockModels.Animated::close);
   }

   private static Map<String, BedrockBlockModels.Placed> place(
      TextureAtlas atlas, BedrockBlockModels.Prepared content, List<BedrockBlockModels.Animated> animations, int[] made
   ) {
      Map<String, BedrockBlockModels.Image> images = content.images();
      SlotPlan.Plan plan = content.plan();
      GpuTexture gpu = atlas.getTexture();
      List<ArrayDeque<TextureAtlasSprite>> free = new ArrayList<>();

      for (int sizeClass = 0; sizeClass < BlockSlotSource.SIZES.length; sizeClass++) {
         ArrayDeque<TextureAtlasSprite> slots = new ArrayDeque<>();
         int index = 0;

         while (true) {
            TextureAtlasSprite sprite = atlas.getSprite(BlockSlotSource.slot(BlockSlotSource.SIZES[sizeClass], index));
            if (sprite == atlas.missingSprite()) {
               free.add(slots);
               break;
            }

            slots.add(sprite);
            index++;
         }
      }

      Map<String, BedrockBlockModels.Placed> placed = new HashMap<>();
      List<String> unplaced = new ArrayList<>();
      int texture = -1;

      for (Entry<String, BedrockBlockModels.Image> entry : images.entrySet()) {
         texture++;
         BedrockBlockModels.Image image = entry.getValue();
         int planned = texture < plan.classes().length ? plan.classes()[texture] : SlotPlan.sizeClass(image.width(), image.height());
         int sizeClass = planned < 0 ? -1 : sizeClass(planned, free);
         if (sizeClass < 0) {
            unplaced.add(entry.getKey());
         } else {
            TextureAtlasSprite sprite = free.get(sizeClass).poll();
            int size = BlockSlotSource.SIZES[sizeClass];
            if (image.animation() != null) {
               NativeImage[] frames = frames(image, size);
               animations.add(new BedrockBlockModels.Animated(entry.getKey(), sprite, frames, image.animation()));
               upload(atlas, sprite, frames[image.animation().frame(0L)]);
               placed.put(entry.getKey(), new BedrockBlockModels.Placed(sprite, (float)frames[0].getWidth() / size, (float)frames[0].getHeight() / size));
            } else {
               BedrockBlockModels.SlotPicture picture = content.pictures().get(entry.getKey());
               int padding = AtlasPadding.contentOffset(sprite.getX(), sprite.getU0(), gpu.getWidth(0));
               int levels = levels(gpu, size);
               if (picture != null && picture.size() == size && picture.padding() == padding && picture.levels().length >= levels) {
                  write(gpu, sprite, picture.levels(), levels, size, padding);
                  placed.put(entry.getKey(), new BedrockBlockModels.Placed(sprite, (float)picture.contentWidth() / size, (float)picture.contentHeight() / size));
                  made[0]++;
               } else {
                  NativeImage fitted = fit(image.image(), 0, image.width(), image.height(), size);
                  upload(atlas, sprite, fitted);
                  placed.put(entry.getKey(), new BedrockBlockModels.Placed(sprite, (float)fitted.getWidth() / size, (float)fitted.getHeight() / size));
                  made[1]++;
                  if (fitted != image.image()) {
                     fitted.close();
                  }
               }
            }
         }
      }

      if (!unplaced.isEmpty()) {
         boolean restitching = BlockSlotSource.behind();
         BedrockNative.LOGGER
            .warn(
               "Bedrock blocks: no free atlas slot for {} of {} textures ({}); they show the missing texture: {}",
               new Object[]{
                  unplaced.size(),
                  images.size(),
                  restitching ? "the block atlas will be restitched with the slots " + plan : "planned slots " + plan,
                  unplaced.subList(0, Math.min(10, unplaced.size()))
               }
            );
         if (!restitching) {
            CustomBlockLooks.Loaded looks = content.looks();

            for (String key : unplaced) {
               CustomBlockLooks.Texture missingTexture = looks.textures().get(key);
               if (missingTexture != null) {
                  problem(looks, missingTexture, BlockProblems.Reason.NO_ATLAS_SLOT, missingTexture.path() + " (slots " + plan + ")");
               }
            }

            looks.problems().log(LOGGED, BedrockNative.LOGGER::info);
         }
      }

      return placed;
   }

   private static NativeImage[] frames(BedrockBlockModels.Image image, int size) {
      int frameSize = image.width();
      int count = image.image().getHeight() / frameSize;
      NativeImage[] frames = new NativeImage[count];

      for (int index = 0; index < count; index++) {
         NativeImage frame = new NativeImage(frameSize, frameSize, false);
         image.image().copyRect(frame, 0, index * frameSize, 0, 0, frameSize, frameSize, false, false);
         NativeImage fitted = fit(frame, 0, frameSize, frameSize, size);
         if (fitted != frame) {
            frame.close();
         }

         frames[index] = fitted;
      }

      return frames;
   }

   private static NativeImage fit(NativeImage image, int y, int width, int height, int size) {
      int needed = Math.max(width, height);
      if (needed <= size && y == 0 && width == image.getWidth() && height == image.getHeight()) {
         return image;
      }

      float factor = Math.min(1.0F, (float)size / needed);
      NativeImage fitted = new NativeImage(Math.max(1, Math.round(width * factor)), Math.max(1, Math.round(height * factor)), false);
      image.resizeSubRectTo(0, y, width, height, fitted);
      return fitted;
   }

   private static int sizeClass(int planned, List<ArrayDeque<TextureAtlasSprite>> free) {
      for (int sizeClass = planned; sizeClass < BlockSlotSource.SIZES.length; sizeClass++) {
         if (!free.get(sizeClass).isEmpty()) {
            return sizeClass;
         }
      }

      for (int sizeClass = planned - 1; sizeClass >= 0; sizeClass--) {
         if (!free.get(sizeClass).isEmpty()) {
            return sizeClass;
         }
      }

      return -1;
   }

   private static void upload(TextureAtlas atlas, TextureAtlasSprite sprite, NativeImage image) {
      int size = sprite.contents().width();
      GpuTexture texture = atlas.getTexture();
      int padding = AtlasPadding.contentOffset(sprite.getX(), sprite.getU0(), texture.getWidth(0));
      int levels = levels(texture, size);
      write(texture, sprite, slotPictures(sprite.contents().name(), image, size, padding, levels), levels, size, padding);
   }

   private static int levels(GpuTexture texture, int size) {
      return Math.min(texture.getMipLevels(), Integer.numberOfTrailingZeros(size) + 1);
   }

   private static int[][] slotPictures(Identifier name, NativeImage image, int size, int padding, int levels) {
      NativeImage slot = new NativeImage(size, size, true);
      image.copyRect(slot, 0, 0, 0, 0, Math.min(size, image.getWidth()), Math.min(size, image.getHeight()), false, false);
      NativeImage[] mips = MipmapGenerator.generateMipLevels(name, new NativeImage[]{slot}, levels - 1, MipmapStrategy.AUTO, 0.0F, slot.computeTransparency());
      int[][] pictures = new int[levels][];

      for (int level = 0; level < levels; level++) {
         NativeImage mip = mips[level];
         pictures[level] = AtlasPadding.extend(mip.getPixelsABGR(), mip.getWidth(), mip.getHeight(), padding >> level);
      }

      for (NativeImage mip : mips) {
         mip.close();
      }

      if (mips.length == 0 || mips[0] != slot) {
         slot.close();
      }

      return pictures;
   }

   private static void write(GpuTexture texture, TextureAtlasSprite sprite, int[][] pictures, int levels, int size, int padding) {
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      int largest = size + 2 * padding;
      ByteBuffer buffer = MemoryUtil.memAlloc(largest * largest * 4);

      try {
         for (int level = 0; level < levels; level++) {
            int side = (size >> level) + 2 * (padding >> level);
            int[] picture = pictures[level];
            MemoryUtil.memIntBuffer(MemoryUtil.memAddress(buffer), picture.length).put(0, picture);
            encoder.writeToTexture(texture, buffer, level, 0, sprite.getX() >> level, sprite.getY() >> level, side, side);
         }
      } finally {
         MemoryUtil.memFree(buffer);
      }
   }

   static BlockStateModel model(
      @Nullable CustomBlockState state, CustomBlockLooks.Look look, Map<String, BedrockBlockModels.Placed> placed, TextureAtlasSprite missing
   ) {
      Builder builder = new Builder();
      List<CustomBlockModel.ModelQuad> source = look.quads();
      List<BakedQuad> quads = quads(source, look, placed, missing, true);
      CustomBlockStateModel.Quad[] prepared = new CustomBlockStateModel.Quad[quads.size()];
      boolean ambientOcclusion = false;
      TextureAtlasSprite particle = null;

      for (int i = 0; i < quads.size(); i++) {
         CustomBlockModel.ModelQuad quad = source.get(i);
         BakedQuad baked = quads.get(i);
         if (quad.cullFace() != null) {
            builder.addCulledFace(quad.cullFace(), baked);
         } else {
            builder.addUnculledFace(baked);
         }

         ambientOcclusion |= quad.material().ambientOcclusion();
         if (!quad.back() && (particle == null || quad.face().equals("north"))) {
            particle = baked.materialInfo().sprite();
         }

         prepared[i] = new CustomBlockStateModel.Quad(
            baked, quad.cullFace(), quad.material().ambientOcclusion(), look.culls().get(i), quad.back() ? quad.direction().getOpposite() : null
         );
      }

      Material.Baked particleMaterial = new Material.Baked(particle != null ? particle : missing, false);
      SimpleModelWrapper part = new SimpleModelWrapper(builder.build(), ambientOcclusion, particleMaterial);
      return new CustomBlockStateModel(prepared, part, state != null ? state.state().name() : "", state != null ? state.geometry().cullingLayer() : null);
   }

   private static List<BakedQuad> quads(CustomBlockLooks.Look look, Map<String, BedrockBlockModels.Placed> placed, TextureAtlasSprite missing, boolean tints) {
      return quads(look.quads(), look, placed, missing, tints);
   }

   private static List<BakedQuad> quads(
      List<CustomBlockModel.ModelQuad> source,
      CustomBlockLooks.Look look,
      Map<String, BedrockBlockModels.Placed> placed,
      TextureAtlasSprite missing,
      boolean tints
   ) {
      List<BakedQuad> quads = new ArrayList<>(source.size());

      for (int i = 0; i < source.size(); i++) {
         String key = look.textures().get(i);
         BedrockBlockModels.Placed where = key == null ? null : placed.get(key);
         quads.add(bake(source.get(i), where != null ? where : new BedrockBlockModels.Placed(missing, 1.0F, 1.0F), tints));
      }

      return quads;
   }

   private static BakedQuad bake(CustomBlockModel.ModelQuad quad, BedrockBlockModels.Placed where, boolean tints) {
      BlockMaterial material = quad.material();
      ChunkSectionLayer layer = material.renderMethod().translucent()
         ? ChunkSectionLayer.TRANSLUCENT
         : (material.renderMethod().cutout() ? ChunkSectionLayer.CUTOUT : ChunkSectionLayer.SOLID);
      boolean translucent = layer == ChunkSectionLayer.TRANSLUCENT;
      TextureAtlasSprite sprite = where.sprite();
      Direction shade = !material.faceDimming() ? Direction.UP : (quad.back() ? quad.direction().getOpposite() : null);
      MaterialInfo info = new MaterialInfo(
         sprite,
         layer,
         translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
         translucent ? Sheets.translucentBlockItemGlintSheet() : Sheets.cutoutBlockItemGlintSheet(),
         translucent ? Sheets.translucentBlockItemGlintSpecialSheet() : Sheets.cutoutBlockItemGlintSpecialSheet(),
         tints ? material.tint().tintIndex() : -1,
         shade,
         0
      );
      long[] uvs = new long[4];

      for (int vertex = 0; vertex < 4; vertex++) {
         float u = Math.clamp(quad.uvs()[vertex * 2], 0.0F, 1.0F) * where.scaleU();
         float v = Math.clamp(quad.uvs()[vertex * 2 + 1], 0.0F, 1.0F) * where.scaleV();
         uvs[vertex] = UVPair.pack(sprite.getU(u), sprite.getV(v));
      }

      Vector3fc[] positions = quad.positions();
      return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], quad.direction(), info);
   }

   private static final class Animated {
      private final String key;
      private final TextureAtlasSprite sprite;
      private final NativeImage[] frames;
      private final FlipbookAnimation animation;
      private long shown = Long.MIN_VALUE;
      private int frame = -1;
      private @Nullable NativeImage scratch;

      Animated(String key, TextureAtlasSprite sprite, NativeImage[] frames, FlipbookAnimation animation) {
         this.key = key;
         this.sprite = sprite;
         this.frames = frames;
         this.animation = animation;
      }

      boolean blendDue(long tick) {
         return this.animation.picture(tick) != this.shown && this.animation.blendFactor(tick) > 0.0F;
      }

      boolean show(TextureAtlas atlas, long tick) {
         long picture = this.animation.picture(tick);
         if (picture == this.shown) {
            return false;
         }

         this.shown = picture;
         this.frame = this.animation.frame(tick);
         float blend = this.animation.blendFactor(tick);
         NativeImage from = this.frames[this.frame];
         if (blend <= 0.0F) {
            BedrockBlockModels.upload(atlas, this.sprite, from);
            return false;
         }

         NativeImage to = this.frames[this.animation.nextFrame(tick)];
         int count = from.getWidth() * from.getHeight();
         if (this.scratch == null) {
            this.scratch = new NativeImage(from.getWidth(), from.getHeight(), false);
         }

         IntBuffer source = MemoryUtil.memIntBuffer(from.getPointer(), count);
         IntBuffer target = MemoryUtil.memIntBuffer(to.getPointer(), count);
         IntBuffer out = MemoryUtil.memIntBuffer(this.scratch.getPointer(), count);

         for (int i = 0; i < count; i++) {
            out.put(i, FlipbookAnimation.lerp(source.get(i), target.get(i), blend));
         }

         BedrockBlockModels.upload(atlas, this.sprite, this.scratch);
         return true;
      }

      void close() {
         for (NativeImage image : this.frames) {
            image.close();
         }

         if (this.scratch != null) {
            this.scratch.close();
            this.scratch = null;
         }
      }
   }

   private record Baked(BlockStateModel[] models, long nanos) {
      static BedrockBlockModels.Baked of(BedrockBlockModels.Prepared content, Map<String, BedrockBlockModels.Placed> placed, TextureAtlasSprite missing) {
         long started = System.nanoTime();
         CustomBlockLooks.Look[] looks = content.looks().bySlot();
         BlockStateModel[] baked = new BlockStateModel[looks.length];

         for (int slot = 0; slot < looks.length; slot++) {
            baked[slot] = new LazyCustomBlockStateModel(content.set().data(slot), looks[slot], placed, missing);
         }

         return new BedrockBlockModels.Baked(baked, System.nanoTime() - started);
      }
   }

   private record Image(NativeImage image, @Nullable FlipbookAnimation animation) {
      int width() {
         return this.image.getWidth();
      }

      int height() {
         return this.animation != null ? this.image.getWidth() : this.image.getHeight();
      }
   }

   public record ItemLook(ItemQuads quads, Supplier<Vector3fc[]> extents, @Nullable BlockItemDisplay display, Vector3fc[] points) {
      @Override
      public int hashCode() {
         return System.identityHashCode(this);
      }

      @Override
      public boolean equals(Object other) {
         return this == other;
      }
   }

   record Placed(TextureAtlasSprite sprite, float scaleU, float scaleV) {
   }

   private record Prepared(
      CustomBlockSet set,
      CustomBlockLooks.Loaded looks,
      Map<String, BedrockBlockModels.Image> images,
      SlotPlan.Plan plan,
      Map<String, BedrockBlockModels.SlotPicture> pictures
   ) {
      void close() {
         this.images.values().forEach(image -> image.image().close());
      }

      void releaseQuads() {
         this.looks.releaseQuads();
      }
   }

   private record SlotPicture(int size, int padding, int contentWidth, int contentHeight, int[][] levels) {
   }
}
