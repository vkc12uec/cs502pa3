/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import java.util.*;
import Translate.Temp;

public class Color {
    public Temp[] spills() {
        Temp[] spills = null;
        // TODO: return an array holding the spilled temporaries or null if none
        return spills;
    }

    public Color(InterferenceGraph ig, Translate.Frame frame) {
	// TODO: Color each node of the interference graph ig with a register,
	// respecting the interference edges.  The colors are the
	// frame.registers().
        // Any actual spills must be returned by the callback to spills().
    }
}
