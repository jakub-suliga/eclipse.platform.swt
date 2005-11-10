/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.custom;


import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.accessibility.*;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.printing.*;
import org.eclipse.swt.widgets.*;

/**
 * A StyledText is an editable user interface object that displays lines 
 * of text.  The following style attributes can be defined for the text: 
 * <ul>
 * <li>foreground color 
 * <li>background color
 * <li>font style (bold, italic, bold-italic, regular)
 * <li>underline
 * <li>strikeout
 * </ul>
 * <p>
 * In addition to text style attributes, the background color of a line may 
 * be specified.
 * </p>
 * <p>
 * There are two ways to use this widget when specifying text style information.  
 * You may use the API that is defined for StyledText or you may define your own 
 * LineStyleListener.  If you define your own listener, you will be responsible 
 * for maintaining the text style information for the widget.  IMPORTANT: You may 
 * not define your own listener and use the StyledText API.  The following
 * StyledText API is not supported if you have defined a LineStyleListener:
 * <ul>
 * <li>getStyleRangeAtOffset(int)
 * <li>getStyleRanges()
 * <li>replaceStyleRanges(int,int,StyleRange[])
 * <li>setStyleRange(StyleRange)
 * <li>setStyleRanges(StyleRange[])
 * </ul>
 * </p>
 * <p>
 * There are two ways to use this widget when specifying line background colors.
 * You may use the API that is defined for StyledText or you may define your own 
 * LineBackgroundListener.  If you define your own listener, you will be responsible 
 * for maintaining the line background color information for the widget.  
 * IMPORTANT: You may not define your own listener and use the StyledText API.  
 * The following StyledText API is not supported if you have defined a 
 * LineBackgroundListener:
 * <ul>
 * <li>getLineBackground(int)
 * <li>setLineBackground(int,int,Color)
 * </ul>
 * </p>
 * <p>
 * The content implementation for this widget may also be user-defined.  To do so,
 * you must implement the StyledTextContent interface and use the StyledText API
 * setContent(StyledTextContent) to initialize the widget. 
 * </p>
 * <p>
 * IMPORTANT: This class is <em>not</em> intended to be subclassed.
 * </p>
 * <dl>
 * <dt><b>Styles:</b><dd>FULL_SELECTION, MULTI, READ_ONLY, SINGLE, WRAP
 * <dt><b>Events:</b><dd>ExtendedModify, LineGetBackground, LineGetSegments, LineGetStyle, Modify, Selection, Verify, VerifyKey
 * </dl>
 */
public class StyledText extends Canvas {
	static final char TAB = '\t';
	static final String PlatformLineDelimiter = System.getProperty("line.separator");
	static final int BIDI_CARET_WIDTH = 3;
	static final int DEFAULT_WIDTH	= 64;
	static final int DEFAULT_HEIGHT = 64;
	static final int V_SCROLL_RATE = 50;
	static final int H_SCROLL_RATE = 10;
	
	static final int ExtendedModify = 3000;
	static final int LineGetBackground = 3001;
	static final int LineGetStyle = 3002;
	static final int TextChanging = 3003;
	static final int TextSet = 3004;
	static final int VerifyKey = 3005;
	static final int TextChanged = 3006;
	static final int LineGetSegments = 3007;
	static final int PaintObject = 3008;
	
	Color selectionBackground;	// selection background color
	Color selectionForeground;	// selection foreground color
	StyledTextContent content;			// native content (default or user specified)
	StyledTextRenderer renderer;
	Listener listener;
	TextChangeListener textChangeListener;	// listener for TextChanging, TextChanged and TextSet events from StyledTextContent
	int verticalScrollOffset = 0;		// pixel based
	int horizontalScrollOffset = 0;		// pixel based
	int topIndex = 0;					// top visible line
	int topOffset = 0;					// offset of first character in top line
	int clientAreaHeight = 0;			// the client area height. Needed to calculate content width for new visible lines during Resize callback
	int clientAreaWidth = 0;			// the client area width. Needed during Resize callback to determine if line wrap needs to be recalculated
	int tabLength = 4;					// number of characters in a tab
	int leftMargin;
	int topMargin;
	int rightMargin;
	int bottomMargin;
	int columnX;						// keep track of the horizontal caret position when changing lines/pages. Fixes bug 5935
	int caretOffset = 0;
	Point selection = new Point(0, 0);	// x and y are start and end caret offsets of selection
	Point clipboardSelection;           // x and y are start and end caret offsets of previous selection
	int selectionAnchor;				// position of selection anchor. 0 based offset from beginning of text
	Point doubleClickSelection;			// selection after last mouse double click
	boolean editable = true;
	boolean wordWrap = false;
	boolean doubleClickEnabled = true;	// see getDoubleClickEnabled 
	boolean overwrite = false;			// insert/overwrite edit mode
	int textLimit = -1;					// limits the number of characters the user can type in the widget. Unlimited by default.
	Hashtable keyActionMap = new Hashtable();
	Color background = null;			// workaround for bug 4791
	Color foreground = null;			//
	Clipboard clipboard;
	boolean mouseDown = false;
	boolean mouseDoubleClick = false;	// true=a double click ocurred. Don't do mouse swipe selection.
	int autoScrollDirection = SWT.NULL;	// the direction of autoscrolling (up, down, right, left)
	int autoScrollDistance = 0;
	int lastTextChangeStart;			// cache data of the 
	int lastTextChangeNewLineCount;		// last text changing 
	int lastTextChangeNewCharCount;		// event for use in the 
	int lastTextChangeReplaceLineCount;	// text changed handler
	int lastTextChangeReplaceCharCount;
	int lastLineBottom;					// the bottom pixel of the last line been replaced
	boolean isMirrored;
	boolean bidiColoring = false;		// apply the BIDI algorithm on text segments of the same color
	Image leftCaretBitmap = null;
	Image rightCaretBitmap = null;
	int caretDirection = SWT.NULL;
	boolean advancing = true;
	Caret defaultCaret = null;
	boolean updateCaretDirection = true;
	int partialHeight;						// amount, in pixels, of the partial top index that is visible
	boolean fixedLineHeight;
	
	int alignment;
	boolean justify;
	int indent;
	int lineSpacing;

	final static boolean IS_CARBON, IS_GTK, IS_MOTIF;
	static {
		String platform = SWT.getPlatform();
		IS_CARBON = "carbon".equals(platform);
		IS_GTK = "gtk".equals(platform);
		IS_MOTIF = "motif".equals(platform);
	}

	/**
	 * The Printing class implements printing of a range of text.
	 * An instance of <class>Printing </class> is returned in the 
	 * StyledText#print(Printer) API. The run() method may be 
	 * invoked from any thread.
	 */
	static class Printing implements Runnable {
		final static int LEFT = 0;						// left aligned header/footer segment
		final static int CENTER = 1;					// centered header/footer segment
		final static int RIGHT = 2;						// right aligned header/footer segment

		Printer printer;
		StyledTextRenderer printerRenderer;
		StyledTextPrintOptions printOptions;
		Rectangle clientArea;
		FontData fontData;
		Font printerFont;
		Hashtable resources;
		int tabLength;
		GC gc;											// printer GC
		int pageWidth;									// width of a printer page in pixels
		int startPage;									// first page to print
		int endPage;									// last page to print
		int startLine;									// first (wrapped) line to print
		int endLine;									// last (wrapped) line to print
		boolean singleLine;								// widget single line mode
		Point selection = null;					// selected text
		boolean mirrored;						// indicates the printing gc should be mirrored
		int lineSpacing;

	/**
	 * Creates an instance of <class>Printing</class>.
	 * Copies the widget content and rendering data that needs 
	 * to be requested from listeners.
	 * </p>
	 * @param parent StyledText widget to print.
	 * @param printer printer device to print on.
	 * @param printOptions print options
	 */		
	Printing(StyledText styledText, Printer printer, StyledTextPrintOptions printOptions) {
		this.printer = printer;
		this.printOptions = printOptions;
		this.mirrored = (styledText.getStyle() & SWT.MIRRORED) != 0;
		singleLine = styledText.isSingleLine();
		startPage = 1;
		endPage = Integer.MAX_VALUE;
		PrinterData data = printer.getPrinterData();
		if (data.scope == PrinterData.PAGE_RANGE) {
			startPage = data.startPage;
			endPage = data.endPage;
			if (endPage < startPage) {
				int temp = endPage;
				endPage = startPage;
				startPage = temp;
			}
		} else if (data.scope == PrinterData.SELECTION) {
			selection = styledText.getSelectionRange();
		}
		printerRenderer = new StyledTextRenderer(printer, null);
		printerRenderer.setContent(copyContent(styledText.getContent()));
		cacheLineData(styledText);

	}
	/**
	 * Caches all line data that needs to be requested from a listener.
	 * </p>
	 * @param printerContent <class>StyledTextContent</class> to request 
	 * 	line data for.
	 */
	void cacheLineData(StyledText styledText) {
		StyledTextRenderer renderer = styledText.renderer;
		renderer.copyInto(printerRenderer);
		fontData = styledText.getFont().getFontData()[0];
		tabLength = styledText.tabLength;
		int lineCount = printerRenderer.lineCount;
		if (styledText.isListening(LineGetBackground) || (styledText.isBidi() && styledText.isListening(LineGetSegments)) || styledText.isListening(LineGetStyle)) {
			StyledTextContent content = printerRenderer.content;
			for (int i = 0; i < lineCount; i++) {
				String line = content.getLine(i);
				int lineOffset = content.getOffsetAtLine(i);
				StyledTextEvent event = styledText.getLineBackgroundData(lineOffset, line);
				if (event != null && event.lineBackground != null) {
					printerRenderer.setLineBackground(i, 1, event.lineBackground);
				}
				if (styledText.isBidi()) {
					int[] segments = styledText.getBidiSegments(lineOffset, line);
					printerRenderer.setLineSegments(i, 1, segments);
				}			
				event = styledText.getLineStyleData(lineOffset, line);
				if (event != null) {
					printerRenderer.setLineIndent(i, 1, event.indent);
					printerRenderer.setLineAlignment(i, 1, event.alignment);
					printerRenderer.setLineJustify(i, 1, event.justify);
					printerRenderer.setLineBullet(i, 1, event.bullet);
					printerRenderer.setStyleRanges(event.ranges, event.styles);
				}
			}
		}
		Point screenDPI = styledText.getDisplay().getDPI();
		Point printerDPI = printer.getDPI();
		resources = new Hashtable ();
		for (int i = 0; i < lineCount; i++) {
			Color color = printerRenderer.getLineBackground(i, null);
			if (color != null) {
				if (printOptions.printLineBackground) {
					Color printerColor = (Color)resources.get(color);
					if (printerColor == null) {
						printerColor = new Color (printer, color.getRGB());
						resources.put(color, printerColor); 
					}				
					printerRenderer.setLineBackground(i, 1, printerColor);
				} else {
					printerRenderer.setLineBackground(i, 1, null);
				}
			}
			int indent = printerRenderer.getLineIndent(i, 0);
			if (indent != 0) {
				printerRenderer.setLineIndent(i, 1, indent * printerDPI.x / screenDPI.x);
			}
		}
		StyleRange[] styles = printerRenderer.styles;
		for (int i = 0; i < printerRenderer.styleCount; i++) {
			StyleRange style = styles[i];
			Font font = style.font;
			if (style.font != null) {
				Font printerFont = (Font)resources.get(font);
				if (printerFont == null) {
					printerFont = new Font (printer, font.getFontData());
					resources.put(font, printerFont);					
				}
				style.font = printerFont;
			}
			Color color = style.foreground;
			if (color != null) {
				Color printerColor = (Color)resources.get(color);
				if (printOptions.printTextForeground) {
					if (printerColor == null) {
						printerColor = new Color (printer, color.getRGB());
						resources.put(color, printerColor); 
					}
					style.foreground = printerColor;
				} else {
					style.foreground = null;
				}
			}
			color = style.background;
			if (color != null) {
				Color printerColor = (Color)resources.get(color);
				if (printOptions.printTextBackground) {
					if (printerColor == null) {
						printerColor = new Color (printer, color.getRGB());
						resources.put(color, printerColor); 
					}
					style.background = printerColor;
				} else {
					style.background = null;
				}
			}
			if (!printOptions.printTextFontStyle) {
				style.fontStyle = SWT.NORMAL;
			}
			style.rise = style.rise * printerDPI.y / screenDPI.y;
			GlyphMetrics metrics = style.metrics;
			if (metrics != null) {
				metrics.ascent = metrics.ascent * printerDPI.y / screenDPI.y;
				metrics.descent = metrics.descent * printerDPI.y / screenDPI.y;
				metrics.width = metrics.width * printerDPI.x / screenDPI.x;
			}
		}
		lineSpacing = styledText.lineSpacing * printerDPI.y / screenDPI.y;
	}
	/**
	 * Copies the text of the specified <class>StyledTextContent</class>.
	 * </p>
	 * @param original the <class>StyledTextContent</class> to copy.
	 */
	StyledTextContent copyContent(StyledTextContent original) {
		StyledTextContent printerContent = new DefaultContent();
		int insertOffset = 0;
		for (int i = 0; i < original.getLineCount(); i++) {
			int insertEndOffset;
			if (i < original.getLineCount() - 1) {
				insertEndOffset = original.getOffsetAtLine(i + 1);
			} else {
				insertEndOffset = original.getCharCount();
			}
			printerContent.replaceTextRange(insertOffset, 0, original.getTextRange(insertOffset, insertEndOffset - insertOffset));
			insertOffset = insertEndOffset;
		}
		return printerContent;
	}
	/**
	 * Disposes of the resources and the <class>PrintRenderer</class>.
	 */
	void dispose() {
		if (gc != null) {
			gc.dispose();
			gc = null;
		}
		if (resources != null) {
			Enumeration enumeration = resources.elements();			
			while (enumeration.hasMoreElements()) {
				Resource resource = (Resource) enumeration.nextElement();
				resource.dispose();
			}
			resources = null;
		}
		if (printerFont != null) {
			printerFont.dispose();
			printerFont = null;
		}
		if (printerRenderer != null) {
			printerRenderer.dispose();
			printerRenderer = null;
		}
	}
	void init() {
		Rectangle trim = printer.computeTrim(0, 0, 0, 0);
		Point dpi = printer.getDPI();
		
		printerFont = new Font(printer, fontData.getName(), fontData.getHeight(), SWT.NORMAL);
		clientArea = printer.getClientArea();
		pageWidth = clientArea.width;
		// one inch margin around text
		clientArea.x = dpi.x + trim.x; 				
		clientArea.y = dpi.y + trim.y;
		clientArea.width -= (clientArea.x + trim.width);
		clientArea.height -= (clientArea.y + trim.height); 

		int style = mirrored ? SWT.RIGHT_TO_LEFT : SWT.LEFT_TO_RIGHT;
		gc = new GC(printer, style);
		gc.setFont(printerFont);
		printerRenderer.setFont(printerFont, tabLength);
		int lineHeight = printerRenderer.getLineHeight();
		if (printOptions.header != null) {
			clientArea.y += lineHeight * 2;
			clientArea.height -= lineHeight * 2;
		}
		if (printOptions.footer != null) {
			clientArea.height -= lineHeight * 2;
		}
		
		// TODO not wrapped
		StyledTextContent content = printerRenderer.content;
		startLine = 0;
		endLine = singleLine ? 0 : content.getLineCount() - 1;
		PrinterData data = printer.getPrinterData();
		if (data.scope == PrinterData.PAGE_RANGE) {
			int pageSize = clientArea.height / lineHeight;//WRONG
			startLine = (startPage - 1) * pageSize;
		} else if (data.scope == PrinterData.SELECTION) {
			startLine = content.getLineAtOffset(selection.x);
			if (selection.y > 0) {
				endLine = content.getLineAtOffset(selection.x + selection.y - 1);
			} else {
				endLine = startLine - 1;
			}
		}
	}
	/**
	 * Prints the lines in the specified page range.
	 */
	void print() {
		Color background = gc.getBackground();
		Color foreground = gc.getForeground();
		int paintY = clientArea.y;
		int paintX = clientArea.x;
		int page = startPage;
		int pageBottom = clientArea.y + clientArea.height;
		int orientation =  gc.getStyle() & (SWT.RIGHT_TO_LEFT | SWT.LEFT_TO_RIGHT);
		
		
		for (int i = startLine; i <= endLine && page <= endPage; i++) {
			if (paintY == clientArea.y) {
				printer.startPage();
				printDecoration(page, true);
			}
			TextLayout layout = printerRenderer.getTextLayout(i, orientation, clientArea.width, lineSpacing);
			Color lineBackground = printerRenderer.getLineBackground(i, background);
			int paragraphBottom = paintY + layout.getBounds().height; 
			if (paragraphBottom <= pageBottom) {
				//normal case, the whole paragraph fits in the current page
				printLine(paintX, paintY, gc, foreground, lineBackground, layout);
				paintY = paragraphBottom;
			} else {
				int lineCount = layout.getLineCount();
				while (paragraphBottom > pageBottom && lineCount > 0) {
					lineCount--;
					paragraphBottom -= layout.getLineBounds(lineCount).height + layout.getSpacing();
				}
				if (lineCount == 0) {
					//the whole paragraph goes to the next page
					printDecoration(page, false);
					printer.endPage();
					page++;
					if (page <= endPage) {
						printer.startPage();
						printDecoration(page, true);
						paintY = clientArea.y;
						printLine(paintX, paintY, gc, foreground, lineBackground, layout);
						paintY += layout.getBounds().height;
					}
				} else {
					//draw paragraph top in the current page and paragraph bottom in the next
					gc.setClipping(paintX, paintY, clientArea.width, paragraphBottom - paintY);
					printLine(paintX, paintY, gc, foreground, lineBackground, layout);
					printDecoration(page, false);
					printer.endPage();					
					page++;
					if (page <= endPage) {
						printer.startPage();
						printDecoration(page, true);
						paintY = clientArea.y;
						int height = layout.getBounds().height - paragraphBottom;
						gc.setClipping(paintX, paintY, clientArea.width, height);
						printLine(paintX, paintY, gc, foreground, lineBackground, layout);
						paintY += height;
					}
					gc.setClipping((Rectangle)null);
				}
			}
			printerRenderer.disposeTextLayout(layout);
		}
		if (paintY > clientArea.y) {
			// close partial page
			printDecoration(page, false);
			printer.endPage();
		}
	}
	/**
	 * Print header or footer decorations.
	 * 
	 * @param page page number to print, if specified in the StyledTextPrintOptions header or footer.
	 * @param header true = print the header, false = print the footer
	 */
	void printDecoration(int page, boolean header) {
		String text = header ? printOptions.header : printOptions.footer;
		if (text == null) return;
		int lastSegmentIndex = 0;
		int segmentCount = 3;
		for (int i = 0; i < segmentCount; i++) {
			int segmentIndex = text.indexOf(StyledTextPrintOptions.SEPARATOR, lastSegmentIndex);
			String segment;
			if (segmentIndex == -1) {
				segment = text.substring(lastSegmentIndex);
				printDecorationSegment(segment, i, page, header);
				break;
			} else {
				segment = text.substring(lastSegmentIndex, segmentIndex);
				printDecorationSegment(segment, i, page, header);
				lastSegmentIndex = segmentIndex + StyledTextPrintOptions.SEPARATOR.length();
			}
		}
	}
	/**
	 * Print one segment of a header or footer decoration.
	 * Headers and footers have three different segments.
	 * One each for left aligned, centered, and right aligned text.
	 * 
	 * @param segment decoration segment to print
	 * @param alignment alignment of the segment. 0=left, 1=center, 2=right 
	 * @param page page number to print, if specified in the decoration segment.
	 * @param header true = print the header, false = print the footer
	 */
	void printDecorationSegment(String segment, int alignment, int page, boolean header) {		
		int pageIndex = segment.indexOf(StyledTextPrintOptions.PAGE_TAG);
		if (pageIndex != -1) {
			int pageTagLength = StyledTextPrintOptions.PAGE_TAG.length();
			StringBuffer buffer = new StringBuffer(segment.substring (0, pageIndex));
			buffer.append (page);
			buffer.append (segment.substring(pageIndex + pageTagLength));
			segment = buffer.toString();
		}
		if (segment.length() > 0) {
			TextLayout layout = new TextLayout(printer);
			layout.setText(segment);
			layout.setFont(printerFont);
			int segmentWidth = layout.getBounds().width;
			int segmentHeight = printerRenderer.getLineHeight();
			int drawX = 0, drawY;
			if (alignment == LEFT) {
				drawX = clientArea.x;
			} else if (alignment == CENTER) {
				drawX = (pageWidth - segmentWidth) / 2;
			} else if (alignment == RIGHT) {
				drawX = clientArea.x + clientArea.width - segmentWidth;
			}
			if (header) {
				drawY = clientArea.y - segmentHeight * 2;
			} else {
				drawY = clientArea.y + clientArea.height + segmentHeight;
			}
			layout.draw(gc, drawX, drawY);
			layout.dispose();
		}
	}
	void printLine(int paintX, int paintY, GC gc, Color foreground, Color background, TextLayout layout) {
		if (background != null) {
			Rectangle rect = layout.getBounds();
			gc.setBackground(background);
			gc.fillRectangle(paintX, paintY, rect.width, rect.height);
			
//			int lineCount = layout.getLineCount();
//			for (int i = 0; i < lineCount; i++) {
//				Rectangle rect = layout.getLineBounds(i);
//				rect.x += paintX;
//				rect.y += paintY + layout.getSpacing();
//				rect.width = width;//layout bounds
//				gc.fillRectangle(rect);
//			}
		}
		gc.setForeground(foreground);
		layout.draw(gc, paintX, paintY);
	}
	/**
	 * Starts a print job and prints the pages specified in the constructor.
	 */
	public void run() {
		String jobName = printOptions.jobName;
		if (jobName == null) {
			jobName = "Printing";
		}
		if (printer.startJob(jobName)) {
			init();
			print();
			dispose();
			printer.endJob();
		}
	}	
	}
	/**
	 * The <code>RTFWriter</code> class is used to write widget content as
	 * rich text. The implementation complies with the RTF specification 
	 * version 1.5.
	 * <p>
	 * toString() is guaranteed to return a valid RTF string only after 
	 * close() has been called. 
	 * </p>
	 * <p>
	 * Whole and partial lines and line breaks can be written. Lines will be
	 * formatted using the styles queried from the LineStyleListener, if 
	 * set, or those set directly in the widget. All styles are applied to
	 * the RTF stream like they are rendered by the widget. In addition, the 
	 * widget font name and size is used for the whole text.
	 * </p>
	 */
	class RTFWriter extends TextWriter {
		static final int DEFAULT_FOREGROUND = 0;
		static final int DEFAULT_BACKGROUND = 1;
		Vector colorTable, fontTable;
		boolean WriteUnicode;
		
