# Yutnori App Architecture

## 핵심 규칙

- `YutGameEngine.java`: 팀, 윷 결과, 선택 순서, 말 이동, 업기, 잡기, 완주, 턴 전환을 관리한다.
- `BoardPath.java`: 기본 경로, 지름길, 빽도와 이동 경로 추적을 담당한다.
- `Piece.java`: 말 하나의 위치, 경로와 완주 상태를 보관한다.
- 핵심 규칙은 Android UI에 의존하지 않으며 `YutGameEngineTest.java`로 검증한다.

## 화면 계층

- `MainActivity.java`: 화면 생명주기, 입력 연결, 타이머, 말 배치와 애니메이션을 조정한다.
- `YutDialogs.java`: 설정, 전체 기록, 게임 종료 다이얼로그를 만든다.
- `YutBoardView.java`: 윷판을 그린다.
- `SquareFrameLayout.java`: 화면 비율과 무관하게 윷판을 정사각형으로 유지한다.
- `BoardGeometry.java`: 노드 좌표를 한 곳에서 관리한다.

## 저장과 기기 기능

- `GameStateStore.java`: 설정, 게임 상태, 타이머 기준 시각과 최근 기록을 SharedPreferences에 저장한다.
- `GameFeedback.java`: 효과음과 진동을 담당한다.
- 저장 데이터는 복원 시 팀 수, 말 위치, 경로와 결과 ID를 검증한다.

## 반응형 레이아웃

- `res/layout`: 휴대폰 세로
- `res/layout-land`: 휴대폰 가로와 접이식 좁은 가로
- `res/layout-sw600dp`: 태블릿 세로
- `res/layout-sw600dp-land`: 태블릿 가로

## 배포 규칙

- 애플리케이션 ID는 `com.das312.yutnori`다.
- 작은 수정은 patch, 기능 추가는 minor, 공개 버전은 `1.0.0`으로 올린다.
- 출시 전 단위 테스트, Android lint, APK 또는 AAB 빌드를 모두 통과시킨다.
