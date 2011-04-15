package M3;

/**
 * This class records the relative level of nested procedures.
 */
class ProcBody {
    final Value value;
    ProcBody(Value value) {
        this.value = value;
    }

    ProcBody parent;
    int level;
    public ProcBody sibling;
    public ProcBody children;

    static ProcBody current;
    private static ProcBody head;
    private static ProcBody done;
    private static int depth = -1;

    /**
     * Push a procedure as a child of the current procedure.
     */
    static void Push(ProcBody t) {
        assert t.parent == null && t.sibling == null && t.children == null;
        t.level = ++depth;
        t.parent = current;
        if (current == null) {
            // depth == 0
            t.sibling = head;
            head = t;
        } else {
            t.sibling = current.children;
            current.children = t;
        }
        current = t;
    }
    /**
     * Pops the current procedure.
     */
    static void Pop() {
        current = current.parent;
        depth--;
    }
    /**
     * Schedules 't' to be written as a top-level procedure.
     */
    static void Schedule(ProcBody t) {
        t.sibling = head;
        head = t;
    }
    /**
     * Generate all the procedure bodies.
     */
    static void EmitAll(Value.Visitor decl, Value.Visitor body) {
        // generate the declarations and bodies
        while (head != null) {
            ProcBody t = head; head = null; // grab the guys that are waiting
            t = SourceOrder(t); // put 'em in source order
            EmitDecl(t, decl); // generate their declarations
            EmitBody(t, body); // generate their bodies & build "done" list
        }
    }
    private static ProcBody SourceOrder(ProcBody t) {
        // reverse the list
        ProcBody a = t, b = null;
        while (a != null) {
            ProcBody c = a.sibling;
            a.sibling = b;
            b = a;
            a = c;
        }
        t = b;
        // recursively reorder the children
        while (t != null) {
            t.children = SourceOrder(t.children);
            t = t.sibling;
        }
        return b;
    }
    private static void EmitDecl(ProcBody t, Value.Visitor emit) {
        while (t != null) {
            t.value.accept(emit);
            EmitDecl(t.children, emit);
            t = t.sibling;
        }
    }
    private static void EmitBody(ProcBody t, Value.Visitor emit) {
        while (t != null) {
            t.value.accept(emit);
            EmitBody(t.children, emit);
            // move to the next sibling, but leave this guy on the "done" list
            ProcBody a = t.sibling;
            t.sibling = done; done = t;
            t = a;
        }
    }
}
