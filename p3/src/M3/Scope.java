package M3;

import java.util.*;

/**
 * The block-structured symbol table mapping symbols to values.
 */
class Scope {
    private final Scope parent;
    private final String name;
    List<Scope> children = new LinkedList<Scope>();
    private final Map<String, Value> values = new LinkedHashMap<String, Value>();

    static Collection<Value> ToList(Scope t) {
        return t.values.values();
    }

    /**
     * lookups can see parent
     */
    private final boolean open;
    /**
     * is a procedure frame
     */
    private final boolean proc;
    private final Value.Module home;

    static final List<Scope> allScopes = new LinkedList<Scope>();
    private static Scope bottom = new Scope(null, true, false);
    static Scope top = bottom;

    /**
     * Create a new scope.
     * @param name the name of the scope
     * @param open scope (like a block) or closed (like a record)?
     * @param proc procedure scope?
     */
    private Scope(String name, boolean open, boolean proc) {
        allScopes.add(this);
        parent = top;
        if (parent != null) {
            parent.children.add(this);
            if (name == null)
                name = Integer.toString(parent.children.size());
        }
        this.name = name;
        this.open = open;
        this.proc = proc;
        this.home = Value.Module.Current();
    }

    static Scope PushNewModule(String name) {
        return top = new Scope(name, true, false);
    }

    static Scope PushNewProc(String name) {
        return top = new Scope(name, true, true);
    }

    static Scope PushNewOpen() {
        return top = new Scope(null, true, false);
    }

    static void PopNew() {
        top = top.parent;
    }

    static Scope NewClosed() {
        return new Scope(null, false, false);
    }

    /**
     * Reset the top of the scope stack.
     * @param t the new top
     * @return the scope to reset to (for later Pop)
     */
    static Scope Push(Scope t) {
        Scope old = top;
        assert t != null;
        top = t;
        return old;
    }

    /**
     * Reset the top of the scope stack.
     * @param the scope to reset to (from previous Push)
     */
    static void Pop(Scope old) {
        assert old != null;
        top = old;
    }

    /**
     * @return the top "open" scope
     */
    static Scope Top() {
        Scope t = top;
        while (t != null && !t.open)
            t = t.parent;
        return t;
    }

    /**
     * Test if a scope is the outermost (module/interface level) scope
     * @param t the scope to test
     * @return true if outermost, false if not
     */
    static boolean OuterMost(Scope t) {
        return t != null && t.open && !t.proc;
    }

    /**
     * Look up the value denoted by a symbol in a given scope.
     * Notes when a value is referenced from an inner scope.
     * @param t the scope in which to look for the symbol
     * @param name the symbol to look up
     * @param strict look up in outer scopes?
     * @return the valuer denoted by the symbol
     */
    static Value LookUp(Scope t, String name, boolean strict) {
        Value o;
        boolean up_level = false;
        for (;;) {
            if (t == null)
                return null;
            o = t.values.get(name);
            if (o != null)
                break;
            if (strict || !t.open)
                return null;
            up_level = up_level || t.proc;
            t = t.parent;
        }
        o.up_level = o.up_level || up_level;
        return o;
    }

    /**
     * Insert a value into the current (top) scope,
     * but only if it has not been inserted into any other scope.
     * @param o the value to insert 
     */
    static void Insert(Value o) {
        Scope t = top;
        // check for a reserved word
        if (t != bottom && LookUp(bottom, o.name, true) != null)
            Semant.error(o.getDecl(), "reserved identifier redefined", o.name);
        if (t.values.get(o.name) != null)
            Semant.error(o.getDecl(), "symbol redefined", o.name);
        else
            t.values.put(o.name, o);
        if (o.scope == null) o.scope = top;
    }

    /**
     * Type-check the values in a scope, including procedure bodies.
     * @param t the scope to check
     */
    static void TypeCheck(Scope t) {
        for (Value v : Scope.ToList(t))
            Value.TypeCheck(v);
        for (Value v : Scope.ToList(t)) {
            Value.Procedure p = Value.Procedure.Is(v);
            if (p != null)
                p.checkBody();
        }
    }

    /**
     * Create a fully qualified name prefix for a value.
     * @param v the value to create the prefix for
     * @return the prefix
     */
    static String NameToPrefix(Value v) {
        v = Value.Base(v);
        if (v.external) {
            // simple external name: foo
            return v.extName;
        } else if (v.exported || v.imported || v.scope == null || OuterMost(v.scope)) {
            // global names: foo, module.foo
            if (v.scope == null || v.scope.name == null) {
                return v.name;
            } else {
                return v.scope.name + "." + v.name;
            }
        } else if (Value.Procedure.Is(v) != null) {
            // procedure => fully qualified name: module.p1.p2.p
            String result = v.name;
            for (Scope t = v.scope; t != null; t = t.parent) {
                if (t.name != null) {
                    result = t.name + "." + result;
                }
                if (!t.open || !t.proc)
                    break;
            }
            return result;
        } else {
            // variable => simple name: foo
            String result = v.name;
            for (Scope t = v.scope; t != null; t = t.parent) {
                if (t.name != null) {
                    result = t.name + "." + result;
                }
                if (!t.open || !t.proc)
                    break;
            }
            return result;
        }
    }

    /**
     * Get the compilation unit (module/interface) in which a value is declared.
     * @param v the value
     * @return the compilation unit
     */
    static Value.Module ToUnit(Value v) {
        v = Value.Base(v);
        return v.scope.home;
    }

    public String toString() {
        if (parent == null) return name;
        if (!proc) return name;
        if (parent.name == null) return name;
        return parent.toString() + "." + name;
    }

    static void Print(Scope scope) {
        if (!scope.open) return;
        System.out.println("BEGIN " + scope);
        for (Value v: Scope.ToList(scope)) {
            System.out.println(v);
        }
        for (Scope s: scope.children) {
            Print(s);
        }
        System.out.println("END " + scope);
    }
}
