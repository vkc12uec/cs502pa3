package M3;

import java.util.Iterator;

/**
 * These classes represent the constructed types built for source-level type expressions.
 */
abstract class Type {
    interface Visitor<R> {
        R visit(Type t);
        R visit(Enum t);
        R visit(Named t);
        R visit(Object t);
        R visit(OpenArray t);
        R visit(Proc t);
        R visit(Ref t);
    }
    abstract <R> R accept(Visitor<R> v);

    static Type Parse(Absyn.Type t) {
        if (t == null) return null;
        if (t.type != null) return t.type;
        return t.type = t.accept(new Absyn.Type.Visitor<Type>() {
            @Override
            public Type visit(Absyn.Type.Array t) {
                return new Type.OpenArray(t, Parse(t.element));
            }

            @Override
            public Type visit(Absyn.Type.Named t) {
                return new Type.Named(t);
            }

            @Override
            public Type visit(Absyn.Type.Object t) {
                Type parent = Parse(t.parent);
                if (parent == null) parent = Type.ROOT;
                Type.Object type = new Type.Object(t, parent);
                {
                    Scope zz = Scope.Push(type.fields);
                    for (Absyn.Decl.Field f : t.fields)
                        Scope.Insert(new Value.Field(f, Parse(f.type)));
                    Scope.Pop(zz);
                }
                {
                    Scope zz = Scope.Push(type.methods);
                    for (Absyn.Decl.Method m : t.methods)
                        Scope.Insert(new Value.Method(type, m, (Type.Proc) Parse(m.type)));
                    for (Absyn.Decl.Method m : t.overrides)
                        Scope.Insert(new Value.Method(type, m, null));
                    Scope.Pop(zz);
                }
                return type;
            }

            @Override
            public Type visit(Absyn.Type.Proc t) {
                Type result = Parse(t.result);
                int numArgs = t.formals.size();
                Type.Proc type = new Type.Proc(t, numArgs, numArgs, result) {
                    @Override
                    Type check(Absyn.Expr.Call call) {
                        Value.Formal.CheckArgs(call.actuals, Scope.ToList(formals), call.proc);
                        if (result != null)
                            result = Type.Check(result);
                        return result;
                    }
                };
                Scope zz = Scope.Push(type.formals);
                for (Absyn.Decl.Formal f : t.formals)
                    Scope.Insert(new Value.Formal(f, Parse(f.type)));
                Scope.Pop(zz);
                return type;
            }

            @Override
            public Type visit(Absyn.Type.Ref t) {
                return new Type.Ref(t, Parse(t.target));
            }
        });
    }

    /**
     * The AST from which this type was built (for error messages).
     */
    final Absyn.Type ast;

    private Type(Absyn.Type ast) {
        this.ast = ast;
        if (Value.Module.Current() != null)
            Value.Module.Current().types.add(this);
    }

    boolean checked = false;
    private boolean errored = false;
    int checkDepth = -1;
    static int recursionDepth = 0;
    // incremented/decremented every time the type checker enters/leaves one of
    // the types that's allowed to introduce recursions

    /**
     * Type check 't'. Return the underlying constructed type of 't' (i.e.,
     * strip renaming).
     */
    static Type Check(Type t) {
        if (t == null) return ERROR;
        if (!t.checked) {
            if (t.checkDepth == recursionDepth) {
                IllegalRecursion(t);
            } else {
                int saveDepth = t.checkDepth;
                t.checkDepth = recursionDepth;
                t.check();
                t.checkDepth = saveDepth;
                t.checked = true;
            }
        }
        if (t instanceof Named) t = Strip(t);
        return t;
    }

    abstract void check();

    /**
     * Return the constructed type of 't' (i.e., strip renaming).
     */
    static Type Strip(Type t) {
        Type u = t;
        Type v = t;
        for (;;) {
            if (!(u instanceof Named))
                return u;
            u = ((Named) u).strip();
            if (!(v instanceof Named))
                return v;
            v = ((Named) v).strip();
            if (!(v instanceof Named))
                return v;
            v = ((Named) v).strip();
            if (u == v) {
                IllegalRecursion(t);
                return ERROR;
            }
        }
    }

