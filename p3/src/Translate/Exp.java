/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Translate;

import Translate.Temp.*;
import Translate.Tree.Exp.*;
import Translate.Tree.Stm.*;

/**
 * Translation from abstract syntax to intermediate code trees assumes that
 * every AST node maps to some corresponding IR code tree. Sometimes, the way an
 * AST node should be translated depends on the context in which that node
 * appears. Thus, since translation operates as a depth-first traversal of the
 * AST, we may need to defer how a node is translated until we return back to
 * the context in which that node appears. It has been said that all problems in
 * computer science can be solved using indirection or caching. This is a case
 * where we use indirection to defer how a node is translated by wrapping every
 * translated subtree in one of the following subclasses of 'Exp'. The idea is
 * that every wrapper comes with associated unwrap methods ('unEx', 'unCx', 'unNx')
 * that perform an appropriate translation of the wrapped code to match its
 * specific use. Thus we are able to defer translation of a node until we see
 * how it is used.
 */
public abstract class Exp {
    /**
     * Unwrap this expression, to obtain tree code that computes a value.
     * 
     * @return the tree code that computes the value
     */
    public abstract Tree.Exp unEx();

    /**
     * Unwrap this expression, to obtain tree code that should be executed only
     * for its side-effects.
     * 
     * @return the tree code to be executed for its side-effects
     */
    public abstract Tree.Stm unNx();

    /**
     * Unwrap this expression, to obtain code that causes transfer of control
     * based on the Boolean value of the expression.
     * 
     * @param t
     *                label to branch to on true
     * @param f
     *                label to branch to on false
     * @return the tree code to evaluate the expression and effect the transfer
     *         of control.
     */
    public abstract Tree.Stm unCx(Label t, Label f);

    private static Tree.Stm SEQ(Tree.Stm left, Tree.Stm right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        return new SEQ(left, right);
    }

    private static CONST CONST(int v) {
        return new CONST(v);
    }

    private static LABEL LABEL(Label l) {
        return new LABEL(l);
    }

    private static NAME NAME(Label l) {
        return new NAME(l);
    }

    private static BINOP XOR(Tree.Exp l, Tree.Exp r) {
        return new BINOP(BINOP.Operator.XOR, l, r);
    }

    private static Tree.Exp ESEQ(Tree.Stm stm, Tree.Exp exp) {
        if (stm == null)
            return exp;
        return new ESEQ(stm, exp);
    }

    private static Tree.Stm ESTM(Tree.Exp exp) {
        if (exp == null)
            return null;
        return new EXP(exp);
    }

    private static Tree.Stm MOVE(Tree.Exp dst, Tree.Exp src) {
        return new MOVE(dst, src);
    }

    private static Tree.Stm JUMP(Label l) {
        return new JUMP(l);
    }

    private static Tree.Stm JUMP(Tree.Exp e, Label[] t) {
        return new JUMP(e, t);
    }

    private static CJUMP BNE(Tree.Exp l, Tree.Exp r, Label t, Label f) {
        return new CJUMP(CJUMP.Operator.BNE, l, r, t, f);
    }

    private static Tree.Exp TEMP(Temp t) {
        return new TEMP(t);
    }

    /**
     * A wrapper for tree code expressions that produce a value.
     */
    public static class Ex extends Exp {
        /**
         * The tree code expression that computes the value.
         */
        final Tree.Exp exp;

        /**
         * Wrap and return a tree code expression.
         * 
         * @param e
         *                the wrapped tree code expression
         */
        public Ex(Tree.Exp e) {
            exp = e;
        }

        public Tree.Exp unEx() {
            return exp;
        }

        public Tree.Stm unNx() {
            if (exp instanceof CONST || exp instanceof NAME)
                // no side-effects so no code.
                return null;
            if (exp instanceof ESEQ) {
                ESEQ eseq = (ESEQ) exp;
                if (eseq.exp instanceof CONST || eseq.exp instanceof NAME)
                    // no side-effects so no code.
                    return eseq.stm;
            }
            return ESTM(exp);
        }

        public Tree.Stm unCx(Label t, Label f) {
            // if exp is a constant, emit JUMP directly
            if (exp instanceof CONST) {
                CONST c = (CONST) exp;
                // keep the targets so we don't lose property that every node
                // reaches exit
                Label[] targets =
                    (c.value == 0) ? new Label[] { f, t }
                        : new Label[] { t, f };
                return JUMP(NAME(targets[0]), targets);
            }
            return BNE(exp, CONST(0), t, f);
        }

    }

    /**
     * A wrapper for tree code statements that have side-effects.
     */
    public static class Nx extends Exp {
        /**
         * The tree code statement to be executed.
         */
        final Tree.Stm stm;

        /**
         * Wrap and return a tree code statement.
         * 
         * @param s
         *                the wrapped tree code statement
         */
        public Nx(Tree.Stm s) {
            stm = s;
        }

        public Tree.Exp unEx() {
            if (stm instanceof MOVE) {
                MOVE m = (MOVE) stm;
                if (m.dst instanceof TEMP)
                    return ESEQ(m, m.dst);
                Temp t = new Temp();
                return ESEQ(MOVE(m.dst, ESEQ(MOVE(TEMP(t), m.src), TEMP(t))),
                            TEMP(t));
            }
            return null;
        }

        public Tree.Stm unNx() {
            // just unwrap the statement
            return stm;
        }

        public Tree.Stm unCx(Label t, Label f) {
            if (stm instanceof MOVE)
                return BNE(unEx(), CONST(0), t, f);
            return null;
        }
    }

