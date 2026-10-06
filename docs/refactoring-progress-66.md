# 빌드 66 리팩토링 진행과 검증

기록일: 2026-10-06 / Codex / C:\yutnori
작업 브랜치: `refactor/history-status-build66`
기준 태그: `baseline-build66`. 내부 버전: `1.5.12 / 66`.

## 완료 범위

[설계](refactoring-design-66.md)의 A·B를 구현했다. 다음 구현은 C(화면 배치 분리)다.

- `HistoryPresenter`: 기록 포맷·아이콘·색·접근성 설명·최근 기록 표시와 클릭 연결을 소유한다. 기록 원본·순서·추가/삭제·저장·다이얼로그 시간 보류는 Activity가 계속 관리한다.
- `TeamStatusPanel`: 현황판의 팀 행·표시용 말·현재 차례 아이콘·골인 표시·행 높이와 실측 리스너를 소유한다. 실제 움직이는 말과 대기 칸은 기존 Activity에 있다.
- 회전으로 XML을 새로 연결하거나 Activity가 종료될 때 두 표시 객체를 폐기한다. 이전 현황판의 예약 작업과 기록 클릭이 새 화면에 영향을 주지 않도록 정리한다.
- 엔진·경로·저장 형식·리소스·버전·이동/취소 애니메이션 로직은 기준 소스와 동일하다. 이번 커밋은 A·B까지만 포함한다.

## 구조 지표

행 수는 빈 줄을 포함한 물리 행 수다.

| 지표 | 기준 | A·B 후 |
| --- | ---: | ---: |
| MainActivity | 4094 | 3818 |
| HistoryPresenter | 없음 | 180 |
| TeamStatusPanel | 없음 | 165 |
| 위 세 파일 합계 | 4094 | 4163 |

MainActivity의 책임을 276줄 분리했지만 전체 소스는 생명주기 처리와 경계를 명시하면서 69줄 늘었다. 이를 용량·메모리·실행 속도 개선으로 해석하지 않는다. 성능 최적화는 F 단계에서 측정한다.

## 확인 결과

- 추출 전에 신규 `HistoryPresentationTest` 7개를 기존 앱 코드에서 통과시켜 실제 문자열·아이콘·클릭 동작 기준을 고정했다.
- 추출 후 `testDebugUnitTest lintDebug assembleDebug`: 성공. 테스트 138개, 실패 0개, 오류 0개. 기준 130개를 유지하고 기록 테스트 7개·오래된 화면 콜백 테스트 1개를 추가했다.
- 기존 현황판 테스트 6곳의 private 필드 접근 위치만 새 소유자에 맞춰 바꿨다. 기존 기대값·시나리오는 유지했다.
- lint 오류 0개, 경고 44개로 기준과 같다.
- Robolectric native graphics 테스트에서 이번 실행으로 다시 생성한 PNG 25장 모두 기준 PNG와 SHA-256이 같다. 세로·가로·태블릿·2/3/4팀·큰 글씨·골인·설정 화면을 포함한다.
- 기준 이미지: `C:\yutnori\builds\baseline-66-ux`. 새 이미지: `app/build/reports/ux`. 사용자 배포용 빌드 66 APK/AAB를 덮어쓰지 않았다.
- 컴파일 검증 APK: `app/build/outputs/apk/debug/app-debug.apk`. 기존 버전 번호와 debug 서명이며 스토어 업로드용 산출물이 아니다. 새 release APK/AAB는 생성하지 않았다.
- 연결된 Android 기기가 없어 instrumented/실기기 실행과 성능 측정은 수행하지 않았다. 자동 검증 범위 밖의 기기 결과를 완료로 간주하지 않는다.

## 남은 단계

C 화면 배치 → D 말·화살표·애니메이션 → E 시간 계산 → F 측정 후 최적화. 기존 [기능 유지 계약](refactoring-baseline-66.md)과 설계의 단계별 검증 조건을 계속 적용한다. 각 단계는 별도 변경 묶음과 커밋으로 검토한다.