    static void IllegalRecursion(Type t) {
        if (t.errored)
            return;
        if (t instanceof Named) {
            Named named = (Named) t;
            Value v = named.value;
            if (v != null)
                Value.IllegalRecursion(v);
            else
                Semant.error(t.ast, "illegal recursive type declaration", named.name);
        } else
            Semant.error(t.ast, "illegal recursive type decalaration");
        t.errored = true;
    }

    /**
     * Used to terminate recursion in type equivalence.
     */
    static class Assumption {
        final Assumption prev;
        Type a, b;

        Assumption(Assumption prev, Type a, Type b) {
            this.prev = prev;
            this.a = a;
            this.b = b;
        }
    }

    /**
     * Return true iff (a == b) given the assumptions x.
     */
    static boolean IsEqual(Type a, Type b, Assumption x) {
        if (a == null || b == null)
            return false;
        if (a == b)
            return true;
        if (a instanceof Named)
            a = Strip(a);
        if (b instanceof Named)
            b = Strip(b);
        if (a == b)
            return true;
        if (a == ERROR || b == ERROR)
            return true; // ignore errors
        if (a.getClass() != b.getClass())
            return false;
        for (Assumption y = x; y != null; y = y.prev) {
            if (y.a == a) {
                if (y.b == b)
                    return true;
            } else if (y.a == b) {
                if (y.b == a)
                    return true;
            }
        }
        Assumption y = new Assumption(x, a, b);
        return a.isEqual(b, y);
    }

    /**
     * Return true iff (a == b).
     */
    static boolean IsEqual(Type a, Type b) {
        return IsEqual(a, b, null);
    }

    boolean isEqual(Type t, Assumption x) { return false; }

    /**
     * Returns true iff (a <: b).
     */
    static boolean IsSubtype(Type a, Type b) {
        if (a == null || b == null)
            return false;
        if (a == b)
            return true;
        if (a instanceof Named)
            a = Strip(a);
        if (b instanceof Named)
            b = Strip(b);
        if (a == ERROR || b == ERROR)
            return true; // ignore errors
        if (IsEqual(a, b))
            return true;
        return a.isSubtype(b);
    }

    boolean isSubtype(Type t) { return false; }

    /**
     * Returns true iff (a := b) type-checks.
     */
    static boolean IsAssignable(Type a, Type b) {
        if (IsEqual(a, b) || IsSubtype(b, a))
            return true;
        return false;
    }

    /**
     * Returns true if the type 't' is an ordinal (integer, enumeration, subrange) 
     */
    static boolean IsOrdinal(Type t) {
        return t == ERROR || t == INTEGER || Enum.Is(t) != null;
    }

    /**
     * Return the number of values of the type 't' or -1 if not ordinal.
     */
    static int Number(Type t) {
        return t.number();
    }

    int number() {
        Semant.error(ast, "type has too many elements");
        return -1;
    }

    Value.Tipe tipe;
    boolean inToString = false;

    static String ToString(Type t) {
        if (t == null)
            return null;
        if (t.tipe != null)
            return t.tipe.name;
        return t.toString();
    }

    private String uid;
    private static int count = 0;
    static String GlobalUID(Type t) {
        if (t == null)
            return null;
        if (t.tipe != null)
            return Value.GlobalName(t.tipe);
        if (t.uid == null)
            t.uid = "_T" + count++;
        return t.uid;
    }

    /**
     * Returns true iff the type 't' is a record, set, or array.
     */
    static boolean IsStructured(Type t) {
        if (t == null)
            return false;
        if (OpenArray.Is(t) != null)
            return true;
        return false;
    }

    /**
     * ARRAY OF element
     */
    static class OpenArray extends Type {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        Type element;

        OpenArray(Absyn.Type.Array ast, Type element) {
            super(ast);
            this.element = element;
        }

        /**
         * Test whether a type is an open array type
         * @param t the type to test
         * @return the open array type or null
         */
        static OpenArray Is(Type t) {
            if (t instanceof OpenArray)
                return (OpenArray) t;
            return null;
        }

