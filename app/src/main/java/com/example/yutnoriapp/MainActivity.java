package com.example.yutnoriapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    // --- 변수 선언부 ---
    int totalTeams = 2;
    int currentTeam = 0;

    Piece[][] pieces = new Piece[totalTeams][4];
    View[][] pieceViews = new View[totalTeams][4];
    View[] spotViews = new View[29];
    FrameLayout[] waitSpots = new FrameLayout[4];

    TextView textStatus;

    final int START_NODE = -1;
    final int END_NODE = 30;

    int[] nextNode = new int[30];
    int[] prevNode = new int[30];

    // 게임 상태 관리 변수들
    ArrayList<Integer> rollResults = new ArrayList<>();
    boolean canRoll = true;
    boolean forceRoll = false; // 잡았을 때 무조건 윷을 굴려야 하는 강제 상태
    int selectedResultIndex = -1;
    boolean isGameOver = false;

    // ==========================================
    // 1. 초기화 및 설정 그룹
    // ==========================================

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
        findViewById(R.id.btn_restart).setOnClickListener(v -> resetGame());
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

    /**
     * [핵심 기능] 게임 화면과 데이터를 초기 상태로 되돌립니다.
     */
    void resetGame() {
        currentTeam = 0;
        rollResults.clear();
        canRoll = true;
        forceRoll = false;
        selectedResultIndex = -1;
        isGameOver = false;

        textStatus.setText("1팀(빨강) 차례입니다.");
        textStatus.setTextColor(Color.parseColor("#112D4E"));
        textStatus.animate().cancel();
        textStatus.setScaleX(1f);
        textStatus.setScaleY(1f);

        ((LinearLayout)findViewById(R.id.layout_results)).removeAllViews();
        ConstraintLayout rootLayout = findViewById(R.id.root_layout);

        for (int t = 0; t < totalTeams; t++) {
            for (int i = 0; i < 4; i++) {
                Piece p = pieces[t][i];
                p.position = START_NODE;
                p.route = 0;
                p.isFinished = false;

                View view = pieceViews[t][i];
                view.setVisibility(View.VISIBLE);
                view.animate().cancel();

                if (view.getParent() != rootLayout) {
                    ((ViewGroup)view.getParent()).removeView(view);
                    ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(60, 60);
                    view.setLayoutParams(params);
                    rootLayout.addView(view);
                }

                final int teamIndex = t;
                final int pieceIndex = i;
                rootLayout.post(() -> moveToWaitSpot(teamIndex, pieceIndex));
            }
        }
    }

    // ==========================================
    // 2. 윷 던지기 및 UI 그룹
    // ==========================================

    /**
     * [핵심 기능] 윷을 던졌을 때의 결과를 바구니에 담고, 턴 상태를 제어합니다.
     */
    void handleYutInput(int steps) {
        if (isGameOver) return;
        if (!canRoll) {
            Toast.makeText(this, "먼저 선택한 결과를 사용해서 말을 움직이세요!", Toast.LENGTH_SHORT).show();
            return;
        }

        forceRoll = false;
        rollResults.add(steps);
        updateResultButtons();

        boolean isLoudSound = (steps == 4 || steps == 5);

        if (isLoudSound) {
            textStatus.setText("큰소리! 한 번 더 던질 수 있습니다.");
            canRoll = true;
        } else {
            textStatus.setText("작은소리! 이제 아래 버튼 중 하나를 선택하세요.");
            canRoll = false;
        }
    }

    void updateResultButtons() {
        LinearLayout layout = findViewById(R.id.layout_results);
        layout.removeAllViews();

        for (int i = 0; i < rollResults.size(); i++) {
            int steps = rollResults.get(i);
            Button btn = new Button(this);
            btn.setText(getResultName(steps));

            final int index = i;
            btn.setOnClickListener(v -> onResultClick(index));

            layout.addView(btn);
        }
    }

    void onResultClick(int index) {
        if (forceRoll) {
            Toast.makeText(this, "상대 말을 잡았습니다! 윷을 먼저 굴려야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedResultIndex = index;
        textStatus.setText(getResultName(rollResults.get(index)) + "을(를) 선택했습니다. 이제 말을 클릭하세요.");
    }

    String getResultName(int steps) {
        switch(steps) {
            case -1: return "빽도";
            case 1: return "도";
            case 2: return "개";
            case 3: return "걸";
            case 4: return "윷";
            case 5: return "모";
            default: return "";
        }
    }

    // ==========================================
    // 3. 말 이동 및 게임 엔진 그룹
    // ==========================================

    void selectPiece(int teamId, int id) {
        if (isGameOver) return;
        if (pieces[teamId][id].isFinished) {
            Toast.makeText(this, "이미 완주한 말입니다!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (teamId != currentTeam) {
            Toast.makeText(this, "당신 차례가 아닙니다!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (forceRoll) {
            Toast.makeText(this, "윷을 먼저 한 번 더 굴려야 합니다!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedResultIndex == -1) {
            Toast.makeText(this, "사용할 윷 결과를 먼저 선택하세요!", Toast.LENGTH_SHORT).show();
            return;
        }

        int steps = rollResults.remove(selectedResultIndex);
        selectedResultIndex = -1;

        moveSelectedPiece(id, steps);
    }

    /**
     * [핵심 기능] 선택된 말을 이동시키고 업기, 잡기, 완주 처리, 턴 교체를 총괄합니다.
     */
    void moveSelectedPiece(int pieceId, int steps) {
        int originalPos = pieces[currentTeam][pieceId].position;

        // 1. 업기 로직 (운명 공동체 결성)
        ArrayList<Integer> groupedPieces = new ArrayList<>();
        if (originalPos == START_NODE) {
            groupedPieces.add(pieceId);
        } else {
            for (int i = 0; i < 4; i++) {
                Piece teamPiece = pieces[currentTeam][i];
                if (!teamPiece.isFinished && teamPiece.position == originalPos) {
                    groupedPieces.add(i);
                }
            }
        }

        Piece leaderPiece = pieces[currentTeam][pieceId];
        int targetNode = calculateMove(leaderPiece, steps);
        boolean caught = false;
        boolean isWin = false; // 승리 상태 저장용 변수

        // 2. 완주 처리 (보관소로 이동)
        if (targetNode == END_NODE) {
            ConstraintLayout rootLayout = findViewById(R.id.root_layout);
            int layoutId = (currentTeam == 0) ? R.id.layout_finished_team1 : R.id.layout_finished_team2;
            LinearLayout finishedLayout = findViewById(layoutId);

            for (int gId : groupedPieces) {
                Piece gp = pieces[currentTeam][gId];
                gp.isFinished = true;
                gp.position = END_NODE;

                View finishedView = pieceViews[currentTeam][gId];

                // 확실하게 원래 판에서 분리
                if (finishedView.getParent() == rootLayout) {
                    rootLayout.removeView(finishedView);
                }

                finishedView.animate().cancel();
                finishedView.setX(0);
                finishedView.setY(0);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(60, 60);
                params.setMargins(5, 0, 5, 0);
                finishedView.setLayoutParams(params);

                // 이미 보관소에 들어간 말이 중복 처리되는 것을 방지
                if (finishedView.getParent() == null) {
                    finishedLayout.addView(finishedView);
                }
            }
            isWin = checkWin(); // 강력해진 크로스 체크 실행!
        }
        // 3. 판 위 이동 및 상대 말 잡기
        else {
            for (int gId : groupedPieces) {
                Piece gp = pieces[currentTeam][gId];
                gp.position = targetNode;
                gp.route = leaderPiece.route;
                movePieceUI(currentTeam, gId, targetNode);
            }

            int opponentTeam = (currentTeam == 0) ? 1 : 0;
            for (int i = 0; i < 4; i++) {
                Piece oppPiece = pieces[opponentTeam][i];
                if (!oppPiece.isFinished && oppPiece.position != START_NODE && oppPiece.position == targetNode) {
                    oppPiece.position = START_NODE;
                    oppPiece.route = 0;
                    moveToWaitSpot(opponentTeam, i);
                    caught = true;
                }
            }
        }

        if (isWin) return;

        // 4. 턴 유지 및 교체 판정
        if (caught) {
            canRoll = true;
            forceRoll = true;
            Toast.makeText(this, "상대 말을 잡았습니다! 윷을 먼저 굴리세요!", Toast.LENGTH_SHORT).show();
        }

        if (rollResults.isEmpty() && !canRoll) {
            currentTeam = (currentTeam + 1) % totalTeams;
            canRoll = true;

            textStatus.setText((currentTeam + 1) + "팀 차례입니다.");
            textStatus.setTextColor(currentTeam == 0 ? Color.RED : Color.BLUE);

            textStatus.animate().scaleX(1.5f).scaleY(1.5f).setDuration(300).withEndAction(() -> {
                textStatus.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
            }).start();
        } else if (forceRoll) {
            textStatus.setText("상대 말을 잡았습니다! 무조건 윷을 한 번 더 던지세요.");
        } else {
            String status = canRoll ? "윷을 더 던지거나 " : "";
            textStatus.setText(status + "남은 결과를 선택하세요.");
        }

        updateResultButtons();
    }

    /**
     * [핵심 기능] 윷판의 코너 및 중앙 지름길 로직을 판단하여 최종 도착 노드를 계산합니다.
     */
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

    // ==========================================
    // 4. 애니메이션 및 유틸리티 그룹
    // ==========================================

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

    boolean checkWin() {
        int finishedCount = 0;
        for (Piece p : pieces[currentTeam]) {
            if (p.isFinished) finishedCount++;
        }

        // 크로스 체크: 데이터가 4개이거나, 실제 보관소 화면(LinearLayout)에 말이 4개 들어갔다면 무조건 승리!
        int layoutId = (currentTeam == 0) ? R.id.layout_finished_team1 : R.id.layout_finished_team2;
        LinearLayout finishedLayout = findViewById(layoutId);

        if (finishedCount == 4 || finishedLayout.getChildCount() == 4) {
            isGameOver = true; // 게임 강제 종료

            // 텍스트 및 효과
            textStatus.setText("🎉 " + (currentTeam + 1) + "팀 승리! 🎉");
            textStatus.setTextColor(Color.RED);
            textStatus.animate().cancel();
            textStatus.setScaleX(1.5f);
            textStatus.setScaleY(1.5f);

            // 에러 방지: 승리했는데 남은 윷 버튼이 굴러다니지 않게 싹 다 지워버림
            ((LinearLayout)findViewById(R.id.layout_results)).removeAllViews();
            rollResults.clear();
            canRoll = false;

            return true;
        }
        return false;
    }
}