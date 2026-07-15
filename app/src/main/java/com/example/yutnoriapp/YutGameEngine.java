package com.example.yutnoriapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YutGameEngine {
    public static final int MIN_TEAM_COUNT = 2;
    public static final int MAX_TEAM_COUNT = 4;
    public static final int TEAM_COUNT = MAX_TEAM_COUNT;
    public static final int PIECE_COUNT = 4;

    private final BoardPath boardPath = new BoardPath();
    private final Piece[][] pieces = new Piece[MAX_TEAM_COUNT][PIECE_COUNT];
    private final ArrayList<YutResult> pendingResults = new ArrayList<>();
    private final ArrayList<Integer> selectedResultIds = new ArrayList<>();

    private int teamCount = MIN_TEAM_COUNT;
    private int currentTeam = 0;
    private int nextResultId = 1;
    private boolean rollAllowed = true;
    private boolean mustRollBeforeMoving = false;
    private boolean catchBonusPending = false;
    private boolean gameOver = false;

    public YutGameEngine() {
        for (int team = 0; team < MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < PIECE_COUNT; id++) {
                pieces[team][id] = new Piece(team, id);
            }
        }
    }

    public ActionResult setTeamCount(int teamCount) {
        if (teamCount < MIN_TEAM_COUNT || teamCount > MAX_TEAM_COUNT) {
            return ActionResult.failure("\ud300\uc740 2\ud300\ubd80\ud130 4\ud300\uae4c\uc9c0 \uc124\uc815\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.");
        }
        this.teamCount = teamCount;
        reset();
        return ActionResult.success(teamCount + "\ud300 \uac8c\uc784\uc744 \uc2dc\uc791\ud569\ub2c8\ub2e4.");
    }

    public void reset() {
        currentTeam = 0;
        nextResultId = 1;
        rollAllowed = true;
        mustRollBeforeMoving = false;
        catchBonusPending = false;
        gameOver = false;
        pendingResults.clear();
        selectedResultIds.clear();

        for (int team = 0; team < MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < PIECE_COUNT; id++) {
                pieces[team][id].reset();
            }
        }
    }

    public ActionResult addRoll(int steps) {
        if (gameOver) {
            return ActionResult.failure("\uac8c\uc784\uc774 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4. \uc0c8 \uac8c\uc784\uc744 \ub20c\ub7ec\uc8fc\uc138\uc694.");
        }
        if (!isValidResult(steps)) {
            return ActionResult.failure("\uc54c \uc218 \uc5c6\ub294 \uc737 \uacb0\uacfc\uc785\ub2c8\ub2e4.");
        }
        pendingResults.add(new YutResult(nextResultId++, steps));
        selectedResultIds.clear();
        mustRollBeforeMoving = false;
        rollAllowed = isBonusRoll(steps);

        if (rollAllowed) {
            return ActionResult.success(getResultName(steps) + "\uc774 \ub098\uc654\uc2b5\ub2c8\ub2e4. \ud55c \ubc88 \ub354 \ub358\uc9c8 \uc218 \uc788\uc2b5\ub2c8\ub2e4.");
        }
        return ActionResult.success(getResultName(steps) + "\uc774 \ub098\uc654\uc2b5\ub2c8\ub2e4. \uc0ac\uc6a9\ud560 \uacb0\uacfc\ub97c \uc120\ud0dd\ud558\uc138\uc694.");
    }

    public ActionResult undoLastRoll() {
        if (gameOver) {
            return ActionResult.failure("\uac8c\uc784\uc774 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4. \uc0c8 \uac8c\uc784\uc744 \ub20c\ub7ec\uc8fc\uc138\uc694.");
        }
        if (pendingResults.isEmpty()) {
            return ActionResult.failure("\ucde8\uc18c\ud560 \uc737 \uacb0\uacfc\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.");
        }

        YutResult removed = pendingResults.remove(pendingResults.size() - 1);
        selectedResultIds.remove(Integer.valueOf(removed.id));
        if (pendingResults.isEmpty()) {
            rollAllowed = true;
            mustRollBeforeMoving = catchBonusPending;
        } else {
            rollAllowed = isBonusRoll(pendingResults.get(pendingResults.size() - 1).steps);
            mustRollBeforeMoving = false;
        }
        return ActionResult.success("\ub9c8\uc9c0\ub9c9 \uc737 \uacb0\uacfc\ub97c \ucde8\uc18c\ud588\uc2b5\ub2c8\ub2e4.");
    }

    public ActionResult endTurn() {
        if (gameOver) {
            return ActionResult.failure("\uac8c\uc784\uc774 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4. \uc0c8 \uac8c\uc784\uc744 \ub20c\ub7ec\uc8fc\uc138\uc694.");
        }

        pendingResults.clear();
        selectedResultIds.clear();
        rollAllowed = true;
        mustRollBeforeMoving = false;
        catchBonusPending = false;
        currentTeam = getNextTeam();
        return ActionResult.success(getTeamName(currentTeam) + " \ucc28\ub840\uc785\ub2c8\ub2e4.");
    }

    public ActionResult selectResult(int choiceIndex) {
        if (gameOver) {
            return ActionResult.failure("\uac8c\uc784\uc774 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4. \uc0c8 \uac8c\uc784\uc744 \ub20c\ub7ec\uc8fc\uc138\uc694.");
        }
        if (mustRollBeforeMoving) {
            return ActionResult.failure("\uc0c1\ub300 \ub9d0\uc744 \uc7a1\uc558\uc2b5\ub2c8\ub2e4. \uc737\uc744 \uba3c\uc800 \ud55c \ubc88 \ub354 \uad74\ub824\uc57c \ud569\ub2c8\ub2e4.");
        }
        List<MoveChoice> choices = getMoveChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            return ActionResult.failure("\uc120\ud0dd\ud560 \uc218 \uc5c6\ub294 \uc737 \uacb0\uacfc\uc785\ub2c8\ub2e4.");
        }

        int resultId = choices.get(choiceIndex).resultId;
        if (selectedResultIds.contains(resultId)) {
            selectedResultIds.remove(Integer.valueOf(resultId));
        } else {
            selectedResultIds.add(resultId);
        }

        if (selectedResultIds.isEmpty()) {
            return ActionResult.success("\uc0ac\uc6a9\ud560 \uc737 \uacb0\uacfc\ub97c \uc21c\uc11c\ub300\ub85c \uc120\ud0dd\ud558\uc138\uc694.");
        }
        return ActionResult.success(getSelectedPlanText() + " \uc21c\uc11c\ub85c \uc6c0\uc9c1\uc77c \ub9d0\uc744 \uace0\ub974\uc138\uc694.");
    }

    public MoveResult moveSelectedPiece(int teamId, int pieceId) {
        if (gameOver) {
            return MoveResult.failure("\uac8c\uc784\uc774 \ub05d\ub0ac\uc2b5\ub2c8\ub2e4. \uc0c8 \uac8c\uc784\uc744 \ub20c\ub7ec\uc8fc\uc138\uc694.");
        }
        if (teamId < 0 || teamId >= teamCount) {
            return MoveResult.failure("\ucc38\uac00\ud558\uc9c0 \uc54a\ub294 \ud300\uc785\ub2c8\ub2e4.");
        }
        if (teamId != currentTeam) {
            return MoveResult.failure("\uc9c0\uae08\uc740 " + getTeamName(currentTeam) + " \ucc28\ub840\uc785\ub2c8\ub2e4.");
        }
        if (pieceId < 0 || pieceId >= PIECE_COUNT) {
            return MoveResult.failure("\uc120\ud0dd\ud560 \uc218 \uc5c6\ub294 \ub9d0\uc785\ub2c8\ub2e4.");
        }
        Piece selectedPiece = pieces[teamId][pieceId];
        if (selectedPiece.isFinished) {
            return MoveResult.failure("\uc774\ubbf8 \uc644\uc8fc\ud55c \ub9d0\uc785\ub2c8\ub2e4.");
        }
        if (mustRollBeforeMoving) {
            return MoveResult.failure("\uc737\uc744 \uba3c\uc800 \ud55c \ubc88 \ub354 \uad74\ub824\uc57c \ud569\ub2c8\ub2e4.");
        }
        if (selectedResultIds.isEmpty()) {
            return MoveResult.failure("\uc0ac\uc6a9\ud560 \uc737 \uacb0\uacfc\ub97c \uba3c\uc800 \uc120\ud0dd\ud558\uc138\uc694.");
        }

        ArrayList<YutResult> plan = getSelectedResultsInOrder();
        if (plan.isEmpty()) {
            selectedResultIds.clear();
            return MoveResult.failure("\uc120\ud0dd\ud55c \uc737 \uacb0\uacfc\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \ub2e4\uc2dc \uc120\ud0dd\ud558\uc138\uc694.");
        }

        MoveResult result = MoveResult.success(teamId, sumSteps(plan));
        result.startNode = selectedPiece.position;
        ArrayList<Integer> consumedResultIds = new ArrayList<>();
        boolean finishedDuringPlan = false;

        for (YutResult selectedResult : plan) {
            consumedResultIds.add(selectedResult.id);
            int originalPosition = selectedPiece.position;
            List<Integer> groupedPieceIds = findGroupedPieces(teamId, originalPosition, pieceId);
            BoardPath.MoveTrace trace = boardPath.trace(selectedPiece, selectedResult.steps);
            MoveAnimation animation = new MoveAnimation(groupedPieceIds, originalPosition, trace.visitedNodes);
            result.targetNode = trace.node;
            result.animationPath.addAll(trace.visitedNodes);
            result.animationSegments.add(animation);
            addUnique(result.usedPieceIds, groupedPieceIds);

            if (trace.node == BoardPath.END_NODE) {
                for (int id : groupedPieceIds) {
                    Piece piece = pieces[teamId][id];
                    piece.position = BoardPath.END_NODE;
                    piece.isFinished = true;
                    piece.route = trace.route;
                    addUnique(result.finishedPieceIds, id);
                }
                finishedDuringPlan = true;
                break;
            }

            for (int id : groupedPieceIds) {
                Piece piece = pieces[teamId][id];
                piece.position = trace.node;
                piece.route = trace.route;
                addUnique(result.movedPieceIds, id);
            }

            for (int opponentTeam = 0; opponentTeam < teamCount; opponentTeam++) {
                if (opponentTeam == currentTeam) {
                    continue;
                }
                for (int id = 0; id < PIECE_COUNT; id++) {
                    Piece opponent = pieces[opponentTeam][id];
                    if (!opponent.isFinished
                            && opponent.position != BoardPath.START_NODE
                            && opponent.position == trace.node) {
                        opponent.reset();
                        PieceRef caughtPiece = new PieceRef(opponentTeam, id);
                        result.caughtPieces.add(caughtPiece);
                        animation.caughtPieces.add(caughtPiece);
                    }
                }
            }
        }

        removeConsumedResults(consumedResultIds);
        selectedResultIds.clear();

        if (finishedDuringPlan && hasTeamWon(teamId)) {
            gameOver = true;
            rollAllowed = false;
            pendingResults.clear();
            catchBonusPending = false;
            result.gameWon = true;
            result.message = getTeamName(teamId) + " \uc2b9\ub9ac!";
            return result;
        }

        if (!result.caughtPieces.isEmpty()) {
            rollAllowed = true;
            mustRollBeforeMoving = true;
            catchBonusPending = true;
            result.caught = true;
            result.message = "\uc0c1\ub300 \ub9d0\uc744 \uc7a1\uc558\uc2b5\ub2c8\ub2e4. \uc737\uc744 \ud55c \ubc88 \ub354 \ub358\uc9c0\uc138\uc694.";
            return result;
        }

        catchBonusPending = false;
        finishTurnAfterMove(result);
        return result;
    }

    private void finishTurnAfterMove(MoveResult result) {
        if (pendingResults.isEmpty() && !rollAllowed) {
            currentTeam = getNextTeam();
            rollAllowed = true;
            result.turnChanged = true;
            result.message = getTeamName(currentTeam) + " \ucc28\ub840\uc785\ub2c8\ub2e4.";
            return;
        }

        if (pendingResults.isEmpty()) {
            result.message = "\uc737\uc744 \ub354 \ub358\uc9c8 \uc218 \uc788\uc2b5\ub2c8\ub2e4.";
        } else if (rollAllowed) {
            result.message = "\uc737\uc744 \ub354 \ub358\uc9c0\uac70\ub098 \ub0a8\uc740 \uacb0\uacfc\ub97c \uc120\ud0dd\ud558\uc138\uc694.";
        } else {
            result.message = "\ub0a8\uc740 \uacb0\uacfc\ub97c \uc120\ud0dd\ud558\uc138\uc694.";
        }
    }

    private List<Integer> findGroupedPieces(int teamId, int originalPosition, int selectedPieceId) {
        ArrayList<Integer> grouped = new ArrayList<>();
        if (originalPosition == BoardPath.START_NODE) {
            grouped.add(selectedPieceId);
            return grouped;
        }

        for (int id = 0; id < PIECE_COUNT; id++) {
            Piece piece = pieces[teamId][id];
            if (!piece.isFinished && piece.position == originalPosition) {
                grouped.add(id);
            }
        }
        return grouped;
    }

    private boolean hasTeamWon(int teamId) {
        for (int id = 0; id < PIECE_COUNT; id++) {
            if (!pieces[teamId][id].isFinished) {
                return false;
            }
        }
        return true;
    }

    private int getNextTeam() {
        return (currentTeam + 1) % teamCount;
    }

    private boolean isValidResult(int steps) {
        return steps == -1 || (steps >= 1 && steps <= 5);
    }

    private boolean isBonusRoll(int steps) {
        return steps == 4 || steps == 5;
    }

    public Piece[][] getPieces() {
        return pieces;
    }

    public Piece getPiece(int teamId, int pieceId) {
        return pieces[teamId][pieceId];
    }

    public int getTeamCount() {
        return teamCount;
    }

    public List<Integer> getPendingResults() {
        ArrayList<Integer> steps = new ArrayList<>();
        for (YutResult result : pendingResults) {
            steps.add(result.steps);
        }
        return Collections.unmodifiableList(steps);
    }

    public List<MoveChoice> getMoveChoices() {
        ArrayList<MoveChoice> choices = new ArrayList<>();
        for (YutResult result : pendingResults) {
            String name = getResultName(result.steps);
            int selectionOrder = getSelectionOrder(result.id);
            String label = selectionOrder > 0 ? selectionOrder + ". " + name : name;
            choices.add(new MoveChoice(result.id, result.steps, name, label, selectionOrder));
        }
        return Collections.unmodifiableList(choices);
    }

    public List<Integer> getSelectedSteps() {
        ArrayList<Integer> selectedSteps = new ArrayList<>();
        for (YutResult result : getSelectedResultsInOrder()) {
            selectedSteps.add(result.steps);
        }
        return Collections.unmodifiableList(selectedSteps);
    }

    public MovePreview previewMove(int teamId, int pieceId) {
        if (teamId != currentTeam
                || teamId < 0
                || teamId >= teamCount
                || pieceId < 0
                || pieceId >= PIECE_COUNT
                || selectedResultIds.isEmpty()) {
            return MovePreview.unavailable();
        }

        Piece original = pieces[teamId][pieceId];
        if (original.isFinished) {
            return MovePreview.unavailable();
        }

        Piece simulated = new Piece(teamId, pieceId);
        simulated.position = original.position;
        simulated.route = original.route;
        simulated.isFinished = original.isFinished;

        int targetNode = simulated.position;
        int stepsUsed = 0;
        for (YutResult result : getSelectedResultsInOrder()) {
            BoardPath.MoveTarget target = boardPath.calculate(simulated, result.steps);
            targetNode = target.node;
            simulated.position = target.node;
            simulated.route = target.route;
            stepsUsed += 1;
            if (target.node == BoardPath.END_NODE) {
                return new MovePreview(true, true, targetNode, stepsUsed);
            }
        }
        return new MovePreview(true, false, targetNode, stepsUsed);
    }

    public int getCurrentTeam() {
        return currentTeam;
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
        return selectedResultIds.isEmpty() ? -1 : selectedResultIds.get(0);
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isRollAllowed() {
        return rollAllowed;
    }

    public SavedState saveState() {
        SavedState state = new SavedState();
        state.teamCount = teamCount;
        state.currentTeam = currentTeam;
        state.nextResultId = nextResultId;
        state.rollAllowed = rollAllowed;
        state.mustRollBeforeMoving = mustRollBeforeMoving;
        state.catchBonusPending = catchBonusPending;
        state.gameOver = gameOver;

        state.pendingIds = new int[pendingResults.size()];
        state.pendingSteps = new int[pendingResults.size()];
        for (int i = 0; i < pendingResults.size(); i++) {
            YutResult result = pendingResults.get(i);
            state.pendingIds[i] = result.id;
            state.pendingSteps[i] = result.steps;
        }

        state.selectedResultIds = new int[selectedResultIds.size()];
        for (int i = 0; i < selectedResultIds.size(); i++) {
            state.selectedResultIds[i] = selectedResultIds.get(i);
        }

        int pieceTotal = MAX_TEAM_COUNT * PIECE_COUNT;
        state.piecePositions = new int[pieceTotal];
        state.pieceRoutes = new int[pieceTotal];
        state.pieceFinished = new boolean[pieceTotal];
        for (int team = 0; team < MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < PIECE_COUNT; id++) {
                int index = pieceIndex(team, id);
                Piece piece = pieces[team][id];
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

        teamCount = clamp(state.teamCount, MIN_TEAM_COUNT, MAX_TEAM_COUNT);
        currentTeam = clamp(state.currentTeam, 0, teamCount - 1);
        nextResultId = Math.max(1, state.nextResultId);
        rollAllowed = state.rollAllowed;
        mustRollBeforeMoving = state.mustRollBeforeMoving;
        catchBonusPending = state.catchBonusPending;
        gameOver = state.gameOver;

        pendingResults.clear();
        int pendingCount = Math.min(lengthOf(state.pendingIds), lengthOf(state.pendingSteps));
        int highestResultId = 0;
        for (int i = 0; i < pendingCount; i++) {
            int steps = state.pendingSteps[i];
            int resultId = state.pendingIds[i];
            if (resultId > 0 && isValidResult(steps) && findPendingResult(resultId) == null) {
                pendingResults.add(new YutResult(resultId, steps));
                highestResultId = Math.max(highestResultId, resultId);
            }
        }
        nextResultId = Math.max(nextResultId, highestResultId + 1);

        selectedResultIds.clear();
        for (int i = 0; i < lengthOf(state.selectedResultIds); i++) {
            int resultId = state.selectedResultIds[i];
            if (findPendingResult(resultId) != null && !selectedResultIds.contains(resultId)) {
                selectedResultIds.add(resultId);
            }
        }

        for (int team = 0; team < MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < PIECE_COUNT; id++) {
                int index = pieceIndex(team, id);
                Piece piece = pieces[team][id];
                piece.reset();
                if (team >= teamCount) {
                    continue;
                }
                if (index < lengthOf(state.piecePositions)
                        && BoardPath.isValidNode(state.piecePositions[index])) {
                    piece.position = state.piecePositions[index];
                }
                if (index < lengthOf(state.pieceRoutes)
                        && BoardPath.isValidRoute(state.pieceRoutes[index])) {
                    piece.route = state.pieceRoutes[index];
                }
                if (index < lengthOf(state.pieceFinished)) {
                    piece.isFinished = state.pieceFinished[index];
                }
                if (piece.isFinished || piece.position == BoardPath.END_NODE) {
                    piece.position = BoardPath.END_NODE;
                    piece.isFinished = true;
                }
            }
        }
    }

    public int visualSpotFor(int logicalNode) {
        return boardPath.visualSpotFor(logicalNode);
    }

    private int pieceIndex(int teamId, int pieceId) {
        return teamId * PIECE_COUNT + pieceId;
    }

    private int lengthOf(int[] values) {
        return values == null ? 0 : values.length;
    }

    private int lengthOf(boolean[] values) {
        return values == null ? 0 : values.length;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ArrayList<YutResult> getSelectedResultsInOrder() {
        ArrayList<YutResult> results = new ArrayList<>();
        for (int resultId : selectedResultIds) {
            YutResult result = findPendingResult(resultId);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    private YutResult findPendingResult(int resultId) {
        for (YutResult result : pendingResults) {
            if (result.id == resultId) {
                return result;
            }
        }
        return null;
    }

    private void removeConsumedResults(List<Integer> consumedResultIds) {
        for (int resultId : consumedResultIds) {
            for (int i = 0; i < pendingResults.size(); i++) {
                if (pendingResults.get(i).id == resultId) {
                    pendingResults.remove(i);
                    break;
                }
            }
        }
    }

    private int getSelectionOrder(int resultId) {
        for (int i = 0; i < selectedResultIds.size(); i++) {
            if (selectedResultIds.get(i) == resultId) {
                return i + 1;
            }
        }
        return 0;
    }

    private String getSelectedPlanText() {
        ArrayList<String> names = new ArrayList<>();
        for (YutResult result : getSelectedResultsInOrder()) {
            names.add(getResultName(result.steps));
        }
        return String.join(" \u2192 ", names);
    }

    private int sumSteps(List<YutResult> results) {
        int total = 0;
        for (YutResult result : results) {
            total += result.steps;
        }
        return total;
    }

    private void addUnique(ArrayList<Integer> target, List<Integer> values) {
        for (int value : values) {
            addUnique(target, value);
        }
    }

    private void addUnique(ArrayList<Integer> target, int value) {
        if (!target.contains(value)) {
            target.add(value);
        }
    }

    public static String getResultName(int steps) {
        switch (steps) {
            case -1:
                return "\ube7d\ub3c4";
            case 1:
                return "\ub3c4";
            case 2:
                return "\uac1c";
            case 3:
                return "\uac78";
            case 4:
                return "\uc737";
            case 5:
                return "\ubaa8";
            default:
                return "";
        }
    }

    public static String getTeamName(int teamId) {
        return (teamId + 1) + "\ud300";
    }

    public static class ActionResult {
        public final boolean success;
        public final String message;

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

    public static class SavedState {
        public int teamCount;
        public int currentTeam;
        public int nextResultId;
        public boolean rollAllowed;
        public boolean mustRollBeforeMoving;
        public boolean catchBonusPending;
        public boolean gameOver;
        public int[] pendingIds;
        public int[] pendingSteps;
        public int[] selectedResultIds;
        public int[] piecePositions;
        public int[] pieceRoutes;
        public boolean[] pieceFinished;
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
        public final int resultId;
        public final int steps;
        public final String name;
        public final String label;
        public final int selectionOrder;

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
        public final int targetNode;
        public final int stepsUsed;

        MovePreview(boolean available, boolean finishes, int targetNode, int stepsUsed) {
            this.available = available;
            this.finishes = finishes;
            this.targetNode = targetNode;
            this.stepsUsed = stepsUsed;
        }

        static MovePreview unavailable() {
            return new MovePreview(false, false, BoardPath.START_NODE, 0);
        }
    }

    public static class MoveResult {
        public final boolean success;
        public final int teamId;
        public final int steps;
        public final ArrayList<Integer> usedPieceIds = new ArrayList<>();
        public final ArrayList<Integer> movedPieceIds = new ArrayList<>();
        public final ArrayList<Integer> finishedPieceIds = new ArrayList<>();
        public final ArrayList<Integer> animationPath = new ArrayList<>();
        public final ArrayList<MoveAnimation> animationSegments = new ArrayList<>();
        public final ArrayList<PieceRef> caughtPieces = new ArrayList<>();
        public int startNode = BoardPath.START_NODE;
        public int targetNode = BoardPath.START_NODE;
        public boolean caught = false;
        public boolean turnChanged = false;
        public boolean gameWon = false;
        public String message;

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
        public final int teamId;
        public final int pieceId;

        PieceRef(int teamId, int pieceId) {
            this.teamId = teamId;
            this.pieceId = pieceId;
        }
    }

    public static class MoveAnimation {
        public final ArrayList<Integer> pieceIds = new ArrayList<>();
        public final ArrayList<PieceRef> caughtPieces = new ArrayList<>();
        public final int startNode;
        public final ArrayList<Integer> path = new ArrayList<>();

        MoveAnimation(List<Integer> pieceIds, int startNode, List<Integer> path) {
            this.pieceIds.addAll(pieceIds);
            this.startNode = startNode;
            this.path.addAll(path);
        }
    }
}
