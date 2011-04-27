/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import java.util.*;

import Translate.Temp;

public class Node extends Graph.Node<Temp, Node> {
    Temp temp;
    Temp color = null;
    double spillCost = 0.0;
    int degree = 0;
    LinkedHashSet<Move> moveList = new LinkedHashSet<Move>();
    LinkedHashSet<Node> adjList = new LinkedHashSet<Node>();

    Node(InterferenceGraph g, Temp t) {
        super(g, t);
        temp = t;
    }
    
    @Override public String toString() {
    	return temp.toString();
    }
}
