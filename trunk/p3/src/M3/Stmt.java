package M3;

import java.util.List;

/**
 * These classes provide support for type-checking statements generally,
 * and assignment statements in particular.
 */
abstract class Stmt {
    /**
     * Type-check a list of statements
     * @param stmts the statements to check
     * @return the type returned by the last statement in the list
     */
    static Absyn.Stmt currentLoop = null;
    static Type TypeCheck(List<? extends Absyn.Stmt> stmts) {
        Type t = null;
        for (Absyn.Stmt s: stmts)
            t = s.accept(new Absyn.Stmt.Visitor<Type>() {
                public Type visit(Absyn.Stmt.Assign s) {
                    Type lhs = Expr.TypeCheck(s.lhs);
                    if (!Expr.IsDesignator(s.lhs))
                        Semant.error(s.lhs, "left-hand side is not a designator");
                    else if (!Expr.IsWritable(s.lhs))
                        Semant.error(s.lhs, "left-hand side is read-only");
                    AssignStmt.Check(lhs, s.rhs);
                    return null;
                }
                public Type visit(Absyn.Stmt.Call s) {
                    Type t = Expr.TypeCheck(s.expr);
                    if (t != null && t != Type.ERROR)
                        Semant.error(s.expr, "expression is not a statement");
                    return null;
                }
                public Type visit(Absyn.Stmt.Exit s) {
                    if (currentLoop == null) {
                        Semant.error(s, "EXIT not contained in a loop");
                    }
                    return null;
                }
                public Type visit(Absyn.Stmt.Eval s) {
                    Type t = Expr.TypeCheck(s.expr);
                    if (t == null)
                        Semant.error(s, "expression doesn't have a value");
                    return null;
                }
                public Type visit(Absyn.Stmt.For s) {
                    Type tFrom = Expr.TypeCheck(s.from);
                    Type tTo = Expr.TypeCheck(s.to);
                    Type tStep = Expr.TypeCheck(s.by);
                    if (tFrom == Type.ERROR || tTo == Type.ERROR) {
                    //  already an error
                        tFrom = Type.ERROR;
                        tTo = Type.ERROR;
                    } else if (Type.Enum.Is(tFrom) != null) {
                        if (!Type.IsEqual(tFrom, tTo))
                            Semant.error(s, "'from' and 'to' expressions are incompatible");
                    } else if (tFrom == Type.INTEGER && tTo == Type.INTEGER) {
                        // ok
                    } else
                        Semant.error(s, "'from' and 'to' expressions must be compatible ordinals");
                    if (!Type.IsSubtype(tStep, Type.INTEGER))
                        Semant.error(s, "'by' expression must be an integer");
                    s.scope = Scope.PushNewOpen();
                    Scope.Insert(new Value.Variable(s, tFrom));
                    Absyn.Stmt prevLoop = currentLoop;
                    currentLoop = s;
                    Scope.TypeCheck(s.scope);
                    TypeCheck(s.stmts);
                    currentLoop = prevLoop;
                    Scope.PopNew();
                    return null;
                }
                public Type visit(Absyn.Stmt.If s) {
                    Type t = Type.ERROR;
                    for (Absyn.Stmt.If.Clause c : s.clauses) {
                        if (c.accept(this) == null)
                            t = null;
                    }
                    return t;
                }
                public Type visit(Absyn.Stmt.If.Clause c) {
                    Type t = Expr.TypeCheck(c.expr);
                    if (!Type.IsEqual(t, Type.BOOLEAN))
                        Semant.error(c.expr, "IF condition must be a BOOLEAN");
                    return TypeCheck(c.stmts);
                }
                public Type visit(Absyn.Stmt.Loop s) {
                    Absyn.Stmt prevLoop = currentLoop;
                    currentLoop = s;
                    TypeCheck(s.stmts);
                    currentLoop = prevLoop;
                    return null;
                }
                public Type visit(Absyn.Stmt.Repeat s) {
                    Absyn.Stmt prevLoop = currentLoop;
                    currentLoop = s;
                    TypeCheck(s.stmts);
                    currentLoop = prevLoop;
                    Type t = Expr.TypeCheck(s.expr);
                    if (!Type.IsEqual(t, Type.BOOLEAN))
                        Semant.error(s.expr, "REPEAT condition must be a BOOLEAN");
                    return null;
                }
                public Type visit(Absyn.Stmt.Return s) {
                    Type t = Expr.TypeCheck(s.expr);
                    if (Value.Procedure.Is(ProcBody.current.value) == null) {
                        Semant.error(s, "RETURN not in a procedure");
                        return t;
                    }
                    Type result = Value.Procedure.Is(ProcBody.current.value).signature.result;
                    if (s.expr == null) {
                        if (result != null) {
                            Semant.error(s, "missing return result");
                            t = Type.ERROR;
                        }
                    } else if (result == null) {
                        Semant.error(s, "procedure does not have a return result");
                        t = null;
                    } else {
                        AssignStmt.Check(result, s.expr);
                        return result;
                    }
                    return t;
                }
                public Type visit(Absyn.Stmt.While s) {
                    Type t = Expr.TypeCheck(s.expr);
                    if (!Type.IsEqual(t, Type.BOOLEAN))
                        Semant.error(s.expr, "WHILE condition must be a BOOLEAN");
                    Absyn.Stmt prevLoop = currentLoop;
                    currentLoop = s;
                    TypeCheck(s.stmts);
                    currentLoop = prevLoop;
                    return null;
                }
                public Type visit(Absyn.Stmt.Block s) {
                    s.scope = Scope.PushNewOpen();
                    for (Absyn.Decl d: s.decls) Value.Parse(d);
                    Scope.TypeCheck(s.scope);
                    Type t = TypeCheck(s.stmts);
                    Scope.PopNew();
                    return t;
                }
            });
        return t;
    }
}

abstract class AssignStmt {
    /**
     * Type-check an assignment
     * @param tlhs the type of the LHS
     * @param rhs the RHS expression
     */
    static void Check(Type tlhs, Absyn.Expr rhs) {
        tlhs = Type.Check(tlhs);
        Type trhs = Expr.TypeCheck(rhs);
        if (!Type.IsAssignable(tlhs, trhs)) {
            Semant.error(rhs, "types are not assignable");
        } else if (Type.Proc.Is(tlhs) != null) {
            CheckProcedure(rhs);
        } else {
            // ok
        }
    }

    /**
     * Type-check assignment of a procedure
     * @param rhs the RHS expression
     */
    private static void CheckProcedure(Absyn.Expr rhs) {
        Value v = NamedExpr.Split(rhs);
        if (v == null)
            return;
        if (Value.Procedure.Is(v) != null)
            if (!v.toplevel)
                Semant.error(rhs, "cannot assign nested procedures");
    }
}
