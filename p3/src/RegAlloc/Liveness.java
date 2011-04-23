/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import FlowGraph.AssemNode;
import Assem.Instr;
import Translate.Temp;
import Translate.Temp.Label;

import java.util.*;

public class Liveness extends InterferenceGraph {
    LinkedList<Move> moves = new LinkedList<Move>();

    public List<Move> moves() {
        return moves;
    }
    
    public boolean isEqual (Set <Temp> orig, Set <Temp> curr){
    	// orig will always be small than curr
    	for (Temp t : curr){
    		if(!orig.contains(t)){
    			orig.addAll(curr);	// add to orig Set
    			return false;
    		}
    	}
    	return true;
    }

    public Liveness(FlowGraph.AssemFlowGraph flow, Translate.Frame frame) {
        // TODO: Compute the interference graph representing liveness
        // information for the temporaris used and defined by the program represented
        // by flow.
    	
    	// populate move list here by each instr in node n
    	
    	/*
    	         for (AssemNode n : nodes()) {
            LinkedList<Assem.Instr> instrs = n.instrs;
            for (Assem.Instr i : instrs) {
                for (Temp u : i.use)
                    if (!n.def.contains(u))
                        n.use.add(u);
                for (Temp d : i.def)
                    n.def.add(d);
            }
            Assem.Instr i = instrs.getLast();
            for (Label l : i.jumps) {
                AssemNode t = blocks.get(l);
                if (t != null)
                    addEdge(n, t);
            }
        }
    	 */
    	
    	boolean repeat = false;
    	
    	do {
    		repeat = false;
    		for (AssemNode n: flow.nodes()){
    			Set<Temp> node_def = flow.def(n);
    			Set<Temp> node_use = flow.use(n);
    			
    			// in = use + out -def
    			// out = union (in [foreach succ(i) of n])
    			
    			LinkedHashSet<Temp> in = new LinkedHashSet<Temp>();
    			LinkedHashSet<Temp> out = new LinkedHashSet<Temp>();
    			
    			for (Temp t : node_use){
    				in.add(t);
    			}
    			for (Temp t : n.liveOut){
    				in.add (t);
    			}
    			for (Temp t: node_def){
    				in.remove(t);
    			}
    			
    			for (AssemNode a : n.succs){
    				out.addAll(a.liveIn);    			
    			}
    			
    			//check
    			if (!n.liveIn.equals(in) || !n.liveOut.equals(out))
    				repeat = true;    			
    		}
    		
    	} while(repeat);
    	
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
}
