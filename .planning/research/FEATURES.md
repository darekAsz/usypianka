# Feature Research

**Domain:** Android baby sleep sound / white noise player (Usypianka) with timer, dB-based fade-out, live mic dB meter, safety tab
**Researched:** 2026-10-03
**Confidence:** MEDIUM overall. Safety evidence: HIGH for the Hugh 2014 and Hong 2021 abstracts (read from PubMed records) and the AAP 2023 statements (abstracts plus a direct quote from the lead author). MEDIUM for WHO values (secondary mirror of the WHO text). Competitor features: MEDIUM-LOW (store-listing snippets via web search, not hands-on testing).

Confidence tags used below: HIGH = primary source read directly. MEDIUM = two or more consistent secondary sources. LOW = single or unverified source. A single web-search result is LOW by the project's classify-confidence seam; cross-checked results are MEDIUM.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Missing these means parents uninstall, or leave 1-star reviews. Reviews of this category are dominated by two complaints: sound stops mid-night, and ads/paywalls interrupt sound. Reliability and being ad-free are therefore table stakes, and for this project that is also the Core Value.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Library of 10-40+ looping sounds: white/pink/brown noise, rain, ocean, wind, fan, heartbeat/womb, a few lullabies | Every competitor ships this (White Noise Baby: 40 sounds + 4 lullabies; Hatch: white noise, ocean, rain, birds, wind, water, thunderstorm, dryer, heartbeat, fan) | LOW-MED | The project's list (3 noises, 3 nature, instrumental lullabies) is lean but adequate. Prefer 12-20 sounds at launch. Source audio licensing is the real cost: use CC0 or self-generated audio. Generated noise (white/pink/brown) can be synthesized or shipped as long seamless files. |
| Gapless seamless looping | Audible loop seams or "pause/skip every few seconds" after an update is a reported complaint. A seam wakes a light sleeper. | MED | Needs a seamless-loop-capable player (Media3/ExoPlayer with a pre-trimmed loop or a custom audio path). Loop points must be at zero crossings. This applies to user-uploaded files too, and is hard there (MP3 encoder padding). |
| Sleep timer (preset durations + custom) | Universal. Relax Melodies allows 1 min to 23 h. | LOW | Presets 15/30/45/60/90 min + custom field (matches PROJECT.md). |
| Fade-out at end of timer | Universal. myNoise 1 min, Relax Melodies 30 s to 10 min. | LOW-MED | PROJECT.md asks for a few seconds. Fade must be linear in dB (not amplitude), which is a core requirement and a mild differentiator. |
| Playback continues with screen off / phone locked, with notification controls | The #1 functional expectation. "Cutting off in the middle of the night" is the top complaint. | HIGH | Foreground service (mediaPlayback type), MediaSession notification with play/pause/stop and remaining time, wake lock handling, battery optimization exemption guidance. This is the project's central reliability risk. |
| Own volume control independent of system volume | White Noise Baby does this explicitly. Parents change phone volume and ring volume accidentally. | LOW-MED | In this project it is replaced by the dB slider. Consider per-stream volume (STREAM_MUSIC) and whether Bluetooth/wired outputs change the calibration. |
| Works fully offline | Stated by most competitors. Phones in bedrooms are often in airplane mode (parents avoid EMF) or have no signal. | LOW | All audio bundled. No network permission needed (also helps the Play privacy declaration). |
| Dark / night UI with low brightness | Parents operate it in a dark room. | LOW | PROJECT.md "tryb nocny". Default to dark. Avoid pure white flashes at startup (splash theme). |
| No ads or interruptions during playback | Top review complaint across the category: ads in the middle of the night, paywall cutting sound after a "usage limit". | LOW (to omit) | Free with no monetization in v1, so this is a free win. Say so in the store listing. |
| Simple one-hand, fast "start" flow | Parent is holding a baby. Competitors win with "open, tap, done". | LOW-MED | Last-used / favorite one-tap start on the home screen. Big touch targets. |
| Language: PL + EN | Stated constraint. | LOW | Android string resources. Store listing text for both. |

### Differentiators (Competitive Advantage)

