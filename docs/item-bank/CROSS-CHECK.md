# item-bank-php 비즈니스 규칙 (코드 기준 교차 검증용)

- 대상: `legacy/item-bank-php` (외부 라이브러리 폴더 `vendor/` 는 읽지 않음, `docs/` 도 참고하지 않음)
- 읽은 파일: `search.php`, `register.php`, `units.php`, `index.php`, `inc/db.php`, `inc/layout.php`
- 원칙: 코드에 있는 규칙만 적는다. 주석과 코드가 다르면 코드를 따르고, 그 차이를 규칙 아래에 적는다.
- 줄번호는 읽은 시점의 파일 기준이다.

## 1. 문항 검색 — 조건 조합 (`search.php`)

### CX-01 키워드는 제목 또는 지문에 부분 일치한다
- 규칙: 키워드(`q`)가 있으면 앞뒤 공백을 제거한 뒤 `title` 또는 `stem` 에 `LIKE %키워드%` 로 찾는다.
- 근거: `legacy/item-bank-php/search.php:85`, `:97-98`
```php
$q = trim($q);
$like = '%' . $q . '%';
$where .= " AND (title LIKE ? OR stem LIKE ?)";
```

### CX-02 키워드는 100자까지만 쓴다
- 규칙: 키워드가 100자를 넘으면 100자로 잘라서 검색하고 경고만 남긴다(오류로 막지 않는다).
- 근거: `search.php:86-89`
```php
if (mb_strlen($q, 'UTF-8') > 100) {
    $q = mb_substr($q, 0, 100, 'UTF-8');
    $warnings[] = '키워드가 너무 길어 100자까지만 사용했습니다.';
```

### CX-03 단원은 코드와 정확히 일치해야 하며, 형식이 틀려도 그대로 조회한다
- 규칙: 단원(`unit`)은 `unit_code = ?` 로 비교한다. 형식 · 대소문자가 다르거나 등록되지 않은 코드여도 조건을 빼지 않고 그대로 조회한다(경고만 표시).
- 근거: `search.php:111-114`, `:118-120`, `:141-142`
```php
if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
    // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
$where .= " AND unit_code = ?";
```

### CX-04 난이도가 비어 있으면 난이도 5 문항이 제외된다 (주석과 코드 불일치)
- 규칙: 난이도를 지정하지 않으면 `level < 5` 조건이 붙어 난이도 5 문항은 결과에 나오지 않는다.
- 근거: `search.php:213-216`
```php
// 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
if ($level == '') {
    $where .= " AND level < 5";
```
- 주석은 "1~5 모두 포함"이라고 하지만 코드는 5를 뺀다. 이 문서는 코드를 따른다. 난이도 5 문항은 `level=5` 를 명시해야만 조회된다(CX-05).

### CX-05 난이도 1~5 를 지정하면 그 값과 같은 문항만 나온다
- 규칙: 난이도가 `1`~`5` 한 자리 숫자면 `level = ?` 로 비교한다.
- 근거: `search.php:217-221`
```php
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
    $values[] = (int)$level;
```

### CX-06 범위 밖 난이도는 정수로 바꿔 그대로 비교한다
- 규칙: `1`~`5` 가 아닌 값은 경고를 남기고 `(int)` 로 변환해 `level = ?` 로 비교한다. 숫자가 아닌 문자열은 0이 되어 0건이 된다.
- 근거: `search.php:223-229`
```php
$warnings[] = '난이도는 1~5 사이여야 합니다.';
$where .= " AND level = ?";
$values[] = (int)$level;
```

### CX-07 태그는 이름이 정확히 같은 것만 걸리고, 50자까지만 쓴다
- 규칙: 태그(`tag`)는 `item_tag` · `tag` 를 조인해 `t.name = ?` 로 비교한다(부분 일치 없음). 50자를 넘으면 잘라서 쓰고, 등록되지 않은 태그여도 조회는 그대로 한다.
- 근거: `search.php:240-247`, `:258-260`
```php
if (mb_strlen($tag, 'UTF-8') > 50) {
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```

### CX-08 여러 조건은 모두 AND 로 결합하고, 같은 이름 파라미터가 여러 개면 첫 값만 쓴다
- 규칙: 키워드 · 단원 · 난이도 · 태그는 `WHERE 1=1` 뒤에 각각 `AND` 로 이어 붙는다(OR 조합 없음). 배열로 들어온 파라미터는 첫 값만 사용한다.
- 근거: `search.php:44`, `:55`, `:98`, `:118`, `:244`
```php
$where   = " WHERE 1=1";
$q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
```

