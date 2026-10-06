# RCA — b-deploy-5xx (item-bank-api, 2026-09-17)

## 요약

- **무엇이 · 언제**: 2026-09-17 v1.4.2 배포(13:40:05 시작) 뒤 두 가지 장애가 있었다. ① 13:41:51 ~ 13:41:59 재기동 중 전 API 502 8건. ② 13:46:38 ~ 14:34:45 학급 1 재배포 API(`POST /api/distributions/{42,43}/redistribute`) 500 39건. 로그가 14:39:58 에 끝나서 ②가 언제 끝났는지는 관측하지 못했다.
- **원인 ②**: v1.4.2 가 재배포 응답에 학급 배포 이력 조회(`listByClass`)를 추가했다. 그 결과 재배포할 때마다 학급 1 에 배포 전부터 있던 깨진 과제 참조(assignment id 9)를 읽게 되었다. 이 깨진 참조 때문에 배포 전에도 500 이 7건(09:52 ~ 11:18) 났다.
- **원인 ①**: 대기 호스트 없이 호스트 한 대만 재기동했다(무중단 배포 아님). ②와는 별개 사건이다.
- **확신 수준**: ②는 **높음(확정 아님)**이다. 배포 이미지와 저장소 코드가 같은지, 운영 DB 에 고아 행이 실제로 있는지는 확인하지 못했다. ①은 로그와 배포 문서가 서로 맞지만 nginx error 로그가 없다.
- **추가 영향**: 500 을 받은 재배포 39건도 DB 에는 커밋되었을 수 있다. 데이터 확인이 필요하다(6.1).

대상 자료: `incident-logs/b-deploy-5xx/`. 모든 수치는 `wc` · `head` · `tail` · `grep -c` · `grep -n` · `awk` 집계로 얻었다(파일 전체를 읽지 않음).

## 1. 수집 범위

### 1.1 파일별 기록 기간 · 형식

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---|---|---|---|
| `app.log` | 2,938 (타임스탬프 줄 1,275 + 스택트레이스 연속 줄 1,663) | 2026-09-17 09:00:04.878 ~ 14:39:57.905 | ISO 8601, 밀리초 — `2026-09-17T09:00:04.878+09:00` | `+09:00` (1,275줄 전부) |
| `nginx-access.log` | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | nginx combined — `[17/Sep/2026:09:00:00 +0900]` (초 단위) | `+0900` (20,365줄 전부) |
| `deploy-history.md` | 39 | 2026-09-01 ~ 2026-09-17 (배포 4건) | `YYYY-MM-DD HH:MM:SS`, 오프셋 표기 없음 | 문서 머리말에 "모두 KST" 명시 |

**KST 변환 규칙**: 세 파일 모두 이미 KST(UTC+9)라서 변환할 필요가 없다. 비교할 때 주의할 점은 아래와 같다.

- `nginx-access.log` 는 초 단위, `app.log` 는 밀리초 단위다. 두 로그를 맞춰 볼 때는 초 단위로 내림해서 비교한다.
- `app.log` 의 스택트레이스 연속 줄(1,663줄)에는 시각이 없다. 바로 앞의 타임스탬프 줄 시각을 따른다고 본다.
- 날짜는 2026-09-17 하루뿐이다. 두 로그 모두 이 날짜만 들어 있다.

### 1.2 분 단위 이상 신호 집계

집계 기준:

- `app.log`: 레벨 필드 `ERROR` · `WARN`. 이 파일에는 `timeout` · `timed out` 이 0건이다.
- `nginx-access.log`: status 필드 `5xx`(500 · 502). 이 파일에는 504와 `timeout` · `upstream` 문자열이 0건이다. 비교를 위해 `409` 와 4xx 전체도 함께 셌다.

전체 건수:

| 파일 | 신호 | 건수 | 내용(메시지를 패턴으로 묶음) |
|---|---|---|---|
| app.log | ERROR | 46 | `unhandled exception on /api/distributions/N/redistribute` 45, `.../distributions/N` 1 |
| app.log | WARN | 35 | `state conflict: 마감된 과제는 재배포할 수 없습니다` 35 |
| nginx | 500 | 46 | `POST /api/distributions/N/redistribute` 45, `/api/distributions/N` 1 (app ERROR와 분 단위로 1:1 일치) |
| nginx | 502 | 8 | 13:41:51 ~ 13:41:59에 모두 몰려 있음 |
| nginx | 409 | 35 | app WARN과 분 단위로 1:1 일치 |
| nginx | 4xx(409 제외) | 994 | 대부분 404(봇 경로 · 오타 경로) |

분 단위 분포 (0건인 분은 생략):

| 시각대 | nginx 500 (= app ERROR) | nginx 502 | app WARN (= nginx 409) |
|---|---|---|---|
| 09:00 ~ 13:39 (280분) | 09:52 ×3, 10:31 ×1, 11:17 ×1, 11:18 ×2 → **7건, 분당 0.025** | 0 | 31건, 분당 0.11 (분당 최대 2) |
| 13:40 ~ 13:45 | 0 | **13:41 ×8** | 0 |
| 13:46 ~ 14:39 (54분) | 13:46 ×2, 13:47 ×1, 13:50 ×4, 13:52 ×1, 13:53 ×1, 13:55 ×3, 13:58 ×2, 14:02 ×2, 14:03 ×4, 14:06 ×1, 14:11 ×3, 14:15 ×1, 14:16 ×2, 14:22 ×3, 14:27 ×2, 14:28 ×1, 14:31 ×2, 14:32 ×1, 14:34 ×3 → **39건, 분당 0.72** | 0 | 13:59, 14:24, 14:30, 14:35 → 4건 |

평소 수준과 비교한 값:

| 지표 | 09:00 ~ 13:39 | 13:40 ~ 14:39 | 판단 |
|---|---|---|---|
| nginx 전체 요청/분 | 평균 59.9 (하루 전체 최소 40, 최대 82) | 13:38 ~ 13:44 사이 58 ~ 76 | 변화 없음 |
| nginx 4xx(409 제외)/분 | 2.92 | 3.00 | 변화 없음 |
| app INFO/분 | 3.33 | 4.35 | 13:41의 재기동 로그 28줄 때문에 올라감 |
| redistribute 요청 중 500 비율 | 6 / 103 (5.8%) | 39 / 49 (79.6%) | **크게 달라짐** |
| app ERROR · nginx 500/분 | 0.025 | 0.72 | **약 29배** |

같은 시각대의 관측 기록(원인 판단은 하지 않았다):

- `deploy-history.md`: v1.4.2 배포 시작 13:40:05, 완료 13:42:31.
- `app.log:1240`: 13:41:48 GracefulShutdown 시작.
- `app.log:1245`: 13:41:55 Starting.
- `app.log:1264`: 13:42:00 Started.
- 502 8건(13:41:51 ~ 59)은 위 재기동 구간과 시각이 겹친다.

**제안 구간**

| 구간 | 시작 | 끝 | 근거 |
|---|---|---|---|
| A. 재기동 중 502 | 13:41:51 | 13:41:59 | 502 8건이 모두 이 안에 있다 |
| B. 500 지속 증가 | 13:46 | 14:34 이후(**끝을 관측하지 못함**) | 마지막 500은 14:34이고 로그는 14:39:58에 끝난다. 14:35 ~ 14:39의 5분 동안 0건이지만, 평소 간격(최대 4분)과 구분할 만큼 길지 않다 |
| 이상 구간(A ∪ B) | **13:41** | **14:40(로그 끝)** | |

### 1.3 앞뒤 여유

| 방향 | 여유 | 수집 구간 | 이유 |
|---|---|---|---|
| 앞 | 이상 시작(13:41)보다 **31분 앞** | **13:10 ~** | 배포 승인(13:38) · 배포 시작(13:40:05) 직전의 정상 상태를 30분 이상 확보한다. 직전 WARN(13:13, 13:15)도 포함된다 |
| 뒤 | 마지막 500(14:34)보다 **6분 뒤** = 로그 끝 | **~ 14:40** | 현재 자료로는 더 늘릴 수 없다. **14:40 이후 로그를 최소 30분(~15:10) 더 받아야** 종료 시점을 판단할 수 있다 |

추가로 따로 두는 기준선:

- **09:00 ~ 13:40** 구간은 비교용 기준선으로 따로 둔다. 배포 전에도 redistribute 500이 7건(09:52, 10:31, 11:17 ~ 18) 있었으므로, 이 7건을 배포 후 500과 같은 종류로 볼지는 다음 단계에서 구간을 나눠 비교해야 한다.
- 지금은 2026-09-17 하루치뿐이다. 배포 전 평소 수준을 더 확인하려면 v1.4.1 배포(09-14 18:07) 이후 다른 날의 같은 시간대 로그가 필요하다.

