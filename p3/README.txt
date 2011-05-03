Summary: we fully implemented George's register allocation algorithm.
Liveness information is obtained by the iterative set-equation algorithm
described in the class. We use the liveness range (measured by number of instructions) as the spill heuristic.

Changes are made to
src/RegAlloc/Color.java
src/RegAlloc/Liveness.java
src/RegAlloc/Node.java
src/RegAlloc/RegAlloc.java

Specific changes:

Node.java --- new fields are added to store the move-related instructions 
that use the node, adjacency list of the node, degree and spill cost of
the node.

Liveness.java --- this class is modified to perform liveness analysis and
to build interference graph. Nodes and edges are stored in this class.

Color.java ---  this is the main class for register allocation. Variables 
and functions are named after that of George's paper. We directly
translate the psuedo-code to Java code.

RegAlloc.java --- Implements "Main()" in George's paper.


Run the program

Only "-main" switch is tested.
