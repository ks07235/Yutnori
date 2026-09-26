package com.example.yutnoriapp;

final class GameText {
    private final boolean korean;

    private GameText(boolean korean) {
        this.korean = korean;
    }

    static GameText korean() {
        return new GameText(true);
    }

    static GameText forLanguage(boolean korean) {
        return new GameText(korean);
    }

    String teamCountRange() {
        return korean
                ? "팀은 2팀부터 4팀까지 설정할 수 있습니다."
                : "Choose between 2 and 4 teams.";
    }

    String gameStarted(int teamCount) {
        return korean
                ? teamCount + "팀 게임을 시작합니다."
                : "Starting a " + teamCount + "-team game.";
    }

    String gameEnded() {
        return korean
                ? "게임이 끝났습니다. 새 게임을 눌러주세요."
                : "The game is over. Start a new game to play again.";
    }

    String unknownResult() {
        return korean
                ? "알 수 없는 윷 결과입니다."
                : "That Yut result is not available.";
    }

    String normalRollLimit() {
        return korean
                ? "\uc774\ubbf8 \uc77c\ubc18 \uc737 \uacb0\uacfc\ub97c \uc785\ub825\ud588\uc2b5\ub2c8\ub2e4. \uc737\u00b7\ubaa8\ub97c \uc785\ub825\ud558\uac70\ub098 \ub9d0\uc744 \uc6c0\uc9c1\uc774\uc138\uc694."
                : "A normal result is already recorded. Enter Yut or Mo, or move a piece.";
    }

    String rollWithBonus(int steps) {
        String result = resultName(steps);
        return korean
                ? result + subjectParticle(result) + " 나왔습니다. 한 번 더 던질 수 있습니다."
                : result + " was entered. You may throw again.";
    }

    String rollAndSelect(int steps) {
        String result = resultName(steps);
        return korean
                ? result + subjectParticle(result) + " 나왔습니다. 사용할 결과를 선택하세요."
                : result + " was entered. Select the result to use.";
    }

    String noRollToUndo() {
        return korean
                ? "취소할 윷 결과가 없습니다."
                : "There is no Yut result to undo.";
    }

    String lastRollUndone() {
        return korean
                ? "마지막 윷 결과를 취소했습니다."
                : "The last Yut result was undone.";
    }

    String teamTurn(int teamId) {
        return korean
                ? teamName(teamId) + " 차례입니다."
                : "Turn: " + teamName(teamId) + ".";
    }

    String captureBonusRollFirst() {
        return korean
                ? "상대 말을 잡았습니다. 윷을 먼저 한 번 더 굴려야 합니다."
                : "You captured an opponent piece. Throw once more before moving.";
    }

    String unavailableResult() {
        return korean
                ? "선택할 수 없는 윷 결과입니다."
                : "That Yut result cannot be selected.";
    }

    String selectResultsInOrder() {
        return korean
                ? "사용할 윷 결과를 순서대로 선택하세요."
                : "Select the Yut results in the order you want to use them.";
    }

    String choosePieceForPlan(String plan) {
        return korean
                ? plan + " 순서로 움직일 말을 고르세요."
                : "Choose a piece to move with " + plan + ".";
    }

    String nonParticipatingTeam() {
        return korean
                ? "참가하지 않는 팀입니다."
                : "That team is not in this game.";
    }

    String wrongTeam(int teamId) {
        return korean
                ? "지금은 " + teamName(teamId) + " 차례입니다."
                : "It is " + teamName(teamId) + "'s turn.";
    }

    String invalidPiece() {
        return korean
                ? "선택할 수 없는 말입니다."
                : "That piece cannot be selected.";
    }

    String alreadyFinished() {
        return korean
                ? "이미 완주한 말입니다."
                : "That piece has already finished.";
    }

    String rollFirst() {
        return korean
                ? "윷을 먼저 한 번 더 굴려야 합니다."
                : "Throw once more before moving.";
    }

    String selectResultFirst() {
        return korean
                ? "사용할 윷 결과를 먼저 선택하세요."
                : "Select the Yut result to use first.";
    }

    String selectedResultsMissing() {
        return korean
                ? "선택한 윷 결과를 찾을 수 없습니다. 다시 선택하세요."
                : "The selected Yut results are no longer available. Select them again.";
    }

    String waitingPieceBackDo() {
        return korean
                ? "대기 중인 말은 빽도를 쓸 수 없습니다. 판 위의 말을 고르거나 턴을 종료하세요."
                : "A waiting piece cannot use Back Do. Choose a piece on the board or end the turn.";
    }

    String victory(int teamId) {
        return korean
                ? teamName(teamId) + " 승리!"
                : teamName(teamId) + " wins!";
    }

    String capturedBonus() {
        return korean
                ? "상대 말을 잡았습니다. 윷을 한 번 더 던지세요."
                : "You captured an opponent piece. Throw once more.";
    }

    String canRollMore() {
        return korean
                ? "윷을 더 던질 수 있습니다."
                : "You may throw again.";
    }

    String rollOrSelectRemaining() {
        return korean
                ? "윷을 더 던지거나 남은 결과를 선택하세요."
                : "Throw again or select a remaining result.";
    }

    String selectRemaining() {
        return korean
                ? "남은 결과를 선택하세요."
                : "Select a remaining result.";
    }

    String resultName(int steps) {
        switch (steps) {
            case -1:
                return korean ? "빽도" : "Back Do";
            case 1:
                return korean ? "도" : "Do";
            case 2:
                return korean ? "개" : "Gae";
            case 3:
                return korean ? "걸" : "Geol";
            case 4:
                return korean ? "윷" : "Yut";
            case 5:
                return korean ? "모" : "Mo";
            default:
                return "";
        }
    }

    String teamName(int teamId) {
        return korean ? (teamId + 1) + "팀" : "Team " + (teamId + 1);
    }

    private String subjectParticle(String word) {
        if (word.isEmpty()) {
            return "가";
        }
        char last = word.charAt(word.length() - 1);
        boolean hasBatchim = last >= 0xAC00 && last <= 0xD7A3 && ((last - 0xAC00) % 28 != 0);
        return hasBatchim ? "이" : "가";
    }
}