/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import java.util.*;

import Assem.Instr;
import Translate.Temp;
import Translate.Frame.Access;

public class Color {
	
	static public class SpillComparator implements Comparator<Node> {
		@Override
		public int compare(Node x, Node y) {
			if (x.spillCost > y.spillCost)
				return -1;
			else if (x.spillCost < y.spillCost)
				return 1;
			else
				return 0;
		}
	}
	public LinkedList<Node> simplifyWorklist = new LinkedList<Node>(),  
			freezeWorklist = new LinkedList<Node>();
	Comparator<Node> comparator = new SpillComparator();
	public PriorityQueue<Node> spillWorklist = new PriorityQueue<Node>(20, comparator);
	private int K = 0;
	private Liveness ig = null;
	private Translate.Frame frame;
	private Stack <Node> selectStack = new Stack <Node> ();
	private LinkedHashMap<Node, Node> alias = new LinkedHashMap<Node, Node>();
	
	//nodes
    //private Set <Node> precolored = new LinkedHashSet <Node> ();
	private Set <Node> initial = new LinkedHashSet <Node> ();
	private Set <Node> spilledNodes = new LinkedHashSet <Node> (),
		coalescedNodes = new LinkedHashSet <Node> (), coloredNodes = new LinkedHashSet <Node> ();
	
	// moves
	private Set <Move> activeMoves = new LinkedHashSet<Move>(), frozenMoves = new LinkedHashSet<Move>(),
				constrainedMoves = new LinkedHashSet<Move>(), coalescedMoves = new LinkedHashSet<Move>();
	public LinkedList<Move> worklistMoves = new LinkedList <Move> ();
	
    public Set<Temp> spills() {
    	LinkedHashSet<Temp> spilledTemps = new LinkedHashSet<Temp>();
    	for (Node n : spilledNodes) {
    		spilledTemps.add(n.temp);
    	}
    	return spilledTemps;
    }
    
    public Set<Move> NodeMoves(Node n) {
    	LinkedHashSet<Move> nmoves = new LinkedHashSet<Move>(worklistMoves);
    	nmoves.addAll(activeMoves);
    	nmoves.retainAll(n.moveList);
    	return nmoves;
    }

    public boolean MoveRelated(Node n) {
    	return !NodeMoves(n).isEmpty();
    }

    private Set<Node> Adjacent(Node n) {
    	LinkedHashSet<Node> adj = new LinkedHashSet<Node>(n.adjList);
    	adj.removeAll(selectStack);
    	adj.removeAll(coalescedNodes);
    	return adj;
    }
    
    public Color(Liveness ig_param, Translate.Frame frame_param) {
		// Color each node of the interference graph ig with a register,
		// respecting the interference edges.  The colors are the
		// frame.registers().
	    // Any actual spills must be returned by the callback to spills().
	    ig = ig_param;
	    frame = frame_param;
	    worklistMoves.addAll(ig.moves());
	    initial.addAll(ig.nodes());
	    initial.removeAll(ig.precolored);
    	MkWorklist();
	    
	    do {
	    	if (!simplifyWorklist.isEmpty()) Simplify();
	    	else if (!worklistMoves.isEmpty()) Coalesce();
	    	else if (!freezeWorklist.isEmpty()) Freeze();
	    	else if (!spillWorklist.isEmpty()) SelectSpill();
	    } while (!simplifyWorklist.isEmpty() || !worklistMoves.isEmpty() ||
	    		 !spillWorklist.isEmpty() || !freezeWorklist.isEmpty());
	    
	    AssignColors();
    }
    
    public void MkWorklist() {
    	K = ig.precolored.size();
    	for (Node n : initial) {
    		if (n.degree > K) {
    			spillWorklist.add(n);
    		} else if (MoveRelated(n)) {
    			freezeWorklist.add(n);
    		} else {
    			simplifyWorklist.add(n);
    		}
    	}
    }
    
    private void Simplify() {
    	Node n = simplifyWorklist.getFirst();
    	simplifyWorklist.remove();
    	selectStack.push(n);
    	for (Node m : Adjacent(n)) {
    		DecrementDegree(m);
    	}
    }
    
    private void DecrementDegree(Node m) {
    	int d = m.degree;
    	m.degree--;
    	if (d == K) {
    		LinkedHashSet<Node> nodes = new LinkedHashSet<Node>(Adjacent(m));
    		nodes.add(m);
    		EnableMoves(nodes);
    		spillWorklist.remove(m);
    		
    		if (MoveRelated(m)) {
    			freezeWorklist.add(m);
    		} else {
    			simplifyWorklist.add(m);
    		}
    	}
    }
    
    private void EnableMoves(LinkedHashSet<Node> nodes) {
    	for (Node n : nodes) {
    		for (Move m : NodeMoves(n)) {
    			if (activeMoves.contains(m)) {
    				activeMoves.remove(m);
    				worklistMoves.add(m);
    			}
    		}
    	}
    }
    
