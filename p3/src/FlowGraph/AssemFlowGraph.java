/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package FlowGraph;

import Assem.Instr.*;
import Translate.Temp;
import Translate.Temp.*;

import java.util.*;

public class AssemFlowGraph extends FlowGraph<Assem.Instr, AssemNode> {

    public Set<Temp> def(AssemNode node) {
        return node.def;
    }

    public Set<Temp> use(AssemNode node) {
        return node.use;
    }

    /**
     * My implementation builds a flowgraph node per basic block.
     */
    public AssemFlowGraph(Translate.Frame frame, LinkedList<Assem.Instr> insns) {
        LinkedHashMap<Label, AssemNode> blocks =
            new LinkedHashMap<Label, AssemNode>();
        AssemNode from = null;
        boolean seenStm = false;
        for (Assem.Instr i : insns) {
            if (i instanceof LABEL) {
                if (from == null) {
                    from = new AssemNode(this, i);
                    seenStm = false;
                } else if (seenStm) {
                    AssemNode to = new AssemNode(this, i);
                    addEdge(from, to);
                    from = to;
                    seenStm = false;
                }
                blocks.put(((LABEL) i).label, from);
            } else {
                if (from == null)
                    from = new AssemNode(this, i);
                seenStm = true;
                from.instrs.add(i);
                if (i.jumps.length != 0)
                    from = null;
            }
        }
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
    }
}
