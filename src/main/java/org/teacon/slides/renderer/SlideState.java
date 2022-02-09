package org.teacon.slides.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL32C;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.SlideImageStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author BloCamLimb
 */
public final class SlideState {

    private static final int RECYCLE_TICKS = 2400; // 2min
    private static final int RETRY_INTERVAL_TICKS = 160; // 8s
    private static final int FORCE_RECYCLE_LIFESPAN = 60000; // 50min

    private static final int CLEANER_INTERVAL_TICKS = 0xFFFF; // 54.6125min, must be (powerOfTwo - 1)

    private static final AtomicReference<Map<String, SlideState>> sCache;


    private static int sCleanerTimer;

    static {
        sCache = new AtomicReference<>(Collections.synchronizedMap(new HashMap<>()));
    }

    public static void tick(Minecraft mc) {
        Map<String, SlideState> map = sCache.getAcquire();
        if (!map.isEmpty()) {
            map.entrySet().removeIf(entry -> entry.getValue().tick(entry.getKey()));
        }
        if ((++sCleanerTimer & CLEANER_INTERVAL_TICKS) == 0) {
            int c = SlideImageStore.cleanImages();
            if (c != 0) {
                SlideShow.LOGGER.debug("Cleanup {} http cache image entries", c);
            }
            sCleanerTimer = 0;
        }
    }

    public static void onPlayerLeft(Minecraft mc) {
        RenderSystem.recordRenderCall(() -> {
            Map<String, SlideState> newCache = Collections.synchronizedMap(new HashMap<>());
            sCache.getAndSet(newCache).forEach((key, state) -> state.mSlide.close());
            SlideShow.LOGGER.info("Release all image resources");
        });
    }

    @Nullable
    public static Slide getSlide(@Nonnull String location) {
        if (location.isEmpty()) {
            return null;
        }
        return sCache.getAcquire().computeIfAbsent(location, key -> new SlideState())
                .getWithUpdate();
    }

    private Slide mSlide = Slide.empty();

    /**
     * Current state.
     */
    private volatile State mState = State.INITIALIZED;
    private int mCounter;
    private int mLifespan = FORCE_RECYCLE_LIFESPAN;

    @Nonnull
    private Slide getWithUpdate() {
        if (mState != State.FAILED_OR_EMPTY) {
            mCounter = RECYCLE_TICKS;
        }
        return mSlide;
    }

    /**
     * Ticks on the client/render thread.
     *
     * @return this slide is destroyed
     */
    public boolean tick(String location) {
        if (mState == State.INITIALIZED) {
            URI uri = createURI(location);
            if (uri == null) {
                mSlide = Slide.empty();
                mState = State.FAILED_OR_EMPTY;
                mCounter = RETRY_INTERVAL_TICKS;
            } else {
                loadImage(uri);
            }
        } else if (--mCounter < 0 || --mLifespan < 0) {
            RenderSystem.recordRenderCall(() -> {
                mSlide.close();
                // stop creating texture if the image is still downloading, but recycled
                mState = State.LOADED;
            });
            return true;
        }
        return false;
    }

    private void loadImageRemote(URI uri, boolean releaseOld) {
        SlideImageStore.getImage(uri, true)
                .thenCompose(this::loadImage)
                .thenAccept(texture -> {
                    if (mState != State.LOADED) {
                        if (releaseOld) {
                            mSlide.close();
                        }
                        mSlide = Slide.make(texture);
                        mState = State.LOADED;
                    }
                }).exceptionally(e -> {
                    // ZBX FA
                    SlideShow.LOGGER.fatal(e);
                    RenderSystem.recordRenderCall(() -> {
                        if (releaseOld) {
                            mSlide.close();
                        }
                        mSlide = Slide.failed();
                        mState = State.FAILED_OR_EMPTY;
                        mCounter = RETRY_INTERVAL_TICKS;
                    });
                    return null;
                });
    }

    private void loadImage(URI uri) {
        SlideImageStore.getImage(uri, true)
                .thenCompose(this::loadImage)
                .thenAccept(texture -> RenderSystem.recordRenderCall(() -> {
                    if (mState != State.LOADED) {
                        mSlide = Slide.make(texture);
                        loadImageRemote(uri, true);
                    }
                })).exceptionally(e -> {
                    RenderSystem.recordRenderCall(() -> {
                        if (mState != State.LOADED) {
                            loadImageRemote(uri, false);
                        }
                    });
                    return null;
                });
        mSlide = Slide.loading();
        mState = State.LOADING;
        mCounter = RECYCLE_TICKS;
    }

    @Nonnull
    private CompletableFuture<Integer> loadImage(byte[] data) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        RenderSystem.recordRenderCall(() -> {
            try {
                // specifying null will use image source channels
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                NativeImage image = NativeImage.read(null, inputStream);
                future.complete(loadTexture(image));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private int loadTexture(@Nonnull NativeImage image) {
        int texture = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(image.format() == NativeImage.Format.RGB ?
                        NativeImage.InternalGlFormat.RGB : NativeImage.InternalGlFormat.RGBA,
                texture, 4, image.getWidth(), image.getHeight());

        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_MAG_FILTER, GL32C.GL_NEAREST);
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_MIN_FILTER, GL32C.GL_LINEAR_MIPMAP_LINEAR);

        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_WRAP_S, GL32C.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_WRAP_T, GL32C.GL_CLAMP_TO_EDGE);

        GlStateManager._pixelStore(GL32C.GL_UNPACK_ROW_LENGTH, 0);

        GlStateManager._pixelStore(GL32C.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL32C.GL_UNPACK_SKIP_ROWS, 0);

        // specify pixel row alignment to 1
        GlStateManager._pixelStore(GL32C.GL_UNPACK_ALIGNMENT, 1);

        try (image) {
            GlStateManager._texSubImage2D(GL32C.GL_TEXTURE_2D, 0, 0, 0,
                    image.getWidth(), image.getHeight(), image.format().glFormat(), GL32C.GL_UNSIGNED_BYTE,
                    image.pixels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get image pointer", e);
        }

        // auto generate mipmap
        GL32C.glGenerateMipmap(GL32C.GL_TEXTURE_2D);

        return texture;
    }

    @Nullable
    public static URI createURI(String location) {
        if (StringUtils.isNotBlank(location)) {
            try {
                return URI.create(location);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public enum State {
        /**
         * States that will be changed at the next tick
         * <p>
         * NOTHING: the slide is ready for loading.
         * LOADING: a slide is loading and a loading image is displayed (expired after {@link #RECYCLE_TICKS}).
         */
        INITIALIZED, LOADING,
        /**
         * States that will not be changed but can be expired
         * <p>
         * LOADED: a network resource is succeeded to retrieve (no expiration if the slide is rendered).
         * FAILED_OR_EMPTY: it is empty or failed to retrieve the network resource (expired after {@link
         * #RETRY_INTERVAL_TICKS}).
         */
        LOADED, FAILED_OR_EMPTY
    }
}