**결론: 1차 수집 구간은 2026-09-17 13:10:00 ~ 14:39:58 KST, 비교 기준선은 09:00:00 ~ 13:40:00 KST 이다. 추가로 받을 자료는 14:40 이후 로그다.**

## 2. 타임라인

- 대상 구간: 2026-09-17 13:10:00 ~ 14:39:58 KST. 1.3에서 정한 수집 구간이다.
- 대상 줄 범위: `app.log:1146` ~ 끝, `nginx-access.log:14793` ~ 끝, `deploy-history.md` 전체.
- 줄임말: `app` = `app.log`, `nginx` = `nginx-access.log`, `deploy` = `deploy-history.md`.
- 원문 발췌에서 공통 접두어(`+09:00  INFO 1 --- [item-bank-api] [스레드]`, nginx 의 IP · 사용자 에이전트)는 지웠다.
- `deploy` 는 초 단위, `nginx` 는 초 단위, `app` 은 밀리초 단위다. 출처가 다른 두 사건의 차이가 5초 이내면 **(선후 불확실)** 이라고 적었다.

### 2.1 사건 목록

| 시각 (KST) | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 13:10 ~ 13:39 | nginx:14793 ~ | 평소 수준. 전체 요청은 분당 평균 64.5(최대 82, 13:35), 404 · "no handler" 류도 평소 수준이다 | — |
| 13:12:21 | app:1149, nginx:14926 | 재배포 성공(dist 6, class 3). 13:40 전까지 재배포 200이 9건 있다 (13:12 ~ 13:25 사이) | `redistributed distribution 6 (assignment 4, class 3)` |
| 13:13:03 | app:1153, nginx:14965 | **WARN** 409 — 마감 과제 재배포 거절(dist 1). 13:15:54(dist 3, app:1166)에 1건 더 있다 | `WARN … state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=1` |
| 13:13:05 · 13:13:27 | app:1154 · 1157, nginx:14969 · 15002 | 재배포 성공(dist 43, class 1). 같은 IP(교사단말A)에서 22초 간격으로 2회 | `redistributed distribution 43 (assignment 6, class 1)` |
| 13:24:45 | app:1191 | 학급 리포트 생성(class 1) | `built class report for class 1: 3 distributions, 10 submissions` |
| 13:38 | deploy:21 | v1.4.2 승인 (분 단위만 기록됨) | `승인: ***@example.com (13:38)` |
| 13:40:05 | deploy:7 | v1.4.2 **배포 시작** | `\| v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \|` |
| 13:41:20 | app:1238, nginx:16829 | 재기동 전 마지막 재배포 성공(dist 43, class 1) | `redistributed distribution 43 (assignment 6, class 1) reason=학생A` |
| 13:41:48.214 | app:1240 | Graceful shutdown 시작 | `GracefulShutdown : Commencing graceful shutdown. Waiting for active` |
| 13:41:50.144 ~ .224 | app:1241 ~ 1244 | 종료 완료, JPA · Hikari 풀(itembank-pool) 닫힘 | `itembank-pool - Shutdown completed.` |
| 13:41:50 | nginx:16862 ~ 16864 | 재기동 전 마지막 200 응답 3건 **(선후 불확실 — app:1241 과 같은 초)** | `"GET /api/units/M5-3/items?student=학생B …" 200` |
| 13:41:51 | nginx:16865 | **502 시작**, 이후 13:41:59 까지 8건 (9초 동안) | `"GET /api/units/M5-2/items?student=학생C HTTP/1.1" 502` |
| 13:41:55.902 | app:1245 | v1.4.2 기동 시작 **(선후 불확실 — nginx:16870 502 와 같은 초)** | `Starting ItemBankApplication v1.4.2 using Java 21.0.4 with PID 1` |
| 13:41:55.906 | app:1246 | 설정 관련 기록: 활성 프로필 없이 default 로 기동. 이 날 로그에는 이전 기동 기록이 없어서 평소와 비교할 수 없다 | `No active profile set, falling back to 1 default profile: "default"` |
| 13:41:58.351 ~ .535 | app:1258 ~ 1260 | Hikari 풀 시작, 커넥션 추가 **(선후 불확실 — nginx:16871 502 와 같은 초)** | `itembank-pool - Added connection org.mariadb.jdbc.Connection@4c1e7a3b` |
| 13:41:59 | nginx:16872 | 마지막 502 **(선후 불확실 — app:1261 ~ 1262 JPA 초기화와 같은 초)** | `"GET /api/units/M6-1/items?student=학생A HTTP/1.1" 502` |
| 13:42:00.304 | app:1264 | 기동 완료 (4.921초) | `Started ItemBankApplication in 4.921 seconds (process running for 5.488)` |
| 13:42:01 | nginx:16873 | 재기동 후 첫 200 **(선후 불확실 — app:1264 와 1초 차이)** | `"GET /api/units HTTP/1.1" 200` |
| 13:42:10 | app:1266, nginx:16884 | 재기동 후 첫 재배포 성공(dist 7, class 3) | `redistributed distribution 7 (assignment 5, class 3)` |
| 13:42:31 | deploy:7 | v1.4.2 **배포 완료** 기록 **(선후 불확실 — app:1267 13:42:33 과 2초 차이)** | `\| 2026-09-17 13:42:31 \|` |
| 13:46:38.107 | app:1274 | dist 42(class 1) 재배포 처리 로그가 남음. 바로 다음 줄이 ERROR다 | `redistributed distribution 42 (assignment 4, class 1) reason=보강 수업` |
| **13:46:38.113** | app:1275 ~, nginx:17155 | **첫 500 / ERROR** (dist 42). 예외는 `Assignment` id `9` 조회 실패. app ERROR 와 nginx 500 이 같은 초에 찍혀 **선후 불확실**. **이후 38건, 13:46 ~ 14:34 사이 분당 약 0.78건** | `ERROR … unhandled exception on /api/distributions/42/redistribute` |
| (위와 같은 묶음) | app:1276 · 1304 | 예외 본문. 구간 안 ERROR 39건 모두 같은 예외, 같은 id `9` | `JpaObjectRetrievalFailureException: Entity …Assignment… value \`9\` does not exist` |
| 13:50:00 | app:1393, nginx:17353 | 500 구간 안의 재배포 성공(dist 8, class 2). 같은 구간의 다른 성공: 13:51:08 dist 7 · 13:56:43 dist 44 · 14:21:02 / 14:22:46 dist 5 (nginx:17424 · 17734 · 19238 · 19347) | `redistributed distribution 8 (assignment 6, class 2)` |
| 13:50:12 | app:1395, nginx:17365 | dist 43(class 1)의 첫 500. 이후 dist 43 은 20건, dist 42 는 19건이 모두 500 | `unhandled exception on /api/distributions/43/redistribute` |
| 13:59:30 | nginx:17867 | WARN 409 (dist 3). 이후 14:24:12 · 14:30:00 · 14:35:26 에 1건씩 → 평소 빈도 | `"POST /api/distributions/3/redistribute HTTP/1.1" 409` |
| 14:34:45.439 | app:2887, nginx:20084 | **마지막 500 / ERROR** (dist 43) **(선후 불확실 — 같은 초)** | `unhandled exception on /api/distributions/43/redistribute` |
| 14:35:26 | nginx:20119 | 구간 안 마지막 재배포 요청(409) | `"POST /api/distributions/2/redistribute HTTP/1.1" 409` |
| 14:39:58 | nginx:20365 | 로그 끝 | `[17/Sep/2026:14:39:58 +0900] "GET /api/units HTTP/1.1" 200` |

### 2.2 첫 500(13:46:38)보다 앞선 기록 중 평소와 다른 것

| 항목 | 결과 |
|---|---|
| 느린 쿼리 | 해당 줄 없음. `slow` · `took` · `exceeded` · `leak` 0건이다. nginx 로그에는 응답 시간 필드가 없다(combined 형식) |
| 경고 | 13:13:03, 13:15:54 의 WARN 409 2건. 09:00 ~ 13:40 의 평소 빈도(분당 0.11)와 같은 수준이다 |
| 설정 재적재 | 재기동 시 기동 로그 21줄(app:1245 ~ 1264). 그중 프로필 관련 줄(app:1246)이 있다. `refresh` · `reload` 류 런타임 재적재 줄은 0건이다 |
| 트래픽 변화 | 분당 전체 요청은 변화 없음(13:10 ~ 13:39 평균 64.5, 13:46 ~ 14:39 평균 60.1). 13:41:51 ~ 59 동안은 502 만 응답했다. 재배포 요청은 13:10 ~ 13:39 에 11건(30분), 13:46 ~ 14:39 에 49건(54분)이다. 13:46 이후 교사단말A 가 같은 dist 에 10 ~ 30초 간격으로 2 ~ 4회씩 연속 요청하는 묶음이 반복된다(예: nginx:17155 · 17170 · 17181) |
| 그 밖 | 13:42 ~ 13:46 사이에는 ERROR · WARN · 5xx 가 없다. 이 사이 재배포 요청은 13:42:10(dist 7, 200) 1건뿐이다 |

