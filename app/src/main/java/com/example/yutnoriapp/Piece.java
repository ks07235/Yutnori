package com.example.yutnoriapp;

/* JADX INFO: loaded from: classes3.dex */
public class Piece {
    public int id;
    public boolean isFinished;
    public int position;
    public int route;
    public int teamId;

    public Piece(int teamId, int id) {
        this.teamId = teamId;
        this.id = id;
        reset();
    }

    public void reset() {
        this.position = -1;
        this.isFinished = false;
        this.route = 0;
    }
}
