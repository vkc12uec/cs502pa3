package M3;

import java.io.PrintWriter;
import java.util.*;

import Translate.*;
import Translate.Temp.Label;

class Translate {
    private static void usage() {
        throw new Error("Usage: java M3.Translate "
            + "[ -target= Mips|PPCDarwin|PPCLinux ] <source>.java");
    }

    static Frame target;
    public static void main(String[] args) {
        target = new Mips.Frame();
        boolean main = false;
        if (args.length < 1) usage();
        if (args.length > 1)
            for (String arg : args) {
                if (arg.equals("-main"))
                    main = true;
                else if (arg.equals("-target=Mips"))
                    target = new Mips.Frame();
                else if (arg.equals("-target=PPCDarwin"))
                    target = new PPC.Frame.Darwin();
                else if (arg.equals("-target=PPCLinux"))
                    target = new PPC.Frame.Linux();
                else if (arg.startsWith("-"))
                    usage();
            }
        java.io.File file = new java.io.File(args[args.length - 1]);
        try {
            Value.Module module = Semant.TypeCheck(file);
            if (Semant.anyErrors) return;
            List<Frag> frags = Compile(module, main);
            if (Semant.anyErrors) return;
            PrintWriter out = new PrintWriter(System.out);
            for (Frag f : frags) {
              out.println(f);
              out.flush();
            }
        } catch (ParseException e) {
            System.err.println(e.getMessage());
        } catch (TokenMgrError e) {
            System.err.println(e.getMessage());
        }
        System.err.flush();
    }

    static ProcBody currentBody;
    static Label returnLabel;
    static final Map<ProcBody,Frame> frames = new HashMap<ProcBody,Frame>();
    static LinkedList<Frag> frags = new LinkedList<Frag>();

    static List<Frag> Compile(final Value.Module m, boolean main) {
        String bodyName = m.name + (m.isInterface ? "_I3" : "_M3");
        Frame f = frames.put(m.body, target.newFrame(bodyName));
        assert f == null;
        Scope zz = Scope.Push(m.locals);
        for (Type t : m.types) Compile(t);
        // declare my imports, exports and local variables
        for (Value.External.Port p : m.externals.imports.values()) {
            EnterScope(p.module.locals);
        }
        EnterScope(m.imports);
        EnterScope(m.locals);
        // generate any internal procedures
        Value.Visitor emitDecl = new Value.Visitor() {
            public void visit(Value.Field v) { assert false; }
            public void visit(Value.Formal v) { assert false; }
            public void visit(Value.Method v) { assert false; }
            public void visit(Value.Procedure v) {
                currentBody = v.body;
                Declare(v);
            }
            public void visit(Value.Variable v) { assert false; }
            public void visit(Value.Constant v) { assert false; }
            public void visit(Value.External v) { assert false; }
            public void visit(Value.Module v) {
                currentBody = v.body;
            }
            public void visit(Value.Tipe v) { assert false; }
        };
        Value.Visitor emitBody = new Value.Visitor() {
            public void visit(Value.Field v) { assert false; }
            public void visit(Value.Formal v) { assert false; }
            public void visit(Value.Method v) { assert false; }
            public void visit(Value.Procedure v) {
                returnLabel = new Temp.Label();
                currentBody = v.body;
                Tree.Stm stm = null;
                Scope zz = Scope.Push(v.syms);
                EnterScope(v.syms);
                stm = SEQ(stm, InitValues(v.syms));
                for (Absyn.Stmt stmt : v.decl.stmts) {
                    stm = SEQ(stm, Compile(stmt).unNx());
                }
                stm = SEQ(stm, LABEL(returnLabel));
                Scope.Pop(zz);
                frags.add(new Frag.Proc(stm, frames.get(currentBody)));
            }
            public void visit(Value.Variable v) { assert false; }
            public void visit(Value.Constant v) { assert false; }
            public void visit(Value.External v) { assert false; }
            public void visit(Value.Module v) {
                returnLabel = null;
                currentBody = v.body;
                Tree.Stm stm = null;
                stm = SEQ(stm, InitValues(m.imports));
                stm = SEQ(stm, InitValues(m.locals));
                // initialize my exported variables
                stm = SEQ(stm, InitGlobals(m.externals));
                // perform the main body
                if (m.decl.stmts != null)
                    for (Absyn.Expr.Stmt s : m.decl.stmts)
                        stm = SEQ(stm, Compile(s).unNx());
                frags.add(new Frag.Proc(stm, frames.get(currentBody)));
            }
            public void visit(Value.Tipe v) { assert false; }
        };
        ProcBody.EmitAll(emitDecl, emitBody);
        Scope.Pop(zz);

        if (!main) return frags;

        // generate code for main method
        {
            Frame frame = target.newFrame("main");
            frame.isGlobal = true;
            Tree.Stm stm = ESTM(CALL(NAME(Temp.getLabel(bodyName)), Exps()));
            frags.add(new Frag.Proc(stm, frame));
        }
        // generate code for helper functions
        {
            Frame frame = target.newFrame("new");
            Tree.Exp _size = frame.allocFormal(new Temp("_size")).exp(frame.FP());
            Tree.Exp _head = frame.allocFormal(new Temp("_head")).exp(frame.FP());
            Temp size = new Temp();
            Temp head = new Temp();
            Temp p = new Temp();
            Tree.Stm stm =
                SEQ(MOVE(TEMP(size), _size), //
                    MOVE(TEMP(head), _head), //
                    MOVE(TEMP(p), //
                         CALL(frame.external("malloc"), //
                              Exps(ADD(CONST(target.wordSize()), TEMP(size))))), //
                    MOVE(MEM(TEMP(p)), TEMP(head)), //
                    MOVE(TEMP(p), ADD(TEMP(p), CONST(target.wordSize()))), //
                    ESTM(CALL(frame.external("bzero"), //
                              Exps(TEMP(p), TEMP(size)))), //
                    MOVE(frame.RV(), TEMP(p)));
            frags.add(new Frag.Proc(stm, frame));
        }
        {
            Frame frame = target.newFrame("badPtr");
            Label msg = stringLabel("Attempt to use a null pointer");
            Tree.Stm stm =
                SEQ(ESTM(CALL(frame.external("puts"), Exps(NAME(msg)))),
                    ESTM(CALL(frame.external("exit"), Exps(CONST(1)))));
            frags.add(new Frag.Proc(stm, frame));
        }
        {
            Frame frame = target.newFrame("badSub");
            Label msg = stringLabel("Subscript out of bounds");
            Tree.Stm stm =
                SEQ(ESTM(CALL(frame.external("puts"), Exps(NAME(msg)))),
                    ESTM(CALL(frame.external("exit"), Exps(CONST(1)))));
            frags.add(new Frag.Proc(stm, frame));
        }
        return frags;
    }