Aligned with the Core Value: reliable, precisely controlled, and honest about loudness. No consumer competitor found offers dB-denominated control; the closest is Hatch (hardware, volume 1-100 with a help-center statement that max volume is below 50 dB at 5-10 ft in a typical nursery), and SNOOZ (hardware) with its own app meter. Treat "no competitor" as MEDIUM-LOW confidence: it comes from searches, not an exhaustive store audit.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Volume slider expressed in estimated dB(A), with mic calibration | Parents can follow the AAP/hospital 50 dBA reference directly instead of guessing a "1-100" scale. The main differentiator. | HIGH | Two tough problems. First, phone mics are not measurement instruments: label values "approx." and show a calibration confidence note. Second, the safety references are A-weighted (dBA) and white, pink and brown noise differ a lot in A-weighted level for the same amplitude, so calibrate per sound (or per sound category) and apply A-weighting to the mic signal. See the pitfall note under Dependencies. |
| Two fade modes linear in dB: "rate" (X dB per Y min, with a floor) and "start to end" | Precise, predictable wind-down. No competitor mentions dB-denominated fades (competitors only have a fade duration). | MED | Pure scheduler math on the dB scale, converted to gain at the player. Test that the floor holds to the end of the timer. Unit-testable without audio. |
| Live mic dB meter (display only), also in background | Verifies the actual level in the room, reassures parent, supports the safety message. Display-only avoids a feedback loop. | HIGH | Background mic needs a foreground service of type `microphone`, a persistent notification, and a runtime permission. Play policy and privacy-declaration impact. Android 14+ while-in-use restrictions on starting mic FGS from background need research. Local-only processing, no recording. |
| Visual "safe zone" on the meter and the dB slider (green up to about 50 dB(A), amber/red above) | Turns the safety guidance into an instant visual cue, in a calm night palette. | LOW | Depends on the dB slider and meter. Threshold values come from the Safety content below. Use cautious wording ("reference value for hospital nurseries"), not "safe for your baby". |
| Soft warning when the chosen level exceeds about 50 dB(A) or duration is long | Product embodies the AAP advice ("lowest volume possible, short duration") rather than just telling it. | LOW | A dismissible one-time-per-session notice, not a nag. Never blocks. |
| Saved presets ("Nap", "Night") bundling sound + duration + fade settings | Faster routine, supports the "one tap" use. Hatch "routines" and Relax Melodies "favorite mixes" are the nearest. | LOW-MED | Local storage (Room/DataStore). Depends on all settings being in a single serializable model. |
| User-uploaded audio file (own lullaby, recorded voice or womb sound) | White Noise Baby Sleep Sounds and Baby Sleep Sounds advertise "record your own voice". Uploading is the cheaper version of that. | MED | Android Storage Access Framework picker (no broad storage permission). Gapless loop is hard for lossy files (see Table Stakes). Calibration for user files is by file loudness unknown; run calibration per file. |
| Safety tab with cited, honest guidance | Competitors mostly bury safety in marketing blog posts. A cited in-app tab is a trust differentiator for a parent audience. | LOW | Content below. Content work, not engineering. Needs a Polish translation reviewed for accuracy. |
| Distance-aware estimate ("phone is 2 m from the crib, estimated level at the crib about X dB") | Phone mic measures at the phone; the baby is elsewhere. Bridges the gap the project already identified. | MED | Inverse-distance approximation (about -6 dB per doubling in free field; real rooms differ). Risk of false precision, so show as a range or rough hint. Candidate for v1.x, not launch. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Automatic volume regulation from the mic (closed loop) | "Keep it at 45 dB no matter what" | Already Out of Scope in PROJECT.md. Feedback loops, uncalibrated mics, wake-ups from sudden changes, and the app's own sound contaminates the measurement. | Display-only meter plus colored safe zone. |
| "Play all night / infinite" default | Parents want sound through the night; competitors offer infinite playback | AAP/Hugh advise a short duration (until the infant is sleeping soundly). Continuous 8 h at even 50+ dBA has no safety evidence behind it, and the 2024 scoping review calls for caution on extended exposure. | Timer with a floor level (tempo mode) held at a low dB to the end of the time. No "infinite" option in v1, or cap at a documented maximum with a warning. |
| Ads, subscriptions, "usage limits" | Standard monetization in the category | The top 1-star complaint category: ads waking babies, subscriptions forgotten, sound cut when a free limit is hit. Also Families policy complexity. | Free, no ads (already decided). |
| Accounts / cloud sync | Common in connected products (Hatch) | Unneeded for a local player. Adds privacy burden and Play data-safety complexity. | Local presets only (already Out of Scope). |
| Step-wise (staircase) volume fade | Simpler to implement | Audible steps can wake a baby. Already decided against. | Smooth dB-linear fade. |
| Medical claims ("cures colic", "guaranteed to sleep", "safe for your baby") | Marketing appeal | The evidence is mixed (a single 1990 RCT of 40 neonates showed faster sleep onset; a 2024 scoping review notes risks) and the app cannot guarantee loudness at the ear. Legal/Play policy risk. | Neutral wording: "reference values", "approximate", cite sources. |
| Recording or uploading microphone audio ("baby cry monitor", cry detection) | Natural extension of the mic | Violates the privacy constraint; Play policy and children's privacy exposure; scope explosion. | Mic analysis in memory only; nothing stored or sent. |
| Sleep tracking / analytics / baby-log | Seen in Sleep Cycle-style apps | Different product; requires always-on mic, data retention, health-data policy. | Out of scope. |
| Night light / hardware control (Hatch style) | Hatch's core hardware feature | Phone screen light is the wrong tool (blue light, battery). Hardware integration is a separate product. | Dim dark UI only. |
| Sound mixing of 12 layers (Relax Melodies style) | Rich soundscapes | Doubles calibration complexity (each layer shifts the total dB). Not needed for infant use. | Single sound per session in v1. Optionally add a two-layer mix later. |

