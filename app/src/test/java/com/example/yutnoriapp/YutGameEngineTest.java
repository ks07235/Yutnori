package com.example.yutnoriapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YutGameEngineTest {
    @Test
    public void doFromStartMovesOneStepOntoBoardAndChangesTurn() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.success);
        assertEquals(16, game.getPiece(0, 0).position);
        assertTrue(result.turnChanged);
        assertEquals(1, game.getCurrentTeam());
    }

    @Test
    public void yutCanBeSavedBeforeMoving() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(4);

        assertTrue(game.isRollAllowed());
        assertEquals(1, game.getPendingResults().size());
        assertEquals(4, (int) game.getPendingResults().get(0));
    }

    @Test
    public void canEnterYutAfterGaeBeforeMoving() {
        YutGameEngine game = new YutGameEngine();

        YutGameEngine.ActionResult gae = game.addRoll(2);
        YutGameEngine.ActionResult yut = game.addRoll(4);

        assertTrue(gae.success);
        assertTrue(yut.success);
        assertEquals(2, game.getPendingResults().size());
        assertEquals(2, (int) game.getPendingResults().get(0));
        assertEquals(4, (int) game.getPendingResults().get(1));
    }

    @Test
    public void canUndoLastRollBeforeMoving() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(2);
        game.addRoll(4);
        YutGameEngine.ActionResult result = game.undoLastRoll();

        assertTrue(result.success);
        assertEquals(1, game.getPendingResults().size());
        assertEquals(2, (int) game.getPendingResults().get(0));
    }

    @Test
    public void undoRemovesSelectedResultOrder() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(2);
        game.addRoll(4);
        game.selectResult(1);
        game.undoLastRoll();

        assertEquals(0, game.getSelectedSteps().size());
    }

    @Test
    public void undoAfterCatchRestoresRequiredBonusRollState() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult caught = game.moveSelectedPiece(0, 0);
        assertTrue(caught.caught);

        game.addRoll(2);
        assertTrue(game.undoLastRoll().success);
        assertFalse(game.selectResult(0).success);
    }

    @Test
    public void backDoFromStartIsUnavailableAndCannotConsumeResult() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(-1);
        game.selectResult(0);

        YutGameEngine.MovePreview preview = game.previewMove(0, 0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertFalse(preview.available);
        assertFalse(result.success);
        assertEquals(BoardPath.START_NODE, game.getPiece(0, 0).position);
        assertEquals(1, game.getPendingResults().size());
        assertEquals(-1, (int) game.getPendingResults().get(0));
        assertEquals(1, game.getSelectedSteps().size());
        assertEquals(0, game.getCurrentTeam());
    }

    @Test
    public void secondNormalResultIsRejectedWithoutChangingPendingPlan() {
        YutGameEngine game = new YutGameEngine();

        assertTrue(game.addRoll(-1).success);
        assertFalse(game.addRoll(1).success);
        game.selectResult(0);

        assertFalse(game.previewMove(0, 0).available);
        assertFalse(game.moveSelectedPiece(0, 0).success);
        assertEquals(BoardPath.START_NODE, game.getPiece(0, 0).position);
        assertEquals(1, game.getPendingResults().size());
        assertEquals(-1, (int) game.getPendingResults().get(0));
        assertEquals(1, game.getSelectedSteps().size());
        assertEquals(0, game.getNormalRollAllowance());
    }

    @Test
    public void undoingNormalResultRestoresNormalInputAllowance() {
        YutGameEngine game = new YutGameEngine();

        assertTrue(game.addRoll(1).success);
        assertFalse(game.addRoll(-1).success);
        assertEquals(0, game.getNormalRollAllowance());

        assertTrue(game.undoLastRoll().success);

        assertEquals(1, game.getNormalRollAllowance());
        assertTrue(game.addRoll(-1).success);
        assertEquals(1, game.getPendingResults().size());
        assertEquals(-1, (int) game.getPendingResults().get(0));
        assertEquals(0, game.getNormalRollAllowance());
    }

    @Test
    public void backDoMovesPieceAlreadyOnBoard() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 17;

        game.addRoll(-1);
        game.selectResult(0);

        YutGameEngine.MovePreview preview = game.previewMove(0, 0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(preview.available);
        assertEquals(16, preview.targetNode);
        assertTrue(result.success);
        assertEquals(16, game.getPiece(0, 0).position);
    }

    @Test
    public void singleResultPreviewMatchesCommittedMoveAcrossEveryBoardRoute() {
        java.util.ArrayList<int[]> boardStates = new java.util.ArrayList<>();
        for (int position = 0; position <= 19; position++) {
            boardStates.add(new int[]{position, 0});
        }
        for (int position = 20; position <= 24; position++) {
            boardStates.add(new int[]{position, 1});
        }
        for (int position = 25; position <= 29; position++) {
            boardStates.add(new int[]{position, 3});
        }
        boardStates.add(new int[]{10, 3});
        boardStates.add(new int[]{15, 1});

        int[] results = {-1, 1, 2, 3, 4, 5};
        for (int[] state : boardStates) {
            for (int steps : results) {
                YutGameEngine game = new YutGameEngine();
                Piece piece = game.getPiece(0, 0);
                piece.position = state[0];
                piece.route = state[1];
                game.addRoll(steps);
                game.selectResult(0);

                String scenario = "node=" + state[0] + ", route=" + state[1] + ", steps=" + steps;
                YutGameEngine.MovePreview preview = game.previewMove(0, 0);
                YutGameEngine.MoveResult move = game.moveSelectedPiece(0, 0);

                assertTrue(scenario, preview.available);
                assertTrue(scenario, move.success);
                assertEquals(scenario, preview.targetNode, move.targetNode);
                assertEquals(scenario, preview.targetNode, game.getPiece(0, 0).position);
            }
        }
    }

    @Test
    public void endTurnDiscardsUnusableWaitingBackDo() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(-1);
        game.selectResult(0);

        assertTrue(game.endTurn().success);
        assertEquals(1, game.getCurrentTeam());
        assertEquals(0, game.getPendingResults().size());
        assertEquals(BoardPath.START_NODE, game.getPiece(0, 0).position);
    }

    @Test
    public void canConfigureThreeTeamGameAndCycleTurns() {
        YutGameEngine game = new YutGameEngine();
        assertTrue(game.setTeamCount(3).success);

        game.getPiece(0, 0).position = 1;
        game.addRoll(2);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).turnChanged);
        assertEquals(1, game.getCurrentTeam());

        game.getPiece(1, 0).position = 3;
        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(1, 0).turnChanged);
        assertEquals(2, game.getCurrentTeam());

        game.getPiece(2, 0).position = 6;
        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(2, 0).turnChanged);
        assertEquals(0, game.getCurrentTeam());
    }

    @Test
    public void canConfigureFourTeamGame() {
        YutGameEngine game = new YutGameEngine();

        assertTrue(game.setTeamCount(4).success);

        assertEquals(4, game.getTeamCount());
    }

    @Test
    public void friendlyPiecesMeetingAtCenterBecomeOneMovingGroup() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 21;
        game.getPiece(0, 0).route = 1;
        game.getPiece(0, 1).position = 29;
        game.getPiece(0, 1).route = 3;

        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).success);

        assertEquals(22, game.getPiece(0, 0).position);
        assertEquals(22, game.getPiece(0, 1).position);
        assertEquals(1, game.getPiece(0, 1).route);
    }

    @Test
    public void landingOnAlternateCenterNodeCapturesOpponent() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 21;
        game.getPiece(0, 0).route = 1;
        game.getPiece(1, 0).position = 29;
        game.getPiece(1, 0).route = 3;

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.success);
        assertTrue(result.caught);
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 0).position);
    }

    @Test
    public void piecesAlreadyOnBothCenterRoutesMoveTogether() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 22;
        game.getPiece(0, 0).route = 1;
        game.getPiece(0, 1).position = 29;
        game.getPiece(0, 1).route = 3;

        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).success);

        assertEquals(23, game.getPiece(0, 0).position);
        assertEquals(23, game.getPiece(0, 1).position);
    }

    @Test
    public void twoTeamGameCyclesOnlyBetweenFirstTwoTeams() {
        YutGameEngine game = new YutGameEngine();
        assertTrue(game.setTeamCount(2).success);

        game.addRoll(2);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).turnChanged);
        assertEquals(1, game.getCurrentTeam());

        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(1, 0).turnChanged);
        assertEquals(0, game.getCurrentTeam());
    }

    @Test
    public void twoTeamGameRejectsNonParticipatingTeamMove() {
        YutGameEngine game = new YutGameEngine();
        assertTrue(game.setTeamCount(2).success);

        game.addRoll(1);
        game.selectResult(0);

        assertFalse(game.moveSelectedPiece(2, 0).success);
    }

    @Test
    public void changingTeamCountResetsPreviousGameState() {
        YutGameEngine game = new YutGameEngine();
        assertTrue(game.setTeamCount(4).success);
        game.addRoll(2);
        game.selectResult(0);
        game.getPiece(3, 0).position = 16;

        assertTrue(game.setTeamCount(2).success);

        assertEquals(2, game.getTeamCount());
        assertEquals(0, game.getCurrentTeam());
        assertEquals(0, game.getPendingResults().size());
        assertEquals(BoardPath.START_NODE, game.getPiece(3, 0).position);
        assertFalse(game.moveSelectedPiece(3, 0).success);
    }

    @Test
    public void catchesEveryOtherTeamOnTargetNode() {
        YutGameEngine game = new YutGameEngine();
        game.setTeamCount(3);
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;
        game.getPiece(2, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.caught);
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 0).position);
        assertEquals(BoardPath.START_NODE, game.getPiece(2, 0).position);
        assertEquals(2, result.caughtPieces.size());
    }

    @Test
    public void catchingStackedOpponentPiecesReturnsAllToStart() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;
        game.getPiece(1, 1).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.caught);
        assertEquals(2, result.caughtPieces.size());
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 0).position);
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 1).position);
    }

    @Test
    public void animationSegmentRecordsCaughtPiecesAtThatStep() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.caught);
        assertEquals(1, result.animationSegments.size());
        assertEquals(1, result.animationSegments.get(0).caughtPieces.size());
        assertEquals(1, result.animationSegments.get(0).caughtPieces.get(0).teamId);
    }

    @Test
    public void yutAndGaeCanBeUsedInUserSelectedOrder() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(1, 0).position = 17;

        game.addRoll(4);
        game.addRoll(2);

        assertEquals("\uc737", game.getMoveChoices().get(0).label);
        assertEquals("\uac1c", game.getMoveChoices().get(1).label);

        game.selectResult(1);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.caught);
        assertEquals(17, game.getPiece(0, 0).position);
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 0).position);
        assertEquals(1, game.getPendingResults().size());
        assertEquals(4, (int) game.getPendingResults().get(0));
    }

    @Test
    public void multipleResultsMoveInSelectedOrder() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(4);
        game.addRoll(2);
        game.selectResult(1);
        game.selectResult(0);

        assertEquals("2. \uc737", game.getMoveChoices().get(0).label);
        assertEquals("1. \uac1c", game.getMoveChoices().get(1).label);

        YutGameEngine.MovePreview preview = game.previewMove(0, 0);
        assertTrue(preview.available);
        assertEquals(1, preview.targetNode);

        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.success);
        assertEquals(1, game.getPiece(0, 0).position);
        assertEquals(0, game.getPendingResults().size());
    }

    @Test
    public void selectingSameResultAgainClearsSelectionAndPreventsMove() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(2);
        assertTrue(game.selectResult(0).success);
        assertEquals(1, game.getSelectedSteps().size());
        assertTrue(game.selectResult(0).success);

        assertEquals(0, game.getSelectedSteps().size());
        assertFalse(game.moveSelectedPiece(0, 0).success);
    }

    @Test
    public void manualEndTurnClearsPendingResultsAndCyclesTeam() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(4);
        game.addRoll(2);
        game.selectResult(0);
        YutGameEngine.ActionResult result = game.endTurn();

        assertTrue(result.success);
        assertEquals(1, game.getCurrentTeam());
        assertEquals(0, game.getPendingResults().size());
        assertEquals(0, game.getSelectedSteps().size());
        assertTrue(game.isRollAllowed());
    }

    @Test
    public void manualEndTurnOverridesCatchBonusState() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).caught);
        assertTrue(game.endTurn().success);

        assertEquals(1, game.getCurrentTeam());
        game.addRoll(1);
        assertTrue(game.selectResult(0).success);
    }

    @Test
    public void moveResultIncludesStepByStepAnimationPath() {
        YutGameEngine game = new YutGameEngine();

        game.addRoll(3);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.success);
        assertEquals(3, result.animationPath.size());
        assertEquals(16, (int) result.animationPath.get(0));
        assertEquals(17, (int) result.animationPath.get(1));
        assertEquals(18, (int) result.animationPath.get(2));
    }

    @Test
    public void animationSegmentsReflectPiecesJoiningMidMove() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 1).position = 19;

        game.addRoll(4);
        game.addRoll(1);
        game.selectResult(0);
        game.selectResult(1);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.success);
        assertEquals(2, result.animationSegments.size());
        assertEquals(1, result.animationSegments.get(0).pieceIds.size());
        assertEquals(2, result.animationSegments.get(0).arrivedPieceIds.size());
        assertTrue(result.animationSegments.get(0).arrivedPieceIds.contains(0));
        assertTrue(result.animationSegments.get(0).arrivedPieceIds.contains(1));
        assertEquals(2, result.animationSegments.get(1).pieceIds.size());
        assertTrue(result.animationSegments.get(1).pieceIds.contains(0));
        assertTrue(result.animationSegments.get(1).pieceIds.contains(1));
    }

    @Test
    public void differentSelectedOrderCanReachDifferentNode() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 1;

        game.addRoll(4);
        game.addRoll(2);
        game.selectResult(0);
        game.selectResult(1);

        YutGameEngine.MovePreview preview = game.previewMove(0, 0);

        assertTrue(preview.available);
        assertEquals(21, preview.targetNode);
    }

    @Test
    public void piecesOnSameNodeMoveTogether() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(0, 1).position = 16;

        game.addRoll(2);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.success);
        assertEquals(18, game.getPiece(0, 0).position);
        assertEquals(18, game.getPiece(0, 1).position);
    }

    @Test
    public void catchingOpponentReturnsOpponentToStartAndRequiresExtraRoll() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);

        assertTrue(result.caught);
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 0).position);
        assertFalse(game.selectResult(0).success);

        game.addRoll(1);
        assertTrue(game.selectResult(0).success);
    }

    @Test
    public void shortcutFromTopRightCornerUsesCenterPath() {
        BoardPath path = new BoardPath();
        Piece piece = new Piece(0, 0);
        piece.position = 5;

        BoardPath.MoveTarget target = path.calculate(piece, 3);

        assertEquals(22, target.node);
        assertEquals(1, target.route);
    }

    @Test
    public void backDoFollowsShortcutHistory() {
        BoardPath path = new BoardPath();
        Piece piece = new Piece(0, 0);
        piece.position = 15;
        piece.route = 1;

        BoardPath.MoveTarget target = path.calculate(piece, -1);

        assertEquals(24, target.node);
        assertEquals(1, target.route);
    }

    @Test
    public void fourFinishedPiecesWinGame() {
        YutGameEngine game = new YutGameEngine();
        for (int id = 0; id < 3; id++) {
            Piece piece = game.getPiece(0, id);
            piece.position = BoardPath.END_NODE;
            piece.isFinished = true;
        }
        Piece lastPiece = game.getPiece(0, 3);
        lastPiece.position = 14;

        game.addRoll(2);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 3);

        assertTrue(result.gameWon);
        assertTrue(game.isGameOver());
    }

    @Test
    public void gameOverBlocksFurtherRollsAndMoves() {
        YutGameEngine game = new YutGameEngine();
        for (int id = 0; id < 3; id++) {
            Piece piece = game.getPiece(0, id);
            piece.position = BoardPath.END_NODE;
            piece.isFinished = true;
        }
        Piece lastPiece = game.getPiece(0, 3);
        lastPiece.position = 14;

        game.addRoll(2);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 3).gameWon);

        assertFalse(game.addRoll(1).success);
        assertFalse(game.moveSelectedPiece(0, 0).success);
        assertFalse(game.endTurn().success);
    }

    @Test
    public void savedStateRestoresTeamsPiecesPendingResultsAndSelectionOrder() {
        YutGameEngine game = new YutGameEngine();
        assertTrue(game.setTeamCount(3).success);
        game.getPiece(0, 0).position = 5;
        game.getPiece(0, 0).route = 1;
        game.getPiece(2, 3).position = BoardPath.END_NODE;
        game.getPiece(2, 3).isFinished = true;

        game.addRoll(4);
        game.addRoll(2);
        game.selectResult(1);
        game.selectResult(0);

        YutGameEngine.SavedState state = game.saveState();
        YutGameEngine restored = new YutGameEngine();
        restored.restoreState(state);

        assertEquals(3, restored.getTeamCount());
        assertEquals(0, restored.getCurrentTeam());
        assertEquals(2, restored.getPendingResults().size());
        assertEquals(4, (int) restored.getPendingResults().get(0));
        assertEquals(2, (int) restored.getPendingResults().get(1));
        assertEquals(2, restored.getSelectedSteps().size());
        assertEquals(2, (int) restored.getSelectedSteps().get(0));
        assertEquals(4, (int) restored.getSelectedSteps().get(1));
        assertEquals(5, restored.getPiece(0, 0).position);
        assertEquals(1, restored.getPiece(0, 0).route);
        assertTrue(restored.getPiece(2, 3).isFinished);
    }

    @Test
    public void savedStateRestoresCatchBonusState() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).caught);

        YutGameEngine restored = new YutGameEngine();
        restored.restoreState(game.saveState());

        assertFalse(restored.selectResult(0).success);
        assertTrue(restored.addRoll(1).success);
        assertTrue(restored.selectResult(0).success);
    }

    @Test
    public void restoreRejectsInvalidPiecePositionAndRoute() {
        YutGameEngine game = new YutGameEngine();
        YutGameEngine.SavedState state = game.saveState();
        state.piecePositions[0] = 999;
        state.pieceRoutes[0] = 7;

        game.restoreState(state);

        assertEquals(BoardPath.START_NODE, game.getPiece(0, 0).position);
        assertEquals(0, game.getPiece(0, 0).route);
    }

    @Test
    public void preMoveSnapshotRestoresStackAndCapturedOpponent() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(0, 1).position = 16;
        game.getPiece(1, 0).position = 17;
        game.addRoll(1);
        game.selectResult(0);

        YutGameEngine.SavedState beforeMove = game.saveState();
        YutGameEngine.MoveResult move = game.moveSelectedPiece(0, 0);

        assertTrue(move.success);
        assertTrue(move.caught);
        assertEquals(BoardPath.START_NODE, game.getPiece(1, 0).position);

        game.restoreState(beforeMove);

        assertEquals(16, game.getPiece(0, 0).position);
        assertEquals(16, game.getPiece(0, 1).position);
        assertEquals(17, game.getPiece(1, 0).position);
        assertEquals(1, game.getPendingResults().size());
        assertEquals(1, game.getSelectedSteps().size());
        assertEquals(0, game.getCurrentTeam());
    }

    @Test
    public void restoreNormalizesFinishedPieceToEndNode() {
        YutGameEngine game = new YutGameEngine();
        YutGameEngine.SavedState state = game.saveState();
        state.piecePositions[0] = 7;
        state.pieceFinished[0] = true;

        game.restoreState(state);

        assertEquals(BoardPath.END_NODE, game.getPiece(0, 0).position);
        assertTrue(game.getPiece(0, 0).isFinished);
    }

    @Test
    public void restoreDropsDuplicateAndInvalidPendingResults() {
        YutGameEngine game = new YutGameEngine();
        YutGameEngine.SavedState state = game.saveState();
        state.pendingIds = new int[] {4, 4, -1, 8};
        state.pendingSteps = new int[] {2, 5, 3, 99};
        state.selectedResultIds = new int[] {4, 4, -1};
        state.nextResultId = 1;
        state.normalRollAllowance = -1;

        game.restoreState(state);

        assertEquals(1, game.getPendingResults().size());
        assertEquals(2, (int) game.getPendingResults().get(0));
        assertEquals(1, game.getSelectedSteps().size());
        assertFalse(game.addRoll(1).success);
        assertTrue(game.addRoll(4).success);
        assertEquals(2, game.getPendingResults().size());
    }

    @Test
    public void restoreClearsPiecesFromTeamsOutsideConfiguredGame() {
        YutGameEngine game = new YutGameEngine();
        YutGameEngine.SavedState state = game.saveState();
        state.teamCount = 2;
        int teamThreePieceZero = 2 * YutGameEngine.PIECE_COUNT;
        state.piecePositions[teamThreePieceZero] = 10;

        game.restoreState(state);

        assertEquals(BoardPath.START_NODE, game.getPiece(2, 0).position);
    }

    @Test
    public void englishLanguageLocalizesGameplayText() {
        YutGameEngine game = new YutGameEngine(false);

        assertEquals("Team 1", game.getLocalizedTeamName(0));
        assertEquals("Back Do", game.getLocalizedResultName(-1));
        assertTrue(game.setTeamCount(2).message.contains("2-team"));
        assertTrue(game.addRoll(4).message.contains("Yut"));
        assertEquals("Yut", game.getMoveChoices().get(0).label);
        assertTrue(game.getWaitingPieceBackDoMessage().contains("cannot use Back Do"));
    }

    @Test
    public void gaeAndYutConsumeTheSameAllowanceInEitherInputOrder() {
        YutGameEngine gaeFirst = new YutGameEngine();
        YutGameEngine yutFirst = new YutGameEngine();

        assertTrue(gaeFirst.addRoll(2).success);
        assertTrue(gaeFirst.addRoll(4).success);
        assertTrue(yutFirst.addRoll(4).success);
        assertTrue(yutFirst.addRoll(2).success);

        assertEquals(0, gaeFirst.getNormalRollAllowance());
        assertEquals(0, yutFirst.getNormalRollAllowance());
        assertEquals(2, gaeFirst.getPendingResults().size());
        assertEquals(2, yutFirst.getPendingResults().size());
        assertFalse(gaeFirst.addRoll(1).success);
        assertFalse(yutFirst.addRoll(-1).success);
    }

    @Test
    public void multipleYutAndMoRemainValidButOnlyOneNormalResultIsAccepted() {
        YutGameEngine game = new YutGameEngine();

        assertTrue(game.addRoll(4).success);
        assertTrue(game.addRoll(5).success);
        assertTrue(game.addRoll(4).success);
        assertEquals(1, game.getNormalRollAllowance());

        assertTrue(game.addRoll(3).success);
        assertEquals(0, game.getNormalRollAllowance());
        assertFalse(game.addRoll(2).success);
        assertEquals(4, game.getPendingResults().size());
    }

    @Test
    public void captureGrantsExactlyOneAdditionalNormalInput() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(0, 0).position = 16;
        game.getPiece(1, 0).position = 17;

        game.addRoll(1);
        game.selectResult(0);
        assertTrue(game.moveSelectedPiece(0, 0).caught);
        assertEquals(1, game.getNormalRollAllowance());

        assertTrue(game.addRoll(2).success);
        assertEquals(0, game.getNormalRollAllowance());
        assertFalse(game.addRoll(3).success);
    }

    @Test
    public void normalInputAllowanceSurvivesSaveAndRestore() {
        YutGameEngine game = new YutGameEngine();
        game.addRoll(4);
        game.addRoll(2);

        YutGameEngine restored = new YutGameEngine();
        restored.restoreState(game.saveState());

        assertEquals(0, restored.getNormalRollAllowance());
        assertFalse(restored.addRoll(1).success);
        assertTrue(restored.addRoll(5).success);
        assertEquals(3, restored.getPendingResults().size());
    }

    @Test
    public void separateCaptureLandingsEachGrantOneAdditionalInput() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(1, 0).position = 16;
        game.getPiece(1, 1).position = 0;
        game.addRoll(1);
        game.addRoll(4);
        game.selectResult(0);
        game.selectResult(1);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);
        assertEquals(2, result.captureEventCount());
        assertEquals(2, game.getNormalRollAllowance());
        assertTrue(game.addRoll(1).success);
        assertTrue(game.addRoll(2).success);
        assertFalse(game.addRoll(3).success);
    }

    @Test
    public void capturingAStackStillGrantsOnlyOneAdditionalInput() {
        YutGameEngine game = new YutGameEngine();
        game.getPiece(1, 0).position = 16;
        game.getPiece(1, 1).position = 16;
        game.addRoll(1);
        game.selectResult(0);
        YutGameEngine.MoveResult result = game.moveSelectedPiece(0, 0);
        assertEquals(2, result.caughtPieces.size());
        assertEquals(1, result.captureEventCount());
        assertEquals(1, game.getNormalRollAllowance());
    }
}