    static Tree.Stm InitValues(Scope scope) {
        Tree.Stm stm = null;
        for (Value v : Scope.ToList(scope)) stm = SEQ(stm, LangInit(v));
        for (Value v : Scope.ToList(scope)) stm = SEQ(stm, UserInit(v));
        return stm;
    }

    static Tree.Stm InitGlobals(Value.External.Set set) {
        Tree.Stm stm = null;
        for (Value.External.Port p: set.exports.values())
            stm = SEQ(stm, InitExports(p.module));
        return stm;
    }

    static Tree.Stm InitExports(Value.Module m) {
        Tree.Stm stm = null;
        for (Value v : Scope.ToList(m.locals))
            if (v.exported)
                stm = SEQ(stm, InitGlobal(Value.Variable.Is(v)));
        return stm;
    }

    static Tree.Stm InitGlobal(Value.Variable v) {
        if (v == null) return null;
        if (!v.initDone && !v.external) {
            assert !Type.IsStructured(v.type);
            return MOVE(LoadLValue(v), CONST(0));
        }
        return null;
    }

    static Tree.Exp MEM(Tree.Exp exp) {
        return new Tree.Exp.MEM(exp, CONST(0));
    }

    static Tree.Exp MEM(Tree.Exp exp, int i) {
        return new Tree.Exp.MEM(exp, CONST(i));
    }

    static Tree.Exp TEMP(Temp temp) {
        return new Tree.Exp.TEMP(temp);
    }

    static Tree.Exp ESEQ(Tree.Stm stm, Tree.Exp exp) {
        return (stm == null) ? exp : new Tree.Exp.ESEQ(stm, exp);
    }

    static Tree.Exp NAME(Temp.Label label) {
        return new Tree.Exp.NAME(label);
    }

    static Tree.Exp.CONST CONST(int value) {
        return new Tree.Exp.CONST(value);
    }

    static Tree.Exp[] Exps(Tree.Exp... a) {
        return a;
    }

    static Tree.Exp CALL(Tree.Exp f, Tree.Exp[] a) {
        return CALL(f, CONST(0), a);
    }
    static Tree.Exp CALL(Tree.Exp f, Tree.Exp l, Tree.Exp[] a) {
        Frame frame = frames.get(currentBody);
        if (a.length > frame.maxArgsOut)
            frame.maxArgsOut = a.length;
        assert f != null;
        return new Tree.Exp.CALL(f, l, a);
    }