---

## Feature Dependencies

```
[Foreground playback service + MediaSession notification]
    ├──requires──> [Gapless looping engine]
    ├──requires──> [Timer + fade scheduler]
    │                  └──requires──> [dB <-> gain mapping]
    │                                     └──requires──> [Mic calibration]
    │                                                        └──requires──> [A-weighted mic metering]
    └──enhances──> [Presets]

[Live mic dB meter] ──requires──> [A-weighted mic metering]
    └──requires──> [Foreground service type=microphone + RECORD_AUDIO + notification]
    └──enhances──> [Safe-zone colouring] ──requires──> [Safety content thresholds]

[User-uploaded file] ──requires──> [Gapless looping engine] and ──requires──> [per-file calibration]

[Presets] ──requires──> [one serializable settings model (sound, duration, fade mode, dB values)]

[Distance-aware estimate] ──requires──> [Mic calibration] and ──enhances──> [Safety tab]

[Safety tab] ──independent of engineering; content must be done before safe-zone/warnings are final]

[Infinite play] ──conflicts──> [Safety guidance: short duration]
[Closed-loop mic volume] ──conflicts──> [display-only meter decision]
```

### Dependency Notes

- **Fade scheduler requires dB-to-gain mapping:** the fade is defined in dB. The mapping from "dB(A) at the phone" to player gain depends on calibration (and on output device and sound type). Without calibration the app can still fade linearly in relative dB (dBFS) but must then label values as relative, not SPL.
- **dB-to-gain mapping requires A-weighted mic metering:** safety references are dB(A). Phone mic AGC and noise suppression can distort measurement, so the recording source choice matters (for example UNPROCESSED/VOICE_RECOGNITION source if supported). Needs a spike on real devices. (From domain knowledge; flag for phase research.)
- **Mic meter requires microphone foreground service:** background level display needs the permission, notification and a policy declaration. This is the highest Play-policy risk in the list.
- **User upload requires per-file calibration:** an arbitrary file has unknown loudness; dB sliders are meaningless unless calibrated for it.
- **Infinite play conflicts with safety guidance:** the safety tab would contradict the feature set.
- **Presets require a single settings model:** design it before the UI so all later features (fade mode, floor) serialize cleanly.

