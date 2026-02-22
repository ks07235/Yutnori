package com.example.yutnoriapp;

public class Piece {
    public int id;
    public int position;
    public boolean isFinished;
    public int route; //  어느 지름길을 탔는지. 빽도 때문에 추가 (0: 외곽 경로, 1: 우상단 꺾임, 2: 좌상단 꺾임)

    public Piece(int id) {
        this.id = id;
        this.position = -1;
        this.isFinished = false;
        this.route = 0; // 처음엔 무조건 외곽 경로(0)로 시작
    }
}