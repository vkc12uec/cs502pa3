/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Translate;

import java.util.*;
import Translate.Temp.*;

/**
 * Frame is an abstract class whose concrete instances represent frames on the
 * target machine.
 */
public abstract class Frame {
    /**
     * An Access represents storage for a variable accessible in the scope of this frame.
     */
    public abstract class Access {
        /**
         * Generate an expression to access the variable.
         * @param fp    frame pointer of the frame to access
         * @return      expression to access the variable
         */
        public abstract Tree.Exp exp(Tree.Exp fp);
    }

    /**
     * The label for this frame.
     */
    public final Label name;

    public boolean isGlobal = false;

    protected Frame(Label name) {
        this.name = name;
    }

    /**
     * Factory for obtaining concrete instances of the target frame.
     * 
     * @param name      the name of the method
     * @return          a new frame
     */
    public abstract Frame newFrame(String name);

    public Access link = null;

    /**
     * Allocate a formal parameter represented by temporary t.
     * 
     * If t is null, then allocate the parameter in the frame.
     */
    public abstract Access allocFormal(Temp t);

    /**
     * LinkedList of the allocated formals.
     */
    public final LinkedList<Access> formals = new LinkedList<Access>();

    /**
     * Allocate a local variable represented by temporary t.
     * 
     * If t is null, then allocate the variable in the frame.
     */
    public abstract Access allocLocal(Temp t);

    /**
     * Maximum number of arguments used by calls in this frame;
     */
    public int maxArgsOut = -1; // <0 for leaf routines

    /**
     * The registers supported by this target.
     */
    public abstract Temp[] registers();

    /**
     * The word size (in bytes) of this target.
     */
    public abstract int wordSize();

    /**
     * Get a reference to an external procedure.
     */
    public abstract Tree.Exp external(String func);

    /**
     * An expression for the frame pointer of this frame.
     */
    public abstract Tree.Exp FP();

    /**
     * An expression for the return value of this frame.
     */
    public abstract Tree.Exp RV();

    /**
     * Allocate an initialized static string literal.
     * 
     * @param label
     *                the label to use for the string
     * @param value
     *                the value of the literal
     * @return the assembler data fragment
     */
    public abstract String string(Label label, String value);

    /**
     * Allocate a zeroed static record
     * 
     * @param label
     *                the label to use for the record
     * @param words
     *                the size of the record in words
     * @return the assembler data fragment
     */
    public abstract String record(Label label, int words);

    /**
     * Allocate and initialize a static virtual dispatch table
     * 
     * @param label
     *                the label to use for the record
     * @param methods
     *                the labels of the methods in the table
     * @return the assembler data fragment
     */
    public abstract String vtable(Label label, Collection<Label> methods);

    /**
     * Allocate and initialize a switch table
     * 
     * @param label
     *                the label to use for the record
     * @param values
     *                the values of the cases in the table
     * @param labels
     *                the labels of the cases in the table
     * @return the assembler data fragment
     */
    public abstract String switchtable(Label label,
				       int[] values,
				       Label[] labels);

    /**
     * Get the label to branch to for null pointers
     * 
     * @return the label
     */
    public abstract Label badPtr();

    /**
     * Get the label to branch to for bad subscripts
     * 
     * @return the label
     */
    public abstract Label badSub();

    /**
     * Wrap the statement tree body of a procedure with entry/exit after
     * translation.
     * 
     * @param stms
     *                the procedure body
     */
    public abstract void procEntryExit1(LinkedList<Tree.Stm> stms);

    /**
     * Obtain a code generator visitor for the target, producing a list of
     * assembler instructions.
     * 
     * @return the code generator visitor
     */
    public interface CodeGen extends Tree.Visitor<Temp> {
	LinkedList<Assem.Instr> insns();
    }
    public abstract CodeGen codegen();

    /**
     * Wrap the assembly body of a procedure with entry/exit after code
     * generation.
     * 
     * @param insns
     *                the procedure body
     */
    public abstract void procEntryExit2(LinkedList<Assem.Instr> insns);

    /**
     * Wrap the assembly body of a procedure with entry/exit after register
     * allocation.
     * 
     * @param insns
     *                the procedure body
     * @param map
     *                the register allocation mapping temporaries to hard
     *                registers
     */
    public abstract void procEntryExit3(LinkedList<Assem.Instr> insns, Temp.Map map);
}
