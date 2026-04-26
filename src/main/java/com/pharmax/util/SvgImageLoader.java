package com.pharmax.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.InputStream;

public class SvgImageLoader {

    private static final Logger logger = LoggerFactory.getLogger(SvgImageLoader.class);

    /**
     * Loads an SVG file from the classpath and returns it as a JavaFX Image.
     * 
     * @param resourcePath The path to the SVG resource (e.g., "/icons/my-icon.svg")
     * @param width        The desired width. If <= 0, the SVG's width is used.
     * @param height       The desired height. If <= 0, the SVG's height is used.
     * @return The JavaFX Image, or null if loading fails.
     */
    public static Image loadSvgImage(String resourcePath, float width, float height) {
        try (InputStream inputStream = SvgImageLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                logger.error("SVG resource not found: " + resourcePath);
                return null;
            }

            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();

            // Set dimensions if provided
            if (width > 0) {
                transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, width);
            }
            if (height > 0) {
                transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, height);
            }

            TranscoderInput input = new TranscoderInput(inputStream);
            transcoder.transcode(input, null);

            BufferedImage bufferedImage = transcoder.getBufferedImage();
            if (bufferedImage != null) {
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }

        } catch (Exception e) {
            logger.error("Failed to load SVG: " + resourcePath, e);
        }
        return null;
    }

    /**
     * Loads an SVG file from the classpath and returns it as a JavaFX Image using
     * default size.
     */
    public static Image loadSvgImage(String resourcePath) {
        return loadSvgImage(resourcePath, -1, -1);
    }

    // Internal Transcoder helper
    private static class BufferedImageTranscoder extends ImageTranscoder {
        private BufferedImage image = null;

        @Override
        public BufferedImage createImage(int w, int h) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) throws TranscoderException {
            this.image = img;
        }

        public BufferedImage getBufferedImage() {
            return image;
        }
    }
}
