/* class MouseEventInfo
 *
 * Copyright (C) 2001 - 2003  R M Pitman
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

/**
 * This (package-private) class encapsulates information about a mouse event.
 */
class MouseEventInfo {
    /**
     * Construct an event with the specified source and ID.
     */
    public MouseEventInfo(int button_, int x_, int y_) {
        button = button_;
        x = x_;
        y = y_;
    }

    public int getButton() {
        return button;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    //====================================================================
    // INSTANCE VARIABLES

    int button;
    int x;
    int y;
}
