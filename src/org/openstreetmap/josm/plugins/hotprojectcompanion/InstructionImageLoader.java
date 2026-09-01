package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;

/** Downloads and scales instruction images with conservative resource limits. */
final class InstructionImageLoader {
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final long MAX_PIXELS = 24_000_000L;
    private static final int MAX_DIMENSION = 10_000;
    private static final int THUMBNAIL_WIDTH = 340;
    private static final int THUMBNAIL_HEIGHT = 220;
    private static final int ANALYSIS_MAX_DIMENSION = 720;
    private static final int CACHE_SIZE = 8;
    private static final Map<String, BufferedImage> IMAGE_CACHE = new LinkedHashMap<String, BufferedImage>(
            CACHE_SIZE, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private InstructionImageLoader() {
    }

    static ImageIcon loadThumbnail(InstructionImage reference) throws IOException {
        return new ImageIcon(scale(loadImage(reference), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
    }

    static BufferedImage loadForAnalysis(InstructionImage reference) throws IOException {
        return scale(loadImage(reference), ANALYSIS_MAX_DIMENSION, ANALYSIS_MAX_DIMENSION);
    }

    private static BufferedImage loadImage(InstructionImage reference) throws IOException {
        if (!isSupportedUrl(reference.getUrl())) {
            throw new IOException("Unsupported or local image address");
        }
        synchronized (IMAGE_CACHE) {
            BufferedImage cached = IMAGE_CACHE.get(reference.getUrl());
            if (cached != null) {
                return cached;
            }
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(reference.getUrl()).openConnection();
        connection.setConnectTimeout(6_000);
        connection.setReadTimeout(12_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "image/*");
        connection.setRequestProperty("User-Agent", "JOSM HOT Project Companion/0.5.0");
        try {
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                throw new IOException("Image server returned HTTP " + response);
            }
            if (!isSupportedUrl(connection.getURL().toString())) {
                throw new IOException("Image redirected to an unsupported address");
            }
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IOException("The linked file is not an image");
            }
            int length = connection.getContentLength();
            if (length > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Image is larger than 8 MB");
            }
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readLimited(input);
            }
            BufferedImage image = decodeLimited(bytes);
            synchronized (IMAGE_CACHE) {
                IMAGE_CACHE.put(reference.getUrl(), image);
            }
            return image;
        } finally {
            connection.disconnect();
        }
    }

    static boolean isSupportedUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getHost() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return !("localhost".equals(host) || host.endsWith(".localhost")
                    || host.equals("0.0.0.0") || host.equals("::1") || host.startsWith("127.")
                    || host.startsWith("10.") || host.startsWith("192.168.")
                    || host.startsWith("169.254.") || isPrivate172(host)
                    || (host.indexOf(':') >= 0 && (host.startsWith("fc")
                    || host.startsWith("fd") || host.startsWith("fe80:"))));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean isPrivate172(String host) {
        if (!host.startsWith("172.")) {
            return false;
        }
        int nextDot = host.indexOf('.', 4);
        if (nextDot < 0) {
            return false;
        }
        try {
            int secondOctet = Integer.parseInt(host.substring(4, nextDot));
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Image is larger than 8 MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static BufferedImage decodeLimited(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("Image could not be decoded");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image dimensions are too large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("Image could not be decoded");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage source, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min(
                maxWidth / (double) source.getWidth(),
                maxHeight / (double) source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        if (width == source.getWidth() && height == source.getHeight()) {
            return source;
        }
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return thumbnail;
    }
}
