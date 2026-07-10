package org.takesome.kaylasEngine.gui.components.image;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Reusable Swing GIF player owned by KaylasUIEngine.
 *
 * <p>Frame decoding runs outside the EDT. Playback uses a coalesced one-shot Swing timer rather
 * than a dedicated executor thread for every component. The decoder applies GIF disposal methods
 * and frame offsets before publishing immutable, fully composed animation frames.</p>
 */
public final class GifPlayer extends JPanel implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger(GifPlayer.class);
    private static final Dimension DEFAULT_SIZE = new Dimension(100, 100);
    private static final int MIN_FRAME_DELAY_MS = 10;

    private final GifSource source;
    private final SwingWorker<List<AnimationFrame>, Void> loadWorker;

    private volatile List<AnimationFrame> frames = List.of();
    private volatile BufferedImage displayImage;
    private volatile Throwable loadFailure;
    private volatile double speedFactor = 1.0d;

    private Timer playbackTimer;
    private int currentFrameIndex;
    private boolean playRequested = true;
    private boolean running;
    private boolean closed;

    public GifPlayer(File gifFile) throws FileNotFoundException {
        this(fileSource(validateFile(gifFile)));
    }

    public GifPlayer(InputStream inputStream) throws IOException {
        this(memorySource(readSource(inputStream)));
    }

    private GifPlayer(GifSource source) {
        this.source = Objects.requireNonNull(source, "source");
        setOpaque(false);
        this.loadWorker = createLoadWorker();
        this.loadWorker.execute();
    }

    public void start() {
        runOnEdt(() -> {
            if (closed) {
                return;
            }
            playRequested = true;
            startPlaybackIfReady();
        });
    }

    public void stop() {
        runOnEdt(() -> {
            playRequested = false;
            stopPlaybackTimer();
        });
    }

    public void setSpeedFactor(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("Speed factor must be finite and positive.");
        }
        speedFactor = factor;
        runOnEdt(() -> {
            if (running) {
                scheduleNextFrame();
            }
        });
    }

    public double getSpeedFactor() {
        return speedFactor;
    }

    public boolean isLoaded() {
        return !frames.isEmpty() && loadFailure == null;
    }

    public boolean isRunning() {
        return running;
    }

    public Throwable getLoadFailure() {
        return loadFailure;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (playRequested) {
            startPlaybackIfReady();
        }
    }

    @Override
    public void removeNotify() {
        stopPlaybackTimer();
        super.removeNotify();
    }

    @Override
    public void close() {
        runOnEdt(() -> {
            if (closed) {
                return;
            }
            closed = true;
            playRequested = false;
            stopPlaybackTimer();
            loadWorker.cancel(true);
            frames = List.of();
            displayImage = null;
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        BufferedImage image = displayImage;
        if (image != null) {
            graphics.drawImage(image, 0, 0, getWidth(), getHeight(), this);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        BufferedImage image = displayImage;
        return image == null
                ? new Dimension(DEFAULT_SIZE)
                : new Dimension(image.getWidth(), image.getHeight());
    }

    private SwingWorker<List<AnimationFrame>, Void> createLoadWorker() {
        return new SwingWorker<>() {
            @Override
            protected List<AnimationFrame> doInBackground() throws Exception {
                return decodeFrames(source);
            }

            @Override
            protected void done() {
                if (closed || isCancelled()) {
                    return;
                }
                try {
                    List<AnimationFrame> decodedFrames = get();
                    if (decodedFrames.isEmpty()) {
                        throw new IOException("GIF contains no decodable frames.");
                    }
                    frames = decodedFrames;
                    currentFrameIndex = 0;
                    displayImage = decodedFrames.get(0).image();
                    revalidate();
                    repaint();
                    startPlaybackIfReady();
                } catch (Exception error) {
                    loadFailure = rootCause(error);
                    LOGGER.error("Unable to decode GIF animation", loadFailure);
                    repaint();
                }
            }
        };
    }

    private void startPlaybackIfReady() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::startPlaybackIfReady);
            return;
        }
        if (closed || !playRequested || running || frames.size() <= 1 || !isDisplayable()) {
            return;
        }
        running = true;
        scheduleNextFrame();
    }

    private void scheduleNextFrame() {
        if (!running || closed || frames.isEmpty()) {
            return;
        }
        stopTimerOnly();
        int baseDelay = frames.get(currentFrameIndex).delayMs();
        int adjustedDelay = Math.max(MIN_FRAME_DELAY_MS, (int) Math.round(baseDelay / speedFactor));
        playbackTimer = new Timer(adjustedDelay, event -> advanceFrame());
        playbackTimer.setRepeats(false);
        playbackTimer.setCoalesce(true);
        playbackTimer.start();
    }

    private void advanceFrame() {
        if (!running || closed || frames.isEmpty()) {
            return;
        }
        currentFrameIndex = (currentFrameIndex + 1) % frames.size();
        displayImage = frames.get(currentFrameIndex).image();
        repaint();
        scheduleNextFrame();
    }

    private void stopPlaybackTimer() {
        running = false;
        stopTimerOnly();
    }

    private void stopTimerOnly() {
        if (playbackTimer != null) {
            playbackTimer.stop();
            playbackTimer = null;
        }
    }

    private static List<AnimationFrame> decodeFrames(GifSource source) throws IOException {
        try (ImageInputStream input = source.open()) {
            if (input == null) {
                throw new IOException("Unable to create an image input stream for the GIF source.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader is available for the supplied source.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                int frameCount = reader.getNumImages(true);
                if (frameCount <= 0) {
                    return List.of();
                }

                Dimension logicalSize = GifMetadata.logicalScreenSize(reader);
                int canvasWidth = logicalSize.width > 0 ? logicalSize.width : reader.getWidth(0);
                int canvasHeight = logicalSize.height > 0 ? logicalSize.height : reader.getHeight(0);
                BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                List<AnimationFrame> decoded = new ArrayList<>(frameCount);

                Graphics2D canvasGraphics = canvas.createGraphics();
                try {
                    for (int index = 0; index < frameCount; index++) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("GIF decoding was interrupted.");
                        }

                        FrameMetadata metadata = GifMetadata.frameMetadata(reader, index);
                        BufferedImage rawFrame = reader.read(index);
                        BufferedImage previousState = metadata.disposalMethod() == DisposalMethod.RESTORE_PREVIOUS
                                ? copy(canvas)
                                : null;

                        canvasGraphics.setComposite(AlphaComposite.SrcOver);
                        canvasGraphics.drawImage(rawFrame, metadata.x(), metadata.y(), null);
                        decoded.add(new AnimationFrame(copy(canvas), metadata.delayMs()));

                        applyDisposal(canvasGraphics, canvas, previousState, metadata);
                    }
                } finally {
                    canvasGraphics.dispose();
                }
                return List.copyOf(decoded);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void applyDisposal(
            Graphics2D graphics,
            BufferedImage canvas,
            BufferedImage previousState,
            FrameMetadata metadata
    ) {
        switch (metadata.disposalMethod()) {
            case RESTORE_BACKGROUND -> {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(metadata.x(), metadata.y(), metadata.width(), metadata.height());
                graphics.setComposite(AlphaComposite.SrcOver);
            }
            case RESTORE_PREVIOUS -> {
                if (previousState != null) {
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.drawImage(previousState, 0, 0, null);
                    graphics.setComposite(AlphaComposite.SrcOver);
                }
            }
            case NONE, KEEP -> {
                // Keep the composed canvas for the next frame.
            }
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        return new BufferedImage(
                source.getColorModel(),
                source.copyData(null),
                source.isAlphaPremultiplied(),
                null
        );
    }

    private static File validateFile(File file) throws FileNotFoundException {
        if (file == null) {
            throw new IllegalArgumentException("GIF file cannot be null.");
        }
        if (!file.isFile() || !file.canRead()) {
            throw new FileNotFoundException("GIF file is missing or unreadable: " + file.getAbsolutePath());
        }
        return file;
    }

    private static byte[] readSource(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("GIF input stream cannot be null.");
        }
        byte[] data = inputStream.readAllBytes();
        if (data.length == 0) {
            throw new IllegalArgumentException("GIF input stream is empty.");
        }
        return data;
    }

    private static GifSource fileSource(File file) {
        return () -> ImageIO.createImageInputStream(file);
    }

    private static GifSource memorySource(byte[] data) {
        byte[] immutableData = data.clone();
        return () -> ImageIO.createImageInputStream(new ByteArrayInputStream(immutableData));
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    @FunctionalInterface
    private interface GifSource {
        ImageInputStream open() throws IOException;
    }

    private record AnimationFrame(BufferedImage image, int delayMs) {
        private AnimationFrame {
            Objects.requireNonNull(image, "image");
            delayMs = Math.max(MIN_FRAME_DELAY_MS, delayMs);
        }
    }

    private enum DisposalMethod {
        NONE,
        KEEP,
        RESTORE_BACKGROUND,
        RESTORE_PREVIOUS
    }

    private record FrameMetadata(
            int delayMs,
            DisposalMethod disposalMethod,
            int x,
            int y,
            int width,
            int height
    ) {
    }

    private static final class GifMetadata {
        private GifMetadata() {
        }

        private static Dimension logicalScreenSize(ImageReader reader) throws IOException {
            IIOMetadata metadata = reader.getStreamMetadata();
            if (metadata == null || metadata.getNativeMetadataFormatName() == null) {
                return new Dimension();
            }
            Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            Node descriptor = findNode(root, "LogicalScreenDescriptor");
            if (descriptor == null) {
                return new Dimension();
            }
            NamedNodeMap attributes = descriptor.getAttributes();
            return new Dimension(
                    integerAttribute(attributes, "logicalScreenWidth", 0),
                    integerAttribute(attributes, "logicalScreenHeight", 0)
            );
        }

        private static FrameMetadata frameMetadata(ImageReader reader, int frameIndex) throws IOException {
            IIOMetadata metadata = reader.getImageMetadata(frameIndex);
            String formatName = metadata.getNativeMetadataFormatName();
            Node root = metadata.getAsTree(formatName);
            Node control = findNode(root, "GraphicControlExtension");
            Node descriptor = findNode(root, "ImageDescriptor");

            int delayMs = 100;
            DisposalMethod disposal = DisposalMethod.NONE;
            if (control != null) {
                NamedNodeMap attributes = control.getAttributes();
                delayMs = Math.max(
                        MIN_FRAME_DELAY_MS,
                        integerAttribute(attributes, "delayTime", 10) * 10
                );
                disposal = disposalMethod(stringAttribute(attributes, "disposalMethod", "none"));
            }

            int width = reader.getWidth(frameIndex);
            int height = reader.getHeight(frameIndex);
            int x = 0;
            int y = 0;
            if (descriptor != null) {
                NamedNodeMap attributes = descriptor.getAttributes();
                x = integerAttribute(attributes, "imageLeftPosition", 0);
                y = integerAttribute(attributes, "imageTopPosition", 0);
                width = integerAttribute(attributes, "imageWidth", width);
                height = integerAttribute(attributes, "imageHeight", height);
            }
            return new FrameMetadata(delayMs, disposal, x, y, width, height);
        }

        private static DisposalMethod disposalMethod(String value) {
            return switch (value) {
                case "doNotDispose" -> DisposalMethod.KEEP;
                case "restoreToBackgroundColor" -> DisposalMethod.RESTORE_BACKGROUND;
                case "restoreToPrevious" -> DisposalMethod.RESTORE_PREVIOUS;
                default -> DisposalMethod.NONE;
            };
        }

        private static Node findNode(Node parent, String name) {
            if (parent == null) {
                return null;
            }
            if (name.equals(parent.getNodeName())) {
                return parent;
            }
            NodeList children = parent.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node found = findNode(children.item(index), name);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private static int integerAttribute(NamedNodeMap attributes, String name, int fallback) {
            String value = stringAttribute(attributes, name, null);
            if (value == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static String stringAttribute(NamedNodeMap attributes, String name, String fallback) {
            if (attributes == null) {
                return fallback;
            }
            Node attribute = attributes.getNamedItem(name);
            return attribute == null ? fallback : attribute.getNodeValue();
        }
    }
}
