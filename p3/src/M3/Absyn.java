package M3;

import java.util.*;

public abstract class Absyn {
    private static void usage() {
        throw new Error("Usage: java M3.Absyn <source>");
    }
    public static void main (String[] args) {
        if (args.length != 1) usage();
	try {
	    java.io.InputStream stream = new java.io.FileInputStream(args[0]);
	    Parser parser = new Parser(stream);
            Decl.Module unit = parser.Unit();
            new Print(unit);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } catch (TokenMgrError e) {
            System.err.println(e.getMessage());
        }
    }

    public final Token token;

    private Absyn(Token t) { token = t; }

    public int line() {	return token.beginLine; }

    public int column() { return token.beginColumn; }

    interface Visitor<R> extends Expr.Visitor<R>, Decl.Visitor<R>, Type.Visitor<R>, Stmt.Visitor<R> {}

    public static abstract class Stmt extends Absyn {
	private Stmt(Token t) {
	    super(t);
	}
	interface Visitor<R> {
	    R visit(Assign s);
	    R visit(Call s);
	    R visit(Exit s);
	    R visit(Eval s);
	    R visit(For s);
	    R visit(If s);
	    R visit(If.Clause s);
	    R visit(Loop s);
	    R visit(Repeat s);
	    R visit(Return s);
	    R visit(While s);
	    R visit(Block s);
	}
        abstract <R> R accept(Visitor<R> v);