    /**
     * A wrapper for tree code that may be used both to compute a Boolean value
     * and to effect transfer of control.
     * 
     * Concrete subclasses of Cx are used to capture evaluation of different
     * kinds of conditional.
     */
    public abstract static class Cx extends Exp {
        // need to compute a Boolean value
        public Tree.Exp unEx() {
            Temp r = new Temp(); // the Boolean result
            Label t = new Label(); // a true target
            Label f = new Label(); // a false target

            // Use the respective branches of the conditional to deposit 0
            // (false) or 1 (true) in the result temporary
            return ESEQ(SEQ(SEQ(MOVE(TEMP(r), CONST(1)), unCx(t, f)),
                            SEQ(SEQ(LABEL(f), MOVE(TEMP(r), CONST(0))),
                                LABEL(t))), TEMP(r));
        }

        // need to evaluate for side-effects
        public Tree.Stm unNx() {
            Label join = new Label();

            return SEQ(unCx(join, join), LABEL(join));
        }

        // conditional code depends on the particular kind of conditional
        public abstract Tree.Stm unCx(Label t, Label f);

        /**
         * A wrapper for relational operators that may be used for both their
         * Boolean value and for control transfer.
         */
        public static class Rel extends Cx {
            final CJUMP.Operator op;
            final Tree.Exp left, right;

            public Rel(CJUMP.Operator o, Tree.Exp l, Tree.Exp r) {
                op = o;
                left = l;
                right = r;
            }
            
            public Tree.Stm unCx(Label t, Label f) {
                return new CJUMP(op, left, right, t, f);
            }
        }

        /**
         * A wrapper for short-circuiting conditional expressions (e.g., &&,
         * ||).
         */
        public static class IfThenElseExp extends Cx {
            final Exp cond, tExp, fExp;

            /**
             * Wrap the expressions and return the wrapper.
             * 
             * @param c
             *                condition expression
             * @param t
             *                then expression
             * @param f
             *                else expression
             */
            public IfThenElseExp(Exp c, Exp t, Exp f) {
                cond = c;
                tExp = t;
                fExp = f;
            }

            public Tree.Exp unEx() {
                Tree.Exp cExp = cond.unEx();
                if (cExp instanceof CONST) {
                    int c = ((CONST) cExp).value;
                    return (c != 0) ? tExp.unEx() : fExp.unEx();
                }
                Tree.Exp tExp = this.tExp.unEx();
                Tree.Exp fExp = this.fExp.unEx();
                if (tExp == null || fExp == null)
                    return null;
                if (tExp instanceof CONST && fExp instanceof CONST) {
                    int t = ((CONST) tExp).value;
                    int f = ((CONST) fExp).value;
                    if (t == 1 && f == 0)
                        return cond.unEx();
                    if (t == 0 && f == 1)
                        return XOR(cond.unEx(), CONST(1));
                    if (t == f) {
                        Label j = new Label();
                        return ESEQ(SEQ(cond.unCx(j, j), LABEL(j)), CONST(t));
                    }
                }
                Temp r = new Temp();
                Label j = new Label();
                Label t = new Label();
                Label f = new Label();
                Tree.Stm tStm =
                    SEQ(SEQ(LABEL(t), MOVE(TEMP(r), tExp)), JUMP(j));
                Tree.Stm fStm =
                    SEQ(SEQ(LABEL(f), MOVE(TEMP(r), fExp)), JUMP(j));
                Tree.Stm cStm = cond.unCx(t, f);
                return ESEQ(SEQ(SEQ(cStm, SEQ(tStm, fStm)), LABEL(j)), TEMP(r));
            }

            public Tree.Stm unNx() {
                Tree.Exp cExp = cond.unEx();
                if (cExp instanceof CONST) {
                    int cValue = ((CONST) cExp).value;
                    return (cValue != 0) ? tExp.unNx() : fExp.unNx();
                }
                Tree.Stm tStm = tExp.unNx();
                Tree.Stm fStm = fExp.unNx();
                if (tStm == null && fStm == null)
                    return cond.unNx();
                Label j = new Label();
                Label t, f;
                if (tStm != null) {
                    t = new Label();
                    tStm = SEQ(SEQ(LABEL(t), tStm), JUMP(j));
                } else
                    t = j;
                if (fStm != null) {
                    f = new Label();
                    fStm = SEQ(SEQ(LABEL(f), fStm), JUMP(j));
                } else
                    f = j;
                Tree.Stm cStm = cond.unCx(t, f);
                return SEQ(SEQ(cStm, SEQ(tStm, fStm)), LABEL(j));
            }

            /**
             * If cond is true then evaluate thenExp, otherwise if cond is false
             * evaluate elseExp. If the result is true then branch to tt,
             * otherwise branch to ff.
             */
            public Tree.Stm unCx(Label tt, Label ff) {
                Tree.Exp cExp = cond.unEx();
                if (cExp instanceof CONST) {
                    int c = ((CONST) cExp).value;
                    return c != 0 ? tExp.unCx(tt, ff) : fExp.unCx(tt, ff);
                }
                Tree.Exp tExp = this.tExp.unEx();
                Tree.Exp fExp = this.fExp.unEx();
                Tree.Stm tStm, fStm;
                Label t, f;
                if (tExp instanceof CONST) {
                    int tValue = ((CONST) tExp).value;
                    t = (tValue != 0) ? tt : ff;
                    tStm = null;
                } else {
                    t = new Label();
                    tStm = SEQ(LABEL(t), this.tExp.unCx(tt, ff));
                }
                if (fExp instanceof CONST) {
                    int fValue = ((CONST) fExp).value;
                    f = (fValue != 0) ? tt : ff;
                    fStm = null;
                } else {
                    f = new Label();
                    fStm = SEQ(LABEL(f), this.fExp.unCx(tt, ff));
                }
                return SEQ(cond.unCx(t, f), SEQ(tStm, fStm));
            }
        }
    }
}
