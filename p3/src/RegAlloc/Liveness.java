/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import FlowGraph.AssemNode;
import Assem.Instr;
import Translate.Temp;
import java.util.*;

public class Liveness extends InterferenceGraph {
    LinkedList<Move> moves = new LinkedList<Move>();

    public List<Move> moves() {
        return moves;
    }
    
    public Set <Node> precolored = new LinkedHashSet <Node> ();
	//edge, node related variables
	public LinkedHashSet<Edge> adjSet = new LinkedHashSet<Edge>();
	
	public LinkedHashSet<Temp> ArrayToSet(Temp[] arr){
		LinkedHashSet<Temp> set = new LinkedHashSet<Temp>(Arrays.asList(arr));
    	return set;
    }
    
	private Node getNode(Temp t) {
		Node n = get(t);
		
		if (n == null) {
			n = new Node(this, t);
			put(t, n);
		}
		
		return n;
	}
	
    public Liveness(FlowGraph.AssemFlowGraph flow, Translate.Frame frame) {
    	//Liveness analysis for the CFG
    	
    	boolean repeat;
    	
    	do {
    		repeat = false;
    		
    		for (AssemNode n: flow.nodes()){
    			Set<Temp> node_def = flow.def(n);
    			Set<Temp> node_use = flow.use(n);
    						
    			// in'[n] <- in[n]
    			// out'[n] <- out[n]
    			LinkedHashSet<Temp> in = new LinkedHashSet<Temp>(n.liveIn);
    			LinkedHashSet<Temp> out = new LinkedHashSet<Temp>(n.liveOut);
    			
    			// in = use + (out - def)
    			n.liveIn = new LinkedHashSet<Temp>(n.liveOut);
    			n.liveIn.removeAll(node_def);
    			n.liveIn.addAll(node_use);
    			
    			// out = union (in [foreach succ(s) of n])
    			n.liveOut.clear();
    			for (AssemNode sNode : n.succs){
    				n.liveOut.addAll(sNode.liveIn);    			
    			}
    			
    			//check
    			if (!n.liveIn.equals(in) || !n.liveOut.equals(out))
    				repeat = true;    			
    		}//for
    		
    	} while(repeat);
    	
    	//assign precolor registers
    	LinkedHashSet<Temp> precolorTemp = ArrayToSet(frame.registers());
    	for ( Temp t : precolorTemp) {
    		Node n = new Node(this, t);
    		n.color = t;
			put(t, n);
			precolored.add(n);
    	}
    	
    	Build(flow);

    }
    
    private void Build(FlowGraph.AssemFlowGraph flow) {
		for (AssemNode b : flow.nodes()) {
			LinkedHashSet<Temp> live = new LinkedHashSet<Temp>(b.liveOut);
			for (int i = b.instrs.size() - 1; i >= 0; i--) {
				Instr inst = b.instrs.get(i);
				
				if (inst instanceof Instr.MOVE) {
					live.removeAll(ArrayToSet(inst.use));
					LinkedHashSet<Temp> nodes = ArrayToSet(inst.def);
					nodes.addAll(ArrayToSet(inst.use));
					
					Move m = null;
					Node s = getNode(((Instr.MOVE) inst).src());
					Node d = getNode(((Instr.MOVE) inst).dst());
					m = new Move(s, d);
					moves.add(m);
					for (Temp n : nodes){
						getNode(n).moveList.add(m);
					}
				}
				
				live.addAll(ArrayToSet(inst.def));
				
				for (Temp l : live){
					Node ln = getNode(l);
					//TODO spill cost
					ln.spillCost++;
					for (Temp d : inst.def){
						Node dn = getNode(d);
						addEdge(dn, ln);
					}
				}
				
				live.removeAll(ArrayToSet(inst.def));
				live.addAll(ArrayToSet(inst.use));
			}
		}
	}
    
    public void addEdge(Node n0, Node n1) {
    	if (n0 != n1) {
    		adjSet.add(new Edge(n0, n1));
    		adjSet.add(new Edge(n1, n0));
    		if (!precolored.contains(n0)) {
    			n0.adjList.add(n1);
    			n0.degree++;
    		}
    		if (!precolored.contains(n1)) {
    			n1.adjList.add(n0);
    			n1.degree++;
    		}
    	}
    }
    
    public void show(java.io.PrintWriter out) {
        for (Node n : nodes()) {
            out.print(n.temp.toString());
            out.print(": ");
            for (Node s : n.succs) {
                out.print(s.temp.toString());
                out.print(" ");
            }
            out.println();
        }
        for (Move move : moves) {
            out.print(move.dst.temp.toString());
            out.print(" <= ");
            out.println(move.src.temp.toString());
        }
        out.flush();
    }
    
    static public class Edge {
    	public Node u = null;
    	public Node v = null;
    	public Edge(Node first, Node second) {
    		u = first;
    		v = second;
    	}
    	public int hashCode() {
    		int hashu = u.hashCode();
    		int hashv = v.hashCode();
    		return (hashu + hashv) * hashv + hashu;
    	}
    	
    	public boolean equals(Object other) {
    		if (other instanceof Edge) {
    			return (this.u == ((Edge)other).u) && (this.v == ((Edge)other).v);
    		} else
    			return false;
    	}
    }
}
