/*******************************************************************************
 * Copyright (c) 2015, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.snippets;

import java.io.*;

import org.apache.batik.anim.dom.*;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class Snippet367 {
	private static final String IMAGE_100 = "eclipse16.png";
	private static final String IMAGES_ROOT = "bin/org/eclipse/swt/snippets/";

	private static final String IMAGE_PATH_100 = IMAGES_ROOT + IMAGE_100;
	private static final String SVG_IMAGE_PATH = "D:\\Dev\\Eclipse\\eclipse\\jdt-master2\\git\\eclipse.platform.images\\org.eclipse.images\\eclipse-svg\\eclipse.platform.releng.tychoeclipsebuilder\\eclipse.platform.repository\\icons\\Eclipse.icns.svg";
	private static final String TEMP_PNG_IMAGE_PATH = IMAGES_ROOT + "temp_image_from_svg.png";

	public static void main(String[] args) {
		final Display display = new Display();
		final Shell shell = new Shell(display);
		shell.setText("Snippet367");
		shell.setLayout(new GridLayout(3, false));

		// Set PNG image for Label
		new Label(shell, SWT.NONE).setText(IMAGE_100 + ":");
		new Label(shell, SWT.NONE).setImage(new Image(display, IMAGE_PATH_100));

		try {
			// Convert SVG to PNG
			convertSVGToPNG(SVG_IMAGE_PATH, TEMP_PNG_IMAGE_PATH);

			// Load PNG (converted from SVG) as SWT Image for Button
			Image swtImageFromSVG = new Image(display, TEMP_PNG_IMAGE_PATH);

			new Button(shell, SWT.NONE).setImage(swtImageFromSVG);
		} catch (IOException | TranscoderException e) {
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

	// Function to convert SVG to PNG
	private static void convertSVGToPNG(String svgPath, String pngPath) throws IOException, TranscoderException {
		TranscoderInput input_svg_image = new TranscoderInput(new File(svgPath).toURI().toString());
		// Define OutputStream to PNG Image
		String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
		java.io.OutputStream png_ostream = new java.io.FileOutputStream(pngPath);
		TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);

		// Create PNGTranscoder and perform conversion
		PNGTranscoder my_converter = new PNGTranscoder();
		my_converter.transcode(input_svg_image, output_png_image);

		// Close OutputStream
		png_ostream.flush();
		png_ostream.close();
	}
}
