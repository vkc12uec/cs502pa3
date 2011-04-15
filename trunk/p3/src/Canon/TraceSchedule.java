/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Canon;

import java.util.*;
import Translate.Temp.*;
import Translate.Tree.Stm;
import Translate.Tree.Stm.*;

public class TraceSchedule {
    List<Stm> stms;
    HashMap<Label, LinkedList<Stm>> map = new HashMap<Label, LinkedList<Stm>>();

    JUMP trace(LinkedList<Stm> l) {
        for (;;) {
            Stm s = l.getFirst();
            if (s instanceof LABEL) {
                stms.addAll(l.subList(0, l.size() - 1));
                s = l.getLast();
                if (s instanceof JUMP) {
                    JUMP j = (JUMP) s;
                    if (j.targets.length == 1) {
                        Label target = j.targets[0];
                        l = map.remove(target);
                        if (l != null)
                            continue;
                        if (target == blocks.done && map.isEmpty())
                            return null;
                    }
                    return j;
                }
                if (s instanceof CJUMP) {
                    CJUMP j = (CJUMP) s;
                    // Try to follow by false target
                    l = map.remove(j.iffalse);
                    if (l != null) {
                        stms.add(j);
                        continue;
                    }
                    // else try to follow by true target
                    l = map.remove(j.iftrue);
                    if (l != null) {
                        stms.add(j.not());
                        continue;
                    }

                    // else add bridging jump
                    if (j.iffalse == blocks.done && map.isEmpty()) {
                        stms.add(j);
                        return null;
                    }
                    Label f = new Label();
                    stms.add(new CJUMP(j.op, j.left, j.right, j.iftrue, f));
                    stms.add(new LABEL(f));
                    return new JUMP(j.iffalse);
                }
            }
            throw new Error("Bad basic block in TraceSchedule");
        }
    }

    BasicBlocks blocks;

    public TraceSchedule(BasicBlocks b, List<Stm> stmts) {
        blocks = b;
        for (LinkedList<Stm> l : blocks.list)
            map.put(((LABEL) l.getFirst()).label, l);
        LinkedList<Stm> last = map.remove(blocks.done);
        stms = stmts;
        JUMP j = null;
        for (LinkedList<Stm> block : blocks.list) {
            Stm s = block.getFirst();
            LABEL lab = (LABEL) s;
            LinkedList<Stm> l = map.remove(lab.label);
            if (l != null) {
                if (j != null)
                    stms.add(j);
                j = trace(l);
            }
        }
        if (j != null)
            stms.add(j);
        stms.addAll(last);
        map = null;
    }
}