### 2.3 구간 안 반복 소음 (묶어서 기록)

아래 INFO 4xx 류는 구간 전체에서 평소 비율(분당 약 3건, 1.2 참조)대로 계속 나온다. 배포 전후로 비율이 달라지지 않았다.

- `no handler for /robots.txt`
- `no handler for /favicon.ico`
- `no handler for /apple-touch-icon.png`
- `no handler for /api/unit` · `/api/item/N`
- `not found: 문항이 없습니다` · `배포가 없습니다`

## 3. 가설과 검증

- 3.1 ~ 3.3 은 가설만 세운다. 5열(판정)은 **로그로 확인할 수 있는 반증 조건만** 실행해서 매겼다(3.4). "유지" 는 "맞다" 는 뜻이 아니다. 로그 범위에서 반증되지 않았다는 뜻이다. 가설끼리 서로를 배제하지 않으며, 둘 이상이 함께 맞을 수도 있다.
- 가장 많이 나온 오류 메시지 `JpaObjectRetrievalFailureException: … Assignment … value 9 does not exist`(46건)는 **증상**이다. 아래 가설은 "왜 그 예외가 13:46 이후 더 자주 나는가" 를 설명하려는 것이다.
- 줄번호 표기: `rca` = 이 문서(`docs/rca/b-deploy-5xx.md`), `app` · `nginx` · `deploy` 는 2절과 같다.

### 3.1 가설을 세우며 추가로 확인한 기록

2절 타임라인에 없던 사실이다. `grep -n` · `uniq -c` 로만 셌고, 해석은 하지 않았다.

| 항목 | 결과 | 출처 |
|---|---|---|
| 배포 전 500 7건의 예외 | 배포 후와 **같은 예외, 같은 id `9`**. 대상은 모두 dist 41 (redistribute 6, GET 1) | `app:194` ~ `195`, `app:451` ~ `452` |
| 배포 전 500 의 호출 경로 | `DistributionService.loadOrThrow:65` ← `DistributionController.redistribute:38` (재배포 대상 건 자체를 읽다가 실패) | `app:205`, `app:214` |
| 배포 후 500 39건의 호출 경로 | 39건 모두 `DistributionService.listByClass:38` ← `DistributionController.redistribute:40` (재배포는 끝났고, 학급 이력을 읽다가 실패) | `app:1294` |
| 배포 후 500 이 난 dist | dist 42 · 43 뿐. 둘 다 class 1. 같은 시간대의 class 2 · 3 재배포(dist 5 · 7 · 8 · 44)는 200 | `rca:122` ~ `123` |
| 500 의 요청 IP | redistribute 500 45건 모두 `교사단말A` | `nginx` 의 `$9==500` 집계 |
| 기동 프로필 | `No active profile set` 줄은 이 날 로그에 1번(재기동 때)만 있다 | `app:1246` |

### 3.2 가설 표

| # | 계층 | 1. 가설 한 문장 | 2. 지지 근거 (파일:줄번호) | 3. 반증 조건 (이것이 확인되면 이 가설은 틀렸다) | 4. 확인 방법 | 5. 로그 확인 판정 (상세는 3.4) |
|---|---|---|---|---|---|---|
| H1 | 애플리케이션 코드 (v1.4.2 변경) | v1.4.2 에서 재배포 응답에 추가된 `listByClass(classId)` 호출이 **재배포 대상과 무관한 같은 학급의 배포 행까지 모두 읽게 되면서**, 원래 dist 41 조회 때만 터지던 깨진 참조를 class 1 의 모든 재배포 요청이 밟게 되었다. | `rca:105`(v1.4.2 배포 시작), `rca:119` ~ `121`(재배포 로그가 찍힌 직후 같은 요청에서 ERROR), `rca:123`(class 1 의 dist 43 도 500), `rca:122`(class 2 · 3 은 같은 구간에 성공), `deploy:15` ~ `16`(변경 지점이 `listByClass` 호출 추가), `app:1294`(스택에 `DistributionController.redistribute:40`) | (a) 배포 후 500 중 스택에 `DistributionController.java:40` · `listByClass` 가 **없는** 건이 있다. (b) 13:46 이후 class 1 재배포가 v1.4.2 에서 200 으로 끝난 건이 있다. (c) class 1 에 assignment 9 를 참조하는 배포 행이 **없다**. (d) 배포된 이미지의 `DistributionController` 40번 줄이 `listByClass` 호출이 아니다. | `grep -n "DistributionController.java:40" incident-logs/b-deploy-5xx/app.log \| wc -l` 를 배포 후 ERROR 건수(39)와 비교 · `awk '$9==200 && $7 ~ /distributions\/(42\|43)\/redistribute/' nginx-access.log` 로 13:46 이후 200 유무 확인 · v1.4.2 태그의 `modern/api/src/main/java/com/example/assignment/DistributionController.java:40` 확인 (`git show v1.4.2:<경로>`) · 아래 H2 의 SQL 로 class 1 행 확인 | **유지** — 반증 (a) · (b) 둘 다 성립하지 않았다: 배포 후 ERROR 39건 중 39건이 `DistributionController.java:40` 경유(C1), 13:42 이후 dist 42 · 43 재배포 200 은 0건(C2). (c) 는 DB 조회, (d) 는 배포 이미지가 있어야 확인된다(저장소에 `v1.4.2` 태그 없음, C3) |
| H2 | DB · 데이터 | `distribution` 테이블에 **존재하지 않는 assignment 9 를 가리키는 고아 행**(dist 41 로 추정)이 있고, 이 데이터 불일치가 배포 이전부터 있었다. 코드 변경과 무관하게 이 행을 읽는 모든 경로가 500 을 낸다. | `rca:43`(배포 전에도 500 7건), `rca:82`(배포 전 500 을 같은 종류로 볼지 미정), `rca:121`(배포 후 예외도 id `9`), `app:194` ~ `195` · `app:451` ~ `452`(배포 전 예외도 id `9`, 대상 dist 41) | (a) 운영 DB 에 `assignment.id = 9` 가 **존재한다**(그렇다면 "없는 행" 이 아니라 조회 조건 · 세션 · 스키마 문제다). (b) `assignment_id` 가 `assignment` 에 없는 `distribution` 행이 0건이다. (c) 운영 DB 의 `fk_distribution_assignment` 가 살아 있고 `FOREIGN_KEY_CHECKS` 가 끈 적이 없다(그렇다면 고아 행이 생길 수 없다). | 읽기 전용 계정으로: `SELECT id FROM assignment WHERE id = 9;` · `SELECT d.id, d.class_id, d.assignment_id FROM distribution d LEFT JOIN assignment a ON a.id = d.assignment_id WHERE a.id IS NULL;` · `SHOW CREATE TABLE distribution;` 로 FK 존재 확인 · 스키마 원본 `db/mariadb/init/01-schema.sql:92` ~ `102` 와 대조 · assignment 9 삭제 이력(DB 감사 로그 / `assignment-thymeleaf` 의 삭제 기능 사용 기록) | **판단 불가(로그 부족)** — 반증 조건 (a) ~ (c) 가 모두 DB 상태라 로그로는 확인할 수 없다. 로그상 반증은 없다: 재기동 전 7건 · 후 39건 모두 같은 id `9`(C4). **필요한 자료**: 운영 DB 의 `assignment` · `distribution` 조회 결과, `SHOW CREATE TABLE distribution`, assignment 9 삭제 시점이 남은 DB 감사 로그(binlog 등) |
| H3 | 배포 · 설정 변경 | v1.4.2 재기동 때 활성 프로필 없이 `default` 로 떠서, 이전 인스턴스와 **다른 설정(데이터소스 · 스키마 · Hibernate 설정)** 으로 동작하고 있다. | `rca:112`(`No active profile set`, 이전 기동과 비교 불가), `app:1246`, `rca:113`(재기동 때 새 Hikari 풀 생성), `rca:105` ~ `118`(재기동 직후부터 이상 구간) | (a) v1.4.1 이하의 기동 로그에도 같은 `No active profile set` 줄이 있다(평소에도 default 로 떴다). (b) 재기동 전후 접속 대상(JDBC URL · DB 이름 · 사용자)이 같다. (c) 배포 **전**(v1.4.1, 같은 데이터소스) 500 도 같은 id `9` 예외였다는 사실(3.1 첫 줄)이 설정 차이 없이 설명된다. | v1.4.1 기동 시점(09-14 18:07 무렵)의 `app.log` 에서 `grep -n "No active profile\|The following .* profile"` · 배포 파이프라인의 실행 인자 / 환경 변수에서 `SPRING_PROFILES_ACTIVE` 유무 비교(v1.4.1 vs v1.4.2) · 이미지 안 `application*.yml` 목록과 `modern/api/src/main/resources/application.yml:4` ~ `8` 의 datasource 비교 · 재기동 전후 `SELECT @@hostname, DATABASE();` | **판단 불가(로그 부족)** — 이 날 로그의 기동 기록은 v1.4.2 1번뿐이라(C5) 반증 (a) 를 확인할 수 없다. JDBC URL 이 찍힌 줄도 없어 (b) 도 확인할 수 없다(C6). (c) 는 확인됐다: 재기동 전 인스턴스에서도 같은 id `9` 예외가 났다(C4). 설정 변경 없이도 예외가 날 수 있다는 뜻이지만 H3 를 기각하지는 않는다. **필요한 자료**: v1.4.1 기동 로그(09-14 18:07 무렵), v1.4.1 · v1.4.2 배포 때의 실행 인자 · 환경 변수(`SPRING_PROFILES_ACTIVE`), 기동 시 datasource URL 이 찍힌 로그 |
| H4 | 트래픽 · 클라이언트 (증폭) | 실제로 깨진 재배포 작업 수는 적고, 500 을 받은 **단일 클라이언트(교사단말A)의 자동 재시도**가 500 건수를 부풀려 "약 29배" 로 보이게 했다. 특히 v1.4.2 는 재배포를 커밋한 **뒤에** 500 을 돌려주므로 클라이언트가 실패로 알고 다시 보낸다. | `rca:136`(교사단말A 가 같은 dist 에 10 ~ 30초 간격 2 ~ 4회 연속, `nginx:17155` · `17170` · `17181`), `rca:102`(배포 전에도 같은 IP 가 22초 간격 2회), `rca:119` ~ `120`(재배포 로그 `redistributed …` 다음 줄이 ERROR — 처리 후 실패), 3.1 의 "500 요청 IP 전부 교사단말A" | (a) 연속 요청 묶음을 1건으로 합쳐도(같은 IP · 같은 dist · 60초 이내) 배포 후 500 비율이 배포 전 대비 크게 높다. (b) 묶음 안 요청의 사유(`reason=`)가 서로 다르다 — 재시도가 아니라 서로 다른 사용자 조작이다. (c) 교사단말A 가 사용자 단말이 아니라 여러 사용자를 모은 게이트웨이 · 프록시다. | `awk '$7 ~ /redistribute/ {print $4, $1, $7, $9}' nginx-access.log` 로 IP · dist 별 시간 간격을 뽑아 60초 이내 묶음 개수 세기 · `grep -n "redistributed distribution 4[23]" app.log` 로 묶음 안 `reason=` 비교 · 교사단말A 의 정체(교사 단말 / 프런트 서버) 확인 · `modern/web` 의 재배포 호출부(`src/api/client.ts` 의 `getJson()` 을 쓰는 쪽)에 재시도 로직이 있는지 확인 | **기각** — 반증 (a) 가 성립했다: 연속 요청 묶음(같은 IP · 같은 URL · 60초 이내)을 1건으로 합쳐도 첫 요청이 500 인 묶음의 비율이 배포 전 2.2%(2/93) → 배포 후 58.3%(14/24)로 약 26배다. 합치기 전 요청 기준 비율은 5.8% → 79.6% 다(C7). 재시도 묶음이 있기는 하다(배포 후 24묶음 중 13묶음이 2회 이상). 하지만 이 묶음들이 500 비율 증가를 설명하지는 못한다 |
| H5 | 인프라 (502 구간 한정) | 13:41:51 ~ 59 의 502 8건은 **단일 호스트 비무중단 재기동** 중 nginx 가 내려간 upstream 으로 요청을 보낸 결과다. 500 증가와는 별개의 사건이다. | `rca:69`(구간 A), `rca:107` ~ `116`(종료 13:41:50 → 첫 502 13:41:51 → 기동 완료 13:42:00 → 첫 200 13:42:01), `deploy:3`(대기 호스트 api-2 평소 정지), `deploy:18`(무중단 아님, 예상 중단 10초) | (a) 502 가 재기동 구간(13:41:48 ~ 13:42:00) 밖에서도 나온다. (b) 같은 시각 nginx upstream 에 살아 있는 다른 서버(api-2)가 등록 · 기동되어 있었다. (c) nginx error 로그의 502 원인이 `connect() failed (111: Connection refused)` 가 아니다. | `awk '$9==502' nginx-access.log` 로 502 시각 전수 확인 · nginx `upstream` 설정 파일과 13:41 의 api-2 상태 확인 · nginx **error** 로그(이번 자료에 없음 — 추가 요청 필요)에서 13:41:51 ~ 59 줄 확인 | **유지** — 반증 (a) 가 성립하지 않았다: 502 8건이 모두 13:41:51 ~ 59 에 있고, 사이에 200 이 끼어 있지 않다. 앞뒤 200 은 13:41:50 과 13:42:01 이다(C8). (b) · (c) 는 로그 부족: 이번 자료에는 api-2 · upstream 흔적이 0건이다(C9). **필요한 자료**: nginx error 로그(13:41:51 ~ 59 의 upstream 오류 사유), nginx upstream 설정, 13:41 시점의 api-2 상태 |

