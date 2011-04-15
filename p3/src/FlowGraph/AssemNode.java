/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package FlowGraph;

import Translate.Temp;
import Graph.Node;
import Assem.Instr;
import java.util.*;

public class AssemNode extends Node<Instr, AssemNode> {
    public LinkedList<Instr> instrs = new LinkedList<Instr>();
    public Set<Temp> liveIn = new LinkedHashSet<Temp>(),
            liveOut = new LinkedHashSet<Temp>(),
            def = new LinkedHashSet<Temp>(), use = new LinkedHashSet<Temp>();

    AssemNode(AssemFlowGraph g, Instr i) {
        super(g, i);
    }
}
