package M3;

class Semant {
    private static void usage() {
        throw new Error("Usage: java M3.Semant <module>");
    }

    public static void main(String[] args) {
        if (args.length != 1) usage();
        java.io.File file = new java.io.File(args[0]);
        try {
            TypeCheck(file);
            Scope.Print(Scope.Top());
            System.out.flush();
        } catch (ParseException e) {
            error(e.getMessage());
        } catch (TokenMgrError e) {
            error(e.getMessage());
        }
        System.err.flush();
    }

    static java.io.File file;
    static Value.Module TypeCheck(java.io.File file) throws ParseException {
        Semant.file = file;
        java.io.InputStream stream;
        try {
            stream = new java.io.FileInputStream(file);
        } catch (java.io.FileNotFoundException e) {
            error("File not found:" + file.getName());
            return null;
        }
        Parser parse = new Parser(stream);
        Absyn.Decl.Module main = parse.Unit();
        String[] split = file.getName().split("\\.");
        String prefix, suffix;
        if (split.length == 2) {
            prefix = split[0];
            suffix = split[1];
        } else {
            prefix = file.getName();
            suffix = main.stmts == null ? "i3" : "m3";
        }
        if (!(prefix.equals(main.name))
                || !(suffix.equals("m3") && main.stmts != null
                || !(suffix.equals("i3") && main.stmts == null)))
            warning(main, "file name (" + file.getName()
                    + ") doesn't match module name");
        Value.Module m = new Value.Module(main);
        m.toplevel = true;
        if (m.isInterface)
            Value.Module.PushInterface(m.name);
        Value.TypeCheck(m);
        if (m.isInterface)
            Value.Module.PopInterface();
        Value.Module.NoteVisibility(m);
        return m;
    }

    static boolean anyErrors = false;

    private static void message(Absyn loc, String msg) {
        System.err.println(msg + ": line " + loc.line() + ", column "
                + loc.column());
    }

    static void error(Absyn node, String msg) {
        anyErrors = true;
        message(node, msg);
    }

    static void error(Absyn node, String msg, String name) {
        anyErrors = true;
        message(node, msg + "(" + name + ")");
    }

    static void error(String msg) {
        anyErrors = true;
        System.err.println(msg);
    }

    static void warning(Absyn loc, String msg) {
        message(loc, "warning: " + msg);
    }

    static {
        Scope.Insert(new Value.Tipe("INTEGER", Type.INTEGER));
        Scope.Insert(new Value.Tipe("BOOLEAN", Type.BOOLEAN));
        Scope.Insert(Type.Enum.LookUp(Type.BOOLEAN, "FALSE"));
        Scope.Insert(Type.Enum.LookUp(Type.BOOLEAN, "TRUE"));
        Scope.Insert(new Value.Tipe("CHAR", Type.CHAR));
        Scope.Insert(new Value.Tipe("NULL", Type.NULL));
        Scope.Insert(new Value.Constant("NIL", 0, Type.NULL));
        Scope.Insert(new Value.Tipe("REFANY", Type.REFANY));
        Scope.Insert(new Value.Tipe("ROOT", Type.ROOT));
        Scope.Insert(new Value.Tipe("TEXT", Type.TEXT));

        Scope.Insert(new Value.Procedure("FIRST", Type.FIRST));
        Scope.Insert(new Value.Procedure("LAST", Type.LAST));
        Scope.Insert(new Value.Procedure("ORD", Type.ORD));
        Scope.Insert(new Value.Procedure("VAL", Type.VAL));
        Scope.Insert(new Value.Procedure("NUMBER", Type.NUMBER));
        Scope.Insert(new Value.Procedure("NEW", Type.NEW));
    }
}