	/**
	 * Creates a RTF writer that writes content starting at offset "start"
	 * in the document.  <code>start</code> and <code>length</code>can be set to specify partial 
	 * lines.
	 * <p>
	 *
	 * @param start start offset of content to write, 0 based from 
	 * 	beginning of document
	 * @param length length of content to write
	 */
	public RTFWriter(int start, int length) {
		super(start, length);
		colorTable = new Vector();
		fontTable = new Vector();
		colorTable.addElement(getForeground());
		colorTable.addElement(getBackground());
		fontTable.addElement(getFont());
		setUnicode();
	}
	/**
	 * Closes the RTF writer. Once closed no more content can be written.
	 * <b>NOTE:</b>  <code>toString()</code> does not return a valid RTF string until 
	 * <code>close()</code> has been called.
	 */
	public void close() {
		if (!isClosed()) {
			writeHeader();
			write("\n}}\0");
			super.close();
		}
	}	
	/**
	 * Returns the index of the specified color in the RTF color table.
	 * <p>
	 *
	 * @param color the color
	 * @param defaultIndex return value if color is null
	 * @return the index of the specified color in the RTF color table
	 * 	or "defaultIndex" if "color" is null.
	 */
	int getColorIndex(Color color, int defaultIndex) {
		if (color == null) return defaultIndex;
		int index = colorTable.indexOf(color);
		if (index == -1) {
			index = colorTable.size();
			colorTable.addElement(color);
		}
		return index;
	}
	/**
	 * Returns the index of the specified color in the RTF color table.
	 * <p>
	 *
	 * @param color the color
	 * @param defaultIndex return value if color is null
	 * @return the index of the specified color in the RTF color table
	 * 	or "defaultIndex" if "color" is null.
	 */
	int getFontIndex(Font font) {
		int index = fontTable.indexOf(font);
		if (index == -1) {
			index = fontTable.size();
			fontTable.addElement(font);
		}
		return index;
	}
	/**
	 * Determines if Unicode RTF should be written.
	 * Don't write Unicode RTF on Windows 95/98/ME or NT.
	 */
	void setUnicode() {
		final String Win95 = "windows 95";
		final String Win98 = "windows 98";
		final String WinME = "windows me";		
		final String WinNT = "windows nt";
		String osName = System.getProperty("os.name").toLowerCase();
		String osVersion = System.getProperty("os.version");
		int majorVersion = 0;
		
		if (osName.startsWith(WinNT) && osVersion != null) {
			int majorIndex = osVersion.indexOf('.');
			if (majorIndex != -1) {
				osVersion = osVersion.substring(0, majorIndex);
				try {
					majorVersion = Integer.parseInt(osVersion);
				} catch (NumberFormatException exception) {
					// ignore exception. version number remains unknown.
					// will write without Unicode
				}
			}
		}
		WriteUnicode =	!osName.startsWith(Win95) &&
						!osName.startsWith(Win98) &&
						!osName.startsWith(WinME) &&
						(!osName.startsWith(WinNT) || majorVersion > 4);
	}
	/**
	 * Appends the specified segment of "string" to the RTF data.
	 * Copy from <code>start</code> up to, but excluding, <code>end</code>.
	 * <p>
	 *
	 * @param string string to copy a segment from. Must not contain
	 * 	line breaks. Line breaks should be written using writeLineDelimiter()
	 * @param start start offset of segment. 0 based.
	 * @param end end offset of segment
	 */
	void write(String string, int start, int end) {
		for (int index = start; index < end; index++) {
			char ch = string.charAt(index);
			if (ch > 0xFF && WriteUnicode) {
				// write the sub string from the last escaped character 
				// to the current one. Fixes bug 21698.
				if (index > start) {
					write(string.substring(start, index));
				}
				write("\\u");
				write(Integer.toString((short) ch));
				write(' ');						// control word delimiter
				start = index + 1;
			} else if (ch == '}' || ch == '{' || ch == '\\') {
				// write the sub string from the last escaped character 
				// to the current one. Fixes bug 21698.
				if (index > start) {
					write(string.substring(start, index));
				}
				write('\\');
				write(ch);
				start = index + 1;
			}
		}
		// write from the last escaped character to the end.
		// Fixes bug 21698.
		if (start < end) {
			write(string.substring(start, end));
		}
	}
	/**
	 * Writes the RTF header including font table and color table.
	 */
	void writeHeader() {
		StringBuffer header = new StringBuffer();
		FontData fontData = getFont().getFontData()[0];
		header.append("{\\rtf1\\ansi");
		// specify code page, necessary for copy to work in bidi 
		// systems that don't support Unicode RTF.
		String cpg = System.getProperty("file.encoding").toLowerCase();
		if (cpg.startsWith("cp") || cpg.startsWith("ms")) {
			cpg = cpg.substring(2, cpg.length());
			header.append("\\ansicpg");
			header.append(cpg);
		}
		header.append("\\uc0\\deff0{\\fonttbl{\\f0\\fnil ");
		header.append(fontData.getName());
		header.append(";");
		for (int i = 1; i < fontTable.size(); i++) {
			header.append("\\f");
			header.append(i);
			header.append(" ");
			FontData fd = ((Font)fontTable.elementAt(i)).getFontData()[0];
			header.append(fd.getName());
			header.append(";");			
		}
		header.append("}}\n{\\colortbl");
		for (int i = 0; i < colorTable.size(); i++) {
			Color color = (Color) colorTable.elementAt(i);
			header.append("\\red");
			header.append(color.getRed());
			header.append("\\green");
			header.append(color.getGreen());
			header.append("\\blue");
			header.append(color.getBlue());
			header.append(";");
		}
		// some RTF readers ignore the deff0 font tag. Explicitly 
		// set the font for the whole document to work around this.
		header.append("}\n{\\f0\\fs");
		// font size is specified in half points
		header.append(fontData.getHeight() * 2);
		header.append(" ");
		write(header.toString(), 0);
	}
	/**
	 * Appends the specified line text to the RTF data.  Lines will be formatted 
	 * using the styles queried from the LineStyleListener, if set, or those set 
	 * directly in the widget.
	 * <p>
	 *
	 * @param line line text to write as RTF. Must not contain line breaks
	 * 	Line breaks should be written using writeLineDelimiter()
	 * @param lineOffset offset of the line. 0 based from the start of the 
	 * 	widget document. Any text occurring before the start offset or after the 
	 * 	end offset specified during object creation is ignored.
	 * @exception SWTException <ul>
	 *   <li>ERROR_IO when the writer is closed.</li>
	 * </ul>
	 */
	public void writeLine(String line, int lineOffset) {
		if (isClosed()) {
			SWT.error(SWT.ERROR_IO);
		}
		int lineIndex = content.getLineAtOffset(lineOffset);
		int lineAlignment, lineIndent;
		boolean lineJustify;
		int[] ranges;
		StyleRange[] styles;
		StyledTextEvent event = getLineStyleData(lineOffset, line);
		if (event != null) {
			lineAlignment = event.alignment;
			lineIndent = event.indent;
			lineJustify = event.justify;
			ranges = event.ranges;
			styles = event.styles;
		} else {
			lineAlignment = renderer.getLineAlignment(lineIndex, alignment);
			lineIndent =  renderer.getLineIndent(lineIndex, indent);			
			lineJustify = renderer.getLineJustify(lineIndex, justify);
			ranges = renderer.getRanges(lineOffset, line.length());
			styles = renderer.getStyleRanges(lineOffset, line.length(), false);
		}
		if (styles == null) styles = new StyleRange[0];		
		Color lineBackground = renderer.getLineBackground(lineIndex, null);
		event = getLineBackgroundData(lineOffset, line);
		if (event != null && event.lineBackground != null) lineBackground = event.lineBackground;
		writeStyledLine(line, lineOffset, ranges, styles, lineBackground, lineIndent, lineAlignment, lineJustify);
	}
	/**
	 * Appends the specified line delmimiter to the RTF data.
	 * <p>
	 *
	 * @param lineDelimiter line delimiter to write as RTF.
	 * @exception SWTException <ul>
	 *   <li>ERROR_IO when the writer is closed.</li>
	 * </ul>
	 */
	public void writeLineDelimiter(String lineDelimiter) {
		if (isClosed()) {
			SWT.error(SWT.ERROR_IO);
		}
		write(lineDelimiter, 0, lineDelimiter.length());
		write("\\par ");
	}
	/**
	 * Appends the specified line text to the RTF data.
	 * Use the colors and font styles specified in "styles" and "lineBackground".
	 * Formatting is written to reflect the text rendering by the text widget.
	 * Style background colors take precedence over the line background color.
	 * Background colors are written using the \highlight tag (vs. the \cb tag).
	 * <p>
	 *
	 * @param line line text to write as RTF. Must not contain line breaks
	 * 	Line breaks should be written using writeLineDelimiter()
	 * @param lineOffset offset of the line. 0 based from the start of the 
	 * 	widget document. Any text occurring before the start offset or after the 
	 * 	end offset specified during object creation is ignored.
	 * @param styles styles to use for formatting. Must not be null.
	 * @param lineBackground line background color to use for formatting. 
	 * 	May be null.
	 */
	void writeStyledLine(String line, int lineOffset, int ranges[], StyleRange[] styles, Color lineBackground, int indent, int alignment, boolean justify) {
		int lineLength = line.length();
		int startOffset = getStart();
		int writeOffset = startOffset - lineOffset;
		if (writeOffset >= lineLength) return;
		int lineIndex = Math.max(0, writeOffset);
			
		write("\\fi");
		write(indent);
		switch (alignment) {
			case SWT.LEFT: write("\\ql"); break;
			case SWT.CENTER: write("\\qc"); break;
			case SWT.RIGHT: write("\\qr"); break;
		}
		if (justify) write("\\qj");
		
		if (lineBackground != null) {
			write("{\\highlight");
			write(getColorIndex(lineBackground, DEFAULT_BACKGROUND));
			write(" "); 
		}
		int endOffset = startOffset + super.getCharCount();
		int lineEndOffset = Math.min(lineLength, endOffset - lineOffset);
		for (int i = 0; i < styles.length; i++) {
			StyleRange style = styles[i];
			int start, end;
			if (ranges != null) {
				start = ranges[i << 1] - lineOffset;
				end = start + ranges[(i << 1) + 1];
			} else {
				start = style.start - lineOffset;
				end = start + style.length;
			}
			// skip over partial first line
			if (end < writeOffset) {
				continue;
			}
			// style starts beyond line end or RTF write end
			if (start >= lineEndOffset) {
				break;
			}
			// write any unstyled text
			if (lineIndex < start) {
				// copy to start of style
				// style starting betond end of write range or end of line 
				// is guarded against above.
				write(line, lineIndex, start);
				lineIndex = start;
			}
			// write styled text
			write("{\\cf");
			write(getColorIndex(style.foreground, DEFAULT_FOREGROUND));
			int colorIndex = getColorIndex(style.background, DEFAULT_BACKGROUND);
			if (colorIndex != DEFAULT_BACKGROUND) {
				write("\\highlight");
				write(colorIndex);
			}
			Font font = style.font;
			if (font != null) {
				int fontIndex = getFontIndex(font);
				write("\\f");
				write(fontIndex);
				FontData fontData = font.getFontData()[0];
				write("\\fs");
				write(fontData.getHeight() * 2);
			} else {
				if ((style.fontStyle & SWT.BOLD) != 0) {
					write("\\b"); 
				}
				if ((style.fontStyle & SWT.ITALIC) != 0) {
					write("\\i"); 
				}
			}
			if (style.underline) {
				write("\\ul");
			}
			if (style.strikeout) {
				write("\\strike");
			}
			write(" "); 
			// copy to end of style or end of write range or end of line
			int copyEnd = Math.min(end, lineEndOffset);
			// guard against invalid styles and let style processing continue
			copyEnd = Math.max(copyEnd, lineIndex);
			write(line, lineIndex, copyEnd);
			if (font == null) {
				if ((style.fontStyle & SWT.BOLD) != 0) {
					write("\\b0"); 
				}
				if ((style.fontStyle & SWT.ITALIC) != 0) {
					write("\\i0"); 
				}
			}
			if (style.underline) {
				write("\\ul0");
			}
			if (style.strikeout) {
				write("\\strike0");
			}
			write("}");
			lineIndex = copyEnd;
		}
		// write unstyled text at the end of the line
		if (lineIndex < lineEndOffset) {
			write(line, lineIndex, lineEndOffset);
		}
		if (lineBackground != null) write("}");
	}
	}
	/**
	 * The <code>TextWriter</code> class is used to write widget content to
	 * a string.  Whole and partial lines and line breaks can be written. To write 
	 * partial lines, specify the start and length of the desired segment 
	 * during object creation.
	 * <p>
	 * </b>NOTE:</b> <code>toString()</code> is guaranteed to return a valid string only after close() 
	 * has been called. 
	 */
	class TextWriter {
		private StringBuffer buffer;
		private int startOffset;	// offset of first character that will be written
		private int endOffset;		// offset of last character that will be written. 
									// 0 based from the beginning of the widget text. 
		private boolean isClosed = false;
	
	/**
	 * Creates a writer that writes content starting at offset "start"
	 * in the document.  <code>start</code> and <code>length</code> can be set to specify partial lines.
	 * <p>
	 *
	 * @param start start offset of content to write, 0 based from beginning of document
	 * @param length length of content to write
	 */
	public TextWriter(int start, int length) {
		buffer = new StringBuffer(length);
		startOffset = start;
		endOffset = start + length;
	}
	/**
	 * Closes the writer. Once closed no more content can be written.
	 * <b>NOTE:</b>  <code>toString()</code> is not guaranteed to return a valid string unless
	 * the writer is closed.
	 */
	public void close() {
		if (!isClosed) {
			isClosed = true;
		}
	}
	/** 
	 * Returns the number of characters to write.
	 * @return the integer number of characters to write
	 */
	public int getCharCount() {
		return endOffset - startOffset;
	}	
	/** 
	 * Returns the offset where writing starts. 0 based from the start of 
	 * the widget text. Used to write partial lines.
	 * @return the integer offset where writing starts
	 */
	public int getStart() {
		return startOffset;
	}
	/**
	 * Returns whether the writer is closed.
	 * @return a boolean specifying whether or not the writer is closed
	 */
	public boolean isClosed() {
		return isClosed;
	}
	/**
	 * Returns the string.  <code>close()</code> must be called before <code>toString()</code> 
	 * is guaranteed to return a valid string.
	 *
	 * @return the string
	 */
	public String toString() {
		return buffer.toString();
	}
	/**
	 * Appends the given string to the data.
	 */
	void write(String string) {
		buffer.append(string);
	}
	/**
	 * Inserts the given string to the data at the specified offset.
	 * Do nothing if "offset" is < 0 or > getCharCount()
	 * <p>
	 *
	 * @param string text to insert
	 * @param offset offset in the existing data to insert "string" at.
	 */
	void write(String string, int offset) {
		if (offset < 0 || offset > buffer.length()) {
			return;
		}
		buffer.insert(offset, string);
	}
	/**
	 * Appends the given int to the data.
	 */
	void write(int i) {
		buffer.append(i);
	}
	/**
	 * Appends the given character to the data.
	 */
	void write(char i) {
		buffer.append(i);
	}
	/**
	 * Appends the specified line text to the data.
	 * <p>
	 *
	 * @param line line text to write. Must not contain line breaks
	 * 	Line breaks should be written using writeLineDelimiter()
	 * @param lineOffset offset of the line. 0 based from the start of the 
	 * 	widget document. Any text occurring before the start offset or after the 
	 *	end offset specified during object creation is ignored.
	 * @exception SWTException <ul>
	 *   <li>ERROR_IO when the writer is closed.</li>
	 * </ul>
	 */
	public void writeLine(String line, int lineOffset) {	
		if (isClosed) {
			SWT.error(SWT.ERROR_IO);
		}		
		int writeOffset = startOffset - lineOffset;
		int lineLength = line.length();
		int lineIndex;
		if (writeOffset >= lineLength) {
			return;							// whole line is outside write range
		} else if (writeOffset > 0) {
			lineIndex = writeOffset;		// line starts before write start
		} else {
			lineIndex = 0;
		}
		int copyEnd = Math.min(lineLength, endOffset - lineOffset);
		if (lineIndex < copyEnd) {
			write(line.substring(lineIndex, copyEnd));
		}
	}
	/**
	 * Appends the specified line delmimiter to the data.
	 * <p>
	 *
	 * @param lineDelimiter line delimiter to write
	 * @exception SWTException <ul>
	 *   <li>ERROR_IO when the writer is closed.</li>
	 * </ul>
	 */
	public void writeLineDelimiter(String lineDelimiter) {
		if (isClosed) {
			SWT.error(SWT.ERROR_IO);
		}
		write(lineDelimiter);
	}
	}

/**
 * Constructs a new instance of this class given its parent
 * and a style value describing its behavior and appearance.
 * <p>
 * The style value is either one of the style constants defined in
 * class <code>SWT</code> which is applicable to instances of this
 * class, or must be built by <em>bitwise OR</em>'ing together 
 * (that is, using the <code>int</code> "|" operator) two or more
 * of those <code>SWT</code> style constants. The class description
 * lists the style constants that are applicable to the class.
 * Style bits are also inherited from superclasses.
 * </p>
 *
 * @param parent a widget which will be the parent of the new instance (cannot be null)
 * @param style the style of widget to construct
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the parent is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
 * </ul>
 *
 * @see SWT#FULL_SELECTION
 * @see SWT#MULTI
 * @see SWT#READ_ONLY
 * @see SWT#SINGLE
 * @see SWT#WRAP
 * @see #getStyle
 */
public StyledText(Composite parent, int style) {
	super(parent, checkStyle(style | SWT.NO_REDRAW_RESIZE | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED));
	// set the bg/fg in the OS to ensure that these are the same as StyledText, necessary
	// for ensuring that the bg/fg the IME box uses is the same as what StyledText uses
	super.setForeground(getForeground());
	super.setBackground(getBackground());
	Display display = getDisplay();
	isMirrored = (super.getStyle() & SWT.MIRRORED) != 0;
	fixedLineHeight = true;
	if ((style & SWT.READ_ONLY) != 0) {
		setEditable(false);
	}
	leftMargin = rightMargin = isBidiCaret() ? BIDI_CARET_WIDTH - 1: 0;
	if ((style & SWT.SINGLE) != 0 && (style & SWT.BORDER) != 0) {
		leftMargin = topMargin = rightMargin = bottomMargin = 2;
	}
	alignment = SWT.LEFT;
	clipboard = new Clipboard(display);
	installDefaultContent();
	renderer = new StyledTextRenderer(getDisplay(), this);
	renderer.setContent(content);
	renderer.setFont(getFont(), tabLength);
	defaultCaret = new Caret(this, SWT.NULL);
	if ((style & SWT.WRAP) != 0) {
		setWordWrap(true);
	}
	if (isBidiCaret()) {
		createCaretBitmaps();
		Runnable runnable = new Runnable() {
			public void run() {
				int direction = BidiUtil.getKeyboardLanguage() == BidiUtil.KEYBOARD_BIDI ? SWT.RIGHT : SWT.LEFT;
				if (direction == caretDirection) return;
				if (getCaret() != defaultCaret) return;
				Point newCaretPos = getPointAtOffset(caretOffset);
				setCaretLocation(newCaretPos, direction);
			}
		};
		BidiUtil.addLanguageListener(handle, runnable);
	}
	setCaret(defaultCaret);	
	calculateScrollBars();
	createKeyBindings();
	setCursor(display.getSystemCursor(SWT.CURSOR_IBEAM));
	installListeners();
	initializeAccessible();
}
/**	 
 * Adds an extended modify listener. An ExtendedModify event is sent by the 
 * widget when the widget text has changed.
 * <p>
 *
 * @param extendedModifyListener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addExtendedModifyListener(ExtendedModifyListener extendedModifyListener) {
	checkWidget();
	if (extendedModifyListener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	StyledTextListener typedListener = new StyledTextListener(extendedModifyListener);
	addListener(ExtendedModify, typedListener);
}
/**
 * Sets the default alignment of the receiver
 * <p>
 * 
 * @param alignment alignment 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @see #setLineAlignment(int, int, int) 
 * @since 3.2
 */
public void setAlignment(int alignment) {
	checkWidget();
	alignment &= (SWT.LEFT | SWT.RIGHT | SWT.CENTER);
	if (alignment == 0 || this.alignment == alignment);	
	this.alignment = alignment;
	resetCache(0, content.getLineCount());
	setCaretLocation();
	super.redraw();
}
/** 
 * Maps a key to an action.
 * One action can be associated with N keys. However, each key can only 
 * have one action (key:action is N:1 relation).
 * <p>
 *
 * @param key a key code defined in SWT.java or a character. 
 * 	Optionally ORd with a state mask.  Preferred state masks are one or more of
 *  SWT.MOD1, SWT.MOD2, SWT.MOD3, since these masks account for modifier platform 
 *  differences.  However, there may be cases where using the specific state masks
 *  (i.e., SWT.CTRL, SWT.SHIFT, SWT.ALT, SWT.COMMAND) makes sense.
 * @param action one of the predefined actions defined in ST.java. 
 * 	Use SWT.NULL to remove a key binding.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setKeyBinding(int key, int action) {
	checkWidget();
	int modifierValue = key & SWT.MODIFIER_MASK;
	char keyChar = (char)(key & SWT.KEY_MASK);
	if (Compatibility.isLetter(keyChar)) {
		// make the keybinding case insensitive by adding it
		// in its upper and lower case form
		char ch = Character.toUpperCase(keyChar);
		int newKey = ch | modifierValue;
		if (action == SWT.NULL) {
			keyActionMap.remove(new Integer(newKey));
		} else {
		 	keyActionMap.put(new Integer(newKey), new Integer(action));
		}
		ch = Character.toLowerCase(keyChar);
		newKey = ch | modifierValue;
		if (action == SWT.NULL) {
			keyActionMap.remove(new Integer(newKey));
		} else {
		 	keyActionMap.put(new Integer(newKey), new Integer(action));
		}
	} else {
		if (action == SWT.NULL) {
			keyActionMap.remove(new Integer(key));
		} else {
		 	keyActionMap.put(new Integer(key), new Integer(action));
		}
	}		
}
/**
 * Adds a bidirectional segment listener. A BidiSegmentEvent is sent 
 * whenever a line of text is measured or rendered. The user can 
 * specify text ranges in the line that should be treated as if they 
 * had a different direction than the surrounding text.
 * This may be used when adjacent segments of right-to-left text should
 * not be reordered relative to each other. 
 * E.g., Multiple Java string literals in a right-to-left language
 * should generally remain in logical order to each other, that is, the
 * way they are stored. 
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 * @see BidiSegmentEvent
 * @since 2.0
 */
public void addBidiSegmentListener(BidiSegmentListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	addListener(LineGetSegments, new StyledTextListener(listener));
}
/**
 * Adds a line background listener. A LineGetBackground event is sent by the 
 * widget to determine the background color for a line.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addLineBackgroundListener(LineBackgroundListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (!isListening(LineGetBackground)) {
		renderer.clearLineBackground(0, content.getLineCount());
	}
	addListener(LineGetBackground, new StyledTextListener(listener));
}
/**
 * Adds a line style listener. A LineGetStyle event is sent by the widget to 
 * determine the styles for a line.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addLineStyleListener(LineStyleListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (!isListening(LineGetStyle)) {
		setStyleRanges(0, 0, null, null, true);
		renderer.clearLineStyle(0, content.getLineCount());
	}
	setVariableLineHeight();
	addListener(LineGetStyle, new StyledTextListener(listener));
}
/**	 
 * Adds a modify listener. A Modify event is sent by the widget when the widget text 
 * has changed.
 * <p>
 *
 * @param modifyListener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addModifyListener(ModifyListener modifyListener) {
	checkWidget();
	if (modifyListener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	addListener(SWT.Modify, new TypedListener(modifyListener));
}
/**	 
 * Adds a paint object listener. A paint object event is sent by the widget when an object
 * needs to be drawn.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 * @since 3.2
 */
public void addPaintObjectListener(PaintObjectListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	addListener(PaintObject, new StyledTextListener(listener));
}
/**	 
 * Adds a selection listener. A Selection event is sent by the widget when the 
 * selection has changed.
 * <p>
 * When <code>widgetSelected</code> is called, the event x amd y fields contain
 * the start and end caret indices of the selection.
 * <code>widgetDefaultSelected</code> is not called for StyledTexts.
 * </p>
 * 
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addSelectionListener(SelectionListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	addListener(SWT.Selection, new TypedListener(listener));
}
/**	 
 * Adds a verify key listener. A VerifyKey event is sent by the widget when a key 
 * is pressed. The widget ignores the key press if the listener sets the doit field 
 * of the event to false. 
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addVerifyKeyListener(VerifyKeyListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	addListener(VerifyKey, new StyledTextListener(listener));
}
/**	 
 * Adds a verify listener. A Verify event is sent by the widget when the widget text 
 * is about to change. The listener can set the event text and the doit field to 
 * change the text that is set in the widget or to force the widget to ignore the 
 * text change.
 * <p>
 *
 * @param verifyListener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void addVerifyListener(VerifyListener verifyListener) {
	checkWidget();
	if (verifyListener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	addListener(SWT.Verify, new TypedListener(verifyListener));
}
/** 
 * Appends a string to the text at the end of the widget.
 * <p>
 *
 * @param string the string to be appended
 * @see #replaceTextRange(int,int,String)
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void append(String string) {
	checkWidget();
	if (string == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	int lastChar = Math.max(getCharCount(), 0);
	replaceTextRange(lastChar, 0, string);
}
/**
 * Calculates the scroll bars
 */
void calculateScrollBars() {
	ScrollBar horizontalBar = getHorizontalBar();
	ScrollBar verticalBar = getVerticalBar();
	setScrollBars(true);
	if (verticalBar != null) {
		verticalBar.setIncrement(getVerticalIncrement());
	}	
	if (horizontalBar != null) {
		horizontalBar.setIncrement(getHorizontalIncrement());
	}
}
/**
 * Calculates the top index based on the current vertical scroll offset.
 * The top index is the index of the topmost fully visible line or the
 * topmost partially visible line if no line is fully visible.
 * The top index starts at 0.
 */
void calculateTopIndex(int delta) {
	int oldTopIndex = topIndex;	
	if (isFixedLineHeight()) {
		int verticalIncrement = getVerticalIncrement();
		if (verticalIncrement == 0) {
			return;
		}
		topIndex = Compatibility.ceil(getVerticalScrollOffset(), verticalIncrement);
		// Set top index to partially visible top line if no line is fully 
		// visible but at least some of the widget client area is visible.
		// Fixes bug 15088.
		if (topIndex > 0) {
			int clientAreaHeight = getClientArea().height;
			if (clientAreaHeight > 0) {
				int bottomPixel = getVerticalScrollOffset() + clientAreaHeight;
				int fullLineTopPixel = topIndex * verticalIncrement;
				int fullLineVisibleHeight = bottomPixel - fullLineTopPixel;
				// set top index to partially visible line if no line fully fits in 
				// client area or if space is available but not used (the latter should
				// never happen because we use claimBottomFreeSpace)
				if (fullLineVisibleHeight < verticalIncrement) {
					topIndex--;
				}
			} else if (topIndex >= content.getLineCount()) {
				topIndex = content.getLineCount() - 1;
			}
		}
	} else {
		if (delta > 0) {
			if (partialHeight > delta) {
				partialHeight -= delta;
				return;
			}
			delta -= partialHeight;
			partialHeight = 0;
			
			int lineCount = content.getLineCount();
			while (delta > 0 && topIndex < lineCount -1) {
				int lineHeight = renderer.getLineHeight(topIndex);
				topIndex++;
				if (lineHeight > delta) {
					partialHeight = lineHeight - delta;
					break;
				}
				delta -= lineHeight;
			}
		} else {
			if (topIndex > 0) {
				int height = renderer.getLineHeight(topIndex - 1) - partialHeight;
				if (height > -delta) {
					partialHeight -= delta;
					return;
				}
				delta += height;
				partialHeight = 0;
				topIndex--;
			}
			while (-delta > 0 && topIndex > 0) {
				int lineHeight = renderer.getLineHeight(topIndex - 1);
				if (lineHeight > -delta) {
					partialHeight = -delta;
					break;
				}
				topIndex--;
				delta += lineHeight;
			}
		}
	}
	if (topIndex != oldTopIndex) {
		topOffset = content.getOffsetAtLine(topIndex);
		renderer.calculateClientArea();
		setScrollBars(false);
	}
}
/**
 * Hides the scroll bars if widget is created in single line mode.
 */
static int checkStyle(int style) {
	if ((style & SWT.SINGLE) != 0) {
		style &= ~(SWT.H_SCROLL | SWT.V_SCROLL | SWT.WRAP | SWT.MULTI);
	} else {
		style |= SWT.MULTI;
		if ((style & SWT.WRAP) != 0) {
			style &= ~SWT.H_SCROLL;
		}
	}
	return style;
}
/**
 * Scrolls down the text to use new space made available by a resize or by 
 * deleted lines.
 */
void claimBottomFreeSpace() {
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		int newVerticalOffset = Math.max(0, content.getLineCount() * lineHeight - getClientArea().height);
		if (newVerticalOffset < getVerticalScrollOffset()) {
			// Scroll up so that empty lines below last text line are used.
			// Fixes 1GEYJM0
			scrollVertical(newVerticalOffset - getVerticalScrollOffset(), true);
		}
	} else {	
		int clientAreaHeight = getClientArea().height;
		int bottomIndex = getPartialBottomIndex();
		int height = getLinePixel(bottomIndex + 1);
		if (clientAreaHeight > height) {
			int delta = clientAreaHeight - height;
			int maxDelta = verticalScrollOffset;
			if (maxDelta == -1) {
				int index = getPartialTopIndex();
				maxDelta = renderer.getLineHeight(index) - partialHeight;
				while (delta > maxDelta && index > 0) {
					index--;
					maxDelta += renderer.getLineHeight(index);
				}
			}
			delta = Math.min(maxDelta, delta);
			if (verticalScrollOffset != -1) {
				scrollVertical(-delta, true);
			} else {
				// scrolling when verticalScrollOffset is invalid is a slow operation
				// only update the top index and the caret, caller needs to invalidate the area
				// verticalScrollOffset is valid at the end of the calculate idle
				calculateTopIndex(-delta);
			}
			
		}
	}
}
/**
 * Scrolls text to the right to use new space made available by a resize.
 */
void claimRightFreeSpace() {
	int newHorizontalOffset = Math.max(0, renderer.getWidth() - (getClientArea().width - leftMargin - rightMargin));
	if (newHorizontalOffset < horizontalScrollOffset) {			
		// item is no longer drawn past the right border of the client area
		// align the right end of the item with the right border of the 
		// client area (window is scrolled right).
		scrollHorizontal(newHorizontalOffset - horizontalScrollOffset, true);					
	}
}
/**
 * Clears the widget margin.
 * 
 * @param gc GC to render on
 * @param background background color to use for clearing the margin
 * @param clientArea widget client area dimensions
 */
void clearMargin(GC gc, Color background, Rectangle clientArea, int y) {
	// clear the margin background
	gc.setBackground(background);
	if (topMargin > 0) {
		gc.fillRectangle(0, -y, clientArea.width, topMargin);
	}
	if (bottomMargin > 0) {
		gc.fillRectangle(0, clientArea.height - bottomMargin - y, clientArea.width, bottomMargin);
	}
	if (leftMargin > 0) {
		gc.fillRectangle(0, -y, leftMargin, clientArea.height);
	}
	if (rightMargin > 0) {
		gc.fillRectangle(clientArea.width - rightMargin, -y, rightMargin, clientArea.height);
	}
}
/**
 * Removes the widget selection.
 * <p>
 *
 * @param sendEvent a Selection event is sent when set to true and when the selection is actually reset.
 */
void clearSelection(boolean sendEvent) {
	int selectionStart = selection.x;
	int selectionEnd = selection.y;	
	resetSelection();
	// redraw old selection, if any
	if (selectionEnd - selectionStart > 0) {
		int length = content.getCharCount();
		// called internally to remove selection after text is removed
		// therefore make sure redraw range is valid.
		int redrawStart = Math.min(selectionStart, length);
		int redrawEnd = Math.min(selectionEnd, length);
		if (redrawEnd - redrawStart > 0) {
			internalRedrawRange(redrawStart, redrawEnd - redrawStart);
		}
		if (sendEvent) {
			sendSelectionEvent();
		}
	}
}
public Point computeSize (int wHint, int hHint, boolean changed) {
	checkWidget();
	boolean singleLine = (getStyle() & SWT.SINGLE) != 0;
	int lineCount = singleLine ? 1 : content.getLineCount();
	int width = 0;
	int height = 0;
	if (wHint == SWT.DEFAULT || hHint == SWT.DEFAULT) {
		Display display = getDisplay();
		int maxHeight = display.getClientArea().height;
		int lineIndex = 0;
		while (lineIndex < lineCount && height < maxHeight) {
			TextLayout layout = renderer.getTextLayout(lineIndex);
			if (wordWrap) {
				layout.setWidth(wHint);
			}
			Rectangle rect = layout.getBounds();
			height += rect.height;
			width = Math.max(width, rect.width);
			renderer.disposeTextLayout(layout);
			lineIndex++;
		}
	}
	// Use default values if no text is defined.
	if (width == 0) width = DEFAULT_WIDTH;
	if (height == 0) height = DEFAULT_HEIGHT;
	if (wHint != SWT.DEFAULT) width = wHint;
	if (hHint != SWT.DEFAULT) height = hHint;
	int wTrim = leftMargin + rightMargin + getCaretWidth();
	int hTrim = topMargin + bottomMargin;
	Rectangle rect = computeTrim(0, 0, width + wTrim, height + hTrim);
	return new Point (rect.width, rect.height);
}
/**
 * Copies the selected text to the <code>DND.CLIPBOARD</code> clipboard.
 * The text will be put on the clipboard in plain text format and RTF format.
 * The <code>DND.CLIPBOARD</code> clipboard is used for data that is
 *  transferred by keyboard accelerator (such as Ctrl+C/Ctrl+V) or 
 *  by menu action.
 * 
 * <p>
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void copy() {
	checkWidget();
	copy(DND.CLIPBOARD);
}
/**
 * Copies the selected text to the specified clipboard.  The text will be put in the 
 * clipboard in plain text format and RTF format.
 * 
 * <p>The clipboardType is  one of the clipboard constants defined in class 
 * <code>DND</code>.  The <code>DND.CLIPBOARD</code>  clipboard is 
 * used for data that is transferred by keyboard accelerator (such as Ctrl+C/Ctrl+V) 
 * or by menu action.  The <code>DND.SELECTION_CLIPBOARD</code> 
 * clipboard is used for data that is transferred by selecting text and pasting 
 * with the middle mouse button.</p>
 * 
 * @param clipboardType indicates the type of clipboard
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * 
 * @since 3.1
 */
public void copy(int clipboardType) {
	checkWidget();
	if (clipboardType != DND.CLIPBOARD && clipboardType != DND.SELECTION_CLIPBOARD) return;
	int length = selection.y - selection.x;
	if (length > 0) {
		try {
			setClipboardContent(selection.x, length, clipboardType);
		} catch (SWTError error) {
			// Copy to clipboard failed. This happens when another application 
			// is accessing the clipboard while we copy. Ignore the error.
			// Fixes 1GDQAVN
			// Rethrow all other errors. Fixes bug 17578.
			if (error.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
				throw error;
			}
		}
	}
}
/**
 * Returns the default alignment of the receiver
 * <p>
 * 
 * @return the alignment
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul> 
 * @see #getLineAlignment(int)
 * @since 3.2
 */