### 3.3 가설 사이의 관계 (판정 아님)

- H1 과 H2 는 같은 예외를 서로 다른 계층에서 설명한다. H1 은 "왜 13:46 부터 늘었나", H2 는 "왜 그 예외가 애초에 나는가" 에 대한 가설이다. 한쪽의 반증이 다른 쪽의 반증이 되지는 않는다.
- H4 는 원인 가설이 아니라 **크기(건수 · 비율)에 대한 가설**이다. H4 가 맞아도 H1 · H2 의 검증은 그대로 필요하다.
- H5 는 구간 A(502)만 다룬다. 구간 B(500)와는 원인이 다를 수 있으므로 따로 검증한다.
- 다음 단계에서 필요한 추가 자료: 운영 DB 읽기 전용 조회 결과(H1 · H2), v1.4.1 기동 로그와 배포 파이프라인 실행 인자(H3), 교사단말A 정체와 클라이언트 재시도 정책(H4), nginx error 로그와 upstream 설정(H5).

### 3.4 로그 확인 실행 기록

모든 명령은 `incident-logs/b-deploy-5xx/` 에서 실행했다. 결과는 출력 그대로다(긴 줄은 앞부분만 남김). DB 조회 · 배포 이미지 · 다른 날 로그가 필요한 확인 방법은 실행하지 않았다.

**C1 — H1 (a): 배포 후 ERROR 중 `DistributionController.java:40` 경유 건수**

```bash
grep -n "DistributionController.java:40" app.log | wc -l     # → 39
awk 'NR>=1264 && / ERROR /' app.log | wc -l                  # → 39  (app:1264 = 기동 완료 이후)
```

**C2 — H1 (b): 13:42 이후 dist 42 · 43 재배포의 status**

```bash
awk '$7 ~ /distributions\/(42|43)\/redistribute/ {split($4,t,":"); print t[2]":"t[3], $7, $9}' nginx-access.log \
  | awk '$1>="13:42" {print $2,$3}' | sort | uniq -c
#   19 /api/distributions/42/redistribute 500
#   20 /api/distributions/43/redistribute 500
awk '$7 ~ /distributions\/(42|43)\/redistribute/ && $9==200 {print $4}' nginx-access.log | tail -3
#   [17/Sep/2026:13:13:05
#   [17/Sep/2026:13:13:27
#   [17/Sep/2026:13:41:20     ← 마지막 200 은 재기동 전
```

**C3 — H1 (d): 배포 버전 태그**

```bash
git tag -l 'v1.4.2'          # → (출력 없음)
```

저장소 HEAD 의 `modern/api/src/main/java/com/example/assignment/DistributionController.java:40` 은 `listByClass` 호출이다. 그러나 저장소 코드가 배포된 이미지와 같다는 근거는 없다.

**C4 — H2 · H3: 재기동(app:1245) 전후 예외의 id**

```bash
awk '/^Caused by/ {print (NR<1245?"before":"after"), $0}' app.log \
  | sed -E 's/.*(before|after).*value `([0-9]+)`.*/\1 id=\2/' | sort | uniq -c
#   39 after id=9
#    7 before id=9
grep -c "assignment 9\b\|assignment 9," app.log   # → 0  (assignment 9 를 정상 처리한 기록 없음)
```

**C5 — H3 (a): 이 날 로그의 기동 · 프로필 기록**

```bash
grep -n "Starting ItemBankApplication\|No active profile\|profile is active\|profiles are active" app.log
# 1245: … Starting ItemBankApplication v1.4.2 using Java 21.0.4 with PID 1 …
# 1246: … No active profile set, falling back to 1 default profile: "default"
```

**C6 — H3 (b): 접속 대상 DB 기록**

