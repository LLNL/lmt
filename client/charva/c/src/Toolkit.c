/* Toolkit.c
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
#include "../include/charva_awt_Toolkit.h"
#if (defined _PDCURSES_)
    // Use the PDCurses version of the header file, which must be
    // in the current directory.
    #include "curses.h"
#elif (defined _USE_CURSES_)
    #include <curses.h>
    #include <term.h>
#elif (defined _USE_NCURSESW_)
    #include <locale.h>
    #include <ncursesw/curses.h>
#else
    // Default is to use ncurses
    #include <ncurses.h>
#endif
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>

//************************************************************************
// LOCAL FUNCTION DECLARATIONS
static void my_move(int y_, int x_);
static void my_addch(int chr_);
static void my_addch_with_clip(int chr_);
static int is_special_drawing(int chr_);
static int my_readkey();

//************************************************************************
// LOCAL VARIABLES
// Here are the boundaries of the clipping rectangle.
// Note that "right" refers to the righmost column INCLUDED in the rectangle
// and "bottom" refers to the bottom row that is INCLUDED in the rectangle.
static int left, top, right, bottom;

// This is a local copy of the cursor position, used for clipping.
static int cursorx=0, cursory=0;

static int hascolors = 0;	// set when we initialize
static int colors_started = 0;	// set when we call start_color().


//************************************************************************
// FUNCTION DEFINITIONS

JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getAttribute
  (JNIEnv *env, jclass class, jint offset)
{
    switch (offset) {
	case 0:
	    return (jint) A_NORMAL;
	case 1:
	    return (jint) A_STANDOUT;
	case 2:
#if (defined _PDCURSES_)
	    // PDCurses doesn't handle UNDERLINE correctly; it
	    // just sets the color to blue.
	    return (jint) A_BOLD;
#else
	    return (jint) A_UNDERLINE;
#endif
	case 3:
	    return (jint) A_REVERSE;
	case 4:
	    return (jint) A_BLINK;
	case 5:
	    return (jint) A_DIM;
	case 6:
	    return (jint) A_BOLD;
	case 7:
	    return (jint) A_ALTCHARSET;
	case 8:
	    return (jint) A_INVIS;
    }
}

JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getACSchar
  (JNIEnv *env, jclass class, jint offset)
{
    switch (offset) {
	case 0:
	    return (jint) ACS_ULCORNER;
	case 1:
	    return (jint) ACS_LLCORNER;
	case 2:
	    return (jint) ACS_URCORNER;
	case 3:
	    return (jint) ACS_LRCORNER;
	case 4:
	    return (jint) ACS_LTEE;
	case 5:
	    return (jint) ACS_RTEE;
	case 6:
	    return (jint) ACS_BTEE;
	case 7:
	    return (jint) ACS_TTEE;
	case 8:
	    return (jint) ACS_HLINE;
	case 9:
	    return (jint) ACS_VLINE;
	case 10:
	    return (jint) ACS_PLUS;
	case 11:
	    return (jint) ACS_S1;
	case 12:
	    return (jint) ACS_S9;
	case 13:
	    return (jint) ACS_DIAMOND;
	case 14:
	    return (jint) ACS_CKBOARD;
	case 15:
	    return (jint) ACS_DEGREE;
	case 16:
	    return (jint) ACS_PLMINUS;
	case 17:
	    return (jint) ACS_BULLET;
    }
}

JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getColor
  (JNIEnv *env, jclass class, jint offset)
{
    switch (offset) {
	case 0:
	    return (jint) COLOR_BLACK;
	case 1:
	    return (jint) COLOR_RED;
	case 2:
	    return (jint) COLOR_GREEN;
	case 3:
	    return (jint) COLOR_YELLOW;
	case 4:
	    return (jint) COLOR_BLUE;
	case 5:
	    return (jint) COLOR_MAGENTA;
	case 6:
	    return (jint) COLOR_CYAN;
	case 7:
	    return (jint) COLOR_WHITE;
    }
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_init
  (JNIEnv *env, jobject jo)
{
    char *strcap;
    int i;

#if (defined _USE_NCURSESW_)
    // tested with "cs_CZ.UTF-8" setting, must work with any setting
    setlocale(LC_ALL,""); // from environment (LANG)
#endif

    initscr();
    keypad(stdscr, TRUE);   // enable keyboard mapping
    timeout(100);           // wait up to 100msec for input
    noecho();               /* don't echo input */
    raw();
    nonl();

    hascolors = has_colors();

