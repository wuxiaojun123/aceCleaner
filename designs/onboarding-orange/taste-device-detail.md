# Device overview / Taste redesign

Reading this as: an existing mobile device-information prototype for everyday cleaner users, with a calm, practical visual language that preserves the Orange brand. This is product UI, so the repository's `redesign-existing-projects` is the primary recipe; only relevant typography, hierarchy, contrast and preservation rules from `design-taste-frontend` apply. No landing-page imagery, scroll hijacking or framework migration.

Source skills: `/Users/wangyiyi/AI/skill/taste-skill/skills/taste-skill/SKILL.md` and `skills/redesign-skill/SKILL.md` in the same repository.

Design dials: DESIGN_VARIANCE 4 / MOTION_INTENSITY 2 / VISUAL_DENSITY 6. The existing phone prototype, system font and Orange palette remain the foundation. Scope is `#deviceDetail`; all other screens retain their existing components and styles.

Audit: equally prominent identity icons compete with the model name. Repeated bordered cards flatten the hierarchy. CPU and screen rows have a dense run of dividers. The original primary button's white-on-bright-orange contrast needs improvement.

Changes: prominent model and compact OS metadata; RAM and storage in a joined two-column resource section; a slim battery strip; grouped CPU and screen definitions with sparse separators. The new CTA uses darker orange for readable white text. Meaningful indicators use tabular numbers; motion is limited to button feedback and the existing page transition. Scoped `.dd-*` CSS prevents regressions to other screens.

Preserved sample data: MOTOROLA / moto e20, Android 11 / REL, RAM 63% and 1.2 / 1.9 GB, storage 36% and 9.0 / 25 GB, battery 99% Charging, CPU ums9230_4h10_go / 1612 MHz / 8 cores, screen 720 × 1600 / 280 DPI / 60 Hz. These are the original prototype's example values, not live device measurements.

Preserved actions: Skip follows the existing ad-preview/home path; Clean Junk Files opens the existing storage-permission flow. URL, navigation and Android code remain unchanged.

Before-edit source backups are in `history/`; before/after screenshots are in `previews/`. Review the running page at `index.html?auto=0#deviceDetail`.

Validation: browser runtime errors: none. 390 × 844: no horizontal overflow, full content fits, CTA stays on one line. 320 × 700: no horizontal overflow, content scrolls independently and CTA remains visible. Clean Junk Files opens Permission Request; closing follows the unchanged battery path. Skip opens the existing interstitial preview and Continue reaches #home. All original parameters retained.


## Vivid Orange revision

User requested stronger first impression and the exact original Orange accent. The revised page uses #FF6B2C on both hero and CTA, a generated generic Android phone illustration, oversized model typography, an overlapping white resource panel, a battery strip and paired CPU/screen panels. Graphite text on orange replaces the previous darker-orange CTA, keeping readable contrast without changing the brand accent. All values and callbacks are preserved. The original calm revision is backed up in history/components-taste-calm.jsx and history/prototype-taste-calm.css.

Illustration: assets/device-phone-orange-3d.png, generated using the built-in imagegen tool, with actual alpha transparency. This is an illustrative generic phone, not a product photograph or a precise representation of moto e20. Full generation prompt is saved beside it in assets/device-phone-orange-3d-prompt.txt.

Vivid validation: hero and CTA both compute to rgb(255,107,44). 390 × 844 contains all parameters without overflow. At 320 × 700, content scrolls and CTA stays visible. Image loads successfully, cleaning opens the original permission sheet, browser reports no runtime errors. Results are in previews/device-vivid-validation.json.

User color refinement: hero brand, model, OS/build text and CTA label/icon now use white, preserving #FF6B2C backgrounds. This supersedes the earlier graphite-on-orange text treatment.
