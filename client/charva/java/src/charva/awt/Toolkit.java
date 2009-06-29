/* class Toolkit
 *
 * Copyright (C) 2001, 2002, 2003  R M Pitman
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

/*
 * Modified Jul 14, 2003 by Tadpole Computer, Inc.
 * Modifications Copyright 2003 by Tadpole Computer, Inc.
 *
 * Modifications are hereby licensed to all parties at no charge under
 * the same terms as the original.
 *
 * Modifications include minor bug fixes, and moving the handling of
 * endwin() into an atexit(3) function call.
 */

package charva.awt;

import charva.awt.event.AWTEvent;
import charva.awt.event.GarbageCollectionEvent;
import charva.awt.event.KeyEvent;
import charva.awt.event.MouseEvent;
import charva.awt.event.FocusEvent;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The Toolkit class provides the interface to the "ncurses" library
 * functions. Exceptions and error messages are reported to the
 * logfile $HOME/charva.log.<p>
 * <p/>
 * The default colors are white foreground and black background, but
 * these defaults can be modified by changing the static variables
 * _defaultForeground and _defaultBackground.
 */

/**
 * Correct manage of FocusEvents
    public FocusEvent getLastFocusEvent()
    protected void setLastFocusEvent(FocusEvent ev_ )
 */
public class Toolkit {

    private static final Log LOG = LogFactory.getLog(Toolkit.class);

    /**
     * The constructor can only be called by the getDefaultToolkit() method,
     * making this an example of the Singleton pattern.
     */
    private Toolkit() {
        _evtQueue = EventQueue.getInstance();

        /* If the terminal is capable of handling colors, initialize the
         * color capability and the first color-pair (the default
         * foreground and background colors are white-on-black, but
         * can be modified by setting the static variables _defaultForeground
         * and _defaultBackground.
         */
        if (this.hasColors() && Toolkit.isColorEnabled) {
            startColors();
            _colorPairs.add(new ColorPair(_defaultForeground, _defaultBackground));
        }
    }

    /**
     * This static method instantiates a Toolkit object if it does not
     * already exist; and returns a reference to the instantiated Toolkit.
     */
    public static Toolkit getDefaultToolkit() {
        if (_instance == null) {
            _instance = new Toolkit();
        }
        return _instance;
    }

    public EventQueue getSystemEventQueue() {
        return _evtQueue;
    }

    /**
     * Get the top window of the window stack.
     */
    public Window getTopWindow() {
        return (Window) _windowList.lastElement();
    }

    /**
     * Returns true if the specified window is currently displayed.
     */
    public boolean isWindowDisplayed(Window window_) {
        boolean answer = false;
        synchronized (_windowList) {
            for (int i = 0; i < _windowList.size(); i++) {
                Window w = (Window) _windowList.elementAt(i);
                if (w == window_) {
                    answer = true;
                    break;
                }
            }
        }
        return answer;
    }

    /**
     * Processes a keystroke that was pressed in the currently displayed
     * window.
     *
     * @param key_ the keystroke that was pressed. If it is less than
     *             256, it is a ASCII or ISO8859-1 code; otherwise it is a function
     *             key as defined in the "VK_*" values.
     */
    public void fireKeystroke(int key_) {
        Component currentFocus;

        synchronized (_windowList) {

            Window sourcewin = getTopWindow();

            /* Get the (non-Container) component within the source window
             * that generated the keystroke.
             */
            currentFocus = sourcewin.getCurrentFocus();
            while (currentFocus instanceof Container) {
                currentFocus = ((Container) currentFocus).getCurrentFocus();
            }
        }

        fireKeystroke(key_, currentFocus);
    }

    /**
     * Process a keystroke as if it was pressed while the focus was in the
     * specified component.
     *
     * @param key_ the keystroke that was pressed. If it is less than
     *             256, it is an ASCII or ISO8859-1 code; otherwise it is a function
     *             key as defined in the "VK_*" values.
     */
    public void fireKeystroke(int key_, Component source_) {
        int id;
        if (Toolkit.isActionKey(key_))
            id = AWTEvent.KEY_TYPED;
        else
            id = AWTEvent.KEY_PRESSED;

        _evtQueue.postEvent(new KeyEvent(key_, id, source_));
    }