        @Override
        void check() {
            element = Check(element);
            if (element instanceof OpenArray)
                Semant.error(ast,
                        "M3 restriction: open array element type cannot be an open array");
        }

        @Override
        boolean isEqual(Type t, Assumption x) {
            OpenArray a = this;
            OpenArray b = ((OpenArray) t);
            return IsEqual(a.element, b.element, x);
        }

        @Override
        boolean isSubtype(Type t) {
            if (!(t instanceof OpenArray))
                return false;
            OpenArray a = this;
            OpenArray b = (OpenArray) t;
            return IsEqual(a, b);
        }

        @Override
        public String toString() {
            return "ARRAY OF " + ToString(element);
        }
    }

    /**
     * Enumeration types
     */
    static class Enum extends Type {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        private final Scope scope = Scope.NewClosed();

        Enum(Absyn.Type ast, String... elts) {
            super(ast);
            Scope zz = Scope.Push(scope);
            for (int i = 0; i < elts.length; i++) {
                Value v = new Value.Constant(elts[i], i, this);
                v.checked = true;
                Scope.Insert(v);
            }
            Scope.Pop(zz);
        }

        /**
         * Test if a type is an enumeration type
         * @param t the type to test
         * @return the enumeration type or null
         */
        static Enum Is(Type t) {
            if (t instanceof Enum)
                return (Enum) t;
            return null;
        }

        /**
         * Look up an element of an enumeration type
         * @param t the type in which to look up the element
         * @param key the name of the element
         * @return the value denoted by the key or null
         */
        static Value LookUp(Type t, String key) {
            Enum p = Is(t);
            if (p == null)
                return null;
            Value v = Scope.LookUp(p.scope, key, true);
            return v;
        }

        @Override
        boolean isEqual(Type t, Assumption x) {
            Enum a = this;
            Enum b = (Enum) t;
            Iterator<Value> as = Scope.ToList(a.scope).iterator();
            Iterator<Value> bs = Scope.ToList(b.scope).iterator();
            while (as.hasNext() && bs.hasNext()) {
                Value aa = as.next();
                Value bb = bs.next();
                if (!aa.name.equals(bb.name))
                    return false;
                if (Value.Constant.Is(aa).value != Value.Constant.Is(bb).value)
                    return false;
            }
            if (as.hasNext() || bs.hasNext())
                return false;
            return true;
        }

        @Override
        void check() {
            Scope.TypeCheck(scope);
        }

        @Override
        boolean isSubtype(Type t) {
            return IsEqual(this, t);
        }

        @Override
        int number() {
            return Scope.ToList(scope).size();
        }

        @Override
        public String toString() {
            String s = "{";
            int i = 0;
            for (Value v : Scope.ToList(scope)) {
                if (i != 0)
                    s += ", ";
                s += v.name;
                ++i;
            }
            s += "}";
            return s;
        }
    }

    /**
     * OBJECT
     */
    static class Object extends Type {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        Type parent;
        final Scope fields = Scope.NewClosed();
        final Scope methods = Scope.NewClosed();
        int fieldOffset = 0, fieldSize = 0;
        int methodOffset = 0, methodSize = 0;

        Object(Absyn.Type.Object ast, Type parent) {
            super(ast);
            this.parent = parent;
        }

        /**
         * Test if a type is an object type
         * @param t the type to test
         * @return the object type or null 
         */
        static Object Is(Type t) {
            if (t instanceof Object)
                return (Object) t;
            return null;
        }

        /**
         * Look up a field or method of an object type
         * @param t the type in which to look up the field or method
         * @param key the name of the field or method
         * @return the field or method denoted by the key, or null
         */
        static Value LookUp(Type t, String key) {
            for (;;) {
                t = Check(t);
                if (t == ERROR)
                    return null;
                Object p = Is(t);
                if (p == null)
                    return null;
                {
                    Value v = Scope.LookUp(p.methods, key, true);
                    Value.Method m = Value.Method.Is(v);
                    if (m != null)
                        return PrimaryMethodDeclaration(p, m);
                }
                {
                    Value v = Scope.LookUp(p.fields, key, true);
                    Value.Field f = Value.Field.Is(v);
                    if (f != null)
                        return f;
                }
                t = p.parent;
            }
        }

