# Tasks

## 추천 작업 순서

#1

## 작업 목록

| Index | Tag | Updated | Status | Completed | Deps | 항목 | 완료 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | watcher | 2026-11-18 10:02 | 🔴 TODO |  |  | watcher 대시보드를 Vite CSR 정적 빌드로 바꾸고 봇 HTTP 서버(7918)가 직접 내려주게 통합한다. Node 런타임 서버(`watcher/main.js`)와 봇의 watcher 하위 프로세스 실행을 없앤다. 방식 결정은 [plans.md](plans.md) 참고. | 봇만 실행해도 `:7918`에서 대시보드가 열리고 기존과 같은 화면·데이터(REST·SSE)가 나온다. `/channel/:id` 경로가 동작한다. `fastbuild.sh`가 watcher 빌드까지 수행한다. 런타임에 node 프로세스가 없다. |
