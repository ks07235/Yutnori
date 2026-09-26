package com.example.yutnoriapp;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: classes3.dex */
public class YutGameEngine {
    public static final int MAX_TEAM_COUNT = 4;
    public static final int MIN_TEAM_COUNT = 2;
    public static final int PIECE_COUNT = 4;
    public static final int TEAM_COUNT = 4;
    public static final String WAITING_PIECE_BACK_DO_MESSAGE = "대기 중인 말은 빽도를 쓸 수 없습니다. 판 위의 말을 고르거나 턴을 종료하세요.";
    private final BoardPath boardPath;
    private boolean catchBonusPending;
    private int currentTeam;
    private boolean gameOver;
    private boolean mustRollBeforeMoving;
    private int nextResultId;
    private final ArrayList<YutResult> pendingResults;
    private final Piece[][] pieces;
    private boolean rollAllowed;
    private final ArrayList<Integer> selectedResultIds;
    private int teamCount;
    private final GameText text;

    public static class SavedState {
        public boolean catchBonusPending;
        public int currentTeam;
        public boolean gameOver;
        public boolean mustRollBeforeMoving;
        public int nextResultId;
        public int[] pendingIds;
        public int[] pendingSteps;
        public boolean[] pieceFinished;
        public int[] piecePositions;
        public int[] pieceRoutes;
        public boolean rollAllowed;
        public int[] selectedResultIds;
        public int teamCount;
    }

    public YutGameEngine() {
        this(GameText.korean());
    }

    public YutGameEngine(boolean korean) {
        this(GameText.forLanguage(korean));
    }

