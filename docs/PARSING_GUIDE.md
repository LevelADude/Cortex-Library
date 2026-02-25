# Extended Manual Parsing Guide (Monogatari)

This guide shows a **real, reproducible Manual Parsing setup** using one working extension from this repository’s extension pack.

## Example extension used

- **Site:** Rain of Snow Translations
- **Base URL:** `https://rainofsnow.com`
- **Reference source:** `extensions-dev/src/en/RainOfSnow.lua` from `extensions-dev.zip`
- **Why this one:** it uses straightforward HTML selectors and clean list/novel/chapter parsing.

---

## 1) Page map (what URL each parser target uses)

| Parser target in Monogatari | Example URL pattern | Notes |
|---|---|---|
| Listing / Browse | `https://rainofsnow.com/novels/page/1` | Extension builds this from order + page. |
| Latest listing | `https://rainofsnow.com/latest-release/page/1` | Same card structure as listing. |
| Search | `https://rainofsnow.com/?s=your+query` | Uses query string `s`. |
| Novel page | `https://rainofsnow.com/novel/...` | Contains metadata + chapter list. |
| Chapter page | `https://rainofsnow.com/...chapter...` | Passage content is inside one main container. |

---

## 2) Selector mapping table (UI field -> selector/attribute)

Use this as your copy/paste mapping when filling Manual Parsing fields.

### A. Listing cards (Browse/Latest/Search)

| Monogatari Manual Parsing field | Selector / Attribute | Purpose | Derived from extension |
|---|---|---|---|
| Item selector | `div.minbox` | Selects each novel card container. | `parseListing(): doc:select("div.minbox")` |
| Title selector | `a` + attribute `title` | Extracts novel title from anchor title attribute. | `title = a:attr("title")` |
| URL selector | `a` + attribute `href` | Extracts novel link. | `link = ... a:attr("href")` |
| Image selector | `img` + attribute `data-src` | Extracts card image URL. | `imageURL = ... selectFirst("img"):attr("data-src")` |

### B. Novel details page

| Monogatari field | Selector / Attribute | Purpose | Derived from extension |
|---|---|---|---|
| Novel title | `div.queen div.text > h2` (text) | Main novel title on detail page. | `title = content:selectFirst("div.text > h2"):text()` |
| Cover image | `div.queen img` + `data-src` | Novel cover image. | `imageURL = ... selectFirst("img"):attr("data-src")` |
| Description item selector | `div#synop p` | Gets all synopsis paragraphs. | `doc:selectFirst("div#synop"):select("p")` |
| Author | `div.queen ul.vbtcolor1 > li:nth-child(2) .vt2` (text) | Author line. | `child(1):selectFirst(".vt2")` |
| Genres | `div.queen ul.vbtcolor1 > li:nth-child(5) .vt2 a` (text list) | Genre tags. | `child(4):...select("a")` |

### C. Chapters list

| Monogatari field | Selector / Attribute | Purpose | Derived from extension |
|---|---|---|---|
| Chapter item selector | `#chapter ul.march1 li` | Each chapter row. | `...select("#chapter ul.march1"):select("li")` |
| Chapter title | `a` (text) | Chapter display title. | `title = v:selectFirst("a"):text()` |
| Chapter URL | `a` + `href` | Chapter link. | `link = ... a:attr("href")` |
| Chapter release text (optional) | `.july` (text) | Relative date/release label. | `release = v:selectFirst(".july"):text()` |

### D. Passage/content extraction

| Monogatari field | Selector | Purpose | Derived from extension |
|---|---|---|---|
| Chapter content selector | `.zoomdesc-cont` | Main chapter HTML container to render. | `chap = chap:selectFirst(".zoomdesc-cont")` |
| Optional injected heading | `li.menu-toc-current` (text) | Extension prepends this as `<h1>`. | `title = chap:selectFirst("li.menu-toc-current"):text()` |

---

## 3) Copy/paste starter values

> These are practical starter values for a manual parser profile based on the same site structure.

```text
Base URL: https://rainofsnow.com

Listing item selector: div.minbox
Listing title selector: a
Listing title attribute: title
Listing URL selector: a
Listing URL attribute: href
Listing image selector: img
Listing image attribute: data-src

Novel title selector: div.queen div.text > h2
Novel cover selector: div.queen img
Novel cover attribute: data-src
Novel description selector: div#synop p

Chapter list selector: #chapter ul.march1 li
Chapter title selector: a
Chapter URL selector: a
Chapter URL attribute: href

Passage/content selector: .zoomdesc-cont
```

---

## 4) How to verify selectors with DevTools (important)

1. Open the target page in desktop browser.
2. Press `F12` (or right click -> **Inspect**).
3. Open **Console**.
4. Test selector counts:
   - `document.querySelectorAll('div.minbox').length`
   - `document.querySelectorAll('#chapter ul.march1 li').length`
5. Click one matched element in Elements panel and verify title/link/image attributes.
6. Confirm image attribute source:
   - try `getAttribute('src')`
   - if blank/wrong, try `getAttribute('data-src')`.
7. Confirm URLs:
   - if URL starts with `/`, it is relative and must be joined with base URL.

---

## 5) Common mistakes and troubleshooting

### 0 items returned
- Selector is too specific for a different page template.
- You are testing selector on the wrong page type (listing selector on novel page, etc.).
- Site changed class names.

### Wrong selector scope
- Start broad (`div.minbox`) then narrow only when necessary.
- Avoid deep chains unless needed.

### `src` vs `data-src`
- Many sites lazy-load images.
- If covers are blank, switch image attribute from `src` to `data-src`.

### Relative URLs
- If extracted link is `/novel/abc`, prepend base URL (`https://rainofsnow.com`).

### Chapters missing from paginated lists
- Some novels split chapters across pages.
- This example extension follows extra page links; manual parsing may need per-site pagination support.

---

## 6) End-to-end walkthrough (homepage -> novel -> chapter)

1. Open listing page (`/novels/page/1`).
2. Confirm each novel card is captured by `div.minbox`.
3. Open one card’s `<a href>` and verify it lands on a novel page.
4. On novel page, validate:
   - title from `div.queen div.text > h2`
   - cover from `div.queen img[data-src]`
   - chapter rows from `#chapter ul.march1 li`
5. Open one chapter URL.
6. Validate passage content container `.zoomdesc-cont`.
7. If all steps pass, the parser profile is correctly mapped.

---

## 7) Screenshot placeholders (replace later)

![Screenshot 1 - Listing page with highlighted `div.minbox`](images/parsing-guide-01-listing-cards.png)

![Screenshot 2 - Inspecting title attribute on `<a title="...">`](images/parsing-guide-02-title-attr.png)

![Screenshot 3 - Verifying `data-src` for image](images/parsing-guide-03-image-data-src.png)

![Screenshot 4 - Novel page title selector `div.queen div.text > h2`](images/parsing-guide-04-novel-title.png)

![Screenshot 5 - Chapter list selector `#chapter ul.march1 li`](images/parsing-guide-05-chapter-list.png)

![Screenshot 6 - Chapter content selector `.zoomdesc-cont`](images/parsing-guide-06-passage-content.png)

![Screenshot 7 - Console selector count checks](images/parsing-guide-07-console-counts.png)
