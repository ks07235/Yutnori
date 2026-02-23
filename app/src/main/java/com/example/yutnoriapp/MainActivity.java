package com.example.yutnoriapp; // 본인 패키지명으로 반드시 유지하세요!

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class MainActivity extends AppCompatActivity {

    int totalTeams = 2;
    int currentTeam = 0;

    Piece[][] pieces = new Piece[totalTeams][4];
    View[][] pieceViews = new View[totalTeams][4];
    View[] spotViews = new View[29];
    FrameLayout[] waitSpots = new FrameLayout[4];

    int selectedPieceId = -1;
    TextView textStatus;

    final int START_NODE = -1;
    final int END_NODE = 30;

    int[] nextNode = new int[30];
    int[] prevNode = new int[30];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatus = findViewById(R.id.text_status);
        ConstraintLayout rootLayout = findViewById(R.id.root_layout);

        setupBoardPaths();

        for (int i = 0; i < 29; i++) {
            int resId = getResources().getIdentifier("spot" + i, "id", getPackageName());
            spotViews[i] = findViewById(resId);
        }

        for (int i = 0; i < 4; i++) {
            int resId = getResources().getIdentifier("wait_spot_" + i, "id", getPackageName());
            waitSpots[i] = findViewById(resId);
        }

        for (int t = 0; t < totalTeams; t++) {
            for (int i = 0; i < 4; i++) {
                pieces[t][i] = new Piece(t, i);

                View view = new View(this);
                view.setLayoutParams(new ConstraintLayout.LayoutParams(60, 60));
                view.setBackgroundResource(R.drawable.shape_node_large);

                if (t == 0) view.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark));
                else view.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_dark));

                final int teamIndex = t;
                final int pieceIndex = i;
                view.setOnClickListener(v -> selectPiece(teamIndex, pieceIndex));

                pieceViews[t][i] = view;
                rootLayout.addView(view);

                rootLayout.post(() -> moveToWaitSpot(teamIndex, pieceIndex));
            }
        }

        textStatus.setText("1팀(빨강) 차례입니다.");

        findViewById(R.id.btn_bdo).setOnClickListener(v -> handleYutInput(-1));
        findViewById(R.id.btn_do).setOnClickListener(v -> handleYutInput(1));
        findViewById(R.id.btn_gae).setOnClickListener(v -> handleYutInput(2));
        findViewById(R.id.btn_geol).setOnClickListener(v -> handleYutInput(3));
        findViewById(R.id.btn_yut).setOnClickListener(v -> handleYutInput(4));
        findViewById(R.id.btn_mo).setOnClickListener(v -> handleYutInput(5));
    }

    void setupBoardPaths() {
        nextNode[16]=17; nextNode[17]=18; nextNode[18]=19; nextNode[19]=0;
        nextNode[0]=1; nextNode[1]=2; nextNode[2]=3; nextNode[3]=4; nextNode[4]=5;
        nextNode[5]=6; nextNode[6]=7; nextNode[7]=8; nextNode[8]=9; nextNode[9]=10;
        nextNode[10]=11; nextNode[11]=12; nextNode[12]=13; nextNode[13]=14; nextNode[14]=15;
        nextNode[20]=21; nextNode[21]=22; nextNode[22]=23; nextNode[23]=24; nextNode[24]=15;
        nextNode[28]=27; nextNode[27]=29; nextNode[29]=26; nextNode[26]=25; nextNode[25]=10;
        nextNode[15]=END_NODE;

        prevNode[16]=15; prevNode[17]=16; prevNode[18]=17; prevNode[19]=18; prevNode[0]=19;
        prevNode[1]=0; prevNode[2]=1; prevNode[3]=2; prevNode[4]=3; prevNode[5]=4;
        prevNode[6]=5; prevNode[7]=6; prevNode[8]=7; prevNode[9]=8; prevNode[10]=9;
        prevNode[11]=10; prevNode[12]=11; prevNode[13]=12; prevNode[14]=13; prevNode[15]=14;
        prevNode[28]=0; prevNode[27]=28; prevNode[29]=27; prevNode[26]=29; prevNode[25]=26;
        prevNode[20]=5; prevNode[21]=20; prevNode[22]=21; prevNode[23]=22; prevNode[24]=23;
    }

    void selectPiece(int teamId, int id) {
        if (teamId != currentTeam) {
            Toast.makeText(this, "상대 팀의 차례입니다!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pieces[teamId][id].isFinished) return;

        selectedPieceId = id;
        textStatus.setText((teamId + 1) + "팀 " + (id + 1) + "번 말 선택됨.");

        for (int t = 0; t < totalTeams; t++) {
            for (int i = 0; i < 4; i++) {
                if (t == teamId && i == id) pieceViews[t][i].setAlpha(1.0f);
                else pieceViews[t][i].setAlpha(0.3f);
            }
        }
    }

    void handleYutInput(int steps) {
        if (selectedPieceId == -1) {
            Toast.makeText(this, "먼저 움직일 말을 선택하세요!", Toast.LENGTH_SHORT).show();
            return;
        }

        Piece p = pieces[currentTeam][selectedPieceId];

        if (p.position == START_NODE && steps == -1) {
            Toast.makeText(this, "대기실에서는 빽도를 쓸 수 없습니다!", Toast.LENGTH_SHORT).show();
            return;
        }

        int targetNode = calculateMove(p, steps);
        p.position = targetNode;
        boolean caught = false;

        if (targetNode == END_NODE) {
            p.isFinished = true;
            pieceViews[currentTeam][selectedPieceId].setVisibility(View.GONE);
            checkWin();
        } else if (targetNode == START_NODE) {
            moveToWaitSpot(currentTeam, selectedPieceId);
            textStatus.setText((currentTeam + 1) + "팀 " + (selectedPieceId + 1) + "번 말 빽도로 쫓겨남");
        } else {
            int opponentTeam = (currentTeam == 0) ? 1 : 0;
            for (int i = 0; i < 4; i++) {
                Piece oppPiece = pieces[opponentTeam][i];
                if (!oppPiece.isFinished && oppPiece.position == targetNode) {
                    oppPiece.position = START_NODE;
                    oppPiece.route = 0;
                    moveToWaitSpot(opponentTeam, i);
                    caught = true;
                    Toast.makeText(this, "상대 말을 잡았습니다!", Toast.LENGTH_SHORT).show();
                }
            }
            movePieceUI(currentTeam, selectedPieceId, targetNode);
        }

        if (p.isFinished) return;

        selectedPieceId = -1;
        if (steps == 4 || steps == 5 || caught) {
            textStatus.setText((currentTeam + 1) + "팀 한 번 더!");
            for (int i = 0; i < 4; i++) pieceViews[currentTeam][i].setAlpha(1.0f);
        } else {
            currentTeam = (currentTeam == 0) ? 1 : 0;
            textStatus.setText((currentTeam + 1) + "팀 차례입니다.");

            for (int t = 0; t < totalTeams; t++) {
                for (int i = 0; i < 4; i++) pieceViews[t][i].setAlpha(1.0f);
            }
        }
    }

    int calculateMove(Piece p, int steps) {
        int pos = p.position;

        if (steps == -1) {
            if (pos == START_NODE) return START_NODE;
            if (pos == 16) return 15;

            if (pos == 15 && p.route == 1) return 24;
            if (pos == 10 && p.route == 3) return 25;

            return prevNode[pos];
        }

        for (int i = 0; i < steps; i++) {
            if (pos == END_NODE) break;

            if (i == 0) {
                if (pos == 0) { pos = 28; p.route = 3; continue; }
                if (pos == 5) { pos = 20; p.route = 1; continue; }

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

    void movePieceUI(int teamId, int pieceId, int logicalNode) {
        if (logicalNode == START_NODE || logicalNode == END_NODE) return;
        int spotIndex = (logicalNode == 29) ? 22 : logicalNode;

        View pieceView = pieceViews[teamId][pieceId];
        View targetSpot = spotViews[spotIndex];
        View boardContainer = findViewById(R.id.board_container);

        float targetX = boardContainer.getX() + targetSpot.getX() + (targetSpot.getWidth() / 2f) - (pieceView.getWidth() / 2f);
        float targetY = boardContainer.getY() + targetSpot.getY() + (targetSpot.getHeight() / 2f) - (pieceView.getHeight() / 2f);

        pieceView.animate().x(targetX).y(targetY).setDuration(300).start();
    }

    void moveToWaitSpot(int teamId, int pieceId) {
        View pieceView = pieceViews[teamId][pieceId];
        View targetSpot = waitSpots[pieceId];
        float targetX = targetSpot.getX() + ((View)targetSpot.getParent()).getX() + (targetSpot.getWidth() / 2f) - (pieceView.getWidth() / 2f);
        float targetY = targetSpot.getY() + ((View)targetSpot.getParent()).getY() + (targetSpot.getHeight() / 2f) - (pieceView.getHeight() / 2f);

        if (teamId == 1) targetY -= 20;

        pieceView.setX(targetX);
        pieceView.setY(targetY);
    }

    void checkWin() {
        boolean allFinished = true;
        for (Piece p : pieces[currentTeam]) {
            if (!p.isFinished) allFinished = false;
        }
        if (allFinished) {
            textStatus.setText("🎉 " + (currentTeam + 1) + "팀 승리! 🎉");
            textStatus.setTextColor(Color.RED);
        }
    }
}