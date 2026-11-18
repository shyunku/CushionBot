# CushionBot 프로젝트 명세

최초 작성: 2026-11-18 10:02

## 목적

디스코드 서버 운영을 돕는 다기능 봇이다. 음악 재생, 리그 오브 레전드 내전 모집, 게임·활동 구인, team.gg 계정 연동, 음성 채널 접속 기록과 그 기록을 보여 주는 웹 대시보드(watcher)를 제공한다.

## 핵심 기능

| 기능 | 내용 | 주요 코드 |
| --- | --- | --- |
| 음악 | 지정한 텍스트 채널에 링크·검색어를 입력하면 재생 목록에 추가하고 Lavalink로 재생한다. 버튼으로 정지·일시정지·재생·건너뛰기·반복·나가기를 제어한다. | `src/main/java/service/music/` |
| 내전 | 내전 채널 지정, 일정 생성·변경, 참여/불참/미정 버튼, 호출, 종료·취소 | `src/main/java/service/leagueoflegends/` |
| 구인 | 구인 채널 생성, 모달 입력으로 구인 등록, 수정·취소·광고, 반응으로 참여 | `src/main/java/service/recruit/` |
| team.gg 연동 | Riot RSO 로그인으로 디스코드 계정 연동, 라인 선호도 설정 (`teamgg.kr` API 호출) | `src/main/java/core/command/SlashCommandParser.java` |
| 접속 기록 | 음성 채널 입장·퇴장·이동을 기록하고 서버/개인 랭킹을 출력한다. 기록은 `datafiles/access_logs.json`에 저장한다. | `src/main/java/service/watcher/GuildWatcher.java` |
| watcher 대시보드 | 서버별 접속 타임라인(10초까지 확대), 오늘 요약, 지금 접속 중, 랭킹(리더보드 6종), 서버 통계(평균 동시 접속자 곡선, 요일×시간 히트맵, 1년 날짜 잔디), 사람별 대시보드(시간대 빈도, 들낙 등). 봇 HTTP 서버(7918)가 빌드된 화면과 API·SSE를 함께 내려준다. 공유 링크(`/channel/{서버}`)로 열면 그 서버만 보인다. | `watcher/`, `src/main/java/service/watcher/` |

슬래시 명령은 `src/main/java/core/InternalEventListener.java`에서 등록하고 `src/main/java/listeners/SlashCommandInteractionListener.java`에서 분기한다. 명령 이름은 한국어다(예: `음악채널`, `내전모으기`, `구인`, `팀지지등록`).

## 스택

| 영역 | 내용 |
| --- | --- |
| 봇 | Java 17, Gradle 7.3.3 (wrapper), JDA 6.3.1, lavalink-client 3.4.0, Jedis(Redis), SnakeYAML, Jackson, log4j2/logback |
| 음악 서버 | Lavalink(별도 jar, 봇이 하위 프로세스로 실행). YouTube 재생은 youtube-plugin(snapshot) + TV 클라이언트 OAuth |
| 저장소 | Redis(`localhost:6379`, 채널·메시지 ID 등 임시 데이터), JSON 파일(`datafiles/access_logs.json`) |
| watcher | Vite + React + TypeScript (CSR, 빌드 결과를 봇이 정적으로 서빙), react-router, pnpm |

## 디렉터리 구조와 진입점

```
CushionBot/
├─ src/main/java/
│  ├─ core/CushionBot.java          봇 진입점 (Main-Class: core.CushionBot)
│  ├─ core/                         이벤트 리스너, 명령 파서·라우터, 버전
│  ├─ listeners/                    슬래시 명령·버튼·모달 리스너
│  ├─ service/music|leagueoflegends|recruit|guild|discord|inmemory/
│  ├─ service/watcher/              접속 기록, 대시보드 HTTP 서버(7918: 화면·API·SSE), 멤버 정보 캐시
│  └─ Utilities/                    TokenManager(credentials.yaml 읽기) 등
├─ src/main/resources/              log4j2.xml, logback.xml, credentials.yaml(비밀값, git 제외)
├─ watcher/                         대시보드 웹 (Vite + React, src/ → dist/)
├─ application.sample.yml           Lavalink 설정 샘플 (실제 application.yml은 git 제외)
├─ build.gradle, settings.gradle
└─ run.sh, restart.sh, stop.sh, fastbuild.sh   서버 운영 스크립트
```

봇 시작 순서 (`core/CushionBot.java`): Redis 연결 → 대시보드 HTTP 서버(7918) → Lavalink 하위 프로세스 → Lavalink 클라이언트 연결 → JDA 로그인.

## 포트

| 포트 | 용도 |
| --- | --- |
| 2333 | Lavalink (localhost) |
| 7918 | 봇 HTTP 서버: 대시보드 화면(`watcher/dist`), `/api/guilds`, `/api/guilds/{id}/snapshot`, `/sse` |
| 6379 | Redis (localhost) |

## 설정 파일

| 파일 | 내용 | git |
| --- | --- | --- |
| `src/main/resources/credentials.yaml` | 디스코드 봇 토큰, Google API 토큰, 봇 클라이언트 ID, `production` 여부, team.gg RSO 설정 | 제외 |
| `application.yml` | Lavalink 설정 (YouTube OAuth refresh token, po token 포함) | 제외 |

`production`이 `false`이면 봇 상태 메시지와 음악 박스에 점검 모드가 표시되고, 슬래시 명령은 특정 테스트 서버에만 등록된다.

## 실행·빌드·테스트

- 빌드: `./gradlew build` → `build/libs/CushionBot-1.0-SNAPSHOT.jar` (의존성 포함 fat jar)
- 실행 전제: 작업 디렉터리에 `Lavalink.jar`, `application.yml`, `src/main/resources/credentials.yaml`이 있고 Redis가 떠 있어야 한다. 대시보드를 보려면 `watcher/`에서 `pnpm install && pnpm build`로 `watcher/dist`를 만들어 둬야 한다(없으면 7918이 빌드 안내 문구를 응답한다).
- 서버 운영: `run.sh`, `restart.sh`, `stop.sh`는 `systemctl --user`로 `cushionbot.service`를 제어한다. `fastbuild.sh`는 watcher 빌드와 gradle 빌드 후 재시작한다. 서비스 유닛 파일은 저장소에 없다(서버의 `deploy/`, git 제외) — 확인 필요.
- 테스트: 테스트 코드가 없다.
- watcher 개발: `watcher/`에서 `pnpm dev`. API는 `WATCHER_API`(기본 `http://localhost:7918`)로 프록시한다.
- 접속 기록은 1년이 지난 세션을 지운다(`GuildWatcher.removeOldSessions`). 대시보드의 "누적"은 이 보관 기간 안의 합계다.
- 개발 PC 참고: Gradle 7.3.3은 최신 JDK(25)에서 돌지 않는다. 2026-11-18에 JDK 18로 `compileJava`가 되는 것을 확인했다.

## 확인 필요

- 운영 서버의 Lavalink 버전. v3.3.1.2 커밋 메시지는 Lavalink 4.2.x가 필요하다고 적고 있다.
- 서버의 `cushionbot.service` 유닛 내용(작업 디렉터리, PATH 등).
