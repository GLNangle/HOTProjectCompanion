package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;

/** Regression checks for off-screen JOSM map capture. */
public final class MapCaptureTest {
    private MapCaptureTest() {
    }

    public static void main(String[] args) {
        AtomicInteger paintedLayers = new AtomicInteger();
        MapView mapView = new MapView() {
            private static final long serialVersionUID = 1L;

            @Override
            public void paintLayer(Layer layer, Graphics2D graphics) {
                paintedLayers.incrementAndGet();
                Rectangle clip = graphics.getClipBounds();
                if (!new Rectangle(0, 0, 320, 240).equals(clip)) {
                    throw new AssertionError("Expected a full map-view clip before rendering, got " + clip);
                }
            }

            @Override
            public void paintAll(Graphics graphics) {
                throw new AssertionError("Imagery capture must not paint or mutate the live map component");
            }
        };
        mapView.setSize(320, 240);
        Layer first = new Layer() { };
        Layer second = new Layer() { };
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            assertTrue(graphics.getClipBounds() == null,
                    "BufferedImage graphics should begin without a clip in this regression test");
            BuildingCheckPanel.renderVisibleImagery(mapView, graphics, Arrays.asList(first, second));
            assertEquals(2, paintedLayers.get(), "capture should paint each supplied imagery layer directly");
            assertEquals(new Rectangle(0, 0, 320, 240), graphics.getClipBounds(),
                    "capture should install a full-view clip");
        } finally {
            graphics.dispose();
        }
        System.out.println("MapCaptureTest: all tests passed");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
