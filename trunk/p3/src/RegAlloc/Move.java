/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package RegAlloc;

public class Move {
    public Node src, dst;

    public Move(Node s, Node d) {
        src = s;
        dst = d;
    }
}