```bash
grep -n -i "jdbc:\|HikariPool\|itembank-pool - \|database" app.log
# 1243 · 1244 (Shutdown), 1258 · 1259 · 1260 (Starting / Added connection org.mariadb.jdbc.Connection@4c1e7a3b / Start completed)
# → JDBC URL · DB 이름이 찍힌 줄 없음
```

**C7 — H4 (a): 재시도 묶음을 1건으로 합친 뒤의 500 비율**

같은 IP · 같은 URL 요청의 간격이 60초 이내이면 한 묶음으로 본다. 재기동 시작(13:41:48) 전후로 나눈다.

```bash
awk '
$7 ~ /redistribute/ {
  split($4,t,":"); s=t[2]*3600+t[3]*60+t[4]; k=$1" "$7
  per = (s < 13*3600+41*60+48) ? "pre" : "post"
  if (k in last && s-last[k] <= 60) { n[bid[k]]++; st[bid[k]]=$9 }
  else { bid[k]=++B; n[B]=1; st[B]=$9; pp[B]=per; first[B]=$9 }
  last[k]=s; req[per]++; if($9==500) r500[per]++
}
END {
  for (b=1;b<=B;b++){ grp[pp[b]]++; if(first[b]==500) g500[pp[b]]++; if(st[b]==500) gend500[pp[b]]++; if(n[b]>1) multi[pp[b]]++ }
  for (p in req) printf "%s 요청=%d 500=%d (%.1f%%) | 묶음=%d 첫요청500 묶음=%d (%.1f%%) 마지막도500=%d 2회이상묶음=%d\n",
    p, req[p], r500[p], 100*r500[p]/req[p], grp[p], g500[p], 100*g500[p]/grp[p], gend500[p], multi[p]
}' nginx-access.log
# pre  요청=103 500=6  (5.8%)  | 묶음=93 첫요청500 묶음=2  (2.2%)  마지막도500=2  2회이상묶음=8
# post 요청=49  500=39 (79.6%) | 묶음=24 첫요청500 묶음=14 (58.3%) 마지막도500=14 2회이상묶음=13

awk '$7 ~ /redistribute/ {print $1}' nginx-access.log | sort | uniq -c
#  125 교사단말A
#   27 교사단말B
```

H4 (b) · (c) 는 (a) 가 성립해 기각된 뒤라 판정에 쓰지 않았다. 참고로 배포 후 dist 42 · 43 의 `reason=` 은 5종류(`학생D 결석으로 재배포` 19, `null` 7, `학생A 요청 — 제출 화면 오류` 7, `학생D 보충 제출 기회` 3, `보강 수업 후 재배포` 3)다.

**C8 — H5 (a): 502 전수와 앞뒤 status**

```bash
awk '$9==502 {print NR, $4}' nginx-access.log
# 16865 13:41:51 / 16866 13:41:52 / 16867 · 16868 · 16869 13:41:53 / 16870 13:41:55 / 16871 13:41:58 / 16872 13:41:59
awk 'NR>=16860 && NR<=16874 {print NR, $4, $9}' nginx-access.log
# 16862 ~ 16864 13:41:50 200 → 16865 ~ 16872 502 (8건 연속) → 16873 13:42:01 200
```

**C9 — H5 (b): 대기 호스트 · upstream 흔적**

```bash
grep -c "API-2\|api-2" nginx-access.log app.log   # → app.log:0, nginx-access.log:0
```

**판정과 별개로 기록하는 관찰(의심 동작 후보, 판정하지 않음)**: 배포 후 dist 42 · 43 의 `redistributed …` 로그는 39건이고 500 도 39건이다. 즉 500 을 받은 요청도 재배포 처리 로그는 남겼다. 재배포는 `@Transactional` 메서드 `DistributionService.redistribute` 안에서 처리되고, 실패는 그 뒤 컨트롤러의 `listByClass` 호출(`DistributionController.java:40`)에서 났다. 그렇다면 클라이언트가 실패로 받은 재배포가 실제로는 DB 에 반영되었을 수 있다. 확인하려면 DB 의 `distribution.redistributed` · 갱신 시각을 조회해야 한다.

### 3.5 판정

3.2 에서 유지된 H1 · H5 를 `deploy-history.md` 의 변경 항목과 `modern/api` 코드 · 설정에 대조했다. 판단 불가였던 H2 · H3 도 코드 · 설정으로 볼 수 있는 부분만 함께 대조했다. 코드는 실행하지 않았다(테스트 · 벤치마크 없음). 설정값은 파일에서 읽은 값만 적었다.

**전제**: 저장소 코드가 배포된 v1.4.2 이미지와 같다고 보고 대조했다. 근거는 `modern/api/build.gradle:8` 의 `version = '1.4.2'` 와 `DistributionController.java:39` 의 v1.4.2 주석이다. 이미지와 같은지 직접 확인하지는 못했다(3.4 C3).

#### 3.5.1 로그 단서 → 코드 연결 고리

로그에 찍힌 클래스 · 메서드에서 출발해 배포 변경 항목까지 거꾸로 따라갔다.

| 순서 | 로그 단서 | 코드 위치 | 코드 인용 / 읽은 값 |
|---|---|---|---|
| 1 | `deploy:7` · `deploy:16` 변경 지점 "`DistributionController.redistribute` 에서 `listByClass(classId)` 호출 추가" | `modern/api/src/main/java/com/example/assignment/DistributionController.java:38` ~ `40` | 38 `distributionService.redistribute(id, reason);` → 39 `// v1.4.2 — 재배포 응답에 학급 배포 이력(history)을 함께 돌려주도록 변경` → 40 `distributionService.listByClass(redistributed.classId());` |
| 2 | 스택 `DistributionController.redistribute(DistributionController.java:40)` (`app:1294`) | 위 40번 줄 | 로그의 줄번호와 코드의 줄번호가 일치한다 |
| 3 | 스택 `DistributionService.listByClass(DistributionService.java:38)` | `DistributionService.java:36` ~ `40` | 36 `@Transactional(readOnly = true)` · 38 `distributionRepository.findByClassRoomIdOrderByDistributedAtAsc(classId).stream()` · 39 `.map(DistributionResponse::from)` |
| 4 | 스택 `$Proxy141.findByClassRoomIdOrderByDistributedAtAsc` | `DistributionRepository.java:13` ~ `14` | `@EntityGraph(attributePaths = {"assignment", "classRoom"})` · `List<Distribution> findByClassRoomIdOrderByDistributedAtAsc(Integer classRoomId);` → **학급의 모든 배포 행**을 읽으면서 각 행의 `assignment` 를 함께 가져온다 |
| 5 | `Caused by: FetchNotFoundException: Entity Assignment … value 9 does not exist` | `Distribution.java:23` ~ `25` | `@ManyToOne(fetch = FetchType.LAZY, optional = false)` · `@JoinColumn(name = "assignment_id", nullable = false)` → `assignment_id = 9` 인데 `assignment` 에 id 9 가 없는 행을 만나면 이 예외가 난다 |
| 6 | `ERROR … unhandled exception on …` → nginx 500 | `common/GlobalExceptionHandler.java:53` ~ `56` | `@ExceptionHandler(Exception.class)` → `log.error("unhandled exception on {}", …)` → `HttpStatus.INTERNAL_SERVER_ERROR` |
| 7 | 배포 **전** 500 의 스택 `loadOrThrow(DistributionService.java:65)` (`app:205`) | `DistributionService.java:64` ~ `66` · `DistributionRepository.java:10` ~ `11` | `findWithDetailsById(id)` 도 같은 `@EntityGraph(attributePaths = {"assignment", "classRoom"})` 다 → 재배포 **대상 건 자체**(dist 41)가 깨진 참조일 때만 실패한다 |
| 8 | `deploy:17` "`./gradlew test` 통과 (24 tests)" | `src/test/java/com/example/assignment/DistributionServiceTest.java:47` ~ `78` | `redistribute` 테스트 3개(정상 · 마감 409 · 없는 배포 404)만 있다. `listByClass` 와 `DistributionController` 를 검증하는 테스트는 `src/test/java/com/example/assignment/` 에 없다 |

정리하면 다음과 같다. 배포 전(7번)에는 깨진 참조를 가진 **그 배포 건을 직접 요청할 때만** 실패했다. v1.4.2 이후(1 ~ 5번)에는 **같은 학급의 아무 배포나 재배포해도** 깨진 행을 함께 읽게 되었다. 이렇게 보면 3.1 의 관찰 두 가지와 맞는다. 하나는 배포 후 실패가 class 1 의 dist 42 · 43 에 몰린 점, 다른 하나는 class 2 · 3 은 계속 200 이었던 점이다.

#### 3.5.2 가설별 코드 · 설정 대조 결과

