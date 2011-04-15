package M3;

/**
 * These classes provide support for type-checking expressions generally,
 * and type expressions, names, and qualified names in particular.
 */

abstract class Expr {
    /**
     * Type-check an expression
     * @param e the expression to check
     * @return the type of the expression or null if it has no type
     */
    static Type TypeCheck(Absyn.Expr e) {
        if (e == null) return Type.ERROR;
        if (e.checked) return e.type;
        e.checked = true;
        return e.type = e.accept(new Absyn.Expr.Visitor<Type>() {
            @Override
            public Type visit(Absyn.Expr.Add e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.INTEGER) || !Type.IsEqual(r, Type.INTEGER))
                    return BadOperands(e, l, r);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.And e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.BOOLEAN) || !Type.IsEqual(r, Type.BOOLEAN))
                    return BadOperands(e, l, r);
                return Type.BOOLEAN;
            }

            @Override
            public Type visit(Absyn.Expr.Call e) {
                Type t = TypeCheck(e.proc);
                if (t == null)
                    t = QualifyExpr.MethodType(e.proc);
                Type.Proc proc = Type.Proc.Is(t);
                if (proc != null) {
                    proc.fixArgs(e);
                    return proc.check(e);
                }
                if (t != Type.ERROR)
                    Semant.error(e, "attempting to call a non-procedure");
                for (Absyn.Expr actual : e.actuals)
                    TypeCheck(actual);
                return Type.ERROR;
            }

            @Override
            public Type visit(Absyn.Expr.Char e) {
                return Type.CHAR;
            }

            @Override
            public Type visit(Absyn.Expr.Deref e) {
                Type t = TypeCheck(e.expr);
                Type.Ref ref = Type.Ref.Is(t);
                if (ref != null) {
                    if (ref.target == null) {
                        Semant.error(e, "cannot dereference REFANY or NULL");
                        return Type.ERROR;
                    }
                    return ref.target;
                }
                Semant.error(e, "cannot dereference a non-REF value");
                return Type.ERROR;
            }

            @Override
            public Type visit(Absyn.Expr.Div e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.INTEGER) || !Type.IsEqual(r, Type.INTEGER))
                    return BadOperands(e, l, r);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Eq e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (l == null || r == null || !Type.IsAssignable(l, r) || !Type.IsAssignable(r, l)) {
                    return BadOperands(e, l, r);
                } else {
                    return Type.BOOLEAN;
                }
            }

            @Override
            public Type visit(Absyn.Expr.Ge e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (l == Type.INTEGER && r == Type.INTEGER) {
                    return Type.BOOLEAN;
                } else if (!Type.IsEqual(l, r)) {
                    return BadOperands(e, l, r);
                } else if (Type.Enum.Is(l) != null) {
                    return Type.BOOLEAN;
                } else {
                    return BadOperands(e, l, r);
                }
            }

            @Override
            public Type visit(Absyn.Expr.Gt e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (l == Type.INTEGER && r == Type.INTEGER) {
                    return Type.BOOLEAN;
                } else if (!Type.IsEqual(l, r)) {
                    return BadOperands(e, l, r);
                } else if (Type.Enum.Is(l) != null) {
                    return Type.BOOLEAN;
                } else {
                    return BadOperands(e, l, r);
                }
            }

            @Override
            public Type visit(Absyn.Expr.Le e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (l == Type.INTEGER && r == Type.INTEGER) {
                    return Type.BOOLEAN;
                } else if (!Type.IsEqual(l, r)) {
                    return BadOperands(e, l, r);
                } else if (Type.Enum.Is(l) != null) {
                    return Type.BOOLEAN;
                } else {
                    return BadOperands(e, l, r);
                }
            }

            @Override
            public Type visit(Absyn.Expr.Lt e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (l == Type.INTEGER && r == Type.INTEGER) {
                    return Type.BOOLEAN;
                } else if (!Type.IsEqual(l, r)) {
                    return BadOperands(e, l, r);
                } else if (Type.Enum.Is(l) != null) {
                    return Type.BOOLEAN;
                } else {
                    return BadOperands(e, l, r);
                }
            }

            @Override
            public Type visit(Absyn.Expr.Minus e) {
                Type t = TypeCheck(e.expr);
                if (!Type.IsEqual(t, Type.INTEGER))
                    return BadOperands(e, t, null);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Mod e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.INTEGER) || !Type.IsEqual(r, Type.INTEGER))
                    return BadOperands(e, l, r);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Multiply e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.INTEGER) || !Type.IsEqual(r, Type.INTEGER))
                    return BadOperands(e, l, r);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Named e) {
                e.scope = Scope.Top();
                return NamedExpr.Check(e);
            }

            @Override
            public Type visit(Absyn.Expr.Ne e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (l == null || r == null || !Type.IsAssignable(l, r) || !Type.IsAssignable(r, l)) {
                    return BadOperands(e, l, r);
                } else {
                    return Type.BOOLEAN;
                }
            }

            @Override
            public Type visit(Absyn.Expr.Not e) {
                Type t = TypeCheck(e.expr);
                if (!Type.IsEqual(t, Type.BOOLEAN))
                    return BadOperands(e, t, null);
                return Type.BOOLEAN;
            }

            @Override
            public Type visit(Absyn.Expr.Number e) {
                String[] split = e.token.image.split("_");
                if (split.length == 1) {
                    try {
                        Integer.parseInt(split[0]);
                    } catch (NumberFormatException x) {
                        Semant.error(e, "illegal INTEGER literal");
                    }
                } else {
                    assert split.length == 2;
                    try {
                        int radix = Integer.parseInt(split[0]);
                        Integer.parseInt(split[1], radix);
                    } catch (NumberFormatException x) {
                        Semant.error(e, "illegal based INTEGER literal");
                    }
                }
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Or e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.BOOLEAN) || !Type.IsEqual(r, Type.BOOLEAN))
                    return BadOperands(e, l, r);
                return Type.BOOLEAN;
            }

            @Override
            public Type visit(Absyn.Expr.Plus e) {
                Type t = TypeCheck(e.expr);
                if (!Type.IsEqual(t, Type.INTEGER))
                    return BadOperands(e, t, null);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Qualify e) {
                return QualifyExpr.Check(e);
            }

            @Override
            public Type visit(Absyn.Expr.Subscript e) {
                Type ta = TypeCheck(e.expr);
                Type tb = TypeCheck(e.index);

                if (ta == null) {
                    Semant.error(e, "subscripted expression is not an array");
                    return Type.ERROR;
                }

                Type.Ref r = Type.Ref.Is(ta);
                if (r != null) {
                    // auto-magic dereference
                    e.expr = new Absyn.Expr.Deref(e.token, e.expr);
                    ta = TypeCheck(e.expr);
                }

                Type.OpenArray a = Type.OpenArray.Is(ta);
                if (a == null) {
                    Semant.error(e, "subscripted expression is not an array");
                    return Type.ERROR;
                }
                if (!Type.IsSubtype(tb, Type.INTEGER))
                    Semant.error(e, "open arrays must be indexed by INTEGER expressions");

                return a.element;
            }

            @Override
            public Type visit(Absyn.Expr.Subtract e) {
                Type l = TypeCheck(e.left);
                Type r = TypeCheck(e.right);
                if (!Type.IsEqual(l, Type.INTEGER) || !Type.IsEqual(r, Type.INTEGER))
                    return BadOperands(e, l, r);
                return Type.INTEGER;
            }

            @Override
            public Type visit(Absyn.Expr.Text e) {
                return Type.TEXT;
            }

            @Override
            public Type visit(Absyn.Expr.TypeExpr e) {
                Type.Check(Type.Parse(e.value));
                return null;
            }
        });
    }

    /**
     * Note an "illegal operand(s)" error for an expression
     * @param e the erroring expression
     * @param a the type of the first operand (can be null)
     * @param b the type of the second operand (can be null)
     * @return the error type
     */
    static Type BadOperands(Absyn.Expr e, Type a, Type b) {
        if (a != Type.ERROR && b != Type.ERROR)
            Semant.error(e, "illegal operand(s) for " + e.token);
        return Type.ERROR;
    }

    static boolean IsDesignator(Absyn.Expr e) {
        if (e == null) return true;
        assert e.checked;
        return e.accept(new Absyn.Expr.Visitor<Boolean>() {
            public Boolean visit(Absyn.Expr.Add e) { return false; }
            public Boolean visit(Absyn.Expr.And e) { return false; }
            public Boolean visit(Absyn.Expr.Call e) { return false; }
            public Boolean visit(Absyn.Expr.Char e) { return false; }
            public Boolean visit(Absyn.Expr.Deref e) { return true; }
            public Boolean visit(Absyn.Expr.Div e) { return false; }
            public Boolean visit(Absyn.Expr.Eq e) { return false; }
            public Boolean visit(Absyn.Expr.Ge e) { return false; }
            public Boolean visit(Absyn.Expr.Gt e) { return false; }
            public Boolean visit(Absyn.Expr.Le e) { return false; }
            public Boolean visit(Absyn.Expr.Lt e) { return false; }
            public Boolean visit(Absyn.Expr.Minus e) { return false; }
            public Boolean visit(Absyn.Expr.Mod e) { return false; }
            public Boolean visit(Absyn.Expr.Multiply e) { return false; }
            public Boolean visit(Absyn.Expr.Named e) { return NamedExpr.IsDesignator(e); }
            public Boolean visit(Absyn.Expr.Ne e) { return false; }
            public Boolean visit(Absyn.Expr.Not e) { return false; }
            public Boolean visit(Absyn.Expr.Number e) { return false; }
            public Boolean visit(Absyn.Expr.Or e) { return false; }
            public Boolean visit(Absyn.Expr.Plus e) { return false; }
            public Boolean visit(Absyn.Expr.Qualify e) { return QualifyExpr.IsDesignator(e); }
            public Boolean visit(Absyn.Expr.Subscript e) { return IsDesignator(e.expr); }
            public Boolean visit(Absyn.Expr.Subtract e) { return false; }
            public Boolean visit(Absyn.Expr.Text e) { return false; }
            public Boolean visit(Absyn.Expr.TypeExpr e) { return false; }
        });
    }

    static boolean IsWritable(Absyn.Expr e) {
        if (e == null) return true;
        assert e.checked;
        return e.accept(new Absyn.Expr.Visitor<Boolean>() {
            public Boolean visit(Absyn.Expr.Add e) { return false; }
            public Boolean visit(Absyn.Expr.And e) { return false; }
            public Boolean visit(Absyn.Expr.Call e) { return false; }
            public Boolean visit(Absyn.Expr.Char e) { return false; }
            public Boolean visit(Absyn.Expr.Deref e) { return true; }
            public Boolean visit(Absyn.Expr.Div e) { return false; }
            public Boolean visit(Absyn.Expr.Eq e) { return false; }
            public Boolean visit(Absyn.Expr.Ge e) { return false; }
            public Boolean visit(Absyn.Expr.Gt e) { return false; }
            public Boolean visit(Absyn.Expr.Le e) { return false; }
            public Boolean visit(Absyn.Expr.Lt e) { return false; }
            public Boolean visit(Absyn.Expr.Minus e) { return false; }
            public Boolean visit(Absyn.Expr.Mod e) { return false; }
            public Boolean visit(Absyn.Expr.Multiply e) { return false; }
            public Boolean visit(Absyn.Expr.Named e) { return NamedExpr.IsWritable(e); }
            public Boolean visit(Absyn.Expr.Ne e) { return false; }
            public Boolean visit(Absyn.Expr.Not e) { return false; }
            public Boolean visit(Absyn.Expr.Number e) { return false; }
            public Boolean visit(Absyn.Expr.Or e) { return false; }
            public Boolean visit(Absyn.Expr.Plus e) { return false; }
            public Boolean visit(Absyn.Expr.Qualify e) { return QualifyExpr.IsWritable(e); }
            public Boolean visit(Absyn.Expr.Subscript e) { return IsWritable(e.expr); }
            public Boolean visit(Absyn.Expr.Subtract e) { return false; }
            public Boolean visit(Absyn.Expr.Text e) { return false; }
            public Boolean visit(Absyn.Expr.TypeExpr e) { return false; }
        });
    }

    static void NeedsAddress(Absyn.Expr e) {
        if (e == null) return;
        assert e.checked;
        e.accept(new Absyn.Expr.Visitor<Void>() {
            public Void visit(Absyn.Expr.Add e) { return null; }
            public Void visit(Absyn.Expr.And e) { return null; }
            public Void visit(Absyn.Expr.Call e) { return null; }
            public Void visit(Absyn.Expr.Char e) { return null; }
            public Void visit(Absyn.Expr.Deref e) { return null; }
            public Void visit(Absyn.Expr.Div e) { return null; }
            public Void visit(Absyn.Expr.Eq e) { return null; }
            public Void visit(Absyn.Expr.Ge e) { return null; }
            public Void visit(Absyn.Expr.Gt e) { return null; }
            public Void visit(Absyn.Expr.Le e) { return null; }
            public Void visit(Absyn.Expr.Lt e) { return null; }
            public Void visit(Absyn.Expr.Minus e) { return null; }
            public Void visit(Absyn.Expr.Mod e) { return null; }
            public Void visit(Absyn.Expr.Multiply e) { return null; }
            public Void visit(Absyn.Expr.Named e) { NamedExpr.NeedsAddress(e); return null; }
            public Void visit(Absyn.Expr.Ne e) { return null; }
            public Void visit(Absyn.Expr.Not e) { return null; }
            public Void visit(Absyn.Expr.Number e) { return null; }
            public Void visit(Absyn.Expr.Or e) { return null; }
            public Void visit(Absyn.Expr.Plus e) { return null; }
            public Void visit(Absyn.Expr.Qualify e) { QualifyExpr.NeedsAddress(e); return null; }
            public Void visit(Absyn.Expr.Subscript e) { NeedsAddress(e.expr); return null; }
            public Void visit(Absyn.Expr.Subtract e) { return null; }
            public Void visit(Absyn.Expr.Text e) { return null; }
            public Void visit(Absyn.Expr.TypeExpr e) { return null; }
        });
    }
}

