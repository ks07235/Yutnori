package com.example.yutnoriapp;

public class Piece {
    public int teamId;
    public int id;
    public int position;
    public boolean isFinished;
    public int route;

    public Piece(int teamId, int id) {
        this.teamId = teamId;
        this.id = id;
        reset();
    }

    public void reset() {
        this.position = BoardPath.START_NODE;
        this.isFinished = false;
        this.route = 0;
    }
}
