package com.example.yutnoriapp;

public class BoardPath {
    public static final int START_NODE = -1;
    public static final int END_NODE = 30;

    private final int[] nextNode = new int[30];
    private final int[] prevNode = new int[30];

    public BoardPath() {
        nextNode[16] = 17;
        nextNode[17] = 18;
        nextNode[18] = 19;
        nextNode[19] = 0;
        nextNode[0] = 1;
        nextNode[1] = 2;
        nextNode[2] = 3;
        nextNode[3] = 4;
        nextNode[4] = 5;
        nextNode[5] = 6;
        nextNode[6] = 7;
        nextNode[7] = 8;
        nextNode[8] = 9;
        nextNode[9] = 10;
        nextNode[10] = 11;
        nextNode[11] = 12;
        nextNode[12] = 13;
        nextNode[13] = 14;
        nextNode[14] = 15;
        nextNode[20] = 21;
        nextNode[21] = 22;
        nextNode[22] = 23;
        nextNode[23] = 24;
        nextNode[24] = 15;
        nextNode[28] = 27;
        nextNode[27] = 29;
        nextNode[29] = 26;
        nextNode[26] = 25;
        nextNode[25] = 10;
        nextNode[15] = END_NODE;

        prevNode[16] = 15;
        prevNode[17] = 16;
        prevNode[18] = 17;
        prevNode[19] = 18;
        prevNode[0] = 19;
        prevNode[1] = 0;
        prevNode[2] = 1;
        prevNode[3] = 2;
        prevNode[4] = 3;
        prevNode[5] = 4;
        prevNode[6] = 5;
        prevNode[7] = 6;
        prevNode[8] = 7;
        prevNode[9] = 8;
        prevNode[10] = 9;
        prevNode[11] = 10;
        prevNode[12] = 11;
        prevNode[13] = 12;
        prevNode[14] = 13;
        prevNode[15] = 14;
        prevNode[28] = 0;
        prevNode[27] = 28;
        prevNode[29] = 27;
        prevNode[26] = 29;
        prevNode[25] = 26;
        prevNode[20] = 5;
        prevNode[21] = 20;
        prevNode[22] = 21;
        prevNode[23] = 22;
        prevNode[24] = 23;
    }

    public MoveTarget calculate(Piece piece, int steps) {
        MoveTrace trace = trace(piece, steps);
        return new MoveTarget(trace.node, trace.route);
    }

    public MoveTrace trace(Piece piece, int steps) {
        int pos = piece.position;
        int route = piece.route;
        java.util.ArrayList<Integer> visitedNodes = new java.util.ArrayList<>();

        if (steps == -1) {
            if (pos == START_NODE) {
                return new MoveTrace(START_NODE, route, visitedNodes);
            }
            if (pos == 16) {
                visitedNodes.add(15);
                return new MoveTrace(15, route, visitedNodes);
            }
            if (pos == 15 && route == 1) {
                visitedNodes.add(24);
                return new MoveTrace(24, route, visitedNodes);
            }
            if (pos == 10 && route == 3) {
                visitedNodes.add(25);
                return new MoveTrace(25, route, visitedNodes);
            }
            pos = prevNode[pos];
            visitedNodes.add(pos);
            return new MoveTrace(pos, route, visitedNodes);
        }

        for (int i = 0; i < steps; i++) {
            if (pos == END_NODE) {
                break;
            }

            if (i == 0) {
                if (pos == 0) {
                    pos = 28;
                    route = 3;
                    visitedNodes.add(pos);
                    continue;
                }
                if (pos == 5) {
                    pos = 20;
                    route = 1;
                    visitedNodes.add(pos);
                    continue;
                }
                if (pos == 22 || pos == 29) {
                    pos = 23;
                    route = 1;
                    visitedNodes.add(pos);
                    continue;
                }
            }

            if (pos == START_NODE) {
                pos = 16;
            } else {
                pos = nextNode[pos];
            }
            visitedNodes.add(pos);
        }

        return new MoveTrace(pos, route, visitedNodes);
    }

    public int visualSpotFor(int logicalNode) {
        return logicalNode == 29 ? 22 : logicalNode;
    }

    public boolean isSameBoardSpot(int firstNode, int secondNode) {
        return visualSpotFor(firstNode) == visualSpotFor(secondNode);
    }

    public static boolean isValidNode(int node) {
        return node >= START_NODE && node <= END_NODE;
    }

    public static boolean isValidRoute(int route) {
        return route == 0 || route == 1 || route == 3;
    }

    public static class MoveTarget {
        public final int node;
        public final int route;

        MoveTarget(int node, int route) {
            this.node = node;
            this.route = route;
        }
    }

    public static class MoveTrace {
        public final int node;
        public final int route;
        public final java.util.List<Integer> visitedNodes;

        MoveTrace(int node, int route, java.util.List<Integer> visitedNodes) {
            this.node = node;
            this.route = route;
            this.visitedNodes = visitedNodes;
        }
    }
}
