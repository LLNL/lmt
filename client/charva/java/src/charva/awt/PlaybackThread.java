/* class PlaybackThread
 *
 * Copyright (C) 2001  R M Pitman
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package charva.awt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.StringTokenizer;

/**
 * This class reads a scriptfile line by line, parses each line
 * into a time-interval and a gesture-specifier (either KEY or MOUSE), and
 * fires the specified keystroke (or mouse-click) after the specified delay.
 */
public class PlaybackThread extends Thread {

    private File _scriptFile;
    private Toolkit _toolkit;
    private int numberOfLoops = 1;
    private long playbackRate = 1L;
    private static final Log LOG = LogFactory.getLog(PlaybackThread.class);

    PlaybackThread(File scriptFile) {
        _scriptFile = scriptFile;
        _toolkit = Toolkit.getDefaultToolkit();
        String loops = System.getProperty("charva.script.playbackLoops");
        if (loops != null) {
            try {
                numberOfLoops = Integer.parseInt(loops);
            } catch (NumberFormatException e) {
                LOG.warn("Property charva.script.playbackLoops (value=[" + loops + "" +
                        "]) must be an integer!");
            }
            if (numberOfLoops <= 0) {
                LOG.warn("Property charva.script.playbackLoops (value=[" + loops + "" +
                        "]) must be greater than 0!");
                numberOfLoops = 1;
            }
        }
        String rate = System.getProperty("charva.script.playbackRate");
        if (rate != null) {
            playbackRate = Long.parseLong(rate);
            if (playbackRate < 1L) {
                LOG.warn("Property charva.script.playbackRate (value=" + rate +
                        ") must be greater than 1!");
                playbackRate = 1L;
            }
        }
    }

    public void run() {

        try {
            for (int i = 0; i < numberOfLoops; i++) {
                LOG.debug("Starting script loop " + (i + 1) + " out of " + numberOfLoops);
                runScriptOnce(_scriptFile);
                LOG.debug("Ended script loop " + (i + 1));
            }
        } catch (IOException e) {
            LOG.warn("Error reading script file: " + e.getMessage());
        }

    }

    private void runScriptOnce(File scriptfile) throws IOException {
        String line;
        int lineno = 0;
        BufferedReader scriptReader = null;
        try {
            scriptReader = new BufferedReader(new FileReader(scriptfile));
        } catch (FileNotFoundException e) {
            // should never happen, because we checked the file for readability
        }

        while ((line = scriptReader.readLine()) != null) {

            lineno++;
            StringTokenizer st = new StringTokenizer(line);
            String delayToken = st.nextToken();
            long delay = Long.parseLong(delayToken);
            // Reduce the duration of pauses that are longer than half a second
            if (delay > 500L)
                delay = (delay / playbackRate);

            String gestureToken = st.nextToken();
            if (gestureToken.equals("KEY")) {
                String keycodeToken = st.nextToken();
                int keycode = Integer.parseInt(keycodeToken, 16);

                if (delay != 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ei) {
                    }
                }

                _toolkit.fireKeystroke(keycode);

            } else if (gestureToken.equals("MOUSE")) {
                // It wasn't a keystroke, it must have been a mouse-click
                String buttonToken = st.nextToken();
                int button = Integer.parseInt(buttonToken);
                String xToken = st.nextToken();
                int x = Integer.parseInt(xToken);
                String yToken = st.nextToken();
                int y = Integer.parseInt(yToken);
                MouseEventInfo mouseEventInfo = new MouseEventInfo(button, x, y);

                if (delay != 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ei) {
                    }
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Mouse-click (button " + button +
                            ") at (" + mouseEventInfo.getX() + "," + mouseEventInfo.getY() + ")");
                }
                _toolkit.fireMouseEvent(mouseEventInfo);
            } else {
                throw new IOException("Parse error [" + line + "] on line " + lineno +
                        " of script file " + scriptfile.getAbsolutePath());
            }
        }
        scriptReader.close();
    }

}
