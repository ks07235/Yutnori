package com.example.yutnoriapp;

/* JADX INFO: loaded from: classes3.dex */
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
        if (this.korean) {
            return "팀은 2팀부터 4팀까지 설정할 수 있습니다.";
        }
        return "Choose between 2 and 4 teams.";
    }

    String gameStarted(int teamCount) {
        if (this.korean) {
            return teamCount + "팀 게임을 시작합니다.";
        }
        return "Starting a " + teamCount + "-team game.";
    }

    String gameEnded() {
        if (this.korean) {
            return "게임이 끝났습니다. 새 게임을 눌러주세요.";
        }
        return "The game is over. Start a new game to play again.";
    }

    String unknownResult() {
        if (this.korean) {
            return "알 수 없는 윷 결과입니다.";
        }
        return "That Yut result is not available.";
    }

    String rollWithBonus(int steps) {
        String result = resultName(steps);
        if (this.korean) {
            return result + subjectParticle(result) + " 나왔습니다. 한 번 더 던질 수 있습니다.";
        }
        return result + " was entered. You may throw again.";
    }

    String rollAndSelect(int steps) {
        String result = resultName(steps);
        if (this.korean) {
            return result + subjectParticle(result) + " 나왔습니다. 사용할 결과를 선택하세요.";
        }
        return result + " was entered. Select the result to use.";
    }

    String noRollToUndo() {
        if (this.korean) {
            return "취소할 윷 결과가 없습니다.";
        }
        return "There is no Yut result to undo.";
    }

    String lastRollUndone() {
        if (this.korean) {
            return "마지막 윷 결과를 취소했습니다.";
        }
        return "The last Yut result was undone.";
    }

    String teamTurn(int teamId) {
        if (this.korean) {
            return teamName(teamId) + " 차례입니다.";
        }
        return "Turn: " + teamName(teamId) + ".";
    }

    String captureBonusRollFirst() {
        if (this.korean) {
            return "상대 말을 잡았습니다. 윷을 먼저 한 번 더 굴려야 합니다.";
        }
        return "You captured an opponent piece. Throw once more before moving.";
    }

    String unavailableResult() {
        if (this.korean) {
            return "선택할 수 없는 윷 결과입니다.";
        }
        return "That Yut result cannot be selected.";
    }

    String selectResultsInOrder() {
        if (this.korean) {
            return "사용할 윷 결과를 순서대로 선택하세요.";
        }
        return "Select the Yut results in the order you want to use them.";
    }

    String choosePieceForPlan(String plan) {
        if (this.korean) {
            return plan + " 순서로 움직일 말을 고르세요.";
        }
        return "Choose a piece to move with " + plan + ".";
    }

    String nonParticipatingTeam() {
        if (this.korean) {
            return "참가하지 않는 팀입니다.";
        }
        return "That team is not in this game.";
    }

    String wrongTeam(int teamId) {
        if (this.korean) {
            return "지금은 " + teamName(teamId) + " 차례입니다.";
        }
        return "It is " + teamName(teamId) + "'s turn.";
    }

    String invalidPiece() {
        if (this.korean) {
            return "선택할 수 없는 말입니다.";
        }
        return "That piece cannot be selected.";
    }

    String alreadyFinished() {
        if (this.korean) {
            return "이미 완주한 말입니다.";
        }
        return "That piece has already finished.";
    }

    String rollFirst() {
        if (this.korean) {
            return "윷을 먼저 한 번 더 굴려야 합니다.";
        }
        return "Throw once more before moving.";
    }

    String selectResultFirst() {
        if (this.korean) {
            return "사용할 윷 결과를 먼저 선택하세요.";
        }
        return "Select the Yut result to use first.";
    }

    String selectedResultsMissing() {
        if (this.korean) {
            return "선택한 윷 결과를 찾을 수 없습니다. 다시 선택하세요.";
        }
        return "The selected Yut results are no longer available. Select them again.";
    }

    String waitingPieceBackDo() {
        if (this.korean) {
            return YutGameEngine.WAITING_PIECE_BACK_DO_MESSAGE;
        }
        return "A waiting piece cannot use Back Do. Choose a piece on the board or end the turn.";
    }

    String victory(int teamId) {
        if (this.korean) {
            return teamName(teamId) + " 승리!";
        }
        return teamName(teamId) + " wins!";
    }

    String capturedBonus() {
        if (this.korean) {
            return "상대 말을 잡았습니다. 윷을 한 번 더 던지세요.";
        }
        return "You captured an opponent piece. Throw once more.";
    }

    String canRollMore() {
        if (this.korean) {
            return "윷을 더 던질 수 있습니다.";
        }
        return "You may throw again.";
    }

    String rollOrSelectRemaining() {
        if (this.korean) {
            return "윷을 더 던지거나 남은 결과를 선택하세요.";
        }
        return "Throw again or select a remaining result.";
    }

    String selectRemaining() {
        if (this.korean) {
            return "남은 결과를 선택하세요.";
        }
        return "Select a remaining result.";
    }

    String resultName(int steps) {
        switch (steps) {
            case -1:
                return this.korean ? "빽도" : "Back Do";
            case 0:
            default:
                return "";
            case 1:
                return this.korean ? "도" : "Do";
            case 2:
                return this.korean ? "개" : "Gae";
            case 3:
                return this.korean ? "걸" : "Geol";
            case 4:
                return this.korean ? "윷" : "Yut";
            case 5:
                return this.korean ? "모" : "Mo";
        }
    }

    String teamName(int teamId) {
        return (this.korean ? new StringBuilder().append(teamId + 1).append("팀") : new StringBuilder().append("Team ").append(teamId + 1)).toString();
    }

    private String subjectParticle(String word) {
        if (word.isEmpty()) {
            return "가";
        }
        char last = word.charAt(word.length() - 1);
        boolean hasBatchim = last >= 44032 && last <= 55203 && (last - 44032) % 28 != 0;
        return hasBatchim ? "이" : "가";
    }
}