#ifdef NCURSES_MOUSE_VERSION
    /* For some reason, if the mouse_interval is nonzero, the button-presses
     * are reported very erratically. So we disable click resolution by
     * setting mouse_interval to 0.
     */
    mousemask(BUTTON1_RELEASED | BUTTON1_PRESSED | 
	      BUTTON2_RELEASED | BUTTON2_PRESSED |
	      BUTTON3_RELEASED | BUTTON3_PRESSED, 
	      NULL);
    mouseinterval(0);
#endif

    atexit((void (*)(void))endwin);

    Java_charva_awt_Toolkit_resetClipRect(env, jo);
}


/* 
 * read a key as UTF-8 and return unicode character 
 * Works with one and two bytes characters only.
 */
static int my_readkey()
{
    int utf[6] = {0,0,0,0,0,0};
    utf[0] = getch();
    int c = 0;

    if (utf[0] == -1) return -1;         // error

    if (utf[0] == 0631) return 0631;     // mouse event

    if ( (utf[0] & 0x80) == 0 ) {
        //0xxxxxxxx (1 byte UTF-8)
        c = utf[0];
    }
    else if ( (utf[0] & 0xe0) == 0xc0 ) {
        // 110xxxxx 10xxxxxx ( 2 byte UTF-8 )
        utf[1] = getch();
        if (utf[1] == -1) return -1;
        c = (utf[0] & 0x1f) * 0x40 + (utf[1] & 0x3f);
    }
    else if ( (utf[0] & 0xf8) == 0xf0 ) {
        // 1110xxxx 10xxxxxx 10xxxxxx ( 3 byte UTF-8 )
        // cannot be tested = not supported :-(
        // must be similar to 2 byte encoding
    }
    else if ( (utf[0] & 0xf0) == 0xf0 ) {
        // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx( 4 byte UTF-8 )
        // cannot be tested = not supported :-(
        // must be similar to 2 byte encoding
    }
    else if ( (utf[0] & 0xfc) == 0xf8 ) {
        // 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx( 5 byte UTF-8 )
        // cannot be tested = not supported :-(
        // must be similar to 2 byte encoding
    }
    if ( (utf[0] & 0xfe) == 0xfc ) {
        // 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx ( 6 byte UTF-8 )
        // cannot be tested = not supported :-(
        // must be similar to 2 byte encoding
    }

   return c;
}

