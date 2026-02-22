package com.example.yutnoriapp; // 본인 패키지명 유지!

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class MainActivity extends AppCompatActivity {

    Piece[] pieces = new Piece[4];
    View[] pieceViews = new View[4];
    View[] spotViews = new View[29];
    FrameLayout[] waitSpots = new FrameLayout[4];

    int selectedPieceId = -1;
    TextView textStatus;

    final int START_NODE = -1;
    final int END_NODE = 30;

    // 경로를 저장할 배열
    int[] nextNode = new int[30];
    int[] prevNode = new int[30];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatus = findViewById(R.id.text_status);
        ConstraintLayout rootLayout = findViewById(R.id.root_layout);

        // 맵 경로 세팅 (작성자님 요청대로 좌하단 출발로 싹 다 고침!)
        setupBoardPaths();

        for (int i = 0; i < 29; i++) {
            int resId = getResources().getIdentifier("spot" + i, "id", getPackageName());
            spotViews[i] = findViewById(resId);
        }

        for (int i = 0; i < 4; i++) {
            int resId = getResources().getIdentifier("wait_spot_" + i, "id", getPackageName());
            waitSpots[i] = findViewById(resId);
        }

        for (int i = 0; i < 4; i++) {
            pieces[i] = new Piece(i);

            View view = new View(this);
            view.setLayoutParams(new ConstraintLayout.LayoutParams(60, 60));
            view.setBackgroundResource(R.drawable.shape_node_large);
            view.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark));

            final int pieceId = i;
            view.setOnClickListener(v -> selectPiece(pieceId));

            pieceViews[i] = view;
            rootLayout.addView(view);

            rootLayout.post(() -> moveToWaitSpot(pieceId));
        }

        findViewById(R.id.btn_bdo).setOnClickListener(v -> handleYutInput(-1));
        findViewById(R.id.btn_do).setOnClickListener(v -> handleYutInput(1));
        findViewById(R.id.btn_gae).setOnClickListener(v -> handleYutInput(2));
        findViewById(R.id.btn_geol).setOnClickListener(v -> handleYutInput(3));
        findViewById(R.id.btn_yut).setOnClickListener(v -> handleYutInput(4));
        findViewById(R.id.btn_mo).setOnClickListener(v -> handleYutInput(5));
    }

    // --- [윷판 경로 설정 함수 (fix : 골인 룰 반영)] ---
    void setupBoardPaths() {
        // 좌하단(Start) -> 우하단(0)
        nextNode[16]=17; nextNode[17]=18; nextNode[18]=19; nextNode[19]=0;
        // 우하단(0) -> 우상단(5)
        nextNode[0]=1; nextNode[1]=2; nextNode[2]=3; nextNode[3]=4; nextNode[4]=5;
        // 우상단(5) -> 좌상단(10)
        nextNode[5]=6; nextNode[6]=7; nextNode[7]=8; nextNode[8]=9; nextNode[9]=10;

        // ★ 수정된 부분 1: 좌상단(10)에서 내려올 때 15번에 딱 멈출 수 있게 함
        nextNode[10]=11; nextNode[11]=12; nextNode[12]=13; nextNode[13]=14; nextNode[14]=15;

        // ★ 수정된 부분 2: 대각선으로 내려올 때도 15번에 딱 멈출 수 있게 함
        nextNode[20]=21; nextNode[21]=22; nextNode[22]=23; nextNode[23]=24; nextNode[24]=15;

        // 대각선 1 (우하단 0 -> 중앙 29 -> 좌상단 10)
        nextNode[28]=27; nextNode[27]=29; nextNode[29]=26; nextNode[26]=25; nextNode[25]=10;

        // ★ 핵심: 15번(시작/도착점)에서 1칸 더 가야 진짜 골인(END_NODE) 판정!
        nextNode[15]=END_NODE;

        // 빽도(prevNode) 세팅
        prevNode[16]=15; prevNode[17]=16; prevNode[18]=17; prevNode[19]=18; prevNode[0]=19;
        prevNode[1]=0; prevNode[2]=1; prevNode[3]=2; prevNode[4]=3; prevNode[5]=4;
        prevNode[6]=5; prevNode[7]=6; prevNode[8]=7; prevNode[9]=8; prevNode[10]=9;

        // 빽도 세팅에도 15번 추가
        prevNode[11]=10; prevNode[12]=11; prevNode[13]=12; prevNode[14]=13; prevNode[15]=14;

        prevNode[28]=0; prevNode[27]=28; prevNode[29]=27; prevNode[26]=29; prevNode[25]=26;
        prevNode[20]=5; prevNode[21]=20; prevNode[22]=21; prevNode[23]=22; prevNode[24]=23;
    }

    void selectPiece(int id) {
        if (pieces[id].isFinished) return;
        selectedPieceId = id;
        textStatus.setText((id + 1) + "번 말 선택됨. 윷을 누르세요.");
        for (int i = 0; i < 4; i++) {
            if (i == id) pieceViews[i].setAlpha(1.0f);
            else pieceViews[i].setAlpha(0.3f);
        }
    }

    void handleYutInput(int steps) {
        if (selectedPieceId == -1) {
            Toast.makeText(this, "먼저 움직일 말을 터치해서 선택하세요!", Toast.LENGTH_SHORT).show();
            return;
        }

        Piece p = pieces[selectedPieceId];

        if (p.position == START_NODE && steps == -1) {
            Toast.makeText(this, "대기실에서는 빽도를 쓸 수 없습니다!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 버그 픽스: 위치(p.position)만 넘기는 게 아니라 말(p) 전체를 넘겨서 기억을 읽게 함 (백도 때문에)
        int targetNode = calculateMove(p, steps);
        p.position = targetNode;

        if (targetNode == END_NODE) {
            p.isFinished = true;
            pieceViews[selectedPieceId].setVisibility(View.GONE);
            textStatus.setText((selectedPieceId + 1) + "번 말 골인!");
            selectedPieceId = -1;
            checkWin();
        } else if (targetNode == START_NODE) {
            moveToWaitSpot(selectedPieceId);
            textStatus.setText((selectedPieceId + 1) + "번 말 빽도로 쫓겨남");
        } else {
            movePieceUI(selectedPieceId, targetNode);
        }
    }

    // 매개변수가 int currentPos 에서 Piece p 로 변경됨
    int calculateMove(Piece p, int steps) {
        int pos = p.position;

        // --- [1. 빽도 완벽 해결 로직] ---
        if (steps == -1) {
            if (pos == START_NODE) return START_NODE;
            if (pos == 16) return 15; // 빽도 골인 대기석 안착

            // 교차점 딜레마 완벽 해결 (기억력 사용)
            if (pos == 15 && p.route == 1) return 24; // 중앙에서 내려온 경우
            if (pos == 10 && p.route == 3) return 25; // 0번 모서리에서 대각선 타고 올라온 경우

            // 나머지 빽도는 기본 배열 사용 (22, 29 등은 이미 prevNode에 분리되어 세팅됨)
            return prevNode[pos];
        }

        // --- [2. 전진 및 지름길 로직] ---
        for (int i = 0; i < steps; i++) {
            if (pos == END_NODE) break;

            if (i == 0) { // 출발할 때 모서리나 방(중앙)에 서 있으면 꺾음
                if (pos == 0) { pos = 28; p.route = 3; continue; } // ★ 실수로 빼먹었던 0번 지름길 부활!
                if (pos == 5) { pos = 20; p.route = 1; continue; } // 5번 지름길

                // ★ 윷놀이 핵심 룰: '방(중앙)'에 멈춰있다가 출발하면 무조건 출구(15번) 방향으로 직행!
                if (pos == 22 || pos == 29) { pos = 23; p.route = 1; continue; }
            }

            if (pos == START_NODE) {
                pos = 16;
            } else {
                pos = nextNode[pos];
            }
        }
        return pos;
    }

    // 날아가는 버그 해결 코드 (boardContainer 좌표 반영)
    void movePieceUI(int pieceId, int logicalNode) {
        if (logicalNode == START_NODE || logicalNode == END_NODE) return;

        // 29번 노드는 로직상으로만 존재하고 화면은 정중앙(22)을 씀
        int spotIndex = (logicalNode == 29) ? 22 : logicalNode;

        View pieceView = pieceViews[pieceId];
        View targetSpot = spotViews[spotIndex];
        View boardContainer = findViewById(R.id.board_container);

        // 윷판 컨테이너의 절대 좌표를 더해줌
        float targetX = boardContainer.getX() + targetSpot.getX() + (targetSpot.getWidth() / 2f) - (pieceView.getWidth() / 2f);
        float targetY = boardContainer.getY() + targetSpot.getY() + (targetSpot.getHeight() / 2f) - (pieceView.getHeight() / 2f);

        pieceView.animate().x(targetX).y(targetY).setDuration(300).start();
    }

    void moveToWaitSpot(int pieceId) {
        View pieceView = pieceViews[pieceId];
        View targetSpot = waitSpots[pieceId];
        float targetX = targetSpot.getX() + ((View)targetSpot.getParent()).getX() + (targetSpot.getWidth() / 2f) - (pieceView.getWidth() / 2f);
        float targetY = targetSpot.getY() + ((View)targetSpot.getParent()).getY() + (targetSpot.getHeight() / 2f) - (pieceView.getHeight() / 2f);
        pieceView.setX(targetX);
        pieceView.setY(targetY);
    }

    void checkWin() {
        boolean allFinished = true;
        for (Piece p : pieces) {
            if (!p.isFinished) allFinished = false;
        }
        if (allFinished) {
            textStatus.setText("🎉 승리! 게임 종료 🎉");
            textStatus.setTextColor(Color.RED);
        }
    }
}