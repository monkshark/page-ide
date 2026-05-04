# PAGE IDE Docs

> English: [README_en.md](https://monkshark.github.io/PAGE_IDE/#README_en.md)

> 다언어 데스크톱 IDE — Pair · Atlas · Glass · Echo

PAGE IDE의 공개 문서 진입점. 이 페이지는 목차 역할만 한다. 실제 내용은 각 항목을 따라간다.

뷰어는 `node build_viewer.js`로 `index.html`을 빌드한 뒤, 별도 서버 없이 브라우저에서 바로 열 수 있다.

---

## 목차

### 가이드
- [PAGE 개요](https://monkshark.github.io/PAGE_IDE/#guides/overview.md) — 핵심 가치 네 가지 (Pair · Atlas · Glass · Echo)와 만들지 않을 것
- [아키텍처](https://monkshark.github.io/PAGE_IDE/#guides/architecture.md) — 모듈 구조와 의존 방향, 기술 스택 결정

### core 모듈
- [PageIdentity](https://monkshark.github.io/PAGE_IDE/#modules/core/page_identity.md) — 앱 이름/약자/태그라인 한곳 모음

### editor 모듈
- [TextBuffer](https://monkshark.github.io/PAGE_IDE/#modules/editor/text_buffer.md) — `StringBuilder` 래퍼 + 라인/컬럼 좌표
- [TabBook](https://monkshark.github.io/PAGE_IDE/#modules/editor/tab_book.md) — 탭 묶음과 활성 인덱스
- [EditHistory](https://monkshark.github.io/PAGE_IDE/#modules/editor/edit_history.md) — undo/redo 스택
- [FileDocument](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_document.md) — 본문/저장본/dirty 한 묶음
- [FileKind](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_kind.md) — 텍스트/이미지/SVG 분류
- [FileTree](https://monkshark.github.io/PAGE_IDE/#modules/editor/file_tree.md) — 사이드바 트리 노드 빌더
- [SearchState](https://monkshark.github.io/PAGE_IDE/#modules/editor/search_state.md) — 검색어 + 매치 + active 인덱스
- [Replace](https://monkshark.github.io/PAGE_IDE/#modules/editor/replace.md) — 검색 매치 치환
- [AutoClose](https://monkshark.github.io/PAGE_IDE/#modules/editor/auto_close.md) — 자동 짝괄호/따옴표
- [BracketMatch](https://monkshark.github.io/PAGE_IDE/#modules/editor/bracket_match.md) — 매칭 괄호 위치 찾기
- [Indent](https://monkshark.github.io/PAGE_IDE/#modules/editor/indent.md) — Tab/Enter 자동 들여쓰기
- [LineMove](https://monkshark.github.io/PAGE_IDE/#modules/editor/line_move.md) — 줄 이동/복제
- [MarkdownFence](https://monkshark.github.io/PAGE_IDE/#modules/editor/markdown_fence.md) — 코드 펜스 안 판정
- [SyntaxLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/syntax_lexer.md) — 어휘 분석기 인터페이스
- [SyntaxLexers](https://monkshark.github.io/PAGE_IDE/#modules/editor/syntax_lexers.md) — 확장자 → 렉서 라우팅
- [JvmLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/jvm_lexer.md) — Java/Kotlin 공통 스캐너
- [KotlinLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/kotlin_lexer.md) — Kotlin 키워드 셋
- [JavaLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/java_lexer.md) — Java 키워드 셋
- [JsonLexer](https://monkshark.github.io/PAGE_IDE/#modules/editor/json_lexer.md) — JSON 토크나이저

### ui 모듈
- [GlassTheme](https://monkshark.github.io/PAGE_IDE/#modules/ui/glass_theme.md) — Glass 디자인 토큰 / `MaterialTheme` 적용
- [EditorFonts](https://monkshark.github.io/PAGE_IDE/#modules/ui/editor_fonts.md) — 본문 폰트 패밀리
- [SyntaxColors](https://monkshark.github.io/PAGE_IDE/#modules/ui/syntax_colors.md) — 신택스 팔레트

### app 모듈
- [Main](https://monkshark.github.io/PAGE_IDE/#modules/app/main.md) — `application` 진입점 + 윈도우 상태
- [EditorPanel](https://monkshark.github.io/PAGE_IDE/#modules/app/editor_panel.md) — 본문 + 거터 + 하이라이트
- [TabBar](https://monkshark.github.io/PAGE_IDE/#modules/app/tab_bar.md) — 상단 탭 줄 + 드래그
- [SearchBar](https://monkshark.github.io/PAGE_IDE/#modules/app/search_bar.md) — 검색/치환 바
- [FileTreePanel](https://monkshark.github.io/PAGE_IDE/#modules/app/file_tree_panel.md) — 좌측 사이드바
- [LineNumberGutter](https://monkshark.github.io/PAGE_IDE/#modules/app/line_number_gutter.md) — 라인 번호 거터
- [PreviewPanel](https://monkshark.github.io/PAGE_IDE/#modules/app/preview_panel.md) — 이미지/SVG 미리보기
- [UnsavedChangesDialog](https://monkshark.github.io/PAGE_IDE/#modules/app/unsaved_changes_dialog.md) — 저장 확인 다이얼로그
- [PendingClose](https://monkshark.github.io/PAGE_IDE/#modules/app/pending_close.md) — 닫기 요청 식별
- [FileDialogs](https://monkshark.github.io/PAGE_IDE/#modules/app/file_dialogs.md) — `JFileChooser` 래퍼

> `pair`, `atlas`, `echo`, `language`, `runtime`, `git`, `workspace` 모듈은 단계별로 추가된다.

### 기능
> Pair / Atlas / Glass / Echo 기능 문서는 각 단계가 끝날 때 추가된다.

---

## 외부 링크

- GitHub: <https://github.com/Monkshark/PAGE_IDE>
- 개발기 시리즈: <https://monkshark.github.io/categories/page-개발기/>
