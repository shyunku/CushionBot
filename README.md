# CushionBot

CushionBot은 Java와 JDA 라이브러리를 기반으로 제작된 다기능 Discord 봇입니다. 음악 재생, 리그 오브 레전드 내전 관리, 팀원 모집 등 다양한 기능을 통해 Discord 서버 활동을 지원합니다.

## ✨ 주요 기능

### 🎵 음악 재생

- **고품질 음악 스트리밍**: Lavalink를 사용하여 YouTube, SoundCloud 등 다양한 플랫폼의 음악을 안정적으로 재생합니다.
- **서버 전용 플레이어**: 각 서버(Guild)는 독립된 음악 채널과 플레이리스트를 가집니다.
- **직관적인 컨트롤러**: 메시지 버튼을 통해 재생/일시정지, 건너뛰기, 볼륨 조절, 반복 모드 설정 등 다양한 기능을 쉽게 제어할 수 있습니다.
- **간편한 사용법**: `/music 채널설정` 명령어로 음악 채널을 지정한 뒤, 해당 채널에 YouTube 링크나 검색어를 입력하기만 하면 자동으로 재생 목록에 추가되고 재생이 시작됩니다. 사용자가 입력한 메시지는 깔끔한 관리를 위해 자동으로 삭제됩니다.

### 🎮 리그 오브 레전드 내전 관리

- **간편한 인원 모집**: `/lol 내전시작` 명령어로 내전 시작 시간을 설정하고 참여 인원을 모집할 수 있습니다.
- **실시간 현황판**: 누가 참여/불참 의사를 밝혔는지 실시간으로 확인할 수 있는 임베드 메시지를 제공합니다. 참여자, 불참자, 무응답자 수가 표시되며, 버튼을 눌러 자신의 상태를 변경할 수 있습니다.
- **유연한 시간 설정**: 내전 시작 시간은 `HHmm` 형식으로 입력하며, 24시간을 초과하는 시간(예: `2500`은 다음 날 새벽 1시)도 설정 가능합니다.

### 🙋‍ 팀원 모집 시스템

- **자유로운 모집 공고**: `/모집` 명령어를 사용하여 게임, 스터디 등 원하는 활동에 대한 팀원 모집 공고를 생성할 수 있습니다.
- **간편한 참여**: 다른 사용자들은 공고 메시지에 추가된 `✅` 이모지 반응을 클릭하는 것만으로 간단하게 참여 의사를 밝힐 수 있습니다.
- **자동 관리**: 모집 공고는 등록자가 직접 삭제하거나, 설정된 시간이 지나면 자동으로 만료될 수 있습니다.

## 🛠️ 기술 스택

- **언어**: Java 17
- **빌드 도구**: Gradle
- **핵심 라이브러리**:
  - `net.dv8tion:JDA`: Discord Bot API
  - `dev.arbjerg:lavalink-client`: Lavalink 클라이언트
  - `redis.clients:jedis`: Redis 데이터베이스 연동
- **데이터베이스**: Redis (서버별 채널 ID, 메시지 ID 등 임시 데이터 저장)
- **오디오 서버**: Lavalink

## ⚙️ 설치 및 실행

### 사전 요구사항

- Java 17 이상
- Gradle
- Redis 서버
- Lavalink 서버

### 설정

1. **저장소 복제**:

   ```bash
   git clone https://github.com/shyunku/CushionBot.git
   cd CushionBot
   ```

2. **인증 정보 설정 (`credentials.yaml`)**:
   `src/main/resources/` 경로에 `credentials.yaml` 파일을 생성하고 아래 내용을 채워넣습니다. 이 파일은 `.gitignore`에 추가하여 외부에 노출되지 않도록 관리하는 것을 강력히 권장합니다.

   ```yaml
   tokens:
     discord_bot_token: "YOUR_DISCORD_BOT_TOKEN"
     google_api_token: "YOUR_GOOGLE_API_TOKEN" # YouTube API 사용 시 필요
     bot_client_id: "YOUR_BOT_CLIENT_ID"
     production: true # 개발 모드는 false

   teamgg: # (선택) Team.gg 연동 기능 사용 시
     rso_client_id: "YOUR_RSO_CLIENT_ID"
     rso_redirect_uri: "YOUR_RSO_REDIRECT_URI"
   ```

3. **Lavalink 설정 (`application.yml`)**:
   `src/main/resources/application.yml` 파일을 열어 Lavalink 서버의 주소, 포트, 비밀번호를 설정합니다.
   ```yaml
   lavalink:
     server:
       password: "youshallnotpass" # Lavalink 비밀번호
       # ... 기타 설정
   ```

### 빌드

아래 명령어를 사용하여 프로젝트를 빌드합니다.

```bash
./gradlew build
```

### 실행

프로젝트 루트 디렉토리에서 제공되는 셸 스크립트를 사용하여 봇을 실행할 수 있습니다.

- **시작**:

  ```bash
  ./run.sh
  ```

  이 스크립트는 `nohup`을 사용하여 봇을 백그라운드에서 실행하고 로그를 `nohup.out`에 기록합니다.

- **중지**:

  ```bash
  ./stop.sh
  ```

- **재시작**:
  ```bash
  ./restart.sh
  ```

## 📖 명령어 목록 (예시)

CushionBot은 주로 슬래시(`/`) 명령어를 통해 상호작용합니다. (정확한 명령어는 소스 코드의 `SlashCommandInteractionListener` 또는 관련 클래스에서 확인할 수 있습니다.)

- `/music 채널설정`: 현재 텍스트 채널을 이 서버의 음악 전용 채널로 지정합니다.
- `/lol 채널설정`: 현재 텍스트 채널을 리그 오브 레전드 내전 관리 채널로 지정합니다.
- `/lol 내전시작 시간: [HHmm]`: 지정된 시간에 내전 인원 모집을 시작합니다. (예: `/lol 내전시작 시간: 2230`)
- `/lol 내전종료`: 진행 중인 내전 모집을 중단합니다.
- `/모집 [게임이름] [내용]`: 새로운 팀원 모집 공고를 등록합니다.
- `/모집취소`: 자신이 등록한 모집 공고를 삭제합니다.

---
