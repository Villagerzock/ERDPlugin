package net.villagerzock.erdplugin.ui;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class SvgExport {
    private SvgExport() {}

    public static void exportSvg(GraphRenderer renderer, Rectangle2D worldBounds, File file, int multiplier) throws IOException {
        DOMImplementation impl = GenericDOMImplementation.getDOMImplementation();
        String svgNS = "http://www.w3.org/2000/svg";
        Document doc = impl.createDocument(svgNS, "svg", null);

        SVGGraphics2D svg = new SVGGraphics2D(doc);
        svg.setSVGCanvasSize(new java.awt.Dimension((int) (worldBounds.getWidth() * multiplier), (int) (worldBounds.getHeight() * multiplier)));
        svg.setClip(0,0, (int) worldBounds.getWidth() * multiplier, (int) worldBounds.getHeight() * multiplier);
        svg.scale(multiplier,multiplier);
        svg.translate(-worldBounds.getX(),-worldBounds.getY());

        renderer.paintGraph(svg, worldBounds,false);

        try (FileWriter out = new FileWriter(file)) {
            svg.stream(out, true); // useCSS=true
        }
    }
}
