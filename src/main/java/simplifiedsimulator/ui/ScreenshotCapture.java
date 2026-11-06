package simplifiedsimulator.ui;

import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.system.JmeSystem;
import com.jme3.texture.FrameBuffer;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves what is on screen to a PNG file after a number of frames, then calls back.
 * Add it to the GUI view port so the head-up display is included.
 */
public final class ScreenshotCapture implements SceneProcessor {

    private final Path file;
    private final int afterFrames;
    private final Runnable whenDone;
    private RenderManager renderManager;
    private ViewPort viewPort;
    private int frames;
    private boolean done;

    public ScreenshotCapture(Path file, int afterFrames, Runnable whenDone) {
        this.file = file;
        this.afterFrames = afterFrames;
        this.whenDone = whenDone;
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
        renderManager = rm;
        viewPort = vp;
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
        // the size is read when capturing
    }

    @Override
    public boolean isInitialized() {
        return renderManager != null;
    }

    @Override
    public void preFrame(float tpf) {
        // nothing to prepare
    }

    @Override
    public void postQueue(RenderQueue rq) {
        // nothing to change in the queue
    }

    @Override
    public void postFrame(FrameBuffer out) {
        if (done || ++frames < afterFrames) {
            return;
        }
        done = true;
        int w = viewPort.getCamera().getWidth();
        int h = viewPort.getCamera().getHeight();
        ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
        renderManager.getRenderer().readFrameBuffer(out, buffer);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(file)) {
                JmeSystem.writeImageFile(os, "png", buffer, w, h);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save screenshot " + file, e);
        }
        whenDone.run();
    }

    @Override
    public void cleanup() {
        // nothing to release
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
        // not profiled
    }
}
