/* Copyright (C) 1997-2005, Antony L Hosking.
 * All rights reserved.  */
package Assem;

import Translate.Temp;
import Translate.Temp.*;

public abstract class Instr {
    public String assem;
    public Temp[] def;
    public Temp[] use;
    public Label[] jumps;

    Instr(String a, Temp[] d, Temp[] u, Label... j) {
        assem = a;
        def = d;
        use = u;
        jumps = j;
    }

    public void replaceUse(Temp olduse, Temp newuse) {
        for (int i = 0; i < use.length; i++)
            if (use[i] == olduse)
                use[i] = newuse;
    }

    public void replaceDef(Temp olddef, Temp newdef) {
        for (int i = 0; i < def.length; i++)
            if (def[i] == olddef)
                def[i] = newdef;
    };

    public String format(Temp.Map m) {
        StringBuffer s = new StringBuffer();
        int len = assem.length();
        for (int i = 0; i < len; i++)
            if (assem.charAt(i) == '`')
                switch (assem.charAt(++i)) {
                case 's': {
                    int n = Character.digit(assem.charAt(++i), 10);
                    s.append(m.get(use[n]));
                    break;
                }
                case 'd': {
                    int n = Character.digit(assem.charAt(++i), 10);
                    s.append(m.get(def[n]));
                    break;
                }
                case 'j': {
                    int n = Character.digit(assem.charAt(++i), 10);
                    s.append(jumps[n]);
                    break;
                }
                case '`':
                    s.append('`');
                    break;
                default:
                    throw new Error("bad Assem format:" + assem);
                }
            else
                s.append(assem.charAt(i));
        return s.toString();
    }

    public static class LABEL extends Instr {
        public Label label;

        public LABEL(String a, Label l) {
            super(a, new Temp[] {}, new Temp[] {}, new Label[] {});
            label = l;
        }
    }

    public static class MOVE extends Instr {
        public MOVE(String a, Temp d, Temp s) {
            super(a, new Temp[] { d }, new Temp[] { s });
        }

        public Temp dst() {
            return def[0];
        }

        public Temp src() {
            return use[0];
        }

        public String format(Temp.Map m) {
            if (m.get(src()) == m.get(dst()))
                return "#" + super.format(m);
            return super.format(m);
        }
    }

    public static class OPER extends Instr {
        public OPER(String a, Temp[] d, Temp[] s, Label... j) {
            super(a, d, s, j);
        }
    }
}
