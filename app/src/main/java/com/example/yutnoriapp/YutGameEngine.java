package com.example.yutnoriapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YutGameEngine {
    public static final int MIN_TEAM_COUNT = 2;
    public static final int MAX_TEAM_COUNT = 4;
    public static final int TEAM_COUNT = MAX_TEAM_COUNT;
    public static final int PIECE_COUNT = 4;
    private static final int INITIAL_NORMAL_ROLL_ALLOWANCE = 1;
    public static final String WAITING_PIECE_BACK_DO_MESSAGE =
            "\ub300\uae30 \uc911\uc778 \ub9d0\uc740 \ube7d\ub3c4\ub97c \uc4f8 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \ud310 \uc704\uc758 \ub9d0\uc744 \uace0\ub974\uac70\ub098 \ud134\uc744 \uc885\ub8cc\ud558\uc138\uc694.";

    private final BoardPath boardPath = new BoardPath();
    private final GameText text;
    private final Piece[][] pieces = new Piece[MAX_TEAM_COUNT][PIECE_COUNT];
    private final ArrayList<YutResult> pendingResults = new ArrayList<>();
    private final ArrayList<Integer> selectedResultIds = new ArrayList<>();

    private int teamCount = MIN_TEAM_COUNT;
    private int currentTeam = 0;
    private int nextResultId = 1;
    private boolean rollAllowed = true;
    private int normalRollAllowance = INITIAL_NORMAL_ROLL_ALLOWANCE;
    private boolean mustRollBeforeMoving = false;
    private boolean catchBonusPending = false;
    private boolean gameOver = false;
    private boolean captureBonusStacks = false;

    public void setCaptureBonusStacks(boolean enabled) { captureBonusStacks = enabled; }
    public boolean isCaptureBonusStacks() { return captureBonusStacks; }

    public YutGameEngine() {
        this(GameText.korean());
    }

    public YutGameEngine(boolean korean) {
        this(GameText.forLanguage(korean));
    }

    private YutGameEngine(GameText text) {
        this.text = text;
        for (int team = 0; team < MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < PIECE_COUNT; id++) {
                pieces[team][id] = new Piece(team, id);
            }
        }
    }

    public ActionResult setTeamCount(int teamCount) {
        if (teamCount < MIN_TEAM_COUNT || teamCount > MAX_TEAM_COUNT) {
            return ActionResult.failure(text.teamCountRange());
        }
        this.teamCount = teamCount;
        reset();
        return ActionResult.success(text.gameStarted(teamCount));
    }

    public void reset() {
        currentTeam = 0;
        nextResultId = 1;
        normalRollAllowance = INITIAL_NORMAL_ROLL_ALLOWANCE;
        rollAllowed = normalRollAllowance > 0;
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
            return ActionResult.failure(text.gameEnded());
        }
        if (!isValidResult(steps)) {
            return ActionResult.failure(text.unknownResult());
        }
        if (!isBonusRoll(steps) && normalRollAllowance <= 0) {
            return ActionResult.failure(text.normalRollLimit());
        }

        if (!isBonusRoll(steps)) {
            normalRollAllowance--;
        }
        pendingResults.add(new YutResult(nextResultId++, steps));
        selectedResultIds.clear();
        mustRollBeforeMoving = false;
        rollAllowed = normalRollAllowance > 0;

        if (rollAllowed) {
            return ActionResult.success(text.rollWithBonus(steps));
        }
        return ActionResult.success(text.rollAndSelect(steps));
    }

    public ActionResult undoLastRoll() {
        if (gameOver) {
            return ActionResult.failure(text.gameEnded());
        }
        if (pendingResults.isEmpty()) {
            return ActionResult.failure(text.noRollToUndo());
        }

        YutResult removed = pendingResults.remove(pendingResults.size() - 1);
        selectedResultIds.remove(Integer.valueOf(removed.id));
        if (!isBonusRoll(removed.steps)) {
            normalRollAllowance++;
        }
        rollAllowed = normalRollAllowance > 0;
        if (pendingResults.isEmpty()) {
            mustRollBeforeMoving = catchBonusPending;
        } else {
            mustRollBeforeMoving = false;
        }
        return ActionResult.success(text.lastRollUndone());
    }

    public ActionResult endTurn() {
        if (gameOver) {
            return ActionResult.failure(text.gameEnded());
        }

        pendingResults.clear();
        selectedResultIds.clear();
        rollAllowed = true;
        normalRollAllowance = INITIAL_NORMAL_ROLL_ALLOWANCE;
        mustRollBeforeMoving = false;
        catchBonusPending = false;
        currentTeam = getNextTeam();
        return ActionResult.success(text.teamTurn(currentTeam));
    }

    public ActionResult selectResult(int choiceIndex) {
        if (gameOver) {
            return ActionResult.failure(text.gameEnded());
        }
        if (mustRollBeforeMoving) {
            return ActionResult.failure(text.captureBonusRollFirst());
        }
        List<MoveChoice> choices = getMoveChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            return ActionResult.failure(text.unavailableResult());
        }

        int resultId = choices.get(choiceIndex).resultId;
        if (selectedResultIds.contains(resultId)) {
            selectedResultIds.remove(Integer.valueOf(resultId));
        } else {
            selectedResultIds.add(resultId);
        }

        if (selectedResultIds.isEmpty()) {
            return ActionResult.success(text.selectResultsInOrder());
        }
        return ActionResult.success(text.choosePieceForPlan(getSelectedPlanText()));
    }

    public MoveResult moveSelectedPiece(int teamId, int pieceId) {
        if (gameOver) {
            return MoveResult.failure(text.gameEnded());
        }
        if (teamId < 0 || teamId >= teamCount) {
            return MoveResult.failure(text.nonParticipatingTeam());
        }
        if (teamId != currentTeam) {
            return MoveResult.failure(text.wrongTeam(currentTeam));
        }
        if (pieceId < 0 || pieceId >= PIECE_COUNT) {
            return MoveResult.failure(text.invalidPiece());
        }
        Piece selectedPiece = pieces[teamId][pieceId];
        if (selectedPiece.isFinished) {
            return MoveResult.failure(text.alreadyFinished());
        }
        if (mustRollBeforeMoving) {
            return MoveResult.failure(text.rollFirst());
        }
        if (selectedResultIds.isEmpty()) {
            return MoveResult.failure(text.selectResultFirst());
        }

        ArrayList<YutResult> plan = getSelectedResultsInOrder();
        if (plan.isEmpty()) {
            selectedResultIds.clear();
            return MoveResult.failure(text.selectedResultsMissing());
        }
        if (!previewMove(teamId, pieceId).available) {
            return MoveResult.failure(text.waitingPieceBackDo());
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
            animation.bonusRoll = isBonusRoll(selectedResult.steps);
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

            alignFriendlyPiecesAtTarget(teamId, trace.node, trace.route);
            animation.arrivedPieceIds.clear();
            animation.arrivedPieceIds.addAll(findGroupedPieces(teamId, trace.node, pieceId));

            for (int opponentTeam = 0; opponentTeam < teamCount; opponentTeam++) {
                if (opponentTeam == currentTeam) {
                    continue;
                }
                for (int id = 0; id < PIECE_COUNT; id++) {
                    Piece opponent = pieces[opponentTeam][id];
                    if (!opponent.isFinished
                            && opponent.position != BoardPath.START_NODE
                            && boardPath.isSameBoardSpot(opponent.position, trace.node)) {
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
            normalRollAllowance = 0;
            pendingResults.clear();
            catchBonusPending = false;
            result.gameWon = true;
            result.message = text.victory(teamId);
            return result;
        }

        if (!result.caughtPieces.isEmpty()) {
            for (MoveAnimation segment : result.animationSegments) {
                if (!segment.caughtPieces.isEmpty() && (captureBonusStacks || !segment.bonusRoll)) {
                    result.grantedCaptureRolls++;
                }
            }
            normalRollAllowance += result.grantedCaptureRolls;
            rollAllowed = normalRollAllowance > 0;
            mustRollBeforeMoving = result.grantedCaptureRolls > 0;
            catchBonusPending = mustRollBeforeMoving;
            result.caught = true;
            if (mustRollBeforeMoving) result.message = text.capturedBonus();
            else finishTurnAfterMove(result);
            return result;
        }

        catchBonusPending = false;
        finishTurnAfterMove(result);
        return result;
    }

    private void finishTurnAfterMove(MoveResult result) {
        rollAllowed = normalRollAllowance > 0;
        if (pendingResults.isEmpty() && normalRollAllowance <= 0) {
            currentTeam = getNextTeam();
            normalRollAllowance = INITIAL_NORMAL_ROLL_ALLOWANCE;
            rollAllowed = true;
            result.turnChanged = true;
            result.message = text.teamTurn(currentTeam);
            return;
        }

        if (pendingResults.isEmpty()) {
            result.message = text.canRollMore();
        } else if (normalRollAllowance > 0) {
            result.message = text.rollOrSelectRemaining();
        } else {
            result.message = text.selectRemaining();
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
            if (!piece.isFinished && boardPath.isSameBoardSpot(piece.position, originalPosition)) {
                grouped.add(id);
            }
        }
        return grouped;
    }

    private void alignFriendlyPiecesAtTarget(int teamId, int targetNode, int targetRoute) {
        for (int id = 0; id < PIECE_COUNT; id++) {
            Piece piece = pieces[teamId][id];
            if (!piece.isFinished
                    && piece.position != BoardPath.START_NODE
                    && boardPath.isSameBoardSpot(piece.position, targetNode)) {
                piece.position = targetNode;
                piece.route = targetRoute;
            }
        }
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

    private int deriveNormalRollAllowance(boolean restoredRollAllowed) {
        for (YutResult result : pendingResults) {
            if (!isBonusRoll(result.steps)) {
                return 0;
            }
        }
        return restoredRollAllowed ? INITIAL_NORMAL_ROLL_ALLOWANCE : 0;
    }

    public boolean canAddRoll(int steps) {
        return !gameOver && isValidResult(steps) && (isBonusRoll(steps) || normalRollAllowance > 0);
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
            String name = text.resultName(result.steps);
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
            BoardPath.MoveTrace trace = boardPath.trace(simulated, result.steps);
            if (trace.visitedNodes.isEmpty()
                    && trace.node == simulated.position
                    && trace.route == simulated.route) {
                return MovePreview.unavailable();
            }

            targetNode = trace.node;
            simulated.position = trace.node;
            simulated.route = trace.route;
            stepsUsed += 1;
            if (trace.node == BoardPath.END_NODE) {
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

    public int getNormalRollAllowance() {
        return normalRollAllowance;
    }

    public SavedState saveState() {
        SavedState state = new SavedState();
        state.teamCount = teamCount;
        state.currentTeam = currentTeam;
        state.nextResultId = nextResultId;
        state.rollAllowed = rollAllowed;
        state.mustRollBeforeMoving = mustRollBeforeMoving;
        state.normalRollAllowance = normalRollAllowance;
        state.catchBonusPending = catchBonusPending;
        state.gameOver = gameOver;
        state.captureBonusStacks = captureBonusStacks;

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
        boolean restoredRollAllowed = state.rollAllowed;
        mustRollBeforeMoving = state.mustRollBeforeMoving;
        catchBonusPending = state.catchBonusPending;
        gameOver = state.gameOver;
        captureBonusStacks = state.captureBonusStacks;

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
        normalRollAllowance = state.normalRollAllowance >= 0
                ? clamp(state.normalRollAllowance, 0, MAX_TEAM_COUNT * PIECE_COUNT)
                : deriveNormalRollAllowance(restoredRollAllowed);
        rollAllowed = normalRollAllowance > 0;

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

    public boolean isSameBoardSpot(int firstNode, int secondNode) {
        return boardPath.isSameBoardSpot(firstNode, secondNode);
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
            names.add(text.resultName(result.steps));
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

    public String getLocalizedResultName(int steps) {
        return text.resultName(steps);
    }

    public String getLocalizedTeamName(int teamId) {
        return text.teamName(teamId);
    }

    public String getWaitingPieceBackDoMessage() {
        return text.waitingPieceBackDo();
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
        // Old saved games used stacking. New engines default to the one-bonus rule.
        public boolean captureBonusStacks = true;
        public int teamCount;
        public int currentTeam;
        public int nextResultId;
        public boolean rollAllowed;
        public boolean mustRollBeforeMoving;
        public boolean catchBonusPending;
        public int normalRollAllowance = -1;
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
        public int captureEventCount() {
            int count = 0;
            for (MoveAnimation segment : animationSegments) {
                if (!segment.caughtPieces.isEmpty()) count++;
            }
            return count;
        }

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
        public int grantedCaptureRolls;
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
        public boolean bonusRoll;
        public final ArrayList<Integer> pieceIds = new ArrayList<>();
        public final ArrayList<Integer> arrivedPieceIds = new ArrayList<>();
        public final ArrayList<PieceRef> caughtPieces = new ArrayList<>();
        public final int startNode;
        public final ArrayList<Integer> path = new ArrayList<>();

        MoveAnimation(List<Integer> pieceIds, int startNode, List<Integer> path) {
            this.pieceIds.addAll(pieceIds);
            this.arrivedPieceIds.addAll(pieceIds);
            this.startNode = startNode;
            this.path.addAll(path);
        }
    }
}
