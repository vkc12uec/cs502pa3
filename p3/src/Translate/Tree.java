package Translate;

import java.util.List;
import Translate.Temp.*;

/**
 * Intermediate code trees (IR)
 */
public abstract class Tree {
    public abstract Exp[] kids();

    public abstract <R> R accept(Visitor<R> v);

    public static abstract class Exp extends Tree {
        public abstract Exp build(Exp[] kids);

        public static class BINOP extends Exp {
            public enum Operator {
                ADD("ADD"),
                AND("AND"),
                DIV("DIV"),
                DIVU("DIVU"),
                MOD("MOD"),
                MUL("MUL"),
                OR("OR"),
                SLL("SLL"),
                SRA("SRA"),
                SRL("SRL"),
                SUB("SUB"),
                XOR("XOR");

                final String string;

                Operator(String s) {
                    string = s;
                }

                public String toString() {
                    return string;
                }
            }

            public final Operator op;
            public final Exp left, right;

	    /**
	     * A binary operation: apply operator o to results of evaluating
	     * first l then r.
	     *
	     * @param o
	     *                operator
	     * @param l
	     *                left operand
	     * @param r
	     *                right operand
	     */
            public BINOP(Operator o, Exp l, Exp r) {
                op = o;
                left = l;
                right = r;
            }

            public Exp[] kids() {
                return new Exp[] { left, right };
            }