public int getAlignment() {
	checkWidget();
	return alignment;
}
/**
 * Returns a string that uses only the line delimiter specified by the 
 * StyledTextContent implementation.
 * Returns only the first line if the widget has the SWT.SINGLE style.
 * <p>
 *
 * @param text the text that may have line delimiters that don't 
 * 	match the model line delimiter. Possible line delimiters 
 * 	are CR ('\r'), LF ('\n'), CR/LF ("\r\n")
 * @return the converted text that only uses the line delimiter 
 * 	specified by the model. Returns only the first line if the widget 
 * 	has the SWT.SINGLE style.
 */
String getModelDelimitedText(String text) {	
	int length = text.length();
	if (length == 0) {
		return text;
	}	
	int crIndex = 0;
	int lfIndex = 0;
	int i = 0;
	StringBuffer convertedText = new StringBuffer(length);
	String delimiter = getLineDelimiter();
	while (i < length) {
		if (crIndex != -1) {
			crIndex = text.indexOf(SWT.CR, i);
		}
		if (lfIndex != -1) {
			lfIndex = text.indexOf(SWT.LF, i);
		}
		if (lfIndex == -1 && crIndex == -1) {	// no more line breaks?
			break;
		} else if ((crIndex < lfIndex && crIndex != -1) || lfIndex == -1) {
			convertedText.append(text.substring(i, crIndex));
			if (lfIndex == crIndex + 1) {		// CR/LF combination?
				i = lfIndex + 1;
			} else {
				i = crIndex + 1;
			}
		} else {									// LF occurs before CR!
			convertedText.append(text.substring(i, lfIndex));
			i = lfIndex + 1;
		}
		if (isSingleLine()) {
			break;
		}
		convertedText.append(delimiter);
	}
	// copy remaining text if any and if not in single line mode or no 
	// text copied thus far (because there only is one line)
	if (i < length && (!isSingleLine() || convertedText.length() == 0)) {
		convertedText.append(text.substring(i));
	}
	return convertedText.toString();
}
/**
 * Creates default key bindings.
 */
void createKeyBindings() {
	int nextKey = isMirrored() ? SWT.ARROW_LEFT : SWT.ARROW_RIGHT;
	int previousKey = isMirrored() ? SWT.ARROW_RIGHT : SWT.ARROW_LEFT;
	
	// Navigation
	setKeyBinding(SWT.ARROW_UP, ST.LINE_UP);	
	setKeyBinding(SWT.ARROW_DOWN, ST.LINE_DOWN);
	setKeyBinding(SWT.HOME, ST.LINE_START);
	setKeyBinding(SWT.END, ST.LINE_END);
	setKeyBinding(SWT.PAGE_UP, ST.PAGE_UP);
	setKeyBinding(SWT.PAGE_DOWN, ST.PAGE_DOWN);
	setKeyBinding(SWT.HOME | SWT.MOD1, ST.TEXT_START);
	setKeyBinding(SWT.END | SWT.MOD1, ST.TEXT_END);
	setKeyBinding(SWT.PAGE_UP | SWT.MOD1, ST.WINDOW_START);
	setKeyBinding(SWT.PAGE_DOWN | SWT.MOD1, ST.WINDOW_END);
	setKeyBinding(nextKey, ST.COLUMN_NEXT);
	setKeyBinding(previousKey, ST.COLUMN_PREVIOUS);
	setKeyBinding(nextKey | SWT.MOD1, ST.WORD_NEXT);
	setKeyBinding(previousKey | SWT.MOD1, ST.WORD_PREVIOUS);
	
	// Selection
	setKeyBinding(SWT.ARROW_UP | SWT.MOD2, ST.SELECT_LINE_UP);	
	setKeyBinding(SWT.ARROW_DOWN | SWT.MOD2, ST.SELECT_LINE_DOWN);
	setKeyBinding(SWT.HOME | SWT.MOD2, ST.SELECT_LINE_START);
	setKeyBinding(SWT.END | SWT.MOD2, ST.SELECT_LINE_END);
	setKeyBinding(SWT.PAGE_UP | SWT.MOD2, ST.SELECT_PAGE_UP);
	setKeyBinding(SWT.PAGE_DOWN | SWT.MOD2, ST.SELECT_PAGE_DOWN);
	setKeyBinding(SWT.HOME | SWT.MOD1 | SWT.MOD2, ST.SELECT_TEXT_START);	
	setKeyBinding(SWT.END | SWT.MOD1 | SWT.MOD2, ST.SELECT_TEXT_END);
	setKeyBinding(SWT.PAGE_UP | SWT.MOD1 | SWT.MOD2, ST.SELECT_WINDOW_START);
	setKeyBinding(SWT.PAGE_DOWN | SWT.MOD1 | SWT.MOD2, ST.SELECT_WINDOW_END);
	setKeyBinding(nextKey | SWT.MOD2, ST.SELECT_COLUMN_NEXT);
	setKeyBinding(previousKey | SWT.MOD2, ST.SELECT_COLUMN_PREVIOUS);	
	setKeyBinding(nextKey | SWT.MOD1 | SWT.MOD2, ST.SELECT_WORD_NEXT);
	setKeyBinding(previousKey | SWT.MOD1 | SWT.MOD2, ST.SELECT_WORD_PREVIOUS);
           	  	
	// Modification
	// Cut, Copy, Paste
	setKeyBinding('X' | SWT.MOD1, ST.CUT);
	setKeyBinding('C' | SWT.MOD1, ST.COPY);
	setKeyBinding('V' | SWT.MOD1, ST.PASTE);
	// Cut, Copy, Paste Wordstar style
	setKeyBinding(SWT.DEL | SWT.MOD2, ST.CUT);
	setKeyBinding(SWT.INSERT | SWT.MOD1, ST.COPY);
	setKeyBinding(SWT.INSERT | SWT.MOD2, ST.PASTE);
	setKeyBinding(SWT.BS | SWT.MOD2, ST.DELETE_PREVIOUS);
	
	setKeyBinding(SWT.BS, ST.DELETE_PREVIOUS);
	setKeyBinding(SWT.DEL, ST.DELETE_NEXT);
	setKeyBinding(SWT.BS | SWT.MOD1, ST.DELETE_WORD_PREVIOUS);
	setKeyBinding(SWT.DEL | SWT.MOD1, ST.DELETE_WORD_NEXT);
	
	// Miscellaneous
	setKeyBinding(SWT.INSERT, ST.TOGGLE_OVERWRITE);
}
/**
 * Create the bitmaps to use for the caret in bidi mode.  This
 * method only needs to be called upon widget creation and when the
 * font changes (the caret bitmap height needs to match font height).
 */
void createCaretBitmaps() {
	int caretWidth = BIDI_CARET_WIDTH;
	Display display = getDisplay();
	if (leftCaretBitmap != null) {
		if (defaultCaret != null && leftCaretBitmap.equals(defaultCaret.getImage())) {
			defaultCaret.setImage(null);
		}
		leftCaretBitmap.dispose();
	}
	int lineHeight = renderer.getLineHeight();
	leftCaretBitmap = new Image(display, caretWidth, lineHeight);
	GC gc = new GC (leftCaretBitmap); 
	gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
	gc.fillRectangle(0, 0, caretWidth, lineHeight);
	gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
	gc.drawLine(0,0,0,lineHeight);
	gc.drawLine(0,0,caretWidth-1,0);
	gc.drawLine(0,1,1,1);
	gc.dispose();	
	
	if (rightCaretBitmap != null) {
		if (defaultCaret != null && rightCaretBitmap.equals(defaultCaret.getImage())) {
			defaultCaret.setImage(null);
		}
		rightCaretBitmap.dispose();
	}
	rightCaretBitmap = new Image(display, caretWidth, lineHeight);
	gc = new GC (rightCaretBitmap); 
	gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
	gc.fillRectangle(0, 0, caretWidth, lineHeight);
	gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
	gc.drawLine(caretWidth-1,0,caretWidth-1,lineHeight);
	gc.drawLine(0,0,caretWidth-1,0);
	gc.drawLine(caretWidth-1,1,1,1);
	gc.dispose();
}
/**
 * Moves the selected text to the clipboard.  The text will be put in the 
 * clipboard in plain text format and RTF format.
 * <p>
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void cut(){
	checkWidget();
	int length = selection.y - selection.x;
	if (length > 0) {
		try {
			setClipboardContent(selection.x, length, DND.CLIPBOARD);
		} catch (SWTError error) {
			// Copy to clipboard failed. This happens when another application 
			// is accessing the clipboard while we copy. Ignore the error.
			// Fixes 1GDQAVN
			// Rethrow all other errors. Fixes bug 17578.
			if (error.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
				throw error;
			}
			// Abort cut operation if copy to clipboard fails.
			// Fixes bug 21030.
			return;
		}
		doDelete();
	}
}
/** 
 * A mouse move event has occurred.  See if we should start autoscrolling.  If
 * the move position is outside of the client area, initiate autoscrolling.  
 * Otherwise, we've moved back into the widget so end autoscrolling.
 */
void doAutoScroll(Event event) {
	Rectangle area = getClientArea();	
	if (event.y > area.height) {
		doAutoScroll(SWT.DOWN, event.y - area.height);
	} else if (event.y < 0) {
		doAutoScroll(SWT.UP, -event.y);
	} else if (event.x < leftMargin && !wordWrap) {
		doAutoScroll(ST.COLUMN_PREVIOUS, leftMargin - event.x);
	} else if (event.x > area.width - leftMargin - rightMargin && !wordWrap) {
		doAutoScroll(ST.COLUMN_NEXT, event.x - (area.width - leftMargin - rightMargin));
	} else {
		endAutoScroll();
	}
}
/** 
 * Initiates autoscrolling.
 * <p>
 *
 * @param direction SWT.UP, SWT.DOWN, SWT.COLUMN_NEXT, SWT.COLUMN_PREVIOUS
 */
void doAutoScroll(int direction, int distance) {
	autoScrollDistance = distance;
	// If we're already autoscrolling in the given direction do nothing
	if (autoScrollDirection == direction) {
		return;
	}
	
	Runnable timer = null;
	final Display display = getDisplay();
	// Set a timer that will simulate the user pressing and holding
	// down a cursor key (i.e., arrowUp, arrowDown).
	if (direction == SWT.UP) {
		timer = new Runnable() {
			public void run() {
				if (autoScrollDirection == SWT.UP) {
					doSelectionPageUp(autoScrollDistance);
					display.timerExec(V_SCROLL_RATE, this);
				}
			}
		};
		autoScrollDirection = direction;
		display.timerExec(V_SCROLL_RATE, timer);
	} else if (direction == SWT.DOWN) {
		timer = new Runnable() {
			public void run() {
				if (autoScrollDirection == SWT.DOWN) {
					doSelectionPageDown(autoScrollDistance);
					display.timerExec(V_SCROLL_RATE, this);
				}
			}
		};
		autoScrollDirection = direction;
		display.timerExec(V_SCROLL_RATE, timer);
	} else if (direction == ST.COLUMN_NEXT) {
		timer = new Runnable() {
			public void run() {
				if (autoScrollDirection == ST.COLUMN_NEXT) {
					doVisualNext();
					setMouseWordSelectionAnchor();
					doMouseSelection();
					display.timerExec(H_SCROLL_RATE, this);
				}
			}
		};
		autoScrollDirection = direction;
		display.timerExec(H_SCROLL_RATE, timer);
	} else if (direction == ST.COLUMN_PREVIOUS) {
		timer = new Runnable() {
			public void run() {
				if (autoScrollDirection == ST.COLUMN_PREVIOUS) {
					doVisualPrevious();
					setMouseWordSelectionAnchor();
					doMouseSelection();
					display.timerExec(H_SCROLL_RATE, this);
				}
			}
		};
		autoScrollDirection = direction;
		display.timerExec(H_SCROLL_RATE, timer);
	}
}
/**
 * Deletes the previous character. Delete the selected text if any.
 * Move the caret in front of the deleted text.
 */
void doBackspace() {
	Event event = new Event();
	event.text = "";
	if (selection.x != selection.y) {
		event.start = selection.x;
		event.end = selection.y;
		sendKeyEvent(event);
	} else if (caretOffset > 0) {
		int lineIndex = content.getLineAtOffset(caretOffset);
		int lineOffset = content.getOffsetAtLine(lineIndex);
		if (caretOffset == lineOffset) {
			lineOffset = content.getOffsetAtLine(lineIndex - 1);
			event.start = lineOffset + content.getLine(lineIndex - 1).length();
			event.end = caretOffset;
		} else {
			TextLayout layout = renderer.getTextLayout(lineIndex);
			int start = layout.getPreviousOffset(caretOffset - lineOffset, SWT.MOVEMENT_CHAR);
			renderer.disposeTextLayout(layout); 
			event.start = start + lineOffset;
			event.end = caretOffset;
		}
		sendKeyEvent(event);
	}
}
/**
 * Replaces the selection with the character or insert the character at the 
 * current caret position if no selection exists.
 * If a carriage return was typed replace it with the line break character 
 * used by the widget on this platform.
 * <p>
 *
 * @param key the character typed by the user
 */
void doContent(char key) {
	if (textLimit > 0 && 
		content.getCharCount() - (selection.y - selection.x) >= textLimit) {
		return;
	}
	Event event = new Event();
	event.start = selection.x;
	event.end = selection.y;
	// replace a CR line break with the widget line break
	// CR does not make sense on Windows since most (all?) applications
	// don't recognize CR as a line break.
	if (key == SWT.CR || key == SWT.LF) {
		if (!isSingleLine()) {
			event.text = getLineDelimiter();
		}
	} else if (selection.x == selection.y && overwrite && key != TAB) {
		// no selection and overwrite mode is on and the typed key is not a
		// tab character (tabs are always inserted without overwriting)?	
		int lineIndex = content.getLineAtOffset(event.end);
		int lineOffset = content.getOffsetAtLine(lineIndex);
		String line = content.getLine(lineIndex);
		// replace character at caret offset if the caret is not at the 
		// end of the line
		if (event.end < lineOffset + line.length()) {
			event.end++;
		}
		event.text = new String(new char[] {key});
	} else {
		event.text = new String(new char[] {key});
	}
	if (event.text != null) {
		sendKeyEvent(event);
	}
}
/**
 * Moves the caret after the last character of the widget content.
 */
void doContentEnd() {
	// place caret at end of first line if receiver is in single 
	// line mode. fixes 4820.
	if (isSingleLine()) {
		doLineEnd();
	} else {
		int length = content.getCharCount();		
		if (caretOffset < length) {
			caretOffset = length;
			showCaret();
		}
	}
}
/**
 * Moves the caret in front of the first character of the widget content.
 */
void doContentStart() {
	if (caretOffset > 0) {
		caretOffset = 0;
		showCaret();
	}
}
/**
 * Moves the caret to the start of the selection if a selection exists.
 * Otherwise, if no selection exists move the cursor according to the 
 * cursor selection rules.
 * <p>
 *
 * @see #doSelectionCursorPrevious
 */
void doCursorPrevious() {
	advancing = false;
	if (selection.y - selection.x > 0) {
		caretOffset = selection.x;
		showCaret();
	} else {
		doSelectionCursorPrevious();
	}
}
/**
 * Moves the caret to the end of the selection if a selection exists.
 * Otherwise, if no selection exists move the cursor according to the 
 * cursor selection rules.
 * <p>
 *
 * @see #doSelectionCursorNext
 */
void doCursorNext() {
	if (selection.y - selection.x > 0) {
		advancing = true;
		caretOffset = selection.y;
		showCaret();
	} else {
		doSelectionCursorNext();
	}
}
/**
 * Deletes the next character. Delete the selected text if any.
 */
void doDelete() {
	Event event = new Event();
	event.text = "";
	if (selection.x != selection.y) {
		event.start = selection.x;
		event.end = selection.y;
		sendKeyEvent(event);
	} else if (caretOffset < content.getCharCount()) {
		int line = content.getLineAtOffset(caretOffset);
		int lineOffset = content.getOffsetAtLine(line);
		int lineLength = content.getLine(line).length();
		if (caretOffset == lineOffset + lineLength) {
			event.start = caretOffset;
			event.end = content.getOffsetAtLine(line + 1);
		} else {
			event.start = caretOffset;
			event.end = getClusterNext(caretOffset, line);
		}
		sendKeyEvent(event);
	}
}
/**
 * Deletes the next word.
 */
void doDeleteWordNext() {
	if (selection.x != selection.y) {
		// if a selection exists, treat the as if 
		// only the delete key was pressed
		doDelete();
	} else {
		Event event = new Event();
		event.text = "";
		event.start = caretOffset;
		event.end = getWordEnd(caretOffset);
		sendKeyEvent(event);
	}
}
/**
 * Deletes the previous word.
 */
void doDeleteWordPrevious() {
	if (selection.x != selection.y) {
		// if a selection exists, treat as if 
		// only the backspace key was pressed
		doBackspace();
	} else {
		Event event = new Event();
		event.text = "";
		event.start = getWordStart(caretOffset);
		event.end = caretOffset;
		sendKeyEvent(event);
	}
}
/**
 * Moves the caret one line down and to the same character offset relative 
 * to the beginning of the line. Move the caret to the end of the new line 
 * if the new line is shorter than the character offset.
 * 
 * @return index of the new line relative to the first line in the document
 */
void doLineDown(boolean select) {
	int caretLine = getCaretLine();
	boolean paragraphDown = true;
	if (wordWrap) {
		TextLayout layout = renderer.getTextLayout(caretLine);
		if (layout.getLineCount() > 0) {
			Point pos = getCaret().getLocation();
			pos.x = columnX + horizontalScrollOffset;
			pos.y -= getLinePixel(caretLine);
			int offset = layout.getOffset(pos, null);
			int lineInParagraph = layout.getLineIndex(offset);
			if (lineInParagraph < layout.getLineCount() - 1) {
				int lineOffset = content.getOffsetAtLine(caretLine);
				paragraphDown = false;
				pos.y += layout.getLineBounds(lineInParagraph).height;
				caretOffset = lineOffset + layout.getOffset(pos, null);
				advancing = false;
			}
		}
		renderer.disposeTextLayout(layout);
	}
	if (select && caretLine == content.getLineCount() - 1) {
		caretOffset = content.getCharCount();
	}
	if (paragraphDown && caretLine < content.getLineCount() - 1) {
		caretLine++;
		int lineOffset = content.getOffsetAtLine(caretLine);
		TextLayout layout = renderer.getTextLayout(caretLine);
		int x = columnX + horizontalScrollOffset - leftMargin;
		caretOffset = lineOffset + layout.getOffset(x, 0, null);
		advancing = false;
		renderer.disposeTextLayout(layout);
	}
	int oldColumnX = columnX;
	int oldHScrollOffset = horizontalScrollOffset;
	if (select) {
		setMouseWordSelectionAnchor();	
		// select first and then scroll to reduce flash when key 
		// repeat scrolls lots of lines
		doSelection(ST.COLUMN_NEXT);
	}
	showCaret();
	int hScrollChange = oldHScrollOffset - horizontalScrollOffset;
	columnX = oldColumnX + hScrollChange;
}
/**
 * Moves the caret to the end of the line.
 */
void doLineEnd() {
	int caretLine = getCaretLine();
	int lineOffset = content.getOffsetAtLine(caretLine);	
	int lineLength = content.getLine(caretLine).length();
	int lineEndOffset = lineOffset + lineLength;
	if (caretOffset < lineEndOffset) {
		caretOffset = lineEndOffset;
		showCaret();
	}
}
/**
 * Moves the caret to the beginning of the line.
 */
void doLineStart() {
	int caretLine = getCaretLine();
	int lineOffset = content.getOffsetAtLine(caretLine);
	if (caretOffset > lineOffset) {
		caretOffset = lineOffset;
		showCaret();
	}
}
/**
 * Moves the caret one line up and to the same character offset relative 
 * to the beginning of the line. Move the caret to the end of the new line 
 * if the new line is shorter than the character offset.
 * 
 * @return index of the new line relative to the first line in the document
 */
void doLineUp(boolean select) {
	int caretLine = getCaretLine();
	boolean paragraphUp = true;
	if (wordWrap) {
		TextLayout layout = renderer.getTextLayout(caretLine);
		if (layout.getLineCount() > 0) {
			Point pos = getCaret().getLocation();
			pos.x = columnX + horizontalScrollOffset;
			pos.y -= getLinePixel(caretLine);
			int offset = layout.getOffset(pos, null);
			int lineInParagraph = layout.getLineIndex(offset);
			if (lineInParagraph > 0) {
				int lineOffset = content.getOffsetAtLine(caretLine);
				paragraphUp = false;
				pos.y -= layout.getLineBounds(lineInParagraph).height;
				caretOffset = lineOffset + layout.getOffset(pos, null);
				advancing = false;
			}
		}
		renderer.disposeTextLayout(layout);
	}
	if (select && caretLine == 0) {
		caretOffset = 0;
	}
	if (paragraphUp && caretLine > 0) {
		caretLine--;
		int lineOffset = content.getOffsetAtLine(caretLine);
		TextLayout layout = renderer.getTextLayout(caretLine);
		int x = columnX + horizontalScrollOffset - leftMargin;
		int y = layout.getBounds().height - 1;
		caretOffset = lineOffset + layout.getOffset(x, y, null);
		advancing = false;
		renderer.disposeTextLayout(layout);
	}
	int oldColumnX = columnX;
	int oldHScrollOffset = horizontalScrollOffset;
	if (select) setMouseWordSelectionAnchor();
	showCaret();
	if (select) doSelection(ST.COLUMN_PREVIOUS);
	int hScrollChange = oldHScrollOffset - horizontalScrollOffset;
	columnX = oldColumnX + hScrollChange;
}
/**
 * Moves the caret to the specified location.
 * <p>
 *
 * @param x x location of the new caret position
 * @param y y location of the new caret position
 * @param select the location change is a selection operation.
 * 	include the line delimiter in the selection
 */
void doMouseLocationChange(int x, int y, boolean select) {
	int line = getLineIndex(y);
	boolean oldAdvancing = advancing;

	updateCaretDirection = true;
	// allow caret to be placed below first line only if receiver is 
	// not in single line mode. fixes 4820.
	if (line < 0 || (isSingleLine() && line > 0)) {
		return;
	}
	int newCaretOffset = getOffsetAtPoint(x, y);
	
	if (mouseDoubleClick) {
		// double click word select the previous/next word. fixes bug 15610
		newCaretOffset = doMouseWordSelect(x, newCaretOffset, line);
	}
	int newCaretLine = content.getLineAtOffset(newCaretOffset);
	// Is the mouse within the left client area border or on 
	// a different line? If not the autoscroll selection 
	// could be incorrectly reset. Fixes 1GKM3XS
	if (y >= 0 && y < getClientArea().height && 
		(x >= 0 && x < getClientArea().width || wordWrap ||	
		newCaretLine != content.getLineAtOffset(caretOffset))) {
		if (newCaretOffset != caretOffset || advancing != oldAdvancing) {
			caretOffset = newCaretOffset;
			if (select) {
				doMouseSelection();
			}
			showCaret();
		}
	}
	if (!select) {
		caretOffset = newCaretOffset;
		clearSelection(true);
	}
}
/**
 * Updates the selection based on the caret position
 */
void doMouseSelection() {
	if (caretOffset <= selection.x || 
		(caretOffset > selection.x && 
		 caretOffset < selection.y && selectionAnchor == selection.x)) {
		doSelection(ST.COLUMN_PREVIOUS);
	} else {
		doSelection(ST.COLUMN_NEXT);
	}
}
/**
 * Returns the offset of the word at the specified offset. 
 * If the current selection extends from high index to low index 
 * (i.e., right to left, or caret is at left border of selecton on 
 * non-bidi platforms) the start offset of the word preceeding the
 * selection is returned. If the current selection extends from 
 * low index to high index the end offset of the word following 
 * the selection is returned.
 * 
 * @param x mouse x location
 * @param newCaretOffset caret offset of the mouse cursor location
 * @param line line index of the mouse cursor location
 */
int doMouseWordSelect(int x, int newCaretOffset, int line) {
	// flip selection anchor based on word selection direction from 
	// base double click. Always do this here (and don't rely on doAutoScroll)
	// because auto scroll only does not cover all possible mouse selections
	// (e.g., mouse x < 0 && mouse y > caret line y)
 	if (newCaretOffset < selectionAnchor && selectionAnchor == selection.x) {
		selectionAnchor = doubleClickSelection.y;
	} else if (newCaretOffset > selectionAnchor && selectionAnchor == selection.y) {
		selectionAnchor = doubleClickSelection.x;
	}
	if (x >= 0 && x < getClientArea().width) {
		int wordOffset;
		// find the previous/next word
		if (caretOffset == selection.x) {
			wordOffset = getWordStart(newCaretOffset);
		} else {
			wordOffset = getWordEndNoSpaces(newCaretOffset);
		}
		// mouse word select only on same line mouse cursor is on
		if (content.getLineAtOffset(wordOffset) == line) {
			newCaretOffset = wordOffset;
		}
	}
	return newCaretOffset;
}
/**
 * Scrolls one page down so that the last line (truncated or whole)
 * of the current page becomes the fully visible top line.
 * The caret is scrolled the same number of lines so that its location 
 * relative to the top line remains the same. The exception is the end 
 * of the text where a full page scroll is not possible. In this case 
 * the caret is moved after the last character.
 * <p>
 *
 * @param select whether or not to select the page
 */
void doPageDown(boolean select, int height) {
	// do nothing if in single line mode. fixes 5673
	if (isSingleLine()) {
		return;
	}	
	int oldColumnX = columnX;
	int oldHScrollOffset = horizontalScrollOffset;
	if (isFixedLineHeight()) {
		int lineCount = content.getLineCount();
		int caretLine = getCaretLine();
		if (caretLine < lineCount - 1) {
			int lineHeight = renderer.getLineHeight();
			int lines = height / lineHeight;
			int scrollLines = Math.min(lineCount - caretLine - 1, lines);
			// ensure that scrollLines never gets negative and at leat one 
			// line is scrolled. fixes bug 5602.
			scrollLines = Math.max(1, scrollLines);
			caretOffset = getOffsetAtPoint(columnX, getLinePixel(caretLine + scrollLines));
			if (select) {
				doSelection(ST.COLUMN_NEXT);
			}
			// scroll one page down or to the bottom
			int verticalMaximum = lineCount * getVerticalIncrement();
			int pageSize = getClientArea().height;
			int scrollOffset = getVerticalScrollOffset() + scrollLines * getVerticalIncrement();
			if (scrollOffset + pageSize > verticalMaximum) {
				scrollOffset = verticalMaximum - pageSize;
			}
			if (scrollOffset > getVerticalScrollOffset()) {
				scrollVertical(scrollOffset - getVerticalScrollOffset(), true);
			}
		}
	} else {
		int oldVScrollOffset = getVerticalScrollOffset();
		int caretY = getCaret().getLocation().y;
		boolean scroll = getLineIndex(getClientArea().height) != content.getLineCount() - 1;
		if (scroll) {
			scrollVertical(height, true);
			claimBottomFreeSpace();
		}
		caretY += height - (getVerticalScrollOffset() - oldVScrollOffset);
		caretOffset = getOffsetAtPoint(columnX, caretY);
		setCaretLocation();
		if (select) {
			doSelection(ST.COLUMN_NEXT);
		}
	}	
	showCaret();
	int hScrollChange = oldHScrollOffset - horizontalScrollOffset;
	columnX = oldColumnX + hScrollChange;
}
/**
 * Moves the cursor to the end of the last fully visible line.
 */
void doPageEnd() {
	// go to end of line if in single line mode. fixes 5673
	if (isSingleLine()) {
		doLineEnd();
	} else {
		int line = getBottomIndex();
		int bottomCaretOffset = content.getOffsetAtLine(line) + content.getLine(line).length();
		if (caretOffset < bottomCaretOffset) {
			caretOffset = bottomCaretOffset;
			showCaret();
		}
	}
}
/**
 * Moves the cursor to the beginning of the first fully visible line.
 */
void doPageStart() {
	int topCaretOffset = content.getOffsetAtLine(topIndex);
	if (caretOffset > topCaretOffset) {
		caretOffset = topCaretOffset;
		showCaret();
	}
}
/**
 * Scrolls one page up so that the first line (truncated or whole)
 * of the current page becomes the fully visible last line.
 * The caret is scrolled the same number of lines so that its location 
 * relative to the top line remains the same. The exception is the beginning 
 * of the text where a full page scroll is not possible. In this case the
 * caret is moved in front of the first character.
 */