        private boolean inPrimLookUp = false;
        private static Value PrimaryMethodDeclaration(Object p, Value.Method method) {
            if (!method.override)
                return method;
            if (p.inPrimLookUp) {
                Semant.error(p.ast, "illegal recursive supertype");
                return null;
            } else {
                p.inPrimLookUp = true;
                Value v = LookUp(p.parent, method.name);
                p.inPrimLookUp = false;
                return v;
            }
        }

        @Override
        void check() {
            fieldOffset = 0;
            if (parent != null) {
                parent = Check(parent);
                Object sup = Is(parent);
                if (sup != null) {
                    if (parent == this) {
                        Semant.error(ast, "illegal recursive supertype");
                        parent = null;
                    } else {
                        fieldOffset = sup.fieldOffset + sup.fieldSize;
                        methodOffset = sup.methodOffset + sup.methodSize;
                    }
                } else {
                    Semant.error(ast, "super type must be an object type");
                    parent = null;
                }
            }
            for (Value o : Scope.ToList(fields)) {
                Value.Field field = Value.Field.Is(o);
                field.offset = fieldOffset + fieldSize++;
                if (Scope.LookUp(methods, field.name, true) != null)
                    Semant.error(field.decl, "field and method with the same name", field.name);
            }
            recursionDepth++;
            {
                checked = true;
                for (Value o : Scope.ToList(methods)) {
                    Value.Method method = Value.Method.Is(o);
                    if (method.override) {
                        Value member = LookUp(parent, method.name);
                        Value.Method v = Value.Method.Is(member);
                        if (v != null) {
                            assert v.signature != null;
                            method.signature = v.signature;
                            method.offset = v.offset;
                        } else
                            Semant.error(method.decl, "no method to override in supertype", method.name);
                    } else {
                        method.offset = methodOffset + methodSize++;
                    }
                }
                Scope.TypeCheck(fields);
                Scope.TypeCheck(methods);
            }
            recursionDepth--;
        }

        @Override
        boolean isEqual(Type t, Assumption x) {
            Object a = this;
            Object b = (Object) t;

            // check the field names and offsets
            if (!Value.Field.IsEqual(a.fields, b.fields, x, false))
                return false;

            // check the method names and offsets
            if (!Value.Method.IsEqual(a.methods, b.methods, x, false))
                return false;

            // check the super types
            if (!IsEqual(a.parent, b.parent, x))
                return false;

            // check the field types
            if (!Value.Field.IsEqual(a.fields, b.fields, x, true))
                return false;

            // check the methods types and defaults
            if (!Value.Method.IsEqual(a.methods, b.methods, x, true))
                return false;

            return true;
        }

        @Override
        boolean isSubtype(Type t) {
            if (IsEqual(t, ROOT))
                return true;
            return IsEqual(this, t) || IsSubtype(parent, t);
        }

        @Override
        public String toString() {
            int i;
            String s = String.format("%nOBJECT%n");
            i = 0;
            for (Value o : Scope.ToList(fields)) {
                Value.Field f = Value.Field.Is(o);
                s += String.format(f + "%n");
                ++i;
            }
            s += String.format("METHODS%n");
            i = 0;
            for (Value o : Scope.ToList(methods)) {
                Value.Method m = Value.Method.Is(o);
                s += String.format(m + "%n");
                ++i;
            }
            s += String.format("END");
            return s;
        }
    }

    /**
     * TypeName
     */
    static class Named extends Type {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        String name;
        Named(Absyn.Type.Named ast) {
            super(ast);
            this.name = ast.name;
        }

        /**
         * Test if a type is a named type 
         * @param t the type to test
         * @return the named type or null
         */
        static Named Is(Type t) {
            if (t instanceof Named)
                return (Named) t;
            return null;
        }

        Type type;
        Value value;

        @Override
        void check() {
            if (type == null)
                resolve();
            if (value != null)
                Value.TypeCheck(value);
            type = Check(type);
        }