abstract class TypeExpr {
    /**
     * Test whether an expression is a type expression
     * @param e the expression to test
     * @return the type expression or null
     */
    static Absyn.Expr.TypeExpr Is(Absyn.Expr e) {
        if (e instanceof Absyn.Expr.TypeExpr)
            return (Absyn.Expr.TypeExpr) e;
        return null;
    }

    /**
     * Test whether an expression is a type expression
     * @param e the expression to test
     * @return the type denoted by the type expression
     */
    static Type Split(Absyn.Expr e) {
        if (e == null) return null;
        Absyn.Expr.TypeExpr te = Is(e);
        if (te != null) return te.value.type;
        Value v;
        if ((v = NamedExpr.Split(e)) != null || (v = QualifyExpr.Split(e)) != null) {
            Value.Tipe t = Value.Tipe.Is(v);
            if (t != null) return t.value;
        }
        return null;
    }
}

abstract class NamedExpr {
    private static void Resolve(Absyn.Expr.Named e) {
        assert e.value == null;
        e.value = Scope.LookUp(e.scope, e.name, false);
        if (e.value == null) {
            Semant.error(e, "undefined", e.name);
            e.value = new Value.Variable(e.name, Type.ERROR);
        }
    }

    static Type Check(Absyn.Expr.Named e) {
        if (e.value == null) Resolve(e);
        Value.TypeCheck(e.value);
        Type t = Value.TypeOf(e.value);
        e.value = Value.Base(e.value);
        return t;
    }

