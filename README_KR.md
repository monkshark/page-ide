<div align="center">

<img src="assets/logo_transparent.svg" alt="PAGE" width="128" />

# PAGE

[![CI](https://github.com/monkshark/page-ide/actions/workflows/ci.yml/badge.svg)](https://github.com/monkshark/page-ide/actions/workflows/ci.yml)
[![설계 문서](https://img.shields.io/badge/design%20docs-monkshark.github.io-4C5AB8)](https://monkshark.github.io/page-ide/)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-pre--alpha-orange)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**Kotlin 과 Compose Multiplatform 으로 만든 다언어 데스크톱 IDE.**

[설계 문서](https://monkshark.github.io/page-ide/) · [English](README.md)

<!-- 데모: 녹화 후 아래 주석을 풀고 assets/demo.gif 로 교체
<img src="assets/demo.gif" alt="PAGE 사용 화면" width="880" />
-->

</div>

24개 언어를 편집하고 탐색하고 실행합니다. 언어 서버든 툴체인이든 없으면 PAGE 가 알아서 받아
설치합니다. 이름은 만들려는 네 가지를 그대로 딴 것입니다. **P**air 는 AI 동반자, **A**tlas 는
코드 그래프, **G**lass 는 디자인 시스템, **E**cho 는 작업의 시간축입니다.

> pre-alpha 입니다. 매일 자기 자신을 만드는 데 쓰고 있지만 아직 남에게 권할 물건은 아닙니다.

## 기능

- 자동완성, 진단, 정의 이동, 이름 변경 - 24개 언어에 언어 서버를 붙입니다.
- 프로젝트 전체 심볼 인덱스가 있어 서버가 없어도 사용처와 죽은 코드, 안 쓰는 import 를 찾습니다
- 13개 언어는 파일 하나만 있으면 바로 실행됩니다. 증분 빌드 캐시와 내장 터미널이 함께 붙습니다
- JDK, Node, Python, Go, Rust, .NET, Dart, Swift, Clang, MSVC 를 IDE 안에서 설치합니다
- 코드 액션에 diff 가 함께 뜹니다. 여러 파일을 건드리는 편집은 검토 패널에서 확인하고 적용합니다
- import·호출·모듈 그래프를 빌드 메타데이터가 아니라 tree-sitter 로 그립니다
- 파일 종속성을 방향별로 펼치고 접으며, 탐색 기록과 코드 미리보기로 순환·잠재적 영향 범위를 확인합니다
- 분할 뷰, 폴딩, inlay hint, 테마 9종. 단축키는 전부 액션 테이블 하나에서 나옵니다

<sub>Kotlin · Java · Python · TypeScript/JavaScript · Go · Rust · C/C++ · Swift · Dart/Flutter ·
Ruby · PHP · C# · Vue · Svelte · Bash · JSON · YAML · HTML · CSS · SQL · Markdown · Dockerfile</sub>

## 빌드

릴리스는 아직 없습니다. JDK 는 Gradle 이 알아서 받으니 Git 만 있으면 됩니다.

```bash
git clone https://github.com/monkshark/page-ide.git
cd page-ide
./gradlew :page:app:run
```

`./gradlew build` 는 전체 모듈을 컴파일하고 테스트를 돌립니다.

## 설계 문서

쓸 사람이 아직 없으니 사용 설명서도 없습니다. 지금 있는 문서는 PAGE 를 고치는 쪽을 향해 있습니다.

- [개요](https://monkshark.github.io/page-ide/#guides/overview.md) — 무엇을 위해 만드는지, 무엇은 만들지 않는지
- [아키텍처](https://monkshark.github.io/page-ide/#guides/architecture.md) — 모듈 16개와 단방향 의존, 스택을 그렇게 고른 이유
- [시작하기](https://monkshark.github.io/page-ide/#guides/getting_started.md) — PAGE 를 직접 띄우고 디버깅하는 법
- [내부 구조](https://monkshark.github.io/page-ide/#README_kr.md) — 모듈별 설계 노트

## 기여

`main` 에는 직접 넣을 수 없습니다. 브런치를 파서 PR 을 열고 CI 가 통과 되면 그때 머지합니다.
실제로 돌아가는 코드에는 테스트를 함께 넣어 주세요. 모듈을 새로 만들 생각이라면
[아키텍처 가이드](https://monkshark.github.io/page-ide/#guides/architecture.md)를 먼저 읽어 보시길 권합니다.

## 라이선스

[MIT](LICENSE)

## 연락

[GitHub Issues](https://github.com/monkshark/page-ide/issues) · justinchoo0814@gmail.com · IG@[void___main](https://www.instagram.com/void___main)