    static Tree.Exp ADD(Tree.Exp l, Tree.Exp r) {
        if (l instanceof Tree.Exp.CONST && ((Tree.Exp.CONST)l).value == 0)
            return r;
        if (r instanceof Tree.Exp.CONST && ((Tree.Exp.CONST)r).value == 0)
            return l;
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.ADD, l, r);
    }

    static Tree.Exp AND(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.AND, l, r);
    }

    static Tree.Exp DIV(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.DIV, l, r);
    }

    static Tree.Exp DIVU(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.DIVU, l, r);
    }

    static Tree.Exp MOD(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.MOD, l, r);
    }

    static Tree.Exp MUL(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.MUL, l, r);
    }

    static Tree.Exp OR(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.OR, l, r);
    }

    static Tree.Exp SLL(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.SLL, l, r);
    }

    static Tree.Exp SRA(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.SRA, l, r);
    }

    static Tree.Exp SRL(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.SRL, l, r);
    }

    static Tree.Exp SUB(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.SUB, l, r);
    }

    static Tree.Exp XOR(Tree.Exp l, Tree.Exp r) {
        return new Tree.Exp.BINOP(Tree.Exp.BINOP.Operator.XOR, l, r);
    }

    static Tree.Stm SEQ(Tree.Stm l, Tree.Stm r) {
        if (l == null)
            return r;
        if (r == null)
            return l;
        return new Tree.Stm.SEQ(l, r);
    }

    static Tree.Stm SEQ(Tree.Stm... a) {
        Tree.Stm stm = null;
        for (Tree.Stm s : a)
            stm = SEQ(stm, s);
        return stm;
    }

    static Tree.Stm LABEL(Temp.Label label) {
        return new Tree.Stm.LABEL(label);
    }

    static Tree.Stm JUMP(Temp.Label target) {
        return new Tree.Stm.JUMP(target);
    }

    static Tree.Stm JUMP(Tree.Exp exp, Temp.Label[] targets) {
        return new Tree.Stm.JUMP(exp, targets);
    }

    static Tree.Stm MOVE(Tree.Exp d, Tree.Exp s) {
        return new Tree.Stm.MOVE(d, s);
    }

    static Tree.Stm ESTM(Tree.Exp exp) {
        return new Tree.Stm.EXP(exp);
    }

    static Tree.Stm BEQ(Tree.Exp l, Tree.Exp r, Temp.Label t, Temp.Label f) {
        return new Tree.Stm.CJUMP(Tree.Stm.CJUMP.Operator.BEQ, l, r, t, f);
    }

    static Tree.Stm BGE(Tree.Exp l, Tree.Exp r, Temp.Label t, Temp.Label f) {
        return new Tree.Stm.CJUMP(Tree.Stm.CJUMP.Operator.BGE, l, r, t, f);
    }

    static Tree.Stm BGT(Tree.Exp l, Tree.Exp r, Temp.Label t, Temp.Label f) {
        return new Tree.Stm.CJUMP(Tree.Stm.CJUMP.Operator.BGT, l, r, t, f);
    }

    static Tree.Stm BLE(Tree.Exp l, Tree.Exp r, Temp.Label t, Temp.Label f) {
        return new Tree.Stm.CJUMP(Tree.Stm.CJUMP.Operator.BLE, l, r, t, f);
    }

    static Tree.Stm BLT(Tree.Exp l, Tree.Exp r, Temp.Label t, Temp.Label f) {
        return new Tree.Stm.CJUMP(Tree.Stm.CJUMP.Operator.BLT, l, r, t, f);
    }

    static Tree.Stm BNE(Tree.Exp l, Tree.Exp r, Temp.Label t, Temp.Label f) {
        return new Tree.Stm.CJUMP(Tree.Stm.CJUMP.Operator.BNE, l, r, t, f);
    }

    /**
     * Generate declarations for all the values in a scope.
     * Generate procedure declarations after non-procedure declarations.
     */
    static void EnterScope(Scope scope) {
        if (scope == null) return;
        for (Value v : Scope.ToList(scope))
            if (Value.Procedure.Is(v) == null)
                Declare(v);
        for (Value v : Scope.ToList(scope)) {
            if (Value.Procedure.Is(v) != null) {
                Declare(v);
            }
        }
    }

    /**
     * Every variable declaration has an associated access.
     * This map keeps track of them. 
     */
    static Map<Value.Variable,Frame.Access> accesses = new HashMap<Value.Variable,Frame.Access>();

    /**
     * Generate declaration for v.
     */
    static void Declare(Value v) {
        if (v == null) return;
        if (v.declared) return;
        v.declared = true;
        v.accept(new Value.Visitor() {
            public void visit(Value.Field v) {}
            public void visit(Value.Formal v) {}
            public void visit(Value.Method v) {}
            public void visit(Value.Module v) {}
            public void visit(final Value.Procedure v) {
                Type.Proc sig = v.signature;
                if (v.intf_peer != null) {
                    sig = v.intf_peer.signature;
                    Compile(sig);
                }
                Compile(v.signature);
                // try to compile the imported type first

                ProcBody body = v.body;
                if (body == null) {
                    // it's not a local procedure
                    if (v.impl_peer != null) {
                        // it's an interface procedure that's implemented in this module
                        visit(v.impl_peer);
                        return;
                    } else {
                        // it's an imported procedure
                        ImportProc(v);
                        return;
                    }
                }
                Frame frame = target.newFrame(Value.GlobalName(v));
                Frame f = frames.put(body, frame);
                assert f == null;
                if (body.parent != null)
                    frame.link = frame.allocLocal(body.children == null ? new Temp("_link") : null);
                currentBody = body;
                Scope zz = Scope.Push(v.syms);
                EnterScope(v.syms);
                Scope.Pop(zz);
            }
            void ImportProc(Value.Procedure v) {
                if (v.syms != null) {
                    Scope zz = Scope.Push(v.syms);
                    EnterScope(v.syms);
                    Scope.Pop(zz);
                } else {
                    DeclareFormals(v);
                }
            }
            void DeclareFormals(Value.Procedure p) {
                // declare types for each of the formals
                for (Value v : Scope.ToList(p.signature.formals)) {
                    Value.Formal f = Value.Formal.Is(v);
                    Compile(Value.TypeOf(f));
                    if (f.mode != Value.Formal.Mode.VALUE || f.refType != null) {
                        Compile(f.refType);
                    }
                }
            }
            public void visit(Value.Variable v) {
                Compile(v.type);
                // declare the actual variable
                if (v.external) {
                    // external
                } else if (v.imported) {
                    // imported
                } else if (v.toplevel) {
                    // global
                    Temp.Label l = Temp.getLabel(Value.GlobalName(v));
                    frags.add(new Frag.Data(target.record(l, 1)));
                    if (v.decl.expr == null) v.initDone = true;
                } else {
                    Temp t = null;
                    if (!v.up_level && !v.flags.contains(Value.Flags.needsAddress))
                        t = new Temp(Value.GlobalName(v));
                    if (v.formal == null) {
                        // simple local variable
                        accesses.put(v, frames.get(currentBody).allocLocal(t));
                    } else if (v.indirect) {
                        // formal passed by reference => param is an address
                        accesses.put(v, frames.get(currentBody).allocFormal(t));
                    } else {
                        // simple parameter
                        accesses.put(v, frames.get(currentBody).allocFormal(t));
                    }
                }
            }
            public void visit(Value.Constant v) {
                if (v.exported) Compile(v.type);
            }
            public void visit(Value.External v) {
                Value value = v.value;
                if (value != null) {
                    boolean i = value.imported;
                    boolean e = value.exported;
                    value.imported = v.imported;
                    value.exported = v.exported;
                    Declare(value);
                    value.imported = i;
                    value.exported = e;
                }
            }
            public void visit(Value.Tipe v) {
                Compile(v.value);
            }
        });
    }

    /**
     * Generate language-defined initialization code for v.
     */
    static Tree.Stm LangInit(Value v) {
        class Visitor implements Value.Visitor {
            Tree.Stm stm = null;
            public void visit(Value.Constant v) {}
            public void visit(Value.Module v) {}
            public void visit(Value.Procedure v) {}
            public void visit(Value.Tipe v) {}
            public void visit(Value.External v) {
                stm = LangInit(v.value);
            }
            public void visit(Value.Field v) {
                Compile(v.type);
            }
            public void visit(Value.Formal v) {
                Compile(v.type);
                Compile(v.refType);
            }
            public void visit(Value.Method v) {
                Compile(v.signature);
            }
            @Override
            public void visit(Value.Variable v) {
                if (v.imported || v.external) {
                    v.initDone = true;
                } else if (v.formal != null) {
                    if (v.indirect)
                        stm = SEQ(stm, CopyOpenArray(v, v.formal.refType));
                    // formal parameters don't need any further initialization
                    v.initDone = true;
                } else if (v.indirect && !v.toplevel) {
                    // WITH variable bound to a designator
                    v.initDone = true;
                }

                if (v.initDone) return;

                // initialize the value
                if (v.expr != null && !v.up_level && !v.imported) {
                    // variable has a user specified init value and isn't referenced
                    // by any nested procedures => try to avoid the language defined
                    // init and wait until we get to the user defined initialization.
                    v.initPending = true;
                }
            }
            Tree.Stm CopyOpenArray(Value.Variable v, Type t) {
                if (t == null) return null;
                Semant.error(v.getDecl(), "open array passed by VALUE unimplemented", v.name);
                return null;
            }
        }
        if (v == null) return null;
        if (v.compiled) return null;
        assert v.checked;
        v.compiled = true;
        Visitor result = new Visitor();
        v.accept(result);
        return result.stm;
    }

    /**
     * Generate code to load v.
     */
    static Tree.Exp Load(Value v) {
        class Visitor implements Value.Visitor {
            Tree.Exp exp;
            public void visit(Value.Field v) { assert false; }
            public void visit(Value.Tipe v) { assert false; }
            public void visit(Value.Module v) { assert false; }
            public void visit(Value.Method v) { assert false; }
            public void visit(Value.Formal v) {
                Semant.error(v.decl, "formal has no default value", v.name);
            }
            public void visit(Value.Constant v) {
                exp = CONST(v.value);
            }
            public void visit(Value.Procedure v) {
                if (v.decl == null) Semant.error("builtin operation is not a procedure (" + v.name + ")");
                if (v.impl_peer != null) v = v.impl_peer;
                Declare(v);
                if (v.external)
                    exp = target.external(Value.GlobalName(v));
                else
                    exp = NAME(Temp.getLabel(Value.GlobalName(v)));
            }
            public void visit(Value.External v) {
                exp = Load(v.value);
            }
            public void visit(Value.Variable v) {
                exp = LoadLValue(v);
            }
        }
        if (v == null) return null;
        assert v.checked;
        Visitor result = new Visitor();
        v.accept(result);
        return result.exp;
    }

    static Tree.Exp LoadLValue(Value.Variable v) {
        Declare(v);
        if (v.initPending) ForceInit(v);
        Frame.Access a = accesses.get(v);
        Tree.Exp exp;
        if (a == null) {
            assert v.toplevel;
            if (v.external)
                exp = MEM(target.external(Value.GlobalName(v)));
            else
                exp = MEM(NAME(Temp.getLabel(Value.GlobalName(v))));
        } else {
            Tree.Exp fp = target.FP();
            ProcBody home = v.proc;
            for (ProcBody body = currentBody; body != home; body = body.parent)
                fp = frames.get(body).link.exp(fp);
            exp = a.exp(fp);
        }
        return v.indirect ? MEM(exp) : exp; 
    }

    static Tree.Stm ForceInit(Value.Variable v) {
        v.initPending = false;
        return InitValue(LoadLValue(v), v.type);
    }

    /**
     * Generate language-defined initialization value for a variable of type t.
     */
    static Tree.Stm InitValue(final Tree.Exp lvalue, Type t) {
        class Visitor implements Type.Visitor<Tree.Stm> {
            public Tree.Stm visit(Type.Named t) { assert false; return null; }
            public Tree.Stm visit(Type t) { return MOVE(lvalue, CONST(0)); }
            public Tree.Stm visit(Type.Enum t) { return MOVE(lvalue, CONST(0)); }
            public Tree.Stm visit(Type.Ref t) { return MOVE(lvalue, CONST(0)); }
            public Tree.Stm visit(Type.Proc t) { return MOVE(lvalue, CONST(0)); }
            public Tree.Stm visit(Type.Object t) { return MOVE(lvalue, CONST(0)); }
            public Tree.Stm visit(Type.OpenArray t) {
                assert false;
                return null;
            }
        }
        t = Type.Check(t);
        return t.accept(new Visitor());
    }

    /**
     * Generate user-defined initialization code for v. 
     */
    static Tree.Stm UserInit(Value v) {
        class Visitor implements Value.Visitor {
            Tree.Stm stm;
            public void visit(Value.Field v) {}
            public void visit(Value.Formal v) {}
            public void visit(Value.Method v) {}
            public void visit(Value.Procedure v) {}
            public void visit(Value.Constant v) {}
            public void visit(Value.Module v) {}
            public void visit(Value.Tipe v) {}
            public void visit(Value.Variable v) {
                if (v.expr != null && !v.initDone && !v.imported) {
                    v.initPending = false;
                    stm = MOVE(LoadLValue(v), Compile(v.expr).unEx());
                }
                v.initDone = true;
            }
            public void visit(Value.External v) {
                stm = UserInit(v.value);
            }
        }
        if (v == null) return null;
        Visitor result = new Visitor();
        v.accept(result);
        return result.stm;
    }

    /**
     * Compile a type t.
     */
    static Map<Type,Type> compiled = new HashMap<Type,Type>();
    {
        compiled.put(Type.Check(Type.BOOLEAN),Type.BOOLEAN);
        compiled.put(Type.Check(Type.CHAR),Type.CHAR);
        compiled.put(Type.Check(Type.INTEGER),Type.INTEGER);
        compiled.put(Type.Check(Type.NULL),Type.NULL);
        compiled.put(Type.Check(Type.ROOT),Type.ROOT);
        compiled.put(Type.Check(Type.REFANY),Type.REFANY);
        compiled.put(Type.Check(Type.TEXT),Type.TEXT);
        compiled.put(Type.Check(Type.ERROR),Type.ERROR);
    }
    static void Compile(Type t) {
        class Visitor implements Type.Visitor<Void> {
            public Void visit(Type t) {
                assert t != Type.ERROR;
                return null;
            }
            public Void visit(Type.Enum t) { return null; }
            public Void visit(Type.Named t) {
                Compile(Type.Strip(t));
                return null;
            }
            public Void visit(Type.Object t) {
                Compile(t.parent);
                for (Value v : Scope.ToList(t.fields)) {
                    Value.Field f = Value.Field.Is(v);
                    Compile(Value.TypeOf(f));
                }
                for (Value v : Scope.ToList(t.methods)) {
                    Value.Method m = Value.Method.Is(v);
                    Compile(Value.TypeOf(m));
                }
                Vector<Temp.Label> defaults = new Vector<Temp.Label>(t.methodOffset + t.methodSize);
                GenMethodList(t, defaults);
                String vtable = target.vtable(Temp.getLabel(Type.GlobalUID(t)), defaults);
                frags.add(new Frag.Data(vtable));
                return null;
            }
            void GenMethodList(Type.Object t, Vector<Temp.Label> defaults) {
                if (t == null) return;
                GenMethodList(Type.Object.Is(t.parent), defaults);
                for (Value v : Scope.ToList(t.methods)) {
                    Value.Method m = Value.Method.Is(v);
                    defaults.add(m.offset, m.value == null ? target.badPtr() : Temp.getLabel(Value.GlobalName(m.value)));
                }
            }
            public Void visit(Type.OpenArray t) {
                Compile(t.element);
                return null;
            }
            public Void visit(Type.Proc t) {
                Compile(t.result);
                for (Value v : Scope.ToList(t.formals)) {
                    Value.Formal f = Value.Formal.Is(v);
                    Compile(Value.TypeOf(f));
                }
                return null;
            }
            public Void visit(Type.Ref t) {
                Compile(t.target);
                return null;
            }
        }
        if (t == null) return;
        Type u = Type.Check(t);
        if (compiled.put(u, u) != null) return;
        t.accept(new Visitor());
    }

    static Temp.Label currentExit;

    static Exp Compile(Absyn.Stmt s) {
        class Visitor implements Absyn.Stmt.Visitor<Exp> {
            public Exp visit(Absyn.Stmt.Assign s) {
                Tree.Exp lhs = Compile(s.lhs).unEx();
                Tree.Exp rhs = Compile(s.rhs).unEx();
                assert s.lhs.checked;
                if (Type.IsStructured(s.lhs.type)) {
		    Tree.Stm stm = null;
                    Tree.Exp.MEM mem;

                    Temp a = new Temp();
                    mem = (Tree.Exp.MEM)lhs;
                    stm = SEQ(stm, MOVE(TEMP(a), ADD(mem.exp, mem.offset)));

                    Temp b = new Temp();
                    mem = (Tree.Exp.MEM)rhs;
                    stm = SEQ(stm, MOVE(TEMP(b), ADD(mem.exp, mem.offset)));

                    Temp i = new Temp();
                    stm = SEQ(stm, MOVE(TEMP(i), MEM(TEMP(a), -target.wordSize())));

                    Temp.Label badSub = target.badSub();
                    if (badSub != null) {
                        Temp.Label okPtr = new Label();
                        stm = SEQ(stm, BNE(TEMP(i), MEM(TEMP(b), -target.wordSize()), badSub, okPtr), LABEL(okPtr));
                    }

                    Temp.Label top = new Label();
                    Temp.Label copy = new Label();
                    Temp.Label done = new Label();
                    stm = SEQ(stm, MOVE(TEMP(i), MUL(TEMP(i), CONST(target.wordSize()))));
                    stm = SEQ(stm, LABEL(top));
                    stm = SEQ(stm, MOVE(TEMP(i), SUB(TEMP(i), CONST(target.wordSize()))));
                    stm = SEQ(stm, BLT(TEMP(i), CONST(0), done, copy));
                    stm = SEQ(stm, LABEL(copy));
                    stm = SEQ(stm, MOVE(MEM(ADD(TEMP(a), TEMP(i))), MEM(ADD(TEMP(b), TEMP(i)))));
                    stm = SEQ(stm, JUMP(top));
                    stm = SEQ(stm, LABEL(done));
                    return new Exp.Nx(stm);
                }
                return new Exp.Nx(MOVE(lhs, rhs));
            }
            public Exp visit(Absyn.Stmt.Call s) {
                return Compile(s.expr);
            }
            public Exp visit(Absyn.Stmt.Exit s) {
                return new Exp.Nx(JUMP(currentExit));
            }
            public Exp visit(Absyn.Stmt.Eval s) {
                return Compile(s.expr);
            }
            public Exp visit(Absyn.Stmt.For s) {
                Tree.Exp step, limit, from;
                Tree.Exp.CONST step_val = null, limit_val = null, from_val = null;
                Temp index, to, by;
                Temp.Label top, test, down, up, exit;
                Tree.Stm stm = null;

                from = Compile(s.from).unEx();
                if (from instanceof Tree.Exp.CONST) {
                    from_val = (Tree.Exp.CONST)from;
                }
                index = new Temp();
                stm = SEQ(stm, MOVE(TEMP(index), from));

                limit = Compile(s.to).unEx();
                if (limit instanceof Tree.Exp.CONST) {
                    limit_val = (Tree.Exp.CONST)limit;
                } else {
                    to = new Temp();
                    stm = SEQ(stm, MOVE(TEMP(to), limit));
                    limit = TEMP(to);
                }

                if (s.by == null) step = CONST(1);
                else step = Compile(s.by).unEx();
                if (step instanceof Tree.Exp.CONST) {
                    step_val = (Tree.Exp.CONST)step;
                } else {
                    by = new Temp();
                    stm = SEQ(stm, MOVE(TEMP(by), step));
                    step = TEMP(by);
                }

                top = new Temp.Label();
                test = new Temp.Label();
                exit = new Temp.Label();

                Scope zz = Scope.Push(s.scope);
                EnterScope(s.scope);
                stm = SEQ(stm, InitValues(s.scope));
                if (from_val == null || limit_val == null || step_val == null) {
                    // we don't know all three values
                    stm = SEQ(stm, JUMP(test));
                } else if (step_val.value >= 0 && from_val.value <= limit_val.value) {
                    // we know we'll execute the loop at least once
                } else if (step_val.value <= 0 && limit_val.value <= from_val.value) {
                    // we know we'll execute the loop at least once
                } else {
                    // we won't execute the loop
                    stm = SEQ(stm, JUMP(test));
                }
                stm = SEQ(stm, LABEL(top));

                Temp.Label oldExit = currentExit;
                currentExit = exit;

                // make the user's variable equal to the counter
                Value v = Scope.ToList(s.scope).iterator().next();
                stm = SEQ(stm, MOVE(LoadLValue(Value.Variable.Is(v)), TEMP(index)));

                for (Absyn.Stmt stmt : s.stmts) {
                    stm = SEQ(stm, Compile(stmt).unNx());
                }

                // increment the counter
                stm = SEQ(stm, MOVE(TEMP(index), ADD(TEMP(index), step)));

                // generate the loop test
                stm = SEQ(stm, LABEL(test));
                if (step_val != null) {
                    // constant step value
                    if (step_val.value >= 0)
                        stm = SEQ(stm, BLE(TEMP(index), limit, top, exit));
                    else
                        stm = SEQ(stm, BGE(TEMP(index), limit, top, exit));
                } else {
                    // variable step value
                    up = new Temp.Label();
                    down = new Temp.Label();

                    stm = SEQ(stm, BLT(step, CONST(0), down, up));
                    stm = SEQ(stm, LABEL(up));
                    stm = SEQ(stm, BLE(TEMP(index), limit, top, exit));
                    stm = SEQ(stm, LABEL(down));
                    stm = SEQ(stm, BGE(TEMP(index), limit, top, exit));
                }

                currentExit = oldExit;
                stm = SEQ(stm, LABEL(exit));
                Scope.Pop(zz);
                return new Exp.Nx(stm);
            }
            public Exp visit(Absyn.Stmt.If s) {
                Tree.Stm stm = null;
                Temp.Label exit = new Temp.Label();
                for (Absyn.Stmt.If.Clause c : s.clauses) {
                    if (c.expr != null) {
                        Exp test = Compile(c.expr);
                        Temp.Label current = new Temp.Label();
                        Temp.Label next = new Temp.Label();
                        stm = SEQ(stm, test.unCx(current, next));
                        stm = SEQ(stm, LABEL(current));
                        for (Absyn.Stmt stmt : c.stmts) {
                            stm = SEQ(stm, Compile(stmt).unNx());
                        }
                        stm = SEQ(stm, JUMP(exit));
                        stm = SEQ(stm, LABEL(next));
                    } else {
                        for (Absyn.Stmt stmt : c.stmts) {
                            stm = SEQ(stm, Compile(stmt).unNx());
                        }
                    }
                }
                stm = SEQ(stm, LABEL(exit));
                return new Exp.Nx(stm);
            }
            public Exp visit(Absyn.Stmt.If.Clause c) {
                assert false;
                return null;
            }
            public Exp visit(Absyn.Stmt.Loop s) {
                Tree.Stm stm = null;
                Temp.Label top = new Temp.Label();
                Temp.Label exit = new Temp.Label();
                Temp.Label oldExit = currentExit;
                currentExit = exit;
                for (Absyn.Stmt stmt : s.stmts) {
                    stm = SEQ(stm, Compile(stmt).unNx());
                }
                currentExit = oldExit;
                stm = SEQ(LABEL(top), stm, JUMP(top), LABEL(exit));
                return new Exp.Nx(stm);
            }
            public Exp visit(Absyn.Stmt.Repeat s) {
                Tree.Stm stm = null;
                Temp.Label top = new Temp.Label();
                Temp.Label exit = new Temp.Label();
                Temp.Label oldExit = currentExit;
                currentExit = exit;
                for (Absyn.Stmt stmt : s.stmts) {
                    stm = SEQ(stm, Compile(stmt).unNx());
                }
                currentExit = oldExit;
                stm = SEQ(LABEL(top), stm, Compile(s.expr).unCx(exit, top), LABEL(exit));
                return new Exp.Nx(stm);
            }
            public Exp visit(Absyn.Stmt.Return s) {
                if (s.expr == null)
                    return new Exp.Nx(JUMP(returnLabel));
                Tree.Stm stm = SEQ(MOVE(frames.get(currentBody).RV(), Compile(s.expr).unEx()), JUMP(returnLabel));
                return new Exp.Nx(stm);
            }
            public Exp visit(Absyn.Stmt.While s) {
                Tree.Stm stm = null;
                Temp.Label test = new Temp.Label();
                Temp.Label body = new Temp.Label();
                Temp.Label exit = new Temp.Label();
                Temp.Label oldExit = currentExit;
                currentExit = exit;
                for (Absyn.Stmt stmt : s.stmts) {
                    stm = SEQ(stm, Compile(stmt).unNx());
                }
                currentExit = oldExit;
                stm = SEQ(LABEL(test), Compile(s.expr).unCx(body, exit), LABEL(body), stm, JUMP(test), LABEL(exit));
                return new Exp.Nx(stm);
            }
            public Exp visit(Absyn.Stmt.Block s) {
                Tree.Stm stm = null;
                Scope zz = Scope.Push(s.scope);
                EnterScope(s.scope);
                stm = SEQ(stm, InitValues(s.scope));
                for (Absyn.Stmt stmt : s.stmts) {
                    stm = SEQ(stm, Compile(stmt).unNx());
                }
                Scope.Pop(zz);
                return new Exp.Nx(stm);
            }
        }
        if (s == null) return null;
        return s.accept(new Visitor());
    }

    static HashMap<Absyn.Expr,Temp> temps = new HashMap<Absyn.Expr,Temp>();
    static Temp PassObject(Absyn.Expr e) { return temps.get(e); }
    static Value.Procedure IsProcedureLiteral(Absyn.Expr e) {
        if (e == null) return null;
        Value v;
        if ((v = NamedExpr.Split(e)) == null && (v = QualifyExpr.Split(e)) == null)
            return null;
        return Value.Procedure.Is(v);
    }
    static Exp Compile(Absyn.Expr e) {
        class Visitor implements Absyn.Expr.Visitor<Exp> {
            @Override
            public Exp visit(Absyn.Expr.Call ce) {
                Absyn.Expr proc = ce.proc;
                assert proc.checked;
                Type p_type = proc.type;
                if (p_type == Type.FIRST || p_type == Type.LAST || p_type == Type.NUMBER) {
                    Iterator<Absyn.Expr> args = ce.actuals.iterator();
                    Absyn.Expr arg = args.next();
                    assert arg.checked;
                    Type t = arg.type;
                    if (Type.OpenArray.Is(t) != null) {
                        if (p_type == Type.FIRST) return new Exp.Ex(CONST(0));
                        Tree.Exp exp = Compile(arg).unEx();
                        Tree.Exp.MEM mem = (Tree.Exp.MEM)exp;
                        Tree.Exp number = MEM(mem.exp, mem.offset.value-target.wordSize());
                        if (p_type == Type.LAST)
                            return new Exp.Ex(SUB(number, CONST(1)));
                        assert p_type == Type.NUMBER;
                        return new Exp.Ex(number);
                    }
                    t = TypeExpr.Split(proc);
                    if (Type.Enum.Is(t) != null) {
                        if (p_type == Type.FIRST) return new Exp.Ex(CONST(0));
                        int number = Type.Number(t);
                        if (p_type == Type.LAST) return new Exp.Ex(CONST(number-1));
                        assert p_type == Type.NUMBER;
                        return new Exp.Ex(CONST(number));
                    }
                    assert t == Type.INTEGER;
                    assert target.wordSize() == Integer.SIZE / 8;
                    if (p_type == Type.FIRST) return new Exp.Ex(CONST(Integer.MIN_VALUE));
                    if (p_type == Type.LAST) return new Exp.Ex(CONST(Integer.MAX_VALUE));
                    assert p_type == Type.NUMBER;
                    return new Exp.Ex(CONST(Type.Number(t)));
                } else if (p_type == Type.ORD) {
                    Iterator<Absyn.Expr> args = ce.actuals.iterator();
                    return Compile(args.next());
                } else if (p_type == Type.VAL) {
                    Iterator<Absyn.Expr> args = ce.actuals.iterator();
                    Exp exp = Compile(args.next());
                    Type t = TypeExpr.Split(args.next());
                    if (Type.Enum.Is(t) != null) {
                        Temp.Label badSub = target.badSub();
                        Temp.Label okLo = new Temp.Label();
                        Temp.Label okHi = new Temp.Label();
                        Temp i = new Temp();
                        Tree.Stm loCheck = null, hiCheck = null;
                        if (badSub != null) {
                            Tree.Exp number = CONST(Type.Number(t));
                            loCheck = SEQ(BLT(TEMP(i), CONST(0), badSub, okLo), LABEL(okLo));
                            hiCheck = SEQ(BGE(TEMP(i), number, badSub, okHi), LABEL(okHi));
                        }
                        exp = new Exp.Ex(ESEQ(SEQ(MOVE(TEMP(i), exp.unEx()), loCheck, hiCheck), TEMP(i)));
                    }
                    return exp;
                } else if (p_type == Type.NEW) {
                    Iterator<Absyn.Expr> args = ce.actuals.iterator();
                    Absyn.Expr e = args.next();
                    Type t = TypeExpr.Split(e);
                    assert t != null;
                    t = Type.Check(t);
                    Compile(t);
                    Type.Ref ref = Type.Ref.Is(t);
                    if (ref != null) {
                        Type r = ref.target;
                        r = Type.Check(r);
                        if (Type.OpenArray.Is(r) != null) {
                            int n = ce.actuals.size() - 1;
                            if (n > 1) {
                                Semant.error(ce, "multi-dimensional open arrays not implemented");
                            }
                            Tree.Exp exp = Compile(args.next()).unEx();
                            Temp size = new Temp();
                            return new Exp.Ex(ESEQ(MOVE(TEMP(size), exp),
                                    CALL(NAME(Temp.getLabel("new")),
                                            Exps(MUL(TEMP(size), CONST(target.wordSize())), TEMP(size)))));
                        }
                        // simple scalar
                        return new Exp.Ex(CALL(NAME(Temp.getLabel("new")), Exps(CONST(target.wordSize()), CONST(1))));
                    }
                    Type.Object object = Type.Object.Is(t);
                    if (object != null) {
                        int size = object.fieldSize * target.wordSize();
                        Label vtable = Temp.getLabel(Type.GlobalUID(t));
                        assert vtable != null;
                        return new Exp.Ex(CALL(NAME(Temp.getLabel("new")), Exps(CONST(size), NAME(vtable))));
                    }
                    assert false;
                    return null;
                }

                if (p_type == null) p_type = QualifyExpr.MethodType(proc);
                // grab the formals list
                Scope formals = Type.Proc.Is(p_type).formals;
                LinkedList<Tree.Exp>args = new LinkedList<Tree.Exp>();
                Value.Procedure p_value = IsProcedureLiteral(proc);
                Tree.Exp fp = CONST(0);
                if (p_value != null) {
                    ProcBody caller = currentBody;
                    ProcBody callee = p_value.body;
                    if (callee != null && callee.parent != null) {
                        fp = target.FP();
                        for (ProcBody body = caller; body != callee.parent; body = body.parent)
                            fp = frames.get(body).link.exp(fp);
                    }
                }
                Tree.Exp exp = Compile(proc).unEx();
                Temp t = PassObject(proc);
                if (t != null) args.add(TEMP(t));
                Iterator<Absyn.Expr> actuals = ce.actuals.iterator();
                for (Value f : Scope.ToList(formals)) {
                    Value.Formal formal = Value.Formal.Is(f);
                    switch (formal.mode) {
                    case VALUE: {
                        Tree.Exp actual = Compile(actuals.next()).unEx();
                        if (Type.IsStructured(formal.type)) {
                            // we need to make a copy in the callee
                            Tree.Exp.MEM mem = (Tree.Exp.MEM)actual;
                            actual = ADD(mem.exp, mem.offset);
                        }
                        args.add(actual);
                        break;
                    }
                    case VAR: {
                        Tree.Exp actual = Compile(actuals.next()).unEx();
                        Tree.Exp.MEM mem  = (Tree.Exp.MEM)actual;
                        args.add(ADD(mem.exp, mem.offset));
                        break;
                    }
                    case READONLY: {
                        Tree.Exp actual = Compile(actuals.next()).unEx();
                        if (Type.IsStructured(formal.type)) {
                            Tree.Exp.MEM mem = (Tree.Exp.MEM)actual;
                            args.add(ADD(mem.exp, mem.offset));
                        } else if (actual instanceof Tree.Exp.MEM) {
                            Tree.Exp.MEM mem = (Tree.Exp.MEM)actual;
                            args.add(ADD(mem.exp, mem.offset));
                        } else {
                            Frame frame = frames.get(currentBody);
                            Frame.Access access = frame.allocLocal(null);
                            Tree.Exp.MEM mem = (Tree.Exp.MEM)access.exp(frame.FP());
                            args.add(ESEQ(MOVE(access.exp(frame.FP()), actual), ADD(mem.exp, mem.offset)));
                        }
                        break;
                    }
                    default:
                        assert false;
                        return null;
                    }
                }
                return new Exp.Ex(CALL(exp, fp, args.toArray(new Tree.Exp[args.size()])));
            }
            @Override
            public Exp visit(Absyn.Expr.Or e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.IfThenElseExp(l, new Exp.Ex(CONST(1)), r);
            }
            @Override
            public Exp visit(Absyn.Expr.And e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.IfThenElseExp(l, r, new Exp.Ex(CONST(0)));
            }
            @Override
            public Exp visit(Absyn.Expr.Not e) {
                Exp exp = Compile(e.expr);
                return new Exp.Cx.IfThenElseExp(exp, new Exp.Ex(CONST(0)), new Exp.Ex(CONST(1)));
            }
            @Override
            public Exp visit(Absyn.Expr.Lt e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.Rel(Tree.Stm.CJUMP.Operator.BLT, l.unEx(), r.unEx());
            }
            @Override
            public Exp visit(Absyn.Expr.Gt e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.Rel(Tree.Stm.CJUMP.Operator.BGT, l.unEx(), r.unEx());
            }
            @Override
            public Exp visit(Absyn.Expr.Le e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.Rel(Tree.Stm.CJUMP.Operator.BLE, l.unEx(), r.unEx());
            }
            @Override
            public Exp visit(Absyn.Expr.Ge e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.Rel(Tree.Stm.CJUMP.Operator.BGE, l.unEx(), r.unEx());
            }
            @Override
            public Exp visit(Absyn.Expr.Ne e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.Rel(Tree.Stm.CJUMP.Operator.BNE, l.unEx(), r.unEx());
            }
            @Override
            public Exp visit(Absyn.Expr.Eq e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Cx.Rel(Tree.Stm.CJUMP.Operator.BEQ, l.unEx(), r.unEx());
            }
            @Override
            public Exp visit(Absyn.Expr.Add e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Ex(ADD(l.unEx(), r.unEx()));
            }
            @Override
            public Exp visit(Absyn.Expr.Subtract e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Ex(SUB(l.unEx(), r.unEx()));
            }
            @Override
            public Exp visit(Absyn.Expr.Multiply e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Ex(MUL(l.unEx(), r.unEx()));
            }
            @Override
            public Exp visit(Absyn.Expr.Div e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Ex(DIV(l.unEx(), r.unEx()));
            }
            @Override
            public Exp visit(Absyn.Expr.Mod e) {
                Exp l = Compile(e.left);
                Exp r = Compile(e.right);
                return new Exp.Ex(MOD(l.unEx(), r.unEx()));
            }
            @Override
            public Exp visit(Absyn.Expr.Plus e) {
                return Compile(e.expr);
            }
            @Override
            public Exp visit(Absyn.Expr.Minus e) {
                Exp exp = Compile(e.expr);
                return new Exp.Ex(SUB(CONST(0), exp.unEx()));
            }
            @Override
            public Exp visit(Absyn.Expr.Deref e) {
                Tree.Exp exp = Compile(e.expr).unEx();
                Temp.Label badPtr = target.badPtr();
                Temp a = new Temp();
                Tree.Stm nullCheck = null;
                if (badPtr != null) {
                    Temp.Label okPtr = new Temp.Label();
                    nullCheck = SEQ(BEQ(TEMP(a), CONST(0), badPtr, okPtr), LABEL(okPtr));
                }
                exp = MEM(ESEQ(SEQ(MOVE(TEMP(a), exp), nullCheck), TEMP(a)));
                return new Exp.Ex(exp);
            }
            @Override
            public Exp visit(Absyn.Expr.Qualify e) {
                assert e.expr.checked;
                Type t = e.expr.type;
                Value v = QualifyExpr.Split(e);
                assert v != null;
                if (t == null) {
                    // a module or type
                    Value.Method m;
                    if ((m = Value.Method.Is(v)) != null) {
                        Compile(m.parent);
                        return new Exp.Ex(MEM(NAME(Temp.getLabel(Type.GlobalUID(m.parent))), m.offset * target.wordSize()));
                    }
                }
                if (Type.Object.Is(t) != null) {
                    Value.Method m;
                    if ((m = Value.Method.Is(v)) != null) {
                        Tree.Exp exp = Compile(e.expr).unEx();
                        Temp temp = new Temp();
                        temps.put(e, temp);
                        return new Exp.Ex(ESEQ(MOVE(TEMP(temp), exp), MEM(MEM(TEMP(temp), -target.wordSize()), m.offset * target.wordSize())));
                    }
                    Value.Field f;
                    if ((f = Value.Field.Is(v)) != null) {
                        Tree.Exp exp = Compile(e.expr).unEx();
                        Temp.Label badPtr = target.badPtr();
                        Temp temp = new Temp();
                        Tree.Stm nullCheck = null;
                        if (badPtr != null) {
                            Temp.Label okPtr = new Temp.Label();
                            nullCheck = SEQ(BEQ(TEMP(temp), CONST(0), badPtr, okPtr), LABEL(okPtr));
                        }
                        return new Exp.Ex(ESEQ(SEQ(MOVE(TEMP(temp), exp), nullCheck), MEM(TEMP(temp), f.offset * target.wordSize())));
                    }
                }
                assert v != null;
                return new Exp.Ex(Load(v));
            }
            @Override
            public Exp visit(Absyn.Expr.Subscript e) {
                Tree.Exp.MEM mem = (Tree.Exp.MEM)Compile(e.expr).unEx();
                Tree.Exp index = Compile(e.index).unEx();
                Temp.Label badSub = target.badSub();
                Temp a = new Temp();
                Temp i = new Temp();
                Tree.Stm loCheck = null, hiCheck = null;
                if (badSub != null) {
                    Temp.Label okLo = new Temp.Label();
                    Temp.Label okHi = new Temp.Label();
                    Tree.Exp length = MEM(TEMP(a), -target.wordSize());
                    loCheck = SEQ(BLT(TEMP(i), CONST(0), badSub, okLo), LABEL(okLo));
                    hiCheck = SEQ(BGE(TEMP(i), length, badSub, okHi), LABEL(okHi));
                }
                Tree.Exp exp =
                    MEM(ESEQ(SEQ(MOVE(TEMP(a), ADD(mem.exp, mem.offset)), MOVE(TEMP(i), index), loCheck, hiCheck),
                            ADD(TEMP(a), MUL(TEMP(i), CONST(target.wordSize())))));
                return new Exp.Ex(exp);
            }
            @Override
            public Exp visit(Absyn.Expr.Named e) {
                Value v = NamedExpr.Split(e);
                return new Exp.Ex(Load(v));
            }
            @Override
            public Exp visit(Absyn.Expr.Number e) {
                String[] split = e.token.image.split("_");
                if (split.length == 1)
                    return new Exp.Ex(CONST(Integer.parseInt(split[0])));
                else {
                    assert split.length == 2;
                    int radix = Integer.parseInt(split[0]);
                    return new Exp.Ex(CONST(Integer.parseInt(split[1], radix)));
                }
            }
            @Override
            public Exp visit(Absyn.Expr.Char e) {
                return new Exp.Ex(CONST(mapChar(e.token.image)));
            }
            @Override
            public Exp visit(Absyn.Expr.Text e) {
                return new Exp.Ex(NAME(stringLabel(mapString(e.token.image))));
            }
            @Override
            public Exp visit(Absyn.Expr.TypeExpr e) { assert false; return null; }
        }
        if (e == null) return null;
        assert e.checked;
        return e.accept(new Visitor());
    }

    static char mapChar(String s) {
        String[] split = s.split("\'");
        assert split.length == 2;
        assert split[0].length() == 0;
        s = split[1];
        int i = 0;
        char c = s.charAt(i++);
        if (c == '\\')
            switch (c = s.charAt(i++)) {
            case 'b':
                return '\b';
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'f':
                return '\f';
            case 'r':
                return '\r';
            case '\'':
                return '\'';
            case '"':
                return '\"';
            case '\\':
                return '\\';
            default:
                int value = c - '0';
                c = s.charAt(i);
                if (c >= '0' && c <= '7') {
                    i++;
                    value <<= 3;
                    value += c - '0';
                    c = s.charAt(i);
                    if (c >= '0' && c <= '7') {
                        i++;
                        value <<= 3;
                        value += c - '0';
                    }
                }
                return (char)value;
            }
        else
            return c;
    }

    static HashMap<String,Temp.Label> strings = new HashMap<String,Temp.Label>();
    static String mapString(String s) {
        String result = "";
        int i = 0;
        char c;
        if ((c = s.charAt(i++)) != '"')
            throw new Error();
        while ((c = s.charAt(i++)) != '"') {
            if (c == '\\')
                switch (c = s.charAt(i++)) {
                case 'b':
                    result += '\b';
                    break;
                case 't':
                    result += '\t';
                    break;
                case 'n':
                    result += '\n';
                    break;
                case 'f':
                    result += '\f';
                    break;
                case 'r':
                    result += '\r';
                    break;
                case '\'':
                    result += '\'';
                    break;
                case '"':
                    result += '\"';
                    break;
                case '\\':
                    result += '\\';
                    break;
                default:
                    int value = c - '0';
                    c = s.charAt(i);
                    if (c >= '0' && c <= '7') {
                        i++;
                        value <<= 3;
                        value += c - '0';
                        c = s.charAt(i);
                        if (c >= '0' && c <= '7') {
                            i++;
                            value <<= 3;
                            value += c - '0';
                        }
                    }
                    result += (char) value;
                    break;
                }
            else
                result += c;
        }
        return result;
    }

    static Temp.Label stringLabel(String s) {
        Temp.Label l = strings.get(s);
        if (l != null)
            return l;
        l = new Temp.Label();
        strings.put(s, l);
        frags.add(new Frag.Data(target.string(l, s)));
        return l;
    }
}