    static boolean IsDesignator(Absyn.Expr.Named e) {
        if (e.value == null) Resolve(e);
        return e.value.isDesignator();
    }

    static boolean IsWritable(Absyn.Expr.Named e) {
        if (e.value == null) Resolve(e);
        return e.value.isWritable();
    }

    static void NeedsAddress(Absyn.Expr.Named e) {
        if (e.value == null) Resolve(e);
        e.value.needsAddress();
    }

    /**
     * Test whether an expression is a named expression
     * @param e the expression to test
     * @return the named expression or null
     */
    static Absyn.Expr.Named Is(Absyn.Expr e) {
        if (e instanceof Absyn.Expr.Named)
            return (Absyn.Expr.Named) e;
        return null;
    }

    /**
     * Test whether an expression is a named expression
     * @param e the expression to test
     * @return the value denoted by the named expression
     */
    static Value Split(Absyn.Expr e) {
        Absyn.Expr.Named n = Is(e);
        if (n == null) return null;
        if (n.value == null) Resolve(n);
        return n.value;
    }
}

abstract class QualifyExpr {
    private static void Resolve(Absyn.Expr.Qualify e) {
        assert e.value == null;
        Type t = Expr.TypeCheck(e.expr);
        if (Type.Ref.Is(t) != null) {
            // auto-magic dereference
            e.expr = new Absyn.Expr.Deref(e.token, e.expr);
            t = Expr.TypeCheck(e.expr);
        }
        if (t == Type.ERROR) {
            e.value = new Value.Variable(e.name, Type.ERROR);
            return;
        } else if (t == null) {
            // a module or type
            if ((t = TypeExpr.Split(e.expr)) != null) {
                if ((e.value = Type.Enum.LookUp(t, e.name)) != null)
                    return;
                if ((e.value = Type.Object.LookUp(t, e.name)) != null)
                    return;
            } else if ((e.value = NamedExpr.Split(e.expr)) != null) {
                Value.Module m = Value.Module.Is(e.value);
                if (m != null) {
                    e.value = Scope.LookUp(m.locals, e.value.name, true);
                    return;
                }
            }
        } else {
            if ((e.value = Type.Object.LookUp(t, e.name)) != null)
                return;
        }
        Semant.error(e, "unknown qualification(" + e.name + ")");
        e.value = new Value.Variable(e.name, Type.ERROR);
    }