void doPageUp(boolean select, int height) {
	if (isSingleLine()) {
		return;
	}
	int oldHScrollOffset = horizontalScrollOffset;
	int oldColumnX = columnX;	
	if (isFixedLineHeight()) {
		int caretLine = getCaretLine();	
		if (caretLine > 0) {
			int lineHeight = renderer.getLineHeight();
			int lines = height / lineHeight;
			int scrollLines = Math.max(1, Math.min(caretLine, lines));
			caretLine -= scrollLines;
			caretOffset = getOffsetAtPoint(columnX, getLinePixel(caretLine));
			if (select) {
				doSelection(ST.COLUMN_PREVIOUS);
			}
			// scroll one page up or to the top
			int scrollOffset = Math.max(0, getVerticalScrollOffset() - scrollLines * getVerticalIncrement());
			if (scrollOffset < getVerticalScrollOffset()) {
				scrollVertical(scrollOffset - getVerticalScrollOffset(), true);
			}
		}
	} else {
		int oldVScrollOffset = getVerticalScrollOffset();
		int caretY = getCaret().getLocation().y;	
		int vscroll = Math.min(getVerticalScrollOffset(), height);
		scrollVertical(-vscroll, true);
		caretY -= height - (oldVScrollOffset - getVerticalScrollOffset());
		caretOffset = getOffsetAtPoint(columnX, caretY);
		setCaretLocation();
		if (select) {
			doSelection(ST.COLUMN_PREVIOUS);
		}
	}
	showCaret();
	int hScrollChange = oldHScrollOffset - horizontalScrollOffset;
	columnX = oldColumnX + hScrollChange;
}
/**
 * Updates the selection to extend to the current caret position.
 */
void doSelection(int direction) {
	int redrawStart = -1;
	int redrawEnd = -1;	
	if (selectionAnchor == -1) {
		selectionAnchor = selection.x;
	}	
	if (direction == ST.COLUMN_PREVIOUS) {
		if (caretOffset < selection.x) {
			// grow selection
			redrawEnd = selection.x; 
			redrawStart = selection.x = caretOffset;		
			// check if selection has reversed direction
			if (selection.y != selectionAnchor) {
				redrawEnd = selection.y;
				selection.y = selectionAnchor;
			}
		// test whether selection actually changed. Fixes 1G71EO1
		} else if (selectionAnchor == selection.x && caretOffset < selection.y) {
			// caret moved towards selection anchor (left side of selection). 
			// shrink selection			
			redrawEnd = selection.y;
			redrawStart = selection.y = caretOffset;		
		}
	} else {
		if (caretOffset > selection.y) {
			// grow selection
			redrawStart = selection.y;
			redrawEnd = selection.y = caretOffset;
			// check if selection has reversed direction
			if (selection.x != selectionAnchor) {
				redrawStart = selection.x;				
				selection.x = selectionAnchor;
			}
		// test whether selection actually changed. Fixes 1G71EO1	
		} else if (selectionAnchor == selection.y && caretOffset > selection.x) {
			// caret moved towards selection anchor (right side of selection). 
			// shrink selection			
			redrawStart = selection.x;
			redrawEnd = selection.x = caretOffset;		
		}
	}
	if (redrawStart != -1 && redrawEnd != -1) {
		internalRedrawRange(redrawStart, redrawEnd - redrawStart);
		sendSelectionEvent();
	}
}
/**
 * Moves the caret to the next character or to the beginning of the 
 * next line if the cursor is at the end of a line.
 */
void doSelectionCursorNext() {
	int caretLine = getCaretLine();
	int lineOffset = content.getOffsetAtLine(caretLine);
	int offsetInLine = caretOffset - lineOffset;
	advancing = true;
	if (wordWrap) {
		TextLayout layout = renderer.getTextLayout(caretLine);
		int index = layout.getLineIndex(offsetInLine);
		int[] offsets = layout.getLineOffsets();
		if (offsetInLine == offsets[index + 1] - offsets[index] - 1) {
			advancing = false;
		}
		renderer.disposeTextLayout(layout);
	}
	if (offsetInLine < content.getLine(caretLine).length()) {
		caretOffset = getClusterNext(caretOffset, caretLine);
		showCaret();
	} else if (caretLine < content.getLineCount() - 1 && !isSingleLine()) {
		// only go to next line if not in single line mode. fixes 5673
		caretLine++;		
		caretOffset = content.getOffsetAtLine(caretLine);
		showCaret();
	}
}
/**
 * Moves the caret to the previous character or to the end of the previous 
 * line if the cursor is at the beginning of a line.
 */
void doSelectionCursorPrevious() {
	int caretLine = getCaretLine();
	int lineOffset = content.getOffsetAtLine(caretLine);
	int offsetInLine = caretOffset - lineOffset;
	advancing = false;
	if (offsetInLine > 0) {
		caretOffset = getClusterPrevious(caretOffset, caretLine);
		showCaret();
	} else if (caretLine > 0) {
		caretLine--;
		lineOffset = content.getOffsetAtLine(caretLine);
		caretOffset = lineOffset + content.getLine(caretLine).length();
		showCaret();
	}
}
/**
 * Moves the caret one line down and to the same character offset relative 
 * to the beginning of the line. Moves the caret to the end of the new line 
 * if the new line is shorter than the character offset.
 * Moves the caret to the end of the text if the caret already is on the 
 * last line.
 * Adjusts the selection according to the caret change. This can either add
 * to or subtract from the old selection, depending on the previous selection
 * direction.
 */
void doSelectionLineDown() {
	int oldColumnX = columnX = getPointAtOffset(caretOffset).x;
	doLineDown(true);
	columnX = oldColumnX;
}
/**
 * Moves the caret one line up and to the same character offset relative 
 * to the beginning of the line. Moves the caret to the end of the new line 
 * if the new line is shorter than the character offset.
 * Moves the caret to the beginning of the document if it is already on the
 * first line.
 * Adjusts the selection according to the caret change. This can either add
 * to or subtract from the old selection, depending on the previous selection
 * direction.
 */
void doSelectionLineUp() {
	int oldColumnX = columnX = getPointAtOffset(caretOffset).x;	
	doLineUp(true);	
	columnX = oldColumnX;
}
/**
 * Scrolls one page down so that the last line (truncated or whole)
 * of the current page becomes the fully visible top line.
 * The caret is scrolled the same number of lines so that its location 
 * relative to the top line remains the same. The exception is the end 
 * of the text where a full page scroll is not possible. In this case 
 * the caret is moved after the last character.
 * <p>
 * Adjusts the selection according to the caret change. This can either add
 * to or subtract from the old selection, depending on the previous selection
 * direction.
 * </p>
 */
void doSelectionPageDown(int pixels) {
	int oldColumnX = columnX = getPointAtOffset(caretOffset).x;
	doPageDown(true, pixels);
	columnX = oldColumnX;
}
/**
 * Scrolls one page up so that the first line (truncated or whole)
 * of the current page becomes the fully visible last line.
 * The caret is scrolled the same number of lines so that its location 
 * relative to the top line remains the same. The exception is the beginning 
 * of the text where a full page scroll is not possible. In this case the
 * caret is moved in front of the first character.
 * <p>
 * Adjusts the selection according to the caret change. This can either add
 * to or subtract from the old selection, depending on the previous selection
 * direction.
 * </p>
 */
void doSelectionPageUp(int pixels) {
	int oldColumnX = columnX = getPointAtOffset(caretOffset).x;
	doPageUp(true, pixels);
	columnX = oldColumnX;
}
/**
 * Moves the caret to the end of the next word .
 */
void doSelectionWordNext() {
	int newCaretOffset = getWordEnd(caretOffset);
	// Force symmetrical movement for word next and previous. Fixes 14536
	advancing = false;
	// don't change caret position if in single line mode and the cursor 
	// would be on a different line. fixes 5673
	if (!isSingleLine() || 
		content.getLineAtOffset(caretOffset) == content.getLineAtOffset(newCaretOffset)) {
		caretOffset = newCaretOffset;
		showCaret();
	}
}
/**
 * Moves the caret to the start of the previous word.
 */
void doSelectionWordPrevious() {
	advancing = false;
	caretOffset = getWordStart(caretOffset);
	int caretLine = content.getLineAtOffset(caretOffset);
	// word previous always comes from bottom line. when
	// wrapping lines, stay on bottom line when on line boundary
	if (wordWrap && caretLine < content.getLineCount() - 1 &&
		caretOffset == content.getOffsetAtLine(caretLine + 1)) {
		caretLine++;
	}
	showCaret();
}
/**
 * Moves the caret one character to the left.  Do not go to the previous line.
 * When in a bidi locale and at a R2L character the caret is moved to the 
 * beginning of the R2L segment (visually right) and then one character to the 
 * left (visually left because it's now in a L2R segment).
 */
void doVisualPrevious() {
	caretOffset = getClusterPrevious(caretOffset, getCaretLine());
	showCaret();
}
/**
 * Moves the caret one character to the right.  Do not go to the next line.
 * When in a bidi locale and at a R2L character the caret is moved to the 
 * end of the R2L segment (visually left) and then one character to the 
 * right (visually right because it's now in a L2R segment).
 */
void doVisualNext() {
	caretOffset = getClusterNext(caretOffset, getCaretLine());
	showCaret();
}
/**
 * Moves the caret to the end of the next word.
 * If a selection exists, move the caret to the end of the selection
 * and remove the selection.
 */
void doWordNext() {
	if (selection.y - selection.x > 0) {
		caretOffset = selection.y;
		showCaret();
	} else {
		doSelectionWordNext();
	}
}
/**
 * Moves the caret to the start of the previous word.
 * If a selection exists, move the caret to the start of the selection
 * and remove the selection.
 */
void doWordPrevious() {
	if (selection.y - selection.x > 0) {
		caretOffset = selection.x;
		showCaret();
	} else {
		doSelectionWordPrevious();
	}
}
/** 
 * Ends the autoscroll process.
 */
void endAutoScroll() {
	autoScrollDirection = SWT.NULL;
}
public Color getBackground() {
	checkWidget();
	if (background == null) {
		return getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
	}
	return background;
}
/**
 * Returns the baseline, in pixels. 
 * 
 * @return baseline the baseline
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 3.0
 * @see #getBaseline(int)
 */
public int getBaseline() {
	checkWidget();
	return renderer.getBaseline();
}
/**
 * Returns the baseline at the offset, in pixels. 
 *
 * @param offset the offset
 * @return baseline the baseline
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when the offset is outside the valid range (< 0 or > getCharCount())</li> 
 * </ul> 
 * @since 3.2
 */
public int getBaseline(int offset) {
	checkWidget();
	if (!(0 <= offset && offset < content.getCharCount())) {
		SWT.error(SWT.ERROR_INVALID_RANGE);		
	}
	int lineIndex = content.getLineAtOffset(offset);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	TextLayout layout = renderer.getTextLayout(lineIndex);
	int lineInParagraph = layout.getLineIndex(offset - lineOffset);
	int baseline = layout.getLineMetrics(lineInParagraph).getAscent();
	renderer.disposeTextLayout(layout);
	return baseline;
}
/**
 * Gets the BIDI coloring mode.  When true the BIDI text display
 * algorithm is applied to segments of text that are the same
 * color.
 *
 * @return the current coloring mode
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * <p>
 * @deprecated use BidiSegmentListener instead.
 * </p>
 */
public boolean getBidiColoring() {
	checkWidget();
	return bidiColoring;
}
/** 
 * Returns the index of the last fully visible line.
 * <p>
 *
 * @return index of the last fully visible line.
 */
int getBottomIndex() {
	int bottomIndex;
	if (isFixedLineHeight()) {
		int lineCount = 1;
		int lineHeight = renderer.getLineHeight();
		if (lineHeight != 0) {
			// calculate the number of lines that are fully visible
			int partialTopLineHeight = topIndex * lineHeight - getVerticalScrollOffset();
			lineCount = (getClientArea().height - partialTopLineHeight) / lineHeight;
		}
		bottomIndex = Math.min(content.getLineCount() - 1, topIndex + Math.max(0, lineCount - 1));
	} else {
		int clientAreaHeight = getClientArea().height - bottomMargin;
		bottomIndex = getLineIndex(clientAreaHeight);
		if (bottomIndex > 0) {
			int linePixel = getLinePixel(bottomIndex);
			int lineHeight = renderer.getLineHeight(bottomIndex);
			if (linePixel + lineHeight > clientAreaHeight) {
				bottomIndex--;
			}
		}
	}
	return bottomIndex;
}
/**
 * Returns the caret position relative to the start of the text.
 * <p>
 *
 * @return the caret position relative to the start of the text.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getCaretOffset() {
	checkWidget();
	return caretOffset;
}
/**
 * Returns the caret width.
 * <p>
 *
 * @return the caret width, 0 if caret is null.
 */
int getCaretWidth() {
	Caret caret = getCaret();
	if (caret == null) return 0;
	return caret.getSize().x;
}
Object getClipboardContent(int clipboardType) {
	TextTransfer plainTextTransfer = TextTransfer.getInstance();
	return clipboard.getContents(plainTextTransfer, clipboardType);
}
int getClusterNext(int offset, int lineIndex) {
	int lineOffset = content.getOffsetAtLine(lineIndex);	
	TextLayout layout = renderer.getTextLayout(lineIndex);
	offset -= lineOffset;
	offset = layout.getNextOffset(offset, SWT.MOVEMENT_CLUSTER);
	offset += lineOffset;
	renderer.disposeTextLayout(layout);
	return offset;
}
int getClusterPrevious(int offset, int lineIndex) {
	int lineOffset = content.getOffsetAtLine(lineIndex);	
	TextLayout layout = renderer.getTextLayout(lineIndex);
	offset -= lineOffset;
	offset = layout.getPreviousOffset(offset, SWT.MOVEMENT_CLUSTER);
	offset += lineOffset;
	renderer.disposeTextLayout(layout);
	return offset;
}
/**
 * Returns the content implementation that is used for text storage
 * or null if no user defined content implementation has been set.
 * <p>
 *
 * @return content implementation that is used for text storage or null 
 * if no user defined content implementation has been set.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public StyledTextContent getContent() {
	checkWidget();
	return content;
}
/** 
 * Returns whether the widget implements double click mouse behavior.
 * <p>
 *
 * @return true if double clicking a word selects the word, false if double clicks
 * have the same effect as regular mouse clicks
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public boolean getDoubleClickEnabled() {
	checkWidget();
	return doubleClickEnabled;
}
/**
 * Returns whether the widget content can be edited.
 * <p>
 *
 * @return true if content can be edited, false otherwise
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public boolean getEditable() {
	checkWidget();
	return editable;
}
public Color getForeground() {
	checkWidget();
	if (foreground == null) {
		return getDisplay().getSystemColor(SWT.COLOR_LIST_FOREGROUND);
	}
	return foreground;
}
/** 
 * Returns the horizontal scroll increment.
 * <p>
 *
 * @return horizontal scroll increment.
 */
int getHorizontalIncrement() {
	GC gc = new GC(this);
	int increment = gc.getFontMetrics().getAverageCharWidth();
	gc.dispose();
	return increment;
}
/** 
 * Returns the horizontal scroll offset relative to the start of the line.
 * <p>
 *
 * @return horizontal scroll offset relative to the start of the line,
 * measured in character increments starting at 0, if > 0 the content is scrolled
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getHorizontalIndex() {
	checkWidget();
	return horizontalScrollOffset / getHorizontalIncrement();
}
/** 
 * Returns the horizontal scroll offset relative to the start of the line.
 * <p>
 *
 * @return the horizontal scroll offset relative to the start of the line,
 * measured in pixel starting at 0, if > 0 the content is scrolled.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getHorizontalPixel() {
	checkWidget();
	return horizontalScrollOffset;
}
/**
 * Returns the default indent of the receiver
 * <p>
 * 
 * @return the line indent
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @see #getLineIndent(int)
 * @since 3.2
 */
public int getIndent() {
	checkWidget();
	return indent;
}
/**
 * Returns the default justify of the receiver
 * 
 * @return justify
 * 
 * @since 3.2
 */
public boolean getJustify() {
	checkWidget();
	return justify;
}
/** 
 * Returns the action assigned to the key.
 * Returns SWT.NULL if there is no action associated with the key.
 * <p>
 *
 * @param key a key code defined in SWT.java or a character. 
 * 	Optionally ORd with a state mask.  Preferred state masks are one or more of
 *  SWT.MOD1, SWT.MOD2, SWT.MOD3, since these masks account for modifier platform 
 *  differences.  However, there may be cases where using the specific state masks
 *  (i.e., SWT.CTRL, SWT.SHIFT, SWT.ALT, SWT.COMMAND) makes sense.
 * @return one of the predefined actions defined in ST.java or SWT.NULL 
 * 	if there is no action associated with the key.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getKeyBinding(int key) {
	checkWidget();
	Integer action = (Integer) keyActionMap.get(new Integer(key));	
	return action == null ? SWT.NULL : action.intValue();
}
/**
 * Gets the number of characters.
 * <p>
 *
 * @return number of characters in the widget
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getCharCount() {
	checkWidget();
	return content.getCharCount();
}
/**
 * Returns the alignment of the line at the given index.
 * <p>
 * 
 * @param index the index of the line
 * @return the line alignment
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT when the index is invalid</li>
 * </ul>
 * @see #getAlignment()
 * @since 3.2
 */
public int getLineAlignment(int index) {
	checkWidget();
	if (index < 0 || index > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	return renderer.getLineAlignment(index, alignment);
}
/**
 * Returns the line at the specified offset in the text
 * where 0 &lt= offset &lt= getCharCount() so that getLineAtOffset(getCharCount())
 * returns the line of the insert location.
 *
 * @param offset offset relative to the start of the content. 
 * 	0 <= offset <= getCharCount()
 * @return line at the specified offset in the text
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when the offset is outside the valid range (< 0 or > getCharCount())</li> 
 * </ul>
 */
public int getLineAtOffset(int offset) {
	checkWidget();	
	if (offset < 0 || offset > getCharCount()) {
		SWT.error(SWT.ERROR_INVALID_RANGE);		
	}
	return content.getLineAtOffset(offset);
}
/**
 * Returns the background color of the line at the given index.
 * Returns null if a LineBackgroundListener has been set or if no background 
 * color has been specified for the line. Should not be called if a
 * LineBackgroundListener has been set since the listener maintains the
 * line background colors.
 * 
 * @param index the index of the line
 * @return the background color of the line at the given index.
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT when the index is invalid</li>
 * </ul>
 */
public Color getLineBackground(int index) {
	checkWidget();
	if (index < 0 || index > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	return isListening(LineGetBackground) ? null : renderer.getLineBackground(index, null);
}
/**
 * Returns the bullet of the line at the given index.
 * <p>
 * 
 * @param index the index of the line
 * @return the line bullet
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT when the index is invalid</li>
 * </ul>
 * @since 3.2
 */
Bullet getLineBullet(int index) {
	checkWidget();
	if (index < 0 || index > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	return isListening(LineGetStyle) ? null : renderer.getLineBullet(index, null);
}
/**
 * Returns the line background data for the given line or null if 
 * there is none.
 * <p>
 * @param lineOffset offset of the line start relative to the start
 * 	of the content.
 * @param line line to get line background data for
 * @return line background data for the given line.
 */
StyledTextEvent getLineBackgroundData(int lineOffset, String line) {
	return sendLineEvent(LineGetBackground, lineOffset, line);
}
/** 
 * Gets the number of text lines.
 * <p>
 *
 * @return the number of lines in the widget
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getLineCount() {
	checkWidget();
	return content.getLineCount();
}
/**
 * Returns the number of lines that can be completely displayed in the 
 * widget client area.
 * <p>
 *
 * @return number of lines that can be completely displayed in the widget 
 * 	client area.
 */
int getLineCountWhole() {
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		return lineHeight != 0 ? getClientArea().height / lineHeight : 1;
	}
	return getBottomIndex() - topIndex + 1;
}
/**
 * Returns the line delimiter used for entering new lines by key down
 * or paste operation.
 * <p>
 *
 * @return line delimiter used for entering new lines by key down
 * or paste operation.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public String getLineDelimiter() {
	checkWidget();
	return content.getLineDelimiter();
}
/**
 * Returns the line height.
 * <p>
 *
 * @return line height in pixel.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @see #getLineHeight(int)
 */
public int getLineHeight() {
	checkWidget();
	return renderer.getLineHeight();
}
/**
 * Returns the line height at the given offset
 * <p>
 *
 * @param offset the offset 
 * @return line height in pixel.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when the offset is outside the valid range (< 0 or > getCharCount())</li> 
 * </ul> 
 * @since 3.2
 */
public int getLineHeight(int offset) {
	checkWidget();
	if (!(0 <= offset && offset < content.getCharCount())) {
		SWT.error(SWT.ERROR_INVALID_RANGE);		
	}
	int lineIndex = content.getLineAtOffset(offset);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	TextLayout layout = renderer.getTextLayout(lineIndex);
	int lineInParagraph = layout.getLineIndex(offset - lineOffset);
	int height = layout.getLineBounds(lineInParagraph).height;
	renderer.disposeTextLayout(layout);
	return height;
}
/**
 * Returns the indent of the line at the given index.
 * <p>
 * 
 * @param index the index of the line
 * @return the line indent
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT when the index is invalid</li>
 * </ul>
 * @see #getIndent()
 * @since 3.2
 */
public int getLineIndent(int index) {
	checkWidget();
	if (index < 0 || index > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	return isListening(LineGetStyle) ? 0 : renderer.getLineIndent(index, indent);
}
/**
 * Returns the justify of the line at the given index.
 * <p>
 * 
 * @param index the index of the line
 * @return the line justify
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT when the index is invalid</li>
 * </ul>
 * @see #getJustify()
 * @since 3.2
 */
public boolean getLineJustify(int index) {
	checkWidget();
	if (index < 0 || index > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	return isListening(LineGetStyle) ? false : renderer.getLineJustify(index, justify);	
}
/**
 * Returns the line spacing of the receiver
 * <p>
 * 
 * @return the line spacing 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 3.2
 */
public int getLineSpacing() {
	checkWidget();
	return lineSpacing;
}
/**
 * Returns the line style data for the given line or null if there is 
 * none. If there is a LineStyleListener but it does not set any styles, 
 * the StyledTextEvent.styles field will be initialized to an empty 
 * array.
 * <p>
 * 
 * @param lineOffset offset of the line start relative to the start of 
 * 	the content.
 * @param line line to get line styles for
 * @return line style data for the given line. Styles may start before 
 * 	line start and end after line end
 */
StyledTextEvent getLineStyleData(int lineOffset, String line) {
	return sendLineEvent(LineGetStyle, lineOffset, line);
}
/**
 * Returns the top pixel, relative to the client area, for a line.
 * @param lineIndex, the line index, the max value is lineCount. When
 * lineIndex == lineCount it returns the bottomPixel of the last line 
 */
int getLinePixel(int lineIndex) {
	int lineCount = content.getLineCount();
	if (lineIndex > lineCount) {
		lineIndex = lineCount;
	}
	if (lineIndex < 0) {
		lineIndex = 0;
	}
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		return lineIndex * lineHeight - getVerticalScrollOffset() + topMargin;
	}
	
	if (lineIndex == topIndex) return partialHeight + topMargin;	
	int height = partialHeight;
	if (lineIndex > topIndex) {
		for (int i = topIndex; i < lineIndex; i++) {
			height += renderer.getLineHeight(i);
		}
	} else {
		for (int i = topIndex - 1; i >= lineIndex; i--) {
			height -= renderer.getLineHeight(i);
		}
	}
	return height + topMargin;
}
/**
 * Returns the line index for a y, relative to the client area.
 */
int getLineIndex(int y) {
	y -= topMargin;
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		int lineIndex = (y + getVerticalScrollOffset()) / lineHeight;
		int lineCount = content.getLineCount();
		if (lineIndex >= lineCount) {
			lineIndex = lineCount - 1;
		}
		return lineIndex;
	}
	
	int line = topIndex;
	if (y < 0) {
		if (line == 0) return 0;
		line--;
		y += renderer.getLineHeight(line) - partialHeight;
		while (y < 0 && line > 0) {
			line--;
			y += renderer.getLineHeight(line);
		}
	} else {
		if (partialHeight > y) return line - 1;
		y -= partialHeight;
		int lineCount = content.getLineCount();
		while (line < lineCount - 1) {
			int lineHeight = renderer.getLineHeight(line);
			if (lineHeight > y) break;
			y -= lineHeight;
			line++;
		}
	}
	return line;
}
/**
 * Returns the x, y location of the upper left corner of the character 
 * bounding box at the specified offset in the text. The point is 
 * relative to the upper left corner of the widget client area.
 * <p>
 *
 * @param offset offset relative to the start of the content. 
 * 	0 <= offset <= getCharCount()
 * @return x, y location of the upper left corner of the character 
 * 	bounding box at the specified offset in the text.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when the offset is outside the valid range (< 0 or > getCharCount())</li> 
 * </ul>
 */
public Point getLocationAtOffset(int offset) {
	checkWidget();
	if (offset < 0 || offset > getCharCount()) {
		SWT.error(SWT.ERROR_INVALID_RANGE);		
	}
	return getPointAtOffset(offset);
}
/**
 * Returns the character offset of the first character of the given line.
 * <p>
 *
 * @param lineIndex index of the line, 0 based relative to the first 
 * 	line in the content. 0 <= lineIndex < getLineCount(), except
 * 	lineIndex may always be 0
 * @return offset offset of the first character of the line, relative to
 * 	the beginning of the document. The first character of the document is
 *	at offset 0.  
 *  When there are not any lines, getOffsetAtLine(0) is a valid call that 
 * 	answers 0.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when the offset is outside the valid range (< 0 or > getCharCount())</li> 
 * </ul>
 * @since 2.0
 */
public int getOffsetAtLine(int lineIndex) {
	checkWidget();
	if (lineIndex < 0 || 
		(lineIndex > 0 && lineIndex >= content.getLineCount())) {
		SWT.error(SWT.ERROR_INVALID_RANGE);		
	}
	return content.getOffsetAtLine(lineIndex);
}
/**
 * Returns the offset of the character at the given location relative 
 * to the first character in the document.
 * The return value reflects the character offset that the caret will
 * be placed at if a mouse click occurred at the specified location.
 * If the x coordinate of the location is beyond the center of a character
 * the returned offset will be behind the character.
 * <p>
 *
 * @param point the origin of character bounding box relative to 
 * 	the origin of the widget client area.
 * @return offset of the character at the given location relative 
 * 	to the first character in the document.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_NULL_ARGUMENT when point is null</li>
 *   <li>ERROR_INVALID_ARGUMENT when there is no character at the specified location</li>
 * </ul>
 */
public int getOffsetAtLocation(Point point) {
	checkWidget();
	if (point == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	// is y above first line or is x before first column?
	if (point.y + getVerticalScrollOffset() < 0 || point.x + horizontalScrollOffset < 0) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	int bottomIndex = getLineIndex(getClientArea().height);
	int height = getLinePixel(bottomIndex) + renderer.getLineHeight(bottomIndex);
	if (point.y > height) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);		
	}
	int lineIndex = getLineIndex(point.y);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	TextLayout layout = renderer.getTextLayout(lineIndex);	
	int[] trailing = new int[1];
	int x = point.x + horizontalScrollOffset - leftMargin ;
	int y = point.y - getLinePixel(lineIndex);
	int offsetInLine = layout.getOffset(x, y, trailing);
	String line = content.getLine(lineIndex);
	if (offsetInLine != line.length() - 1) {
		offsetInLine = Math.min(line.length(), offsetInLine + trailing[0]);		
	}
	Rectangle rect = layout.getLineBounds(layout.getLineIndex(offsetInLine));
	renderer.disposeTextLayout(layout);
	if (x > rect.x + rect.width) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	return lineOffset + offsetInLine;
}
/**
 * Returns the offset at the specified x location in the specified line.
 * <p>
 *
 * @param x	x location of the mouse location
 * @param line	line the mouse location is in
 * @return the offset at the specified x location in the specified line,
 * 	relative to the beginning of the document
 */
int getOffsetAtPoint(int x, int y) {
	int lineIndex = getLineIndex(y);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	TextLayout layout = renderer.getTextLayout(lineIndex);
	x += horizontalScrollOffset - leftMargin;
	y -= getLinePixel(lineIndex);
	int[] trailing = new int[1];
	if (wordWrap) {
		//TODO
	}
	
	int offsetInLine = layout.getOffset(x, y, trailing);
	advancing = false;
	if (trailing[0] != 0) {
		int lineInParagraph = layout.getLineIndex(offsetInLine);
		int lineLength;
		if (lineInParagraph + 1 == layout.getLineCount()) {
			lineLength = layout.getLineOffsets()[lineInParagraph + 1];
		} else {
			lineLength = Math.max(0, layout.getLineOffsets()[lineInParagraph + 1] - 1);
		}
		if (offsetInLine + trailing[0] >= lineLength) {
			offsetInLine = lineLength;
			advancing = true;
		} else {
			String line = content.getLine(lineIndex);			
			int level;
			int offset = offsetInLine;
			while (offset > 0 && Character.isDigit(line.charAt(offset))) offset--;
			if (offset == 0 && Character.isDigit(line.charAt(offset))) {
				level = isMirrored() ? 1 : 0;
			} else {
				level = layout.getLevel(offset) & 0x1;
			}
			offsetInLine += trailing[0];
			int trailingLevel = layout.getLevel(offsetInLine) & 0x1;
			advancing  = (level ^ trailingLevel) != 0;
		}
	}
	renderer.disposeTextLayout(layout);
	return offsetInLine + lineOffset;
}
/**
 * Return the orientation of the receiver.
 *
 * @return the orientation style
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * 
 * @since 2.1.2
 */
public int getOrientation () {
	checkWidget();
	return isMirrored() ? SWT.RIGHT_TO_LEFT : SWT.LEFT_TO_RIGHT;
}
/** 
 * Returns the index of the last partially visible line.
 *
 * @return index of the last partially visible line.
 */
int getPartialBottomIndex() {
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		int partialLineCount = Compatibility.ceil(getClientArea().height, lineHeight);
		return Math.min(content.getLineCount(), topIndex + partialLineCount) - 1;
	}
	return getLineIndex(getClientArea().height - bottomMargin);
}
/** 
 * Returns the index of the first partially visible line.
 *
 * @return index of the first partially visible line.
 */
