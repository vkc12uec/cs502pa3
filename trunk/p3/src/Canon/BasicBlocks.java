/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Canon;

import java.util.*;
import Translate.Temp.*;
import Translate.Tree.Stm;
import Translate.Tree.Stm.*;

public class BasicBlocks {
    public Label done;
    public LinkedList<LinkedList<Stm>> list = new LinkedList<LinkedList<Stm>>();

    private LinkedList<Stm> newBlock() {
        LinkedList<Stm> block = new LinkedList<Stm>();
        list.add(block);
        return block;
    }

    public BasicBlocks(LinkedList<Stm> stms) {
        LinkedList<Stm> block = null;
        for (Stm s : stms) {
            if (s instanceof LABEL) {
                if (block != null)
                    block.add(new JUMP(((LABEL) s).label));
                block = newBlock();
                block.add(s);
            } else {
                if (block == null) {
                    block = newBlock();
                    block.add(new LABEL(new Label()));
                }
                block.add(s);
                if (s instanceof JUMP || s instanceof CJUMP)
                    block = null;
            }
        }
        if (block == null) {
            block = newBlock();
            done = new Label();
            block.add(new LABEL(done));
        } else {
            Stm s = block.getFirst();
            done = ((LABEL) s).label;
        }
    }
}
