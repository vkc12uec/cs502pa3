/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Graph;
import java.util.List;
import java.util.LinkedList;

public class Node<K, N extends Node<K, N>> {
    
    public Node(Graph<K,N> g, K key) {
	index = g.size();
	g.put(key, (N)this);
    }

    public List<N> succs = new LinkedList<N>();
    public List<N> preds = new LinkedList<N>();

    public List<N> adj() {
	LinkedList<N> l = new LinkedList<N>();
	l.addAll(succs);
	l.addAll(preds);
	return l;
    }
    public int inDegree () { return preds.size(); }
    public int outDegree() { return succs.size(); }
    public int degree   () { return inDegree() + outDegree(); }

    public boolean goesTo   (N n) { return succs.contains(n); }
    public boolean comesFrom(N n) { return preds.contains(n); }
    public boolean adj      (N n) { return goesTo(n) || comesFrom(n); }

    private int index;
    public String toString() { return String.valueOf(index); }
    public boolean equals(N n) { return index == n.index; }
}
