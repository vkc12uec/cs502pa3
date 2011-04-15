/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Canon;

import java.util.*;
import Translate.Temp;
import Translate.Tree;
import Translate.Tree.Exp;
import Translate.Tree.Exp.*;
import Translate.Tree.Stm;
import Translate.Tree.Stm.*;

public class Canon implements Tree.Visitor<Exp> {

    public Canon(Stm s) {
        s.accept(this);
    }

    final LinkedList<Stm> stms = new LinkedList<Stm>();
    public LinkedList<Stm> stms() { return stms; }

    static boolean commute(LinkedList<Stm> a, Tree.Exp b) {
        return a.isEmpty() || b instanceof NAME || b instanceof CONST;
    }

    public Exp visit(SEQ s) {
        s.left.accept(this);
        s.right.accept(this);
        return null;
    }

    public Exp visit(MOVE s) {
        if (s.dst instanceof TEMP && s.src instanceof CALL) {
            Exp[] kids = s.src.kids();
            reorder(Arrays.asList(kids), stms);
            stms.add(new MOVE(s.dst, s.src.build(kids)));
        } else if (s.dst instanceof ESEQ) {
            ((ESEQ)s.dst).stm.accept(this);
            new MOVE(((ESEQ)s.dst).exp, s.src).accept(this);
        } else {
            reorder(s);
        }
        return null;
    }

    public Exp visit(EXP s) {
        if (s.exp instanceof CALL) {
            Exp[] kids = s.exp.kids();
            reorder(Arrays.asList(kids), stms);
            stms.add(new EXP(s.exp.build(kids)));
        } else {
            reorder(s);
        }
        return null;
    }

    public Exp visit(LABEL s) {
        reorder(s);
        return null;
    }

    public Exp visit(JUMP s) {
        reorder(s);
        return null;
    }

    public Exp visit(CJUMP s) {
        reorder(s);
        return null;
    }

    private void reorder(Stm s) {
        Exp[] kids = s.kids();
        reorder(Arrays.asList(kids), stms);
        stms.add(s.build(kids));
    }

    public Exp visit(ESEQ e) {
        e.stm.accept(this);
        return e.exp.accept(this);
    }

    public Exp visit(MEM e) {
        return reorder(e);
    }

    public Exp visit(TEMP e) {
        return reorder(e);
    }

    public Exp visit(NAME e) {
        return reorder(e);
    }

    public Exp visit(CONST e) {
        return reorder(e);
    }

    public Exp visit(CALL e) {
        return reorder(e);
    }

    public Exp visit(BINOP e) {
        return reorder(e);
    }

    private Exp reorder(Exp e) {
        Exp[] kids = e.kids();
        reorder(Arrays.asList(kids), stms);
        return e.build(kids);
    }

    private void reorder(List<Exp> exps, LinkedList<Stm> l) {
        if (exps.isEmpty())
            return;
        else {
            Tree.Exp a = exps.get(0);
            if (a instanceof CALL) {
                Temp t = new Temp();
                Exp e = new ESEQ(new MOVE(new TEMP(t), a), new TEMP(t));
                exps.set(0, e);
                reorder(exps, stms);
            } else {
                Exp aa = a.accept(this);
                LinkedList<Stm> bb = new LinkedList<Stm>();
                reorder(exps.subList(1, exps.size()), bb);
                if (commute(bb, aa)) {
                    stms.addAll(bb);
                    exps.set(0, aa);
                } else {
                    Temp t = new Temp();
                    l.add(new MOVE(new TEMP(t), aa));
                    l.addAll(bb);
                    exps.set(0, new TEMP(t));
                }
            }
        }
    }
}
