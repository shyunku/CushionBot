# Plans

## watcher 대시보드 봇 통합 (#1)

최초 작성: 2026-11-18 10:02

### 배경

- `watcher/main.js`(Express + EJS)는 정적 파일과 값 3개(`botHost`, `botPort`, `channelId`)만 내려준다. 실제 데이터는 브라우저가 봇 HTTP 서버(7918)에서 직접 받는다.
- 이 때문에 포트가 둘(7918, 7919)이고, CORS 헤더가 필요하며, 페이지 요청마다 공인 IP를 외부 서비스에 조회한다.
- 현재는 봇이 시작할 때 `node main.js`를 하위 프로세스로 실행한다(`service/watcher/WatcherWebServer.java`). 통합이 끝나면 이 실행기는 없앤다.

### 확정

- 대시보드는 CSR로 만들고 Vite로 정적 파일을 빌드한다. 런타임에 Node 서버를 두지 않는다.
- 봇 HTTP 서버(7918)가 빌드 결과(`watcher/dist`)를 디스크에서 읽어 내려준다. 대시보드 주소는 `:7919`에서 `:7918`로 바뀐다.
- 페이지는 자신이 열린 주소(`location`)로 API를 호출하고, `channelId`는 URL 경로(`/channel/:id`)에서 읽는다.
- 빌드는 배포 때 서버에서 한다(`fastbuild.sh`에 `pnpm install`, `pnpm build` 추가). 빌드 결과물은 git에 넣지 않는다.

### 미정

| 항목 | 선택지 | 비고 |
| --- | --- | --- |
| 프론트엔드 코드 방식 | (A) 기존 순수 JS와 자체 캔버스 엔진을 유지하고 Vite로 번들만 한다. jQuery·dayjs·chart.js는 npm 패키지로 바꾼다. (B) React 등으로 다시 작성한다. | A는 화면·동작 변화 없이 작업량이 작다. B는 사실상 새로 만드는 작업이다. |