int getPartialTopIndex() {
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		return getVerticalScrollOffset() / lineHeight;
	}
	return partialHeight == 0 ? topIndex : topIndex - 1;
}
/**
 * Returns the content in the specified range using the platform line 
 * delimiter to separate lines.
 * <p>
 *
 * @param writer the TextWriter to write line text into
 * @return the content in the specified range using the platform line 
 * 	delimiter to separate lines as written by the specified TextWriter.
 */
String getPlatformDelimitedText(TextWriter writer) {
	int end = writer.getStart() + writer.getCharCount();
	int startLine = content.getLineAtOffset(writer.getStart());
	int endLine = content.getLineAtOffset(end);
	String endLineText = content.getLine(endLine);
	int endLineOffset = content.getOffsetAtLine(endLine);
	
	for (int i = startLine; i <= endLine; i++) {
		writer.writeLine(content.getLine(i), content.getOffsetAtLine(i));
		if (i < endLine) {
			writer.writeLineDelimiter(PlatformLineDelimiter);
		}
	}
	if (end > endLineOffset + endLineText.length()) {
		writer.writeLineDelimiter(PlatformLineDelimiter);
	}
	writer.close();
	return writer.toString();
}
/**
 * Returns the ranges.
 * Returns an empty array if a LineStyleListener has been set. 
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * <p>
 *
 * @param start the start offset of the style ranges to return
 * @param length the number of style ranges to return
 * 
 * @return the ranges or an empty array if a LineStyleListener has been set.
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * 
 * @since 3.2
 */
public int[] getRanges(int start, int length) {
	checkWidget();
	int contentLength = getCharCount();
	int end = start + length;
	if (start > end || start < 0 || end > contentLength) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	if (!isListening(LineGetStyle)) {
		int[] ranges = renderer.getRanges(start, length);
		if (ranges != null) return ranges;
	}
	return new int[0];
}
/**
 * Returns the selection.
 * <p>
 * Text selections are specified in terms of caret positions.  In a text
 * widget that contains N characters, there are N+1 caret positions, 
 * ranging from 0..N
 * <p>
 *
 * @return start and end of the selection, x is the offset of the first 
 * 	selected character, y is the offset after the last selected character.
 *  The selection values returned are visual (i.e., x will always always be   
 *  <= y).  To determine if a selection is right-to-left (RtoL) vs. left-to-right 
 *  (LtoR), compare the caretOffset to the start and end of the selection 
 *  (e.g., caretOffset == start of selection implies that the selection is RtoL).
 * @see #getSelectionRange
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public Point getSelection() {
	checkWidget();
	return new Point(selection.x, selection.y);
}
/**
 * Returns the selection.
 * <p>
 *
 * @return start and length of the selection, x is the offset of the 
 * 	first selected character, relative to the first character of the 
 * 	widget content. y is the length of the selection. 
 *  The selection values returned are visual (i.e., length will always always be   
 *  positive).  To determine if a selection is right-to-left (RtoL) vs. left-to-right 
 *  (LtoR), compare the caretOffset to the start and end of the selection 
 *  (e.g., caretOffset == start of selection implies that the selection is RtoL).
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public Point getSelectionRange() {
	checkWidget();
	return new Point(selection.x, selection.y - selection.x);
}
/**
 * Returns the receiver's selection background color.
 *
 * @return the selection background color
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 2.1
 */
public Color getSelectionBackground() {
	checkWidget();
	if (selectionBackground == null) {
		return getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
	}
	return selectionBackground;
}
/**
 * Gets the number of selected characters.
 * <p>
 *
 * @return the number of selected characters.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getSelectionCount() {
	checkWidget();
	return getSelectionRange().y;
}
/**
 * Returns the receiver's selection foreground color.
 *
 * @return the selection foreground color
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 2.1
 */
public Color getSelectionForeground() {
	checkWidget();
	if (selectionForeground == null) {
		return getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
	}
	return selectionForeground;
}
/**
 * Returns the selected text.
 * <p>
 *
 * @return selected text, or an empty String if there is no selection.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public String getSelectionText() {
	checkWidget();
	return content.getTextRange(selection.x, selection.y - selection.x);
}
public int getStyle() {
	int style = super.getStyle();
	style &= ~(SWT.LEFT_TO_RIGHT | SWT.RIGHT_TO_LEFT | SWT.MIRRORED);
	if (isMirrored()) {
		style |= SWT.RIGHT_TO_LEFT | SWT.MIRRORED;
	} else {
		style |= SWT.LEFT_TO_RIGHT;
	}
	return style;
}

/**
 * Returns the text segments that should be treated as if they 
 * had a different direction than the surrounding text.
 * <p>
 *
 * @param lineOffset offset of the first character in the line. 
 * 	0 based from the beginning of the document.
 * @param line text of the line to specify bidi segments for
 * @return text segments that should be treated as if they had a
 * 	different direction than the surrounding text. Only the start 
 * 	index of a segment is specified, relative to the start of the 
 * 	line. Always starts with 0 and ends with the line length. 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the segment indices returned 
 * 		by the listener do not start with 0, are not in ascending order,
 * 		exceed the line length or have duplicates</li>
 * </ul>
 */
int [] getBidiSegments(int lineOffset, String line) {
	if (!isBidi()) return null;
	if (!isListening(LineGetSegments)) {
		return getBidiSegmentsCompatibility(line, lineOffset);
	}
	StyledTextEvent event = sendLineEvent(LineGetSegments, lineOffset, line);
	int lineLength = line.length();
	int[] segments;
	if (event == null || event.segments == null || event.segments.length == 0) {
		segments = new int[] {0, lineLength};
	} else {
		int segmentCount = event.segments.length;
		
		// test segment index consistency
		if (event.segments[0] != 0) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		} 	
		for (int i = 1; i < segmentCount; i++) {
			if (event.segments[i] <= event.segments[i - 1] || event.segments[i] > lineLength) {
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			} 	
		}
		// ensure that last segment index is line end offset
		if (event.segments[segmentCount - 1] != lineLength) {
			segments = new int[segmentCount + 1];
			System.arraycopy(event.segments, 0, segments, 0, segmentCount);
			segments[segmentCount] = lineLength;
		} else {
			segments = event.segments;
		}
	}
	return segments;
}
/**
 * @see #getBidiSegments
 * Supports deprecated setBidiColoring API. Remove when API is removed.
 */
int [] getBidiSegmentsCompatibility(String line, int lineOffset) {
	int lineLength = line.length();
	if (!bidiColoring) {
		return new int[] {0, lineLength};
	}
	StyleRange [] styles = null;
	StyledTextEvent event = getLineStyleData(lineOffset, line);
	if (event != null) {
		styles = event.styles;
	} else {
		styles = renderer.getStyleRanges(lineOffset, lineLength, true);
	}
	if (styles == null || styles.length == 0) {
		return new int[] {0, lineLength};
	}
	int k=0, count = 1;
	while (k < styles.length && styles[k].start == 0 && styles[k].length == lineLength) {
		k++;
	}
	int[] offsets = new int[(styles.length - k) * 2 + 2];
	for (int i = k; i < styles.length; i++) {
		StyleRange style = styles[i];
		int styleLineStart = Math.max(style.start - lineOffset, 0);
		int styleLineEnd = Math.max(style.start + style.length - lineOffset, styleLineStart);
		styleLineEnd = Math.min (styleLineEnd, line.length ());
		if (i > 0 && count > 1 &&
			((styleLineStart >= offsets[count-2] && styleLineStart <= offsets[count-1]) ||
			 (styleLineEnd >= offsets[count-2] && styleLineEnd <= offsets[count-1])) &&
			 style.similarTo(styles[i-1])) {
			offsets[count-2] = Math.min(offsets[count-2], styleLineStart);
			offsets[count-1] = Math.max(offsets[count-1], styleLineEnd);
		} else {
			if (styleLineStart > offsets[count - 1]) {
				offsets[count] = styleLineStart;
				count++;
			}
			offsets[count] = styleLineEnd;
			count++;
		}
	}
	// add offset for last non-colored segment in line, if any
	if (lineLength > offsets[count-1]) {
		offsets [count] = lineLength;
		count++;
	}
	if (count == offsets.length) {
		return offsets;
	}
	int [] result = new int [count];
	System.arraycopy (offsets, 0, result, 0, count);
	return result;
}
/**
 * Returns the style range at the given offset.
 * Returns null if a LineStyleListener has been set or if a style is not set
 * for the offset. 
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * <p>
 *
 * @param offset the offset to return the style for. 
 * 	0 <= offset < getCharCount() must be true.
 * @return a StyleRange with start == offset and length == 1, indicating
 * 	the style at the given offset. null if a LineStyleListener has been set 
 * 	or if a style is not set for the given offset.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when the offset is invalid</li>
 * </ul>
 */
public StyleRange getStyleRangeAtOffset(int offset) {
	checkWidget();
	if (offset < 0 || offset >= getCharCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	if (!isListening(LineGetStyle)) {
		StyleRange[] ranges = renderer.getStyleRanges(offset, 1, true);
		if (ranges != null) return ranges[0];
	}
	return null;
}
/**
 * Returns the styles.
 * Returns an empty array if a LineStyleListener has been set. 
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * <p>
 *
 * @return the styles or an empty array if a LineStyleListener has been set.
  *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public StyleRange[] getStyleRanges() {
	checkWidget();
	return getStyleRanges(0, content.getCharCount (), true);
}
/**
 * Returns the styles.
 * Returns an empty array if a LineStyleListener has been set. 
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * <p>
 *
 * @param includeRanges 
 * @return the styles or an empty array if a LineStyleListener has been set.
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * 
 * @since 3.2
 */
public StyleRange[] getStyleRanges(boolean includeRanges) {
	checkWidget();
	return getStyleRanges(0, content.getCharCount (), includeRanges);
}
/**
 * Returns the styles for the given text range.
 * Returns an empty array if a LineStyleListener has been set. 
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * 
 * @param start the start offset of the style ranges to return
 * @param length the number of style ranges to return
 *
 * @return the styles or an empty array if a LineStyleListener has 
 *  been set.  The returned styles will reflect the given range.  The first 
 *  returned <code>StyleRange</code> will have a starting offset >= start 
 *  and the last returned <code>StyleRange</code> will have an ending 
 *  offset <= start + length - 1
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when start and/or end are outside the widget content</li> 
 * </ul>
 * 
 * @since 3.0
 */
public StyleRange[] getStyleRanges(int start, int length) {
	checkWidget();
	return getStyleRanges(start, length, true);
}
/**
 * Returns the styles for the given text range.
 * Returns an empty array if a LineStyleListener has been set. 
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * 
 * @param start the start offset of the style ranges to return
 * @param length the number of style ranges to return
 * @param includeRanges
 *
 * @return the styles or an empty array if a LineStyleListener has 
 *  been set.  The returned styles will reflect the given range.  The first 
 *  returned <code>StyleRange</code> will have a starting offset >= start 
 *  and the last returned <code>StyleRange</code> will have an ending 
 *  offset <= start + length - 1
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when start and/or end are outside the widget content</li> 
 * </ul>
 * 
 * @since 3.2
 */
public StyleRange[] getStyleRanges(int start, int length, boolean includeRanges) {
	checkWidget();
	int contentLength = getCharCount();
	int end = start + length;
	if (start > end || start < 0 || end > contentLength) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	if (!isListening(LineGetStyle)) {
		StyleRange[] ranges = renderer.getStyleRanges(start, length, includeRanges);
		if (ranges != null) return ranges;
	}
	return new StyleRange[0];
}
/**
 * Returns the tab width measured in characters.
 *
 * @return tab width measured in characters
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getTabs() {
	checkWidget();
	return tabLength;
}
/**
 * Returns a copy of the widget content.
 * <p>
 *
 * @return copy of the widget content
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public String getText() {
	checkWidget();
	return content.getTextRange(0, getCharCount());
}
/**
 * Returns the widget content between the two offsets.
 * <p>
 *
 * @param start offset of the first character in the returned String
 * @param end offset of the last character in the returned String 
 * @return widget content starting at start and ending at end
 * @see #getTextRange(int,int)
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when start and/or end are outside the widget content</li> 
 * </ul>
 */
public String getText(int start, int end) {
	checkWidget();
	int contentLength = getCharCount();
	if (start < 0 || start >= contentLength || end < 0 || end >= contentLength || start > end) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	return content.getTextRange(start, end - start + 1);
}
/**
 * Returns the smallest bounding rectangle that includes the characters between two offsets.
 * <p>
 *
 * @param start offset of the first character included in the bounding box
 * @param end offset of the last character included in the bounding box 
 * @return bounding box of the text between start and end
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when start and/or end are outside the widget content</li> 
 * </ul>
 * @since 3.1
 */
public Rectangle getTextBounds(int start, int end) {
	checkWidget();	
	int contentLength = getCharCount();	
	if (start < 0 || start >= contentLength || end < 0 || end >= contentLength || start > end) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	int lineStart = content.getLineAtOffset(start);
	int lineEnd = content.getLineAtOffset(end);
	Rectangle rect;
	int y = getLinePixel(lineStart);
	int height = 0;
	int left = 0x7fffffff, right = 0;
	for (int i = lineStart; i <= lineEnd; i++) {
		int lineOffset = content.getOffsetAtLine(i);		
		TextLayout layout = renderer.getTextLayout(i);
		if (i == lineStart && i == lineEnd) {
			rect = layout.getBounds(start - lineOffset, end - lineOffset);
		} else if (i == lineStart) {
			String line = content.getLine(i);
			rect = layout.getBounds(start - lineOffset, line.length());
		} else if (i == lineEnd) {
			rect = layout.getBounds(0, end - lineOffset);
		} else {
			rect = layout.getBounds();
		}
		left = Math.min (left, rect.x);
		right = Math.max (right, rect.x + rect.width);
		height += rect.height;
		renderer.disposeTextLayout(layout);
	}
	rect = new Rectangle (left, y, right-left, height);
	rect.x += leftMargin - horizontalScrollOffset;
	return rect;
}
/**
 * Returns the widget content starting at start for length characters.
 * <p>
 *
 * @param start offset of the first character in the returned String
 * @param length number of characters to return 
 * @return widget content starting at start and extending length characters.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when start and/or length are outside the widget content</li> 
 * </ul>
 */
public String getTextRange(int start, int length) {
	checkWidget();
	int contentLength = getCharCount();
	int end = start + length;
	if (start > end || start < 0 || end > contentLength) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}	
	return content.getTextRange(start, length);
}
/**
 * Returns the maximum number of characters that the receiver is capable of holding.
 * 
 * @return the text limit
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getTextLimit() {
	checkWidget();
	return textLimit;
}
/**
 * Gets the top index.  The top index is the index of the fully visible line that
 * is currently at the top of the widget or the topmost partially visible line if 
 * no line is fully visible. 
 * The top index changes when the widget is scrolled. Indexing is zero based.
 * <p>
 *
 * @return the index of the top line
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getTopIndex() {
	checkWidget();
	return topIndex;
}
/**
 * Gets the top pixel.  The top pixel is the pixel position of the line that is 
 * currently at the top of the widget.The text widget can be scrolled by pixels 
 * by dragging the scroll thumb so that a partial line may be displayed at the top 
 * the widget.  The top pixel changes when the widget is scrolled.  The top pixel 
 * does not include the widget trimming.
 * <p>
 *
 * @return pixel position of the top line
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public int getTopPixel() {
	checkWidget();
	return getVerticalScrollOffset();
}
/** 
 * Returns the vertical scroll increment.
 * <p>
 *
 * @return vertical scroll increment.
 */
int getVerticalIncrement() {
	return renderer.getLineHeight();
}
int getVerticalScrollOffset() {
	if (verticalScrollOffset == -1) {
		renderer.calculate(0, topIndex);
		int height = 0;
		for (int i = 0; i < topIndex; i++) {
			height += renderer.getLineHeight(i);
		}
		height -= partialHeight;
		verticalScrollOffset = height;
	}
	return verticalScrollOffset;
}
int getCaretDirection() {
	if (!isBidiCaret()) return SWT.DEFAULT;
	if (!updateCaretDirection && caretDirection != SWT.NULL) return caretDirection;
	updateCaretDirection = false;
	int caretLine = getCaretLine();
	int lineOffset = content.getOffsetAtLine(caretLine);
	String line = content.getLine(caretLine);
	int offset = caretOffset - lineOffset;
	int lineLength = line.length();
	if (lineLength == 0) return isMirrored() ? SWT.RIGHT : SWT.LEFT;
	if (advancing && offset > 0) offset--;
	if (offset == lineLength && offset > 0) offset--;
	while (offset > 0 && Character.isDigit(line.charAt(offset))) offset--;
	if (offset == 0 && Character.isDigit(line.charAt(offset))) {
		return isMirrored() ? SWT.RIGHT : SWT.LEFT;
	}
	TextLayout layout = renderer.getTextLayout(caretLine);
	int level = layout.getLevel(offset);
	renderer.disposeTextLayout(layout);
	return ((level & 1) != 0) ? SWT.RIGHT : SWT.LEFT;
}
/**
 * Returns the index of the line the caret is on.
 * When in word wrap mode and at the end of one wrapped line/ 
 * beginning of the continuing wrapped line the caret offset
 * is not sufficient to determine the caret line.
 * 
 * @return the index of the line the caret is on.
 */
int getCaretLine() {
	int caretLine = content.getLineAtOffset(caretOffset);
//	if (wordWrap && columnX <= leftMargin &&
//		caretLine < content.getLineCount() - 1 &&
//		caretOffset == content.getOffsetAtLine(caretLine + 1)) {
//		caretLine++;
//	}
	return caretLine;
}
int getWrapWidth () {
	if (wordWrap && !isSingleLine()) {
		int width = getClientArea().width - leftMargin - rightMargin; 
		return width > 0 ? width : -1;
	}
	return -1;
}
/**
 * Returns the offset of the character after the word at the specified
 * offset.
 * <p>
 * There are two classes of words formed by a sequence of characters:
 * <ul>
 * <li>from 0-9 and A-z (ASCII 48-57 and 65-122)
 * <li>every other character except line breaks
 * </ul>
 * </p>
 * <p>
 * Space characters ' ' (ASCII 20) are special as they are treated as
 * part of the word leading up to the space character.  Line breaks are 
 * treated as one word.
 * </p>
 */
int getWordEnd(int offset) {
	if (offset >= getCharCount()) {
		return offset;
	}
	int lineIndex = content.getLineAtOffset(offset);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	int lineLength = content.getLine(lineIndex).length();
	if (offset == lineOffset + lineLength) {
		offset = content.getOffsetAtLine(lineIndex + 1);
	} else {
		TextLayout layout = renderer.getTextLayout(lineIndex);
		offset -= lineOffset;
		offset = layout.getNextOffset(offset, SWT.MOVEMENT_WORD);
		offset += lineOffset;
		renderer.disposeTextLayout(layout);
	}
	return offset;
}
/**
 * Returns the offset of the character after the word at the specified
 * offset.
 * <p>
 * There are two classes of words formed by a sequence of characters:
 * <ul>
 * <li>from 0-9 and A-z (ASCII 48-57 and 65-122)
 * <li>every other character except line breaks
 * </ul>
 * </p>
 * <p>
 * Spaces are ignored and do not represent a word.  Line breaks are treated 
 * as one word.
 * </p>
 */
int getWordEndNoSpaces(int offset) {
	if (offset >= getCharCount()) {
		return offset;
	}
	int line = content.getLineAtOffset(offset);
	int lineOffset = content.getOffsetAtLine(line);
	String lineText = content.getLine(line);
	int lineLength = lineText.length();
	if (offset == lineOffset + lineLength) {
		line++;
		offset = content.getOffsetAtLine(line);
	} else {
		offset -= lineOffset;
		char ch = lineText.charAt(offset);
		boolean letterOrDigit = Compatibility.isLetterOrDigit(ch);
		while (offset < lineLength - 1 && Compatibility.isLetterOrDigit(ch) == letterOrDigit && !Compatibility.isSpaceChar(ch)) {
			offset++;
			ch = lineText.charAt(offset);
		}
		if (offset == lineLength - 1 && Compatibility.isLetterOrDigit(ch) == letterOrDigit && !Compatibility.isSpaceChar(ch)) {
			offset++;
		}
		offset += lineOffset;
	}
	return offset;
}
/**
 * Returns the start offset of the word at the specified offset.
 * There are two classes of words formed by a sequence of characters:
 * <p>
 * <ul>
 * <li>from 0-9 and A-z (ASCII 48-57 and 65-122)
 * <li>every other character except line breaks
 * </ul>
 * </p>
 * <p>
 * Space characters ' ' (ASCII 20) are special as they are treated as
 * part of the word leading up to the space character.  Line breaks are treated 
 * as one word.
 * </p>
 */
int getWordStart(int offset) {
	if (offset <= 0) {
		return offset;
	}
	int lineIndex = content.getLineAtOffset(offset);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	if (offset == lineOffset) {
		lineIndex--;
		String lineText = content.getLine(lineIndex);
		offset = content.getOffsetAtLine(lineIndex) + lineText.length();
	} else {
		TextLayout layout = renderer.getTextLayout(lineIndex);
		offset -= lineOffset;
		offset = layout.getPreviousOffset(offset, SWT.MOVEMENT_WORD);
		offset += lineOffset;
		renderer.disposeTextLayout(layout); 
	}
	return offset;
}
/**
 * Returns whether the widget wraps lines.
 * <p>
 *
 * @return true if widget wraps lines, false otherwise
 * @since 2.0
 */
public boolean getWordWrap() {
	checkWidget();
	return wordWrap;
}
/** 
 * Returns the location of the given offset.
 * <b>NOTE:</b> Does not return correct values for true italic fonts (vs. slanted fonts).
 * <p>
 *
 * @return location of the character at the given offset in the line.
 */
Point getPointAtOffset(int offset) {
	int lineIndex = content.getLineAtOffset(offset);
	String line = content.getLine(lineIndex);
	int lineOffset = content.getOffsetAtLine(lineIndex);
	int offsetInLine = offset - lineOffset;
	int lineLength = line.length();
	if (lineIndex < content.getLineCount() - 1) {
		int endLineOffset = content.getOffsetAtLine(lineIndex + 1) - 1;
		if (lineLength < offsetInLine && offsetInLine <= endLineOffset) {
			offsetInLine = lineLength;
		}
	}
	Point point;
	if (lineLength != 0  && offsetInLine <= lineLength) {
		TextLayout layout = renderer.getTextLayout(lineIndex);
		if (!advancing || offsetInLine == 0) {
			point = layout.getLocation(offsetInLine, false);
		} else {
			point = layout.getLocation(offsetInLine - 1, true);
		}
		renderer.disposeTextLayout(layout);
	} else {
		point = new Point(0, 0);
	}
	point.x += leftMargin - horizontalScrollOffset;
	point.y += getLinePixel(lineIndex);
	return point;
}
/** 
 * Inserts a string.  The old selection is replaced with the new text.  
 * <p>
 *
 * @param string the string
 * @see #replaceTextRange(int,int,String)
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when string is null</li>
 * </ul>
 */
public void insert(String string) {
	checkWidget();
	if (string == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	Point sel = getSelectionRange();
	replaceTextRange(sel.x, sel.y, string);
}
/**
 * Creates content change listeners and set the default content model.
 */
void installDefaultContent() {
	textChangeListener = new TextChangeListener() {
		public void textChanging(TextChangingEvent event) {
			handleTextChanging(event);
		}
		public void textChanged(TextChangedEvent event) {
			handleTextChanged(event);
		}
		public void textSet(TextChangedEvent event) {
			handleTextSet(event);
		}
	};
	content = new DefaultContent();
	content.addTextChangeListener(textChangeListener);
}
/** 
 * Adds event listeners
 */
void installListeners() {
	ScrollBar verticalBar = getVerticalBar();
	ScrollBar horizontalBar = getHorizontalBar();
	
	listener = new Listener() {
		public void handleEvent(Event event) {
			switch (event.type) {
				case SWT.Dispose: handleDispose(event); break;
				case SWT.KeyDown: handleKeyDown(event); break;
				case SWT.KeyUp: handleKeyUp(event); break;
				case SWT.MouseDown: handleMouseDown(event); break;
				case SWT.MouseUp: handleMouseUp(event); break;
				case SWT.MouseDoubleClick: handleMouseDoubleClick(event); break;
				case SWT.MouseMove: handleMouseMove(event); break;
				case SWT.Paint: handlePaint(event); break;
				case SWT.Resize: handleResize(event); break;
				case SWT.Traverse: handleTraverse(event); break;
			}
		}		
	};
	addListener(SWT.Dispose, listener);
	addListener(SWT.KeyDown, listener);
	addListener(SWT.KeyUp, listener);
	addListener(SWT.MouseDown, listener);
	addListener(SWT.MouseUp, listener);
	addListener(SWT.MouseDoubleClick, listener);
	addListener(SWT.MouseMove, listener);
	addListener(SWT.Paint, listener);
	addListener(SWT.Resize, listener);
	addListener(SWT.Traverse, listener);
	if (verticalBar != null) {
		verticalBar.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				handleVerticalScroll(event);
			}
		});
	}
	if (horizontalBar != null) {
		horizontalBar.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				handleHorizontalScroll(event);
			}
		});
	}
}

void internalRedrawRange1(int start, int length, boolean clearBackground) {
	if (length <= 0) return;
	int end = start + length - 1;
	int firstLine = content.getLineAtOffset(start);
	int lastLine = content.getLineAtOffset(end);
	int partialBottomIndex = getPartialBottomIndex();
	int partialTopIndex = getPartialTopIndex();
	if (firstLine > partialBottomIndex || lastLine < partialTopIndex) {
		return;
	}
	int lineOffset = content.getOffsetAtLine(firstLine);
	if (partialTopIndex > firstLine) {
		firstLine = partialTopIndex;
		start = lineOffset;
	}
	if (partialBottomIndex < lastLine) {
		lastLine = partialBottomIndex;
		end = content.getOffsetAtLine(lastLine) + content.getLine(lastLine).length();
	}
	Rectangle clientArea = getClientArea();	
	TextLayout firstLayout = renderer.getTextLayout(firstLine), lastLayout;
	start = Math.min(firstLayout.getText().length() - 1, start - lineOffset);
	if (firstLine == lastLine) {
		end = Math.min(firstLayout.getText().length() - 1, end - lineOffset);
		if (!wordWrap || firstLayout.getLineIndex(start) == firstLayout.getLineIndex(end)) {
			Rectangle rect = firstLayout.getBounds(start, end);
			rect.x += leftMargin - horizontalScrollOffset;
			rect.y += getLinePixel(firstLine);
			super.redraw(rect.x, rect.y, rect.width, rect.height, false);
			return;
		}
		lastLayout = firstLayout;
	} else {
		lineOffset = content.getOffsetAtLine(lastLine);
		lastLayout = renderer.getTextLayout(lastLine);
		end = Math.min(lastLayout.getText().length() - 1, end - lineOffset);
	}
	int[] firstOffsets = firstLayout.getLineOffsets();
	int[] lastOffsets = firstLayout == lastLayout ? firstOffsets : lastLayout.getLineOffsets();
	Rectangle firstRect;
	if (firstLayout.getText().length() != 0) {
		firstRect = firstLayout.getBounds(start, firstOffsets[firstLayout.getLineIndex(start + 1)] - 1);
	} else {
		firstRect = firstLayout.getBounds();
	}
	firstRect.x += leftMargin - horizontalScrollOffset;
	firstRect.y += getLinePixel(firstLine);
	firstRect.width = clientArea.x + clientArea.width - firstRect.x;
		
	Rectangle lastRect = lastLayout.getBounds(lastOffsets[lastLayout.getLineIndex(end)], end);
	lastRect.x += leftMargin - horizontalScrollOffset;
	lastRect.y += getLinePixel(lastLine);

	super.redraw(firstRect.x, firstRect.y, firstRect.width, firstRect.height, false);
	super.redraw(lastRect.x, lastRect.y, lastRect.width, lastRect.height, false);
	super.redraw(clientArea.x, firstRect.y + firstRect.height, clientArea.width, lastRect.y - (firstRect.y + firstRect.height), false);
	// TODO - clearBackground

}
/** 
 * Redraws the specified text range.
 * <p>
 *
 * @param start offset of the first character to redraw
 * @param length number of characters to redraw
 */
