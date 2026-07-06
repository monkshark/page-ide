# 시작하기

> English: [getting_started_en.md](https://monkshark.github.io/page-ide/#guides/getting_started_en.md)

> 레포를 받아 데스크톱 IDE 를 빌드하고 실행하는 최소 경로.

PAGE 는 Gradle wrapper 로 빌드한다. JDK 는 Gradle toolchain 이 자동 프로비저닝하므로(Foojay), 래퍼 스크립트만으로 빌드·실행된다.

---

## 준비물

| 항목 | 비고 |
|---|---|
| Git | 레포 클론 |
| Gradle wrapper | 레포에 포함 (`gradlew` / `gradlew.bat`), 별도 설치 불필요 |
| JDK | toolchain 이 JDK 21 을 자동으로 내려받음. 미리 깔려 있으면 그대로 씀 |

버전은 레포에 고정돼 있다 — Kotlin 2.1.20, Compose Multiplatform 1.7.3, Gradle 8.14, JDK 21 toolchain.

---

## 클론

```bash
git clone https://github.com/monkshark/page-ide.git
cd page-ide
```

---

## 실행

데스크톱 IDE 를 바로 띄운다.

```bash
./gradlew :page:app:run
```

Windows PowerShell 에서는 `.\gradlew :page:app:run`, cmd 에서는 `gradlew :page:app:run` 을 쓴다.

진입점은 `page.app.MainKt` 이고, `app` 모듈이 나머지 모듈을 조립해 윈도우를 연다.

---

## 빌드 · 테스트

```bash
# 전체 모듈 빌드 + 테스트
./gradlew build

# 특정 모듈만 테스트
./gradlew :page:runtime:test
```

CI 게이트도 동일하게 `./gradlew build` 다 (ubuntu-latest + Temurin 21). 빌드가 통과해야 머지된다.

---

## 문서 뷰어 로컬 미리보기

이 문서 사이트는 `docs/` 의 마크다운을 하나의 자체 완결 `index.html` 로 묶어 만든다.

```bash
cd docs
node build_viewer.js
```

산출된 `docs/index.html` 은 서버 없이 브라우저에서 바로 열 수 있다. 정적 서버로 보려면:

```bash
python -m http.server 8090 --directory docs
```

멀티플랫폼 위젯 섬(Atlas overview 등)까지 로컬에서 다시 빌드하려면:

```bash
./gradlew :docs-viewer:wasmJsBrowserDevelopmentExecutableDistribution
```

---

## 워크플로우

- `main` 직접 푸시 금지 — 모든 변경은 feature 브랜치 → PR → CI → squash 머지.
- 실제 동작 코드를 다루는 기능에는 단위 테스트를 동반한다. 골격·스캐폴딩은 면제.

---

- [아키텍처 보기](https://monkshark.github.io/page-ide/#guides/architecture.md)
- [목차로 돌아가기](https://monkshark.github.io/page-ide/#README_kr.md)
