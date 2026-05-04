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

### 모듈
- [core](https://monkshark.github.io/PAGE_IDE/#modules/core.md) — 공용 도메인 타입과 상수 (`PageIdentity`)
- [editor](https://monkshark.github.io/PAGE_IDE/#modules/editor.md) — 텍스트 버퍼, 편집 동작, 신택스 하이라이팅, 탭 모델 (UI 의존 없는 순수 로직)
- [ui](https://monkshark.github.io/PAGE_IDE/#modules/ui.md) — Glass 디자인 토큰, 폰트, 신택스 팔레트
- [app](https://monkshark.github.io/PAGE_IDE/#modules/app.md) — 조립 계층 / 진입점, Compose 패널, 단축키, 다이얼로그

> `pair`, `atlas`, `echo`, `language`, `runtime`, `git`, `workspace` 모듈은 단계별로 추가된다.

### 기능
> Pair / Atlas / Glass / Echo 기능 문서는 각 단계가 끝날 때 추가된다.

---

## 외부 링크

- GitHub: <https://github.com/Monkshark/PAGE_IDE>
- 개발기 시리즈: <https://monkshark.github.io/categories/page-개발기/>