// Returns -1 if there was no character to read.
// Otherwise returns the character that was read.
JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_readKey
  (JNIEnv *env, jobject jo)
{
    jint c;	// defined as a long for Linux.

try_again:
#if (defined _USE_NCURSESW_)
    c = (jint) my_readkey();
#else
    c = (jint) getch();
#endif

 // THIS HAS ALL CHANGED: getch() is now called from the event-dispatching thread!!! (November 2004)
    // There are two known scenarios in which getch() returns ERR.
    // 1. The user resizes his PuTTY window (in which case errno == 0).
    // 2. The user closes his Telnet/SSH session (errno == EINTR).
    //if (c == ERR && errno == 0) {
	//    // The window has been resized.
	//    Java_charva_awt_Toolkit_resetClipRect(env, jo);
	//    goto try_again;
    //}

    return c;
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_sync
  (JNIEnv *env, jobject jo)
{
    refresh();
}

#ifndef _PDCURSES_
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_redrawWin
  (JNIEnv *env, jobject jo)
{
    redrawwin(stdscr);	
}
#endif

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_close
  (JNIEnv *env, jobject jo)
{
    endwin();
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_clear
  (JNIEnv *env, jobject jo)
{
    clear();
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_setCursor
  (JNIEnv *env, jobject jo, jint x_, jint y_)
{
    my_move(y_, x_);
}

/* Draw a string of text, taking into account the clipping rectangle.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_addString
  (JNIEnv *env, jobject jo, jstring jstr, jint attrib, jint colorpair)
{
    const jchar *chrs;
    jboolean isCopy;
    jsize stringlength;
    int i;
    int attr = attrib;

    if (colors_started)
	attr |= COLOR_PAIR(colorpair);

    chrs = (*env)->GetStringChars(env, jstr, &isCopy);
    stringlength = (*env)->GetStringLength(env, jstr);

    if (cursory < top || cursory > bottom) {
	cursorx += stringlength;
    }
    else {
	    for (i=0; i<stringlength; i++) {
                attron(attr);
	        if (cursorx >= left && cursorx <= right)
		        my_addch(chrs[i]);
	        else {
		        cursorx++;
		        move(cursory, cursorx);
	        }
                attroff(attr);
	    }
    }

    (*env)->ReleaseStringChars(env, jstr, chrs);
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_addChar
  (JNIEnv *env, jobject jo, jint chr_, jint attrib_, jint colorpair_)
{
    int attr = attrib_;

    if (colors_started)
	attr |= COLOR_PAIR(colorpair_);

    attron(attr);
    my_addch_with_clip(chr_);
    attroff(attr);
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_drawBoxNative
  (JNIEnv *env, jobject jo, jint left_, jint top_, jint right_, jint bottom_, jint colorpair_)
{
    int i, j;
    int attr = 0;

    if (colors_started)
	    attr = COLOR_PAIR(colorpair_);

    // If the top of the box is outside the clipping rectangle, don't bother
    // to draw the top.
    if (top_ >= top && top_ <= bottom) {
	    my_move(top_, left_);
	    my_addch_with_clip(ACS_ULCORNER);    // upper left corner

	    for (i=left_+1; i<right_; i++) {
	        my_addch_with_clip(ACS_HLINE );   // top horizontal line
	    }

	    my_addch_with_clip(ACS_URCORNER);    // upper right corner
    }

    // If the bottom of the box is outside the clipping rectangle, don't bother
    if (bottom_ >= top && bottom_ <= bottom) {
	    my_move(bottom_, left_);
	    my_addch_with_clip(ACS_LLCORNER);    // lower left corner

	    for (i=left_+1; i<right_; i++)
	        my_addch_with_clip(ACS_HLINE );   // bottom horizontal line

	    my_move(bottom_, right_);
	    my_addch_with_clip(ACS_LRCORNER );    // lower right corner
    }

    // If the left side of the box is outside the clipping rectangle, don't
    // bother.
    if (left_ >= left && left_ <= right) {
	    for (i=top_+1; i<bottom_; i++) {
	        my_move(i, left_);
	        my_addch_with_clip(ACS_VLINE);   // left vertical line
	    }
    }
    //
    // If the right side of the box is outside the clipping rectangle, don't
    // bother.
    if (right_ >= left && right_ <= right) {
	    for (i=top_+1; i<bottom_; i++) {
	        my_move(i, right_);
	        my_addch_with_clip(ACS_VLINE);   // right vertical line
	    }
    }
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_blankBoxNative
  (JNIEnv *env, jobject jo, jint left_, jint top_, jint right_, jint bottom_, jint colorpair_)
{
    int row, col;
    int attr = 0;

    if (colors_started)
	attr = COLOR_PAIR(colorpair_);

    attron(attr);
    for (row=top_; row<=bottom_; row++) {
	    if (row < top || row > bottom)
	        continue;	// do some clipping

	    my_move(row, left_);
	    for (col=left_; col<=right_; col++)
            { 
	        my_addch_with_clip(' ');
            }
    }
    attroff(attr);
}

/*
 * Ring the terminal bell.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_beep
  (JNIEnv *env, jobject jo)
{
    beep();
}

/* Returns true if the terminal is capable of displaying colors.
 */
JNIEXPORT jboolean JNICALL Java_charva_awt_Toolkit_hasColors
  (JNIEnv *env, jobject jo)
{
    return has_colors();
}

/* Returns the number of color-pairs that the terminal supports
 * (provides an interface to ncurses COLOR_PAIRS global variable).
 */
JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getMaxColorPairs
  (JNIEnv *env, jobject jo)
{
    if (!colors_started) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "charva/awt/TerminfoCapabilityException"),
		    "startColors() not called yet");
    }
    return (jint) COLOR_PAIRS;
}