## 2. 목록 — 제외 조건과 정렬 (`search.php`, `units.php`)

### CX-09 검색은 `v_item_public` 뷰만 조회한다
- 규칙: 문항 검색과 건수 집계는 테이블이 아니라 공개 문항 뷰 `v_item_public` 을 대상으로 한다. 뷰가 어떤 문항을 거르는지는 이 폴더의 코드에 없다(한계 참조).
- 근거: `search.php:518-526`
```php
// SQL 조립 — 공개 문항 뷰(v_item_public)를 기준으로 조회한다
$from   = " FROM v_item_public";
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```

### CX-10 기본 정렬은 난이도 내림차순, 같으면 ID 오름차순이다
- 규칙: `sort` 를 비우면 `level DESC, id ASC`. 알 수 없는 `sort` 값도 경고 후 같은 기본 정렬로 되돌린다.
- 근거: `search.php:317-319`, `:322-325`
```php
case '':
    // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-11 지정 정렬은 기준별로 보조 정렬 키가 고정되어 있다
- 규칙: `sort` 는 `id` · `title` · `unit` · `level` · `created` 만 인식한다. 모든 기준에 `id ASC` 가 동점 처리로 붙으며(`id` 자체 정렬 제외), `unit` 은 단원 코드 다음에 `level DESC` 가 붙는다.
- 근거: `search.php:285-299`, `:309-315`
```php
$orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
$orderBy = " ORDER BY title ASC, id ASC";
$orderBy = " ORDER BY created_at DESC, id ASC";
```

### CX-12 정렬 방향을 안 주면 `level` · `created` 는 내림차순, 나머지는 오름차순이다
- 규칙: `dir` 이 `asc`/`desc` 가 아니면 비운다. `level` 은 `asc` 일 때만 오름차순이고 `created` 도 같다(그 밖에는 내림차순). `id` · `title` · `unit` 은 `desc` 일 때만 내림차순이다.
- 근거: `search.php:268-273`, `:301-306`, `:328-331`
```php
if ($dir !== 'asc' && $dir !== 'desc') {
if ($dir === 'asc') { $orderBy = " ORDER BY level ASC, id ASC"; } else { ... level DESC, id ASC }
$dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
```

### CX-13 한 페이지는 20건이고 페이지 번호는 1~999 로 보정한다
- 규칙: `LIMIT 20 OFFSET (page-1)*20`. 숫자가 아니면 1페이지, 1 미만은 1, 999 초과는 999 로 바꾼다.
- 근거: `search.php:337-349`, `:523`
```php
if ($page > 999) { $page = 999;
$offset = ($page - 1) * 20;
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
```

### CX-14 단원 목록은 공개(`status='A'`) 문항만 세고, 학년 → 코드 순으로 정렬한다
- 규칙: 단원별 문항 수는 `status = 'A'` 만 센다(삭제 · 검수중 제외). 단원 자체는 문항이 0개여도 모두 나온다.
- 근거: `units.php:15-19`
```php
// 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
ORDER BY u.grade ASC, u.code ASC
```

## 3. 문항 등록 — 검증 (`register.php`)

### CX-15 제목은 앞뒤 공백을 뺀 뒤 5자 이상이어야 한다
- 규칙: 제목은 `trim` 후 글자 수(`mb_strlen`)가 5 미만이면 오류다. 안쪽 공백은 글자 수에 포함된다.
- 근거: `register.php:41`, `:48-51`
```php
$title  = isset($_POST['title'])  ? trim((string)$_POST['title'])  : '';
// 제목은 5자 이상 (공백 제외 아님, 앞뒤 공백만 제거)
if (mb_strlen($title, 'UTF-8') < 5) {
```

### CX-16 제목은 200자를 넘을 수 없다
- 규칙: 200자 초과는 오류이며, 검색 키워드(CX-02)와 달리 자르지 않고 저장을 막는다.
- 근거: `register.php:52-54`
```php
if (mb_strlen($title, 'UTF-8') > 200) {
    $errors[] = '제목은 200자를 넘을 수 없습니다.';
```

### CX-17 지문은 필수다
- 규칙: 지문은 `trim` 후 빈 문자열이면 오류다. 길이 상한 검증은 코드에 없다.
- 근거: `register.php:42`, `:56-58`
```php
$stem   = isset($_POST['stem'])   ? trim((string)$_POST['stem'])   : '';
if ($stem === '') {
```

### CX-18 단원은 등록된 단원의 `id` 중 하나여야 한다
- 규칙: 폼의 `unit_id` 가 `unit` 테이블에서 읽은 목록의 `id` 와 일치하지 않으면 오류다. (검색은 단원 **코드**, 등록은 단원 **id** 를 쓴다.)
- 근거: `register.php:27`, `:60-68`
```php
if ((string)$u['id'] === $unitId) { $unitOk = true; }
if (!$unitOk) { $errors[] = '단원을 선택해야 합니다.';
```

### CX-19 난이도는 1~5 의 한 자리 숫자여야 한다
- 규칙: `^[1-5]$` 에 맞지 않으면 오류다. 검색(CX-06)과 달리 범위 밖 값을 그대로 저장하지 않고 막는다.
- 근거: `register.php:70-72`
```php
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
```

### CX-20 태그는 선택 사항이고, 목록에 없는 태그 id 는 오류 없이 버린다
- 규칙: 태그를 하나도 안 골라도 등록된다. 등록된 `tag.id` 와 일치하는 값만 저장 대상이 되며, 나머지는 오류 없이 무시된다.
- 근거: `register.php:45`, `:74-81`
```php
foreach ($tags as $t) {
    if ((string)$t['id'] === (string)$tid) {
        $validTagIds[] = (int)$tid;
```

## 4. 문항 등록 — 저장 (`register.php`)

### CX-21 새 문항은 검수중(`'R'`) 상태로 저장된다
- 규칙: 등록 직후 `status` 는 항상 `'R'` 이다. 공개(`'A'`, CX-14 참조)로 바꾸는 코드는 이 폴더에 없다. 주석은 검색에 나오지 않는다고 하나, 그 효과는 `v_item_public` 정의(폴더 밖)에 달려 있다.
- 근거: `register.php:84`, `:92-94`
```php
// 등록 직후에는 검수중(R) 상태로 들어간다 → 검수 완료 전까지 검색 화면에 나오지 않는다.
"INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
```

### CX-22 문항 번호는 `MAX(id)+1` 로 직접 매긴다
- 규칙: 자동 증가를 쓰지 않고, 트랜잭션 안에서 `item` 을 `FOR UPDATE` 로 잠근 채 최대 id + 1 을 새 id 로 쓴다.
- 근거: `register.php:85-90`
```php
$conn->begin_transaction();
$res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
$newId = (int)$row['next_id'];
```

### CX-23 문항과 태그 연결은 한 트랜잭션이고, 하나라도 실패하면 모두 취소한다
- 규칙: 문항 INSERT 와 `item_tag` INSERT 는 함께 커밋되며, 예외가 나면 롤백하고 "저장 중 오류" 만 화면에 알린다.
- 근거: `register.php:99-101`, `:114`, `:119-122`
```php
throw new RuntimeException('태그 저장 실패');
$conn->commit();
} catch (Exception $e) { $conn->rollback();
```

## 한계 · 확인이 필요한 부분

1. **수정 기능은 없다.** `index.php:9-11` 의 메뉴는 검색 · 등록 · 단원 목록뿐이고, 등록 외에 문항을 고치는 화면(`edit.php` · `update.php` 등)은 읽은 범위에서 찾지 못했다. 그래서 "수정 시의 검증" 규칙은 적지 않았다.
2. **파일 목록 확인이 불완전하다.** 디렉터리 목록 명령(`find` · `ls`)이 이 세션에서 응답하지 않아, 위 6개 파일 외에 다른 PHP 파일이 있는지 목록으로 확인하지 못했다. `index.php` 의 메뉴 링크와 `require_once` 를 따라 읽은 범위다.
3. **공개 조건의 정의는 범위 밖이다.** CX-09 의 `v_item_public` 이 어떤 문항을 거르는지(예: `status = 'A'` 만 노출하는지)는 DB 초기화 스크립트에 있고, 이번 범위(`legacy/item-bank-php`)에 없어 규칙으로 확정하지 않았다. `units.php` 의 `status = 'A'`(CX-14)만 코드로 확인된다.
4. 이 폴더의 `README` 는 찾지 못했다(`README.md` 없음).