| 가설 | 3.2 판정 | 코드 · 설정 대조 | 근거 (파일:줄번호, 인용) |
|---|---|---|---|
| H1 코드 변경 | 유지 | **지지한다** | 3.5.1 의 1 ~ 6번 연결 고리가 끊김 없이 이어진다. 변경 지점(`deploy:16`), 로그 스택(`app:1294`), 코드 줄(`DistributionController.java:40`)이 서로 일치한다. 7번이 배포 전과 배포 후의 실패 범위 차이를 설명한다. 8번에 따르면 이 경로는 배포 전 테스트로 검증되지 않았다 |
| H5 재기동 502 | 유지 | **관련 코드를 찾지 못했다** | 저장소에 nginx 설정(`upstream` · `proxy_pass`)이 없다(`grep -rl` 0건). 문서 근거는 있다: `deploy:3` "대기 호스트 `api-2` … 평소 정지", `deploy:18` "단일 호스트 재기동(무중단 아님). 예상 중단 10초 안팎". 애플리케이션 쪽 `application.yml` 에는 `server.shutdown` 키가 없다(0건) |
| H2 고아 데이터 | 판단 불가 | **관련 코드를 찾지 못했다** (운영 DB 상태는 코드로 알 수 없다) | 스키마 원본에는 `db/mariadb/init/01-schema.sql:101` `CONSTRAINT fk_distribution_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)` 가 있다. 이 FK 가 운영 DB 에도 있다면 고아 행은 생길 수 없다. 그런데 3.5.1 의 5번 예외는 고아 행이 있어야 난다. 따라서 운영 DB 스키마가 원본과 다를 가능성이 있다. 확인되지 않았다 |
| H3 프로필 · 설정 | 판단 불가 | **반박한다** (확인할 수 있는 범위 안에서) | `src/main/resources/` 에는 `application.yml` 한 파일뿐이다. 프로필별 파일(`application-<프로필>.yml`)이 없으므로 저장소 설정만으로는 "default 로 떠서 다른 datasource 를 썼다"는 경로가 없다. datasource 는 `application.yml:5` `url: [SECRET]` 하나다. 다만 운영 실행 인자 · 환경 변수는 저장소에 없어서 확인하지 못했다 |

#### 3.5.3 최종 판정

**채택: H1** — v1.4.2 가 `DistributionController.redistribute` 에 추가한 `listByClass(classId)` 호출(`DistributionController.java:40`) 때문에 재배포 한 건마다 같은 학급의 모든 배포 행과 그 과제를 읽게 되었다. 그 결과 class 1 에 있는 깨진 과제 참조(assignment id `9`)를 매번 밟아 500 이 났다.

**확신 수준: 높음** — 이유:
- 배포 변경 항목(`deploy:16`), 로그 스택(39/39건이 `:40` 경유, 3.4 C1), 코드 줄번호가 서로 일치한다.
- 실패한 학급은 class 1 뿐이고 같은 시간대의 다른 학급은 200 이었다. 이 패턴은 쿼리 범위가 "학급 전체"로 넓어졌다는 코드(`DistributionRepository.java:14`)와 맞는다.
- 배포 전과 배포 후 예외의 종류와 id 가 같고 호출 경로만 다르다(3.4 C4, 3.5.1 의 7번). 배포 전에 있던 문제가 배포로 노출 범위가 넓어졌다고 보면 앞뒤가 맞는다.
- 확신을 낮추는 점은 두 가지다. 배포 이미지와 저장소 코드가 같은지 직접 확인하지 못했다(C3). 또 class 1 에 assignment 9 를 가리키는 행이 실제로 있는지 DB 로 확인하지 못했다. 이 둘은 원인의 방향을 바꾸지는 않지만, 확인되기 전까지 "확정"이라고 부르지 않는다.

**기각 · 제외한 가설과 사유**

| 가설 | 처리 | 사유 |
|---|---|---|
| H2 고아 데이터 | **기각하지 않음 — 채택 가설의 선행 조건으로 남김** | H1 이 성립하려면 깨진 참조 행이 있어야 한다. H2 는 H1 과 경쟁하는 가설이 아니라 H1 의 전제다. 다만 운영 DB 를 보기 전까지는 판단할 수 없다. 스키마 원본에 FK 가 있는데(`01-schema.sql:101`) 고아 행이 생긴 경위도 별도 조사 대상이다 |
| H3 프로필 · 설정 | **기각** (확신 중간) | ① 재기동 전 인스턴스에서도 같은 예외 · 같은 id `9` 가 났다(3.4 C4). 재기동 때 설정이 바뀌지 않았어도 예외가 난다. ② 저장소에는 프로필별 설정 파일이 없고 datasource 가 하나뿐이다(`application.yml:5`). 확신이 중간인 이유: 운영 실행 인자 · 환경 변수를 보지 못했다. 또 아래 "남은 의문" 1번처럼 운영 설정이 저장소 설정과 다를 수 있다는 단서가 있다 |
| H4 재시도 증폭 | **기각** (확신 높음) | 재시도 묶음을 한 건으로 합쳐도 500 비율이 약 26배였다(3.4 C7). 코드에서도 클라이언트 재시도와 무관하게 class 1 재배포는 매번 같은 경로로 실패한다(3.5.1 의 1 ~ 5번) |
| H5 재기동 502 | **채택 대상에서 제외 — 별개 사건으로 유지** | H5 는 구간 A(502 8건, 9초)만 설명하고 구간 B(500 39건)와 관계가 없다. 로그(3.4 C8)와 `deploy:18` 의 "무중단 아님"이 서로 맞는다. 다만 nginx 설정 · error 로그가 없어 코드 대조는 하지 못했다. 이 사고의 주 원인 판정에는 넣지 않고, 배포 방식 개선 항목으로 따로 다룬다 |

#### 3.5.4 남은 의문 · 의심 동작 (판정에 쓰지 않음)

1. **운영 설정과 저장소 설정의 불일치 단서**: `app:1240` 에는 `GracefulShutdown : Commencing graceful shutdown` 이 찍혀 있다. 그런데 `application.yml` 에는 `server.shutdown` 키가 없다(0건). Spring Boot 버전은 `build.gradle:3` 의 `3.3.13` 이다. 이 버전의 기본 종료 방식이 즉시 종료(immediate)라면, 운영에는 저장소 밖에서 설정이 들어가고 있다는 뜻이다. 이 버전의 기본값은 파일이 아니라 프레임워크 문서로 확인해야 하므로 여기서는 단정하지 않는다. 그렇다면 H3 기각의 근거 ② 가 약해진다.
2. **500 인데 재배포는 반영되었을 가능성 (코드로 확인)**: `DistributionService.redistribute` 는 자체 `@Transactional`(`DistributionService.java:49`)이고, `listByClass` 는 별도의 `@Transactional(readOnly = true)`(`:36`)다. `DistributionController` 에는 `@Transactional` 이 없다(0건). 따라서 38번 줄의 재배포가 커밋된 **뒤에** 40번 줄에서 실패한다. 3.4 에서 본 "재배포 로그 39건 = 500 39건"과 맞는다. 클라이언트가 실패로 받은 재배포 39건이 DB 에는 반영되었을 수 있다(`distribution.redistributed` · `distributed_at` 갱신). 데이터 확인이 필요하다.
3. **테스트 공백**: v1.4.2 의 변경 지점인 `listByClass` 와 컨트롤러 응답 형식 변경을 덮는 테스트가 없다(3.5.1 의 8번). `deploy:17` 의 "24 tests 통과"는 이 변경을 검증하지 않았다.

## 4. 원인

3.5.3 에서 채택한 H1 을 H2 와 함께 인과 순서로 쓴다. 문장 번호(D · B · S)는 5절의 근거 표와 1:1 로 대응한다.

### 4.1 직접 원인 — 500 (구간 B)

- **D1.** 13:40:05 에 v1.4.2 배포가 시작되었고, 13:42:00 에 새 인스턴스가 기동을 마쳤다.
- **D2.** v1.4.2 는 `DistributionController.redistribute` 에서 재배포가 끝난 뒤 `listByClass(classId)` 를 한 번 더 호출한다.
- **D3.** `listByClass` 는 재배포 대상 1건이 아니라 **그 학급의 모든 배포 행**을 `assignment` 와 함께 읽는다.
- **D4.** 학급 1 에는 없는 assignment 9 를 가리키는 배포 행이 있다(dist 41 로 추정). 그래서 학급 1 의 어느 배포를 재배포하든 이 행을 읽는 순간 `FetchNotFoundException` 이 난다. 재배포 대상인 dist 42 의 과제는 4 인데 예외에 찍힌 id 는 9 다. 깨진 행이 재배포 대상이 아니라는 뜻이다.
- **D5.** 이 예외는 `GlobalExceptionHandler` 의 catch-all 에서 500 으로 바뀌었다. 13:46:38 ~ 14:34:45 에 dist 42 · 43 재배포 39건이 모두 500 이었다. 같은 시간대에 학급 2 · 3 재배포는 200 이었다.

