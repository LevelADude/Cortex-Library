# Cortex Library 🧠📚

Cortex Library is a modern Android reading platform for scientific books and research papers.  
It aggregates open-access PDFs from public libraries and presents them in a fast, distraction-free interface optimized for deep study and knowledge organization.

---

## ✨ Features

- 📄 Read scientific books & research papers (PDF)
- 🌐 Fetch open-access content from public libraries
- ⚡ Fast and smooth PDF rendering
- 🔍 Full-text search and quick navigation
- 🔖 Bookmarks and reading progress tracking
- 📥 Offline access for downloaded papers
- 🧠 Study-focused, minimal UI

---

## 🎯 Goal

Built for students, researchers, and lifelong learners who need a focused environment to read, organize, and retain scientific knowledge.

---

## 🏗️ Tech Stack (planned)

- Kotlin
- Jetpack Compose
- Material 3
- Room (local database)
- PDF rendering engine (TBD)
- Open Library / public API integration

---

## 🗺️ Roadmap

### MVP
- [ ] Local PDF reader
- [ ] Library view
- [ ] Bookmarks
- [ ] Reading progress tracking

### v1.0
- [ ] Open-access API integration
- [ ] Advanced search
- [ ] Highlights & notes
- [ ] Cloud sync (optional)

### Future
- [ ] Citation export
- [ ] AI-powered paper summaries
- [ ] Study mode for medical students

---

## 📦 Package Name

`com.cortex.library`

---

## 🤝 Contributing

Contributions, ideas, and feature requests are welcome.  
Please open an issue to discuss changes before submitting a PR.

---

## 📜 License

MIT License (recommended)

## Build locally

```bash
./gradlew :android:assembleDebug
```

### Regenerate Gradle wrapper JAR

If `gradle-wrapper.jar` is missing in this repository policy, regenerate wrapper artifacts locally:

```bash
gradle wrapper
```

## Run tests

```bash
./gradlew :android:testDebugUnitTest
```