    private YutGameEngine(GameText text) {
        this.boardPath = new BoardPath();
        this.pieces = (Piece[][]) Array.newInstance((Class<?>) Piece.class, 4, 4);
        this.pendingResults = new ArrayList<>();
        this.selectedResultIds = new ArrayList<>();
        this.teamCount = 2;
        this.currentTeam = 0;
        this.nextResultId = 1;
        this.rollAllowed = true;
        this.mustRollBeforeMoving = false;
        this.catchBonusPending = false;
        this.gameOver = false;
        this.text = text;
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                this.pieces[team][id] = new Piece(team, id);
            }
        }
    }

    public ActionResult setTeamCount(int teamCount) {
        if (teamCount < 2 || teamCount > 4) {
            return ActionResult.failure(this.text.teamCountRange());
        }
        this.teamCount = teamCount;
        reset();
        return ActionResult.success(this.text.gameStarted(teamCount));
    }

    public void reset() {
        this.currentTeam = 0;
        this.nextResultId = 1;
        this.rollAllowed = true;
        this.mustRollBeforeMoving = false;
        this.catchBonusPending = false;
        this.gameOver = false;
        this.pendingResults.clear();
        this.selectedResultIds.clear();
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                this.pieces[team][id].reset();
            }
        }
    }

    public ActionResult addRoll(int steps) {
        if (this.gameOver) {
            return ActionResult.failure(this.text.gameEnded());
        }
        if (!isValidResult(steps)) {
            return ActionResult.failure(this.text.unknownResult());
        }
        ArrayList<YutResult> arrayList = this.pendingResults;
        int i = this.nextResultId;
        this.nextResultId = i + 1;
        arrayList.add(new YutResult(i, steps));
        this.selectedResultIds.clear();
        this.mustRollBeforeMoving = false;
        this.rollAllowed = isBonusRoll(steps);
        boolean z = this.rollAllowed;
        GameText gameText = this.text;
        if (z) {
            return ActionResult.success(gameText.rollWithBonus(steps));
        }
        return ActionResult.success(gameText.rollAndSelect(steps));
    }

    public ActionResult undoLastRoll() {
        if (this.gameOver) {
            return ActionResult.failure(this.text.gameEnded());
        }
        if (this.pendingResults.isEmpty()) {
            return ActionResult.failure(this.text.noRollToUndo());
        }
        YutResult removed = this.pendingResults.remove(this.pendingResults.size() - 1);
        this.selectedResultIds.remove(Integer.valueOf(removed.id));
        if (this.pendingResults.isEmpty()) {
            this.rollAllowed = true;
            this.mustRollBeforeMoving = this.catchBonusPending;
        } else {
            this.rollAllowed = isBonusRoll(this.pendingResults.get(this.pendingResults.size() - 1).steps);
            this.mustRollBeforeMoving = false;
        }
        return ActionResult.success(this.text.lastRollUndone());
    }

    public ActionResult endTurn() {
        if (this.gameOver) {
            return ActionResult.failure(this.text.gameEnded());
        }
        this.pendingResults.clear();
        this.selectedResultIds.clear();
        this.rollAllowed = true;
        this.mustRollBeforeMoving = false;
        this.catchBonusPending = false;
        this.currentTeam = getNextTeam();
        return ActionResult.success(this.text.teamTurn(this.currentTeam));
    }

    public ActionResult selectResult(int choiceIndex) {
        if (this.gameOver) {
            return ActionResult.failure(this.text.gameEnded());
        }
        if (this.mustRollBeforeMoving) {
            return ActionResult.failure(this.text.captureBonusRollFirst());
        }
        List<MoveChoice> choices = getMoveChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            return ActionResult.failure(this.text.unavailableResult());
        }
        int resultId = choices.get(choiceIndex).resultId;
        boolean zContains = this.selectedResultIds.contains(Integer.valueOf(resultId));
        ArrayList<Integer> arrayList = this.selectedResultIds;
        if (zContains) {
            arrayList.remove(Integer.valueOf(resultId));
        } else {
            arrayList.add(Integer.valueOf(resultId));
        }
        boolean zIsEmpty = this.selectedResultIds.isEmpty();
        GameText gameText = this.text;
        if (zIsEmpty) {
            return ActionResult.success(gameText.selectResultsInOrder());
        }
        return ActionResult.success(gameText.choosePieceForPlan(getSelectedPlanText()));
    }

    public MoveResult moveSelectedPiece(int teamId, int pieceId) {
        ArrayList<YutResult> plan;
        boolean finishedDuringPlan;
        int opponentTeam = pieceId;
        if (this.gameOver) {
            return MoveResult.failure(this.text.gameEnded());
        }
        if (teamId < 0 || teamId >= this.teamCount) {
            return MoveResult.failure(this.text.nonParticipatingTeam());
        }
        if (teamId != this.currentTeam) {
            return MoveResult.failure(this.text.wrongTeam(this.currentTeam));
        }
        if (opponentTeam < 0 || opponentTeam >= 4) {
            return MoveResult.failure(this.text.invalidPiece());
        }
        Piece selectedPiece = this.pieces[teamId][opponentTeam];
        if (selectedPiece.isFinished) {
            return MoveResult.failure(this.text.alreadyFinished());
        }
        if (this.mustRollBeforeMoving) {
            return MoveResult.failure(this.text.rollFirst());
        }
        if (this.selectedResultIds.isEmpty()) {
            return MoveResult.failure(this.text.selectResultFirst());
        }
        ArrayList<YutResult> plan2 = getSelectedResultsInOrder();
        if (plan2.isEmpty()) {
            this.selectedResultIds.clear();
            return MoveResult.failure(this.text.selectedResultsMissing());
        }
        if (!previewMove(teamId, pieceId).available) {
            return MoveResult.failure(this.text.waitingPieceBackDo());
        }
        MoveResult result = MoveResult.success(teamId, sumSteps(plan2));
        result.startNode = selectedPiece.position;
        ArrayList<Integer> consumedResultIds = new ArrayList<>();
        boolean finishedDuringPlan2 = false;
        for (YutResult selectedResult : plan2) {
            consumedResultIds.add(Integer.valueOf(selectedResult.id));
            int originalPosition = selectedPiece.position;
            List<Integer> groupedPieceIds = findGroupedPieces(teamId, originalPosition, opponentTeam);
            BoardPath.MoveTrace trace = this.boardPath.trace(selectedPiece, selectedResult.steps);
            MoveAnimation animation = new MoveAnimation(groupedPieceIds, originalPosition, trace.visitedNodes);
            result.targetNode = trace.node;
            result.animationPath.addAll(trace.visitedNodes);
            result.animationSegments.add(animation);
            addUnique(result.usedPieceIds, groupedPieceIds);
            if (trace.node == 30) {
                Iterator<Integer> it = groupedPieceIds.iterator();
                while (it.hasNext()) {
                    int id = it.next().intValue();
                    Piece piece = this.pieces[teamId][id];
                    piece.position = 30;
                    piece.isFinished = true;
                    piece.route = trace.route;
                    addUnique(result.finishedPieceIds, id);
                }
                finishedDuringPlan2 = true;
                break;
            }
            for (Iterator<Integer> it2 = groupedPieceIds.iterator(); it2.hasNext(); it2 = it2) {
                int id2 = it2.next().intValue();
                Piece piece2 = this.pieces[teamId][id2];
                piece2.position = trace.node;
                piece2.route = trace.route;
                addUnique(result.movedPieceIds, id2);
            }
            alignFriendlyPiecesAtTarget(teamId, trace.node, trace.route);
            int opponentTeam2 = 0;
            while (opponentTeam2 < this.teamCount) {
                if (opponentTeam2 != this.currentTeam) {
                    int id3 = 0;
                    while (id3 < 4) {
                        Piece opponent = this.pieces[opponentTeam2][id3];
                        Piece selectedPiece2 = selectedPiece;
                        if (opponent.isFinished) {
                            plan = plan2;
                            finishedDuringPlan = finishedDuringPlan2;
                        } else {
                            plan = plan2;
                            if (opponent.position == -1) {
                                finishedDuringPlan = finishedDuringPlan2;
                            } else {
                                finishedDuringPlan = finishedDuringPlan2;
                                if (this.boardPath.isSameBoardSpot(opponent.position, trace.node)) {
                                    opponent.reset();
                                    PieceRef caughtPiece = new PieceRef(opponentTeam2, id3);
                                    result.caughtPieces.add(caughtPiece);
                                    animation.caughtPieces.add(caughtPiece);
                                }
                            }
                        }
                        id3++;
                        selectedPiece = selectedPiece2;
                        plan2 = plan;
                        finishedDuringPlan2 = finishedDuringPlan;
                    }
                }
                opponentTeam2++;
                selectedPiece = selectedPiece;
                plan2 = plan2;
                finishedDuringPlan2 = finishedDuringPlan2;
            }
            opponentTeam = pieceId;
        }
        removeConsumedResults(consumedResultIds);
        this.selectedResultIds.clear();
        if (finishedDuringPlan2 && hasTeamWon(teamId)) {
            this.gameOver = true;
            this.rollAllowed = false;
            this.pendingResults.clear();
            this.catchBonusPending = false;
            result.gameWon = true;
            result.message = this.text.victory(teamId);
            return result;
        }
        if (!result.caughtPieces.isEmpty()) {
            this.rollAllowed = true;
            this.mustRollBeforeMoving = true;
            this.catchBonusPending = true;
            result.caught = true;
            result.message = this.text.capturedBonus();
            return result;
        }
        this.catchBonusPending = false;
        finishTurnAfterMove(result);
        return result;
    }

    private void finishTurnAfterMove(MoveResult result) {
        if (this.pendingResults.isEmpty() && !this.rollAllowed) {
            this.currentTeam = getNextTeam();
            this.rollAllowed = true;
            result.turnChanged = true;
            result.message = this.text.teamTurn(this.currentTeam);
            return;
        }
        if (this.pendingResults.isEmpty()) {
            result.message = this.text.canRollMore();
            return;
        }
        boolean z = this.rollAllowed;
        GameText gameText = this.text;
        if (z) {
            result.message = gameText.rollOrSelectRemaining();
        } else {
            result.message = gameText.selectRemaining();
        }
    }

    private List<Integer> findGroupedPieces(int teamId, int originalPosition, int selectedPieceId) {
        ArrayList<Integer> grouped = new ArrayList<>();
        if (originalPosition == -1) {
            grouped.add(Integer.valueOf(selectedPieceId));
            return grouped;
        }
        for (int id = 0; id < 4; id++) {
            Piece piece = this.pieces[teamId][id];
            if (!piece.isFinished && this.boardPath.isSameBoardSpot(piece.position, originalPosition)) {
                grouped.add(Integer.valueOf(id));
            }
        }
        return grouped;
    }

    private void alignFriendlyPiecesAtTarget(int teamId, int targetNode, int targetRoute) {
        for (int id = 0; id < 4; id++) {
            Piece piece = this.pieces[teamId][id];
            if (!piece.isFinished && piece.position != -1 && this.boardPath.isSameBoardSpot(piece.position, targetNode)) {
                piece.position = targetNode;
                piece.route = targetRoute;
            }
        }
    }

    private boolean hasTeamWon(int teamId) {
        for (int id = 0; id < 4; id++) {
            if (!this.pieces[teamId][id].isFinished) {
                return false;
            }
        }
        return true;
    }

    private int getNextTeam() {
        return (this.currentTeam + 1) % this.teamCount;
    }

    private boolean isValidResult(int steps) {
        if (steps != -1) {
            return steps >= 1 && steps <= 5;
        }
        return true;
    }

    private boolean isBonusRoll(int steps) {
        return steps == 4 || steps == 5;
    }

    public Piece[][] getPieces() {
        return this.pieces;
    }

    public Piece getPiece(int teamId, int pieceId) {
        return this.pieces[teamId][pieceId];
    }

    public int getTeamCount() {
        return this.teamCount;
    }

    public List<Integer> getPendingResults() {
        ArrayList<Integer> steps = new ArrayList<>();
        for (YutResult result : this.pendingResults) {
            steps.add(Integer.valueOf(result.steps));
        }
        return Collections.unmodifiableList(steps);
    }

    public List<MoveChoice> getMoveChoices() {
        ArrayList<MoveChoice> choices = new ArrayList<>();
        for (YutResult result : this.pendingResults) {
            String name = this.text.resultName(result.steps);
            int selectionOrder = getSelectionOrder(result.id);
            String label = selectionOrder > 0 ? selectionOrder + ". " + name : name;
            choices.add(new MoveChoice(result.id, result.steps, name, label, selectionOrder));
        }
        return Collections.unmodifiableList(choices);
    }

    public List<Integer> getSelectedSteps() {
        ArrayList<Integer> selectedSteps = new ArrayList<>();
        for (YutResult result : getSelectedResultsInOrder()) {
            selectedSteps.add(Integer.valueOf(result.steps));
        }
        return Collections.unmodifiableList(selectedSteps);
    }

    public MovePreview previewMove(int teamId, int pieceId) {
        if (teamId != this.currentTeam || teamId < 0 || teamId >= this.teamCount || pieceId < 0 || pieceId >= 4 || this.selectedResultIds.isEmpty()) {
            return MovePreview.unavailable();
        }
        Piece original = this.pieces[teamId][pieceId];
        if (original.isFinished) {
            return MovePreview.unavailable();
        }
        ArrayList<YutResult> plan = getSelectedResultsInOrder();
        if (plan.isEmpty()) {
            return MovePreview.unavailable();
        }
        Piece simulated = new Piece(teamId, pieceId);
        simulated.position = original.position;
        simulated.route = original.route;
        simulated.isFinished = original.isFinished;
        int targetNode = simulated.position;
        int stepsUsed = 0;
        for (YutResult result : plan) {
            BoardPath.MoveTrace trace = this.boardPath.trace(simulated, result.steps);
            if (trace.visitedNodes.isEmpty() && trace.node == simulated.position && trace.route == simulated.route) {
                return MovePreview.unavailable();
            }
            targetNode = trace.node;
            simulated.position = trace.node;
            simulated.route = trace.route;
            stepsUsed++;
            if (trace.node == 30) {
                return new MovePreview(true, true, targetNode, stepsUsed);
            }
        }
        return new MovePreview(true, false, targetNode, stepsUsed);
    }

    public int getCurrentTeam() {
        return this.currentTeam;
    }

    public int getSelectedResultIndex() {
        List<MoveChoice> choices = getMoveChoices();
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).selectionOrder == 1) {
                return i;
            }
        }
        return -1;
    }

    public int getSelectedResultId() {
        if (this.selectedResultIds.isEmpty()) {
            return -1;
        }
        return this.selectedResultIds.get(0).intValue();
    }

    public boolean isGameOver() {
        return this.gameOver;
    }

    public boolean isRollAllowed() {
        return this.rollAllowed;
    }

    public SavedState saveState() {
        SavedState state = new SavedState();
        state.teamCount = this.teamCount;
        state.currentTeam = this.currentTeam;
        state.nextResultId = this.nextResultId;
        state.rollAllowed = this.rollAllowed;
        state.mustRollBeforeMoving = this.mustRollBeforeMoving;
        state.catchBonusPending = this.catchBonusPending;
        state.gameOver = this.gameOver;
        state.pendingIds = new int[this.pendingResults.size()];
        state.pendingSteps = new int[this.pendingResults.size()];
        for (int i = 0; i < this.pendingResults.size(); i++) {
            YutResult result = this.pendingResults.get(i);
            state.pendingIds[i] = result.id;
            state.pendingSteps[i] = result.steps;
        }
        state.selectedResultIds = new int[this.selectedResultIds.size()];
        for (int i2 = 0; i2 < this.selectedResultIds.size(); i2++) {
            state.selectedResultIds[i2] = this.selectedResultIds.get(i2).intValue();
        }
        state.piecePositions = new int[16];
        state.pieceRoutes = new int[16];
        state.pieceFinished = new boolean[16];
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                int index = pieceIndex(team, id);
                Piece piece = this.pieces[team][id];
                state.piecePositions[index] = piece.position;
                state.pieceRoutes[index] = piece.route;
                state.pieceFinished[index] = piece.isFinished;
            }
        }
        return state;
    }

    public void restoreState(SavedState state) {
        if (state == null) {
            reset();
            return;
        }
        this.teamCount = clamp(state.teamCount, 2, 4);
        this.currentTeam = clamp(state.currentTeam, 0, this.teamCount - 1);
        this.nextResultId = Math.max(1, state.nextResultId);
        this.rollAllowed = state.rollAllowed;
        this.mustRollBeforeMoving = state.mustRollBeforeMoving;
        this.catchBonusPending = state.catchBonusPending;
        this.gameOver = state.gameOver;
        this.pendingResults.clear();
        int pendingCount = Math.min(lengthOf(state.pendingIds), lengthOf(state.pendingSteps));
        int highestResultId = 0;
        for (int i = 0; i < pendingCount; i++) {
            int steps = state.pendingSteps[i];
            int resultId = state.pendingIds[i];
            if (resultId > 0 && isValidResult(steps) && findPendingResult(resultId) == null) {
                this.pendingResults.add(new YutResult(resultId, steps));
                highestResultId = Math.max(highestResultId, resultId);
            }
        }
        int i2 = this.nextResultId;
        this.nextResultId = Math.max(i2, highestResultId + 1);
        this.selectedResultIds.clear();
        for (int i3 = 0; i3 < lengthOf(state.selectedResultIds); i3++) {
            int resultId2 = state.selectedResultIds[i3];
            if (findPendingResult(resultId2) != null && !this.selectedResultIds.contains(Integer.valueOf(resultId2))) {
                this.selectedResultIds.add(Integer.valueOf(resultId2));
            }
        }
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                int index = pieceIndex(team, id);
                Piece piece = this.pieces[team][id];
                piece.reset();
                if (team < this.teamCount) {
                    if (index < lengthOf(state.piecePositions) && BoardPath.isValidNode(state.piecePositions[index])) {
                        piece.position = state.piecePositions[index];
                    }
                    if (index < lengthOf(state.pieceRoutes) && BoardPath.isValidRoute(state.pieceRoutes[index])) {
                        piece.route = state.pieceRoutes[index];
                    }
                    if (index < lengthOf(state.pieceFinished)) {
                        piece.isFinished = state.pieceFinished[index];
                    }
                    if (piece.isFinished || piece.position == 30) {
                        piece.position = 30;
                        piece.isFinished = true;
                    }
                }
            }
        }
    }

    public int visualSpotFor(int logicalNode) {
        return this.boardPath.visualSpotFor(logicalNode);
    }

    public boolean isSameBoardSpot(int firstNode, int secondNode) {
        return this.boardPath.isSameBoardSpot(firstNode, secondNode);
    }

    private int pieceIndex(int teamId, int pieceId) {
        return (teamId * 4) + pieceId;
    }

    private int lengthOf(int[] values) {
        if (values == null) {
            return 0;
        }
        return values.length;
    }

    private int lengthOf(boolean[] values) {
        if (values == null) {
            return 0;
        }
        return values.length;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ArrayList<YutResult> getSelectedResultsInOrder() {
        ArrayList<YutResult> results = new ArrayList<>();
        Iterator<Integer> it = this.selectedResultIds.iterator();
        while (it.hasNext()) {
            int resultId = it.next().intValue();
            YutResult result = findPendingResult(resultId);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    private YutResult findPendingResult(int resultId) {
        for (YutResult result : this.pendingResults) {
            if (result.id == resultId) {
                return result;
            }
        }
        return null;
    }

    private void removeConsumedResults(List<Integer> consumedResultIds) {
        Iterator<Integer> it = consumedResultIds.iterator();
        while (it.hasNext()) {
            int resultId = it.next().intValue();
            for (int i = 0; i < this.pendingResults.size(); i++) {
                if (this.pendingResults.get(i).id == resultId) {
                    this.pendingResults.remove(i);
                    break;
                }
            }
        }
    }

    private int getSelectionOrder(int resultId) {
        for (int i = 0; i < this.selectedResultIds.size(); i++) {
            if (this.selectedResultIds.get(i).intValue() == resultId) {
                return i + 1;
            }
        }
        return 0;
    }

    private String getSelectedPlanText() {
        ArrayList<String> names = new ArrayList<>();
        for (YutResult result : getSelectedResultsInOrder()) {
            names.add(this.text.resultName(result.steps));
        }
        return MainActivity$$ExternalSyntheticBackport0.m(" → ", names);
    }

    private int sumSteps(List<YutResult> results) {
        int total = 0;
        for (YutResult result : results) {
            total += result.steps;
        }
        return total;
    }

    private void addUnique(ArrayList<Integer> target, List<Integer> values) {
        Iterator<Integer> it = values.iterator();
        while (it.hasNext()) {
            int value = it.next().intValue();
            addUnique(target, value);
        }
    }

    private void addUnique(ArrayList<Integer> target, int value) {
        if (!target.contains(Integer.valueOf(value))) {
            target.add(Integer.valueOf(value));
        }
    }

    public String getLocalizedResultName(int steps) {
        return this.text.resultName(steps);
    }

    public String getLocalizedTeamName(int teamId) {
        return this.text.teamName(teamId);
    }

    public String getWaitingPieceBackDoMessage() {
        return this.text.waitingPieceBackDo();
    }

    public static String getResultName(int steps) {
        switch (steps) {
            case -1:
                return "빽도";
            case 0:
            default:
                return "";
            case 1:
                return "도";
            case 2:
                return "개";
            case 3:
                return "걸";
            case 4:
                return "윷";
            case 5:
                return "모";
        }
    }

    public static String getTeamName(int teamId) {
        return (teamId + 1) + "팀";
    }

    public static class ActionResult {
        public final String message;
        public final boolean success;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    private static class YutResult {
        final int id;
        final int steps;

        YutResult(int id, int steps) {
            this.id = id;
            this.steps = steps;
        }
    }

    public static class MoveChoice {
        public final String label;
        public final String name;
        public final int resultId;
        public final int selectionOrder;
        public final int steps;

        MoveChoice(int resultId, int steps, String name, String label, int selectionOrder) {
            this.resultId = resultId;
            this.steps = steps;
            this.name = name;
            this.label = label;
            this.selectionOrder = selectionOrder;
        }
    }

    public static class MovePreview {
        public final boolean available;
        public final boolean finishes;
        public final int stepsUsed;
        public final int targetNode;

        MovePreview(boolean available, boolean finishes, int targetNode, int stepsUsed) {
            this.available = available;
            this.finishes = finishes;
            this.targetNode = targetNode;
            this.stepsUsed = stepsUsed;
        }

        static MovePreview unavailable() {
            return new MovePreview(false, false, -1, 0);
        }
    }

    public static class MoveResult {
        public String message;
        public final int steps;
        public final boolean success;
        public final int teamId;
        public final ArrayList<Integer> usedPieceIds = new ArrayList<>();
        public final ArrayList<Integer> movedPieceIds = new ArrayList<>();
        public final ArrayList<Integer> finishedPieceIds = new ArrayList<>();
        public final ArrayList<Integer> animationPath = new ArrayList<>();
        public final ArrayList<MoveAnimation> animationSegments = new ArrayList<>();
        public final ArrayList<PieceRef> caughtPieces = new ArrayList<>();
        public int startNode = -1;
        public int targetNode = -1;
        public boolean caught = false;
        public boolean turnChanged = false;
        public boolean gameWon = false;

        private MoveResult(boolean success, int teamId, int steps, String message) {
            this.success = success;
            this.teamId = teamId;
            this.steps = steps;
            this.message = message;
        }

        public static MoveResult success(int teamId, int steps) {
            return new MoveResult(true, teamId, steps, "");
        }

        public static MoveResult failure(String message) {
            return new MoveResult(false, -1, 0, message);
        }
    }

    public static class PieceRef {
        public final int pieceId;
        public final int teamId;

        PieceRef(int teamId, int pieceId) {
            this.teamId = teamId;
            this.pieceId = pieceId;
        }
    }

    public static class MoveAnimation {
        public final int startNode;
        public final ArrayList<Integer> pieceIds = new ArrayList<>();
        public final ArrayList<PieceRef> caughtPieces = new ArrayList<>();
        public final ArrayList<Integer> path = new ArrayList<>();

        MoveAnimation(List<Integer> pieceIds, int startNode, List<Integer> path) {
            this.pieceIds.addAll(pieceIds);
            this.startNode = startNode;
            this.path.addAll(path);
        }
    }
}
