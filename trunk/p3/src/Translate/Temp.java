/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Translate;

import java.util.HashMap;

public class Temp {
    private static int count;
    private final String name;

    public String toString() {
        return name;
    }

    /**
     * Get a label.
     * @param name      name of the label
     * @return          the label, or null if name is null
     */
    public static Label getLabel(String name) {
        if (name == null) return null;
        Label l = labels.get(name);
        if (l == null) {
            l = new Label(name);
            labels.put(name, l);
        }
        return l;
    }
    private static HashMap<String, Label> labels = new HashMap<String, Label>();

    public static Label getLabel() {
        return new Label();
    }

    public Temp(String n) {
        name = n;
    }

    public Temp() {
        this("t." + count++);
    }

    public boolean spillable = true;
    
    public static interface Map {
        public Temp get(Temp t);
        
        public static class Default implements Map {
            public Temp get(Temp t) {
                return t;
            }
        }
    }

    public static class Label {
        private final String string;
        private static int count;

        /**
         * a printable representation of the label, for use in assembly language
         * output.
         */
        public String toString() {
            return string;
        }

        private Label(String name) {
            string = name;
        }

        /**
         * Makes a new label with an arbitrary name.
         */
        public Label() {
            this("L." + count++);
        }
    }

}
