package org.teacon.slides.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.SlideImageStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author BloCamLimb
 */
public final class SlideState {

    private static final int RECYCLE_TICKS = 2400; // 2min
    private static final int RETRY_INTERVAL_TICKS = 160; // 8s
    private static final int FORCE_RECYCLE_LIFESPAN = 36000; // 30min

    private static final int CLEANER_INTERVAL_TICKS = (1 << 16) - 1; // 54min

    private static final Map<String, SlideState> sCache = new HashMap<>();

    private static final Field IMAGE_POINTER;

    private static int sCleanerTimer;

    static {
        Field IMAGE_POINTER1;
        try {
            // TODO: Use access widener, this only work on deobfuscated Minecraft
            IMAGE_POINTER1 = NativeImage.class.getDeclaredField("pixels");
            IMAGE_POINTER1.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            IMAGE_POINTER1 = null;
        }
        IMAGE_POINTER = IMAGE_POINTER1;
    }

    public static void tick(@Nonnull Minecraft mc) {
        if (!sCache.isEmpty()) {
            sCache.entrySet().removeIf(entry -> entry.getValue().tick(entry.getKey()));
        }
        if ((++sCleanerTimer & CLEANER_INTERVAL_TICKS) == 0) {
            int c = SlideImageStore.cleanImages();
            if (c != 0) {
                SlideShow.LOGGER.debug("Cleanup {} http cache image entries", c);
            }
            sCleanerTimer = 0;
        }
    }

    @Nonnull
    public static Slide getSlide(String url) {
        if (url == null || url.isEmpty()) {
            return Slide.NOTHING;
        }
        return sCache.computeIfAbsent(url, k -> new SlideState()).get();
    }

    private Slide mSlide = Slide.empty();

    /**
     * Current state.
     */
    private volatile State mState = State.INITIALIZED;
    private int mCounter;
    private int mLifespan = FORCE_RECYCLE_LIFESPAN;

    @Nonnull
    private Slide get() {
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
                mSlide.release();
                // stop creating texture if the image is still downloading, but recycled
                mState = State.LOADED;
            });
            return true;
        }
        return false;
    }

    private void loadImageRemote(URI uri, boolean releaseOld) {
        SlideImageStore.getImage(uri, true).thenCompose(this::loadImage).thenAccept(texture -> {
            if (mState != State.LOADED) {
                if (releaseOld) {
                    mSlide.release();
                }
                mSlide = Slide.make(texture);
                mState = State.LOADED;
            }
        }).exceptionally(e -> {
            RenderSystem.recordRenderCall(() -> {
                if (releaseOld) {
                    mSlide.release();
                }
                mSlide = Slide.failed();
                mState = State.FAILED_OR_EMPTY;
                mCounter = RETRY_INTERVAL_TICKS;
            });
            return null;
        });
    }

    private void loadImage(URI uri) {
        SlideImageStore.getImage(uri, true).thenCompose(this::loadImage).thenAccept(texture -> {
            RenderSystem.recordRenderCall(() -> {
                if (mState != State.LOADED) {
                    mSlide = Slide.make(texture);
                    loadImageRemote(uri, true);
                }
            });
        }).exceptionally(e -> {
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

    private CompletableFuture<Integer> loadImage(byte[] data) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        RenderSystem.recordRenderCall(() -> {
            try {
                // specifying null will use image source channels
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                NativeImage image = NativeImage.read(null, inputStream);
                future.complete(loadTexture(image));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private int loadTexture(@Nonnull NativeImage image) {
        int texture = TextureUtil.generateTextureId();
        // specify maximum mipmap level to 2
        TextureUtil.prepareImage(image.format() == NativeImage.Format.RGB ?
                        NativeImage.InternalGlFormat.RGB : NativeImage.InternalGlFormat.RGBA,
                texture, 2, image.getWidth(), image.getHeight());

        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);

        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);

        // specify pixel row alignment to 1
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);

        try {
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                    image.getWidth(), image.getHeight(), image.format().glFormat(), GL11.GL_UNSIGNED_BYTE,
                    IMAGE_POINTER.getLong(image));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get image pointer");
        }

        image.close();

        // auto generate mipmap
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

        return texture;
    }

    @Nullable
    private static URI createURI(String location) {
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
         * FAILED_OR_EMPTY: it is empty or failed to retrieve the network resource (expired after {@link #RETRY_INTERVAL_TICKS}).
         */
        LOADED, FAILED_OR_EMPTY
    }
}
