/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

import java.util.*;

import Translate.Frame;
import Translate.Temp;
import Translate.Tree;
import Translate.Frame.Access;
import Translate.Tree.Exp.*;
import Translate.Tree.Stm.*;
import Assem.Instr;

public class RegAlloc implements Temp.Map {
    FlowGraph.AssemFlowGraph cfg;
    Liveness ig;
    public Set<Node> spills;
    Color color;

    public Temp get(Temp temp) {
        Temp t = ig.get(temp).color;
        if (t == null)
            t = temp;
        return t;
    }
    
    private void RewriteProgram(Translate.Frame frame, LinkedList<Instr> insns) {
    	// code generator
    	Frame.CodeGen cg = frame.codegen();
    	// allocate memory for spill in frame
        LinkedHashMap<Temp, Access> spillMap = new LinkedHashMap<Temp, Access>();
        for (Node n : spills) {
        	Access acc = frame.allocLocal(n.temp);
        	spillMap.put(n.temp, acc);
        }
        // spill the instructions
        for (Instr insn : insns) {
        	for (int i = 0; i < insn.def.length; i++) {
        		if (spills.contains(insn.def[i])) {
        			Temp v = new Temp();
        			Tree.Stm stm = new MOVE(spillMap.get(insn.def[i]).exp(frame.FP()), new TEMP(v));
        			stm.accept(cg);
        			LinkedList<Assem.Instr> insnsStr = cg.insns();
        			insn.def[i] = v;
        			insns.addAll(insns.indexOf(insn) + 1, insnsStr);
        		}
        	}
        	for (int i = 0; i < insn.use.length; i++) {
        		if (spills.contains(insn.use[i])) {
        			Temp v = new Temp();
        			Tree.Stm stm = new MOVE(new TEMP(v), spillMap.get(insn.def[i]).exp(frame.FP()));
        			stm.accept(cg);
        			LinkedList<Assem.Instr> insnsFch = cg.insns();
        			insn.use[i] = v;
        			insns.addAll(insns.indexOf(insn), insnsFch);
        		}
        	}
        }
    }

    public RegAlloc(Translate.Frame frame, LinkedList<Instr> insns,
            java.io.PrintWriter out) {
        for (;;) {
            out.println("# Control Flow Graph:");
            cfg = new FlowGraph.AssemFlowGraph(frame, insns);
            cfg.show(out);
            out.println("# Interference Graph:");
            ig = new Liveness(cfg, frame);
            ig.show(out);
            color = new Color(ig, frame);
            spills = color.spills();
            if (spills.isEmpty())
                break;
            out.println("# Spills:");
            for (Node s : spills)
                out.println(s);
            // rewrite programs
            RewriteProgram(frame, insns);
        }
        out.println("# Register Allocation:");
        for (Node n : ig.nodes()) {
            out.print(n.temp);
            out.print("->");
            out.println(n.color);
        }
    }
}
