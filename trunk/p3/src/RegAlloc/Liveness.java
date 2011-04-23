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
    
	Set <Node> precolored = new LinkedHashSet <Node> ();
	Set <Node> initial = new LinkedHashSet <Node> ();
	Set <Node> simplifyWorklist = new LinkedHashSet <Node> (), freezeWorklist = new LinkedHashSet <Node> (),
						spillWorklist = new LinkedHashSet <Node> (), spilledNodes = new LinkedHashSet <Node> (),
						coalescedNodes = new LinkedHashSet <Node> (), coloredNodes = new LinkedHashSet <Node> ();
	Stack <Node> selectStack = new Stack <Node> ();
	Set <Move> worklistMoves = new LinkedHashSet <Move> ();
	
	public Hashtable <Temp, Node> temp2Node = new Hashtable <Temp, Node> ();
	
	public LinkedHashSet<Temp> arr2Set(Temp []u){
    	LinkedHashSet<Temp> lh = new LinkedHashSet<Temp>();
    	for (Temp ui : u)	//int i =0 ;i < u.length; i++)
    		lh.add(ui);
    	return lh;
    }
    
	private Node t2N(Temp t) {
		Node n = temp2Node.get(t);
		if (n == null) {
			n = new Node(this,t);
			temp2Node.put(t, n);
		}
		return n;
	}
	
    public Liveness(FlowGraph.AssemFlowGraph flow, Translate.Frame frame) {
        // TODO: Compute the interference graph representing liveness
        // information for the temporaris used and defined by the program represented
        // by flow.
    	
    	// populate move list here by each instr in node n TODO
    	
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
    	
    //build();

		for (AssemNode b : flow.nodes()) {
			LinkedHashSet <Temp> live = new LinkedHashSet<Temp>(b.liveOut);
			for (int i = b.instrs.size() - 1; i >= 0; i--) {
				Instr inst = b.instrs.get(i);
				
				if (inst instanceof Instr.MOVE) {
					live.removeAll(arr2Set(inst.use));
					LinkedHashSet <Temp> nodes = arr2Set(inst.def);
					nodes.addAll(arr2Set(inst.use));
					for (Temp n : nodes)
						t2N(n).moveList.add((Instr.MOVE)inst);		// where do we define these ?
					moves.add((Instr.MOVE)inst);
				}
				
				live.addAll(arr2Set(inst.def));
				//)Addall(live, inst.def);	//live.addAll(inst.def);	CHECK is it pass by ref ?
				
				for (Temp d : inst.def){
					Node n1 = new Node(this,d);
					for (Temp l : live){
						Node n2 = new Node(this,l);
						addEdge(n1, n2);
					}
				}
				live.removeAll(arr2Set(inst.def));
				live.addAll(arr2Set(inst.use));
			}
		}
	
    }
    
    public void Addall (LinkedHashSet<Temp> l, Temp [] u){
    	l.addAll(arr2Set(u));
    }
    

    
    public void build(){
    	/*Build only adds an interference edge between a node that is defined
    	at some point and the nodes that are currently live at that point. It is not necessary
    	to add interferences between nodes in the live set. These edges will be added when
    	processing other blocks in the program*/
		/*for (BasicBlock b : blocks) {
			HashSet <Temp> live = new HashSet<Temp>(b.live_out);
			for (int i = b.list.size() - 1; i >= 0; i--) {
				TExp inst = b.list.get(i);
				if (inst instanceof Move) {
					live.removeAll(inst.livenessNode.use);
					HashSet <Temp> nodes = new HashSet<Temp>(inst.livenessNode.def);
					nodes.addAll(inst.livenessNode.use);
					for (Temp n : nodes)
						t2N(n).moveList.add((Move)inst);
					worklistMoves.add((Move)inst);
				}
				live.addAll(inst.livenessNode.def);
				for (Temp d : inst.livenessNode.def)
					for (Temp l : live)
						addEdge(l, d);
				live.removeAll(inst.livenessNode.def);
				live.addAll(inst.livenessNode.use);
			}
		}
	*/
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