/* Return the number of columns that the screen can display.
* The ncurses does not return a reliable value for xterm windows,
* where the screen size can change at runtime.
 */
JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getScreenColumns
  (JNIEnv *env, jobject jo)
{
    int x, y;
    getmaxyx(stdscr, y, x);
    return (jint) x;
}

/* Return the number of rows that the screen can display.
 */
JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getScreenRows
  (JNIEnv *env, jobject jo)
{
    int x, y;
    getmaxyx(stdscr, y, x);
    return (jint) y;
}

/* Draw a vertical line starting at the current cursor position.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_addVerticalLine
  (JNIEnv *env, jobject jo, jint length_, jint attrib_, jint colorpair_)
{
    int x, y, i;
    int attr = attrib_;

    if (colors_started)
	    attr |= COLOR_PAIR(colorpair_);

    // Set current cursor position
    x = cursorx;
    y = cursory;
    
    attron(attr);
    for (i=0; i<length_; i++) {
	    my_move(y+i, x);
	    my_addch_with_clip(ACS_VLINE);
    }
    attroff(attr);
}

/* Draw a horizontal line starting at the current cursor position.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_addHorizontalLine
  (JNIEnv *env, jobject jo, jint length_, jint attrib_, jint colorpair_)
{
    int x, y, i;
    int attr = attrib_;

    if (colors_started)
	attr |= COLOR_PAIR(colorpair_);

    // Set current cursor position
    x = cursorx;
    y = cursory;

    attron(attr);
    for (i=0; i<length_; i++) {
	    my_move(y, x+i);
	    my_addch_with_clip(ACS_HLINE);
    }
    attroff(attr);
}

/* Get the y position of the cursor.
 */
JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_gety
  (JNIEnv *env, jobject jo)
{
    int x, y;

    getyx(stdscr, y, x);
    return y;
}

/* Get the x position of the cursor.
 */
JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getx
  (JNIEnv *env, jobject jo)
{
    int x, y;

    getyx(stdscr, y, x);
    return x;
}

/* Set a clipping rectangle
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_setClipRectNative
  (JNIEnv *env, jobject jo, jint left_, jint top_, jint right_, jint bottom_)
{
    left = left_;
    top = top_;
    right = right_;
    bottom = bottom_;
}

/* Reset the clipping rectangle to the screen size.
 * Unfortunately this does not work reliably on xterm windows where the window
 * size can change at runtime. The ncurses library appears not to update the
 * value of the window reliably.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_resetClipRect
  (JNIEnv *env, jobject jo)
{
    left = top = 0;
    getmaxyx(stdscr, bottom, right);
    bottom--;
    right--;
}

#if ( ! defined _PDCURSES_)
JNIEXPORT jstring JNICALL Java_charva_awt_Toolkit_getStringCapability
  (JNIEnv *env, jobject jo, jstring capname_)
{
    const char* capCname;
    char *capValue;
    jstring ret;

    capCname = (*env)->GetStringUTFChars(env, capname_, NULL);
    capValue = tigetstr((char *) capCname);
    if (capValue == (char *) -1 || capValue == (char *) 0) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "charva/awt/TerminfoCapabilityException"), capCname);
    }
    else {
	    ret = (*env)->NewStringUTF(env, capValue);
    }

    (*env)->ReleaseStringUTFChars(env, capname_, capCname);
    return ret;
}


JNIEXPORT jint JNICALL Java_charva_awt_Toolkit_getNumericCapability
  (JNIEnv *env, jobject jo, jstring capname_)
{
    const char* capCname;
    jint capValue;

    capCname = (*env)->GetStringUTFChars(env, capname_, NULL);
    capValue = tigetnum((char *) capCname);
    if (capValue < 0) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "charva/awt/TerminfoCapabilityException"), capCname);
    }

    (*env)->ReleaseStringUTFChars(env, capname_, capCname);
    return capValue;
}

JNIEXPORT jboolean JNICALL Java_charva_awt_Toolkit_getBooleanCapability
  (JNIEnv *env, jobject jo, jstring capname_)
{
    const char* capCname;
    int capValue;
    jboolean ret;

    capCname = (*env)->GetStringUTFChars(env, capname_, NULL);
    capValue = tigetflag((char *) capCname);
    if (capValue == -1) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "charva/awt/TerminfoCapabilityException"), capCname);
    }
    else {
	    ret = (capValue != 0);
    }

    (*env)->ReleaseStringUTFChars(env, capname_, capCname);
    return ret;
}

/** An interface to the terminfo "putp()" function.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_putp
  (JNIEnv *env, jobject jo, jstring jstr_)
{
    const jchar *chrs;
    jboolean isCopy;
    jsize stringlength;
    char *cstring;
    int i;

    /* We don't make the assumption that all the characters are 7-bit ASCII
     * (otherwise we could have just used GetStringUTFChars).
     */
    chrs = (*env)->GetStringChars(env, jstr_, &isCopy);
    stringlength = (*env)->GetStringLength(env, jstr_);

    cstring = malloc(stringlength + 1);
    for (i=0; i<stringlength; i++) {
	    cstring[i] = (char) chrs[i];
    }
    cstring[i] = '\0';	    // terminate the C string

    putp(cstring);
    free(cstring);

    (*env)->ReleaseStringChars(env, jstr_, chrs);
}

