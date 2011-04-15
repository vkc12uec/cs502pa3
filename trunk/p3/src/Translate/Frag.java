/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Translate;

import java.io.*;

/**
 * A program fragment is either data or a procedure.
 */
public abstract class Frag {
    /**
     * A data fragment is simply an assembly-code string.
     */
    public static class Data extends Frag {
        public String data;

        public Data(String s) {
            data = s;
        }

        public String toString() {
            return data;
        }
    }

    /**
     * A procedure fragment has a code body, and a frame representing the
     * activation record for this procedure.
     */
    public static class Proc extends Frag {
        public Tree.Stm body;
        public Frame frame;

        public Proc(Tree.Stm b, Frame f) {
            body = b;
            frame = f;
        }
        
        public String toString() {
            StringWriter s = new StringWriter();
            PrintWriter w = new PrintWriter(s);
            w.println("\t.text");
            w.println(frame.name + ":");
            if (body != null) {
                new Tree.Print(w, body);
                w.println();
            }
            return s.toString();
        }
    }
}
