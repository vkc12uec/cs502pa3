package M3;

import java.util.*;

/**
 * A Value represents a declaration in some scope.
 */
abstract class Value {
    interface Visitor {
        void visit(Value.Field v);
        void visit(Value.Formal v);
        void visit(Value.Method v);
        void visit(Value.Procedure v);
        void visit(Value.Variable v);
        void visit(Value.Constant v);
        void visit(Value.External v);
        void visit(Value.Module v);
        void visit(Value.Tipe v);
    }
    abstract void accept(Visitor v);

    static void Parse(Absyn.Decl d) {
        d.accept(new Absyn.Decl.Visitor<Void>() {
            public Void visit(Absyn.Decl.Field d) {
                throw new Error("unreachable");
            }
            public Void visit(Absyn.Decl.Formal d) {
                throw new Error("unreachable");
            }
            public Void visit(Absyn.Decl.Method d) {
                throw new Error("unreachable");
            }
            public Void visit(Absyn.Decl.Module d) {
                Value.Module m = new Value.Module(d);
                Value.TypeCheck(m);
                return null;
            }
            public Void visit(Absyn.Decl.Procedure d) {
                Type.Proc type = (Type.Proc)Type.Parse(d.type);
                Scope.Insert(new Value.Procedure(d, type));
                return null;
            }
            public Void visit(Absyn.Decl.Tipe d) {
                Type t = Type.Parse(d.value);
                Scope.Insert(new Value.Tipe(d, t));
                return null;
            }
            public Void visit(Absyn.Decl.Variable d) {
                Type type = Type.Parse(d.type);
                Scope.Insert(new Value.Variable(d, type));
                return null;
            }            
        });
    }

    /**
     * Get the AST location of the declaration for this value
     * @return the declaration location
     */
    abstract Absyn.Decl getDecl();

    /**
     * The scope in which this value is declared.
     */
    Scope scope;

    final String name;
    String extName;
    Value(String name) {
        this.name = name;
    }

    boolean toplevel = false; // value is declared at top level (outside any procedure)
    boolean declared = false; // declaration for this value has been emitted
    boolean inited = false;   // initialization of this value has been emitted
    boolean compiled = false; // value has been compiled
    boolean imported = Module.curModule == null ? false : Module.curModule.imported; // value is imported
    boolean exported = false; // value is exported
    boolean external = false; // value is external
    boolean up_level = false; // value is accessed from an inner procedure scope

    /**
     * isWritable, isDesignator, needsAddress
     */
    protected static enum Flags { isWritable, isDesignator, needsAddress; }
    protected final EnumSet<Value.Flags> flags = EnumSet.noneOf(Value.Flags.class);
    boolean isDesignator() {
        return flags.contains(Flags.isDesignator);
    }
    boolean isWritable() {
        return flags.contains(Flags.isWritable);
    }
    void needsAddress() {
        flags.add(Flags.needsAddress);
    }

    /**
     * Get the type of a value
     * @param v the value
     * @return the value's type or null
     */
    static Type TypeOf(Value v) {
        if (v == null)
            return Type.ERROR;
        if (v.inTypeOf) {
            IllegalRecursion(v);
            return Type.ERROR;
        }
        v.inTypeOf = true;
        Type x = v.typeOf();
        v.inTypeOf = false;
        return x;
    }
    private boolean inTypeOf = false;

    abstract Type typeOf();

    /**
     * Type-check a value
     * @param v the value to check
     */
    static void TypeCheck(Value v) {
        if (v == null)
            return;
        if (v.checked)
            return;
        if (v.checkDepth == -1) {
            v.checkDepth = Type.recursionDepth;
            v.typeCheck();
            v.checkDepth = 0;
            v.checked = true;
        } else if (v.checkDepth != Type.recursionDepth) {
            // this is a legal recursion, just return
        } else {
            IllegalRecursion(v);
        }
    }
    private int checkDepth = -1;
    boolean checked = false;

    abstract void typeCheck();

    /**
     * Report an illegal recursive declaration of a value
     * @param v the value in error
     */
    static void IllegalRecursion(Value v) {
        if (v.errored)
            return;
        Semant.error(v.getDecl(), "illegal recursive declaration", v.name);
        v.errored = true;
    }
    private boolean errored = false;

    /**
     * Get base value (i.e., strip external references)
     */
    static Value Base(Value v) {
        if (v == null)
            return null;
        return v.base();
    }

