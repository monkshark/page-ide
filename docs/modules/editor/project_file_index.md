# ProjectFileIndex

> `page/editor/src/main/kotlin/page/editor/ProjectFileIndex.kt` — 프로젝트 루트 평탄화 워커

`Ctrl+P` 가 열릴 때 `rootDir` 아래의 파일을 한 번에 훑어 플랫 리스트로 반환. 무거운 디렉터리는 스킵해서 5000 개 한계 안에서 끝낸다

> English: [project_file_index_en.md](https://monkshark.github.io/page-ide/#modules/editor/project_file_index_en.md)

---

## `walk`

```kotlin
fun walk(root: Path, limit: Int = 5000): List<IndexedFile>
```

DFS 로 진행 (`ArrayDeque` 스택). 디렉터리는 SKIP 셋과 hidden (`.` 시작) 검사를 통과한 것만 들어가고, 파일은 `IndexedFile(path, root.relativize(child))` 로 모은다. 마지막에 `relative` 소문자 기준으로 정렬

| 스킵 디렉터리 |
|---|
| `.git`, `.idea`, `.gradle`, `.kotlin`, `.vscode` |
| `build`, `out`, `node_modules`, `target`, `dist`, `.cache`, `bin` |

`limit` 은 디폴트 5000. 그 이상은 잘리는데, 빠른 열기는 사람 눈으로 훑는 도구이지 풀 검색 도구가 아니라 그게 합리적

---

## `IndexedFile`

```kotlin
data class IndexedFile(val path: Path, val relative: String)
```

`path` 는 절대 (열 때 그대로 `openInTab` 으로 넘김), `relative` 는 항상 `/` 정규화 (Windows 의 `\` 도 `/` 로 치환). 매처는 `relative` 만 본다

---

## 사용처

| 위치 | 역할 |
|---|---|
| `page.app.Main.openQuickOpen` | `Ctrl+P` 누르는 순간 한 번 호출, 결과를 `quickOpenIndex` 스테이트에 캐싱 |

다이얼로그가 닫힌 동안엔 인덱스를 들고 있지 않는다 — 다음에 열 때 다시 워크. 파일이 자주 추가/삭제되어도 매번 새로 잡으니 stale 안 됨

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