#if ( ! defined _USE_CURSES_)
/* I am told that the ncurses function "mcprint" is not 
 * defined in curses.
 */
JNIEXPORT void JNICALL Java_charva_awt_Toolkit_print
  (JNIEnv *env, jobject jo_, jstring jstr_)
{
    const jchar *chrs;
    jboolean isCopy;
    jsize stringlength;
    char *cstring;
    int i, ret;

    /* We don't make the assumption that all the characters are 7-bit ASCII
     * (otherwise we could have just used GetStringUTFChars).
     */
    chrs = (*env)->GetStringChars(env, jstr_, &isCopy);
    stringlength = (*env)->GetStringLength(env, jstr_);

    cstring = malloc(stringlength + 1);
    for (i=0; i<stringlength; i++) {
	    cstring[i] = (char) chrs[i];
    }
    cstring[i] = '\0';	    // terminate the C string
    (*env)->ReleaseStringChars(env, jstr_, chrs);

    ret = mcprint(cstring, strlen(cstring));
    free(cstring);

    if (ret < 0) {
	    if (errno == ENODEV) {
	        (*env)->ThrowNew(env, (*env)->FindClass(env,
		        "charva/awt/TerminfoCapabilityException"),
		        "mc4 or mc5 capability not defined");
	    }
	    else if (errno == ENOMEM) {
	        (*env)->ThrowNew(env, (*env)->FindClass(env,
		        "java/lang/OutOfMemoryError"), "");
	    }
	    else {
	        (*env)->ThrowNew(env, (*env)->FindClass(env,
		        "java/lang/RuntimeException"),
		        strerror(errno));
	    }
    }
}

JNIEXPORT jobject JNICALL Java_charva_awt_Toolkit_getMouseEventInfo
  (JNIEnv *env, jobject jo)
{
    MEVENT event;
    jclass class_MouseEventInfo;
    jmethodID id_MouseEventInfo;
    jint button_state, x, y;
    jobject info;

    if (getmouse(&event) != OK) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "java/lang/RuntimeException"),
		    "ncurses getmouse() function returned error");
    }

    class_MouseEventInfo = (*env)->FindClass(env, "charva/awt/MouseEventInfo");
    id_MouseEventInfo = (*env)->GetMethodID(env, class_MouseEventInfo, "<init>", "(III)V");
    button_state = event.bstate;
    x = event.x;
    y = event.y;
    info = (*env)->NewObject(env,
	    class_MouseEventInfo, id_MouseEventInfo, button_state, x, y);
    return info;
}
#endif /* ( ! defined _USE_CURSES_) */
#endif /* (! defined _PDCURSES_) */


