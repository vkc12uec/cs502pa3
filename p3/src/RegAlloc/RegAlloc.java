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
    public Set<Temp> spills;
    Color color;

    public Temp get(Temp temp) {
        Temp t = ig.get(temp).color;
        if (t == null)
            t = temp;
        return t;
    }
    
    private void RewriteProgram(Translate.Frame frame, LinkedList<Instr> insns) {
    	// code generator
    	//Frame.CodeGen cg = frame.codegen();
    	// allocate memory for spill in frame
        LinkedHashMap<Temp, Access> spillMap = new LinkedHashMap<Temp, Access>();
        for (Temp t : spills) {
        	Access acc = frame.allocLocal(null);
        	spillMap.put(t, acc);
        }
        // make a new instruction list
        LinkedList<Instr> insnsCopy = new LinkedList<Instr>(insns);
        insns.clear();
        // spill the instructions
        ListIterator<Instr> itr = insnsCopy.listIterator();
        while(itr.hasNext()) {
        	Instr insn = itr.next();
        	for (int i = 0; i < insn.use.length; i++) {
        		if (spills.contains(insn.use[i])) {
        			Temp v = new Temp();
        			Tree.Exp vFrame = spillMap.get(insn.use[i]).exp(frame.FP());
        			Tree.Stm stm = new MOVE(new TEMP(v), vFrame);
        			Frame.CodeGen cg = frame.codegen();
        			stm.accept(cg);
        			LinkedList<Assem.Instr> insnsFch = cg.insns();
        			insn.use[i] = v;
        			insns.addAll(insnsFch);
        		}
        	}
        	// insert between use and def
        	insns.addLast(insn);
        	for (int i = 0; i < insn.def.length; i++) {
        		if (spills.contains(insn.def[i])) {
        			Temp v = new Temp();
        			Tree.Exp vFrame = spillMap.get(insn.def[i]).exp(frame.FP());
        			Tree.Stm stm = new MOVE(vFrame, new TEMP(v));
        			Frame.CodeGen cg = frame.codegen();
        			stm.accept(cg);
        			LinkedList<Assem.Instr> insnsStr = cg.insns();
        			insn.def[i] = v;
        			insns.addAll(insnsStr);
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
            for (Temp s : spills)
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
