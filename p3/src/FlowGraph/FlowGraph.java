/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package FlowGraph;

import Translate.Temp;
import Graph.Node;
import Graph.Graph;
import java.util.Set;

/**
 * A control flow graph is a directed graph in which each edge indicates a
 * possible flow of control. Also, each node in the graph defines a set of
 * temporaries; each node uses a set of temporaries; and each node is, or is
 * not, a <strong>move</strong> instruction.
 * 
 * @see AssemFlowGraph
 */

public abstract class FlowGraph<K, N extends Node<K, N>> extends Graph<K, N> {
    /**
     * The set of temporaries defined by this instruction or block
     */
    public abstract Set<Temp> def(N node);

    /**
     * The set of temporaries used by this instruction or block
     */
    public abstract Set<Temp> use(N node);

    /**
     * Print a human-readable dump for debugging.
     */
    public void show(java.io.PrintWriter out) {
        for (N n : nodes()) {
            out.print(n);
            out.print(": ");
            for (Temp t : def(n))
                out.print(t + " ");
            out.print("<- ");
            for (Temp t : use(n))
                out.print(t + " ");
            out.print("; goto ");
            for (N s : n.succs)
                out.print(s + " ");
            out.println();
        }
        out.flush();
    }
}
