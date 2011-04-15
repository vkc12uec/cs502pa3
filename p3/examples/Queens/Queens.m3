MODULE Queens;
(* A M3 program to solve the 8-queens problem *)

TYPE Queens = OBJECT
  row, col, diag1, diag2: REF ARRAY OF INTEGER;
METHODS
  init(): Queens := Init;
  run(c: INTEGER) := Run;
  print() := PrintBoard;
END;

PROCEDURE Init(this: Queens): Queens =
  VAR n := 8;
  BEGIN
    this.row := NEW(REF ARRAY OF INTEGER, n);
    this.col := NEW(REF ARRAY OF INTEGER, n);
    this.diag1 := NEW(REF ARRAY OF INTEGER, n+n-1);
    this.diag2 := NEW(REF ARRAY OF INTEGER, n+n-1);
    RETURN this;
  END Init;

PROCEDURE Run(this: Queens; c: INTEGER) =
  BEGIN
    IF c = NUMBER(this.col^) THEN
      this.print();
    ELSE
      FOR r := 0 TO LAST(this.row^) DO
        IF this.row[r] = 0
          AND this.diag1[r+c] = 0
          AND this.diag2[r+LAST(this.row^)-c] = 0 THEN
          this.row[r] := 1;
          this.diag1[r+c] := 1;
          this.diag2[r+LAST(this.row^)-c] := 1;
          this.col[c] := r;
          this.run(c+1);
          this.row[r] := 0;
          this.diag1[r+c] := 0;
          this.diag2[r+LAST(this.row^)-c] := 0;
        END
      END
    END
  END Run;

PROCEDURE PrintBoard(this: Queens) =
  BEGIN
    FOR i := 0 TO LAST(this.col^) DO
      FOR j := 0 TO LAST(this.row^) DO
        putchar(' ');
        IF this.col[i] = j
          THEN putchar('Q');
          ELSE putchar('.');
        END;
      END;
      putchar('\n');
    END;
    putchar('\n');
  END PrintBoard;

VAR q := NEW(Queens).init();
BEGIN
  q.run(0);
END Queens.
