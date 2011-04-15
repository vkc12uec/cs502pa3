/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import Translate.Temp;

public class Node extends Graph.Node<Temp, Node> {
    Temp temp;
    Temp color = null;
    double spillCost = 0.0;

    Node(InterferenceGraph g, Temp t) {
        super(g, t);
        temp = t;
    }
}