    /**
     * Type-check a qualified expression
     * @param e the qualified expression to check
     * @param flags for passing back information about the expression,
     *        or passing in constraints on the expression
     * @return the type of the qualified expression
     */
    static Type Check(Absyn.Expr.Qualify e) {
        if (e.value == null) Resolve(e);
        Value.TypeCheck(e.value);
        Type t = Expr.TypeCheck(e.expr);
        if (t == null) {
            // a module or type
            if ((t = TypeExpr.Split(e.expr)) != null) {
                if (Type.Object.Is(t) != null) {
                    Value.Method m = Value.Method.Is(e.value);
                    if (m != null)
                        return Type.Proc.MethodSigAsProcSig(m.signature, t);
                    Semant.error(e, "doesn't name a method (" + e.name + ")");
                    return Type.ERROR;
                }
            }
        } else {
            if (Value.Method.Is(e.value) != null)
                return null;
        }
        return Value.TypeOf(e.value);
    }

    static boolean IsDesignator(Absyn.Expr.Qualify e) {
        if (e.value == null) Resolve(e);
        return e.value.isDesignator();
    }

    static boolean IsWritable(Absyn.Expr.Qualify e) {
        if (e.value == null) Resolve(e);
        return e.value.isWritable();
    }

    static void NeedsAddress(Absyn.Expr.Qualify e) {
        if (e.value == null) Resolve(e);
        if (Value.Variable.Is(e.value) != null)
            e.value.needsAddress();
        else if (Value.Field.Is(e.value) != null)
            Expr.NeedsAddress(e.expr);
        else assert false;
    }

    /**
     * Test whether an expression is a qualified expression
     * @param e the expression to test
     * @return the qualified expression or null
     */
    static Absyn.Expr.Qualify Is(Absyn.Expr e) {
        if (e instanceof Absyn.Expr.Qualify)
            return (Absyn.Expr.Qualify) e;
        return null;
    }

    /**
     * Test whether an expression is a qualified expression
     * @param e the expression to test
     * @return the value denoted by the qualified expression
     */
    static Value Split(Absyn.Expr e) {
        Absyn.Expr.Qualify q = Is(e);
        if (q == null) return null;
        if (q.value == null) Resolve(q);
        return q.value;
    }

    /**
     * Test whether an expression is a qualified expression that denotes a method
     * @param e the expression to test
     * @return the type of the method denoted by the qualified expression, or null
     */
    static Type MethodType(Absyn.Expr e) {
        Absyn.Expr.Qualify q = Is(e);
        if (q == null) return null;
        if (q.value == null) Resolve(q);
        if (Value.Method.Is(q.value) != null)
            return Value.TypeOf(q.value);
        return null;
    }    
}