### 4.2 배경 원인 — 직접 원인을 가능하게 하거나 키운 조건

- **B1. 깨진 참조가 배포 전부터 방치되었다.** 09:52 ~ 11:18 에 dist 41 을 요청하다 500 이 7건 났고, 예외도 같은 id 9 였다. 이 시점에 감지 · 대응한 기록은 없다.
- **B2. FK 가 있으면 생길 수 없는 데이터다.** 스키마 원본에는 `distribution.assignment_id` FK 가 있다. 운영 DB 스키마가 원본과 다를 가능성이 있지만 확인하지 못했다.
- **B3. 변경 경로를 검증하지 않았다.** `listByClass` 와 컨트롤러를 덮는 테스트가 없었다. 스테이징 확인은 깨진 참조가 없는 시드 데이터에서 3건을 수동으로 본 것뿐이다.
- **B4. 쓰기를 커밋한 뒤 조회해서 응답을 만든다.** 재배포는 자체 트랜잭션에서 먼저 커밋되고, 그 뒤 조회가 실패하면 성공한 쓰기가 500 으로 보고된다. 클라이언트(교사단말A)는 같은 배포를 되풀이해 요청했다.
- **B5. 수업 시간에 배포했다.** 이전 배포 3건은 모두 18시대였지만 v1.4.2 는 "수업 중 반영 요청"으로 13:40 에 나갔다.
- **B6. 경보가 없었다.** 500 은 첫 발생 뒤 48분 동안 이어졌다. 그 사이 롤백 기록은 없다.

### 4.3 별개 사건 — 502 (구간 A)

- **S1.** v1.4.2 배포는 단일 호스트(API-1) 재기동이었다. 13:41:48 종료 시작부터 13:42:00 기동 완료까지 받을 서버가 없었고, 이 구간에 nginx 가 502 8건을 돌려주었다. 대기 호스트(API-2)는 평소 정지 상태다.

## 5. 근거 로그 줄

nginx 발췌의 사용자 에이전트 · referer 는 지웠다. 코드 줄번호는 저장소 HEAD 기준이다(3.5 전제 참고).

| 문장 | 파일:줄번호 | 발췌 |
|---|---|---|
| D1 | `deploy:7` | `\| v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \|` |
| D1 | `app:1264` | `13:42:00.304 … Started ItemBankApplication in 4.921 seconds` |
| D2 | `deploy:16` | `` `DistributionController.redistribute` 에서 재배포 후 `DistributionService.listByClass(classId)` 호출 추가 `` |
| D2 | `DistributionController.java:38` · `:40` | `distributionService.redistribute(id, reason);` → `distributionService.listByClass(redistributed.classId());` |
| D2 | `app:1294` | `at com.example.assignment.DistributionController.redistribute(DistributionController.java:40)` |
| D3 | `DistributionService.java:38` | `distributionRepository.findByClassRoomIdOrderByDistributedAtAsc(classId).stream()` |
| D3 | `DistributionRepository.java:13` ~ `14` | `@EntityGraph(attributePaths = {"assignment", "classRoom"})` · `List<Distribution> findByClassRoomIdOrderByDistributedAtAsc(Integer classRoomId);` |
| D4 | `app:1274` | `redistributed distribution 42 (assignment 4, class 1)` — 대상의 과제는 4 |
| D4 | `app:1304` | ``Caused by: org.hibernate.FetchNotFoundException: Entity `com.example.assignment.Assignment` with identifier value `9` does not exist`` |
| D4 | `Distribution.java:23` ~ `24` | `@ManyToOne(fetch = FetchType.LAZY, optional = false)` · `@JoinColumn(name = "assignment_id", nullable = false)` |
| D5 | `GlobalExceptionHandler.java:53` ~ `56` | `@ExceptionHandler(Exception.class)` … `build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류", request)` |
| D5 | `app:1275` · `nginx:17155` | `13:46:38.113 ERROR … unhandled exception on /api/distributions/42/redistribute` · `교사단말A - - [17/Sep/2026:13:46:38 +0900] "POST /api/distributions/42/redistribute HTTP/1.1" 500` |
| D5 | `app:2887` · `nginx:20084` | `14:34:45.439 ERROR … unhandled exception on /api/distributions/43/redistribute` (마지막 500) |
| D5 | `app:1393` · `nginx:17353` | `redistributed distribution 8 (assignment 6, class 2)` · `"POST /api/distributions/8/redistribute HTTP/1.1" 200` |
| B1 | `app:194` ~ `195` · `nginx:3150` | `09:52:17.335 ERROR … unhandled exception on /api/distributions/41/redistribute` · ``Entity `…Assignment` with identifier value `9` does not exist`` |
| B1 | `app:205` | `at com.example.assignment.DistributionService.loadOrThrow(DistributionService.java:65)` — 배포 전에는 대상 건 자체를 읽을 때만 실패 |
| B2 | `db/mariadb/init/01-schema.sql:101` | `CONSTRAINT fk_distribution_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)` |
| B3 | `deploy:17` | `` 테스트: `./gradlew test` 통과 (24 tests). 스테이징 DB(시드 데이터)에서 재배포 3건 수동 확인. `` |
| B3 | `DistributionServiceTest.java:47` ~ `78` | `redistribute` 테스트 3개만 있음. `listByClass` · 컨트롤러 테스트 없음(3.5.1 의 8번) |
| B4 | `DistributionService.java:49` · `:36` | 재배포 `@Transactional` 과 이력 조회 `@Transactional(readOnly = true)` 가 서로 다른 트랜잭션 |
| B4 | `app:1274` → `app:1275` | `13:46:38.107 … redistributed distribution 42` → `13:46:38.113 ERROR …` (커밋 로그 6ms 뒤 실패) |
| B4 | `nginx:17155` · `17170` · `17181` | 교사단말A 가 dist 42 를 13:46:38 · 13:46:58 · 13:47:08 에 연속 요청, 모두 500 (3.4 C7: 배포 후 24묶음 중 13묶음이 2회 이상) |
| B5 | `deploy:7` ~ `10` · `deploy:14` | 배포 시작 13:40:05 / 18:05:12 / 18:10:03 / 18:02:47 · `수업 중 반영 요청` |
| B6 | `nginx:17155` → `nginx:20084` · `nginx:20365` | 첫 500 13:46:38 → 마지막 500 14:34:45 → 로그 끝 14:39:58. `deploy-history.md` 에 v1.4.2 롤백 기록 없음 |
| S1 | `deploy:3` · `deploy:18` | `` 배포 대상 호스트: `api-1` (API-1), 대기 호스트 `api-2` (API-2, 평소 정지) `` · `단일 호스트 재기동(무중단 아님). 예상 중단 10초 안팎.` |
| S1 | `app:1240` · `app:1264` | `13:41:48.214 … Commencing graceful shutdown` · `13:42:00.304 … Started ItemBankApplication` |
| S1 | `nginx:16865` ~ `16872` · `16873` | `13:41:51 "GET /api/units/M5-2/items?student=학생C HTTP/1.1" 502` … `13:41:59 … 502` → `13:42:01 "GET /api/units HTTP/1.1" 200` |

## 6. 수정안 (지금 당장)

사람이 실행할 조치와 코드 변경 제안이다. 이 문서 작업에서는 아무것도 실행하거나 수정하지 않았다.

### 6.1 운영 조치 (사람이 실행)

1. **롤백**: `v1.4.1` 이미지로 재기동한다(`deploy:22`, 약 3분). 단, 교사 화면이 이미 `{distribution, history[]}` 응답을 기대하도록 바뀌었다면 롤백 뒤 화면이 깨질 수 있다. 실행 전에 `modern/web` 의 배포 버전부터 확인한다.
2. **깨진 행 확인 (읽기 전용 계정)**:
   `SELECT d.id, d.class_id, d.assignment_id FROM distribution d LEFT JOIN assignment a ON a.id = d.assignment_id WHERE a.id IS NULL;`
   assignment 9 를 복구할지, 해당 배포 행을 정리할지는 데이터 담당 승인을 받아 정한다. 쓰기 작업이므로 이 문서에서는 제안하지 않는다.
3. **500 을 받은 재배포가 실제로 반영되었는지 확인**: `SELECT id, redistributed, distributed_at FROM distribution WHERE id IN (42, 43);`. 반영되었다면 교사에게 "실패로 보였지만 처리됨"을 안내한다.

### 6.2 코드 수정 (롤백 대신 v1.4.3 으로 고칠 경우)

