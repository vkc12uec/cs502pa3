/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import java.util.*;
import Translate.Temp;
import Graph.Graph;

public abstract class InterferenceGraph extends Graph<Temp, Node> {
    public abstract List<Move> moves();
}
