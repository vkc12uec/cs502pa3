/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Graph;
import java.util.LinkedHashMap;
import java.util.Collection;

public class Graph<K,N extends Node<K,N>> {
    private LinkedHashMap<K,N> nodes = new LinkedHashMap<K,N>();

    public N put(K k, N n) { return nodes.put(k, n); }
    public N get(K k) { return nodes.get(k); }
    public Collection<N> nodes() { return nodes.values(); }
    public int size() { return nodes.size(); }

    void check(N n) {
    	if (nodes.containsValue(n)) return;
    	throw new Error("Graph.addEdge using nodes from the wrong graph");
    }

    public void addEdge(N from, N to) {
    	check(from); check(to);
    	if (from.goesTo(to)) return;
    	to.preds.add(from);
    	from.succs.add(to);
    }

    public void rmEdge(N from, N to) {
    	to.preds.remove(from);
    	from.succs.remove(to);
    }

    /**
     * Print a human-readable dump for debugging.
     */
    public void show(java.io.PrintWriter out) {
	for (N n : nodes()) {
	    out.print(n);
	    out.print(": ");
	    for (N s : n.succs) {
		out.print(s);
		out.print(" ");
	    }
	    out.println();
	}
	out.flush();
    }
}