	/**
	 * AssignSt = Expr ":=" Expr
	 */
	public static class Assign extends Stmt {
	    public final Expr lhs, rhs;
	    public Assign(Token t, Expr lhs, Expr rhs) {
		super(t);
		this.lhs = lhs;
		this.rhs = rhs;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * CallSt = Expr "(" [ Actual { "," Actual } ] ")" 
	 */
	public static class Call extends Stmt {
	    public final Expr.Call expr;
	    public Call(Token t, Expr.Call expr) {
		super(t);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * ExitSt = EXIT
	 */
	public static class Exit extends Stmt {
	    public Exit(Token t) {
		super(t);
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * EvalSt = EVAL Expr
	 */
	public static class Eval extends Stmt {
	    public final Expr expr;
	    public Eval(Token t, Expr expr) {
		super(t);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * ForSt = FOR Id ":=" Expr TO Expr [ BY Expr ] DO S END
	 */
	public static class For extends Stmt {
	    public final Decl.Variable var;
	    public final Expr from, to, by;
	    public final List<Stmt> stmts;
	    public For(Token t, Decl.Variable var, Expr from, Expr to, Expr by,
		       List<Stmt> stmts)
	    {
		super(t);
		this.var = var;
		this.from = from;
		this.to = to;
		this.by = by;
		this.stmts = stmts;
	    }
	    M3.Scope scope;
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * IfSt = IF Expr THEN S { ELSIF Expr THEN S } [ ELSE S ] END 
	 */
	public static class If extends Stmt {
	    public final List<Clause> clauses;
	    public If(List<Clause> clauses) {
		super(null);
		this.clauses = clauses;
	    }
	    public static class Clause extends Stmt {
		public final Expr expr;
		public final List<Stmt> stmts;
		public Clause(Token t, Expr expr, List<Stmt> stmts) {
		    super(t);
		    this.expr = expr;
		    this.stmts = stmts;
		}
		<R> R accept(Visitor<R> v) { return v.visit(this); }
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * LoopSt = LOOP S END
	 */
	public static class Loop extends Stmt {
	    public final List<Stmt> stmts;
	    public Loop(Token t, List<Stmt> stmts) {
		super(t);
		this.stmts = stmts;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * RepeatSt = REPEAT S UNTIL Expr
	 */
	public static class Repeat extends Stmt {
	    public final List<Stmt> stmts;
	    public final Expr expr;
	    public Repeat(Token t, List<Stmt> stmts, Expr expr) {
		super(t);
		this.stmts = stmts;
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * ReturnSt = RETURN [ Expr ]
	 */
	public static class Return extends Stmt {
	    public final Expr expr;
	    public Return(Token t, Expr expr) {
		super(t);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * WhileSt = WHILE Expr DO S END
	 */
	public static class While extends Stmt {
	    public final Expr expr;
	    public final List<Stmt> stmts;
	    public While(Token t, Expr expr, List<Stmt> stmts) {
		super(t);
		this.expr = expr;
		this.stmts = stmts;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Block = { Decl } BEGIN S END
	 */
	public static class Block extends Stmt {
	    public final List<Decl> decls;
	    public final List<Stmt> stmts;
	    public Block(List<Decl> decls, List<Stmt> stmts) {
		super(null);
		this.decls = decls;
		this.stmts = stmts;
	    }
	    M3.Scope scope;
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
    }

    public static abstract class Type extends Absyn {
        private Type(Token t) {
	    super(t);
	}
        interface Visitor<R> {
            R visit(Array t);
            R visit(Named t);
            R visit(Object t);
            R visit(Proc t);
            R visit(Ref t);
        }
        abstract <R> R accept(Visitor<R> v);

        /**
         * My type descriptor.
         */
        M3.Type type;

        /**
         * ArrayType = ARRAY OF Type
         */
	public static class Array extends Type {
	    public final Type element;
	    public Array(Token t, Type element) {
		super(t);
		this.element = element;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * TypeName = Id | ROOT
	 */
	public static class Named extends Type {
	    final String name;
	    public Named(Token t) {
		super(t);
		name = t.image;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * ObjectType = [TypeName | ObjectType] OBJECT Fields [METHODS Methods] [OVERRIDES Overrides] END
	 */
	public static class Object extends Type {
	    public final Type parent;
	    public final List<Decl.Field> fields;
	    public final List<Decl.Method> methods;
	    public final List<Decl.Method> overrides;
	    public Object (Token t, Type parent,
			   List<Decl.Field> fields,
			   List<Decl.Method> methods,
			   List<Decl.Method> overrides) {
		super(t);
		this.parent = parent;
		this.fields = fields;
		this.methods = methods;
		this.overrides = overrides;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Signature = "(" Formals ")" [ ":" Type ]
	 */
	public static class Proc extends Type {
	    public final List<Decl.Formal> formals;
	    public final Type result;
	    public Proc(Token t, List<Decl.Formal> formals, Type result) {
		super(t);
		this.formals = formals;
		this.result = result;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * RefType = REF Type
	 */
	public static class Ref extends Type {
	    public final Type target;
	    public Ref(Token t, Type target) {
		super(t);
		this.target = target;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
    }

    public static abstract class Decl extends Absyn {
        final String name;
        /**
         * @param t the symbol (Id) being declared
         */
	private Decl(Token t) {
	    super(t);
	    this.name = t.image;
	}
        interface Visitor<R> {
            R visit(Field d);
            R visit(Formal d);
            R visit(Method d);
            R visit(Module d);
            R visit(Procedure d);
            R visit(Tipe d);
            R visit(Variable d);
        }
        abstract <R> R accept(Visitor<R> v);

	/**
	 * Field = Id ":" Type
	 */
	public static class Field extends Decl {
	    public final Type type;
	    public Field(Token id, Type type) {
		super(id);
		this.type = type;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Formal = [ Mode ] Id ":" Type
	 */
	public static class Formal extends Decl {
	    public static enum Mode { VALUE, VAR, READONLY; }
	    public final Mode mode;
	    public final Type type;
	    public Formal(Mode mode, Token id, Type type) {
		super(id);
		this.mode = mode;
		this.type = type;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Method = Id Signature [ ":=" ConstExpr ]
	 */
	public static class Method extends Decl {
	    public final Type.Proc type;
	    public final Expr expr;
	    public Method(Token id, Type.Proc type, Expr expr) {
		super(id);
		this.type = type;
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Interface = INTERFACE Id ";" { Decl } END Id.
	 * Module = MODULE Id ";" Block Id "."
	 */
	public static class Module extends Decl {
	    public final List<Decl> decls;
	    public final List<Stmt> stmts; // null => INTERFACE
            public final List<Decl> exports = null;
            public final List<Decl> imports = new LinkedList<Decl>();
            public final List<Decl> fromImports = new LinkedList<Decl>();
	    public Module(Token id, List<Decl> decls, List<Stmt> stmts) {
		super(id);
		this.decls = decls;
		this.stmts = stmts;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * ProcedureDecl = PROCEDURE Id Signature [ "=" Block Id ] ";"
	 */
	public static class Procedure extends Decl {
	    public final Type.Proc type;
	    public final List<Decl> decls;
	    public final List<Stmt> stmts;
	    public final Token external;
	    public Procedure(Token id, Type.Proc type,
			     List<Decl> decls, List<Stmt> stmts,
			     Token external) {
		super(id);
		this.type = type;
		this.decls = decls;
		this.stmts = stmts;
		this.external = external;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * TypeDecl = TYPE Id "=" Type
	 */
	public static class Tipe extends Decl {
	    public final Type value;
	    public Tipe(Token id, Type type) {
		super(id);
		this.value = type;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * VarDecl = VAR Id ( ":" Type & ":=" Expr )
	 */
	public static class Variable extends Decl {
	    public final Type type;
	    public final Expr expr;
	    public final Token external;
	    public Variable(Token id, Type type, Expr expr, Token external) {
		super(id);
		this.type = type;
		this.expr = expr;
		this.external = external;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
    }

    public static abstract class Expr extends Absyn {
	public final int precedence;
	protected Expr(Token t, int precedence) {
	    super(t);
	    this.precedence = precedence;
	}
	interface Visitor<R> {
            R visit(Add e);
	    R visit(And e);
            R visit(Call e);
            R visit(Char e);
            R visit(Deref e);
            R visit(Div e);
            R visit(Eq e);
            R visit(Ge e);
            R visit(Gt e);
            R visit(Le e);
            R visit(Lt e);
            R visit(Minus e);
            R visit(Mod e);
            R visit(Multiply e);
            R visit(Named e);
            R visit(Ne e);
            R visit(Not e);
            R visit(Number e);
            R visit(Or e);
            R visit(Plus e);
            R visit(Qualify e);
            R visit(Subscript e);
            R visit(Subtract e);
            R visit(Text e);
            R visit(TypeExpr e);
	}
	abstract <R> R accept(Visitor<R> v);

	boolean checked = false;
	/**
	 * My checked type.
	 */
        M3.Type type;

	/**
	 * Expr = Expr OR Expr
	 */
	public static class Or extends Expr {
	    public final Expr left, right;
	    public Or(Token t, Expr left, Expr right) {
		super(t, 0);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Expr AND Expr
	 */
	public static class And extends Expr {
	    public final Expr left, right;
	    public And(Token t, Expr left, Expr right) {
		super(t, 1);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = NOT Expr
	 */
	public static class Not extends Expr {
	    public final Expr expr;
	    public Not(Token t, Expr expr) {
		super(t, 2);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Expr "<" Expr
	 */
        public static class Lt extends Expr {
            public final Expr left, right;
            public Lt(Token t, Expr left, Expr right) {
                super(t, 3);
                this.left = left;
                this.right = right;
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = Expr ">" Expr
         */
        public static class Gt extends Expr {
            public final Expr left, right;
            public Gt(Token t, Expr left, Expr right) {
                super(t, 3);
                this.left = left;
                this.right = right;
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = Expr "<=" Expr
         */
        public static class Le extends Expr {
            public final Expr left, right;
            public Le(Token t, Expr left, Expr right) {
                super(t, 3);
                this.left = left;
                this.right = right;
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = Expr ">=" Expr
         */
        public static class Ge extends Expr {
            public final Expr left, right;
            public Ge(Token t, Expr left, Expr right) {
                super(t, 3);
                this.left = left;
                this.right = right;
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = Expr "=" Expr
         */
        public static class Eq extends Expr {
            public final Expr left, right;
            public Eq(Token t, Expr left, Expr right) {
                super(t, 3);
                this.left = left;
                this.right = right;
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = Expr "#" Expr
         */
        public static class Ne extends Expr {
            public final Expr left, right;
            public Ne(Token t, Expr left, Expr right) {
                super(t, 3);
                this.left = left;
                this.right = right;
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = Expr "+" Expr
         */
	public static class Add extends Expr {
	    public final Expr left, right;
	    public Add(Token t, Expr left, Expr right) {
		super(t, 4);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = Expr "-" Expr
         */
	public static class Subtract extends Expr {
	    public final Expr left, right;
	    public Subtract(Token t, Expr left, Expr right) {
		super(t, 4);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = Expr "*" Expr
         */
	public static class Multiply extends Expr {
	    public final Expr left, right;
	    public Multiply(Token t, Expr left, Expr right) {
		super(t, 5);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = Expr DIV Expr
         */
	public static class Div extends Expr {
	    public final Expr left, right;
	    public Div(Token t, Expr left, Expr right) {
		super(t, 5);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = Expr MOD Expr
         */
	public static class Mod extends Expr {
	    public final Expr left, right;
	    public Mod(Token t, Expr left, Expr right) {
		super(t, 5);
		this.left = left;
		this.right = right;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = "+" Expr
         */
	public static class Plus extends Expr {
	    public final Expr expr;
	    public Plus(Token t, Expr expr) {
		super(t, 6);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = "-" Expr
         */
	public static class Minus extends Expr {
	    public final Expr expr;
	    public Minus(Token t, Expr expr) {
		super(t, 6);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
        /**
         * Expr = Expr "^"
         */
	public static class Deref extends Expr {
	    public final Expr expr;
	    public Deref(Token t, Expr expr) {
		super(t, 7);
		this.expr = expr;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Expr "." Id
	 */
	public static class Qualify extends Expr {
	    public Expr expr; // not final to allow for auto-magic dereference
	    final String name;
	    public Qualify(Token t, Expr expr, Token name) {
		super(t, 7);
		this.expr = expr;
		this.name = name.image;
	    }
	    M3.Value value;
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Expr "[" Expr "]"
	 */
	public static class Subscript extends Expr {
	    public Expr expr; // not final to allow for auto-magic dereference
	    public final Expr index;
	    public Subscript(Token t, Expr expr, Expr index) {
		super(t, 7);
		this.expr = expr;
		this.index = index;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Expr "(" [ Actual { "," Actual } ] ")"
	 */
	public static class Call extends Expr {
	    public final Expr proc;
	    public final List<Expr> actuals;
	    public Call(Token t, Expr proc, List<Expr> actuals) {
		super(t, 7);
		this.proc = proc;
		this.actuals = actuals;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Id
	 */
	public static class Named extends Expr {
	    final String name;
	    public Named(Token t) {
		super(t, 8);
		name = t.image;
	    }
	    M3.Scope scope;
	    M3.Value value;
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = Number
	 */
	public static class Number extends Expr {
	    public Number(Token t) {
		super(t, 8);
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Expr = CharLiteral
	 */
        public static class Char extends Expr {
            public Char(Token t) {
                super(t, 8);
            }
            <R> R accept(Visitor<R> v) { return v.visit(this); }
        }
        /**
         * Expr = TextLiteral
         */
	public static class Text extends Expr {
	    public Text(Token t) {
		super(t, 8);
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
	/**
	 * Actual = Type 
	 */
	public static class TypeExpr extends Expr {
	    public final Type value;
	    public TypeExpr(Type type) {
		super(type.token, 8);
		this.value = type;
	    }
	    <R> R accept(Visitor<R> v) { return v.visit(this); }
	}
    }

    public static class Print implements Visitor<Void> {
        int i = 0;
        public Print(Decl.Module absyn) {
            absyn.accept(this);
            System.out.flush();
        }
        void print(Decl d, int i) {
            int save = this.i;
            this.i = i;
            d.accept(this);
            this.i = save;
        }
        void print(Stmt s, int i) {
            int save = this.i;
            this.i = i;
            s.accept(this);
            this.i = save;
        }
        void print(Type t, int i) {
            int save = this.i;
            this.i = i;
            t.accept(this);
            this.i = save;
        }
        void print(Expr e, int i) {
            int save = this.i;
            this.i = i;
            e.accept(this);
            this.i = save;
        }
        @Override
        public Void visit(Decl.Module d) {
	    indent(i);
	    if (d.stmts == null) say("INTERFACE "); else say("MODULE ");
	    sayln(d.token + ";");
	    for (Decl decl: d.decls)
		print(decl, i);
	    if (d.stmts != null) {
	        indent(i); sayln("BEGIN");
	        for (Stmt stmt: d.stmts)
	            print(stmt, i+2);
	    }
	    indent(i); sayln("END " + d.token + ".");
	    return null;
	}
        @Override
        public Void visit(Stmt.Block s) {
	    for (Decl decl: s.decls)
		print(decl, i);
	    indent(i); sayln("BEGIN");
	    for (Stmt stmt: s.stmts)
	        print(stmt, i+2);
	    indent(i); sayln("END;");
	    return null;
	}
        @Override
	public Void visit(Decl.Tipe d) {
	    indent(i); say("TYPE " + d.token + " = ");
	    print(d.value, i+2);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Decl.Variable d) {
	    indent(i);
	    if (d.external != null) {
	        say("<*EXTERNAL "); say(d.external); say("*>");
	    }
	    say("VAR " + d.token);
	    if (d.type != null) {
		say(": ");
		print(d.type, i+2);
	    }
	    if (d.expr != null) {
		say(" := ");
		print(d.expr, i+2);
	    }
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Decl.Procedure d) {
	    indent(i);
	    if (d.external != null) {
	        say("<*EXTERNAL "); say(d.external); say("*>");
	    }
	    say("PROCEDURE " + d.token);
	    print(d.type, i+2);
	    if (d.decls != null || d.stmts != null) {
	        sayln(" =");
	        for (Decl decl: d.decls)
	            print(decl, i+2);
                indent(i+2); sayln("BEGIN");
                for (Stmt stmt: d.stmts)
                    print(stmt, i+4);
                indent(i+2); say("END "); say(d.token);
	    }
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Type.Proc t) {
	    if (t.formals.isEmpty()) {
		say(t.token); say(")");
	    } else {
		sayln(t.token);
		for (Decl.Formal f: t.formals)
		    print(f, i+2);
		indent(i); say(")");
	    }
	    if (t.result != null) {
		say(": ");
		print(t.result, i+2);
	    }
            return null;
	}
        @Override
	public Void visit(Decl.Formal d) {
	    indent(i);
	    switch (d.mode) {
	    case VALUE:
	        say("VALUE");
	        break;
	    case VAR:
	        say("VAR");
	        break;
	    case READONLY:
	        say("READONLY");
	        break;
	    }
	    say(d.token);
	    say(": ");
	    print(d.type, i+2);
	    sayln(";");
            return null;
	}
        @Override
        public Void visit(Stmt.Assign s) {
	    indent(i);
	    print(s.lhs, i+2);
	    say(" "); say(s.token); say(" ");
	    print(s.rhs, i+2);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Stmt.Call s) {
	    indent(i);
	    print(s.expr, i+2);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Stmt.Exit s) {
	    indent(i);
	    say(s.token);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Stmt.Eval s) {
	    indent(i);
	    say(s.token); say(" ");
	    print(s.expr, i+2);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Stmt.For s) {
	    indent(i);
	    say(s.token); say(" "); say(s.var.token); say(" := ");
	    print(s.from, i+2);
	    say(" TO ");
	    print(s.to, i+2);
	    if (s.by != null) {
		say(" BY ");
		print(s.by, i+2);
	    }
	    sayln(" DO");
	    for (Stmt stmt: s.stmts) {
		print(stmt, i+2);
	    }
	    indent(i); sayln("END;");
            return null;
	}
        @Override
	public Void visit(Stmt.If s) {
	    for (Stmt.If.Clause c : s.clauses)
		print(c, i);
	    indent(i); sayln("END;");
            return null;
	}
        @Override
	public Void visit(Stmt.If.Clause c) {
	    indent(i);
	    say(c.token); say(" ");
	    if (c.expr != null) {
		print(c.expr, i+2);
		sayln(" THEN");
	    } else {
		sayln("");
	    }
	    for (Stmt stmt: c.stmts)
		print(stmt, i+2);
            return null;
	}
        @Override
	public Void visit(Stmt.Loop s) {
	    indent(i);
	    sayln(s.token);
	    for (Stmt stmt: s.stmts)
		print(stmt, i+2);
	    indent(i); sayln("END;");
            return null;
	}
        @Override
	public Void visit(Stmt.Repeat s) {
	    indent(i);
	    sayln(s.token);
	    for (Stmt stmt: s.stmts)
		print(stmt, i+2);
	    indent(i); say("UNTIL"); print(s.expr, i+2);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Stmt.Return s) {
	    indent(i);
	    say(s.token); say(" ");
	    if (s.expr != null)
		print(s.expr, i+2);
	    sayln(";");
            return null;
	}
        @Override
	public Void visit(Stmt.While s) {
	    indent(i);
	    say(s.token + " ");
	    print(s.expr, i+2);
	    sayln(" DO");
	    for (Stmt stmt: s.stmts)
		print(stmt, i+2);
	    sayln("END;");
            return null;
	}
        @Override
	public Void visit(Type.Array t) {
	    say(t.token); say(" OF ");
	    print(t.element, i+2);
            return null;
	}
        @Override
	public Void visit(Type.Object t) {
	    if (t.parent != null) {
	        print(t.parent, i);
		say(" ");
	    }
	    sayln(t.token);
	    for (Decl.Field field: t.fields) {
		print(field, i+2);
	    }
	    if (!t.methods.isEmpty()) {
		indent(i); sayln("METHODS");
		for (Decl.Method method: t.methods)
		    print(method, i+2);
	    }
	    if (!t.overrides.isEmpty()) {
		indent(i); sayln("OVERRIDES");
		for (Decl.Method method: t.overrides)
		    print(method, i+2);
	    }
	    indent(i); say("END");
            return null;
	}
        @Override
	public Void visit(Type.Ref t) {
	    say(t.token); say(" ");
	    print(t.target, 0);
            return null;
	}
        @Override
	public Void visit(Type.Named t) {
	    say(t.token);
            return null;
	}
        @Override
	public Void visit(Decl.Field d) {
	    indent(i);
	    say(d.token + ": ");
	    print(d.type, 0);
	    sayln(";");
            return null;
	}
        @Override
        public Void visit(Decl.Method d) {
	    indent(i);
	    say(d.token);
	    if (d.type != null)
		print(d.type, i);
	    if (d.expr != null) {
		say(" := ");
		print(d.expr, i+2);
	    }
	    sayln(";");
            return null;
	}
	private void visit(Expr child, Expr parent) {
	    if (child.precedence <= parent.precedence) say("(");
	    print(child, i+2);
	    if (child.precedence <= parent.precedence) say(")");
	}
        @Override
	public Void visit(Expr.Or e) {
	    visit(e.left, e);
	    sayln("");
	    indent(i); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.And e) {
	    visit(e.left, e);
	    sayln("");
	    indent(i); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.Not e) {
	    say(e.token); say(" ");
	    visit(e.expr, e);
            return null;
	}
        @Override
        public Void visit(Expr.Lt e) {
            visit(e.left, e);
            say(" "); say(e.token); say(" ");
            visit(e.right, e);
            return null;
        }
        @Override
        public Void visit(Expr.Gt e) {
            visit(e.left, e);
            say(" "); say(e.token); say(" ");
            visit(e.right, e);
            return null;
        }
        @Override
        public Void visit(Expr.Le e) {
            visit(e.left, e);
            say(" "); say(e.token); say(" ");
            visit(e.right, e);
            return null;
        }
        @Override
        public Void visit(Expr.Ge e) {
            visit(e.left, e);
            say(" "); say(e.token); say(" ");
            visit(e.right, e);
            return null;
        }
        @Override
        public Void visit(Expr.Eq e) {
            visit(e.left, e);
            say(" "); say(e.token); say(" ");
            visit(e.right, e);
            return null;
        }
        @Override
        public Void visit(Expr.Ne e) {
            visit(e.left, e);
            say(" "); say(e.token); say(" ");
            visit(e.right, e);
            return null;
        }
        @Override
	public Void visit(Expr.Add e) {
	    visit(e.left, e);
	    say(" "); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.Subtract e) {
	    visit(e.left, e);
	    say(" "); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.Multiply e) {
	    visit(e.left, e);
	    say(" "); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.Div e) {
	    visit(e.left, e);
	    say(" "); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.Mod e) {
	    visit(e.left, e);
	    say(" "); say(e.token); say(" ");
	    visit(e.right, e);
            return null;
	}
        @Override
	public Void visit(Expr.Plus e) {
	    say(e.token); visit(e.expr, e);
            return null;
	}
        @Override
	public Void visit(Expr.Minus e) {
	    say(e.token); visit(e.expr, e);
            return null;
	}
        @Override
	public Void visit(Expr.Deref e) {
	    visit(e.expr, e); say(e.token);
            return null;
	}
        @Override
	public Void visit(Expr.Qualify e) {
	    visit(e.expr, e);
	    say(e.token);
	    say(e.name);
            return null;
	}
        @Override
	public Void visit(Expr.Subscript e) {
	    visit(e.expr, e);
	    say(e.token);
	    print(e.index, i+2);
	    say("]");
            return null;
	}
        @Override
	public Void visit(Expr.Call e) {
	    boolean first = true;
	    visit(e.proc, e);
	    say(e.token);
	    for (Expr expr: e.actuals) {
		if (first) first = false; else say(", ");
		print(expr, i+2);
	    }
	    say(")");
            return null;
	}
        @Override
	public Void visit(Expr.Named e) {
	    say(e.token);
            return null;
	}
        @Override
        public Void visit(Expr.Number e) {
            say(e.token);
            return null;
        }
        @Override
        public Void visit(Expr.Char e) {
            say(e.token);
            return null;
        }
        @Override
	public Void visit(Expr.Text e) {
	    say(e.token);
            return null;
	}
        @Override
	public Void visit(Expr.TypeExpr e) {
	    print(e.value, i);
            return null;
	}

	private int c;
        private void indent(int d) {
            for (int i = 0; i < d; i++)
                System.out.print(' ');
            c = d;
        }
        private void say(String s) {
            System.out.print(s);
            c += s.length();
        }
        private void sayln(String s) {
            System.out.println(s);
	    c = 0;
	    System.out.flush();
        }
        private void say(Token s) {
            System.out.print(s.image);
            c += s.image.length();
        }
        private void sayln(Token s) {
            System.out.println(s.image);
	    c = 0;
	    System.out.flush();
        }
    }
}
