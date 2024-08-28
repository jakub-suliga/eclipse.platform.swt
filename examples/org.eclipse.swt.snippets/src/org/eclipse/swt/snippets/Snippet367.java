package org.eclipse.swt.snippets;

import java.io.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.apache.batik.anim.dom.*;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.*;
import org.apache.batik.util.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.w3c.dom.*;
import org.w3c.dom.svg.*;

public class Snippet367 {
    private static final String IMAGE_100 = "eclipse16.png";
    private static final String SVG_IMAGE = "input.svg";
    private static final String IMAGES_ROOT = "D:\\Dev\\Test\\";

    private static final String IMAGE_PATH_100 = IMAGES_ROOT + IMAGE_100;
    private static final String SVG_IMAGE_PATH = IMAGES_ROOT + SVG_IMAGE;
    private static final String TEMP_SVG_IMAGE_PATH = IMAGES_ROOT + "resized_input.svg";
    private static final String TEMP_PNG_IMAGE_PATH = IMAGES_ROOT + "temp_image_from_svg.png";

    public static void main(String[] args) {
        final Display display = new Display();
        final Shell shell = new Shell(display);
        shell.setText("Snippet367");
        shell.setLayout(new GridLayout(3, false));

        new Label(shell, SWT.NONE).setText(IMAGE_100 + ":");
        new Label(shell, SWT.NONE).setImage(new Image(display, IMAGE_PATH_100));

        try {
            resizeSVG(SVG_IMAGE_PATH, TEMP_SVG_IMAGE_PATH, 32, 32);
            convertSVGToPNG(TEMP_SVG_IMAGE_PATH, TEMP_PNG_IMAGE_PATH);

            Image swtImageFromSVG = new Image(display, TEMP_PNG_IMAGE_PATH);
            new Label(shell, SWT.NONE);
            new Label(shell, SWT.NONE).setText("SVG:");
            new Label(shell, SWT.NONE).setImage(swtImageFromSVG);
        } catch (Exception e) {
            e.printStackTrace();
        }

        shell.pack();
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        display.dispose();
    }

    private static void resizeSVG(String inputSvgPath, String outputSvgPath, int width, int height) throws IOException, TransformerException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        SVGDocument svgDoc = factory.createSVGDocument(new File(inputSvgPath).toURI().toString());

        Element svgRoot = svgDoc.getDocumentElement();


        NodeList rectElements = svgRoot.getElementsByTagName("rect");
        for (int i = 0; i < rectElements.getLength(); i++) {
            Element rectElement = (Element) rectElements.item(i);
            if ("#ffffff".equals(rectElement.getAttribute("fill"))) {
                svgRoot.removeChild(rectElement);
            }
        }

        svgRoot.setAttribute("width", String.valueOf(width));
        svgRoot.setAttribute("height", String.valueOf(height));

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        Source source = new DOMSource(svgDoc);
        Result result = new StreamResult(new File(outputSvgPath));
        transformer.transform(source, result);
    }

    private static void convertSVGToPNG(String svgPath, String pngPath) throws IOException, TranscoderException {
        TranscoderInput input_svg_image = new TranscoderInput(new File(svgPath).toURI().toString());
        OutputStream png_ostream = new FileOutputStream(pngPath);
        TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);

        PNGTranscoder my_converter = new PNGTranscoder();


        my_converter.addTranscodingHint(PNGTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 0.084666f); // 300dpi
        my_converter.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 32f);
        my_converter.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 32f);
        my_converter.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, new java.awt.Color(0, 0, 0, 0));

        my_converter.transcode(input_svg_image, output_png_image);

        png_ostream.flush();
        png_ostream.close();
    }
}