    private void Coalesce() {
    	Move m = worklistMoves.getFirst();
    	worklistMoves.remove();
    	Node x = GetAlias(m.dst);
    	Node y = GetAlias(m.src);
    	Node u = null, v = null;
    	if (ig.precolored.contains(y)) {
    		u = y; v = x;
    	} else {
    		u = x; v = y;
    	}
    	
    	if (u == v) {
    		coalescedMoves.add(m);
    		AddWorklist(u);
    	} else if (ig.precolored.contains(v) || 
    			ig.adjSet.contains(new Liveness.Edge(u, v))) {
    		constrainedMoves.add(m);
    		AddWorklist(u);
    		AddWorklist(v);
    	} else if(ig.precolored.contains(u)) {
    		boolean flag = true;
    		for (Node t : Adjacent(v)) {
    			if (! OK(t, u)) {
    				flag = false;
    				break;
    			}
    		}
    		if (flag) {
    			coalescedMoves.add(m);
    			Combine(u, v);
    			AddWorklist(u);
    		}
    	} else if (!ig.precolored.contains(u)) {
    		boolean flag = false;
    		//Brigg's conservative coalescing heuristic
    		LinkedHashSet<Node> coalescedAdj = new LinkedHashSet<Node>(Adjacent(u));
    		coalescedAdj.addAll(Adjacent(v));
    		int numSigNodes = 0;
    		for (Node n : coalescedAdj) {
    			if (n.degree >= K) numSigNodes++;
    		}
    		if (numSigNodes < K) flag = true;
    		
    		if (flag) {
	    		coalescedMoves.add(m);
	    		Combine(u, v);
	    		AddWorklist(u);
    		}
    	} else {
    		activeMoves.add(m);
    	}
    }
    
    private void Combine(Node u, Node v) {
    	if (freezeWorklist.contains(v))
    		freezeWorklist.remove(v);
    	else
    		spillWorklist.remove(v);
    	
    	coalescedNodes.add(v);
    	alias.put(v, u);
    	//TODO: typo in the paper?
    	u.moveList.addAll(v.moveList);
    	
    	for (Node t : Adjacent(v)) {
    		ig.addEdge(t, u);
    		DecrementDegree(t);
    	}
    	
    	if ((u.degree >= K) && freezeWorklist.contains(u)) {
    		freezeWorklist.remove(u);
    		spillWorklist.add(u);
    	}
    }
    
    private boolean OK(Node t, Node r) {
    	return (t.degree < K) || (ig.precolored.contains(t)) 
    			|| ig.adjSet.contains(new Liveness.Edge(t, r)); 
    }
    
    private void AddWorklist(Node u) {
    	if (!ig.precolored.contains(u) && !MoveRelated(u) && (u.degree < K)) {
    		freezeWorklist.remove(u);
    		simplifyWorklist.add(u);
    	}
    }
    
    private Node GetAlias(Node n) {
    	if (coalescedNodes.contains(n)) {
    		return GetAlias(alias.get(n));
    	} else {
    		return n;
    	}
    }
    
    private void Freeze() {
    	Node u = freezeWorklist.getFirst();
    	freezeWorklist.remove();
    	simplifyWorklist.add(u);
    	FreezeMoves(u);
    }
    
    private void FreezeMoves(Node u) {
    	for (Move m : u.moveList) {
    		if (m.dst == u || m.src == u) {
    			Node v = null;
    			if (m.dst == u) v = m.src;
    			else v = m.dst;
    			
    			if (activeMoves.contains(m))
    				activeMoves.remove(m);
    			else
    				worklistMoves.remove(m);
    			frozenMoves.add(m);
    			if (v.moveList.isEmpty() && v.degree < K) {
    				freezeWorklist.remove(v);
    				simplifyWorklist.remove(v);
    			}
    		}
    	}
    }

    private void SelectSpill() {
    	Node m = spillWorklist.remove();
    	//spillWorklist.remove();
    	simplifyWorklist.add(m);
    	FreezeMoves(m);
    }
    
    private void AssignColors() {
    	while (!selectStack.isEmpty()) {
    		Node n = selectStack.pop();
    		LinkedHashSet<Temp> okColor = ig.ArrayToSet(frame.registers());
    		for (Node w : n.adjList) {
    			Node aw = GetAlias(w);
    			if (coloredNodes.contains(aw) || ig.precolored.contains(aw)) {
    				okColor.remove(aw.color);
    			}
    			if (okColor.isEmpty()) {
    				spilledNodes.add(n);
    			} else {
    				coloredNodes.add(n);
    				n.color = okColor.iterator().next();
    			}
    		}
    	}
    	
    	for (Node n : coalescedNodes)
    		n.color = GetAlias(n).color;
    }
    
}