    public FocusEvent getLastFocusEvent() {
        return _lastFocusEvent;
    }

    protected void setLastFocusEvent(FocusEvent ev_ ) {
        _lastFocusEvent = ev_;
    }

    /**
     * Processes the mouse-click specified by mouse_info.
     * Note that we disable mouse-click resolution in the ncurses library
     * by setting "mouse_interval" to 0, because it doesn't appear to work properly.
     * Instead we do it in this method.
     */
    public void fireMouseEvent(MouseEventInfo mouse_info) {
        Window top_window = getTopWindow();
        Point origin = top_window.getLocation();
        int x = mouse_info.x;
        int y = mouse_info.y;
        int button = 0;
        int modifiers = 0;
        switch (mouse_info.button) {
            case BUTTON1_PRESSED:
                button = MouseEvent.BUTTON1;
                modifiers = MouseEvent.MOUSE_PRESSED;
                break;
            case BUTTON1_RELEASED:
                button = MouseEvent.BUTTON1;
                modifiers = MouseEvent.MOUSE_RELEASED;
                break;
            case BUTTON2_PRESSED:
                button = MouseEvent.BUTTON2;
                modifiers = MouseEvent.MOUSE_PRESSED;
                break;
            case BUTTON2_RELEASED:
                button = MouseEvent.BUTTON2;
                modifiers = MouseEvent.MOUSE_RELEASED;
                break;
            case BUTTON3_PRESSED:
                button = MouseEvent.BUTTON3;
                modifiers = MouseEvent.MOUSE_PRESSED;
                break;
            case BUTTON3_RELEASED:
                button = MouseEvent.BUTTON3;
                modifiers = MouseEvent.MOUSE_RELEASED;
                break;
        }

        // If the mouse has been pressed outside the top-level window
        if (!top_window.contains(x, y)) {
            if (modifiers == MouseEvent.MOUSE_PRESSED)
                beep();
            return;
        }

        if (modifiers == MouseEvent.MOUSE_PRESSED)
            _lastMousePressTime = System.currentTimeMillis();

        Component component =
                top_window.getComponentAt(x - origin.x, y - origin.y);

        if (component != null) {
            _evtQueue.postEvent(new MouseEvent(component, modifiers, x, y, 0, button));

            // If this is a button-release within 400 msec of the corresponding
            // button-press.
            long current_time = System.currentTimeMillis();
            if (modifiers == MouseEvent.MOUSE_RELEASED &&
                    current_time - _lastMousePressTime < 400L) {

                _evtQueue.postEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, x, y, 1, button));

                // Check for a double-click.
                if (current_time - _lastMouseClickTime < 500L) {
                    _evtQueue.postEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, x, y, 2, button));
                }
                _lastMouseClickTime = current_time;
            }
        }
    }

    /**
     * Wait up to 100msec for a keystroke (or mouse-event).
     */
    public void checkForKeystroke() {

        int key = readKey();
        MouseEventInfo mouse_info = null;

        /* The possibility of an error return from readKey() was
         * pointed out by Uwe Buettner.
         * THIS HAS CHANGED! readKey returns -1 if there was no key to read.
         */
        if (key == -1) {
            return;
        }

        /* Note that if the "kent" key is defined, ncurses returns
         * VK_ENTER when the ENTER key is pressed; but some terminfo
         * files don't define the "kent" capability.
         */
        else if (key == '\n' || key == '\r')
            key = KeyEvent.VK_ENTER;

        /* Likewise, some versions of curses don't map '\b' to
         * VK_BACK_SPACE (Solaris at least); this works around
         * that.  (I can't imagine anyone wanting \b to be mapped
         * to anything else.  If they do, then they should set it
         * up that way in the terminfo database.)
         */
        else if (key == '\b')
            key = KeyEvent.VK_BACK_SPACE;

        if (key == KEY_MOUSE) {
            mouse_info = getMouseEventInfo();
            fireMouseEvent(mouse_info);
        } else {
            fireKeystroke(key);
        }

        /* If script recording is on.
         */
        if (_scriptPrintStream != null) {
            scriptbuffer.setLength(0);

            /* Compute the elapsed time since the last keystroke.
            */
            long current = System.currentTimeMillis();
            long elapsed = 1000;    // initial delay of 1 sec
            if (_prevTimeMillis != 0)
                elapsed = current - _prevTimeMillis;
            _prevTimeMillis = current;
            scriptbuffer.append(elapsed).append(" ");

            if (key == KEY_MOUSE) {
                scriptbuffer.append("MOUSE ").append(" ").
                        append(mouse_info.getButton()).append(" ").
                        append(mouse_info.getX()).append(" ").
                        append(mouse_info.getY());
            } else {
                scriptbuffer.append("KEY ");
                scriptbuffer.append(Integer.toHexString(key));
                scriptbuffer.append(" ");
                scriptbuffer.append(key2ASCII(key));
            }

            _scriptPrintStream.println(scriptbuffer.toString());
        }   // if (_scriptPrintStream != null)
    }

    /**
     * Convert the integer representation of a keystroke to an ASCII string.
     */
    public static String key2ASCII(int key_) {
        StringBuffer buf = new StringBuffer();

        if (key_ < 0x20) {
            buf.append("^");
            buf.append((char) (key_ + 0x40));
        } else if (key_ == 0x20) {
            buf.append("SPACE");
        } else if (key_ < 0x7f) {
            buf.append((char) key_);
        } else {
            switch (key_) {
                case KeyEvent.VK_DOWN:
                    buf.append("VK_DOWN");
                    break;
                case KeyEvent.VK_UP:
                    buf.append("VK_UP");
                    break;
                case KeyEvent.VK_LEFT:
                    buf.append("VK_LEFT");
                    break;
                case KeyEvent.VK_RIGHT:
                    buf.append("VK_RIGHT");
                    break;
                case KeyEvent.VK_HOME:
                    buf.append("VK_HOME");
                    break;
                case KeyEvent.VK_BACK_SPACE:
                    buf.append("VK_BACK_SPACE");
                    break;
                case KeyEvent.VK_F1:
                case KeyEvent.VK_F2:
                case KeyEvent.VK_F3:
                case KeyEvent.VK_F4:
                case KeyEvent.VK_F5:
                case KeyEvent.VK_F6:
                case KeyEvent.VK_F7:
                case KeyEvent.VK_F8:
                case KeyEvent.VK_F9:
                case KeyEvent.VK_F10:
                case KeyEvent.VK_F11:
                case KeyEvent.VK_F12:
                case KeyEvent.VK_F13:
                case KeyEvent.VK_F14:
                case KeyEvent.VK_F15:
                case KeyEvent.VK_F16:
                case KeyEvent.VK_F17:
                case KeyEvent.VK_F18:
                case KeyEvent.VK_F19:
                case KeyEvent.VK_F20:
                    buf.append("VK_F");
                    int c = 1 + key_ - KeyEvent.VK_F1;
                    buf.append(c);
                    break;
                case KeyEvent.VK_DELETE:
                    buf.append("VK_DELETE");
                    break;
                case KeyEvent.VK_INSERT:
                    buf.append("VK_INSERT");
                    break;
                case KeyEvent.VK_PAGE_DOWN:
                    buf.append("VK_PAGE_DOWN");
                    break;
                case KeyEvent.VK_PAGE_UP:
                    buf.append("VK_PAGE_UP");
                    break;
                case KeyEvent.VK_ENTER:
                    buf.append("VK_ENTER");
                    break;
                case KeyEvent.VK_BACK_TAB:
                    buf.append("VK_BACK_TAB");
                    break;
                case KeyEvent.VK_END:
                    buf.append("VK_END");
                    break;
                default:
                    buf.append("UNKNOWN");
            }
        }

        return buf.toString();

    }

    /**
     * Close the terminal window and restore terminal settings
     * (calls the curses endwin() function).
     */
    public native void close();

    /**
     * Clears the screen (calls the curses clear() function).
     */
    public native void clear();

    /**
     * Set absolute cursor position
     */
    public void setCursor(Point p) {
        setCursor(p.x, p.y);
    }

    /**
     * Set absolute cursor position
     */
    public native void setCursor(int x_, int y_);

    /**
     * Get absolute cursor position
     */
    public Point getCursor() {
        int x = getx();
        int y = gety();
        return new Point(x, y);
    }

    /**
     * Get absolute cursor position.  Use this overloaded version to
     * avoid allocating a new Point on the heap.
     */
    public Point getCursor(Point p_) {
        p_.x = getx();
        p_.y = gety();
        return p_;
    }

    /**
     * Add a character to the virtual terminal at the current cursor position.
     */
    public native void addChar(int chr, int attrib, int colorpair_);

    /**
     * Draw a horizontal line of the specified length starting at the current
     * cursor position.
     */
    public native void addHorizontalLine(int length_, int attrib_,
                                         int colorpair);

    /**
     * Draw a vertical line of the specified length starting at the current
     * cursor position.
     */
    public native void addVerticalLine(int length_, int attrib_,
                                       int colorpair_);

    /**
     * Add a string to the virtual terminal at the current cursor position.
     */
    public native void addString(String str_, int attrib_, int colorpair_);

    /**
     * Draw a box.
     */
    public void drawBox(Point origin_, Dimension size_) {
        drawBoxNative(origin_.x, origin_.y,
                origin_.x + size_.width - 1,
                origin_.y + size_.height - 1,
                0);
    }

    /**
     * Draw a box using the specified color pair.
     */
    public void drawBox(Point origin_, Dimension size_, int colorpair_) {
        drawBoxNative(origin_.x, origin_.y,
                origin_.x + size_.width - 1,
                origin_.y + size_.height - 1,
                colorpair_);
    }

    public native void drawBoxNative(int left_, int top_,
                                     int right_, int bottom_, int colorpair_);

    /**
     */
    public void blankBox(Point origin_, Dimension size_) {
        blankBoxNative(origin_.x, origin_.y,
                origin_.x + size_.width - 1,
                origin_.y + size_.height - 1,
                0);
    }

    /**
     * Blank out a box using the specified color pair.
     *
     * @param origin_    The top left corner of the rectangle.
     * @param size_      The dimensions of the rectangle to blank out.
     * @param colorpair_ The number of the color-pair (foreground+background)
     *                   to use for blanking the rectangle.
     */
    public void blankBox(Point origin_, Dimension size_, int colorpair_) {
        blankBoxNative(origin_.x, origin_.y,
                origin_.x + size_.width - 1,
                origin_.y + size_.height - 1,
                colorpair_);
    }

    /**
     * Blank out the specified rectangle.
     */
    public native void blankBoxNative(int left_, int top_,
                                      int right_, int bottom_, int colorpair_);

    /**
     * Set a clipping rectangle.
     */
    public void setClipRect(Rectangle clip_) {
        setClipRectNative(clip_.getLeft(), clip_.getTop(),
                clip_.getRight(), clip_.getBottom());
    }

    /**
     * Set a clipping rectangle.
     */
    public native void setClipRectNative(int left_, int top_,
                                         int right_, int bottom_);

    /**
     * Reset the clipping rectangle to the screen size.
     */
    public native void resetClipRect();

    /**
     * Ring the terminal's bell.
     */
    public native void beep();

    public native int getScreenRows();

    public native int getScreenColumns();

    public Dimension getScreenSize() {
        return new Dimension(getScreenColumns(), getScreenRows());
    }

    /**
     * Returns true if the terminal is capable of displaying colours.
     */
    public native boolean hasColors();

    /**
     * Returns the maximum number of color-pairs (provides an interface
     * to the ncurses COLOR_PAIRS global variable).
     */
    public native int getMaxColorPairs();

    /**
     * An interface to the terminfo "start_colors()" function.
     */
    public native void startColors();

    /**
     * Returns the color-pair index corresponding to the specified
     * color-pair.  If the color pair does not exist yet, it is created
     * using the ncurses "init_pair" function.  If all the available
     * color-pairs are already in use, a TerminfoCapabilityException
     * is thrown.
     */
    public int getColorPairIndex(ColorPair pair_)
            throws TerminfoCapabilityException {
        int index = _colorPairs.indexOf(pair_);
        if (index != -1)
            return index;

        if (_colorPairs.size() == getMaxColorPairs()) {
            throw new TerminfoCapabilityException("max number of color pairs (" + getMaxColorPairs() +
                    ") exceeded");
        }

        index = _colorPairs.size();
        _colorPairs.add(pair_);
        initColorPair(index,
                pair_.getForeground(),
                pair_.getBackground());
        return index;
    }

    /**
     * Synchronize the state of the physical terminal with the state of
     * the virtual terminal maintained by curses (ie provides an interface
     * to the curses refresh() function).
     */
    public native void sync();

    /**
     * Indicate to ncurses that all the lines on the screen have changed
     * and need to be redrawn the next time Toolkit.sync() is called. This
     * just calls the ncurses function "redrawwin(stdscr)".
     */
    public native void redrawWin();

    /**
     * Provides an interface to the ncurses "tigetstr()" function.
     */
    public native String getStringCapability(String capname_)
            throws TerminfoCapabilityException;

    /**
     * Provides an interface to the ncurses "tigetnum()" function.
     */
    public native int getNumericCapability(String capname_)
            throws TerminfoCapabilityException;

    /**
     * Provides an interface to the ncurses "tigetflag()" function.
     */
    public native boolean getBooleanCapability(String capname_)
            throws TerminfoCapabilityException;

    /**
     * Provides an interface to the ncurses "putp()" function, to write a
     * string to the terminal; the string must be a return value from
     * getStringCapability().
     */
    public native void putp(String str_);

    /**
     * Provides an interface to the ncurses "mcprint()" function to ship
     * the specified string to a printer attached to the terminal. Makes
     * use of the "mc4" and "mc5" capabilities of the terminal (see
     * "man curs_print").
     */
    public native void print(String str_) throws TerminfoCapabilityException;

    /**
     * Provides an interface to the terminfo "init_pair()" function.
     */
    public native void initColorPair(int pair_, int fgnd_, int bgnd_)
            throws TerminfoCapabilityException;

    /**
     * Emulates the terminfo COLOR_PAIR macro.
     */
    public static int COLOR_PAIR_ATTRIBUTE(int pair_) {
        return (pair_ << 8);
    }

    /**
     * Returns the tty device name (provides an interface to the
     * Unix C function "ttyname()").
     */
    public native String getTtyName();

    //====================================================================
    // PACKAGE-PRIVATE METHODS

    /**
     * Add a window to the list of displayed windows.
     * Intended to be called by the Window class only.
     */
    void addWindow(Window window_) {
        synchronized (_windowList) {
            _windowList.add(window_);
        }
    }

    /**
     * Remove a window from the list of displayed windows.
     * This is intended to be called by the Window object when it hides itself.
     */
    void removeWindow(Window window_) {
        synchronized (_windowList) {
            if (_windowList.remove(window_) == false)
                throw new RuntimeException("trying to remove window not in windowlist");
        }
    }

    /**
     * Returns a Vector of all the currently-displayed Windows. Note that the
     * calling thread must synchronize on the returned Vector before using or
     * modifying it, because the window list is accessed by the
     * keyboard-reading thread as well as by the event-dispatching thread.
     */
    Vector getWindowList() {
        return _windowList;
    }

    /**
     * This method is used for initializing the color constants in the
     * Color class.
     */
    static native int getColor(int index);

    /**
     * Changes the default foreground color.
     */
    public static void setDefaultForeground(Color color_) {
        _defaultForeground = color_;
    }

    /**
     * Returns the default foreground color.
     */
    public static Color getDefaultForeground() {
        return _defaultForeground;
    }

    /**
     * Changes the default background color.
     */
    public static void setDefaultBackground(Color color_) {
        _defaultBackground = color_;
    }

    /**
     * Returns the default background color.
     */
    public static Color getDefaultBackground() {
        return _defaultBackground;
    }

    /**
     * Trigger garbage collection. This method can be called inside an
     * event handler, but the call to <code>System.gc()</code> will be
     * made after the event handler has completed (i.e. after drawing is
     * completed). This is a convenient, but OPTIONAL, way of ensuring
     * that the heap does not grow too large. Some experts say that it is
     * better not to call System.gc() explicitly from inside an application;
     * you take your pick.
     *
     * @param source_ the component that triggered the garbage collection
     *                (not important).
     */
    public void triggerGarbageCollection(Component source_) {
        _evtQueue.postEvent(new GarbageCollectionEvent(source_));
    }

    /**
     * Get information about a mouse event (i.e the coordinates, and which
     * button was clicked).
     */
    public native MouseEventInfo getMouseEventInfo();


    //====================================================================
    // PRIVATE METHODS

    /**
     * This method is intended to be called by the keyboard-reading thread
     * only.
     */
    private native int readKey();

    /**
     * This method is intended to be called by the constructor.
     */
    private static native void init();

    /**
     * Get current X position of cursor.
     */
    private native int getx();

    /**
     * Get current Y position of cursor.
     */
    private native int gety();

    /**
     * This method is used for initializing the curses / ncurses video
     * attributes.
     */
    private static native int getAttribute(int offset_);

    /**
     * This method is used for initializing the line-drawing
     * characters using curses/ncurses.
     */
    private static native int getACSchar(int offset_);

 /**
     * Returns true if the key code is greater than 255, indicating
     * that it is a function key.
     */
    //public boolean isActionKey() { return (_key >= 256); }
    public static boolean isActionKey( int _key )
    {
        boolean value = false;
        if ( _key == charva.awt.event.KeyEvent.VK_ESCAPE ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_DOWN ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_UP ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_LEFT ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_RIGHT ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_HOME ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_BACK_SPACE ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F1 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F2 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F3 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F4 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F5 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F6 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F7 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F8 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F9 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F10 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F11 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F12 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F13 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F14 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F15 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F16 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F17 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F18 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F19 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_F20 ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_DELETE ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_INSERT ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_PAGE_DOWN ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_PAGE_UP ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_ENTER ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_BACK_TAB ) value=true;
        if ( _key == charva.awt.event.KeyEvent.VK_END ) value=true;

        return (value);
    }

    //====================================================================
    // INSTANCE VARIABLES

    private static Toolkit _instance = null;

    private static FocusEvent _lastFocusEvent = null;

    /**
     * A list of visible Windows.  The first in the list is at the bottom, the
     * last is on top.
     */
    private Vector _windowList = new Vector();

    /**
     * A list of color-pairs.
     */
    private Vector _colorPairs = new Vector();

    private EventQueue _evtQueue;

    /**
     * Used to record keystrokes if "charva.script.record" is defined.
     */
    private static PrintStream _scriptPrintStream = null;

    /**
     * Used to save the time the previous key was pressed.
     */
    private long _prevTimeMillis = 0;

    private long _lastMousePressTime;
    private long _lastMouseClickTime;

    // Definition as for ISO8859-1 (Latin-1) characters
    public static final char Auml = (char) 0xc4;
    public static final char Ccedil = (char) 0xc7;
    public static final char Eacute = (char) 0xc9;
    public static final char Euml = (char) 0xcb;
    public static final char Ouml = (char) 0xd6;
    public static final char Uuml = (char) 0xdc;

    /* This is a static initializer block. It is executed once,
     * when the class is initialized.
     */
    static {
        /* Check if the user wants to record or play back a script.
         */
        String scriptfilename = System.getProperty("charva.script.record");
        if (scriptfilename != null) {
            try {
                _scriptPrintStream = new PrintStream(new FileOutputStream(scriptfilename));
            } catch (FileNotFoundException ef) {
                LOG.warn("Cannot open script file \"" +
                        scriptfilename + "\" for writing");
                System.exit(1);
            }
        }

        System.loadLibrary("Terminal");
        Toolkit.init();	    // call native function to initalize ncurses.
    }

    /**
     * This flag is true is the system property "charva.color" has been set.
     */
    public static final boolean isColorEnabled =
            (System.getProperty("charva.color") != null);

    private StringBuffer scriptbuffer = new StringBuffer();

    /* These are set to match the equivalent definitions in <ncurses.h>
     * or <curses.h> (they have to be set dynamically because the values 
     * differ between curses, ncurses and PDCurses).
     */
    public static final int A_NORMAL = getAttribute(0);
    public static final int A_STANDOUT = getAttribute(1);
    public static final int A_UNDERLINE = getAttribute(2);
    public static final int A_REVERSE = getAttribute(3);
    public static final int A_BLINK = getAttribute(4);
    public static final int A_DIM = getAttribute(5);
    public static final int A_BOLD = getAttribute(6);
    public static final int A_ALTCHARSET = getAttribute(7);
    public static final int A_INVIS = getAttribute(8);

    // graphical symbols that must be initialized from <ncurses.h> or
    // <curses.h>
    public static final int ACS_ULCORNER = getACSchar(0);
    public static final int ACS_LLCORNER = getACSchar(1);
    public static final int ACS_URCORNER = getACSchar(2);
    public static final int ACS_LRCORNER = getACSchar(3);
    public static final int ACS_LTEE = getACSchar(4);
    public static final int ACS_RTEE = getACSchar(5);
    public static final int ACS_BTEE = getACSchar(6);
    public static final int ACS_TTEE = getACSchar(7);
    public static final int ACS_HLINE = getACSchar(8);
    public static final int ACS_VLINE = getACSchar(9);
    public static final int ACS_PLUS = getACSchar(10);
    public static final int ACS_S1 = getACSchar(11);
    public static final int ACS_S9 = getACSchar(12);
    public static final int ACS_DIAMOND = getACSchar(13);
    public static final int ACS_CKBOARD = getACSchar(14);
    public static final int ACS_DEGREE = getACSchar(15);
    public static final int ACS_PLMINUS = getACSchar(16);
    public static final int ACS_BULLET = getACSchar(17);

    // These constants must be the same as in ncurses.h
    public static final int BLACK = getColor(0);
    public static final int RED = getColor(1);
    public static final int GREEN = getColor(2);
    public static final int YELLOW = getColor(3);
    public static final int BLUE = getColor(4);
    public static final int MAGENTA = getColor(5);
    public static final int CYAN = getColor(6);
    public static final int WHITE = getColor(7);

    public static Color _defaultForeground = Color.white;
    public static Color _defaultBackground = Color.black;

    public static final int KEY_MOUSE = 0631;

    public static final int BUTTON1_RELEASED = 000001;
    public static final int BUTTON1_PRESSED = 000002;
    public static final int BUTTON1_CLICKED = 000004;
    public static final int BUTTON2_RELEASED = 000100;
    public static final int BUTTON2_PRESSED = 000200;
    public static final int BUTTON2_CLICKED = 000400;
    public static final int BUTTON3_RELEASED = 010000;
    public static final int BUTTON3_PRESSED = 020000;
    public static final int BUTTON3_CLICKED = 040000;
}
