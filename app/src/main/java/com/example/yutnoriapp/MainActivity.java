package com.example.yutnoriapp;

// 주의: 맨 윗줄 package com.본인이름... 은 지우지 마세요!

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // --- [게임 설정 값] ---
    int totalTeams = 2;
    int currentTeam = 0;
    int extraTurns = 0;

    List<List<Piece>> teams = new ArrayList<>();

    // --- [UI 연결용 변수] ---
    View[] spotViews = new View[29]; // XML에 만든 29개 점을 담을 배열
    View testPieceView; // 화면에 소환할 빨간색 말

    // --- [윷판 경로 설정 (Map)] ---
    final int START_NODE = -1;
    final int END_NODE = 30;

    final int[] nextNode = {
            1, 2, 3, 4, 5,
            6, 7, 8, 9, 10,
            11, 12, 13, 14, 15,
            16, 17, 18, 19, END_NODE,
            21, 22,
            23, 24, 15,
            26, 22,
            28, END_NODE
    };

    final int[] prevNode = {
            19, 0, 1, 2, 3,
            4, 5, 6, 7, 8,
            9, 10, 11, 12, 13,
            14, 15, 16, 17, 18,
            5, 20, 21,
            22, 23,
            10, 25,
            22, 27
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. XML에 있는 29개의 점(View)을 자바로 불러와서 배열에 꽂아넣기
        for (int i = 0; i < 29; i++) {
            int resId = getResources().getIdentifier("spot" + i, "id", getPackageName());
            spotViews[i] = findViewById(resId);
        }

        // 2. 자바에서 동적으로 '빨간색 말' 생성해서 윷판 위에 올리기
        ConstraintLayout container = findViewById(R.id.board_container);
        testPieceView = new View(this);
        testPieceView.setLayoutParams(new ConstraintLayout.LayoutParams(60, 60)); // 말 크기
        testPieceView.setBackgroundResource(R.drawable.shape_node_large); // 기존에 만든 동그라미 재활용
        testPieceView.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark)); // 빨갛게 칠하기
        container.addView(testPieceView);

        // 게임 초기화
        initGame(totalTeams);

        // 화면이 다 그려진 후 시작점(spot0)으로 빨간 말을 이동시킴
        container.post(() -> movePieceUI(testPieceView, 0));

        // 3. 윷판 아무데나 터치하면 '윷 던지기' 실행
        container.setOnClickListener(v -> {
            int randomYut = new Random().nextInt(5) + 1; // 1~5 랜덤 (도~모)
            String[] yutNames = {"도", "개", "걸", "윷", "모"};
            Toast.makeText(this, yutNames[randomYut-1] + " (" + randomYut + "칸 이동)!", Toast.LENGTH_SHORT).show();

            playTurn(0, randomYut); // 1팀 0번 말 이동명령 내리기
        });
    }

    // --- [말을 화면에서 부드럽게 이동시키는 마법 (애니메이션)] ---
    void movePieceUI(View pieceView, int spotIndex) {
        if (spotIndex == END_NODE || spotIndex == START_NODE) return;

        View targetSpot = spotViews[spotIndex];

        // 목표 칸의 정중앙 좌표 계산 (수학 문제 해결!)
        float targetX = targetSpot.getX() + (targetSpot.getWidth() / 2f) - (pieceView.getWidth() / 2f);
        float targetY = targetSpot.getY() + (targetSpot.getHeight() / 2f) - (pieceView.getHeight() / 2f);

        // 0.3초 동안 스르륵 이동
        pieceView.animate().x(targetX).y(targetY).setDuration(300).start();
    }

    // --- [게임 로직 함수들] ---
    void initGame(int numberOfTeams) {
        teams.clear();
        for (int i = 0; i < numberOfTeams; i++) {
            List<Piece> teamPieces = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                teamPieces.add(new Piece(i, j));
            }
            teams.add(teamPieces);
        }
    }

    void playTurn(int pieceIndex, int steps) {
        Piece piece = teams.get(currentTeam).get(pieceIndex);
        if (piece.position == END_NODE) return;

        int finalPos = calculateMove(piece.position, steps);
        movePieceAndGroup(piece, finalPos);

        boolean isCaught = checkCatchAndStack(piece);

        if (steps == 4 || steps == 5 || isCaught) {
            extraTurns++;
        } else {
            if (extraTurns > 0) extraTurns--;
            else nextTeam();
        }
    }

    int calculateMove(int currentPos, int steps) {
        if (currentPos == END_NODE) return END_NODE;
        int pos = currentPos;

        if (steps == -1) {
            if (pos == START_NODE) return START_NODE;
            return prevNode[pos];
        }

        for (int i = 0; i < steps; i++) {
            if (pos == END_NODE) break;
            // 지름길 처리
            if (i == 0) {
                if (pos == 5) { pos = 20; continue; }
                if (pos == 10) { pos = 25; continue; }
                if (pos == 22) { pos = 27; continue; }
            }
            if (pos == START_NODE) pos = 0;
            else pos = nextNode[pos];
        }
        return pos;
    }

    void movePieceAndGroup(Piece piece, int newPos) {
        piece.position = newPos;
        for (Piece p : piece.groupedPieces) p.position = newPos;

        // ★ 로직이 이동한 후, 화면의 UI(빨간 말)도 같이 이동시킴!
        if (piece.teamId == 0 && piece.pieceId == 0) {
            movePieceUI(testPieceView, newPos);
        }
    }

    boolean checkCatchAndStack(Piece movedPiece) {
        // 복잡도 생략을 위해 현재는 로직만 유지
        return false;
    }

    void nextTeam() {
        currentTeam++;
        if (currentTeam >= totalTeams) currentTeam = 0;
    }

    class Piece {
        int teamId, pieceId;
        int position = START_NODE;
        List<Piece> groupedPieces = new ArrayList<>();
        Piece(int teamId, int pieceId) { this.teamId = teamId; this.pieceId = pieceId; }
    }
}