package com.example.yutnoriapp;

import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes3.dex */
public class BoardPath {
    public static final int END_NODE = 30;
    public static final int START_NODE = -1;
    private final int[] nextNode = new int[30];
    private final int[] prevNode = new int[30];

    public BoardPath() {
        this.nextNode[16] = 17;
        this.nextNode[17] = 18;
        this.nextNode[18] = 19;
        this.nextNode[19] = 0;
        this.nextNode[0] = 1;
        this.nextNode[1] = 2;
        this.nextNode[2] = 3;
        this.nextNode[3] = 4;
        this.nextNode[4] = 5;
        this.nextNode[5] = 6;
        this.nextNode[6] = 7;
        this.nextNode[7] = 8;
        this.nextNode[8] = 9;
        this.nextNode[9] = 10;
        this.nextNode[10] = 11;
        this.nextNode[11] = 12;
        this.nextNode[12] = 13;
        this.nextNode[13] = 14;
        this.nextNode[14] = 15;
        this.nextNode[20] = 21;
        this.nextNode[21] = 22;
        this.nextNode[22] = 23;
        this.nextNode[23] = 24;
        this.nextNode[24] = 15;
        this.nextNode[28] = 27;
        this.nextNode[27] = 29;
        this.nextNode[29] = 26;
        this.nextNode[26] = 25;
        this.nextNode[25] = 10;
        this.nextNode[15] = 30;
        this.prevNode[16] = 15;
        this.prevNode[17] = 16;
        this.prevNode[18] = 17;
        this.prevNode[19] = 18;
        this.prevNode[0] = 19;
        this.prevNode[1] = 0;
        this.prevNode[2] = 1;
        this.prevNode[3] = 2;
        this.prevNode[4] = 3;
        this.prevNode[5] = 4;
        this.prevNode[6] = 5;
        this.prevNode[7] = 6;
        this.prevNode[8] = 7;
        this.prevNode[9] = 8;
        this.prevNode[10] = 9;
        this.prevNode[11] = 10;
        this.prevNode[12] = 11;
        this.prevNode[13] = 12;
        this.prevNode[14] = 13;
        this.prevNode[15] = 14;
        this.prevNode[28] = 0;
        this.prevNode[27] = 28;
        this.prevNode[29] = 27;
        this.prevNode[26] = 29;
        this.prevNode[25] = 26;
        this.prevNode[20] = 5;
        this.prevNode[21] = 20;
        this.prevNode[22] = 21;
        this.prevNode[23] = 22;
        this.prevNode[24] = 23;
    }

    public MoveTarget calculate(Piece piece, int steps) {
        MoveTrace trace = trace(piece, steps);
        return new MoveTarget(trace.node, trace.route);
    }

    /* JADX WARN: Code duplicated, block: B:40:0x009a A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:41:0x009c  */
    /* JADX WARN: Code duplicated, block: B:42:0x009f  */
    public MoveTrace trace(Piece piece, int steps) {
        int pos = piece.position;
        int route = piece.route;
        ArrayList<Integer> visitedNodes = new ArrayList<>();
        if (steps == -1) {
            if (pos == -1) {
                return new MoveTrace(-1, route, visitedNodes);
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
            int pos2 = this.prevNode[pos];
            visitedNodes.add(Integer.valueOf(pos2));
            return new MoveTrace(pos2, route, visitedNodes);
        }
        for (int i = 0; i < steps && pos != 30; i++) {
            if (i == 0) {
                if (pos == 0) {
                    pos = 28;
                    route = 3;
                    visitedNodes.add(28);
                } else if (pos == 5) {
                    pos = 20;
                    route = 1;
                    visitedNodes.add(20);
                } else if (pos == 22 || pos == 29) {
                    pos = 23;
                    route = 1;
                    visitedNodes.add(23);
                } else {
                    if (pos == -1) {
                        pos = 16;
                    } else {
                        pos = this.nextNode[pos];
                    }
                    visitedNodes.add(Integer.valueOf(pos));
                }
            } else {
                if (pos == -1) {
                    pos = 16;
                } else {
                    pos = this.nextNode[pos];
                }
                visitedNodes.add(Integer.valueOf(pos));
            }
        }
        return new MoveTrace(pos, route, visitedNodes);
    }

    public int visualSpotFor(int logicalNode) {
        if (logicalNode == 29) {
            return 22;
        }
        return logicalNode;
    }

    public boolean isSameBoardSpot(int firstNode, int secondNode) {
        return visualSpotFor(firstNode) == visualSpotFor(secondNode);
    }

    public static boolean isValidNode(int node) {
        return node >= -1 && node <= 30;
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
        public final List<Integer> visitedNodes;

        MoveTrace(int node, int route, List<Integer> visitedNodes) {
            this.node = node;
            this.route = route;
            this.visitedNodes = visitedNodes;
        }
    }
}
