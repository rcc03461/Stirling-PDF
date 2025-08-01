<p align="center"><img src="https://raw.githubusercontent.com/Stirling-Tools/Stirling-PDF/main/docs/stirling.png" width="80"></p>
<h1 align="center">Stirling-PDF</h1>

[![Docker Pulls](https://img.shields.io/docker/pulls/frooodle/s-pdf)](https://hub.docker.com/r/frooodle/s-pdf)
[![Discord](https://img.shields.io/discord/1068636748814483718?label=Discord)](https://discord.gg/HYmhKj45pU)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/Stirling-Tools/Stirling-PDF/badge)](https://scorecard.dev/viewer/?uri=github.com/Stirling-Tools/Stirling-PDF)
[![GitHub Repo stars](https://img.shields.io/github/stars/stirling-tools/stirling-pdf?style=social)](https://github.com/Stirling-Tools/stirling-pdf)

<a href="https://www.producthunt.com/posts/stirling-pdf?embed=true&utm_source=badge-featured&utm_medium=badge&utm_souce=badge-stirling&#0045;pdf" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=641239&theme=light" alt="Stirling&#0032;PDF - Open&#0032;source&#0032;locally&#0032;hosted&#0032;web&#0032;PDF&#0032;editor | Product Hunt" style="width: 250px; height: 54px;" width="250" height="54" /></a>
[![Deploy to DO](https://www.deploytodo.com/do-btn-blue.svg)](https://cloud.digitalocean.com/apps/new?repo=https://github.com/Stirling-Tools/Stirling-PDF/tree/digitalOcean&refcode=c3210994b1af)

[Stirling-PDF](https://www.stirlingpdf.com) is a robust, locally hosted web-based PDF manipulation tool using Docker. It enables you to carry out various operations on PDF files, including splitting, merging, converting, reorganizing, adding images, rotating, compressing, and more. This locally hosted web application has evolved to encompass a comprehensive set of features, addressing all your PDF requirements.

All files and PDFs exist either exclusively on the client side, reside in server memory only during task execution, or temporarily reside in a file solely for the execution of the task. Any file downloaded by the user will have been deleted from the server by that point.

Homepage: [https://stirlingpdf.com](https://stirlingpdf.com)

All documentation available at [https://docs.stirlingpdf.com/](https://docs.stirlingpdf.com/)

![stirling-home](images/stirling-home.jpg)

## Features

- 50+ PDF Operations
- Parallel file processing and downloads
- Dark mode support
- Custom download options
- Custom 'Pipelines' to run multiple features in a automated queue
- API for integration with external scripts
- Optional Login and Authentication support (see [here](https://docs.stirlingpdf.com/Advanced%20Configuration/System%20and%20Security) for documentation)
- Database Backup and Import (see [here](https://docs.stirlingpdf.com/Advanced%20Configuration/DATABASE) for documentation)
- Enterprise features like SSO (see [here](https://docs.stirlingpdf.com/Advanced%20Configuration/Single%20Sign-On%20Configuration) for documentation)

## PDF Features

### Page Operations

- View and modify PDFs - View multi-page PDFs with custom viewing, sorting, and searching. Plus, on-page edit features like annotating, drawing, and adding text and images. (Using PDF.js with Joxit and Liberation fonts)
- Full interactive GUI for merging/splitting/rotating/moving PDFs and their pages
- Merge multiple PDFs into a single resultant file
- Split PDFs into multiple files at specified page numbers or extract all pages as individual files
- Reorganize PDF pages into different orders
- Rotate PDFs in 90-degree increments
- Remove pages
- Multi-page layout (format PDFs into a multi-paged page)
- Scale page contents size by set percentage
- Adjust contrast
- Crop PDF
- Auto-split PDF (with physically scanned page dividers)
- Extract page(s)
- Convert PDF to a single page
- Overlay PDFs on top of each other
- PDF to a single page
- Split PDF by sections

### Conversion Operations

- Convert PDFs to and from images
- Convert any common file to PDF (using LibreOffice)
- Convert PDF to Word/PowerPoint/others (using LibreOffice)
- Convert HTML to PDF
- Convert PDF to XML
- Convert PDF to CSV
- URL to PDF
- Markdown to PDF

### Security & Permissions

- Add and remove passwords
- Change/set PDF permissions
- Add watermark(s)
- Certify/sign PDFs
- Sanitize PDFs
- Auto-redact text

### Other Operations

- Add/generate/write signatures
- Split by Size or PDF
- Repair PDFs
- Detect and remove blank pages
- Compare two PDFs and show differences in text
- Add images to PDFs
- Compress PDFs to decrease their filesize (using qpdf)
- Extract images from PDF
- Remove images from PDF
- Extract images from scans
- Remove annotations
- Add page numbers
- Auto-rename files by detecting PDF header text
- OCR on PDF (using Tesseract OCR)
- PDF/A conversion (using LibreOffice)
- Edit metadata
- Flatten PDFs
- Get all information on a PDF to view or export as JSON
- Show/detect embedded JavaScript




# 📖 Get Started

Visit our comprehensive documentation at [docs.stirlingpdf.com](https://docs.stirlingpdf.com) for:

- Installation guides for all platforms
- Configuration options
- Feature documentation
- API reference
- Security setup
- Enterprise features


## Supported Languages

Stirling-PDF currently supports 40 languages!

| Language                                     | Progress                               |
| -------------------------------------------- | -------------------------------------- |
| Arabic (العربية) (ar_AR)                        | ![63%](https://geps.dev/progress/63)   |
| Azerbaijani (Azərbaycan Dili) (az_AZ)        | ![63%](https://geps.dev/progress/63)   |
| Basque (Euskara) (eu_ES)                     | ![37%](https://geps.dev/progress/37)   |
| Bulgarian (Български) (bg_BG)                | ![70%](https://geps.dev/progress/70)   |
| Catalan (Català) (ca_CA)                     | ![69%](https://geps.dev/progress/69)   |
| Croatian (Hrvatski) (hr_HR)                  | ![62%](https://geps.dev/progress/62)   |
| Czech (Česky) (cs_CZ)                        | ![71%](https://geps.dev/progress/71)   |
| Danish (Dansk) (da_DK)                       | ![63%](https://geps.dev/progress/63)   |
| Dutch (Nederlands) (nl_NL)                   | ![61%](https://geps.dev/progress/61)   |
| English (English) (en_GB)                    | ![100%](https://geps.dev/progress/100) |
| English (US) (en_US)                         | ![100%](https://geps.dev/progress/100) |
| French (Français) (fr_FR)                    | ![91%](https://geps.dev/progress/91)   |
| German (Deutsch) (de_DE)                     | ![100%](https://geps.dev/progress/100)   |
| Greek (Ελληνικά) (el_GR)                     | ![69%](https://geps.dev/progress/69)   |
| Hindi (हिंदी) (hi_IN)                          | ![68%](https://geps.dev/progress/68)   |
| Hungarian (Magyar) (hu_HU)                   | ![99%](https://geps.dev/progress/99)   |
| Indonesian (Bahasa Indonesia) (id_ID)        | ![63%](https://geps.dev/progress/63)   |
| Irish (Gaeilge) (ga_IE)                      | ![70%](https://geps.dev/progress/70)   |
| Italian (Italiano) (it_IT)                   | ![98%](https://geps.dev/progress/98)   |
| Japanese (日本語) (ja_JP)                    | ![95%](https://geps.dev/progress/95)   |
| Korean (한국어) (ko_KR)                      | ![69%](https://geps.dev/progress/69)   |
| Norwegian (Norsk) (no_NB)                    | ![67%](https://geps.dev/progress/67)   |
| Persian (فارسی) (fa_IR)                      | ![66%](https://geps.dev/progress/66)   |
| Polish (Polski) (pl_PL)                      | ![73%](https://geps.dev/progress/73)   |
| Portuguese (Português) (pt_PT)               | ![70%](https://geps.dev/progress/70)   |
| Portuguese Brazilian (Português) (pt_BR)     | ![77%](https://geps.dev/progress/77)   |
| Romanian (Română) (ro_RO)                    | ![59%](https://geps.dev/progress/59)   |
| Russian (Русский) (ru_RU)                    | ![90%](https://geps.dev/progress/90)   |
| Serbian Latin alphabet (Srpski) (sr_LATN_RS) | ![97%](https://geps.dev/progress/97)   |
| Simplified Chinese (简体中文) (zh_CN)         | ![95%](https://geps.dev/progress/95)   |
| Slovakian (Slovensky) (sk_SK)                | ![53%](https://geps.dev/progress/53)   |
| Slovenian (Slovenščina) (sl_SI)              | ![73%](https://geps.dev/progress/73)   |
| Spanish (Español) (es_ES)                    | ![75%](https://geps.dev/progress/75)   |
| Swedish (Svenska) (sv_SE)                    | ![67%](https://geps.dev/progress/67)   |
| Thai (ไทย) (th_TH)                           | ![60%](https://geps.dev/progress/60)   |
| Tibetan (བོད་ཡིག་) (bo_CN)                     | ![66%](https://geps.dev/progress/66) |
| Traditional Chinese (繁體中文) (zh_TW)        | ![77%](https://geps.dev/progress/77)   |
| Turkish (Türkçe) (tr_TR)                     | ![82%](https://geps.dev/progress/82)   |
| Ukrainian (Українська) (uk_UA)               | ![72%](https://geps.dev/progress/72)   |
| Vietnamese (Tiếng Việt) (vi_VN)              | ![58%](https://geps.dev/progress/58)   |
| Malayalam (മലയാളം) (ml_IN)              | ![75%](https://geps.dev/progress/75)   |

## Stirling PDF Enterprise

Stirling PDF offers an Enterprise edition of its software. This is the same great software but with added features, support and comforts.
Check out our [Enterprise docs](https://docs.stirlingpdf.com/Pro)


## 🤝 Looking to contribute?

Join our community:
- [Contribution Guidelines](CONTRIBUTING.md)
- [Translation Guide (How to add custom languages)](devGuide/HowToAddNewLanguage.md)
- [Developer Guide](devGuide/DeveloperGuide.md)
- [Issue Tracker](https://github.com/Stirling-Tools/Stirling-PDF/issues)
- [Discord Community](https://discord.gg/HYmhKj45pU)
