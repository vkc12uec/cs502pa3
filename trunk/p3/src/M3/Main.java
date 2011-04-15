/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package M3;

import java.io.*;
import java.util.*;
import Translate.Frame;
import Translate.Frag;
import Translate.Tree;
import Translate.Temp;

public class Main {
    static PrintWriter out, dbg;

    static void emitProc(Frag.Proc f) {
        Frame frame = f.frame;
        dbg.println("PROCEDURE " + frame.name);
        LinkedList<Tree.Stm> traced = new LinkedList<Tree.Stm>();
        if (f.body != null) {
            dbg.println("# Before canonicalization (trees):");
            new Tree.Print(dbg, f.body);

            LinkedList<Tree.Stm> stms = new Canon.Canon(f.body).stms();

            dbg.println("# After canonicalization (trees):");
            new Tree.Print(dbg, stms);

            dbg.println("# Basic Blocks:");
            Canon.BasicBlocks blocks = new Canon.BasicBlocks(stms);
            for (LinkedList<Tree.Stm> b : blocks.list)
                new Tree.Print(dbg, b);

            dbg.println("# Trace Scheduled:");
            new Canon.TraceSchedule(blocks, traced);
            new Tree.Print(dbg, traced);
        }
        dbg.println("# With procedure entry/exit:");
        frame.procEntryExit1(traced);
        new Tree.Print(dbg, traced);
        dbg.println("# Instructions:");
        Frame.CodeGen cg = frame.codegen();
        for (Tree.Stm s : traced)
            s.accept(cg);
        LinkedList<Assem.Instr> insns = cg.insns();
        Temp.Map map = new Temp.Map.Default();
        for (Assem.Instr i : insns) {
            dbg.print(i.format(map));
            dbg.print("\t# ");
            for (Temp d : i.def)
                dbg.print(d + " ");
            dbg.print((i instanceof Assem.Instr.MOVE) ? ":= " : "<- ");
            for (Temp u : i.use)
                dbg.print(u + " ");
            if (i.jumps.length > 0) {
                dbg.print(": goto ");
                for (Temp.Label l : i.jumps)
                    dbg.print(l + " ");
            }
            dbg.println();
        }
        dbg.flush();
        frame.procEntryExit2(insns);
        map = new RegAlloc.RegAlloc(frame, insns, dbg);
        dbg.println("# Assembly code:");
        frame.procEntryExit3(insns, map);
        for (Assem.Instr i : insns) {
            String insn = i.format(map);
            out.println(insn);
            dbg.println(insn);
        }
        out.flush();
        dbg.flush();
        dbg.println("END " + frame.name);
        dbg.flush();
    }

    public static boolean useFP = false;
    public static boolean verbose = true;
    public static boolean spilling = true;
    public static boolean coalescing = true;

    private static void usage() {
        String usage =
            "Usage: java Main.MiniJava [-useFP|-nouseFP]"
            + "[-target=[Mips|PPCDarwin|PPCLinux]]"
            + "[-quiet|-verbose]"
            + "[-spill|-nospill] [-coalesce|-nocoalesce]"
            + "<source>.java";
        throw new Error(usage);
    }

    static String mainClass;

    public static void main(String[] args) throws java.io.IOException {
        Frame target = new Mips.Frame();
        boolean main = false;
        if (args.length < 1) usage();
        if (args.length > 1)
            for (String arg : args) {
                if (arg.equals("-main"))
                    main = true;
                else if (arg.equals("-useFP")) useFP = true;
                else if (arg.equals("-nouseFP")) useFP = false;
                else if (arg.equals("-quiet")) verbose = false;
                else if (arg.equals("-verbose")) verbose = true;
                else if (arg.equals("-target=Mips"))
                    target = new Mips.Frame();
                else if (arg.equals("-target=PPCDarwin"))
                    target = new PPC.Frame.Darwin();
                else if (arg.equals("-target=PPCLinux"))
                    target = new PPC.Frame.Linux();
                else if (arg.equals("-target=Mini"))
                    target = new Mini.Frame();
                else if (arg.equals("-spill")) spilling = true;
                else if (arg.equals("-nospill")) spilling = false;
                else if (arg.equals("-coalesce")) coalescing = true;
                else if (arg.equals("-nocoalesce")) coalescing = false;
                else if (arg.startsWith("-")) usage();
            }
        Translate.target = target;
        String src = args[args.length - 1];
        java.io.File file = new java.io.File(src);

        try {
            Value.Module module = Semant.TypeCheck(file);
            if (Semant.anyErrors) return;
            List<Frag> frags = Translate.Compile(module, main);
            if (Semant.anyErrors) return;
            String dst = module.name + ".s";
            out = new PrintWriter(new FileOutputStream(dst));
            dbg =
                (verbose) ? new PrintWriter(System.out)
                    : new PrintWriter(new NullOutputStream());
            for (Frag f : frags) {
                if (f instanceof Frag.Proc)
                    emitProc((Frag.Proc) f);
                else {
                    dbg.println(f);
                    dbg.flush();
                    out.println(f);
                }
            }
            out.close();
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            return;
        } catch (TokenMgrError e) {
            System.err.println(e.getMessage());
            return;
        }
    }        
}

class NullOutputStream extends java.io.OutputStream {
    public void write(int b) {}
}
