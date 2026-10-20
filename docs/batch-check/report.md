# 배치 점검 리포트
- 점검 범위: 2026-09-08 ~ 2026-09-17, 로그 폴더 `pipeline-samples/batch-logs`
- 점검 일시: 2026-10-13

| 날짜 | job | 점검 항목 | 근거 | 확인 필요 |
|---|---|---|---|---|
| 2026-09-09 | build_item_stats | 실행 여부 (START만 있고 SUCCESS 없음) | `2026-09-09.log:2` START(run_id=bis-20260910-0030-5192), 마지막 기록은 `2026-09-09.log:4` `status=RUNNING progress=25% rows=3109`. 이후 SUCCESS · 실패 · lock 해제 기록 없음. `datamart-counts.csv` 에 이날 build_item_stats 행 없음 | 프로세스(pid=40655)가 어떻게 끝났는지(호스트 로그 · 스케줄러 기록), `dm.item_stats_daily` 의 09-09분 적재 여부, 재처리 여부 |
| 2026-09-12 | export_grades | 지연 | `2026-09-12.log:10` START 01:00:01.170 → `2026-09-12.log:16` SUCCESS 01:16:04.368 = 963s / 평소 319s = 3.02배. 로그에 WARN 없음, 구간별 진행(25%마다 약 4분)이 고르게 느림(`2026-09-12.log:12`~`15`) | 그 시각 원천 `grade-db/grades.dbo.grade_summary` · S3 업로드 쪽 지연 기록 |
| 2026-09-15 | extract_submissions | 지연 | `2026-09-15.log:18` START 01:30:02.724 → `2026-09-15.log:20` SUCCESS 01:57:50.724 = 1668s / 평소 659s = 2.53배. 관련 기록: `2026-09-15.log:19` `WARN ... status=RUNNING rows=19204 note="source read slow: grade-db 10.20.2.41 replica lag 412s"` | grade-db 복제 지연이 생긴 원인과 시간대(DB 모니터링) |
| 2026-09-15 | load_datamart | 지연 | `2026-09-15.log:21` START 02:00:03.643 → `2026-09-15.log:40` SUCCESS 03:12:41.043 = 4357s / 평소 901s = 4.84배. 관련 기록: `2026-09-15.log:27` `WARN ... note="elapsed 40m exceeds p95 17m" rows=94111`, `2026-09-15.log:28` `WARN [sla-monitor@10.20.7.12] job=load_datamart date=2026-09-15 status=NOT_FINISHED sla=02:45 ...` | 02:00~03:12 구간 DB 부하 · 락 대기 기록, 앞선 extract_submissions 지연과의 관계 |
| 2026-09-15 | load_datamart | 중복 실행 (SUCCESS 2회) | `2026-09-15.log:40` SUCCESS run_id=ldm-20260916-0200-edf5 rows=184532 / `2026-09-15.log:42` SUCCESS run_id=ldm-20260916-0258-8963 rows=184532. 두 번째 실행 기록: `2026-09-15.log:31` `WARN ... lock=held_by run_id=ldm-20260916-0200-edf5 pid=46735 since=02:00:03 action=ignored reason="--force"`, `2026-09-15.log:32` `status=START ... trigger=manual operator=ops.park@example.com lock=bypassed`, `2026-09-15.log:43` `WARN ... lock=release_skipped reason="not owner (bypassed)"` | `dm.daily_submission_fact` 09-15분에 같은 데이터가 두 번 들어갔는지, 수동 실행 경위(운영자 확인), 첫 실행이 lock 을 정상 해제했는지 |
| 2026-09-15 | load_datamart | 적재 건수 (+99.91%) | `datamart-counts.csv L39 369064` / 직전 7일(09-08~09-14) 평균 184617.4 = +99.91%. CSV 값은 두 SUCCESS 의 rows 합(184532 × 2 = 369064)과 같다. 관련 기록: `2026-09-15.log:45` `WARN ... input_rows=369064 note="input_rows differs from extract_submissions rows=41573 x avg fan-out"`, `2026-09-15.log:47` `WARN [dq-check@10.20.7.12] table=dm.daily_submission_fact date=2026-09-15 rows=369064 avg_7d=184617 deviation=+99% notify=data-oncall@example.com` | 실제 테이블 행 수 · 중복 키 여부, 이 값을 입력으로 쓴 build_report_cache(09-15) 결과 영향 |
| 2026-09-17 | build_item_stats | 적재 건수 (−41.51%) | `datamart-counts.csv L46 7284` / 직전 7일(09-10~09-16) 평균 12453.1 = −41.51%. 로그도 같은 값: `2026-09-17.log:8` `status=SUCCESS rows=7284 elapsed=202s`. 소요 시간은 평소 수준(202.8s / 평소 216.5s = 0.94배), WARN 없음 | 원천 `itembank.item_response` 의 09-17 응답 건수, 입력 쪽 누락 여부 |

## 적용 기준 메모
- **소요 시간**: 모든 job 을 START→SUCCESS 시각 차로 계산했다(`build_report_cache` 에는 `elapsed=` 가 없음). "평소"는 그 job 의 **다른 날** 소요 시간 중앙값. 09-15 load_datamart 는 실행이 2건이라 각각 따로 계산했다(두 번째 수동 실행은 1008.9s / 평소 901.1s = 1.12배로 기준 안).
- **적재 건수**: 직전 7일은 달력 기준(D-7 ~ D-1)이고, 그 안에 있는 날만으로 평균을 냈다.
  - 7일치가 안 찬 구간: 모든 job 의 09-09 ~ 09-14(로그 시작일이 09-08). 09-08 은 비교할 직전 데이터가 없어 건수 점검에서 빠졌다.
  - build_item_stats 는 09-09 행이 CSV 에 없어서 09-15 · 09-16 도 6일 평균이다.
  - **앞선 이상값이 평균에 섞인 날**: load_datamart 09-16(L44 184895) · 09-17(L49 184551) 평균에 09-15 의 369064 가 들어 있다. 섞인 평균(211253.0 · 211493.4) 기준 −12.48% · −12.74%, 09-15 를 뺀 6일 평균(184951.2 · 185231.7) 기준 −0.03% · −0.37%. 어느 쪽이든 ±30% 안이라 이상으로 잡지 않았다.
- **기준 밖이지만 참고할 사항**
  - load_datamart 가 **매일**(09-08~09-17) 같은 WARN 을 남긴다: `step=validate skipped=1 student_id=STU-1013 reason="class_id is NULL"` (예: `2026-09-08.log:22`, `2026-09-17.log:22`). 이번 점검 기준의 이상은 아니지만 계속 반복되는 데이터 품질 기록이다.
  - build_report_cache 09-15 는 입력 369064건을 받았지만 결과 rows=1271(`datamart-counts.csv L40`)로 평소 수준(−0.20%)이다. 중복 입력이 결과에 영향을 줬는지는 확인 필요.
  - 09-15 load_datamart 첫 실행은 SLA(02:45)를 넘겨 03:12 에 끝났다(`2026-09-15.log:28`, `:40`).
- 계산 스크립트: scratchpad 의 `check.py`(실행 여부 · 중복 · 소요 시간 · 건수), `counts_cal.py`(달력 기준 7일 평균, 이상값 제외 평균).
