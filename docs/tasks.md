# Tasks

## 추천 작업 순서

#1

## 작업 목록

| Index | Tag | Updated | Status | Completed | Deps | 항목 | 완료 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | watcher | 2026-11-18 10:14 | 🟡 WIP |  |  | watcher 대시보드를 Vite + React CSR로 다시 만들고(UI/UX 개편 포함) 봇 HTTP 서버(7918)가 직접 내려주게 통합한다. 처음 로딩 속도를 개선한다. Node 런타임 서버(`watcher/main.js`)와 봇의 watcher 하위 프로세스 실행을 없앤다. 방향은 [plans.md](plans.md) 참고. | 확정된 시안대로 화면이 나오고 합성 데이터 캡처로 시안과 비교했다. 봇만 실행해도 `:7918`에서 대시보드가 열리고 REST·SSE 데이터가 나온다. `/channel/:id` 경로가 동작한다. 처음 로딩이 서버·사람 수만큼의 개별 요청 없이 끝난다. `fastbuild.sh`가 watcher 빌드까지 수행한다. 런타임에 node 프로세스가 없다. |