| 파일 | 무엇을 | 어떻게 |
|---|---|---|
| `modern/api/src/main/java/com/example/assignment/DistributionRepository.java:13` ~ `14` | 학급 이력 조회가 깨진 참조 행 하나 때문에 통째로 실패한다 | 파생 쿼리 + `@EntityGraph` 를 `@Query("select d from Distribution d join fetch d.assignment join fetch d.classRoom where d.classRoom.id = :classId order by d.distributedAt")` 로 바꾼다. inner join 이라 참조가 깨진 행은 결과에서 빠지고, 나머지 이력은 정상으로 돌려준다 |
| `modern/api/src/main/java/com/example/assignment/DistributionController.java:38` ~ `41` · `DistributionService.java` | 쓰기 커밋 뒤 별도 조회가 실패하면 성공한 쓰기가 500 으로 보고된다 | `DistributionService` 에 `@Transactional` 메서드 `redistributeWithHistory(id, reason)` 를 두고, 컨트롤러는 그 메서드 하나만 호출한다. 조회가 실패하면 재배포도 롤백되어 "500 인데 반영됨" 상태가 없어진다. 추가되는 일은 같은 트랜잭션 안의 조회 1회뿐이라 Hikari 풀(max 5)에 주는 부담은 작다 |
| `modern/api/src/test/java/com/example/assignment/` | 변경 경로를 덮는 테스트가 없다 | ① `DistributionServiceTest` 에 "같은 학급에 참조가 깨진 행이 있어도 재배포 · 이력 조회가 성공하고 그 행은 빠진다" 케이스를 추가한다(H2 에서 `SET REFERENTIAL_INTEGRITY FALSE` 로 고아 행을 넣는다). ② `DistributionController` 응답 형식 테스트를 추가한다 |

설정 파일은 이번 원인을 고치는 데 바꿀 필요가 없다.

## 7. 재발 방지 (구조적 장치)

| # | 막으려는 것 | 장치 |
|---|---|---|
| 1 | 운영 DB 의 참조 무결성이 원본 스키마와 어긋나는 것 (B2) | 운영 스키마와 `db/mariadb/init/01-schema.sql` 의 FK 를 비교하는 드리프트 점검을 배포 파이프라인 사전 단계에 넣는다. 고아 행 탐지 쿼리는 매일 정기 작업으로 돌린다 |
| 2 | 변경된 응답 경로가 검증 없이 나가는 것 (B3) | CLAUDE.md 의 "새 Controller · Service public 메서드에는 대응 테스트" 규칙을 CI 에서 강제한다(변경된 클래스에 대응하는 테스트 클래스가 없으면 실패). 공용 테스트 데이터에 참조가 깨진 행을 일부러 넣어 둔다 |
| 3 | 시드 데이터라 보이지 않는 결함 (B3) | 스테이징 검증을 운영 마스킹 스냅샷 데이터로 한다. 시드만으로 수동 확인한 결과는 배포 승인 근거로 쓰지 않는다 |
| 4 | 쓰기 성공을 실패로 보고하는 구조 (B4) | `templates/CLAUDE.spring.md` 금지 사항과 리뷰 체크리스트에 "쓰기 API 의 응답 조회는 쓰기와 같은 서비스 메서드 · 같은 트랜잭션에서 한다. 컨트롤러가 서비스를 연달아 호출해 응답을 만들지 않는다"를 추가한다 |
| 5 | 재기동 중 502 (S1) | `api-2` 를 활용해 블루/그린으로 전환하고, nginx upstream 헬스체크를 통과한 뒤에만 트래픽을 넘긴다 |
| 6 | 수업 중 장애 (B5) | 수업 시간(예: 09:00 ~ 17:00) 배포를 금지한다. 긴급 예외는 승인자와 롤백 담당이 대기하는 조건으로만 허용한다 |
| 7 | 장애가 길어지는 것 (B6) | 배포 뒤 15분을 파이프라인의 "관찰 단계"로 둔다. 이 단계에서 8절 경보가 울리면 자동 롤백하고, 롤백 여부와 시각을 `deploy-history.md` 에 남긴다 |

## 8. 모니터링 항목

경보 시각은 이번 로그(`app.log`, `nginx-access.log`)에 임계값을 그대로 적용해 계산했다. 배포 전(09:00 ~ 13:40) 구간에서 오경보가 나는지도 함께 확인했다.

| # | 지표 | 임계값 | 이번에 울렸을 시각 | 배포 전 구간 오경보 |
|---|---|---|---|---|
| M1 | 엔티티 참조 오류 로그(`FetchNotFoundException` · `EntityNotFoundException` · `JpaObjectRetrievalFailureException`) | 1건 이상 → 즉시 경보 | **09:52:17** (`app:194`). 배포보다 3시간 48분 먼저 깨진 데이터를 알 수 있었다 | 해당 없음(이 경보가 바로 데이터 결함 신호다) |
| M2 | 엔드포인트별 nginx 500 건수 | 5분 창 5건 이상 | **13:50:13** (5번째 500, `nginx:17366`) | 없음. 배포 전 5분 창 최대값은 3건(09:52, 11:18) |
| M3 | 배포 뒤 30분 동안 엔드포인트별 5xx 비율 | 비율 20% 이상 그리고 5xx 3건 이상 | **13:47:08** (재배포 4건 중 500 3건 = 75%, `nginx:17181`) | 해당 없음(배포 뒤에만 평가). 배포 전 재배포 500 비율은 5.8% |
| M4 | nginx 502 (upstream 연결 실패) | 1분 창 3건 이상 | **13:41:53** (3번째 502, `nginx:16867`) | 없음(배포 전 502 0건) |
| M5 | 같은 클라이언트 · 같은 URL 에서 60초 안에 5xx 2회 이상 (사용자가 다시 시도하고 있다는 신호) | 2회 이상 | **13:46:58** (dist 42 두 번째 500, `nginx:17170`) | 09:52:30 (dist 41). M1 과 같은 데이터 결함이라 맞는 경보다 |

실제로는 14:34:45 까지 500 이 이어졌고, 대응 기록이 없다. M3 · M5 가 있었다면 첫 500 뒤 1분 안에 경보가 울렸을 것이다. M1 이 있었다면 배포 전에 데이터를 고쳐 이 장애 자체가 나지 않았을 수 있다.

## 9. 확인하지 못한 것

### 9.1 로그 · 자료가 없어 판단하지 못한 부분

| 항목 | 없는 자료 |
|---|---|
| 500 이 언제 끝났는지, 롤백했는지 | 14:40 이후 로그, v1.4.2 이후 배포 이력 |
| 깨진 행이 실제로 있는지 · 어느 dist 인지, assignment 9 가 언제 · 어떻게 지워졌는지 | 운영 DB 조회 결과, DB 감사 로그(binlog) |
| 운영 DB 에 FK 가 있는지, 원본 스키마와 다른지 | 운영 `SHOW CREATE TABLE distribution` |
| 500 을 받은 39건이 DB 에 반영되었는지 | `distribution.redistributed` · `distributed_at` 조회 결과 |
| 배포된 이미지가 저장소 코드와 같은지 | v1.4.2 태그 · 이미지 digest |
| 운영 실행 설정(활성 프로필, graceful shutdown 이 어디서 설정되었는지) | 실행 인자 · 환경 변수, v1.4.1 기동 로그 |
| 502 의 upstream 오류 사유, 그 시각 API-2 의 상태 | nginx error 로그, upstream 설정 |
| 교사단말A 가 무엇인지, 반복 요청이 사람인지 자동 재시도인지 | 단말 · 프록시 정보, 클라이언트 재시도 정책 |
| 사용자 영향 범위(교사 · 학생 수), 응답 지연이 있었는지 | 사용자 식별 로그, nginx 응답 시간 필드 |

### 9.2 다음을 위해 남겨야 할 로그

- **nginx access 로그**: `$request_time` · `$upstream_status` · `$upstream_addr` · `$request_id` 를 추가한다. error 로그도 access 로그와 같은 기간 보관한다.
- **app 로그**: 모든 줄에 요청 ID 를 남긴다(MDC, nginx `$request_id` 와 같은 값). 기동할 때 이미지 digest · git SHA · 활성 프로필 · datasource 호스트와 DB 이름을 남긴다. 자격 증명은 빼고 남긴다.
- **예외 로그**: 깨진 참조 id · distribution id · class id 를 구조화 필드로 남긴다. 쓰기 트랜잭션 커밋이 끝났다는 사실도 함께 남긴다.
- **배포 이력**: 이미지 digest, 관찰 단계 결과, 롤백 여부와 시각을 남긴다.
- **DB**: `assignment` 삭제 감사 로그를 남긴다.
- **남기면 안 되는 것**: `deploy:20` 의 배포 명령에 `Authorization: [TOKEN]` 가 평문으로 남아 있다. 이 토큰은 폐기하고 교체한다. 파이프라인 로그를 복사할 때 헤더 값을 가린다.

---

마스킹 적용: IP 21건 · 이메일 1건 · 학생 식별자 8건 · 토큰 1건 · 비밀 1건
