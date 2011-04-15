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

    public Liveness(FlowGraph.AssemFlowGraph flow, Translate.Frame frame) {
        // TODO: Compute the interference graph representing liveness
        // information for the temporaris used and defined by the program represented
        // by flow.
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