        private final Scope resolveScope = Scope.Top();
        private Value resolve() {
            if (type != null)
                return value;
            value = Scope.LookUp(resolveScope, name, false);
            Value.Tipe t;
            if (value == null) {
                Semant.error(ast, "undefined", name);
                type = ERROR;
            } else if ((t = Value.Tipe.Is(value)) != null) {
                type = t.value;
            } else {
                Semant.error(ast, "name isn't bound to a type", name);
                type = ERROR;
            }
            return value;
        }

        Type strip() {
            if (type == null)
                resolve();
            return type;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Procedure signature.
     */
    static abstract class Proc extends Type {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        final Scope formals = Scope.NewClosed();
        final int minArgs, maxArgs;
        Type result;

        Proc(Absyn.Type.Proc ast, int minArgs, int maxArgs, Type result) {
            super(ast);
            this.minArgs = minArgs;
            this.maxArgs = maxArgs;
            this.result = result;
        }

        /**
         * Test if a type is a procedure type 
         * @param t the type to test
         * @return the procedure type or null
         */
        static Proc Is(Type t) {
            if (t instanceof Proc)
                return (Proc) t;
            return null;
        }

        @Override
        void check() {
            recursionDepth++;
            {
                checked = true;
                Scope.TypeCheck(formals);
                if (result != null) {
                    result = Check(result);
                    if (result instanceof OpenArray)
                        Semant.error(result.ast, "procedures may not return open arrays");
                }
            }
            recursionDepth--;
        }

        void fixArgs(Absyn.Expr.Call call) {
            int actuals = call.actuals.size();
            if (actuals < minArgs) {
                Semant.error(call, "too few arguments");
                for (int i = actuals; i < minArgs; i++)
                    call.actuals.add(i, null);
            } else if (maxArgs >= 0 && actuals > maxArgs) {
                Semant.error(call, "too many arguments");
            }
        }

        abstract Type check(Absyn.Expr.Call call);

        /**
         * Check that a (procedure) type is compatible with a method signature in an object type
         * @param proc the type to test
         * @param type the object type in which to check
         * @param meth the method signature
         * @return true if compatible, false otherwise
         */
        static boolean IsCompatible(Type proc, Object type, Type meth) {
            Proc p = Is(proc);
            if (p == null)
                return false;
            Proc q = Is(meth);
            if (q == null)
                return false;
            if (Scope.ToList(p.formals).size() != Scope.ToList(q.formals)
                    .size() + 1)
                return false;
            if (p.result == null && q.result == null) /* ok */
                ;
            else if (!IsEqual(p.result, q.result))
                return false;
            Iterator<Value> formals = Scope.ToList(p.formals).iterator();
            // first arg
            Value.Formal formal = Value.Formal.Is(formals.next());
            if (formal.mode != Value.Formal.Mode.VALUE)
                return false;
            if (!IsSubtype(type, formal.type))
                return false;
            if (!FormalsMatch(formals, Scope.ToList(q.formals).iterator(),
                    false, null))
                return false;
            return true;
        }

        @Override
        boolean isEqual(Type t, Assumption x) {
            Proc a = this;
            Proc b = (Proc) t;
            if (a.result == null && b.result == null) /* ok */
                ;
            else if (!IsEqual(a.result, b.result, x))
                return false;
            return FormalsMatch(Scope.ToList(a.formals).iterator(), Scope
                    .ToList(b.formals).iterator(), true, x);
        }

        @Override
        boolean isSubtype(Type t) {
            if (!(t instanceof Proc))
                return false;
            Proc a = this;
            Proc b = (Proc) t;
            if (a.result == null && b.result == null) /* ok */
                ;
            else if (!IsEqual(a.result, b.result))
                return false;
            return FormalsMatch(Scope.ToList(a.formals).iterator(), Scope
                    .ToList(b.formals).iterator(), false, null);
        }

        private static boolean FormalsMatch(Iterator<Value> aa, Iterator<Value> bb,
                boolean strict, Assumption x) {
            while (aa.hasNext() && bb.hasNext()) {
                Value.Formal a = Value.Formal.Is(aa.next());
                Value.Formal b = Value.Formal.Is(bb.next());
                if (a.mode != b.mode)
                    return false;
                if (!IsEqual(a.type, b.type, x))
                    return false;
                if (strict) {
                    if (!a.name.equals(b.name))
                        return false;
                }
            }
            return (!aa.hasNext() && !bb.hasNext());
        }

        /**
         * Convert a method signature into a procedure signature
         * @param sig the method signature to convert
         * @param objType the object type
         * @return the resulting procedure type
         */
        static Proc MethodSigAsProcSig(Proc sig, Type objType) {
            Proc type = new Proc(null, sig.minArgs + 1, sig.maxArgs + 1, sig.result) {
                @Override
                Type check(Absyn.Expr.Call call) {
                    Value.Formal.CheckArgs(call.actuals, Scope.ToList(formals),
                            call.proc);
                    if (result == null)
                        return null;
                    return Check(result);
                }
            };
            Scope zz = Scope.Push(type.formals);
            Scope.Insert(new Value.Formal(objType));
            for (Value f : Scope.ToList(sig.formals))
                Scope.Insert(new Value.Formal(Value.Formal.Is(f)));
            Scope.Pop(zz);
            return type;
        }

        @Override
        public String toString() {
            String s = "(";
            int i = 0;
            for (Value o : Scope.ToList(formals)) {
                Value.Formal f = Value.Formal.Is(o);
                if (i != 0)
                    s += "; ";
                s += f;
                ++i;
            }
            s += ")";
            if (result != null) {
                s += ":";
                if (result.tipe != null)
                    s += result.tipe.name;
                else
                    s += ToString(result);
            }
            return s;
        }
    }

    /**
     * REF
     */
    static class Ref extends Type {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        Type target;

        Ref(Absyn.Type.Ref ast, Type target) {
            super(ast);
            this.target = target;
        }

        /**
         * Test if a type is a reference type 
         * @param t the type to test
         * @return the reference type or null
         */
        static Ref Is(Type t) {
            if (t instanceof Ref)
                return (Ref) t;
            return null;
        }

        @Override
        void check() {
            recursionDepth++;
            {
                checked = true;
                if (target != null)
                    target = Check(target);
            }
            recursionDepth--;
        }

        @Override
        boolean isEqual(Type t, Assumption x) {
            Ref a = this;
            Ref b = (Ref) t;
            if (a.target == null && b.target == null)
                return a == b;
            return IsEqual(a.target, b.target, x);
        }

        @Override
        boolean isSubtype(Type t) {
            if (IsEqual(this, t))
                return true;
            if (IsEqual(this, NULL))
                return IsSubtype(t, REFANY) || Proc.Is(t) != null;
            return IsEqual(t, REFANY);
        }

        @Override
        public String toString() {
            return "REF " + ToString(target);
        }
    }

    /**
     * The builtin types.
     */
    static final Type ERROR = new Type(null) {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        @Override
        void check() {}

        @Override
        public String toString() { return "ERROR"; }            
    };
    static final Type INTEGER = new Type(null) {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        @Override
        void check() {}
    };
    static final Enum BOOLEAN = new Enum(null, "FALSE", "TRUE");
    static final Enum CHAR;
    static {
        String[] chars = new String[256];
        for (char i = 0; i < 256; i++)
            chars[i] = String.valueOf(i);
        CHAR = new Enum(null, chars);
    }
    static final Type TEXT = new Ref(null, null);
    static final Ref NULL = new Ref(null, null);
    static final Ref REFANY = new Ref(null, null);
    static final Object ROOT = new Object(null, null);
    static final Proc FIRST = new Proc(null, 1, 1, null) {
        @Override
        <R> R accept(Visitor<R> v) { return v.visit(this); }

        @Override
        Type check(Absyn.Expr.Call call) {
            Iterator<Absyn.Expr> args = call.actuals.iterator();
            Absyn.Expr e = args.next();
            Type t = Expr.TypeCheck(e);
            Type index;
            if (OpenArray.Is(t) != null) {
                index = INTEGER;
            } else if ((t = TypeExpr.Split(e)) != null) {
                if (OpenArray.Is(t) != null) {
                    Semant.error(call, "argument cannot be an open array type");
                    index = INTEGER;
                } else
                    index = t;
            } else {
                Semant.error(call, "argument must be a type or array");
                index = INTEGER;
            }
            if (Enum.Is(index) != null) {
                if (Number(index) <= 0)
                    Semant.error(call, "empty enumeration type");
            } else if (IsOrdinal(index)) {
                // ok
            } else {
                Semant.error(call,
                        "argument must be an ordinal type, array type or array");
            }
            while (args.hasNext())
                Expr.TypeCheck(args.next());
            return index;
        }
    };
    static final Proc LAST = new Proc(null, 1, 1, null) {
        @Override
        Type check(Absyn.Expr.Call call) {
            return FIRST.check(call);
        }
    };
    static final Proc NUMBER = new Proc(null, 1, 1, null) {
        @Override
        Type check(Absyn.Expr.Call call) {
            return FIRST.check(call);
        }
    };
    static final Proc ORD = new Proc(null, 1, 1, null) {
        @Override
        Type check(Absyn.Expr.Call call) {
            Iterator<Absyn.Expr> args = call.actuals.iterator();
            Type t = Expr.TypeCheck(args.next());
            if (!IsOrdinal(t))
                Semant.error(call, "argument must be an ordinal");
            while (args.hasNext())
                Expr.TypeCheck(args.next());
            return INTEGER;
        }
    };
    static final Proc VAL = new Proc(null, 2, 2, null) {
        @Override
        Type check(Absyn.Expr.Call call) {
            Iterator<Absyn.Expr> args = call.actuals.iterator();
            Absyn.Expr e = args.next();
            Type t = Expr.TypeCheck(e);
            if (IsSubtype(t, INTEGER))
                t = INTEGER;
            else {
                Semant.error(call, "first argument must be an integer");
            }
            e = args.next();
            t = Expr.TypeCheck(e);
            if ((t = TypeExpr.Split(e)) == null) {
                Semant.error(call, "second argument must be a type");
            } else if (!IsOrdinal(t)) {
                Semant.error(call, "second argument must be an ordinal type");
            }
            while (args.hasNext())
                Expr.TypeCheck(args.next());
            return t;
        }
    };
    static final Proc NEW = new Proc(null, 1, -1, null) {
        @Override
        Type check(Absyn.Expr.Call call) {
            Iterator<Absyn.Expr> args = call.actuals.iterator();
            Absyn.Expr e = args.next();
            Expr.TypeCheck(e);
            Type t = TypeExpr.Split(e);
            if (t == null) {
                Semant.error(call, "NEW must be applied to a reference type");
                return NULL;
            }
            t = Check(t);
            Ref ref = Ref.Is(t);
            if (ref != null) {
                Type r = ref.target;
                if (r == null) {
                    Semant.error(call, "cannot NEW a variable of type REFANY or NULL");
                    return t;
                }
                r = Check(r);
                if (OpenArray.Is(r) != null) {
                    while (args.hasNext()) {
                        e = args.next();
                        Type x = Expr.TypeCheck(e);
                        if (!IsEqual(x, INTEGER))
                            Semant.error(e, "argument must be an integer");
                        else {
                            OpenArray a = OpenArray.Is(r);
                            if (a == null) {
                                Semant.error(e, "too many dimensions specified");
                            } else {
                                r = a.element;
                            }
                        }
                    }
                    if (OpenArray.Is(r) != null)
                        Semant.error(call, "not enough dimensions specified");
                    return t;
                }
                if (args.hasNext())
                    Semant.error(call, "too many arguments to NEW");
                while (args.hasNext())
                    Expr.TypeCheck(args.next());
                return t;
            }
            Object object = Object.Is(t);
            if (object != null) {
                if (args.hasNext())
                    Semant.error(call, "too many arguments to NEW");
                while (args.hasNext())
                    Expr.TypeCheck(args.next());
                return t;
            }
            if (t != ERROR)
                Semant.error(call, "NEW must be applied to a reference type");
            return t;
        }
    };
}