---

## MVP Definition

### Launch With (v1)

- [ ] Built-in sound library of 12-20 looping sounds (noises, nature, lullabies), gapless. Core and table stakes.
- [ ] Timer with presets and custom time, end-of-timer short fade, then stop. Table stakes.
- [ ] Reliable playback with screen off through a foreground service and notification controls. The Core Value. Verify on several OEMs (battery optimization).
- [ ] Volume as dB slider with short mic calibration, labelled approximate. Main differentiator.
- [ ] Both fade modes (rate with floor, start to end), linear in dB. Differentiator.
- [ ] Live mic meter (foreground service, display only, with in-app disclosure). Differentiator. Defer background meter if Play policy review blocks it.
- [ ] Safety tab with cited content (below), PL + EN.
- [ ] Dark/night UI, PL + EN, favorites/presets.
- [ ] User-uploaded file (best-effort looping). Already in the Active requirements.

### Add After Validation (v1.x)

- [ ] Distance-aware level estimate. Trigger: users report confusion between phone and crib levels.
- [ ] Two-layer mix. Trigger: requests for rain + white noise.
- [ ] Soft warning when level or duration exceeds reference values. Cheap, may move to v1 if time allows (it is LOW complexity).
- [ ] Gradual wake-up / alarm. Trigger: requests. Note this is a different use case.

### Future Consideration (v2+)

- [ ] iOS port. Out of scope for v1.
- [ ] Wear OS / Android Auto-style remote controls.
- [ ] Voice recording of the parent (Baby Sleep Sounds advertises it). Needs a privacy review.

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Reliable background playback + timer | HIGH | HIGH | P1 |
| Gapless looping of built-in sounds | HIGH | MEDIUM | P1 |
| End-of-timer fade (dB-linear) | HIGH | LOW | P1 |
| Sound library (12-20) | HIGH | MEDIUM (licensing) | P1 |
| dB slider + calibration | HIGH | HIGH | P1 |
| Two dB fade modes with floor | HIGH | MEDIUM | P1 |
| Safety tab (cited) | HIGH | LOW | P1 |
| Night UI, PL/EN | MEDIUM | LOW | P1 |
| Live mic meter (foreground) | MEDIUM-HIGH | HIGH | P1 |
| Mic meter in background | MEDIUM | HIGH (policy) | P2 |
| Presets/favorites | MEDIUM | LOW-MEDIUM | P1 |
| User-uploaded file | MEDIUM | MEDIUM | P2 |
| Safe-zone colouring + soft warnings | MEDIUM | LOW | P2 |
| Distance-aware estimate | MEDIUM | MEDIUM | P3 |
| Layered mixing | LOW | MEDIUM | P3 |

**Priority key:** P1 must have for launch; P2 should have, add when possible; P3 future.

---

## Competitor Feature Analysis

| Feature | Hatch Rest (app + hardware) | White Noise Baby (TMSOFT) / White Noise Baby Sleep Sounds | Relax Melodies / myNoise | Our Approach |
|---------|-----------------------------|------------------------------------------------------------|--------------------------|--------------|
| Sound count | About 10 (white noise, ocean, rain, birds, wind, water, thunder, dryer, heartbeat, fan) | 40 sounds + 4 lullabies (Baby Sleep Sounds listing) | 100+ / 200+ | 12-20 curated |
| Mixing | No | Mixer with per-sound volume (listing) | Up to 12 layers (Relax Melodies) | Single sound v1 |
| Timer | Schedules/routines | Timer with soft fade-out | 1 min to 23 h, fade 30 s to 10 min (Relax Melodies); fade up to 1 min (myNoise) | Preset + custom, dB-linear fade |
| Volume | Level 1-100 (hardware) | App volume separate from system volume | App volume | **dB(A) estimate, calibrated, two fade modes** |
| Loudness safety | Help-center claim: max below 50 dB at 5-10 ft in a typical nursery; depends on room | Not stated in listings | Not stated | **Live meter + cited safety tab + safe zone** |
| Background / screen off | Hardware, so N/A | Yes ("background audio support") | Yes | Foreground service, tested for reliability |
| Offline | N/A | Yes | Mostly | Yes |
| Ads/paywall | Free app, hardware purchase | Ad-supported and some paid; "ads never interrupt playback" claimed by one | Freemium (many sounds paywalled) | None |
| Custom audio | No | Record own voice (Baby Sleep Sounds) | Limited | Upload file |
| Night light / alarm | Yes | No | Alarm (some) | No (anti-feature) |

