// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 item-bank 레거시 기본 주소).
//   이관 후에는 PATH_ALIASES 에 따라 /search.php 가 404 이면 /api/items/search 로 다시 요청한다.
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 입력값 경고(<ul id="warnings">)는 정규화 대상이 아니라서 비교하지 않는다.
// - 기대값을 손으로 적지 않는다. 베이스라인: npm run baseline -- item-bank
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

async function search(params) {
  return fetchNormalized(MODULE, PATH, params);
}

describe('item-bank · 문항 검색(search.php)', () => {
  // 정상 입력
  it('01 조건 없음 — 파라미터 누락 시 기본 난이도 조건(level < 5) · 기본 정렬(level DESC, id ASC)', async () => {
    expect(await search({})).toMatchSnapshot();
  });

  it('02 키워드 — q=분수 (제목 · 지문 LIKE)', async () => {
    expect(await search({ q: '분수' })).toMatchSnapshot();
  });

  it('03 단원 — unit=M5-1 (단원 코드 정확히 일치)', async () => {
    expect(await search({ unit: 'M5-1' })).toMatchSnapshot();
  });

  it('04 태그 — tag=계산 (태그 이름 정확히 일치)', async () => {
    expect(await search({ tag: '계산' })).toMatchSnapshot();
  });

  // 경계값
  it('05 난이도 하한 — level=1', async () => {
    expect(await search({ level: 1 })).toMatchSnapshot();
  });

  it('06 난이도 상한 — level=5 (명시하면 5 포함, 빈값이면 제외되는 규칙과 대비)', async () => {
    expect(await search({ level: 5 })).toMatchSnapshot();
  });

  it('07 난이도 범위 바로 아래 — level=0', async () => {
    expect(await search({ level: 0 })).toMatchSnapshot();
  });

  it('08 난이도 범위 바로 위 — level=6', async () => {
    expect(await search({ level: 6 })).toMatchSnapshot();
  });

  it('09 페이지 경계 — page=2 (한 페이지 20건, 기본 조건 결과가 정확히 20건)', async () => {
    expect(await search({ page: 2 })).toMatchSnapshot();
  });

  // 빈 값 · 누락
  it('10 난이도 빈값 — level= (빈값이면 level < 5 로 검색)', async () => {
    expect(await search({ level: '' })).toMatchSnapshot();
  });

  it('11 키워드 공백만 — q="   " (trim 뒤 조건 없음)', async () => {
    expect(await search({ q: '   ' })).toMatchSnapshot();
  });

  // 이상한 값
  it('12 난이도 음수 — level=-1', async () => {
    expect(await search({ level: -1 })).toMatchSnapshot();
  });

  it('13 아주 긴 키워드 — q 101자 (100자까지만 사용)', async () => {
    expect(await search({ q: '분'.repeat(101) })).toMatchSnapshot();
  });

  it('14 없는 단원 코드 — unit=Z9-9', async () => {
    expect(await search({ unit: 'Z9-9' })).toMatchSnapshot();
  });

  // 정렬
  it('15 정렬 — sort=level&dir=asc (level ASC, id ASC)', async () => {
    expect(await search({ sort: 'level', dir: 'asc' })).toMatchSnapshot();
  });

  it('16 단원 코드 소문자 — unit=m5-1 (입력값 그대로 조회)', async () => {
    expect(await search({ unit: 'm5-1' })).toMatchSnapshot();
  });

  it('17 난이도에 숫자 뒤 문자 — level=3abc (정수 변환 규칙)', async () => {
    expect(await search({ level: '3abc' })).toMatchSnapshot();
  });
});