    abstract Value base();

    /**
     * Return a global name for a value.
     */
    static String GlobalName(Value t) {
        if (t == null) return null;
        return Scope.NameToPrefix(t);
    }

    /**
     * CONST
     */
    static class Constant extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final int value;
        Type type;

        Constant(String name, int value, Type type) {
            super(name);
            this.value = value;
            this.type = type;
        }

        /**
         * Test if a value is a constant.
         */
        static Constant Is(Value v) {
            v = Base(v);
            if (v instanceof Constant)
                return (Constant) v;
            return null;
        }

        @Override
        Absyn.Decl getDecl() { return null; }

        @Override
        Type typeOf() {
            return type;
        }

        @Override
        void typeCheck() {
            this.type = Type.Check(this.type);
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            return GlobalName(this) + ":" + Type.ToString(type) + "=" + value;
        }
    }

    /**
     * A field of an object or record.
     */
    static class Field extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final Absyn.Decl decl;
        Type type;
        int offset;

        Field(Absyn.Decl.Field decl, Type type) {
            super(decl.name);
            this.decl = decl;
            this.type = type;
            flags.add(Value.Flags.isWritable);
            flags.add(Value.Flags.isDesignator);
        }

        /**
         * Test if a value is a field.
         */
        static Field Is(Value v) {
            v = Base(v);
            if (v instanceof Field)
                return (Field) v;
            return null;
        }

        /**
         * Returns "true" if the two lists of fields represented by "aa" and "ab"
         * have the same length and for each pair of values "a" and "b",
         * "IsEqual(a, b, x, types)" returns "true".  Otherwise, returns "false".
         */
        static boolean IsEqual(Scope aa, Scope bb, Type.Assumption x,
                boolean types) {
            Iterator<Value> a = Scope.ToList(aa).iterator();
            Iterator<Value> b = Scope.ToList(bb).iterator();
            while (a.hasNext() && b.hasNext())
                if (!IsEqual(Is(a.next()), Is(b.next()), x, types))
                    return false;
            return !a.hasNext() && !b.hasNext();
        }

        /**
         * If "types" is "false", only the surface syntax (name & field index) are
         * checked.  Otherwise, the field types and default values are checked too.
         */
        static boolean IsEqual(Field a, Field b, Type.Assumption x,
                boolean types) {
            if (a == null || b == null || !a.name.equals(b.name))
                return false;
            if (!types)
                return true;
            return Type.IsEqual(TypeOf(a), TypeOf(b), x);
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        @Override
        Type typeOf() {
            return type;
        }

        @Override
        void typeCheck() {
            type = Type.Check(type);
            if (Type.OpenArray.Is(type) != null)
                Semant.error(decl, "fields may not be open arrays", decl.name);
            checked = true;
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            return GlobalName(this) + ":" + Type.ToString(type);
        }
    }

    /**
     * A formal parameter.
     */
    static class Formal extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        enum Mode { VAR, VALUE, READONLY };
        final Absyn.Decl.Formal decl;
        final Mode mode;
        Type type;

        Formal(Absyn.Decl.Formal decl, Type type) {
            super(decl.name);
            this.decl = decl;
            this.type = type;
            switch (decl.mode){
            case VAR:
                mode = Mode.VAR;
                flags.add(Value.Flags.isWritable);
                break;
            case READONLY:
                mode = Mode.READONLY;
                break;
            default:
                mode = Mode.VALUE;
                flags.add(Value.Flags.isWritable);
                break;
            }
        }

        Formal(Formal f) {
            super(f.name);
            this.decl = f.decl;
            this.type = f.type;
            this.mode = f.mode;
            if (f.flags.contains(Value.Flags.isWritable)) {
                flags.add(Value.Flags.isWritable);
            }
        }

        /**
         * A formal for the hidden 'this' parameter.
         */
        Formal(Type type) {
            super("_");
            this.decl = null;
            this.type = type;
            this.mode = Mode.VALUE;
            flags.add(Value.Flags.isWritable);
        }

        /**
         * Test if a value is a formal parameter.
         */
        static Formal Is(Value v) {
            v = Base(v);
            if (v instanceof Formal)
                return (Formal) v;
            return null;
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        @Override
        Type typeOf() {
            return type;
        }

        Type refType = null;

        @Override
        void typeCheck() {
            type = Type.Check(type);
            if (mode == Mode.VALUE && Type.OpenArray.Is(type) != null) {
                refType = new Type.Ref(null, type);
                refType = Type.Check(refType);
            }
        }

        /**
         * Check that the actuals and formals match in call to a procedure.
         */
        static boolean CheckArgs(
                Collection<Absyn.Expr> actuals,
                Collection<Value> formals,
                Absyn.Expr proc) {
            Iterator<Absyn.Expr> aa = actuals.iterator();
            Iterator<Value> ff = formals.iterator();
            boolean ok = true;
            while (aa.hasNext() && ff.hasNext()) {
                Absyn.Expr actual = aa.next();
                Formal formal = Value.Formal.Is(ff.next());
                if (actual != null && formal != null) {
                    // we've got both a formal and an actual
                    Type ft = formal.type;
                    switch (formal.mode) {
                    case VALUE:
                        {
                            Type at = Expr.TypeCheck(actual);
                            if (!Type.IsAssignable(ft, at)) {
                                Semant.error(actual, "incompatible types");
                                ok = false;
                            }
                            break;
                        }
                    case VAR:
                        {
                            Type at = Expr.TypeCheck(actual);
                            if (!Expr.IsDesignator(actual)) {
                                Semant.error(actual, "VAR actual must be a designator");
                                ok = false;
                            } else if (!Expr.IsWritable(actual)) {
                                Semant.error(actual, "VAR actual must be writable");
                                ok = false;
                            } else if (Type.IsEqual(ft, at, null)) {
                                Expr.NeedsAddress(actual);
                            } else if (Type.OpenArray.Is(ft) != null
                                    && Type.IsAssignable(ft, at)) {
                                Expr.NeedsAddress(actual);
                            } else {
                                Semant.error(actual, "incompatible types");
                                ok = false;
                            }
                            break;
                        }
                    case READONLY:
                        {
                            Type at = Expr.TypeCheck(actual);
                            if (!Type.IsAssignable(ft, at)) {
                                Semant.error(actual, "incompatible types");
                                ok = false;
                            } else if (!Expr.IsDesignator(actual)) {
                                // we'll make a copy when it's generated
                            } else if (Type.IsEqual(ft, at, null)) {
                                Expr.NeedsAddress(actual);
                            } else {
                                // we'll make a copy when it's generated
                            }
                            break;
                        }
                    }
                }
            }
            assert !ff.hasNext();
            while (aa.hasNext()) {
                Expr.TypeCheck(aa.next());
            }
            return ok;
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            return mode + " " + name + ":" + Type.ToString(type);
        }
    }

    /**
     * A method.
     */
    static class Method extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final Type.Object parent;
        final Absyn.Decl.Method decl;
        Type.Proc signature;
        Value value = null;
        int offset;
        final boolean override;

        Method(Type.Object parent, Absyn.Decl.Method decl, Type.Proc type) {
            super(decl.name);
            this.decl = decl;
            this.parent = parent;
            this.signature = type;
            override = type == null;
        }

        /**
         * Test if a value is a method.
         */
        static Method Is(Value v) {
            v = Base(v);
            if (v instanceof Method)
                return (Method) v;
            return null;
        }

        /**
         * Returns "true" if the two lists of methods represented by "aa" and "ab"
         * have the same length and for each pair of values "a" and "b",
         * "IsEqual(a, b, x, types)" returns "true".  Otherwise, returns "false".
         */
        static boolean IsEqual(Scope aa, Scope bb, Type.Assumption x,
                boolean types) {
            Iterator<Value> a = Scope.ToList(aa).iterator();
            Iterator<Value> b = Scope.ToList(bb).iterator();
            while (a.hasNext() && b.hasNext())
                if (!IsEqual(Is(a.next()), Is(b.next()), x, types))
                    return false;
            return !a.hasNext() && !b.hasNext();
        }

        /**
         * If "types" is "false", only the surface syntax (name & method index) are
         * checked.  Otherwise, the method types and default values are checked too.
         */
        static boolean IsEqual(Method a, Method b, Type.Assumption x,
                boolean types) {
            if (a == null || b == null || !a.name.equals(b.name)
                    || a.override != b.override)
                return false;
            if (!types)
                return true;
            // now we'll do the harder type-based checks
            a.resolveDefault();
            b.resolveDefault();
            return Type.IsEqual(a.signature, b.signature, x)
                    && Base(a.value) == Base(b.value);
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        @Override
        Type typeOf() {
            return signature;
        }

        @Override
        void typeCheck() {
            if (signature != null)
                signature = (Type.Proc) Type.Check(signature);
            if (decl.expr != null) {
                Expr.TypeCheck(decl.expr);
                resolveDefault();
            }
            if (value != null) {
                TypeCheck(value);
                Type procType = TypeOf(value);
                if (procType == Type.NULL)
                    value = null;
                else if (!(value instanceof Procedure))
                    Semant.error(decl, "default is not a procedure", decl.name);
                else if (!value.toplevel)
                    Semant.error(decl, "default is a nested procedure", decl.name);
                else if (!Type.Proc.IsCompatible(procType, parent, signature))
                    Semant.error(decl, "default is incompatible with method type", decl.name);
                else
                    return;
                value = null;
            }
        }

        private Scope resolveScope = Scope.Top();
        private void resolveDefault() {
            if (value != null)
                return;
            Absyn.Expr.Named n = NamedExpr.Is(decl.expr);
            if (n != null)
                value = Scope.LookUp(resolveScope, n.name, false);
            if (value == null)
                Semant.error(decl, "default is not a procedure constant", decl.name);
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            return GlobalName(this) + Type.ToString(signature) + "=" + value;
        }
    }

    /**
     * INTERFACE or MODULE
     */
    static class Module extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final Absyn.Decl.Module decl;
        final boolean isInterface;
        final External.Set externals = new External.Set();
        LinkedList<Type> types = new LinkedList<Type>();

        Module(Absyn.Decl.Module decl) {
            super(decl.name);
            this.decl = decl;
            this.isInterface = decl.stmts == null;
        }

        /**
         * Test if a value is a module.
         */
        static Module Is(Value v) {
            v = Base(v);
            if (v instanceof Module)
                return (Module) v;
            return null;
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        Scope imports, locals;

        @Override
        Type typeOf() { return null; }

        private static Module curModule;
        static Module Switch(Module module) {
            Module old = curModule;
            curModule = module;
            return old;
        }
        static Module Current() { return curModule; }

        ProcBody body;
        @Override
        void typeCheck() {
            Module save = Switch(this);

            if (decl.exports != null) {
                for (Absyn.Decl ex : decl.exports) {
                    External.NoteExport(ex, externals);
                }
            } else if (!isInterface)
                External.NoteExport(decl, externals);
            External.ParseImports(externals, this);

            imports = Scope.PushNewModule("_imports");
            {
                External.LoadImports(externals, this);
                Scope.TypeCheck(imports);
                locals = Scope.PushNewModule(name);
                {
                    for (Absyn.Decl d : decl.decls) Parse(d);
                    for (Value v : Scope.ToList(locals))
                        v.toplevel = true;
                    Scope.TypeCheck(locals);
                    if (toplevel) {
                        body = new ProcBody(this);
                        ProcBody.Push(body);
                    }
                    if (!isInterface)
                        Stmt.TypeCheck(decl.stmts);
                    if (toplevel) {
                        ProcBody.Pop();
                    }
                }
                Scope.PopNew();
            }
            Scope.PopNew();
            checkDuplicates();

            Switch(save);
        }

        private void checkDuplicates() {
            Map<String, Value> marks = new HashMap<String, Value>();
            // mark all the imports
            for (Value v : Scope.ToList(imports)) {
                marks.put(v.name, v);
            }
            // check for anything already marked in the local scope
            for (Value v1 : Scope.ToList(locals)) {
                if (marks.get(v1.name) != null) {
                    Value v2 = Scope.LookUp(imports, v1.name, true);
                    if (v2 != null) {
                        // possible duplicate
                        Procedure p1 = Procedure.Is(v1);
                        Procedure p2 = Procedure.Is(v2);
                        External x = External.IsExportable(v2);
                        if (x == null || p1 == null || p2 == null)
                            Semant.error(v2.getDecl(), "symbol redefined", v2.name);
                        else {
                            Procedure.NoteExport(p1, p2);
                            External.Redirect(x, v1);
                        }
                    }
                }
            }
        }

        static void NoteVisibility(Module m) {
            for (Value v : Scope.ToList(m.locals)) {
                if (Variable.Is(v) != null || Tipe.Is(v) != null
                        || Procedure.Is(v) != null) {
                    assert !v.imported;
                    v.exported = true;
                }
            }
        }

        @Override
        Value base() {
            return this;
        }

        static boolean IsInterface() {
            return curModule != null && curModule.isInterface;
        }

        /**
         * Find and return the named interface module.
         */
        static Module LookUp(Absyn.Decl decl, String name) {
            try {
                if (!PushInterface(name))
                    return null;
                // open the external file and parse the interface
                java.io.File parent = Semant.file.getAbsoluteFile().getParentFile();
                java.io.InputStream stream = new java.io.FileInputStream(new java.io.File(parent, name + ".i3"));
                Parser parser = new Parser(stream);
                Absyn.Decl.Module module = parser.Unit();
                if (!name.equals(module.name)) {
                    Semant.error(decl, "imported interface has wrong name", decl.name);
                    return null;
                }
                if (module.stmts != null) {
                    Semant.error(decl, "imported unit is not an interface", decl.name);
                    return null;
                }
                Module m = new Module(module);
                m.imported = true;
                TypeCheck(m);
                return m;
            } catch (ParseException e) {
                Semant.error(e.getMessage());
                return null;
            } catch (TokenMgrError e) {
                Semant.error(e.getMessage());
                return null;
            } catch (java.io.FileNotFoundException e) {
                Semant.warning(decl, "unable to find interface (" + name + ")");
                return null;
            } finally {
                PopInterface();
            }
        }

        private static Stack<String> importStack = new Stack<String>();

        static boolean PushInterface(String name) {
            // check for a cycle in the active imports
            int i = importStack.indexOf(name);
            importStack.push(name);
            if (i < 0) return true;
            String msg = name;
            for (String s: importStack.subList(i+1, importStack.size()))
                msg += " -> " + s;
            Semant.error("circular imports (" + msg + ")");
            return false;
        }

        static void PopInterface() {
            importStack.pop();
        }
    }

    static class Procedure extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final Absyn.Decl.Procedure decl;
        ProcBody body;
        Procedure intf_peer, impl_peer;
        Type.Proc signature;

        Procedure(Absyn.Decl.Procedure decl, Type.Proc type) {
            super(decl.name);
            this.decl = decl;
            this.signature = type;
            external = decl.external != null;
            if (external) {
                extName = decl.external.image;
                if (extName.equals("")) extName = name;
            }
        }

        Procedure(String name, Type.Proc type) {
            super(name);
            this.decl = null;
            this.signature = type;
        }

        static Procedure Is(Value v) {
            v = Base(v);
            if (v instanceof Procedure)
                return (Procedure) v;
            return null;
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        @Override
        Type typeOf() {
            return signature;
        }

        @Override
        void typeCheck() {
            Type.Check(signature);
            /* NOTE: don't save the signature returned by Type.Check cause if
             * you do, the formals will be reused by procedures with the
             * same signature. */

            Value.TypeCheck(intf_peer);
            // defer the rest to checkBody
        }

        Scope syms;

        void checkBody() {
            if (decl == null || decl.decls == null && decl.stmts == null)
                return;
            body = new ProcBody(this);
            ProcBody.Push(body);
            syms = Scope.PushNewProc(name);
            for (Value o : Scope.ToList(signature.formals))
                Scope.Insert(new Variable(Value.Formal.Is(o)));
            checked = true;
            Type.recursionDepth++;
            {
                for (Absyn.Decl d : decl.decls) Parse(d);
                Scope.TypeCheck(syms);
                Type result = Stmt.TypeCheck(decl.stmts);
                if (signature.result != null)
                    if (result == null)
                        Semant.warning(decl, "function may not return a value");
            }
            Type.recursionDepth--;
            Scope.PopNew();
            ProcBody.Pop();
        }

        static void NoteExport(Procedure impl, Procedure intf) {
            if (impl.intf_peer != null || intf.impl_peer != null)
                Semant.error(impl.decl, "procedure redefined", impl.name);
            else {
                if (!Type.IsAssignable(intf.signature, impl.signature))
                    Semant.error(impl.decl, "procedure redefined", impl.name);
                intf.impl_peer = impl;
                impl.intf_peer = intf;
                impl.scope = intf.scope; // retain the exported module name
            }
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            return GlobalName(this) + Type.ToString(signature);
        }
    }

    static class Tipe extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final Absyn.Decl.Tipe decl;
        Type value;

        Tipe(Absyn.Decl.Tipe decl, Type value) {
            super(decl.name);
            this.decl = decl;
            this.value = value;
            value.tipe = this;
        }

        Tipe(String name, Type value) {
            super(name);
            this.decl = null;
            this.value = value;
            value.tipe = this;
        }

        static Tipe Is(Value v) {
            v = Base(v);
            if (v instanceof Tipe)
                return (Tipe) v;
            return null;
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        @Override
        void typeCheck() {
            value = Type.Check(value);
        }

        @Override
        Type typeOf() {
            return null;
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            if (value.ast == null)
                return name;
            return GlobalName(this) + "=" + value.toString();
        }
    }

    static class Variable extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        final Absyn.Decl.Variable decl;
        final Absyn.Expr expr;
        final boolean indirect;
        final Formal formal;
        final ProcBody proc;
        Type type;

        Variable(Absyn.Decl.Variable decl, Type type) {
            super(decl.name);
            this.decl = decl;
            this.type = type;
            this.expr = decl.expr;
            this.indirect = false;
            this.formal = null;
            this.proc = ProcBody.current;
            external = decl.external != null;
            if (external) {
                extName = decl.external.image;
                if (extName.equals("")) extName = name;
            }
            flags.add(Value.Flags.isWritable);
            flags.add(Value.Flags.isDesignator);
        }

        Variable(Formal formal) {
            super(formal.name);
            this.indirect = formal.mode != Formal.Mode.VALUE
                    || Type.OpenArray.Is(formal.type) != null;
            this.decl = null;
            this.formal = formal;
            this.type = formal.type;
            this.expr = null;
            this.imported = false;
            this.initDone = true;
            this.proc = ProcBody.current;
            flags.add(Value.Flags.isDesignator);
            if (formal.mode != Formal.Mode.READONLY)
                flags.add(Value.Flags.isWritable);
        }

        Variable(Absyn.Stmt.For stmt, Type type) {
            super(stmt.var.name);
            this.decl = stmt.var;
            this.type = type;
            this.expr = null;
            this.indirect = false;
            this.formal = null;
            this.initDone = true;
            this.proc = ProcBody.current;
            flags.add(Value.Flags.isDesignator);
        }

        Variable(String name, Type type) {
            super(name);
            this.decl = null;
            this.type = type;
            this.expr = null;
            this.indirect = false;
            this.formal = null;
            this.initDone = true;
            this.proc = ProcBody.current;
            flags.add(Value.Flags.isDesignator);
        }

        boolean initDone = false;
        boolean initPending = false;
        boolean initStatic = false;

        static Variable Is(Value v) {
            v = Base(v);
            if (v instanceof Variable)
                return (Variable) v;
            return null;
        }

        @Override
        Absyn.Decl getDecl() { return decl != null ? decl : formal.decl; }

        @Override
        Type typeOf() {
            if (type == null) {
                if (expr != null)
                    type = Expr.TypeCheck(expr);
                else if (formal != null)
                    type = TypeOf(formal);
                if (type == null) {
                    Semant.error(decl, "variable has no type", decl.name);
                    type = Type.ERROR;
                }
            }
            return type;
        }

        @Override
        void typeCheck() {
            type = Type.Check(typeOf());
            if (formal == null && Type.OpenArray.Is(type) != null)
                Semant.error(decl, "variable cannot be an open array", decl.name);
            if (type != Type.ERROR && Type.IsEqual(type, Type.NULL, null))
                Semant.warning(decl, "variable has type NULL");
            checked = true; // allow recursions through the init expr
            if (formal != null && formal.refType != null
                    && Type.OpenArray.Is(type) != null)
                Semant.warning(formal.decl, "open array passed by value");
            TypeCheck(formal);
            if (decl != null && decl.external != null) {
                if (expr != null) {
                    Semant.error(decl, "<*EXTERNAL*> variables cannot be initialized", decl.name);
                    Expr.TypeCheck(expr);
                    AssignStmt.Check(type, expr);
                }
            } else if (expr != null) {
                Expr.TypeCheck(expr);
                AssignStmt.Check(type, expr);
                if (Module.IsInterface()) {
                    Semant.error(decl, "initial value is not a constant", decl.name);
                }
            } else if (toplevel) {
                // no explicit initialization is given but the var is global
                initDone = true;
            }
        }

        @Override
        Value base() {
            return this;
        }

        @Override
        public String toString() {
            return GlobalName(this) + ":" + Type.ToString(type);
        }
    }

    static class External extends Value {
        @Override
        void accept(Visitor v) { v.visit(this); }

        static class Set {
            final Map<String, Port> exports = new LinkedHashMap<String, Port>();
            final Map<String, Port> imports = new LinkedHashMap<String, Port>();
            final List<External> importObjs = new LinkedList<External>();
        }

        static class Port {
            final Absyn.Decl decl;
            Module module;
            final String name;
            External source = null;
            boolean direct = false;
            boolean export = false;

            Port(Absyn.Decl decl, Module m, String name) {
                this.decl = decl;
                this.module = m;
                this.name = name;
            }
        }

        final Absyn.Decl decl;
        Value value;
        final Port home;
        boolean exportable;

        External(Absyn.Decl decl, Value value, String name, Port port) {
            super(name);
            this.decl = decl;
            this.value = value;
            this.home = port;
        }

        static External IsExportable(Value v) {
            if (v != null && v instanceof External) {
                External t = (External) v;
                if (t.home.export)
                    return t;
            }
            return null;
        }

        static void Redirect(External intf, Value impl) {
            intf.value = impl;
        }

        @Override
        Type typeOf() {
            return TypeOf(value);
        }

        @Override
        void typeCheck() {
            TypeCheck(value);
        }

        @Override
        Absyn.Decl getDecl() { return decl; }

        @Override
        Value base() {
            return Base(value);
        }

        static void NoteExport(Absyn.Decl decl, Set s) {
            String id = decl.name;
            Module ex = Module.LookUp(decl, id);
            if (ex == null)
                return;
            Port p = Push(decl, s.exports, ex, id);
            p.direct = true;
            p.export = true;
        }

        static void NoteImport(Absyn.Decl decl, Set s, Module im, String name) {
            if (im == null)
                return;
            Port p = Push(decl, s.imports, im, name);
            p.source = ImportObj(decl, s, im, name, p);
            p.direct = true;
        }

        static Port Push(Absyn.Decl decl, Map<String,Port> ports, Module m, String name) {
            Port p = ports.get(name);
            if (p != null) {
                if (m == null || p.module == m) {
                    // ok
                } else if (p.module == null) {
                    p.module = m;
                } else {
                    Semant.error(decl, "inconsistent imports", decl.name);
                }
                return p;
            }
            p = new Port(decl, m, name);
            ports.put(name, p);
            return p;
        }

        static External ImportObj(Absyn.Decl decl, Set s, Value v, String name,
                Port port) {
            if (s == null)
                return null;
            External t = new External(decl, v, name, port);
            t.imported = true;
            t.exported = false;
            if (port.export)
                t.exportable = true;
            s.importObjs.add(t);
            return t;
        }

        static void Redirect(External intf, External impl) {
            intf.value = impl;
        }

        static void ParseImports(Set s, Module self) {
            // parse the explicit imports
            for (Absyn.Decl d : self.decl.imports)
                ParseImport(s, d);
            for (Absyn.Decl d : self.decl.fromImports)
                ParseFromImport(s, d);
            ResolveImports(s, self);
        }

        static void ParseImport(Set s, Absyn.Decl d) {
            String id = d.name;
            Module im = Module.LookUp(d, id);
            NoteImport(d, s, im, id);
        }

        static void ParseFromImport(Set s, Absyn.Decl d) {
            // TODO
        }

        static void ResolveImports(Set s, Module self) {
            // import the exported symbols
            for (Port p : s.exports.values()) {
                Module m = p.module;
                if (m != null && m != self) {
                    for (Value v : Scope.ToList(m.locals))
                        ImportObj(p.decl, s, v, v.name, p);
                }
            }
            // resolve the deferred "FROM x IMPORT" modules
            for (Port p : s.imports.values()) {
                if (p.module == null) {
                    p.module = Module.LookUp(p.decl, p.name);
                }
            }
            // resolve the deferred "FROM x IMPORT y" imports
            for (External t : s.importObjs) {
                if (t.value == null) {
                    // this is an item from a "FROM x IMPORT" => look up
                    // deferred
                    Port p = t.home;
                    Value v = Scope.LookUp(p.module.locals, t.name, true);
                    if (v != null)
                        t.value = v;
                    else
                        Semant.error(t.decl, "symbol not exported", t.name);
                }
            }
        }

        static void LoadImports(Set s, Module self) {
            // load the imported symbols
            for (External t : s.importObjs) {
                if (t.value != null)
                    Scope.Insert(t.value);
            }
        }
    }
}
