package com.example.yutnoriapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Configuration;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.TextViewCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BoardPresentation {
    interface Events {
        boolean isGameStarted();
        boolean isAnimating();
        boolean reducedMotion();
        int selectedTeam();
        int selectedPiece();
        int color(int team);
        int shape(int team);
        void selectPiece(int team, int piece);
        void commitMove(View source);
        void updateFinishedSummary();
        void onFinishVisibilityChanged();
        void playCatchFeedback();
        void finishUndoAnimation();
    }

    private final android.content.Context context;
    private final ConstraintLayout root;
    private final BoardOverlayLayout boardOverlay;
    private final LinearLayout waitingArea;
    private final YutGameEngine game;
    private final GameScreenLayout layout;
    private final Events events;
    private final PieceStackView[][] pieceViews =
            new PieceStackView[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
    private final FrameLayout[][] waitSpots =
            new FrameLayout[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
    private final ArrayList<View> previewViews = new ArrayList<>();
    private final ArrayList<View> pieceGuideViews = new ArrayList<>();
    private final ArrayList<View> undoGhosts = new ArrayList<>();
    private final ArrayList<Animator> moveAnimators = new ArrayList<>();
    private TextView finishDestination;
    private PieceStackView finishAnimationView;
    private int generation, movePreviewGeneration, pieceGuideGeneration;
    private boolean disposed;

    BoardPresentation(android.content.Context context, ConstraintLayout root,
            YutGameEngine game, GameScreenLayout layout, Events events) {
        this.context = context;
        this.root = root;
        this.game = game;
        this.layout = layout;
        this.events = events;
        boardOverlay = root.findViewById(R.id.board_overlay);
        waitingArea = root.findViewById(R.id.waiting_area);
    }

    private <T extends View> T findViewById(int id) { return root.findViewById(id); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private int getTeamColor(int team) { return TeamAppearance.label(events.color(team)); }
    private void removeFromParent(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }
    void removePiece(int team, int piece) { removeFromParent(pieceViews[team][piece]); }

    void postRestore(Runnable restore) {
        int expected = generation;
        boardOverlay.post(() -> {
            if (!disposed && expected == generation) restore.run();
        });
    }

    void cancel() {
        // Cancellation may synchronously run onAnimationEnd.
        generation++;
        for (View ghost : undoGhosts) root.getOverlay().remove(ghost);
        undoGhosts.clear();
        for (Animator animator : new ArrayList<>(moveAnimators)) animator.cancel();
        moveAnimators.clear();
        clearFinishAnimation();
        for (PieceStackView[] team : pieceViews) {
            for (PieceStackView piece : team) if (piece != null) piece.animate().cancel();
        }
        clearMovePreviews();
        clearPieceGuides();
    }

    void dispose() {
        disposed = true;
        cancel();
        for (int team = 0; team < pieceViews.length; team++) {
            for (int id = 0; id < pieceViews[team].length; id++) {
                if (pieceViews[team][id] != null) pieceViews[team][id].setOnClickListener(null);
                if (waitSpots[team][id] != null) waitSpots[team][id].setOnClickListener(null);
            }
        }
        if (finishDestination != null) finishDestination.setOnClickListener(null);
    }

    void createPieceViews() {
        for (int team = 0; team < YutGameEngine.TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                PieceStackView view = new PieceStackView(context);
                view.configureAppearance(events.color(team), events.shape(team), id + 1);
                view.setElevation(dp(7));
                view.setContentDescription(context.getString(R.string.piece_description, game.getLocalizedTeamName(team), id + 1));

                final int teamIndex = team;
                final int pieceIndex = id;
                view.setOnClickListener(v -> { if (!disposed) events.selectPiece(teamIndex, pieceIndex); });
                pieceViews[team][id] = view;
            }
        }
    }

    void applyTeamAppearance() {
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                pieceViews[team][id].configureAppearance(events.color(team), events.shape(team), id + 1);
            }
        }
    }

    void createWaitingTeamRow(int team) {
        boolean vertical = layout.usesVerticalWaitingTray();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                vertical ? LinearLayout.LayoutParams.MATCH_PARENT : dp(48));
        waitingArea.addView(row, rowParams);

        TextView label = new TextView(context);
        label.setText(game.getLocalizedTeamName(team));
        label.setTextColor(getTeamColor(team));
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                label,
                10, 13, 1,
                TypedValue.COMPLEX_UNIT_SP);
        row.addView(label, new LinearLayout.LayoutParams(
                vertical ? LinearLayout.LayoutParams.MATCH_PARENT : dp(40),
                vertical ? dp(32) : LinearLayout.LayoutParams.WRAP_CONTENT));
        if (vertical) label.setGravity(Gravity.CENTER);

        LinearLayout spots = new LinearLayout(context);
        spots.setGravity(Gravity.CENTER_VERTICAL);
        spots.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.addView(spots, vertical
                ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            FrameLayout spot = new FrameLayout(context);
            spot.setBackgroundResource(R.drawable.shape_wait_spot_touch);
            spot.setClipChildren(false);
            spot.setClipToPadding(false);
            final int pieceId = id;
            spot.setContentDescription(context.getString(R.string.waiting_piece_description, game.getLocalizedTeamName(team), id + 1));
            spot.setClickable(true);
            spot.setFocusable(true);
            spot.setOnClickListener(v -> { if (!disposed) events.selectPiece(team, pieceId); });
            LinearLayout.LayoutParams spotParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            int spotMargin = 0;
            spotParams.setMargins(spotMargin, vertical && id > 0 ? dp(layout.landscapeSpacing()) : 0, spotMargin, 0);
            spots.addView(spot, spotParams);
            waitSpots[team][id] = spot;
        }
    }

    void rebuildCurrentWaitingArea() {
        events.updateFinishedSummary();
        waitingArea.removeAllViews();
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                waitSpots[team][id] = null;
            }
        }

        if (game.isGameOver()) {
            return;
        }

        createWaitingTeamRow(game.getCurrentTeam());
        syncWaitingPiecesForCurrentTurn();
    }

    void syncWaitingPiecesForCurrentTurn() {
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                Piece piece = game.getPiece(team, id);
                if (team >= game.getTeamCount()
                        || piece.isFinished
                        || piece.position != BoardPath.START_NODE) {
                    continue;
                }

                if (team == game.getCurrentTeam()) {
                    moveToWaitSpot(team, id);
                } else {
                    removeFromParent(pieceViews[team][id]);
                }
            }
        }
    }

    void animateMoveResult(YutGameEngine.MoveResult result, Runnable onComplete) {
        if (events.reducedMotion() || result.animationSegments.isEmpty()) {
            onComplete.run();
            return;
        }

        int animationGeneration = generation;
        boardOverlay.post(() -> {
            if (animationGeneration == generation) {
                animateMoveSegment(result, 0, onComplete, animationGeneration);
            }
        });
    }

    void animateMoveSegment(YutGameEngine.MoveResult result, int segmentIndex, Runnable onComplete, int animationGeneration) {
        if (animationGeneration != generation) {
            return;
        }
        if (segmentIndex >= result.animationSegments.size()) {
            onComplete.run();
            return;
        }

        YutGameEngine.MoveAnimation segment = result.animationSegments.get(segmentIndex);
        ArrayList<Integer> boardPath = new ArrayList<>();
        for (int node : segment.path) {
            if (node != BoardPath.START_NODE) {
                boardPath.add(node);
            }
        }

        Runnable nextSegment = () -> {
            if (animationGeneration == generation) {
                animateMoveSegment(result, segmentIndex + 1, onComplete, animationGeneration);
            }
        };
        if (segment.pieceIds.isEmpty() || boardPath.isEmpty()) {
            for (YutGameEngine.PieceRef caughtPiece : segment.caughtPieces) {
                moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
            }
            nextSegment.run();
            return;
        }

        int representativeId = prepareAnimatedBoardGroup(result.teamId, segment.pieceIds, segment.startNode);
        PieceStackView movingView = pieceViews[result.teamId][representativeId];
        animateStackLayout(
                movingView,
                1f,
                segment.pieceIds.size() > 1 ? 130L : 60L,
                () -> animatePathStep(
                        result.teamId,
                        representativeId,
                        segment,
                        boardPath,
                        0,
                        nextSegment,
                        animationGeneration));
    }

    void animatePathStep(
            int teamId,
            int pieceId,
            YutGameEngine.MoveAnimation segment,
            List<Integer> path,
            int index,
            Runnable onComplete,
            int animationGeneration) {
        if (animationGeneration != generation) {
            return;
        }
        if (index >= path.size()) {
            int arrivalNode = path.get(path.size() - 1);
            Runnable showArrival = () -> animateArrivedBoardGroup(
                    teamId,
                    pieceId,
                    segment,
                    arrivalNode,
                    onComplete,
                    animationGeneration);
            if (segment.caughtPieces.isEmpty()) {
                showArrival.run();
            } else {
                animateCapturedPieces(segment.caughtPieces, showArrival, animationGeneration);
            }
            return;
        }

        int node = path.get(index);
        if (node == BoardPath.END_NODE) {
            animateFinishEntry(teamId, pieceId, segment.pieceIds.size(), onComplete, animationGeneration);
            return;
        }
        int duration = Math.max(110, 190 - Math.min(index, 4) * 12);
        Runnable nextStep = () -> animatePathStep(
                teamId,
                pieceId,
                segment,
                path,
                index + 1,
                onComplete,
                animationGeneration);
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        boardOverlay.releaseAnchor(pieceView);
        float[] target = getBoardNodePosition(node, getBoardPieceViewSize());
        pieceView.bringToFront();
        pieceView.animate()
                .x(target[0])
                .y(target[1])
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(duration)
                .withEndAction(nextStep)
                .start();
    }

    void animateFinishEntry(int teamId, int pieceId, int count, Runnable onComplete,
            int animationGeneration) {
        ViewGroup root = findViewById(R.id.root_layout);
        PieceStackView source = pieceViews[teamId][pieceId];
        int[] rootLocation = new int[2];
        int[] sourceLocation = new int[2];
        int[] targetLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        source.getLocationOnScreen(sourceLocation);
        finishDestination.getLocationOnScreen(targetLocation);
        int size = getBoardPieceViewSize();
        float startX = sourceLocation[0] - rootLocation[0];
        float startY = sourceLocation[1] - rootLocation[1];
        float endX = targetLocation[0] - rootLocation[0] + (finishDestination.getWidth() - size) / 2f;
        float endY = targetLocation[1] - rootLocation[1] + (finishDestination.getHeight() - size) / 2f;

        // The root overlay carries the piece beyond the board without clipping or changing layout.
        PieceStackView ghost = new PieceStackView(context);
        ghost.configureAppearance(events.color(teamId), events.shape(teamId), pieceId + 1);
        ghost.setGroupCount(count);
        ghost.setCollapseProgress(1f);
        ghost.setVisualDiameterPx(getBoardPieceSize());
        ghost.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ghost.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        ghost.layout(0, 0, size, size);
        ghost.setX(startX);
        ghost.setY(startY);
        finishAnimationView = ghost;
        root.getOverlay().add(ghost);
        removeFromParent(source);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimators.add(animator);
        animator.setDuration(600L);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            float travel = Math.min(1f, progress / 0.7f);
            float eased = (float) (0.5 - Math.cos(Math.PI * travel) / 2);
            ghost.setX(startX + (endX - startX) * eased);
            ghost.setY(startY + (endY - startY) * eased);
            float fade = Math.max(0f, (progress - 0.7f) / 0.3f);
            ghost.setScaleX(1f - fade * 0.45f);
            ghost.setScaleY(1f - fade * 0.45f);
            ghost.setAlpha(1f - fade);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                moveAnimators.remove(animator);
                root.getOverlay().remove(ghost);
                if (finishAnimationView == ghost) finishAnimationView = null;
                if (!cancelled && animationGeneration == generation) onComplete.run();
            }
        });
        animator.start();
    }

    void clearFinishAnimation() {
        if (finishAnimationView != null) {
            ViewGroup root = findViewById(R.id.root_layout);
            root.getOverlay().remove(finishAnimationView);
            finishAnimationView = null;
        }
    }

    int prepareAnimatedBoardGroup(int teamId, List<Integer> pieceIds, int logicalNode) {
        int representativeId = getRepresentativePieceId(pieceIds);
        for (int id : pieceIds) {
            if (id != representativeId) {
                removeFromParent(pieceViews[teamId][id]);
            }
        }

        PieceStackView representative = pieceViews[teamId][representativeId];
        resetPieceViewTransform(representative);
        moveViewToParent(representative, boardOverlay);
        configureBoardGroupAtNode(representative, teamId, representativeId, pieceIds.size(), logicalNode);
        int size = getBoardPieceViewSize();
        representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        placePieceOnBoardNode(representative, logicalNode, size);
        representative.bringToFront();
        return representativeId;
    }

    void animateArrivedBoardGroup(
            int teamId,
            int movingPieceId,
            YutGameEngine.MoveAnimation segment,
            int logicalNode,
            Runnable onComplete,
            int animationGeneration) {
        if (animationGeneration != generation) {
            return;
        }
        List<Integer> arrivedIds = segment.arrivedPieceIds.isEmpty()
                ? segment.pieceIds
                : segment.arrivedPieceIds;
        int representativeId = getRepresentativePieceId(arrivedIds);
        PieceStackView representative = pieceViews[teamId][representativeId];
        for (int id : arrivedIds) {
            if (id != representativeId) {
                removeFromParent(pieceViews[teamId][id]);
            }
        }
        if (movingPieceId != representativeId) {
            removeFromParent(pieceViews[teamId][movingPieceId]);
        }

        resetPieceViewTransform(representative);
        moveViewToParent(representative, boardOverlay);
        configureBoardGroupAtNode(representative, teamId, representativeId, arrivedIds.size(), logicalNode);
        int size = getBoardPieceViewSize();
        representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        placePieceOnBoardNode(representative, logicalNode, size);
        representative.setCollapseProgress(1f);
        representative.bringToFront();

        animateStackLayout(representative, 0f, arrivedIds.size() > 1 ? 160L : 70L, () -> {
            ArrayList<Integer> animatedPiece = new ArrayList<>();
            animatedPiece.add(representativeId);
            bounceArrivedPieces(teamId, animatedPiece, () -> {
                if (animationGeneration == generation) {
                    onComplete.run();
                }
            });
        });
    }

    void animateCapturedPieces(
            List<YutGameEngine.PieceRef> caughtPieces,
            Runnable onComplete,
            int animationGeneration) {
        int capturedTeamCount = 0;
        for (int team = 0; team < game.getTeamCount(); team++) {
            for (YutGameEngine.PieceRef caughtPiece : caughtPieces) {
                if (caughtPiece.teamId == team) {
                    capturedTeamCount++;
                    break;
                }
            }
        }
        if (capturedTeamCount == 0) {
            onComplete.run();
            return;
        }

        events.playCatchFeedback();
        int[] remainingTeams = {capturedTeamCount};
        for (int team = 0; team < game.getTeamCount(); team++) {
            ArrayList<Integer> teamPieceIds = new ArrayList<>();
            for (YutGameEngine.PieceRef caughtPiece : caughtPieces) {
                if (caughtPiece.teamId == team) {
                    teamPieceIds.add(caughtPiece.pieceId);
                }
            }
            if (teamPieceIds.isEmpty()) {
                continue;
            }

            int capturedTeam = team;
            int representativeId = getRepresentativePieceId(teamPieceIds);
            PieceStackView capturedView = pieceViews[capturedTeam][representativeId];
            Runnable finishTeam = () -> {
                for (int id : teamPieceIds) {
                    moveToWaitSpot(capturedTeam, id);
                }
                remainingTeams[0]--;
                if (remainingTeams[0] == 0 && animationGeneration == generation) {
                    onComplete.run();
                }
            };
            if (capturedView.getParent() != boardOverlay) {
                finishTeam.run();
                continue;
            }

            configureBoardGroupAppearance(
                    capturedView,
                    capturedTeam,
                    representativeId,
                    teamPieceIds.size());
            capturedView.bringToFront();
            boardOverlay.releaseAnchor(capturedView);
            float startX = capturedView.getX(), startY = capturedView.getY();
            float[] target = getBoardNodePosition(BoardPath.START_NODE, getBoardPieceViewSize());
            ValueAnimator recoil = ValueAnimator.ofFloat(0f, 1f);
            moveAnimators.add(recoil);
            recoil.setDuration(600L);
            recoil.setInterpolator(new android.view.animation.LinearInterpolator());
            recoil.addUpdateListener(a -> {
                if (disposed || animationGeneration != generation) return;
                float progress = (float) a.getAnimatedValue();
                float squash = (float) Math.sin(Math.PI * Math.min(1f, progress / 0.28f));
                float flight = Math.max(0f, (progress - 0.28f) / 0.72f);
                float hop = (float) Math.sin(Math.PI * flight);
                capturedView.setCollapseProgress(Math.min(1f, progress / 0.28f));
                capturedView.setScaleX(1f + 0.3f * squash - 0.45f * flight);
                capturedView.setScaleY(1f - 0.3f * squash - 0.45f * flight);
                capturedView.setRotation((capturedTeam % 2 == 0 ? 1 : -1) *
                        (12f * squash + 24f * (float) Math.sin(Math.PI * 2 * flight)));
                capturedView.setX(startX + (target[0] - startX) * flight);
                capturedView.setY(Math.max(0f, startY + (target[1] - startY) * flight - dp(28) * hop));
                capturedView.setAlpha(1f - Math.max(0f, (flight - 0.55f) / 0.45f));
            });
            recoil.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
                @Override public void onAnimationEnd(Animator animation) {
                    moveAnimators.remove(recoil);
                    if (!cancelled && !disposed && animationGeneration == generation) finishTeam.run();
                }
            });
            recoil.start();
        }
    }

    void animateStackLayout(
            PieceStackView view,
            float targetProgress,
            long duration,
            Runnable onComplete) {
        float startProgress = view.getCollapseProgress();
        if (Math.abs(startProgress - targetProgress) < 0.001f) {
            onComplete.run();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(startProgress, targetProgress);
        moveAnimators.add(animator);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(valueAnimator ->
                view.setCollapseProgress((float) valueAnimator.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                moveAnimators.remove(animator);
                if (!cancelled) {
                    view.setCollapseProgress(targetProgress);
                    onComplete.run();
                }
            }
        });
        animator.start();
    }

    void renderBoardPiecesNow() {
        for (int team = 0; team < game.getTeamCount(); team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                Piece piece = game.getPiece(team, id);
                if (piece.isFinished
                        || piece.position == BoardPath.START_NODE
                        || piece.position == BoardPath.END_NODE) {
                    continue;
                }

                ArrayList<Integer> groupIds = getBoardGroupIds(team, piece.position);
                int representativeId = getRepresentativePieceId(groupIds);
                if (id != representativeId) {
                    removeFromParent(pieceViews[team][id]);
                    continue;
                }

                PieceStackView representative = pieceViews[team][representativeId];
                resetPieceViewTransform(representative);
                moveViewToParent(representative, boardOverlay);
                configureBoardGroupAtNode(representative, team, representativeId, groupIds.size(), piece.position);
                int size = getBoardPieceViewSize();
                representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
                placePieceOnBoardNode(representative, piece.position, size);
            }
        }
    }

    ArrayList<Integer> getBoardGroupIds(int teamId, int logicalNode) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            Piece piece = game.getPiece(teamId, id);
            if (!piece.isFinished && game.isSameBoardSpot(piece.position, logicalNode)) {
                ids.add(id);
            }
        }
        return ids;
    }

    int getRepresentativePieceId(List<Integer> pieceIds) {
        int representativeId = pieceIds.get(0);
        for (int id : pieceIds) {
            representativeId = Math.min(representativeId, id);
        }
        return representativeId;
    }

    void configureBoardGroupAtNode(
            PieceStackView view,
            int teamId,
            int pieceId,
            int groupCount,
            int logicalNode) {
        configureBoardGroupAppearance(view, teamId, pieceId, groupCount);
        int spotIndex = logicalNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT
                : game.visualSpotFor(logicalNode);
        view.setArrangement(BoardGeometry.stackArrangementForSpot(spotIndex));
    }

    void configureBoardGroupAppearance(PieceStackView view, int teamId, int pieceId, int groupCount) {
        view.setVisibility(View.VISIBLE);
        view.setGroupCount(groupCount);
        view.setVisualDiameterPx(getBoardPieceSize());
        view.setCollapseProgress(0f);
        view.setElevation(dp(groupCount > 1 ? 9 : 7));
        view.setContentDescription(groupCount > 1
                ? context.getString(R.string.grouped_piece_description, game.getLocalizedTeamName(teamId), groupCount)
                : context.getString(R.string.piece_description, game.getLocalizedTeamName(teamId), pieceId + 1));
    }

    void restorePieceIdentity(PieceStackView view, int teamId, int pieceId) {
        view.setVisibility(View.VISIBLE);
        view.setGroupCount(1);
        view.setArrangement(PieceStackLayout.SPACIOUS);
        view.setVisualDiameterPx(dp(36));
        view.setCollapseProgress(0f);
        view.setElevation(dp(7));
        view.setContentDescription(context.getString(R.string.piece_description, game.getLocalizedTeamName(teamId), pieceId + 1));
    }

    void placePieceOnBoardNode(View pieceView, int logicalNode, int size) {
        boardOverlay.anchor(pieceView, logicalNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT : game.visualSpotFor(logicalNode));
    }

    float[] getBoardNodePosition(int logicalNode, int size) {
        int spotIndex = logicalNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT
                : game.visualSpotFor(logicalNode);
        if (spotIndex < 0 || spotIndex >= BoardGeometry.POINTS.length) {
            spotIndex = BoardGeometry.START_SPOT;
        }
        float[] center = getBoardSpotCenter(spotIndex);
        return new float[]{center[0] - (size / 2f), center[1] - (size / 2f)};
    }

    float[] getBoardSpotCenter(int spotIndex) {
        return boardOverlay.centerForSpot(spotIndex);
    }

    void createFinishDestination() {
        finishDestination = new TextView(context);
        finishDestination.setId(R.id.finish_destination);
        finishDestination.setText(R.string.finish_destination);
        finishDestination.setContentDescription(context.getString(R.string.finish_destination_description));
        finishDestination.setGravity(Gravity.CENTER);
        finishDestination.setTextColor(context.getResources().getColor(R.color.text_primary));
        finishDestination.setTextSize(14);
        finishDestination.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable tile = new GradientDrawable();
        tile.setColor(context.getResources().getColor(R.color.accent_gold_soft));
        tile.setCornerRadius(dp(12));
        tile.setStroke(dp(2), context.getResources().getColor(R.color.board_direction));
        finishDestination.setBackground(tile);
        finishDestination.setElevation(dp(14));
        finishDestination.setVisibility(View.GONE);
        finishDestination.setClickable(true);
        finishDestination.setFocusable(true);
        finishDestination.setOnClickListener(v -> {
            if (!disposed && events.selectedTeam() == game.getCurrentTeam() && events.selectedPiece() >= 0) {
                YutGameEngine.MovePreview preview = game.previewMove(events.selectedTeam(), events.selectedPiece());
                if (preview.available && preview.finishes) events.commitMove(v);
            }
        });
        ConstraintLayout root = findViewById(R.id.root_layout);
        root.addView(finishDestination, new ConstraintLayout.LayoutParams(dp(64), dp(48)));
    }

    void setFinishDestinationVisible(boolean visible) {
        if (finishDestination == null) return;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (finishDestination.getVisibility() == visibility) return;
        finishDestination.setVisibility(visibility);
        if (!disposed) events.onFinishVisibilityChanged();
    }

    void updateMovePreviews() {
        // Keep the target and board geometry stable until the finish animation ends.
        if (disposed || events.isAnimating()) return;
        clearMovePreviews(false);
        if (events.isAnimating() || game.getSelectedSteps().isEmpty() || game.isGameOver()
                || events.selectedTeam() != game.getCurrentTeam() || events.selectedPiece() == -1) {
            setFinishDestinationVisible(false);
            return;
        }
        YutGameEngine.MovePreview preview = game.previewMove(events.selectedTeam(), events.selectedPiece());
        setFinishDestinationVisible(preview.available && preview.finishes);
        if (!preview.available || preview.finishes) return;
        int previewGeneration = movePreviewGeneration;
        int activeLayoutGeneration = generation;
        int spotIndex = preview.targetNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT : game.visualSpotFor(preview.targetNode);
        boardOverlay.post(() -> {
            if (previewGeneration != movePreviewGeneration || activeLayoutGeneration != generation
                    || events.isAnimating() || game.isGameOver()) return;
            if (spotIndex >= 0 && spotIndex < BoardGeometry.POINTS.length) addPreviewGlow(spotIndex);
        });
    }

    void addPreviewGlow(int spotIndex) {
        int size = Math.max(dp(48), getBoardPieceSize() + dp(10));
        int ringSize = size + dp(12);

        View ring = new View(context);
        ring.setBackground(createPreviewRingDrawable());
        ring.setElevation(dp(6));
        ring.setClickable(false);
        ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        boardOverlay.addView(ring, new FrameLayout.LayoutParams(ringSize, ringSize));
        placeOverlayAtSpot(ring, spotIndex, ringSize);
        previewViews.add(ring);

        ImageView glow = new ImageView(context);
        glow.setImageResource(R.drawable.ic_destination_arrow);
        glow.setColorFilter(context.getResources().getColor(R.color.text_primary));
        int arrowPadding = Math.max(dp(10), size / 4);
        glow.setPadding(arrowPadding, arrowPadding, arrowPadding, arrowPadding);
        glow.setBackground(createPreviewDrawable());
        glow.setElevation(dp(14));
        glow.setClickable(true);
        glow.setFocusable(true);
        glow.setContentDescription(context.getString(R.string.destination_description));
        glow.setOnClickListener(v -> { if (!disposed) events.commitMove(v); });
        boardOverlay.addView(glow, new FrameLayout.LayoutParams(size, size));
        placeOverlayAtSpot(glow, spotIndex, size);
        previewViews.add(glow);

        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        ring.setAlpha(0f);
        ring.setScaleX(0.62f);
        ring.setScaleY(0.62f);
        ring.animate()
                .alpha(0.72f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setInterpolator(interpolator)
                .setDuration(260)
                .withEndAction(() -> ring.animate()
                        .alpha(0.46f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .start())
                .start();

        glow.setAlpha(0.35f);
        glow.setScaleX(0.78f);
        glow.setScaleY(0.78f);
        glow.animate()
                .alpha(1f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setInterpolator(interpolator)
                .setDuration(190)
                .withEndAction(() -> glow.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();
    }

    void placeOverlayAtSpot(View view, int spotIndex, int size) {
        boardOverlay.anchor(view, spotIndex);
    }

    void positionBoardChild(View view, float left, float top) {
        ViewGroup.LayoutParams currentParams = view.getLayoutParams();
        if (!(currentParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) currentParams;
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.round(left);
        params.topMargin = Math.round(top);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setLayoutParams(params);
    }

    void clearMovePreviews() {
        clearMovePreviews(true);
    }

    void clearMovePreviews(boolean hideFinish) {
        if (hideFinish) setFinishDestinationVisible(false);
        movePreviewGeneration++;
        for (View previewView : previewViews) {
            ViewGroup parent = (ViewGroup) previewView.getParent();
            if (parent != null) {
                parent.removeView(previewView);
            }
        }
        previewViews.clear();
    }

    void updatePieceSelectionStyles() {
        updatePieceSelectionStyles(false);
    }

    void updatePieceSelectionStyles(boolean animateGuidance) {
        if (pieceViews == null) {
            return;
        }

        clearPieceGuides();
        boolean showGuidance = shouldShowPieceGuidance();
        int currentTeam = game.getCurrentTeam();
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                PieceStackView pieceView = pieceViews[team][id];
                pieceView.animate().cancel();
                boolean selected = team == events.selectedTeam() && id == events.selectedPiece();
                boolean legal = showGuidance
                        && team == currentTeam
                        && isPieceEligibleForGuidance(team, id);
                float scale = selected ? 1.12f : legal && !animateGuidance ? 1.03f : 1f;
                pieceView.setScaleX(scale);
                pieceView.setScaleY(scale);
                pieceView.setAlpha(showGuidance
                        && team == currentTeam
                        && pieceView.getParent() != null
                        && !legal ? 0.5f : 1f);
                pieceView.setElevation(dp(selected ? 10 : 7));
            }
        }

        if (!showGuidance) {
            return;
        }

        int guidanceGeneration = pieceGuideGeneration;
        boardOverlay.post(() -> {
            if (guidanceGeneration != pieceGuideGeneration || !shouldShowPieceGuidance()) {
                return;
            }
            addPieceGuides(animateGuidance && !events.reducedMotion());
        });
    }

    boolean shouldShowPieceGuidance() {
        return !disposed && events.isGameStarted()
                && !events.isAnimating()
                && !game.isGameOver()
                && !game.getSelectedSteps().isEmpty()
                && events.selectedTeam() == -1
                && events.selectedPiece() == -1;
    }

    boolean isPieceEligibleForGuidance(int teamId, int pieceId) {
        if (teamId != game.getCurrentTeam()) {
            return false;
        }
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        if (pieceView.getParent() == null || game.getPiece(teamId, pieceId).isFinished) {
            return false;
        }
        return game.previewMove(teamId, pieceId).available;
    }

    void addPieceGuides(boolean animateGuidance) {
        int teamId = game.getCurrentTeam();
        for (int pieceId = 0; pieceId < YutGameEngine.PIECE_COUNT; pieceId++) {
            if (!isPieceEligibleForGuidance(teamId, pieceId)) {
                continue;
            }

            PieceStackView pieceView = pieceViews[teamId][pieceId];
            ViewGroup parent = (ViewGroup) pieceView.getParent();
            View guide = new View(context);
            guide.setBackground(createPieceGuideDrawable(teamId));
            guide.setTag("piece_guide_" + teamId + "_" + pieceId);

            if (parent == boardOverlay) {
                int guideSize = Math.max(dp(52), getBoardPieceViewSize() + dp(4));
                guide.setElevation(dp(6));
                guide.setClickable(true);
                guide.setFocusable(true);
                guide.setContentDescription(context.getString(
                        R.string.selectable_piece_description,
                        game.getLocalizedTeamName(teamId),
                        pieceId + 1));
                final int selectedPieceId = pieceId;
                guide.setOnClickListener(v -> { if (!disposed) events.selectPiece(teamId, selectedPieceId); });
                boardOverlay.addView(guide, new FrameLayout.LayoutParams(guideSize, guideSize));
                placePieceGuideOnBoard(guide, game.getPiece(teamId, pieceId).position, guideSize);
            } else if (parent instanceof FrameLayout) {
                guide.setClickable(false);
                guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                ((FrameLayout) parent).addView(
                        guide,
                        0,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER));
                pieceView.bringToFront();
            } else {
                continue;
            }

            pieceGuideViews.add(guide);
            animatePieceGuide(guide, pieceView, animateGuidance);
        }
    }

    void placePieceGuideOnBoard(View guide, int logicalNode, int size) {
        placePieceOnBoardNode(guide, logicalNode, size);
    }

    void animatePieceGuide(View guide, View pieceView, boolean animateGuidance) {
        if (!animateGuidance) {
            guide.setAlpha(0.58f);
            guide.setScaleX(1f);
            guide.setScaleY(1f);
            pieceView.setScaleX(1.03f);
            pieceView.setScaleY(1.03f);
            return;
        }

        guide.setAlpha(0f);
        guide.setScaleX(0.72f);
        guide.setScaleY(0.72f);
        guide.animate()
                .alpha(0.66f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(230)
                .withEndAction(() -> guide.animate()
                        .alpha(0.58f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160)
                        .start())
                .start();
        pieceView.setScaleX(0.96f);
        pieceView.setScaleY(0.96f);
        pieceView.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(190)
                .withEndAction(() -> pieceView.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(140)
                        .start())
                .start();
    }

    void clearPieceGuides() {
        pieceGuideGeneration++;
        for (View guide : pieceGuideViews) {
            guide.animate().cancel();
            ViewGroup parent = (ViewGroup) guide.getParent();
            if (parent != null) {
                parent.removeView(guide);
            }
        }
        pieceGuideViews.clear();
    }

    void moveToWaitSpot(int teamId, int pieceId) {
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        resetPieceViewTransform(pieceView);
        FrameLayout waitSpot = waitSpots[teamId][pieceId];
        if (waitSpot == null) {
            removeFromParent(pieceView);
            return;
        }
        resetPieceViewTransform(pieceView);
        restorePieceIdentity(pieceView, teamId, pieceId);
        moveViewToParent(pieceView, waitSpot);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER);
        pieceView.setLayoutParams(params);
        resetPieceViewTransform(pieceView);
    }

    void moveToFinishedArea(int teamId, int pieceId) {
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        resetPieceViewTransform(pieceView);
        removeFromParent(pieceView);
        events.updateFinishedSummary();
    }

    void moveViewToParent(View view, ViewGroup newParent) {
        ViewGroup oldParent = (ViewGroup) view.getParent();
        if (oldParent == newParent) {
            return;
        }
        if (oldParent != null) {
            oldParent.removeView(view);
        }
        newParent.addView(view);
    }

    GradientDrawable createPreviewDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xEAF8C74E);
        drawable.setStroke(dp(2), 0xFFFFFFFF);
        return drawable;
    }

    GradientDrawable createPreviewRingDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x18F8C74E);
        drawable.setStroke(dp(3), 0xDDF8C74E);
        return drawable;
    }

    GradientDrawable createPieceGuideDrawable(int teamId) {
        int teamColor = getTeamColor(teamId);
        int guideColor = Color.argb(
                220,
                Color.red(teamColor),
                Color.green(teamColor),
                Color.blue(teamColor));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x12FFFFFF);
        drawable.setStroke(dp(2), guideColor);
        return drawable;

    }

    int getBoardPieceSize() {
        // Use the device's stable size class, not a transient board measurement during rotation.
        return dp(layout.usesCompactWaitingTray() ? 36 : 44);
    }

    int getBoardPieceViewSize() {
        return Math.max(dp(48), getBoardPieceSize() + dp(10));
    }

    void bounceArrivedPieces(int teamId, List<Integer> pieceIds, Runnable onComplete) {
        if (pieceIds.isEmpty()) {
            onComplete.run();
            return;
        }

        for (int i = 0; i < pieceIds.size(); i++) {
            PieceStackView pieceView = pieceViews[teamId][pieceIds.get(i)];
            boolean last = i == pieceIds.size() - 1;
            pieceView.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(75)
                    .withEndAction(() -> pieceView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction(last ? onComplete : null)
                            .start())
                    .start();
        }
    }

    void removeAllPiecesFromScreen() {
        if (pieceViews == null) {
            return;
        }

        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                removeFromParent(pieceViews[team][id]);
            }
        }
    }

    void resetPieceViewTransform(View view) {
        view.animate().cancel();
        view.setX(0f);
        view.setY(0f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setRotation(0f);
        view.setAlpha(1f);
        if (view instanceof PieceStackView) {
            ((PieceStackView) view).setCollapseProgress(0f);
        }
    }

    float[] undoPoint(int node, int pieceId, ViewGroup root) {
        int[] base = new int[2]; int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        if (node == BoardPath.START_NODE) {
            View spot = waitSpots[game.getCurrentTeam()][pieceId];
            if (spot == null) spot = waitingArea;
            spot.getLocationOnScreen(base);
            return new float[]{base[0] - origin[0] + spot.getWidth() / 2f,
                    base[1] - origin[1] + spot.getHeight() / 2f};
        }
        boardOverlay.getLocationOnScreen(base);
        float[] position = getBoardNodePosition(node == BoardPath.END_NODE ? 15 : node, 0);
        return new float[]{base[0] - origin[0] + position[0],
                base[1] - origin[1] + position[1] + (node == BoardPath.END_NODE ? dp(32) : 0)};
    }

    void animateUndo(YutGameEngine.SavedState before, GameStateStore.MoveUndoState state) {
        clearMovePreviews();
        int animationGeneration = generation;
        ViewGroup root = findViewById(R.id.root_layout);
        root.post(() -> {
            if (animationGeneration != generation || disposed) return;
            ArrayList<ArrayList<float[]>> paths = new ArrayList<>();
            ArrayList<PieceStackView> ghosts = new ArrayList<>();
            ArrayList<PieceStackView> hidden = new ArrayList<>();
            int longest = 1;
            int size = getBoardPieceViewSize();
            for (int p = 0; p < 16; p++) {
                if (before.piecePositions[p] == state.engineState.piecePositions[p]
                        && before.pieceFinished[p] == state.engineState.pieceFinished[p]) continue;
                int team = p / 4, id = p % 4;
                ArrayList<float[]> points = new ArrayList<>();
                points.add(undoPoint(before.piecePositions[p], id, root));
                int[] recorded = state.reversePaths == null ? null : state.reversePaths[p];
                if (recorded != null) for (int node : recorded) {
                    float[] point = undoPoint(node, id, root);
                    float[] last = points.get(points.size() - 1);
                    if (Math.hypot(last[0] - point[0], last[1] - point[1]) > 1f) points.add(point);
                }
                float[] destination = undoPoint(state.engineState.piecePositions[p], id, root);
                float[] last = points.get(points.size() - 1);
                if (Math.hypot(last[0] - destination[0], last[1] - destination[1]) > 1f) points.add(destination);
                if (points.size() < 2) continue;
                PieceStackView ghost = new PieceStackView(context);
                ghost.configureAppearance(events.color(team), events.shape(team), id + 1);
                ghost.setVisualDiameterPx(getBoardPieceSize());
                ghost.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                ghost.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
                ghost.layout(0, 0, size, size);
                ghost.setX(points.get(0)[0] - size / 2f); ghost.setY(points.get(0)[1] - size / 2f);
                root.getOverlay().add(ghost);
                undoGhosts.add(ghost); ghosts.add(ghost); paths.add(points);
                PieceStackView actual = pieceViews[team][id];
                actual.animate().cancel(); actual.setAlpha(0f); hidden.add(actual);
                longest = Math.max(longest, points.size() - 1);
            }
            if (ghosts.isEmpty()) { events.finishUndoAnimation(); return; }
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            moveAnimators.add(animator);
            // Apply the same per-node timing as forward travel, divided by 1.75.
            long forwardDuration = 0L;
            for (int i = 0; i < longest; i++) forwardDuration += Math.max(110, 190 - Math.min(i, 4) * 12);
            animator.setDuration(Math.max(90L, Math.round(forwardDuration / 1.75f)));
            animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.addUpdateListener(value -> {
                float fraction = (float) value.getAnimatedValue();
                for (int i = 0; i < ghosts.size(); i++) {
                    ArrayList<float[]> points = paths.get(i);
                    float step = fraction * (points.size() - 1);
                    int index = Math.min(points.size() - 2, (int) step);
                    float progress = step - index;
                    float[] a = points.get(index), b = points.get(index + 1);
                    ghosts.get(i).setX(a[0] + (b[0] - a[0]) * progress - size / 2f);
                    ghosts.get(i).setY(a[1] + (b[1] - a[1]) * progress - size / 2f);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
                @Override public void onAnimationEnd(Animator animation) {
                    moveAnimators.remove(animator);
                    for (View ghost : ghosts) { root.getOverlay().remove(ghost); undoGhosts.remove(ghost); }
                    for (View actual : hidden) actual.setAlpha(1f);
                    if (!cancelled && animationGeneration == generation) events.finishUndoAnimation();
                }
            });
            animator.start();
        });
    }
}