void internalRedrawRange(int start, int length) {
	if (length <= 0) return;
	int end = start + length - 1;
	int firstLine = content.getLineAtOffset(start);
	int lastLine = content.getLineAtOffset(end);
	int partialBottomIndex = getPartialBottomIndex();
	int partialTopIndex = getPartialTopIndex();
	if (firstLine > partialBottomIndex || lastLine < partialTopIndex) {
		return;
	}
	int lineOffset = content.getOffsetAtLine(firstLine);
	if (partialTopIndex > firstLine) {
		firstLine = partialTopIndex;
		start = lineOffset;
	}
	if (partialBottomIndex < lastLine) {
		lastLine = partialBottomIndex;
		end = content.getOffsetAtLine(lastLine) + content.getLine(lastLine).length();
	}
	Rectangle clientArea = getClientArea();
	boolean fullSelection = (getStyle() & SWT.FULL_SELECTION) != 0;
	
	//redraw first line from start to end
	TextLayout layout = renderer.getTextLayout(firstLine);
	Rectangle rect = layout.getBounds(start - lineOffset, end - lineOffset);
	rect.x += leftMargin - horizontalScrollOffset;
	rect.y += getLinePixel(firstLine);
	if (end - lineOffset >= layout.getText().length()) {
		if (fullSelection) {
			rect.width = clientArea.width;
		} else {
			rect.width += renderer.getLineEndSpace();
		}
	} else {
		if (wordWrap && fullSelection) {
			if (layout.getLineIndex(start - lineOffset) != layout.getLineIndex(end - lineOffset)) {
				rect.width = clientArea.width;
			}
		}
	}
	renderer.disposeTextLayout(layout);
	//emtpy lines can have no height
	if (rect.height == 0) rect.height = renderer.getLineHeight();
	super.redraw(rect.x, rect.y, rect.width, rect.height, false);
	int lineCount = lastLine - firstLine + 1;
	if (lineCount > 1) {
		// redraw center lines completely
		int lastLineY = getLinePixel(lastLine);
		if (lineCount > 2) {
			rect.y += rect.height;
			super.redraw(0, rect.y, clientArea.width, lastLineY - rect.y, false);
		}

		// redraw last line from zero to end
		layout = renderer.getTextLayout(lastLine);
		lineOffset = content.getOffsetAtLine(lastLine);
		rect = layout.getBounds(0, end - lineOffset);
		rect.x += leftMargin - horizontalScrollOffset;
		rect.y += lastLineY;
		if (end - lineOffset >= layout.getText().length()) {
			if (fullSelection) {
				rect.width = clientArea.width;
			} else {
				rect.width += renderer.getLineEndSpace();
			}
		} else {
			if (wordWrap && fullSelection) {
				if (0 != layout.getLineIndex(end - lineOffset)) {
					rect.width = clientArea.width;
				}
			}
		}
		renderer.disposeTextLayout(layout);
//		emtpy lines can have no height
		if (rect.height == 0) rect.height = renderer.getLineHeight();
		super.redraw(rect.x, rect.y, rect.width, rect.height, false);
	}
}
/** 
 * Frees resources.
 */
void handleDispose(Event event) {
	removeListener(SWT.Dispose, listener);
	notifyListeners(SWT.Dispose, event);
	event.type = SWT.None;

	clipboard.dispose();
	if (renderer != null) {
		renderer.dispose();
		renderer = null;
	}
	if (content != null) {
		content.removeTextChangeListener(textChangeListener);
		content = null;
	}
	if (defaultCaret != null) {
		defaultCaret.dispose();
		defaultCaret = null;
	}
	if (leftCaretBitmap != null) {
		leftCaretBitmap.dispose();
		leftCaretBitmap = null;
	}
	if (rightCaretBitmap != null) {
		rightCaretBitmap.dispose();
		rightCaretBitmap = null;
	}
	if (isBidiCaret()) {
		BidiUtil.removeLanguageListener(handle);
	}
	selectionBackground = null;
	selectionForeground = null;
	textChangeListener = null;
	selection = null;
	doubleClickSelection = null;
	keyActionMap = null;
	background = null;
	foreground = null;
	clipboard = null;
}
/** 
 * Scrolls the widget horizontally.
 */
void handleHorizontalScroll(Event event) {
	int scrollPixel = getHorizontalBar().getSelection() - horizontalScrollOffset;
	scrollHorizontal(scrollPixel, false);
}
/**
 * If an action has been registered for the key stroke execute the action.
 * Otherwise, if a character has been entered treat it as new content.
 * <p>
 *
 * @param event keyboard event
 */
void handleKey(Event event) {
	int action;
	advancing = true;
	if (event.keyCode != 0) {
		// special key pressed (e.g., F1)
		action = getKeyBinding(event.keyCode | event.stateMask);
	} else {
		// character key pressed
		action = getKeyBinding(event.character | event.stateMask);
		if (action == SWT.NULL) {
			// see if we have a control character
			if ((event.stateMask & SWT.CTRL) != 0 && (event.character >= 0) && event.character <= 31) {
				// get the character from the CTRL+char sequence, the control
				// key subtracts 64 from the value of the key that it modifies
				int c = event.character + 64;
				action = getKeyBinding(c | event.stateMask);
			}
		}
	}
	if (action == SWT.NULL) {
		boolean ignore = false;
		
		if (IS_CARBON) {
			// Ignore accelerator key combinations (we do not want to 
			// insert a character in the text in this instance). Do not  
			// ignore COMMAND+ALT combinations since that key sequence
			// produces characters on the mac.
			ignore = (event.stateMask ^ SWT.COMMAND) == 0 ||
					(event.stateMask ^ (SWT.COMMAND | SWT.SHIFT)) == 0;
		} else if (IS_MOTIF) {
			// Ignore accelerator key combinations (we do not want to 
			// insert a character in the text in this instance). Do not  
			// ignore ALT combinations since this key sequence
			// produces characters on motif.
			ignore = (event.stateMask ^ SWT.CTRL) == 0 ||
					(event.stateMask ^ (SWT.CTRL | SWT.SHIFT)) == 0;
		} else {
			// Ignore accelerator key combinations (we do not want to 
			// insert a character in the text in this instance). Don't  
			// ignore CTRL+ALT combinations since that is the Alt Gr 
			// key on some keyboards.  See bug 20953. 
			ignore = (event.stateMask ^ SWT.ALT) == 0 || 
					(event.stateMask ^ SWT.CTRL) == 0 ||
					(event.stateMask ^ (SWT.ALT | SWT.SHIFT)) == 0 ||
					(event.stateMask ^ (SWT.CTRL | SWT.SHIFT)) == 0;
		}
		// -ignore anything below SPACE except for line delimiter keys and tab.
		// -ignore DEL 
		if (!ignore && event.character > 31 && event.character != SWT.DEL || 
		    event.character == SWT.CR || event.character == SWT.LF || 
		    event.character == TAB) {
			doContent(event.character);
		}
	} else {
		invokeAction(action);
	}
}
/**
 * If a VerifyKey listener exists, verify that the key that was entered
 * should be processed.
 * <p>
 *
 * @param event keyboard event
 */
void handleKeyDown(Event event) {
	if (clipboardSelection == null) {
		clipboardSelection = new Point(selection.x, selection.y);
	}
	
	Event verifyEvent = new Event();
	verifyEvent.character = event.character;
	verifyEvent.keyCode = event.keyCode;
	verifyEvent.stateMask = event.stateMask;
	verifyEvent.doit = true;
	notifyListeners(VerifyKey, verifyEvent);
	if (verifyEvent.doit) {
		handleKey(event);
	}
}
/**
 * Update the Selection Clipboard.
 * <p>
 *
 * @param event keyboard event
 */
void handleKeyUp(Event event) {
	if (clipboardSelection != null) {
		if (clipboardSelection.x != selection.x || clipboardSelection.y != selection.y) {
			try {
				if (selection.y - selection.x > 0) {
					setClipboardContent(selection.x, selection.y - selection.x, DND.SELECTION_CLIPBOARD);
				}
			} catch (SWTError error) {
				// Copy to clipboard failed. This happens when another application 
				// is accessing the clipboard while we copy. Ignore the error.
				// Fixes 1GDQAVN
				// Rethrow all other errors. Fixes bug 17578.
				if (error.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
					throw error;
				}
			}
		}
	}
	clipboardSelection = null;
}
/**
 * Updates the caret location and selection if mouse button 1 has been 
 * pressed.
 */
void handleMouseDoubleClick(Event event) {
	if (event.button != 1 || !doubleClickEnabled) {
		return;
	}
	mouseDoubleClick = true;
	caretOffset = getWordStart(caretOffset);
	resetSelection();
	caretOffset = getWordEndNoSpaces(caretOffset);
	showCaret();
	doMouseSelection();
	doubleClickSelection = new Point(selection.x, selection.y);
}
/** 
 * Updates the caret location and selection if mouse button 1 has been 
 * pressed.
 */
void handleMouseDown(Event event) {
	mouseDown = true;
	mouseDoubleClick = false;
	
	//force focus (object support)
	forceFocus();
	
	if (event.button == 2) {
		String text = (String)getClipboardContent(DND.SELECTION_CLIPBOARD);
		if (text != null && text.length() > 0) {
			// position cursor
			doMouseLocationChange(event.x, event.y, false);
			// insert text
			Event e = new Event();
			e.start = selection.x;
			e.end = selection.y;
			e.text = getModelDelimitedText(text);
			sendKeyEvent(e);
		}
	}
	if ((event.button != 1) || (IS_CARBON && (event.stateMask & SWT.MOD4) != 0)) {
		return;	
	}
	boolean select = (event.stateMask & SWT.MOD2) != 0;
	doMouseLocationChange(event.x, event.y, select);
}
/** 
 * Updates the caret location and selection if mouse button 1 is pressed 
 * during the mouse move.
 */
void handleMouseMove(Event event) {
	if (!mouseDown) return;
	if ((event.stateMask & SWT.BUTTON1) == 0) {
		return;
	}
	doMouseLocationChange(event.x, event.y, true);
	update();
	doAutoScroll(event);
}
/** 
 * Autoscrolling ends when the mouse button is released.
 */
void handleMouseUp(Event event) {
	mouseDown = false;
	mouseDoubleClick = false;
	endAutoScroll();
	if (event.button == 1) {
		try {
			if (selection.y - selection.x > 0) {
				setClipboardContent(selection.x, selection.y - selection.x, DND.SELECTION_CLIPBOARD);
			}
		} catch (SWTError error) {
			// Copy to clipboard failed. This happens when another application 
			// is accessing the clipboard while we copy. Ignore the error.
			// Fixes 1GDQAVN
			// Rethrow all other errors. Fixes bug 17578.
			if (error.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
				throw error;
			}
		}
	}
}
/**
 * Renders the invalidated area specified in the paint event.
 * <p>
 *
 * @param event paint event
 */
void handlePaint(Event event) {
	if (event.width == 0 || event.height == 0) return;
	Rectangle clientArea = getClientArea();
	if (clientArea.width == 0 || clientArea.height == 0) return;
	
	int startLine = getLineIndex(event.y);
	int y = getLinePixel(startLine);
	int endY = event.y + event.height;
	GC gc = event.gc;
	Color background = getBackground();
	Color foreground = getForeground();
	if (endY > 0) {
		int lineCount = isSingleLine() ? 1 : content.getLineCount();
		int x = clientArea.x + leftMargin - horizontalScrollOffset;
		for (int i = startLine; y < endY && i < lineCount; i++) {
			y += renderer.drawLine(i, x, y, gc, background, foreground);
		}
		if (y < endY) {
			gc.setBackground(background);
			gc.fillRectangle(0, y, clientArea.width, endY - y);
		}
	}
	clearMargin(gc, background, clientArea, 0);
}	
/**
 * Recalculates the scroll bars. Rewraps all lines when in word 
 * wrap mode.
 * <p>
 *
 * @param event resize event
 */
void handleResize(Event event) {
	int oldHeight = clientAreaHeight;
	int oldWidth = clientAreaWidth;
	Rectangle clientArea = getClientArea();
	clientAreaHeight = clientArea.height;
	clientAreaWidth = clientArea.width;
	/* Redraw the old or new right/bottom margin if needed */
	if (oldWidth != clientAreaWidth) {
		if (rightMargin > 0) {
			int x = (oldWidth < clientAreaWidth ? oldWidth : clientAreaWidth) - rightMargin; 
			super.redraw(x, 0, rightMargin, oldHeight, false);
		}
	}
	if (oldHeight != clientAreaHeight) {
		if (bottomMargin > 0) {
			int y = (oldHeight < clientAreaHeight ? oldHeight : clientAreaHeight) - bottomMargin; 
			super.redraw(0, y, oldWidth, bottomMargin, false);
		}
	}
	if (wordWrap) {
		if (oldWidth != clientAreaWidth) {
			if (partialHeight > 0) {
				int partialTopIndex = getPartialTopIndex();
				TextLayout layout = renderer.getTextLayout(partialTopIndex);
				layout.setWidth(oldWidth);
				int height = layout.getBounds().height;
				layout.setWidth(clientAreaWidth);
				int newHeight = layout.getBounds().height;
				renderer.disposeTextLayout(layout);
				partialHeight += newHeight - height;
				if (partialHeight < 0) partialHeight = 0;
			}
			renderer.reset(0, content.getLineCount());
			verticalScrollOffset = -1;
			renderer.calculateIdle();
			super.redraw();
		}
		if (oldHeight != clientAreaHeight) {
			setScrollBars(true);
		}
		setCaretLocation();
	} else  {
		renderer.calculateClientArea();
		setScrollBars(true);
		claimRightFreeSpace();
	}
	claimBottomFreeSpace();
}
/**
 * Updates the caret position and selection and the scroll bars to reflect 
 * the content change.
 * <p>
 */
void handleTextChanged(TextChangedEvent event) {
	int firstLine = content.getLineAtOffset(lastTextChangeStart);
	resetCache(firstLine, 0);
	int lastLine = firstLine + lastTextChangeNewLineCount;
	int firstLineTop = getLinePixel(firstLine);
	int newLastLineBottom = getLinePixel(lastLine + 1);
	if (newLastLineBottom != lastLineBottom) {
		//TODO fails if margin != 0
		scroll(0, newLastLineBottom, 0, lastLineBottom, clientAreaWidth, clientAreaHeight, true);
	}
	super.redraw(0, firstLineTop, clientAreaWidth, newLastLineBottom - firstLineTop, false);
	// update selection/caret location after styles have been changed.
	// otherwise any text measuring could be incorrect
	// 
	// also, this needs to be done after all scrolling. Otherwise, 
	// selection redraw would be flushed during scroll which is wrong.
	// in some cases new text would be drawn in scroll source area even 
	// though the intent is to scroll it.
	updateSelection(lastTextChangeStart, lastTextChangeReplaceCharCount, lastTextChangeNewCharCount);
	if (newLastLineBottom == lastLineBottom) {		
		//update();
	}
	if (lastTextChangeReplaceLineCount > 0) {
		claimBottomFreeSpace();
	}
	if (lastTextChangeReplaceCharCount > 0) {
		claimRightFreeSpace();
	}
}
/**
 * Updates the screen to reflect a pending content change.
 * <p>
 *
 * @param event.start the start offset of the change
 * @param event.newText text that is going to be inserted or empty String 
 *	if no text will be inserted
 * @param event.replaceCharCount length of text that is going to be replaced
 * @param event.newCharCount length of text that is going to be inserted
 * @param event.replaceLineCount number of lines that are going to be replaced
 * @param event.newLineCount number of new lines that are going to be inserted
 */
void handleTextChanging(TextChangingEvent event) {
	if (event.replaceCharCount < 0) {
		event.start += event.replaceCharCount;
		event.replaceCharCount *= -1;
	}
	lastTextChangeStart = event.start;
	lastTextChangeNewLineCount = event.newLineCount;
	lastTextChangeNewCharCount = event.newCharCount;
	lastTextChangeReplaceLineCount = event.replaceLineCount;
	lastTextChangeReplaceCharCount = event.replaceCharCount;	
	int lineIndex = content.getLineAtOffset(event.start);
	int srcY = getLinePixel(lineIndex + event.replaceLineCount + 1);
	int destY = getLinePixel(lineIndex + 1) + event.newLineCount * renderer.getLineHeight();
	lastLineBottom = destY;
	if (destY != srcY) {
		//TODO fails if margin != 0
		scroll(0, destY, 0, srcY, clientAreaWidth, clientAreaHeight, true);
	}

	renderer.textChanging(event);
	
	// Update the caret offset if it is greater than the length of the content.
	// This is necessary since style range API may be called between the
	// handleTextChanging and handleTextChanged events and this API sets the
	// caretOffset.
	int newEndOfText = content.getCharCount() - event.replaceCharCount + event.newCharCount;
	if (caretOffset > newEndOfText) caretOffset = newEndOfText;
}
/**
 * Called when the widget content is set programatically, overwriting 
 * the old content. Resets the caret position, selection and scroll offsets. 
 * Recalculates the content width and scroll bars. Redraws the widget.
 * <p>
 *
 * @param event text change event. 
 */
void handleTextSet(TextChangedEvent event) {
	reset();
}
/**
 * Called when a traversal key is pressed.
 * Allow tab next traversal to occur when the widget is in single 
 * line mode or in multi line and non-editable mode . 
 * When in editable multi line mode we want to prevent the tab 
 * traversal and receive the tab key event instead.
 * <p>
 *
 * @param event the event
 */
void handleTraverse(Event event) {
	switch (event.detail) {
		case SWT.TRAVERSE_ESCAPE:
		case SWT.TRAVERSE_PAGE_NEXT:
		case SWT.TRAVERSE_PAGE_PREVIOUS:
			event.doit = true;
			break;
		case SWT.TRAVERSE_RETURN:
		case SWT.TRAVERSE_TAB_NEXT:
		case SWT.TRAVERSE_TAB_PREVIOUS:
			if ((getStyle() & SWT.SINGLE) != 0) {
				event.doit = true;
			} else {
				if (!editable || (event.stateMask & SWT.MODIFIER_MASK) != 0) {
					event.doit = true;
				}
			}
			break;
	}
}
/** 
 * Scrolls the widget vertically.
 */
void handleVerticalScroll(Event event) {
	int scrollPixel = getVerticalBar().getSelection() - getVerticalScrollOffset();
	scrollVertical(scrollPixel, false);
}
/**
 * Add accessibility support for the widget.
 */
void initializeAccessible() {
	final Accessible accessible = getAccessible();
	accessible.addAccessibleListener(new AccessibleAdapter() {
		public void getName (AccessibleEvent e) {
			String name = null;
			Label label = getAssociatedLabel ();
			if (label != null) {
				name = stripMnemonic (label.getText());
			}
			e.result = name;
		}
		public void getHelp(AccessibleEvent e) {
			e.result = getToolTipText();
		}
		public void getKeyboardShortcut(AccessibleEvent e) {
			String shortcut = null;
			Label label = getAssociatedLabel ();
			if (label != null) {
				String text = label.getText ();
				if (text != null) {
					char mnemonic = _findMnemonic (text);
					if (mnemonic != '\0') {
						shortcut = "Alt+"+mnemonic; //$NON-NLS-1$
					}
				}
			}
			e.result = shortcut;
		}
	});
	accessible.addAccessibleTextListener(new AccessibleTextAdapter() {
		public void getCaretOffset(AccessibleTextEvent e) {
			e.offset = StyledText.this.getCaretOffset();
		}
		public void getSelectionRange(AccessibleTextEvent e) {
			Point selection = StyledText.this.getSelectionRange();
			e.offset = selection.x;
			e.length = selection.y;
		}
	});
	accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
		public void getRole(AccessibleControlEvent e) {
			e.detail = ACC.ROLE_TEXT;
		}
		public void getState(AccessibleControlEvent e) {
			int state = 0;
			if (isEnabled()) state |= ACC.STATE_FOCUSABLE;
			if (isFocusControl()) state |= ACC.STATE_FOCUSED;
			if (!isVisible()) state |= ACC.STATE_INVISIBLE;
			if (!getEditable()) state |= ACC.STATE_READONLY;
			e.detail = state;
		}
		public void getValue(AccessibleControlEvent e) {
			e.result = StyledText.this.getText();
		}
	});	
	addListener(SWT.FocusIn, new Listener() {
		public void handleEvent(Event event) {
			accessible.setFocus(ACC.CHILDID_SELF);
		}
	});	
}
/* 
 * Return the Label immediately preceding the receiver in the z-order, 
 * or null if none. 
 */
Label getAssociatedLabel () {
	Control[] siblings = getParent ().getChildren ();
	for (int i = 0; i < siblings.length; i++) {
		if (siblings [i] == StyledText.this) {
			if (i > 0 && siblings [i-1] instanceof Label) {
				return (Label) siblings [i-1];
			}
		}
	}
	return null;
}
String stripMnemonic (String string) {
	int index = 0;
	int length = string.length ();
	do {
		while ((index < length) && (string.charAt (index) != '&')) index++;
		if (++index >= length) return string;
		if (string.charAt (index) != '&') {
			return string.substring(0, index-1) + string.substring(index, length);
		}
		index++;
	} while (index < length);
 	return string;
}
/*
 * Return the lowercase of the first non-'&' character following
 * an '&' character in the given string. If there are no '&'
 * characters in the given string, return '\0'.
 */
char _findMnemonic (String string) {
	if (string == null) return '\0';
	int index = 0;
	int length = string.length ();
	do {
		while (index < length && string.charAt (index) != '&') index++;
		if (++index >= length) return '\0';
		if (string.charAt (index) != '&') return Character.toLowerCase (string.charAt (index));
		index++;
	} while (index < length);
 	return '\0';
}
/**
 * Executes the action.
 * <p>
 *
 * @param action one of the actions defined in ST.java
 */
public void invokeAction(int action) {
	checkWidget();
	updateCaretDirection = true;
	switch (action) {
		// Navigation
		case ST.LINE_UP:
			doLineUp(false);
			clearSelection(true);
			break;
		case ST.LINE_DOWN:
			doLineDown(false);
			clearSelection(true);
			break;
		case ST.LINE_START:
			doLineStart();
			clearSelection(true);
			break;
		case ST.LINE_END:
			doLineEnd();
			clearSelection(true);
			break;
		case ST.COLUMN_PREVIOUS:
			doCursorPrevious();
			clearSelection(true);
			break;
		case ST.COLUMN_NEXT:
			doCursorNext();
			clearSelection(true);
			break;
		case ST.PAGE_UP:
			doPageUp(false, getClientArea().height);
			clearSelection(true);
			break;
		case ST.PAGE_DOWN:
			doPageDown(false, getClientArea().height);
			clearSelection(true);
			break;
		case ST.WORD_PREVIOUS:
			doWordPrevious();
			clearSelection(true);
			break;
		case ST.WORD_NEXT:
			doWordNext();
			clearSelection(true);
			break;
		case ST.TEXT_START:
			doContentStart();
			clearSelection(true);
			break;
		case ST.TEXT_END:
			doContentEnd();
			clearSelection(true);
			break;
		case ST.WINDOW_START:
			doPageStart();
			clearSelection(true);
			break;
		case ST.WINDOW_END:
			doPageEnd();
			clearSelection(true);
			break;
		// Selection	
		case ST.SELECT_LINE_UP:
			doSelectionLineUp();
			break;
		case ST.SELECT_ALL:
			selectAll();
			break;
		case ST.SELECT_LINE_DOWN:
			doSelectionLineDown();
			break;
		case ST.SELECT_LINE_START:
			doLineStart();
			doSelection(ST.COLUMN_PREVIOUS);
			break;
		case ST.SELECT_LINE_END:
			doLineEnd();
			doSelection(ST.COLUMN_NEXT);
			break;
		case ST.SELECT_COLUMN_PREVIOUS:
			doSelectionCursorPrevious();
			doSelection(ST.COLUMN_PREVIOUS);
			break;
		case ST.SELECT_COLUMN_NEXT:
			doSelectionCursorNext();
			doSelection(ST.COLUMN_NEXT);
			break;
		case ST.SELECT_PAGE_UP:
			doSelectionPageUp(getClientArea().height);
			break;
		case ST.SELECT_PAGE_DOWN:
			doSelectionPageDown(getClientArea().height);
			break;
		case ST.SELECT_WORD_PREVIOUS:
			doSelectionWordPrevious();
			doSelection(ST.COLUMN_PREVIOUS);
			break;
		case ST.SELECT_WORD_NEXT:
			doSelectionWordNext();
			doSelection(ST.COLUMN_NEXT);
			break;
		case ST.SELECT_TEXT_START:
			doContentStart();
			doSelection(ST.COLUMN_PREVIOUS);
			break;
		case ST.SELECT_TEXT_END:
			doContentEnd();
			doSelection(ST.COLUMN_NEXT);
			break;
		case ST.SELECT_WINDOW_START:
			doPageStart();
			doSelection(ST.COLUMN_PREVIOUS);
			break;
		case ST.SELECT_WINDOW_END:
			doPageEnd();
			doSelection(ST.COLUMN_NEXT);
			break;
		// Modification			
		case ST.CUT:
			cut();
			break;
		case ST.COPY:
			copy();
			break;
		case ST.PASTE:
			paste();
			break;
		case ST.DELETE_PREVIOUS:
			doBackspace();
			break;
		case ST.DELETE_NEXT:
			doDelete();
			break;
		case ST.DELETE_WORD_PREVIOUS:
			doDeleteWordPrevious();
			break;
		case ST.DELETE_WORD_NEXT:
			doDeleteWordNext();
			break;
		// Miscellaneous
		case ST.TOGGLE_OVERWRITE:
			overwrite = !overwrite;		// toggle insert/overwrite mode
			break;
	}
}
/**
 * Temporary until SWT provides this
 */
boolean isBidi() {
	return IS_GTK || BidiUtil.isBidiPlatform() || isMirrored;
}
boolean isBidiCaret() {
	return BidiUtil.isBidiPlatform();
}
boolean isFixedLineHeight() {
	return fixedLineHeight;
}
/**
 * Returns whether the given offset is inside a multi byte line delimiter.
 * Example: 
 * "Line1\r\n" isLineDelimiter(5) == false but isLineDelimiter(6) == true
 * 
 * @return true if the given offset is inside a multi byte line delimiter.
 * false if the given offset is before or after a line delimiter.
 */
boolean isLineDelimiter(int offset) {
	int line = content.getLineAtOffset(offset);
	int lineOffset = content.getOffsetAtLine(line);	
	int offsetInLine = offset - lineOffset;
	// offsetInLine will be greater than line length if the line 
	// delimiter is longer than one character and the offset is set
	// in between parts of the line delimiter.
	return offsetInLine > content.getLine(line).length();
}
/**
 * Returns whether the widget is mirrored (right oriented/right to left 
 * writing order). 
 * 
 * @return isMirrored true=the widget is right oriented, false=the widget 
 * 	is left oriented
 */
boolean isMirrored() {
	return isMirrored;
}
/**
 * Returns whether the widget can have only one line.
 * <p>
 *
 * @return true if widget can have only one line, false if widget can have 
 * 	multiple lines
 */
boolean isSingleLine() {
	return (getStyle() & SWT.SINGLE) != 0;
}
/**
 * Sends the specified verify event, replace/insert text as defined by 
 * the event and send a modify event.
 * <p>
 *
 * @param event	the text change event. 
 *	<ul>
 *	<li>event.start - the replace start offset</li>
 * 	<li>event.end - the replace end offset</li>
 * 	<li>event.text - the new text</li>
 *	</ul>
 * @param updateCaret whether or not he caret should be set behind
 *	the new text
 */
void modifyContent(Event event, boolean updateCaret) {
	event.doit = true;
	notifyListeners(SWT.Verify, event);
	if (event.doit) {
		StyledTextEvent styledTextEvent = null;
		int replacedLength = event.end - event.start;
		if (isListening(ExtendedModify)) {
			styledTextEvent = new StyledTextEvent(content);
			styledTextEvent.start = event.start;
			styledTextEvent.end = event.start + event.text.length();
			styledTextEvent.text = content.getTextRange(event.start, replacedLength);
		}
		if (updateCaret) {
			//Fix advancing flag for delete/backspace key on direction boundary
			if (event.text.length() == 0) {
				int lineIndex = content.getLineAtOffset(event.start);
				int lineOffset = content.getOffsetAtLine(lineIndex);
				TextLayout layout = renderer.getTextLayout(lineIndex);
				int levelStart = layout.getLevel(event.start - lineOffset);
				int lineIndexEnd = content.getLineAtOffset(event.end);
				if (lineIndex != lineIndexEnd) {
					renderer.disposeTextLayout(layout);
					lineOffset = content.getOffsetAtLine(lineIndexEnd);
					layout = renderer.getTextLayout(lineIndexEnd);
				}
				int levelEnd = layout.getLevel(event.end - lineOffset);
				renderer.disposeTextLayout(layout);
				advancing = levelStart != levelEnd;
			}
		}
		content.replaceTextRange(event.start, replacedLength, event.text);
		// set the caret position prior to sending the modify event.
		// fixes 1GBB8NJ
		if (updateCaret) {
			// always update the caret location. fixes 1G8FODP
			setSelection(event.start + event.text.length(), 0, true);
			showCaret();
		}
		sendModifyEvent(event);
		if (isListening(ExtendedModify)) {
			notifyListeners(ExtendedModify, styledTextEvent);
		}
	}
}
void paintObject (GC gc, int x, int y, int ascent, int descent, GlyphMetrics metrics, int start, int length) {
	if (isListening(PaintObject)) {
		StyledTextEvent event = new StyledTextEvent (content) ;
		event.gc = gc;
		event.x = x;
		event.y = y;
		event.ascent = ascent;
		event.descent = descent;
		event.metrics = metrics;
//		event.bullet = bullet;		
		event.start = start;
		event.length = length;
		notifyListeners(PaintObject, event);
	}
}
/** 
 * Replaces the selection with the text on the <code>DND.CLIPBOARD</code>  
 * clipboard  or, if there is no selection,  inserts the text at the current 
 * caret offset.   If the widget has the SWT.SINGLE style and the 
 * clipboard text contains more than one line, only the first line without
 * line delimiters is  inserted in the widget.
 * <p>
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void paste(){
	checkWidget();	
	String text = (String) getClipboardContent(DND.CLIPBOARD);
	if (text != null && text.length() > 0) {
		Event event = new Event();
		event.start = selection.x;
		event.end = selection.y;
		event.text = getModelDelimitedText(text);
		sendKeyEvent(event);
	}
}
/** 
 * Prints the widget's text to the default printer.
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void print() {
	checkWidget();
	Printer printer = new Printer();
	StyledTextPrintOptions options = new StyledTextPrintOptions();
	options.printTextForeground = true;
	options.printTextBackground = true;
	options.printTextFontStyle = true;
	options.printLineBackground = true;	
	new Printing(this, printer, options).run();
	printer.dispose();
}
/** 
 * Returns a runnable that will print the widget's text
 * to the specified printer.
 * <p>
 * The runnable may be run in a non-UI thread.
 * </p>
 * 
 * @param printer the printer to print to
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when printer is null</li>
 * </ul>
 */