JNIEXPORT void JNICALL Java_charva_awt_Toolkit_startColors
  (JNIEnv *env, jobject jo)
{
    /* We checked whether the terminal had color capability when
     * we initialised it.
     */
    if (!hascolors) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "charva/awt/TerminfoCapabilityException"),
		    "color capability not defined");
    }
    start_color();
    colors_started = 1;
}

JNIEXPORT void JNICALL Java_charva_awt_Toolkit_initColorPair
  (JNIEnv *env, jobject jo, jint pair_, jint fgnd_, jint bgnd_)
{
    /* We checked whether the terminal had color capability when
     * we initialised it.
     */
    if (!colors_started) {
	    (*env)->ThrowNew(env, (*env)->FindClass(env,
		    "charva/awt/TerminfoCapabilityException"),
		    "startColors() not called yet");
    }

    init_pair(pair_, fgnd_, bgnd_);
}

#ifndef _PDCURSES_
/*************************************************************************
 * Get the tty name.
 */
JNIEXPORT jstring JNICALL Java_charva_awt_Toolkit_getTtyName
  (JNIEnv *env, jobject jo)
{
    char *ttyName=NULL;
    jstring ret;

    ttyName = ttyname(0);
    if (ttyName == NULL) {
	    ttyName = "not_a_tty";
    }

    ret = (*env)->NewStringUTF(env, ttyName);
    return ret;
}
#endif

/* This calls the standard curses "addch" function but also updates the
 * local copy of the cursor position, so that the clipping works correctly.
 * It is called by functions that can clip more efficiently by doing their
 * own clipping (such as addString, which knows when it starts whether the
 * row is above, inside or below the clipping rectangle).
 */
static void my_addch(int chr_)
{
#if (defined _USE_NCURSESW_)
    if ( is_special_drawing(chr_) )
    {  // for special chars must be used addch(), widechar does not work
       // better way is look for other way of drawing of box in unicode 
       addch(chr_);
    }
    else
    {
       // use method for output unicode characters
       // does exist better way how to display ONE wide character?
       wchar_t buff_wcs[2];  // widechar
       buff_wcs[0] = chr_;   // unicode value
       buff_wcs[1] = 0x0000; // end of string
       addwstr(buff_wcs); // display wide string at curren position
    }
#else
    addch(chr_);
#endif
    cursorx++;
}

/* This calls the standard curses "move" function but also updates a local
 * copy of the cursor position so that clipping works correctly.
 */
static void my_move(int y_, int x_)
{
    move(y_, x_);

    /* Keep track of the cursor position so that we can use the clipping
     * rectangle effectively.
     */
    cursorx = x_;
    cursory = y_;
}

/* Write a character to the virtual screen if the cursor position is within 
 * the clipping rectangle, and (whether the character was clipped or not) 
 * updates the cursor position.
 */
static void my_addch_with_clip(int chr_)
{
    if (cursory >= top && cursory <= bottom && 
		cursorx >= left && cursorx <= right) {

	    my_addch(chr_);
    }
    else {
	    cursorx++;
	move(cursory, cursorx);
    }
}

/*
 * check if character is special character or normal character,
 * this function is used to find a way how display unicode character 
 * Be warned - every this special character replaces one official unicode
 * character.
 * TODO: how to display boxes in unicode mode by other way?
 */
static int is_special_drawing(int chr_)
{
  int value = 0;
  if (chr_ == ACS_ULCORNER) value=1;
  if (chr_ == ACS_LLCORNER) value=1;
  if (chr_ == ACS_URCORNER) value=1;
  if (chr_ == ACS_LRCORNER) value=1;
  if (chr_ == ACS_HLINE) value=1;
  if (chr_ == ACS_VLINE) value=1;
  if (chr_ == ACS_LTEE) value=1;
  if (chr_ == ACS_RTEE) value=1;
  if (chr_ == ACS_BTEE) value=1;
  if (chr_ == ACS_TTEE) value=1;
  if (chr_ == ACS_PLUS) value=1;
  return value;
}
