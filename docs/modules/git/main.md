# Git

> `page:git` — 워크스페이스의 VCS 상태. `git status`를 읽어 파일별 변경 종류를 제공

에디터 트리와 Atlas 그래프는 "이 파일이 커밋 대비 바뀌었나"를 표시해야 한다. 그 판정의 단일 출처가 이 모듈이다. Git 저장소에 `git status`를 돌려 파일별 변경 종류를 뽑고, 결과를 짧게 캐시해 여러 화면이 같은 스냅샷을 공유한다.

> English: [main_en.md](https://monkshark.github.io/page-ide/#modules/git/main_en.md)

---

## 구성

모듈은 인터페이스 하나와 구현 하나로 얇다.

```mermaid
flowchart LR
    repo["git 저장소"] --> gsp["GitStatusProvider<br/>git status 실행·캐시"]
    gsp --> ui["파일 트리 · Atlas VcsOverlay"]
```

| 요소 | 역할 |
|---|---|
| `VcsStatusProvider` | 상태 조회 인터페이스 (`statuses()` · `refresh()`) |
| `GitStatusKind` | 변경 종류 enum (MODIFIED · ADDED · DELETED · RENAMED · UNTRACKED) |
| `GitStatusProvider` | `git status`를 실행하고 결과를 TTL 캐시로 보관하는 구현 |
| `parseGitStatus` | porcelain 출력을 파일 경로 → 종류 맵으로 파싱 |

---

## 상태 조회

`VcsStatusProvider`는 상태의 소비자와 공급자를 가른다.

```kotlin
interface VcsStatusProvider {
    fun statuses(): Map<Path, GitStatusKind>
    fun refresh()
}
```

`statuses()`는 워크스페이스 루트 기준의 절대 경로를 키로, 변경 종류를 값으로 돌려준다. 트리 행이나 그래프 노드는 자기 경로를 이 맵에서 찾아 배지를 그린다.

---

## GitStatusProvider — 실행과 캐시

구현체는 `git status --porcelain=v1 -z`를 서브프로세스로 돌린다. JGit 같은 라이브러리 대신 시스템 `git` 실행 파일을 직접 부르므로, 사용자가 쓰는 것과 같은 Git 설정·자격 증명·ignore 규칙을 그대로 따른다.

같은 상태를 화면 여러 곳이 동시에 물어보므로, 결과는 기본 30초(`ttlMs`) TTL 캐시에 담긴다. 캐시가 살아 있으면 `statuses()`는 프로세스를 다시 띄우지 않고 바로 돌려준다. 파일을 저장하거나 스테이지가 바뀌어 즉시 갱신이 필요하면 `refresh()`로 캐시를 비운다.

생성자는 `clock`과 `runGit`을 파라미터로 받아, 테스트에서 실제 프로세스 실행 없이 출력 문자열과 시간을 주입할 수 있다.

---

## parseGitStatus — porcelain 파싱

`-z` 플래그로 받은 출력은 NUL로 구분되고 각 항목이 두 글자 상태 코드로 시작한다. `parseGitStatus(output, root)`가 이를 순회하며 코드를 `GitStatusKind`로 매핑하고, 경로를 루트 기준 절대 경로로 정규화한다. 이름이 바뀐 항목(`R`)은 원본과 대상 두 경로가 함께 오므로 그 형식도 처리한다.

---

- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