public Runnable print(Printer printer) {
	checkWidget();	
	if (printer == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	StyledTextPrintOptions options = new StyledTextPrintOptions();
	options.printTextForeground = true;
	options.printTextBackground = true;
	options.printTextFontStyle = true;
	options.printLineBackground = true;
	return print(printer, options);
}
/** 
 * Returns a runnable that will print the widget's text
 * to the specified printer.
 * <p>
 * The runnable may be run in a non-UI thread.
 * </p>
 * 
 * @param printer the printer to print to
 * @param options print options to use during printing
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when printer or options is null</li>
 * </ul>
 * @since 2.1
 */
public Runnable print(Printer printer, StyledTextPrintOptions options) {
	checkWidget();
	if (printer == null || options == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	return new Printing(this, printer, options);
}
/**
 * Causes the entire bounds of the receiver to be marked
 * as needing to be redrawn. The next time a paint request
 * is processed, the control will be completely painted.
 * <p>
 * Recalculates the content width for all lines in the bounds.
 * When a <code>LineStyleListener</code> is used a redraw call 
 * is the only notification to the widget that styles have changed 
 * and that the content width may have changed.
 * </p>
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @see Control#update
 */
public void redraw() {
	super.redraw();
	int itemCount = getPartialBottomIndex() - topIndex + 1;
	renderer.reset(topIndex, itemCount);
	renderer.calculate(topIndex, itemCount);
	setScrollBars(false);
}
/**
 * Causes the rectangular area of the receiver specified by
 * the arguments to be marked as needing to be redrawn. 
 * The next time a paint request is processed, that area of
 * the receiver will be painted. If the <code>all</code> flag
 * is <code>true</code>, any children of the receiver which
 * intersect with the specified area will also paint their
 * intersecting areas. If the <code>all</code> flag is 
 * <code>false</code>, the children will not be painted.
 * <p>
 * Marks the content width of all lines in the specified rectangle
 * as unknown. Recalculates the content width of all visible lines.
 * When a <code>LineStyleListener</code> is used a redraw call 
 * is the only notification to the widget that styles have changed 
 * and that the content width may have changed.
 * </p>
 *
 * @param x the x coordinate of the area to draw
 * @param y the y coordinate of the area to draw
 * @param width the width of the area to draw
 * @param height the height of the area to draw
 * @param all <code>true</code> if children should redraw, and <code>false</code> otherwise
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @see Control#update
 */
public void redraw(int x, int y, int width, int height, boolean all) {
	super.redraw(x, y, width, height, all);
	if (height > 0) {
		int firstLine = getLineIndex(y);
		int lastLine = getLineIndex(y + height);
		resetCache(firstLine, lastLine - firstLine + 1);
	}
}
void redrawLines(int startLine, int lineCount) {
	// do nothing if redraw range is completely invisible	
	int partialBottomIndex = getPartialBottomIndex();
	if (startLine > partialBottomIndex || startLine + lineCount - 1 < topIndex) {
		return;
	}
	// only redraw visible lines
	if (startLine < topIndex) {
		lineCount -= topIndex - startLine;
		startLine = topIndex;
	}
	if (startLine + lineCount - 1 > partialBottomIndex) {
		lineCount = partialBottomIndex - startLine + 1;
	}
	startLine -= topIndex;
	int redrawTop = getLinePixel(startLine);
	int redrawBottom = getLinePixel(startLine + lineCount);
	int redrawWidth = getClientArea().width - leftMargin - rightMargin; 
	super.redraw(leftMargin, redrawTop, redrawWidth, redrawBottom - redrawTop, true);
}
/** 
 * Redraws the specified text range.
 * <p>
 *
 * @param start offset of the first character to redraw
 * @param length number of characters to redraw
 * @param clearBackground true if the background should be cleared as
 *  part of the redraw operation.  If true, the entire redraw range will
 *  be cleared before anything is redrawn.  If the redraw range includes
 *	the last character of a line (i.e., the entire line is redrawn) the 
 * 	line is cleared all the way to the right border of the widget.
 * 	The redraw operation will be faster and smoother if clearBackground 
 * 	is set to false.  Whether or not the flag can be set to false depends 
 * 	on the type of change that has taken place.  If font styles or 
 * 	background colors for the redraw range have changed, clearBackground 
 * 	should be set to true.  If only foreground colors have changed for 
 * 	the redraw range, clearBackground can be set to false. 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when start and/or end are outside the widget content</li> 
 * </ul>
 */
public void redrawRange(int start, int length, boolean clearBackground) {
	checkWidget();
	int end = start + length;
	int contentLength = content.getCharCount();
	if (start > end || start < 0 || end > contentLength) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	int firstLine = content.getLineAtOffset(start);
	int lastLine = content.getLineAtOffset(end);
	resetCache(firstLine, lastLine - firstLine + 1);
	internalRedrawRange(start, length);
}
/**
 * Removes the specified bidirectional segment listener.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 * @since 2.0
 */
public void removeBidiSegmentListener(BidiSegmentListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(LineGetSegments, listener);	
}
/**
 * Removes the specified extended modify listener.
 * <p>
 *
 * @param extendedModifyListener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeExtendedModifyListener(ExtendedModifyListener extendedModifyListener) {
	checkWidget();
	if (extendedModifyListener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(ExtendedModify, extendedModifyListener);	
}
/**
 * Removes the specified line background listener.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeLineBackgroundListener(LineBackgroundListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(LineGetBackground, listener);
}
/**
 * Removes the specified line style listener.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeLineStyleListener(LineStyleListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(LineGetStyle, listener);
}
/**
 * Removes the specified modify listener.
 * <p>
 *
 * @param modifyListener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeModifyListener(ModifyListener modifyListener) {
	checkWidget();
	if (modifyListener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(SWT.Modify, modifyListener);
}
/**
 * Removes the specified listener.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 * @since 3.2
 */
public void removePaintObjectListener(PaintObjectListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(PaintObject, listener);
}
/**
 * Removes the specified selection listener.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeSelectionListener(SelectionListener listener) {
	checkWidget();
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(SWT.Selection, listener);
}
/**
 * Removes the specified verify listener.
 * <p>
 *
 * @param verifyListener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeVerifyListener(VerifyListener verifyListener) {
	checkWidget();
	if (verifyListener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(SWT.Verify, verifyListener);
}
/**
 * Removes the specified key verify listener.
 * <p>
 *
 * @param listener the listener
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void removeVerifyKeyListener(VerifyKeyListener listener) {
	if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	removeListener(VerifyKey, listener);
}
/** 
 * Replaces the styles in the given range with new styles.  This method
 * effectively deletes the styles in the given range and then adds the
 * the new styles. 
 * <p>
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * </p>
 *
 * @param start offset of first character where styles will be deleted
 * @param length length of the range to delete styles in
 * @param ranges StyleRange objects containing the new style information.
 * The ranges should not overlap and should be within the specified start 
 * and length. The style rendering is undefined if the ranges do overlap
 * or are ill-defined. Must not be null.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when either start or end is outside the valid range (0 <= offset <= getCharCount())</li> 
 *   <li>ERROR_NULL_ARGUMENT when ranges is null</li>
 * </ul>
 * @since 2.0
 */
public void replaceStyleRanges(int start, int length, StyleRange[] ranges) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
 	if (ranges == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
 	setStyleRanges(start, length, null, ranges, false);
}
/**
 * Replaces the given text range with new text.
 * If the widget has the SWT.SINGLE style and "text" contains more than 
 * one line, only the first line is rendered but the text is stored 
 * unchanged. A subsequent call to getText will return the same text 
 * that was set. Note that only a single line of text should be set when 
 * the SWT.SINGLE style is used.
 * <p>
 * <b>NOTE:</b> During the replace operation the current selection is
 * changed as follows:
 * <ul>	
 * <li>selection before replaced text: selection unchanged
 * <li>selection after replaced text: adjust the selection so that same text 
 * remains selected
 * <li>selection intersects replaced text: selection is cleared and caret
 * is placed after inserted text
 * </ul>
 * </p>
 *
 * @param start offset of first character to replace
 * @param length number of characters to replace. Use 0 to insert text
 * @param text new text. May be empty to delete text.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when either start or end is outside the valid range (0 <= offset <= getCharCount())</li> 
 *   <li>ERROR_INVALID_ARGUMENT when either start or end is inside a multi byte line delimiter. 
 * 		Splitting a line delimiter for example by inserting text in between the CR and LF and deleting part of a line delimiter is not supported</li>  
 *   <li>ERROR_NULL_ARGUMENT when string is null</li>
 * </ul>
 */
public void replaceTextRange(int start, int length, String text) {
	checkWidget();
	if (text == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	int contentLength = getCharCount();
	int end = start + length;
	if (start > end || start < 0 || end > contentLength) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	Event event = new Event();
	event.start = start;
	event.end = end;
	event.text = text;
	modifyContent(event, false);
}
/**
 * Resets the caret position, selection and scroll offsets. Recalculate
 * the content width and scroll bars. Redraw the widget.
 */
void reset() {
	ScrollBar verticalBar = getVerticalBar();
	ScrollBar horizontalBar = getHorizontalBar();
	caretOffset = 0;
	topIndex = 0;
	topOffset = 0;
	verticalScrollOffset = 0;
	horizontalScrollOffset = 0;	
	resetSelection();
	renderer.setContent(content); 
	renderer.calculateClientArea();
	if (verticalBar != null) {
		verticalBar.setSelection(0);
	}
	if (horizontalBar != null) {
		horizontalBar.setSelection(0);	
	}
	setScrollBars(true);
	setCaretLocation();
	super.redraw();
}
void resetCache(int firstLine, int count) {
	if (isFixedLineHeight()) {
		renderer.reset(firstLine, count);
		renderer.calculateClientArea();
		setScrollBars(true);
	} else {
		renderer.reset(firstLine, count);
		if (topIndex > firstLine) {
			verticalScrollOffset = -1;
		}
		renderer.calculateIdle();
	}
}
/**
 * Resets the selection.
 */
void resetSelection() {
	selection.x = selection.y = caretOffset;
	selectionAnchor = -1;
}
/**
 * Scrolls the widget horizontally.
 * <p>
 *
 * @param pixels number of pixels to scroll, > 0 = scroll left,
 * 	< 0 scroll right
 * @param adjustScrollBar 
 * 	true= the scroll thumb will be moved to reflect the new scroll offset.
 * 	false = the scroll thumb will not be moved
 * @return 
 *	true=the widget was scrolled 
 *	false=the widget was not scrolled, the given offset is not valid.
 */
boolean scrollHorizontal(int pixels, boolean adjustScrollBar) {
	if (pixels == 0) {
		return false;
	}
	ScrollBar horizontalBar = getHorizontalBar();
	if (horizontalBar != null && adjustScrollBar) {
		horizontalBar.setSelection(horizontalScrollOffset + pixels);
	}
	scroll(pixels, 0);
	return true;
}
/**
 * Scrolls the widget vertically.
 * <p>
 *
 * @param pixel the new vertical scroll offset
 * @param adjustScrollBar 
 * 	true= the scroll thumb will be moved to reflect the new scroll offset.
 * 	false = the scroll thumb will not be moved
 * @return 
 *	true=the widget was scrolled 
 *	false=the widget was not scrolled, the given offset is not valid.
 */
boolean scrollVertical(int pixels, boolean adjustScrollBar) {
	if (pixels == 0) {
		return false;
	}
	ScrollBar verticalBar = getVerticalBar();
	if (verticalBar != null && adjustScrollBar) {
		verticalBar.setSelection(getVerticalScrollOffset() + pixels);
	}
	scroll(0, pixels);
	return true;
}
/**
 * Scrolls the widget horizontally or vertically.
 * <p>
 * NOTE: This fuction can not be used to scroll the widget
 * horizontally and vertically at the same time.
 */
void scroll(int hscroll, int vscroll) {	
	Rectangle clientArea = getClientArea();
	if (hscroll > 0 || vscroll > 0) {
		int sourceX = leftMargin + hscroll;
		int sourceY = topMargin + vscroll;
		int scrollWidth = clientArea.width - sourceX - rightMargin;
		int scrollHeight = clientArea.height - sourceY - bottomMargin;
		scroll(
			leftMargin, topMargin, 						// destination x, y
			sourceX, sourceY,							// source x, y
			scrollWidth, scrollHeight, true);
		
		// redraw from end of scrolled area to beginning of scroll 
		// invalidated area
		if (sourceX > scrollWidth) {
			super.redraw(
				leftMargin + scrollWidth, topMargin, 
				hscroll - scrollWidth, scrollHeight, true);
		}
		if (sourceY > scrollHeight) {
			super.redraw(
				leftMargin, topMargin + scrollHeight, 
				scrollWidth, vscroll - scrollHeight, true);
		}
	} else {
		int destinationX = leftMargin - hscroll;
		int destinationY = topMargin - vscroll;
		int scrollWidth = clientArea.width - destinationX - rightMargin;
		int scrollHeight = clientArea.height - destinationY - bottomMargin;
		scroll(
			destinationX, destinationY,					// destination x, y
			leftMargin, topMargin,						// source x, y
			scrollWidth, scrollHeight, true);
		
		// redraw from end of scroll invalidated area to scroll 
		// destination
		if (destinationX > scrollWidth) {
			super.redraw(
				leftMargin + scrollWidth, topMargin, 
				-hscroll - scrollWidth, scrollHeight, true);	
		}
		if (destinationY > scrollHeight) {
			// redraw from end of scroll invalidated area to scroll 
			// destination
			super.redraw(
				leftMargin, topMargin + scrollHeight, 
				scrollWidth, -vscroll - scrollHeight, true);	
		}
	}
	horizontalScrollOffset += hscroll;
	if (vscroll != 0) {
		if (verticalScrollOffset == -1) {
			// assure verticalScrollOffset is up to date
			getVerticalScrollOffset();		
		}
		verticalScrollOffset += vscroll;	
		calculateTopIndex(vscroll);
	}
	int oldColumnX = columnX;
	setCaretLocation();
	columnX = oldColumnX;
}
/** 
 * Selects all the text.
 * <p>
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void selectAll() {
	checkWidget();
	setSelection(0, Math.max(getCharCount(),0));
}
/**
 * Replaces/inserts text as defined by the event.
 * <p>
 *
 * @param event the text change event. 
 *	<ul>
 *	<li>event.start - the replace start offset</li>
 * 	<li>event.end - the replace end offset</li>
 * 	<li>event.text - the new text</li>
 *	</ul>
 */
void sendKeyEvent(Event event) {
	if (editable) {
		modifyContent(event, true);
	}
}
/**
 * Returns a StyledTextEvent that can be used to request data such 
 * as styles and background color for a line.
 * The specified line may be a visual (wrapped) line if in word 
 * wrap mode. The returned object will always be for a logical 
 * (unwrapped) line.
 * <p>
 *
 * @param lineOffset offset of the line. This may be the offset of
 * 	a visual line if the widget is in word wrap mode.
 * @param line line text. This may be the text of a visualline if 
 * 	the widget is in word wrap mode.
 * @return StyledTextEvent that can be used to request line data 
 * 	for the given line.
 */
StyledTextEvent sendLineEvent(int eventType, int lineOffset, String line) {
	StyledTextEvent event = null;
	if (isListening(eventType)) {
		event = new StyledTextEvent(content);
		event.detail = lineOffset;
		event.text = line;
		event.alignment = alignment;
		event.indent = indent;
		event.justify = justify;
		notifyListeners(eventType, event);
	}
	return event;
}
void sendModifyEvent(Event event) {
	Accessible accessible = getAccessible();
	if (event.text.length() == 0) {
		accessible.textChanged(ACC.TEXT_DELETE, event.start, event.end - event.start);
	} else {
		if (event.start == event.end) {
			accessible.textChanged(ACC.TEXT_INSERT, event.start, event.text.length());
		} else {
			accessible.textChanged(ACC.TEXT_DELETE, event.start, event.end - event.start);
			accessible.textChanged(ACC.TEXT_INSERT, event.start, event.text.length());	
		}
	}
	notifyListeners(SWT.Modify, event);
}
/**
 * Sends the specified selection event.
 */
void sendSelectionEvent() {
	getAccessible().textSelectionChanged();
	Event event = new Event();
	event.x = selection.x;
	event.y = selection.y;
	notifyListeners(SWT.Selection, event);
}
/**
 * Sets whether the widget wraps lines.
 * This overrides the creation style bit SWT.WRAP.
 * <p>
 *
 * @param wrap true=widget wraps lines, false=widget does not wrap lines
 * @since 2.0
 */
public void setWordWrap(boolean wrap) {
	checkWidget();
	if ((getStyle() & SWT.SINGLE) != 0) return;
	if (wordWrap == wrap) return;
	wordWrap = wrap;
	setVariableLineHeight();
	resetCache(0, content.getLineCount());
	horizontalScrollOffset = 0;
	ScrollBar horizontalBar = getHorizontalBar();
	if (horizontalBar != null) {
		horizontalBar.setVisible(!wordWrap);
	}
	setScrollBars(true);
	setCaretLocation();
	super.redraw();
}
/**
 * Sets the receiver's caret.  Set the caret's height and location.
 * 
 * </p>
 * @param caret the new caret for the receiver
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setCaret(Caret caret) {
	checkWidget ();
	super.setCaret(caret);
	caretDirection = SWT.NULL;
	if (caret != null) {
		setCaretLocation();
	}
}
/**
 * @see org.eclipse.swt.widgets.Control#setBackground
 */
public void setBackground(Color color) {
	checkWidget();
	background = color;
	super.setBackground(getBackground());
	super.redraw();
}
/**
 * Sets the BIDI coloring mode.  When true the BIDI text display
 * algorithm is applied to segments of text that are the same
 * color.
 *
 * @param mode the new coloring mode
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * <p>
 * @deprecated use BidiSegmentListener instead.
 * </p>
 */
public void setBidiColoring(boolean mode) {
	checkWidget();
	bidiColoring = mode;
}
/**
 * Moves the Caret to the current caret offset.
 */
void setCaretLocation() {
	Point newCaretPos = getPointAtOffset(caretOffset);
	setCaretLocation(newCaretPos, getCaretDirection());
}
void setCaretLocation(Point location, int direction) {
	Caret caret = getCaret();
	if (caret != null) {
		int lineHeight = renderer.getLineHeight();
		int caretHeight = lineHeight;
		if (!isFixedLineHeight()) {
			int caretLine = content.getLineAtOffset(caretOffset);
			int lineOffset = content.getOffsetAtLine(caretLine);
			TextLayout layout = renderer.getTextLayout(caretLine);
			int lineInParagraph = layout.getLineIndex(caretOffset - lineOffset);
			caretHeight = layout.getLineBounds(lineInParagraph).height;
			renderer.disposeTextLayout(layout);
			if (caretHeight != lineHeight) {
				direction = SWT.DEFAULT;
			}
		}
		boolean updateImage = caret == defaultCaret;
		int imageDirection = direction;
		if (isMirrored()) {
			if (imageDirection == SWT.LEFT) {
				imageDirection = SWT.RIGHT;
			} else if (imageDirection == SWT.RIGHT) {
				imageDirection = SWT.LEFT;
			}
		}
		if (updateImage && imageDirection == SWT.RIGHT) {
			location.x -= (caret.getSize().x - 1);
		}
		caret.setBounds(location.x, location.y, 0, caretHeight);
		getAccessible().textCaretMoved(getCaretOffset());
		if (direction != caretDirection) {
			caretDirection = direction;
			if (updateImage) {
				if (imageDirection == SWT.DEFAULT) {
					defaultCaret.setImage(null);
				} else if (imageDirection == SWT.LEFT) {
					defaultCaret.setImage(leftCaretBitmap);
				} else if (imageDirection == SWT.RIGHT) {
					defaultCaret.setImage(rightCaretBitmap);
				}
			}
			if (caretDirection == SWT.LEFT) {
				BidiUtil.setKeyboardLanguage(BidiUtil.KEYBOARD_NON_BIDI);
			} else if (caretDirection == SWT.RIGHT) {
				BidiUtil.setKeyboardLanguage(BidiUtil.KEYBOARD_BIDI);
			}
		}
	}
	columnX = location.x;
}
/**
 * Sets the caret offset.
 *
 * @param offset caret offset, relative to the first character in the text.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when either the start or the end of the selection range is inside a 
 * multi byte line delimiter (and thus neither clearly in front of or after the line delimiter)
 * </ul>
 */
public void setCaretOffset(int offset) {
	checkWidget();
	int length = getCharCount();
	if (length > 0 && offset != caretOffset) {
		if (offset < 0) {
			caretOffset = 0;
		} else if (offset > length) {
			caretOffset = length;
		} else {
			if (isLineDelimiter(offset)) {
				// offset is inside a multi byte line delimiter. This is an 
				// illegal operation and an exception is thrown. Fixes 1GDKK3R
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			}
			caretOffset = offset;
		}
		// clear the selection if the caret is moved.
		// don't notify listeners about the selection change.
		clearSelection(false);
	}
	setCaretLocation();
}	
/**
 * Copies the specified text range to the clipboard.  The text will be placed
 * in the clipboard in plain text format and RTF format.
 * <p>
 *
 * @param start start index of the text
 * @param length length of text to place in clipboard
 * 
 * @exception SWTError, see Clipboard.setContents
 * @see org.eclipse.swt.dnd.Clipboard#setContents
 */
void setClipboardContent(int start, int length, int clipboardType) throws SWTError {
	if (clipboardType == DND.SELECTION_CLIPBOARD && !(IS_MOTIF || IS_GTK)) return;
	TextTransfer plainTextTransfer = TextTransfer.getInstance();
	TextWriter plainTextWriter = new TextWriter(start, length);
	String plainText = getPlatformDelimitedText(plainTextWriter);
	Object[] data;
	Transfer[] types;
	if (clipboardType == DND.SELECTION_CLIPBOARD) {
		data = new Object[]{plainText};
		types = new Transfer[]{plainTextTransfer};
	} else {
		RTFTransfer rtfTransfer = RTFTransfer.getInstance();
		RTFWriter rtfWriter = new RTFWriter(start, length);
		String rtfText = getPlatformDelimitedText(rtfWriter);
		data = new Object[]{rtfText, plainText};
		types = new Transfer[]{rtfTransfer, plainTextTransfer};
	}
	clipboard.setContents(data, types, clipboardType);
}
/**
 * Sets the content implementation to use for text storage.
 * <p>
 *
 * @param newContent StyledTextContent implementation to use for text storage.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when listener is null</li>
 * </ul>
 */
public void setContent(StyledTextContent newContent) {
	checkWidget();	
	if (newContent == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	if (content != null) {
		content.removeTextChangeListener(textChangeListener);
	}
	content = newContent;
	content.addTextChangeListener(textChangeListener);
	reset();
}
/**
 * Sets the receiver's cursor to the cursor specified by the
 * argument.  Overridden to handle the null case since the 
 * StyledText widget uses an ibeam as its default cursor.
 *
 * @see org.eclipse.swt.widgets.Control#setCursor
 */
public void setCursor (Cursor cursor) {
	if (cursor == null) {
		Display display = getDisplay();
		super.setCursor(display.getSystemCursor(SWT.CURSOR_IBEAM));
	} else {
		super.setCursor(cursor);
	}
}
/** 
 * Sets whether the widget implements double click mouse behavior.
 * </p>
 *
 * @param enable if true double clicking a word selects the word, if false
 * 	double clicks have the same effect as regular mouse clicks.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setDoubleClickEnabled(boolean enable) {
	checkWidget();
	doubleClickEnabled = enable;
}
/**
 * Sets whether the widget content can be edited.
 * </p>
 *
 * @param editable if true content can be edited, if false content can not be 
 * 	edited
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setEditable(boolean editable) {
	checkWidget();
	this.editable = editable;
}
/**
 * Sets a new font to render text with.
 * <p>
 * <b>NOTE:</b> Italic fonts are not supported unless they have no overhang
 * and the same baseline as regular fonts.
 * </p>
 *
 * @param font new font
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setFont(Font font) {
	checkWidget();
	int oldLineHeight = renderer.getLineHeight();
	super.setFont(font);	
	renderer.setFont(font, tabLength);
	// keep the same top line visible. fixes 5815
	if (isFixedLineHeight()) {
		int lineHeight = renderer.getLineHeight();
		if (lineHeight != oldLineHeight) {
			int vscroll = (getVerticalScrollOffset() * lineHeight / oldLineHeight) - getVerticalScrollOffset();
			scrollVertical(vscroll, true);
		}
	}
	resetCache(0, content.getLineCount());
	claimBottomFreeSpace();	
	calculateScrollBars();
	if (isBidiCaret()) createCaretBitmaps();
	caretDirection = SWT.NULL;
	setCaretLocation();
	super.redraw();
}
/**
 * @see org.eclipse.swt.widgets.Control#setForeground
 */
public void setForeground(Color color) {
	checkWidget();
	foreground = color;
	super.setForeground(getForeground());
	super.redraw();
}
/** 
 * Sets the horizontal scroll offset relative to the start of the line.
 * Do nothing if there is no text set.
 * <p>
 * <b>NOTE:</b> The horizontal index is reset to 0 when new text is set in the 
 * widget.
 * </p>
 *
 * @param offset horizontal scroll offset relative to the start 
 * 	of the line, measured in character increments starting at 0, if 
 * 	equal to 0 the content is not scrolled, if > 0 = the content is scrolled.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setHorizontalIndex(int offset) {
	checkWidget();
	int clientAreaWidth = getClientArea().width;
	if (getCharCount() == 0) {
		return;
	}	
	if (offset < 0) {
		offset = 0;
	}
	offset *= getHorizontalIncrement();
	// allow any value if client area width is unknown or 0. 
	// offset will be checked in resize handler.
	// don't use isVisible since width is known even if widget 
	// is temporarily invisible
	if (clientAreaWidth > 0) {
		int width = renderer.getWidth();
		// prevent scrolling if the content fits in the client area.
		// align end of longest line with right border of client area
		// if offset is out of range.
		if (offset > width - clientAreaWidth) {
			offset = Math.max(0, width - clientAreaWidth);
		}
	}
	scrollHorizontal(offset - horizontalScrollOffset, true);
}
/** 
 * Sets the horizontal pixel offset relative to the start of the line.
 * Do nothing if there is no text set.
 * <p>
 * <b>NOTE:</b> The horizontal pixel offset is reset to 0 when new text 
 * is set in the widget.
 * </p>
 *
 * @param pixel horizontal pixel offset relative to the start 
 * 	of the line.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 2.0
 */
public void setHorizontalPixel(int pixel) {
	checkWidget();
	if (getCharCount() == 0) {
		return;
	}	
	if (pixel < 0) {
		pixel = 0;
	}
	// allow any value if client area width is unknown or 0. 
	// offset will be checked in resize handler.
	// don't use isVisible since width is known even if widget 
	// is temporarily invisible
	int clientAreaWidth = getClientArea().width;
	if (clientAreaWidth > 0) {
		int width = renderer.getWidth();
		// prevent scrolling if the content fits in the client area.
		// align end of longest line with right border of client area
		// if offset is out of range.
		if (pixel > width - clientAreaWidth) {
			pixel = Math.max(0, width - clientAreaWidth);
		}
	}
	scrollHorizontal(pixel - horizontalScrollOffset, true);
}
/**
 * Sets the default indent of the receiver
 * <p>
 * 
 * @param lineIndent line indent
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul> 
 * @see #setLineIndent(int, int, int)
 * @since 3.2
 */
public void setIndent(int indent) {
	checkWidget();
	if (this.indent == indent || indent < 0) return;
	this.indent = indent;
	resetCache(0, content.getLineCount());
	setCaretLocation();
	super.redraw();	
}
/**
 * Set the default justify of the receiver
 * 
 * @param justify
 * 
 * @since 3.2
 */
public void setJustify(boolean justify) {
	checkWidget();
	if (this.justify == justify) return;
	this.justify = justify;
	resetCache(0, content.getLineCount());
	setCaretLocation();
	super.redraw();	
}
/**
 * Sets the alignment of the specified lines.
 * <p>
 *  
 * @param startLine first line the alignment is applied to, 0 based
 * @param lineCount number of lines the alignment applies to.
 * @param alignment line alignment
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when the specified line range is invalid</li>
 * </ul>
 * @see #setAlignment(int)
 * @since 3.2
 */
public void setLineAlignment(int startLine, int lineCount, int alignment) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
	if (startLine < 0 || startLine + lineCount > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}

	renderer.setLineAlignment(startLine, lineCount, alignment);
	resetCache(startLine, lineCount);
	redrawLines(startLine, lineCount);
	int caretLine = getCaretLine();
	if (startLine <= caretLine && caretLine < startLine + lineCount) {
		setCaretLocation();
	}
}
/** 
 * Sets the background color of the specified lines.
 * The background color is drawn for the width of the widget. All
 * line background colors are discarded when setText is called.
 * The text background color if defined in a StyleRange overlays the 
 * line background color. Should not be called if a LineBackgroundListener 
 * has been set since the listener maintains the line backgrounds.
 * <p>
 * Line background colors are maintained relative to the line text, not the 
 * line index that is specified in this method call.
 * During text changes, when entire lines are inserted or removed, the line 
 * background colors that are associated with the lines after the change 
 * will "move" with their respective text. An entire line is defined as 
 * extending from the first character on a line to the last and including the 
 * line delimiter. 
 * </p>
 * <p>
 * When two lines are joined by deleting a line delimiter, the top line 
 * background takes precedence and the color of the bottom line is deleted. 
 * For all other text changes line background colors will remain unchanged. 
 * </p>
 * 
 * @param startLine first line the color is applied to, 0 based
 * @param lineCount number of lines the color applies to.
 * @param background line background color
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when the specified line range is invalid</li>
 * </ul>
 */
public void setLineBackground(int startLine, int lineCount, Color background) {
	checkWidget();	
	if (isListening(LineGetBackground)) return;
	if (startLine < 0 || startLine + lineCount > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	
	renderer.setLineBackground(startLine, lineCount, background);
	redrawLines(startLine, lineCount);
}
/**
 * Sets the bullet of the specified lines.
 * <p>
 *  
 * @param startLine first line the bullet is applied to, 0 based
 * @param lineCount number of lines the bullet applies to.
 * @param indent line indent
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when the specified line range is invalid</li>
 * </ul>
 * @since 3.2
 */
void setLineBullet(int startLine, int lineCount, Bullet bullet) {
	checkWidget();
	if (isListening(LineGetStyle)) return;	
	if (startLine < 0 || startLine + lineCount > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}

	renderer.setLineBullet(startLine, lineCount, bullet);
	resetCache(startLine, lineCount);
	redrawLines(startLine, lineCount);
	int caretLine = getCaretLine();
	if (startLine <= caretLine && caretLine < startLine + lineCount) {
		setCaretLocation();
	}
}
void setVariableLineHeight () {
	fixedLineHeight = false;
}
/**
 * Sets the indent of the specified lines.
 * <p>
 *  
 * @param startLine first line the indent is applied to, 0 based
 * @param lineCount number of lines the indent applies to.
 * @param indent line indent
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when the specified line range is invalid</li>
 * </ul>
 * @see #setIndent(int)
 * @since 3.2
 */
public void setLineIndent(int startLine, int lineCount, int indent) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
	if (startLine < 0 || startLine + lineCount > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}

	renderer.setLineIndent(startLine, lineCount, indent);
	resetCache(startLine, lineCount);
	redrawLines(startLine, lineCount);
	int caretLine = getCaretLine();
	if (startLine <= caretLine && caretLine < startLine + lineCount) {
		setCaretLocation();
	}
}
/**
 * Sets the justify of the specified lines.
 * <p>
 *  
 * @param startLine first line the justify is applied to, 0 based
 * @param lineCount number of lines the justify applies to.
 * @param indent line indent
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when the specified line range is invalid</li>
 * </ul>
 * @see #setJustify(boolean)
 * @since 3.2
 */
public void setLineJustify(int startLine, int lineCount, boolean justify) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
	if (startLine < 0 || startLine + lineCount > content.getLineCount()) {
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}

	renderer.setLineJustify(startLine, lineCount, justify);
	resetCache(startLine, lineCount);
	redrawLines(startLine, lineCount);
	int caretLine = getCaretLine();
	if (startLine <= caretLine && caretLine < startLine + lineCount) {
		setCaretLocation();
	}
}
/**
 * Sets the line spacing of the receiver
 * <p>
 * 
 * @param lineSpacing line spacing
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 3.2
 */
public void setLineSpacing(int lineSpacing) {
	checkWidget();
	if (this.lineSpacing == lineSpacing || lineSpacing < 0) return;
	this.lineSpacing = lineSpacing;	
	setVariableLineHeight();
	resetCache(0, content.getLineCount());
	setCaretLocation();
	super.redraw();
}
void setMargins (int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
	checkWidget();
	this.leftMargin = leftMargin;
	this.topMargin = topMargin;
	this.rightMargin = rightMargin;
	this.bottomMargin = bottomMargin;
	setCaretLocation();
}
/**
 * Flips selection anchor based on word selection direction.
 */
void setMouseWordSelectionAnchor() {
	if (mouseDoubleClick) {
		if (caretOffset < doubleClickSelection.x) {
			selectionAnchor = doubleClickSelection.y;
		} else if (caretOffset > doubleClickSelection.y) {
			selectionAnchor = doubleClickSelection.x;
		}
	}
}
/**
 * Sets the orientation of the receiver, which must be one
 * of the constants <code>SWT.LEFT_TO_RIGHT</code> or <code>SWT.RIGHT_TO_LEFT</code>.
 * <p>
 *
 * @param orientation new orientation style
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * 
 * @since 2.1.2
 */
public void setOrientation(int orientation) {
	if ((orientation & (SWT.RIGHT_TO_LEFT | SWT.LEFT_TO_RIGHT)) == 0) { 
		return;
	}
	if ((orientation & SWT.RIGHT_TO_LEFT) != 0 && (orientation & SWT.LEFT_TO_RIGHT) != 0) {
		return;	
	}
	if ((orientation & SWT.RIGHT_TO_LEFT) != 0 && isMirrored()) {
		return;	
	} 
	if ((orientation & SWT.LEFT_TO_RIGHT) != 0 && !isMirrored()) {
		return;
	}
	if (!BidiUtil.setOrientation(handle, orientation)) {
		return;
	}
	isMirrored = (orientation & SWT.RIGHT_TO_LEFT) != 0;
	caretDirection = SWT.NULL;
	setCaretLocation();
	keyActionMap.clear();
	createKeyBindings();
	super.redraw();
}
/**
 * Adjusts the maximum and the page size of the scroll bars to 
 * reflect content width/length changes.
 * 
 * @param vertical indicates if the vertical scrollbar also needs to be set 
 */
void setScrollBars(boolean vertical) {
	int inactive = 1;
	Rectangle clientArea = getClientArea();
	if (vertical || !isFixedLineHeight()) {
		ScrollBar verticalBar = getVerticalBar();
		if (verticalBar != null) {
			int maximum = renderer.getHeight();
			// only set the real values if the scroll bar can be used 
			// (ie. because the thumb size is less than the scroll maximum)
			// avoids flashing on Motif, fixes 1G7RE1J and 1G5SE92
			if (clientArea.height < maximum) {
				verticalBar.setValues(
					verticalBar.getSelection(),
					verticalBar.getMinimum(),
					maximum,
					clientArea.height,				// thumb size
					verticalBar.getIncrement(),
					clientArea.height);				// page size
			} else if (verticalBar.getThumb() != inactive || verticalBar.getMaximum() != inactive) {
				verticalBar.setValues(
					verticalBar.getSelection(),
					verticalBar.getMinimum(),
					inactive,
					inactive,
					verticalBar.getIncrement(),
					inactive);
			}
		}
	}
	ScrollBar horizontalBar = getHorizontalBar();
	if (horizontalBar != null && horizontalBar.getVisible()) {
		int maximum = renderer.getWidth();
		// only set the real values if the scroll bar can be used 
		// (ie. because the thumb size is less than the scroll maximum)
		// avoids flashing on Motif, fixes 1G7RE1J and 1G5SE92
		if (clientArea.width < maximum) {
			horizontalBar.setValues(
				horizontalBar.getSelection(),
				horizontalBar.getMinimum(),
				maximum,
				clientArea.width - leftMargin - rightMargin,	// thumb size
				horizontalBar.getIncrement(),
				clientArea.width - leftMargin - rightMargin);	// page size
		} else if (horizontalBar.getThumb() != inactive || horizontalBar.getMaximum() != inactive) {
			horizontalBar.setValues(
				horizontalBar.getSelection(),
				horizontalBar.getMinimum(),
				inactive,
				inactive,
				horizontalBar.getIncrement(),
				inactive);
		}
	}
}
/** 
 * Sets the selection to the given position and scrolls it into view.  Equivalent to setSelection(start,start).
 * <p>
 *
 * @param start new caret position
 * @see #setSelection(int,int)
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when either the start or the end of the selection range is inside a 
 * multi byte line delimiter (and thus neither clearly in front of or after the line delimiter)
 * </ul> 
 */
public void setSelection(int start) {
	// checkWidget test done in setSelectionRange	
	setSelection(start, start);
}
/** 
 * Sets the selection and scrolls it into view.
 * <p>
 * Indexing is zero based.  Text selections are specified in terms of
 * caret positions.  In a text widget that contains N characters, there are 
 * N+1 caret positions, ranging from 0..N
 * </p>
 *
 * @param point x=selection start offset, y=selection end offset
 * 	The caret will be placed at the selection start when x > y.
 * @see #setSelection(int,int)
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_NULL_ARGUMENT when point is null</li>
 *   <li>ERROR_INVALID_ARGUMENT when either the start or the end of the selection range is inside a 
 * multi byte line delimiter (and thus neither clearly in front of or after the line delimiter)
 * </ul> 
 */
public void setSelection(Point point) {
	checkWidget();
	if (point == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);	
	setSelection(point.x, point.y);
}
/**
 * Sets the receiver's selection background color to the color specified
 * by the argument, or to the default system color for the control
 * if the argument is null.
 *
 * @param color the new color (or null)
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the argument has been disposed</li> 
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 2.1
 */
public void setSelectionBackground (Color color) {
	checkWidget ();
	if (color != null) {
		if (color.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	selectionBackground = color;
	super.redraw();
}
/**
 * Sets the receiver's selection foreground color to the color specified
 * by the argument, or to the default system color for the control
 * if the argument is null.
 *
 * @param color the new color (or null)
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the argument has been disposed</li> 
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 2.1
 */
public void setSelectionForeground (Color color) {
	checkWidget ();
	if (color != null) {
		if (color.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	selectionForeground = color;
	super.redraw();
}
/** 
 * Sets the selection and scrolls it into view.
 * <p>
 * Indexing is zero based.  Text selections are specified in terms of
 * caret positions.  In a text widget that contains N characters, there are 
 * N+1 caret positions, ranging from 0..N
 * </p>
 *
 * @param start selection start offset. The caret will be placed at the 
 * 	selection start when start > end.
 * @param end selection end offset
 * @see #setSelectionRange(int,int)
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when either the start or the end of the selection range is inside a 
 * multi byte line delimiter (and thus neither clearly in front of or after the line delimiter)
 * </ul>
 */
public void setSelection(int start, int end) {
	// checkWidget test done in setSelectionRange
	setSelectionRange(start, end - start);
//	showSelection();
}
/** 
 * Sets the selection. 
 * The new selection may not be visible. Call showSelection to scroll 
 * the selection into view.
 * <p>
 *
 * @param start offset of the first selected character, start >= 0 must be true.
 * @param length number of characters to select, 0 <= start + length 
 * 	<= getCharCount() must be true. 
 * 	A negative length places the caret at the selection start.
 * @param sendEvent a Selection event is sent when set to true and when 
 * 	the selection is reset.
 */
void setSelection(int start, int length, boolean sendEvent) {
	int end = start + length;
	if (start > end) {
		int temp = end;
		end = start;
		start = temp;
	}
	// is the selection range different or is the selection direction 
	// different?
	if (selection.x != start || selection.y != end || 
		(length > 0 && selectionAnchor != selection.x) || 
		(length < 0 && selectionAnchor != selection.y)) {
		clearSelection(sendEvent);
		if (length < 0) {
			selectionAnchor = selection.y = end;
			caretOffset = selection.x = start;
		} else {
			selectionAnchor = selection.x = start;
			caretOffset = selection.y = end;
		}
		internalRedrawRange(selection.x, selection.y - selection.x);
	}
}
/** 
 * Sets the selection. The new selection may not be visible. Call showSelection to scroll 
 * the selection into view. A negative length places the caret at the visual start of the 
 * selection. <p>
 *
 * @param start offset of the first selected character
 * @param length number of characters to select
 * 
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_ARGUMENT when either the start or the end of the selection range is inside a 
 * multi byte line delimiter (and thus neither clearly in front of or after the line delimiter)
 * </ul>
 */
public void setSelectionRange(int start, int length) {
	checkWidget();
	int contentLength = getCharCount();
	start = Math.max(0, Math.min (start, contentLength));
	int end = start + length;
	if (end < 0) {
		length = -start;
	} else {
		if (end > contentLength) length = contentLength - start;
	}
	if (isLineDelimiter(start) || isLineDelimiter(start + length)) {
		// the start offset or end offset of the selection range is inside a 
		// multi byte line delimiter. This is an illegal operation and an exception 
		// is thrown. Fixes 1GDKK3R
		SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}
	setSelection(start, length, false);
	setCaretLocation();
}
/** 
 * Adds the specified style. The new style overwrites existing styles for the
 * specified range.  Existing style ranges are adjusted if they partially 
 * overlap with the new style, To clear an individual style, call setStyleRange 
 * with a StyleRange that has null attributes. 
 * <p>
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * </p>
 *
 * @param range StyleRange object containing the style information.
 * Overwrites the old style in the given range. May be null to delete
 * all styles.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_INVALID_RANGE when the style range is outside the valid range (> getCharCount())</li> 
 * </ul>
 */
public void setStyleRange(StyleRange range) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
	if (range != null) {
		setStyleRanges(range.start, 0, null, new StyleRange[]{range}, false);
	} else {
		setStyleRanges(0, 0, null, null, true);
	}
}
/**
 * Sets an arrays of styles in the receiver.
 * 
 * <p>
 * @param start
 * @param length
 * @param ranges
 * @param styles
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 3.2 
 */
public void setStyleRanges(int start, int length, int[] ranges, StyleRange[] styles) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
	setStyleRanges(start, length, ranges, styles, false);
}
/**
 * Sets an arrays of styles in the receiver.
 * 
 * <p>
 * @param ranges
 * @param styles
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 3.2 
 */
public void setStyleRanges(int[] ranges, StyleRange[] styles) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
	setStyleRanges(0, 0, ranges, styles, true);
}
void setStyleRanges(int start, int length, int[] ranges, StyleRange[] styles, boolean reset) {
	int charCount = content.getCharCount();
	int end = start + length; // -1 TODO ?
	if (start > end || start < 0) {
		SWT.error(SWT.ERROR_INVALID_RANGE);
	}
	if (styles != null) {
		if (end > charCount) {
			SWT.error(SWT.ERROR_INVALID_RANGE);
		}
		if (ranges != null) {
			if (ranges.length != styles.length << 1) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
		int lastOffset = 0;
		boolean variableHeight = false; 
		for (int i = 0; i < styles.length; i ++) {
			if (styles[i] == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			int rangeStart, rangeLength;
			if (ranges != null) {
				rangeStart = ranges[i << 1];
				rangeLength = ranges[(i << 1) + 1];
			} else {
				rangeStart = styles[i].start;
				rangeLength = styles[i].length;
			}
			if (rangeLength <= 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT); 
			if (!(0 <= rangeStart && rangeStart + rangeLength <= charCount)) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			if (lastOffset > rangeStart) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			variableHeight |= styles[i].isVariableHeight();
			lastOffset = rangeStart + rangeLength;
		}
		if (variableHeight) {
			setVariableLineHeight();
			//TODO Init idle ?
		}
	}
	int rangeStart = start, rangeEnd = end;
	if (styles != null && styles.length > 0) {
		if (ranges != null) {
			rangeStart = ranges[0];
			rangeEnd = ranges[ranges.length - 2] + ranges[ranges.length - 1];
		} else {
			rangeStart = styles[0].start;
			rangeEnd = styles[styles.length - 1].start + styles[styles.length - 1].length;
		}
	}
	int lastLineBottom = 0;
	if (!isFixedLineHeight()) {
		int lineEnd = content.getLineAtOffset(Math.max(end, rangeEnd));
		lastLineBottom = getLinePixel(lineEnd + 1);
	}
	if (reset) {
		renderer.setStyleRanges(null, null);
	} else {
		renderer.updateRanges(start, length, length);
	}
	if (styles != null && styles.length > 0) {
		renderer.setStyleRanges(ranges, styles);
	}
	if (reset) {
		resetCache(0, content.getLineCount());
		super.redraw();
	} else {
		int lineStart = content.getLineAtOffset(Math.min(start, rangeStart));
		int lineEnd = content.getLineAtOffset(Math.max(end, rangeEnd));
		resetCache(lineStart, lineEnd - lineStart + 1);
		int partialTopIndex = getPartialTopIndex();
		int partialBottomIndex = getPartialBottomIndex();
		if (!(lineStart > partialBottomIndex || lineEnd < partialTopIndex)) {
			Rectangle rect = getClientArea();
			if (partialTopIndex <= lineStart && lineStart <= partialBottomIndex) {
				int lineTop = Math.max(rect.y, getLinePixel(lineStart));
				rect.y = lineTop;
				rect.height -= lineTop;
			} 
			if (partialTopIndex <= lineEnd && lineEnd <= partialBottomIndex) {
				int newLastLineBottom = getLinePixel(lineEnd + 1);
				if (!isFixedLineHeight() && lastLineBottom != newLastLineBottom) {
					//TODO fails if margin != 0
					scroll(0, newLastLineBottom, 0, lastLineBottom, rect.width, rect.height - newLastLineBottom, true);
				}
				rect.height = newLastLineBottom - rect.y;
			}
			super.redraw(rect.x, rect.y, rect.width, rect.height, false);		
		}
	}
	setCaretLocation();
}
/** 
 * Sets styles to be used for rendering the widget content. All styles 
 * in the widget will be replaced with the given set of styles.
 * <p>
 * Should not be called if a LineStyleListener has been set since the 
 * listener maintains the styles.
 * </p>
 *
 * @param ranges StyleRange objects containing the style information.
 * The ranges should not overlap. The style rendering is undefined if 
 * the ranges do overlap. Must not be null. The styles need to be in order.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when the list of ranges is null</li>
 *    <li>ERROR_INVALID_RANGE when the last of the style ranges is outside the valid range (> getCharCount())</li> 
 * </ul>
 */
public void setStyleRanges(StyleRange[] ranges) {
	checkWidget();
	if (isListening(LineGetStyle)) return;
 	if (ranges == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	setStyleRanges(0, 0, null, ranges, true);
}
/** 
 * Sets the tab width. 
 * <p>
 *
 * @param tabs tab width measured in characters.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setTabs(int tabs) {
	checkWidget();	
	tabLength = tabs;
	renderer.setFont(renderer.getFont(SWT.NORMAL), tabs);
	if (caretOffset > 0) {
		caretOffset = 0;
		showCaret();
		clearSelection(false);
	}
	resetCache(0, content.getLineCount());
	super.redraw();
}
/** 
 * Sets the widget content. 
 * If the widget has the SWT.SINGLE style and "text" contains more than 
 * one line, only the first line is rendered but the text is stored 
 * unchanged. A subsequent call to getText will return the same text 
 * that was set.
 * <p>
 * <b>Note:</b> Only a single line of text should be set when the SWT.SINGLE 
 * style is used.
 * </p>
 *
 * @param text new widget content. Replaces existing content. Line styles 
 * 	that were set using StyledText API are discarded.  The
 * 	current selection is also discarded.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT when string is null</li>
 * </ul>
 */
public void setText(String text) {
	checkWidget();
	if (text == null) {
		SWT.error(SWT.ERROR_NULL_ARGUMENT);
	}
	Event event = new Event();
	event.start = 0;
	event.end = getCharCount();
	event.text = text;
	event.doit = true;	
	notifyListeners(SWT.Verify, event);
	if (event.doit) {
		StyledTextEvent styledTextEvent = null;
		if (isListening(ExtendedModify)) {
			styledTextEvent = new StyledTextEvent(content);
			styledTextEvent.start = event.start;
			styledTextEvent.end = event.start + event.text.length();
			styledTextEvent.text = content.getTextRange(event.start, event.end - event.start);
		}
		content.setText(event.text);
		sendModifyEvent(event);	
		if (styledTextEvent != null) {
			notifyListeners(ExtendedModify, styledTextEvent);
		}
	}
}
/**
 * Sets the text limit to the specified number of characters.
 * <p>
 * The text limit specifies the amount of text that
 * the user can type into the widget.
 * </p>
 *
 * @param limit the new text limit.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @exception IllegalArgumentException <ul>
 *   <li>ERROR_CANNOT_BE_ZERO when limit is 0</li>
 * </ul>
 */
public void setTextLimit(int limit) {
	checkWidget();
	if (limit == 0) {
		SWT.error(SWT.ERROR_CANNOT_BE_ZERO);
	}
	textLimit = limit;
}
/**
 * Sets the top index. Do nothing if there is no text set.
 * <p>
 * The top index is the index of the line that is currently at the top 
 * of the widget. The top index changes when the widget is scrolled.
 * Indexing starts from zero.
 * Note: The top index is reset to 0 when new text is set in the widget.
 * </p>
 *
 * @param topIndex new top index. Must be between 0 and 
 * 	getLineCount() - fully visible lines per page. If no lines are fully 
 * 	visible the maximum value is getLineCount() - 1. An out of range 
 * 	index will be adjusted accordingly.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setTopIndex(int topIndex) {
	checkWidget();
	if (getCharCount() == 0) {
		return;
	}
	int lineCount = content.getLineCount();
	//TODO broken for variable line height
	int pageSize = Math.max(1, Math.min(lineCount, getLineCountWhole()));
	if (topIndex < 0) {
		topIndex = 0;
	} else if (topIndex > lineCount - pageSize) {
		topIndex = lineCount - pageSize;
	}
	int pixel = getLinePixel(topIndex);
	scrollVertical(pixel, true);
}
/**
 * Sets the top pixel offset. Do nothing if there is no text set.
 * <p>
 * The top pixel offset is the vertical pixel offset of the widget. The
 * widget is scrolled so that the given pixel position is at the top.
 * The top index is adjusted to the corresponding top line.
 * Note: The top pixel is reset to 0 when new text is set in the widget.
 * </p>
 *
 * @param pixel new top pixel offset. Must be between 0 and 
 * 	(getLineCount() - visible lines per page) / getLineHeight()). An out
 * 	of range offset will be adjusted accordingly.
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 * @since 2.0
 */
public void setTopPixel(int pixel) {
	checkWidget();
	if (getCharCount() == 0) {
		return;
	}	
	if (pixel < 0) pixel = 0;
	int lineCount = content.getLineCount();
	int height = getClientArea().height;
	if (isFixedLineHeight()) {
		int maxTopPixel = Math.max(0, lineCount * getVerticalIncrement() - height);
		if (pixel > maxTopPixel) pixel = maxTopPixel;
	} else {
		if (pixel > getVerticalScrollOffset()) {
			int bottomIndex = getLineIndex(height) + 1;
			int bottomPixel = getLinePixel(bottomIndex);
			while (pixel + height > bottomPixel && lineCount > bottomIndex) {
				bottomPixel += renderer.getLineHeight(bottomIndex++);
			}
			if (pixel + height > bottomPixel) pixel = bottomPixel - height; 
		}
	}
	scrollVertical(pixel - getVerticalScrollOffset(), true);
}
/**
 * Scrolls the specified location into view.
 * <p>
 * 
 * @param x the x coordinate that should be made visible.
 * @param line the line that should be made visible. Relative to the
 *	first line in the document.
 * @return 
 *	true=the widget was scrolled to make the specified location visible. 
 *	false=the specified location is already visible, the widget was 
 *	not scrolled. 	
 */
boolean showLocation(Point point) {
	Rectangle clientArea = getClientArea(); 
	int clientAreaWidth = clientArea.width - leftMargin;
	int horizontalIncrement = clientAreaWidth / 4;
	boolean scrolled = false;
	if (point.x < leftMargin) {
		// always make 1/4 of a page visible
		point.x = Math.max(-horizontalScrollOffset, point.x - horizontalIncrement);	
		scrolled = scrollHorizontal(point.x, true);
	} else if (point.x >= clientAreaWidth) {
		// always make 1/4 of a page visible
		point.x = Math.min(renderer.getWidth() - horizontalScrollOffset, point.x + horizontalIncrement);
		scrolled = scrollHorizontal(point.x - clientAreaWidth, true);
	}
	if (point.y < topMargin) {
		scrolled = scrollVertical(point.y - topMargin, true);
	} else if (point.y >= clientArea.height - bottomMargin) {
		scrolled = scrollVertical(point.y - clientArea.height + bottomMargin, true);
	}
	return scrolled;
}
/**
 * Sets the caret location and scrolls the caret offset into view.
 */
void showCaret() {
	Point newCaretPos = getPointAtOffset(caretOffset);	
	boolean scrolled = showLocation(newCaretPos);
	
	//TODO FIX ME - assure caret bottom is visible
	if (!scrolled) {
		int caretLine = content.getLineAtOffset(caretOffset);
		int lineOffset = content.getOffsetAtLine(caretLine);
		TextLayout layout = renderer.getTextLayout(caretLine);
		Point point = new Point(newCaretPos.x, newCaretPos.y);
		int offsetInLine = caretOffset - lineOffset;
		point.y += layout.getLineBounds(layout.getLineIndex(offsetInLine)).height;
		renderer.disposeTextLayout(layout);
		scrolled = showLocation(point);
	}
	
	if (!scrolled) {
		// set the caret location if a scroll operation did not set it
		setCaretLocation(newCaretPos, getCaretDirection());
	}
}
/**
 * Scrolls the selection into view.  The end of the selection will be scrolled into
 * view.  Note that if a right-to-left selection exists, the end of the selection is the
 * visual beginning of the selection (i.e., where the caret is located).
 * <p>
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void showSelection() {
	checkWidget();
	// is selection from right-to-left?
	boolean rightToLeft = caretOffset == selection.x;
	int startOffset, endOffset;
	if (rightToLeft) {
		startOffset = selection.y;
		endOffset = selection.x;
	} else {
		startOffset = selection.x;
		endOffset = selection.y;
	}
	Point startPos = getPointAtOffset(startOffset);	
	Point endPos = getPointAtOffset(endOffset);	
	//TODO endPos.y should be the bottom of the line not the top
	
	// can the selection be fully displayed within the widget's visible width?
	int w = getClientArea().width;
	boolean selectionFits = rightToLeft ? startPos.x - endPos.x <= w : endPos.x - startPos.x <= w;
	if (selectionFits) {
		// show as much of the selection as possible by first showing
		// the start of the selection
		if (showLocation(startPos)) {
			// endX value could change if showing startX caused a scroll to occur
			endPos = getPointAtOffset(endOffset);	
		}
		showLocation(endPos);
	} else {
		// just show the end of the selection since the selection start 
		// will not be visible
		showLocation(endPos);
	}
}
/**
 * Updates the selection and caret position depending on the text change.
 * If the selection intersects with the replaced text, the selection is 
 * reset and the caret moved to the end of the new text.
 * If the selection is behind the replaced text it is moved so that the
 * same text remains selected.  If the selection is before the replaced text 
 * it is left unchanged.
 * <p>
 *
 * @param startOffset offset of the text change
 * @param replacedLength length of text being replaced
 * @param newLength length of new text
 */
void updateSelection(int startOffset, int replacedLength, int newLength) {
	if (selection.y <= startOffset) {
		// selection ends before text change
		return;
	}
	if (selection.x < startOffset) {
		// clear selection fragment before text change
		internalRedrawRange(selection.x, startOffset - selection.x);
	}
	if (selection.y > startOffset + replacedLength && selection.x < startOffset + replacedLength) {
		// clear selection fragment after text change.
		// do this only when the selection is actually affected by the 
		// change. Selection is only affected if it intersects the change (1GDY217).
		int netNewLength = newLength - replacedLength;
		int redrawStart = startOffset + newLength;
		internalRedrawRange(redrawStart, selection.y + netNewLength - redrawStart);
	}
	if (selection.y > startOffset && selection.x < startOffset + replacedLength) {
		// selection intersects replaced text. set caret behind text change
		setSelection(startOffset + newLength, 0, true);
	} else {
		// move selection to keep same text selected
		setSelection(selection.x + newLength - replacedLength, selection.y - selection.x, true);
	}
	setCaretLocation();
}
}