            public Exp build(Exp[] kids) {
                return new BINOP(op, kids[0], kids[1]);
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class CALL extends Exp {
            /**
             * function to call
             */
            public final Exp func;
            /**
             * the static chain
             */
            public final Exp link;
            /**
             * arguments
             */
            public final Exp[] args;

	    /**
	     * A procedure call: evaluate f to obtain address of subroutine
	     * then each of the arguments.
	     *
             * @param f
             *                address expression
             * @param l
             *                static chain argument
	     * @param a
	     *                standard arguments
	     */
            public CALL(Exp f, Exp l, Exp[] a) {
                func = f;
                link = l;
                args = a;
            }

            public Exp[] kids() {
                Exp[] kids = new Exp[args.length + 2];
                kids[0] = func;
                kids[1] = link;
                for (int i = 0; i < args.length; i++)
                    kids[i + 2] = args[i];
                return kids;
            }

            public Exp build(Exp[] kids) {
                Exp[] args = new Exp[kids.length - 2];
                for (int i = 0; i < args.length; i++)
                    args[i] = kids[i + 2];
                return new CALL(kids[0], kids[1], args);
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class CONST extends Exp {
            public final int value;

	    /**
	     * A constant integer.
	     *
	     * @param v
             *                value
	     */
            public CONST(int v) {
                value = v;
            }

            public Exp[] kids() {
                return new Exp[] {};
            }

            public Exp build(Exp[] kids) {
                return this;
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class ESEQ extends Exp {
            public final Stm stm;
            public final Exp exp;

	    /**
	     * An expression sequence.
	     * Execute s for side-effects then evaluate e for result.
	     *
	     * @param s
             *                statement
	     * @param e
	     *                expression
	     */
            public ESEQ(Stm s, Exp e) {
                stm = s;
                exp = e;
            }

            public Exp[] kids() {
                throw new Error("kids() not applicable to ESEQ");
            }

            public Exp build(Exp[] kids) {
                throw new Error("build() not applicable to ESEQ");
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class MEM extends Exp {
            public final Exp exp;
            public final CONST offset;

	    /**
	     * A memory access to the contents of a word of memory
	     * starting at address e+c.
	     * 
	     * @param e
             *                address expression
	     * @param c
	     *                constant offset
	     */
            public MEM(Exp e, CONST c) {
                exp = e;
                offset = c;
            }

            public Exp[] kids() {
                return new Exp[] { exp };
            }

            public Exp build(Exp[] kids) {
                return new MEM(kids[0], offset);
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class NAME extends Exp {
            public final Label label;

	    /**
	     * A symbolic constant naming a labeled location.
	     *
	     * @param l
             *                label
	     */
            public NAME(Label l) {
                label = l;
            }

            public Exp[] kids() {
                return new Exp[] {};
            }

            public Exp build(Exp[] kids) {
                return this;
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class TEMP extends Exp {
            public final Temp temp;

	    /**
	     * A temporary (one of any number of "registers")
	     *
	     * @param t
             *                temporary
	     */
            public TEMP(Temp t) {
                temp = t;
            }

            public Exp[] kids() {
                return new Exp[] {};
            }

            public Exp build(Exp[] kids) {
                return this;
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }
    }

    public static abstract class Stm extends Tree {
        public abstract Stm build(Exp[] kids);

        public static class CJUMP extends Stm {
            public enum Operator {
                BEQ("BEQ"),
                BNE("BNE"),
                BGE("BGE"),
                BLE("BLE"),
                BGT("BGT"),
                BLT("BLT");
                private String string;

                Operator(String s) {
                    string = s;
                }

                public String toString() {
                    return string;
                }
            }

            public final Operator op;
            public final Exp left, right;
            public final Label iftrue, iffalse;

	    /**
	     * A conditional jump: apply operator o to results of evaluating
	     * first l then r.  If true jump to t, if false jump to f.
	     *
	     * @param o
	     *                operator
	     * @param l
	     *                left operand
	     * @param r
	     *                right operand
	     * @param t
	     *                true target
	     * @param f
	     *                false target
	     */
            public CJUMP(Operator o, Exp l, Exp r, Label t, Label f) {
                op = o;
                left = l;
                right = r;
                iftrue = t;
                iffalse = f;
            }

            public Exp[] kids() {
                return new Exp[] { left, right };
            }

            public Stm build(Exp[] kids) {
                return new CJUMP(op, kids[0], kids[1], iftrue, iffalse);
            }

            public CJUMP not() {
                switch (op) {
                case BEQ:
                    return new CJUMP(Operator.BNE, left, right, iffalse, iftrue);
                case BNE:
                    return new CJUMP(Operator.BEQ, left, right, iffalse, iftrue);
                case BGE:
                    return new CJUMP(Operator.BLT, left, right, iffalse, iftrue);
                case BLE:
                    return new CJUMP(Operator.BGT, left, right, iffalse, iftrue);
                case BGT:
                    return new CJUMP(Operator.BLE, left, right, iffalse, iftrue);
                case BLT:
                    return new CJUMP(Operator.BGE, left, right, iffalse, iftrue);
                default:
                    throw new Error();
                }
            }

            public CJUMP swap() {
                switch (op) {
                case BEQ:
                    return new CJUMP(Operator.BEQ, right, left, iftrue, iffalse);
                case BNE:
                    return new CJUMP(Operator.BEQ, right, left, iftrue, iffalse);
                case BGE:
                    return new CJUMP(Operator.BLE, right, left, iftrue, iffalse);
                case BLE:
                    return new CJUMP(Operator.BGE, right, left, iftrue, iffalse);
                case BGT:
                    return new CJUMP(Operator.BLT, right, left, iftrue, iffalse);
                case BLT:
                    return new CJUMP(Operator.BGT, right, left, iftrue, iffalse);
                default:
                    throw new Error();
                }
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class EXP extends Stm {
            public final Exp exp;

	    /**
	     * An expression statement: evaluate e, discarding the result.
	     *
	     * @param e
	     *                expression
	     */
            public EXP(Exp e) {
                exp = e;
            }

            public Exp[] kids() {
                return new Exp[] { exp };
            }

            public Stm build(Exp[] kids) {
                return new EXP(kids[0]);
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class JUMP extends Stm {
            public final Exp exp;
            public final Label[] targets;

	    /**
	     * An unconditional jump: evaluate e to obtain target address
	     * then jump to that target.
	     *
	     * @param e
	     *                address expression
	     * @param t
	     *                complete list of possible targets
	     */
            public JUMP(Exp e, Label[] t) {
                exp = e;
                targets = t;
            }

	    /**
	     * An unconditional jump: jump to target.
	     *
	     * @param t
	     *                target
	     */
            public JUMP(Label target) {
                exp = new Exp.NAME(target);
                targets = new Label[] { target };
            }

            public Exp[] kids() {
                return new Exp[] { exp };
            }

            public Stm build(Exp[] kids) {
                return new JUMP(kids[0], targets);
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class LABEL extends Stm {
            public final Label label;

	    /**
	     * Label this code location with a symbolic name.
	     *
	     * @param l
	     *                label
	     */
            public LABEL(Label l) {
                label = l;
            }

            public Exp[] kids() {
                return new Exp[] {};
            }

            public Stm build(Exp[] kids) {
                return this;
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class MOVE extends Stm {
            public final Exp dst, src;

	    /**
	     * A move statement: evaluate the storage destination expression d,
	     * then the source value expression s, then move the source value
	     * to the addressed storage (memory/temporary)
	     *
	     * @param d
	     *                addressed storage (memory/temporary)
	     * @param s
	     *                value to store
	     */
            public MOVE(Exp d, Exp s) {
                dst = d;
                src = s;
            }

            public Exp[] kids() {
                if (dst instanceof Exp.MEM)
                    return new Exp[] { ((Exp.MEM) dst).exp, src };
                return new Exp[] { src };
            }

            public Stm build(Exp[] kids) {
                if (dst instanceof Exp.MEM) {
                    Exp.MEM m = (Exp.MEM) dst;
                    return new MOVE(new Exp.MEM(kids[0], m.offset), kids[1]);
                }
                return new MOVE(dst, kids[0]);
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }

        public static class SEQ extends Stm {
            public final Stm left, right;

	    /**
	     * A sequence statement: execute l then r.
	     *
	     * @param l
	     *                left operand
	     * @param r
	     *                right operand
	     */
            public SEQ(Stm l, Stm r) {
                left = l;
                right = r;
            }

            public Exp[] kids() {
                throw new Error("kids() not applicable to SEQ");
            }

            public Stm build(Exp[] kids) {
                throw new Error("clone() not applicable to SEQ");
            }

            public <R> R accept(Visitor<R> v) {
                return v.visit(this);
            }
        }
    }

    public static interface Visitor<R> {
        public abstract R visit(Stm.SEQ n);
        public abstract R visit(Stm.LABEL n);
        public abstract R visit(Stm.JUMP n);
        public abstract R visit(Stm.MOVE n);
        public abstract R visit(Stm.EXP n);
        public abstract R visit(Stm.CJUMP n);
        public abstract R visit(Exp.MEM n);
        public abstract R visit(Exp.TEMP n);
        public abstract R visit(Exp.ESEQ n);
        public abstract R visit(Exp.NAME n);
        public abstract R visit(Exp.CONST n);
        public abstract R visit(Exp.CALL n);
        public abstract R visit(Exp.BINOP n);
    }

    public static class Print implements Visitor<Void> {
        final java.io.PrintWriter out;
        int d = 0;

        public Print(java.io.PrintWriter o, Stm s) {
            out = o;
            s.accept(this);
            o.println();
        }
        void print(Tree t, int i) {
            int save = d;
            d = i;
            t.accept(this);
            d = save;
        }

        public Print(java.io.PrintWriter o, List<Stm> stms) {
            out = o;
            for (Stm s : stms) {
                s.accept(this);
                o.println();
            }
            o.flush();
        }

        private void indent(int d) {
            for (int i = 0; i < d; i++)
                out.print(' ');
        }

        private void sayln(String s) {
            out.println(s);
            out.flush();
        }

        private void say(String s) {
            out.print(s);
        }

        private void say(Object o) {
            out.print(o.toString());
        }

        public Void visit(Stm.SEQ s) {
            s.left.accept(this);
            sayln(",");
            s.right.accept(this);
            return null;
        }

        public Void visit(Stm.LABEL s) {
            indent(d);
            say("LABEL ");
            say(s.label);
            return null;
        }

        public Void visit(Stm.JUMP s) {
            indent(d);
            sayln("JUMP(");
            print(s.exp, d + 1);
            say(")");
            return null;
        }

        public Void visit(Stm.CJUMP s) {
            indent(d);
            say(s.op.toString());
            sayln("(");
            print(s.left, d + 1);
            sayln(",");
            print(s.right, d + 1);
            sayln(",");
            indent(d + 1);
            say(s.iftrue);
            say(", ");
            say(s.iffalse);
            say(")");
            return null;
        }

        public Void visit(Stm.MOVE s) {
            indent(d);
            sayln("MOVE(");
            print(s.dst, d + 1);
            sayln(",");
            print(s.src, d + 1);
            say(")");
            return null;
        }

        public Void visit(Stm.EXP s) {
            indent(d);
            sayln("EXP(");
            print(s.exp, d + 1);
            say(")");
            return null;
        }

        public Void visit(Exp.BINOP e) {
            indent(d);
            say(e.op.toString());
            sayln("(");
            print(e.left, d + 1);
            sayln(",");
            print(e.right, d + 1);
            say(")");
            return null;
        }

        public Void visit(Exp.MEM e) {
            indent(d);
            sayln("MEM(");
            print(e.exp, d + 1);
            say(", ");
            say(e.offset.value);
            say(")");
            return null;
        }

        public Void visit(Exp.TEMP e) {
            indent(d);
            say("TEMP ");
            say(e.temp);
            return null;
        }

        public Void visit(Exp.ESEQ e) {
            indent(d);
            sayln("ESEQ(");
            print(e.stm, d + 1);
            sayln(",");
            print(e.exp, d + 1);
            say(")");
            return null;
        }

        public Void visit(Exp.NAME e) {
            indent(d);
            say("NAME ");
            say(e.label);
            return null;
        }

        public Void visit(Exp.CONST e) {
            indent(d);
            say("CONST ");
            say(String.valueOf(e.value));
            return null;
        }

        public Void visit(Exp.CALL e) {
            indent(d);
            sayln("CALL(");
            print(e.func, d + 1);
            sayln(",");
            print(e.link, d + 1);
            for (int i = 0; i < e.args.length; i++) {
                sayln(",");
                print(e.args[i], d + 1);
            }
            say(")");
            return null;
        }
    }
}