---

## Safety Tab Content: Evidence Base (for in-app text, with citable sources)

This section is the source material for the "Bezpieczne odtwarzanie" tab. Keep claims scoped exactly as the sources state them. Several widely repeated blog claims are NOT in the primary sources (see "Claims to avoid").

### 1. Infant sleep machine study (Pediatrics, 2014) — HIGH confidence (abstract read from PubMed)

- **Citation:** Hugh SC, Wolter NE, Propst EJ, Gordon KA, Cushing SL, Papsin BC. Infant sleep machines and hazardous sound pressure levels. *Pediatrics* 2014;133(4):677-681. doi:10.1542/peds.2013-3617. PMID 24590753. Published online 3 March 2014. (Authors are from The Hospital for Sick Children / University of Toronto. This is a journal article in the AAP's journal, not an AAP policy statement. Say "published in the AAP journal Pediatrics", not "AAP recommends".)
- **Method:** 14 infant sleep machines at maximum volume, measured at 30, 100 and 200 cm, with correction factors for a 6-month-old's ear canal.
- **Result (verbatim from abstract):** "Maximum sound levels at 30 cm were >50 A-weighted dB for all devices, which is the current recommended noise limit for infants in hospital nurseries. Three machines produced output levels >85 A-weighted dB, which, if played at these levels for >8 hours, exceeds current occupational limits for accumulated noise exposure in adults and risks noise-induced hearing loss."
- **Further numbers (secondary, NEJM Journal Watch, MEDIUM):** mean maximum output about 79 dBA at 30 cm, 71 dBA at 100 cm, 63 dBA at 200 cm; all but one device above 50 dBA at every distance tested.
- **Recommendations (secondary: NEJM Journal Watch, dreamsense summary; consistent with AAP 2023 quote below; MEDIUM):** place the machine as far from the infant as possible and never in the crib or on the crib rail; play at the lowest volume possible; use for a short duration (until the infant is sleeping soundly).
- **Caveat for the app:** the study measured dedicated machines at maximum volume. A phone speaker at normal volume is a different source. Use the study for the principles, not as a measurement of this app.
- Full text is paywalled (HTTP 403 to automated fetch). Link to the DOI and PubMed page. Verify the exact recommendation wording against the full text before quoting it in the app.

### 2. White noise devices and phone apps (2021) — HIGH confidence (abstract read from PubMed)

- **Citation:** Hong SA, Kuziez D, Das N, Harris D, Brunworth JD. Hazardous sound outputs of white noise devices intended for infants. *Int J Pediatr Otorhinolaryngol* 2021;146:110757. doi:10.1016/j.ijporl.2021.110757. PMID 33992973.
- **Findings:** 8 machines and 6 iPhone apps, measured at 10 cm (inside the crib), 30 cm (just outside the rail) and 100 cm. At minimum volume no device exceeded the NIOSH 85 dBA threshold at any distance. At maximum volume 9 of 14 (64.3%) exceeded 85 dBA at 10 cm. No device exceeded 85 dBA at maximum volume at 30 cm or 100 cm.
- **Authors' recommendation:** conservative use, avoid maximum volume, place any device well outside the crib or at least 30 cm away.
- **Use in app:** this is the one study that includes phone apps, so it directly supports "do not put the phone in the crib" and "do not use maximum volume". It also shows that 85 dBA is the hazard threshold being tested, not a target (the nursery reference is much lower).

### 3. AAP policy statement and technical report (Pediatrics, November 2023) — HIGH for citation, HIGH for the quoted sentence, no numbers

- **Citations:** Balk SJ, Bochner RE, Ramdhanie MA, Reilly BK; Council on Environmental Health and Climate Change; Section on Otolaryngology-Head and Neck Surgery. Preventing excessive noise exposure in infants, children, and adolescents (policy statement). *Pediatrics* 2023;152(5):e2023063752. doi:10.1542/peds.2023-063752 (PMID 37864407). Accompanying technical report: *Pediatrics* 2023;152(5):e2023063753. doi:10.1542/peds.2023-063753 (PMID 37864408).
- **What it says that matters:** infants and young children cannot remove themselves from noisy situations and rely on adults; effects of noise are cumulative; noise exposure limits are enforceable only in workplaces, not for the general public or children.
- **Sleep-machine guidance, from lead author Dr. Sophie Balk (Healio Q&A, 21 Oct 2023; MEDIUM):** "If infant sleep machines are used, it is prudent to locate them as far away as possible from the infant, set the volume as low as possible and limit duration of use." The AAP release itself says only that pediatricians can counsel parents on safe use and that "one study raised potential concern about sound levels" (EurekAlert release).
- **Important:** I could not verify any numeric distance or dB limit in the AAP 2023 materials. Some secondary sources claim "at least 7 feet away and 50 dB max" as AAP guidance. That is NOT confirmed by the AAP sources I read. Treat as LOW and do not attribute it to the AAP in the app.

### 4. The 50 dB(A) "hospital nursery" figure — MEDIUM, attribution needs care

- Hugh 2014 itself states 50 A-weighted dB is "the current recommended noise limit for infants in hospital nurseries" (HIGH, abstract). The abstract does not give the underlying document.
- Related AAP/EPA lineage (MEDIUM, secondary): the AAP Committee on Environmental Health (Noise: a hazard for the fetus and newborn, *Pediatrics* 1997;100(4):724-727) cited the US EPA indoor hospital level of 45 dBA daytime / 35 dBA night and applied about 45 dBA hourly Leq to the NICU. Later NICU design standards add 50 dBA as an L10 limit and 60 dBA as Lmax (via secondary sources; White 2007-2008 nursery criteria, Adv Neonatal Care, PubMed 18818537, not read).
- **Recommended in-app wording:** "A commonly cited reference limit for infant hospital nurseries is 50 dB(A) (cited in Hugh et al., Pediatrics 2014)." Do not write "WHO recommends 50 dB for babies". Do not write "AAP recommends 50 dB for sleep machines" unless the full text supports it.
- The in-app slider default should sit comfortably under 50 dB(A) and the meter should present 50 dB(A) as a "reference", not a certified safe limit.

### 5. WHO values — MEDIUM (secondary mirror of WHO Guidelines for Community Noise, 1999)

- **Citation:** Berglund B, Lindvall T, Schwela DH (eds). *Guidelines for Community Noise.* World Health Organization, Geneva, 1999. Section 4 guideline values. (Read via the ruidos.org mirror; confirm against WHO's own PDF before publishing.) Also WHO Europe, *Night Noise Guidelines for Europe*, 2009.
- **Values (specific environments, indoor):** bedrooms 30 dB LAeq at night (35 dB daytime) and 45 dB LAmax; pre-school bedrooms 30 dB LAeq (sleeping time) and 45 dB LAmax; hospital ward rooms 30 dB LAeq and 40 dB LAmax at night.
- **Vulnerable groups:** "children may be more vulnerable in acquiring noise-induced hearing impairment than adults" (animal data); hearing-impairment risk negligible for LAeq,24h of 70 dB over a lifetime.
- **Correct framing:** the WHO values are for sleep disturbance from environmental noise, not a recommended level for playing a sleep sound. WHO does NOT publish "50 dB for babies". Use WHO to explain why quiet is better for sleep quality (30 dB LAeq bedroom, 45 dB LAmax), and for the comparison scale. The two numbers (WHO 30 and the nursery 50 dBA) are not in conflict: one is a sleep-quality target against intruding noise, the other is a limit for noise hazard in nurseries. State that plainly.

### 6. Occupational reference — HIGH

- NIOSH recommended exposure limit: 85 dBA averaged over an eight-hour workday (CDC/NIOSH). Hugh 2014 and Hong 2021 both use it as the hazard benchmark. It is an adult occupational limit and not a safe level for infants. State it as context for "levels above this are hazardous for adults, infants are more vulnerable".

### 7. Duration and benefit evidence

- **Benefit (MEDIUM, single small RCT):** Spencer JA, Moran DJ, Lee A, Talbert D. White noise and sleep induction. *Arch Dis Child* 1990;65(1):135-137. doi:10.1136/adc.65.1.135. 2 groups of 20 neonates aged 2-7 days: 16 of 20 (80%) fell asleep within 5 minutes with white noise versus 5 of 20 (25%) of controls. Small, old; do not overstate.
- **Review (HIGH for citation):** De Jong RW, Davis GS, Chelf CJ, Marinelli JP, Erbele ID, Bowe SN. Continuous white noise exposure during sleep and childhood development: a scoping review. *Sleep Med* 2024;119:88-94. doi:10.1016/j.sleep.2024.04.006. 20 publications (7 animal, 13 pediatric). Summary read via web search (MEDIUM): safe volume white noise may lengthen sleep and reduce night waking; extended or loud exposure is a concern; there is no regulation of maximum volume of generators; authors advise avoiding loud or extended white noise.
- **No numeric duration limit exists in any source I could verify.** Only qualitative guidance: "short duration, until the infant is sleeping soundly" (Hugh 2014 via NEJM JW; Balk 2023: "limit duration of use"). Do not invent "max 30 min" figures. The in-app text can describe how to use the timer and dB fade as the practical implementation of that guidance, and mark any specific default (for example 15-30 min) as the app's design choice, not an AAP rule.

### 8. Distance

- Qualitative everywhere: "as far away as possible", "never in the crib or on the crib rail" (Hugh 2014 via secondary summaries; Balk 2023 quote).
- Numeric lower bound: at least 30 cm (Hong 2021, to avoid >85 dBA at max volume). Hong 2021 tested 10/30/100 cm and Hugh 2014 tested 30/100/200 cm. Hugh's 200 cm mean maximum was still about 63 dBA, above 50 dBA, so distance alone does not make maximum volume safe. Volume matters more.
- Hatch (MEDIUM, manufacturer help-center article via search snippet; page itself was 403): "At 5-10 feet away from a sleeping baby, in an average-sized nursery with carpeting, the absolute maximum volume is below 50 decibels", and levels depend on room size, placement and surfaces.
- CHOP (MEDIUM, hospital news item) suggests more than 200 cm (about 6.5 ft). This is CHOP's reading, not a number from the AAP policy.
- **Suggested in-app wording:** "Place the phone as far from the baby as practical (at least 1-2 m is a sensible target), never in the crib or on the rail, and keep the volume low. Distance alone is not enough: maximum volume can still be loud at 2 m." Label the 1-2 m number as the app's practical advice, derived from the study distances, not a published standard.

### 9. Comparison scale for the tab (common reference values, approximate)

Whisper about 30 dB, quiet room/library about 40 dB, quiet conversation about 50-60 dB, normal conversation about 60 dB, vacuum cleaner/busy street about 70-80 dB, occupational limit 85 dBA. These are standard acoustic reference values (general knowledge, LOW as a cited source because I did not fetch one). Cite a source such as CDC/NIOSH or the WHO/EPA material before shipping, or use WHO/NIOSH numbers only.

### Claims to avoid in the app (found in secondary sources, not supported by primary sources read)

- "AAP recommends 50 dB maximum for sleep machines at 7 feet." Not found in AAP 2023 materials; the 50 dBA figure is nursery guidance quoted in Hugh 2014.
- "Infants can have hearing damage starting at 60 dB." Single secondary source, not verified.
- "Carpet reduces the dB level by 50-70%." Hatch help-center claim, vague (percent of what?). Do not repeat it.
- "WHO recommends 50 dB for infant rooms." WHO values are 30/35 dB LAeq for bedrooms, as above.
- Any specific safe duration in minutes or hours.
- "Safe for your baby" style assurances. The app cannot know speaker output at the ear.

### Honesty notes the UI must carry (from PROJECT.md context, confirmed by sources)

- Phone microphones are not calibrated meters; dB values are estimates. Safety references are in dB(A) and the same loudness reads differently for white, pink and brown noise.
- The mic reads the level at the phone, not at the child's ear. If the phone is the sound source and is placed 1-2 m away, the level at the crib is lower than the near-field reading by an amount that depends on the room (more reverberant rooms attenuate less).
- Hugh 2014 and Hong 2021 tested particular devices. Findings are directional, not a guarantee for any device.

---

## Sources

**Primary / HIGH (read directly)**
- PubMed record, Hugh et al. 2014: https://pubmed.ncbi.nlm.nih.gov/24590753/ (PMID 24590753; article page https://publications.aap.org/pediatrics/article/133/4/677 returned 403 to fetch; doi:10.1542/peds.2013-3617)
- PubMed record, Hong et al. 2021: https://pubmed.ncbi.nlm.nih.gov/33992973/ (doi:10.1016/j.ijporl.2021.110757)
- PubMed records, AAP 2023 policy statement https://pubmed.ncbi.nlm.nih.gov/37864407/ and technical report https://pubmed.ncbi.nlm.nih.gov/37864408/ (doi:10.1542/peds.2023-063752 and -063753)
- PubMed record, Spencer et al. 1990: https://pubmed.ncbi.nlm.nih.gov/2405784/ (doi:10.1136/adc.65.1.135)
- CDC/NIOSH occupational noise, 85 dBA REL: https://www.cdc.gov/niosh/noise/about/index.html

**Secondary / MEDIUM**
- NEJM Journal Watch summary of Hugh 2014 (distance/volume/duration recommendations, 79/71/63 dBA means): https://clinician.nejm.org/sleep-machines-damage-infant-hearing-nejm-jw.NA34176
- Healio Q&A with Dr. Sophie Balk (quote on sleep machines), 21 Oct 2023: https://www.healio.com/news/pediatrics/20231021/qa-how-children-could-lose-hearing-due-to-loud-noises
- EurekAlert, AAP 2023 release: https://www.eurekalert.org/news-releases/1004607
- CHOP, "White Noise: Not the Right Noise": https://www.chop.edu/news/white-noise-not-right-noise
- WHO Guidelines for Community Noise (1999), section 4 mirror: https://www.ruidos.org/Noise/WHO_Noise_guidelines_4.html (confirm against WHO original; also WHO Night Noise Guidelines for Europe 2009: https://www.polisnetwork.eu/wp-content/uploads/2019/06/who-night-noise-guidelines.pdf)
- AAP 1997 noise statement page: https://pediatrics.aappublications.org/content/100/4/724 (not read, citation only; lineage summarized from search results)
- Scoping review, De Jong et al. 2024: https://www.sciencedirect.com/science/article/abs/pii/S1389945724001588 (abstract-level summary via search)
- Hatch help center, "Your Hatch and Decibel Level": https://help.hatch.co/hc/en-us/articles/34411321590167-Your-Hatch-and-Decibel-Level (snippet via search; page returned 403)

**Competitor features / LOW-MEDIUM (store listings and review summaries via web search; Google Play pages did not render for direct fetch)**
- Hatch Sleep (Google Play): https://play.google.com/store/apps/details?id=com.hatchbaby.rest
- White Noise Baby (TMSOFT): https://play.google.com/store/apps/details?id=com.tmsoft.whitenoise.baby
- White Noise Baby Sleep Sounds: https://play.google.com/store/apps/details?id=com.amikulich.babysleep
- Baby Sleep Sounds: White Noise: https://play.google.com/store/apps/details?id=com.st.sweetdreams
- Baby Sleep - White Noise (Relaxio): https://play.google.com/store/apps/details?id=net.relaxio.babysleep
- Medical News Today and Reviewed comparisons of Relax Melodies / myNoise: https://www.medicalnewstoday.com/articles/white-noise-apps , https://www.reviewed.com/sleep/features/review-sound-machine-apps-for-iphone-and-android

---
*Feature research for: Android baby sleep sound app (Usypianka)*
*Researched: 2026-10-03*
