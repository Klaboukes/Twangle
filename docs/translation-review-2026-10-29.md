# Translation meaning review, 2026-10-29

Phase 50 (ROADMAP.md). Every one of the 553 translatable texts was read again in each of the 15 translated
languages, for meaning rather than for completeness (LocalizationTest already checks that). One reviewer per
language, working as a native-level translator and editor, read each text next to its English, the translator
note above it (PR #463), the Dutch, the language's register line in
[languages.md](architecture/languages.md) and the [glossary](architecture/glossary.md), and looked at the code
where a note left the place unclear. They fixed wrong meanings (a button read as a title, a verb as a noun, "play"
mixed up with "tap"), the wrong register, stiff or literal wording, one term said two ways, and grammar; texts
stayed as short as before, every placeholder and plural stayed as it was. `settings_about_privacy_summary` was left
for the night's `/translate` (its English changed the same day).

Where a tool or term was renamed, the store listing (`fastlane/metadata/android/<locale>/full_description.txt`) and
the glossary follow. Whether the changed texts still fit on screen in the 14 night languages is checked by the next
overnight run; Dutch fit was checked by day.

Each line: `name: old → new (why)`. "For a native speaker to look at" lists the reviewer's own doubts: choices left
as they are, or changed but worth a second ear.

## Dutch (nl): 3 of 553 changed

- Grammar. settings_display_numbers: hoeveel cents → hoeveel cent (the unit stays singular)
- Wording. settings_focus_mode_summary: van een afstand → op afstand
- Consistency. tuner_trail_no_note: geen toon → geen noot (the tuner says "noot")

For a native speaker to look at:
- tour_strum "Sla aan en check alle snaren": everywhere else says "strum"; left because docs and the tour mock-up quote it.
- tour_strings "Kies om vast te zetten, houd vast om te horen": "vastzetten" next to "vasthouden" is awkward.
- tuner_tip_too_loud "leg de telefoon verder weg": "houd" would fit the note; the website quotes it.
- mic_permission_settings_hint "Machtigingen": Samsung and Xiaomi; Pixel says "Rechten".
- maj9 "groot noon": "majeur noon" or "major nine" may be more natural.
- Noot vs toon: Ear training says "toon", tuner and drone say "noot"; each tool is consistent in itself.
- metronome_no_beat "Geen duidelijk ritme gehoord": "Geen vaste tel gehoord" is closer; the website quotes it.

## German (de): 16 of 553 changed

- Meaning. tuner_pitch_trail, tuner_trail_spoken: Tonspur → Tonverlauf ("Tonspur" is a film's audio track)
- Meaning. settings_meter_look_light_arc: Lichtbogen → Lichterbogen ("Lichtbogen" is an electric arc)
- Meaning. ear_not_that_one: Der nicht → Leider nicht (also said for intervals: wrong gender)
- Meaning. settings_vibrate_in_tune_summary: Summen → Vibrieren (a hum is a sound)
- Meaning. mic_permission_settings_hint: Android's own wording „Nur während der Nutzung der App zulassen“
- Wording. practice_chord_none, ear_chord_none: greifbarer Griff → spielbarer Griff
- Wording. error_mic_stopped: hat aufgehört → ist ausgefallen; widget_description: Ein Tippen → Antippen
- Wording. tuner_chord_strum_detail, tuner_chord_unclear_detail: seine Saiten → Saiten des Akkords
- Consistency. metronome_listening: Hört zu… → Höre zu…; settings_about_website_summary: Funktion → Werkzeug
- Grammar. tour_readout: no comma before "und"; tuner_screen_off_keep_on: an bleibt → anbleibt
- Store listing: Tonspur → Tonverlauf. Glossary: Pitch trail = Tonverlauf.

For a native speaker to look at:
- "Tonverlauf" (new tool name); "Tonhöhenverlauf" is more exact but long.
- settings_meter_look_eclipse "Finsternis" can read as "darkness".
- settings_dynamic_color_title "Hintergrundfarben" may read as the app's own background.
- metronome_no_beat "Kein klarer Takt": "Beat" is what musicians say.
- tuner_hint_playing "Spielt %1$s": "%1$s klingt" might read better.
- settings_in_tune_effect_title "Effekt, wenn gestimmt" is a little stiff.

## French (fr): 34 of 553 changed

- Meaning. tuner_strum_hearing, metronome_listening, ear_listening: Écoute… → À l'écoute… (not the imperative)
- Meaning. tuner_chord_spoken_recent: avant %1$s → avant cela, %1$s (read as "before G major")
- Meaning. tuner_string_sounding, tuner_string_locked_sounding: en cours → son en cours
- Meaning. tuner_strum_spoken_less_sure: moins sûr → moins fiable
- Wording. tuner_intonation_press_fret: Appuyez-la → Jouez-la; press_detail: sans juste effleurer → sans effleurer
- Wording. tuner_intonation_all_fine → Cordes testées : tout est bon; mark_fine: pontet bon → pontet bien placé
- Wording. tuner_chord_unclear → Aucun accord net; tuner_delete_tuning_text: pour de bon → définitivement
- Wording. mic_permission_settings_hint: appli → application; settings_about_credits_summary less stiff
- Wording. ear_any_octave → Peu importe l'octave; practice_spoken_scale_open: %1$s à vide (French order)
- Wording. practice_drone_flute_organ: Orgue à flûtes (plural)
- Consistency. ear_notes_naturals, ear_notes_all: case 5 / case 12 → 5e case / 12e case (glossary)
- Consistency. tuner_intonation_play_open, tune_first: la corde de %1$s → la corde %1$s
- Consistency. tuner_lower_more, tuner_lower_less: curly ’ → \' as elsewhere
- Grammar. tour_picker: articles added; no-break space before ":" and "?" in 8 texts
- Grammar. ear_tally_spoken → Bonnes réponses : %1$d sur %2$d ("1 justes" was wrong)

For a native speaker to look at:
- "Doigté" for a chord shape means fingering; "position" or "forme" is common.
- nav_practice "Pratique" can read as "practical"; "Entraînement" would be clearer (glossary word).
- ear_exercise_play_back "Rejouer" can read as "replay"; "Reproduire" is an option.
- tuner_hint_playing "Lecture de %1$s" sounds like a media player.
- settings_wave_look_title "Style d'onde" clashes with the app's visual Styles.
- metronome_sound_cowbell "Cloche de vache": drummers say "cloche" or "cowbell".

## Spanish (es): 12 of 553 changed

- Meaning. tuner_strum_tap_to_tune, tuner_strum_again, tuner_intonation_tap_string: Toca → Elige (tap, not play)
- Meaning. metronome_heard: Oído → Detectado (read as "ear")
- Wording. metronome_beats_in_bar: Pulsos → Tiempos por compás; ear_tally_spoken: bien → correctas
- Wording. ear_played: Tocado → Cómo suenan; settings_vibrate_in_tune_summary: llega al tono → queda afinada
- Grammar. tuner_intonation_press_detail: rozes → roces; settings_show_tune_hint_title: Indicar si subir o bajar
- Grammar. settings_about_credits_summary: esto → ellos; practice_drone_flute_organ: Órgano de flautas

For a native speaker to look at:
- "Cejilla" is both the capo and a barre; Latin America often says "capotraste".
- "selleta" for the saddle; "silleta" or "hueso" in Latin America.
- ear_exercise_play_back "Repítela" names a note never named.
- "Pulso" stays elsewhere in the metronome while the meter picker now says "Tiempos".
- tuner_lower_by "Bajar: Nada" reads a little bare.

## Italian (it): 18 of 553 changed

- Meaning. TalkBack action labels to infinitives, as TalkBack says "Tocca due volte per …" (es, fr, pt already did):
  instrument_button_action, practice_capo_open_chord, settings_about_licence_open, tuner_string_lock, tuner_string_unlock,
  tuner_string_play, tuner_string_stop_tone, tuner_signal_show_figures, tuner_strum_tune_string, tuner_intonation_check_string
  (all only `onClickLabel` / `longClickLabel`, never shown)
- Wording. practice_spoken_scale_open: %1$s a vuoto; settings_in_tune_precision_relaxed: Morbida → Tollerante
- Wording. settings_in_tune_effect_title: Effetto intonato → Effetto d'intonazione
- Wording. settings_show_tune_hint_summary: finché → quando; settings_about_website_summary: Come si usa ogni funzione
- Wording. mic_permission_settings_hint: Android's "solo mentre l'app è in uso"
- Grammar. settings_notation_flats: Bemolle → Bemolli; tuner_strum_spoken_less_sure: sicuro → sicura (la corda)

For a native speaker to look at:
- Chord shape "diteggiatura" everywhere; "posizione" or "forma" is common.
- tuner_cents_flat/sharp "12 cent calante": "calanti" or "calante di 12 cent" are the other choices.
- ear_exercise_play_back "Risuonala" may read as "make it resound".
- ear_direction_up/down "Ascendente/Discendente" are long for one short word.
- settings_show_tour_title "Tour guidato": "Visita guidata" is more Italian.
- metronome_beats_in_bar "Battiti per battuta": musicians say "Tempi", but "tempo" means BPM here.

## Portuguese, Brazil (pt-rBR): 13 of 553 changed

- Meaning. ear_exercise_play_back: Repita → Tirar de ouvido (an exercise name)
- Meaning. ear_spoken_heard: Ouvido → Nota ouvida; metronome_heard: Ouvido → Detectado (read as "ear")
- Meaning. metronome_no_beat → Sem pulso regular; settings_wave_look_loop: Laço → Aro (a hoop)
- Wording. tuner_strum_tap_to_tune, tuner_strum_again, tuner_intonation_tap_string: Toque → Escolha (tap, not play)
- Wording. tour_readout → O desvio e qual é a nota; settings_vibrate_in_tune_summary: fica afinada
- Wording. ear_choices: O que é perguntado → O que perguntar
- Grammar. ear_played: Tocadas; tuner_strum_spoken_less_sure: com menos certeza
- Glossary: Play it back = Tirar de ouvido (tonight's privacy text should use it).

For a native speaker to look at:
- metronome_heard "Detectado": "Tempo ouvido" is closer if it fits.
- Saddle "rastilho": electric players say "carrinho".
- instrument_viola "Viola" first means the viola caipira in Brazil.
- Violão de 8 cordas: 8-string guitars are mostly electric.
- ear_tally_spoken "1 certas" is wrong in the singular (not a plural resource).

## Chinese, Simplified (zh-rCN): 37 of 553 changed

- Meaning. tuning_lowered_2…5, tuner_lowered_2…5: 一个半全音 → 1½ 个全音 (and the digits for 1 and 2 too)
- Meaning. practice_scale_type_phrygian_dominant, practice_scale_phrygian_dominant: 属音 → 属音阶
- Meaning. practice_capo_shapes(_spoken): 我弹的 → 我会的指法; practice_capo_sounds_in: …%1$s 调 (a key)
- Meaning. tuner_trail_bending_up/down → 音高上升 / 音高下降
- Wording. four *_short_string texts: 还不认识 → 暂不支持; ear_chord_prompt → 是哪种和弦？
- Wording. practice_drone_warm_pad: 温暖音垫 → 温暖铺底; tuner_chord_unclear → 和弦不够清晰
- Wording. tuner_strum_tune_string, tuner_intonation_check_string: 调/检查 %1$s 弦 (the string, not the note)
- Wording. tuner_strum_to_tune, tuner_intonation_to_move, tuner_chord_spoken_recent completed; quotes round Android's option name
- Consistency. ear_chord_none, practice_chord_none: 调弦 → 定弦 (the noun, glossary; the verb 调音 is unchanged)
- Consistency. ear_exercises: 练习 → 练习项目 (练习 is the Practice tab); 轻触 → 点按 (2 texts); 许可 → 许可证 (3 texts)

For a native speaker to look at:
- settings_meter_look_title "表盘" means a dial face; most looks are not dials ("指示器", "仪表").
- metronome_sound_rim_click "鼓边", wood_block "木鱼": drummers also say "边击", "木块".
- ear_hold "保持你听到的那个音": "保持" may read as "remember".
- practice_drone_warm_pad "温暖铺底" (new); "温暖 Pad" is common too.
- tuning_baritone "上低音": "男中音" is also seen.
- practice_drone_notes "音符" vs ear_notes "音": two headings for "Notes".

## Japanese (ja): 22 of 553 changed

- Meaning. tuner_lower_by: 下げる → 下げ幅 (a label over an amount)
- Meaning. ear_exercises: 練習 → 練習メニュー; ear_exercise_play_back: 弾いてみる → 聞いて弾く
- Meaning. practice_capo_sounds_in → 実際のキー：%1$s; sounds_column: 鳴る音 → 実音; row_spoken to match
- Register. tuner_intonation_press_detail: て-form → ください; tuner_chord_strum_detail → 全部の弦で
- Wording. tuner_chord_unclear → コードを判別できません; ear_interval_prompt → 音程は？; ear_chord_prompt → コードの種類は？
- Wording. practice_capo_shapes(_spoken): 弾く → 使う押さえ方; tuner_chord_spoken_recent: その前は; metronome_heard: テンポ
- Consistency. ear_chord_none, practice_chord_none: 形 → 押さえ方; 音 → 構成音 (2 texts); full-width colon; 5フレット, 12フレット
- Glossary: Play it back = 聞いて弾く.

For a native speaker to look at:
- half-diminished マイナーセブンスフラットファイブ is long; ハーフディミニッシュ is common.
- 実際のキー vs 実音キー.
- 信号バー is literal; シグナルバー or 入力レベル.
- settings_in_tune_effect_bold 華やか: 強め is closer to "strong glow".
- ear_any_octave どのオクターブでも可 is a bit stiff.
- tuner_intonation_press_detail grew 4 characters: check it fits one line.

## Korean (ko): 21 of 553 changed

- Register. ear_chord_none, practice_chord_none, tuner_chord_unclear, tuner_chord_none: 합쇼체 → 해요체
- Wording. four *_short_string texts: 모릅니다 → 지원하지 않아요
- Wording. practice_capo_sounds_column: 소리 → 실제 코드; sounds_in → 실제 키: %1$s; row_spoken to match
- Wording. settings_about_credits_summary less literal; metronome_no_beat → 뚜렷한 박자가 안 들려요
- Meaning. ear_exercises: 연습 → 연습 종류 (연습 is the Practice tab)
- Consistency. tuner_chord_strum_detail, unclear_detail: 현 → 줄; 음 이름 → 음이름 (2 texts); %d 프렛 → %d프렛 (3 texts)
- Store listing: 음 이름 → 음이름.

For a native speaker to look at:
- practice_capo_sounds_column 실제 코드 is wider than 소리: check it fits.
- tuner_lower_by 내리기 reads like a verb before a value; 낮춤 or 내림 폭.
- settings_meter_look_light_arc 빛의 호, light_bars 빛 막대 are a bit literal.
- style_futuristic 미래: 미래풍 is an option.
- tour_skip 둘러보기 건너뛰기 could be just 건너뛰기.

## Hindi (hi): 37 of 553 changed

- Meaning. style_futuristic: भविष्य → फ़्यूचरिस्टिक (the noun "future"; loanword like क्लासिक, रेट्रो)
- Meaning. metronome_beats_in_bar → बीट प्रति बार; tuner_lowered_none: बिल्कुल नहीं → कोई नहीं
- Wording. chord shape आकार → शेप (8 texts); root मूल स्वर → रूट (3 texts); sound ध्वनि → साउंड (5 texts)
- Wording. settings_group_look → रूप-रंग; settings_a4_title → रेफ़रेंस पिच; settings_notation_automatic → ऑटोमैटिक
- Wording. settings_meter_look_light_arc → लाइट आर्क; light_bars → लाइट बार; tuner_signal_background → बैकग्राउंड शोर
- Wording. practice_spoken_scale_colour → ख़ास सुर; settings_left_handed_title shorter
- Consistency. सप्तक → ऑक्टेव (2 texts); स्वर → सुर (2 texts); सुर → नोट for the shown note (2 texts); शैली → रूप
- Grammar. metronome_no_beat: gender of ताल (now बीट); tour_readout: कौन-सा
- Store listing: रेफ़रेंस पिच, कॉर्ड शेप. Glossary: Futuristic, chord shape, root.

For a native speaker to look at:
- शेप as masculine (पिछला शेप); some say पिछली शेप.
- रूट vs रूट नोट or मूल सुर.
- Drone "+ पंचम और सप्तक" (tanpura words) vs Ear training फ़िफ़्थ / ऑक्टेव.
- नोट (tuner, settings) and सुर (Ear training, chords) both stay.
- ear_exercises "अभ्यास" is also the Practice tab.
- style_contemporary "समकालीन" is formal; "मॉडर्न" is everyday.

## Indonesian (in): 16 of 553 changed

- Meaning. ear_exercise_play_back: Mainkan kembali → Tirukan nada (clashed with Replay)
- Meaning. metronome_stop: Berhenti → Hentikan (a button); ear_any_octave: dihitung → berlaku
- Wording. tuner_tip_too_soft → Mainkan lebih keras; tuner_chord_strum: sebuah → satu akor
- Wording. settings_left_handed_summary fixed; settings_group_pitch → Nada dan notasi
- Consistency. stelan → stem (2 texts, glossary); terbuka → lepas (3 texts); alat musik → instrumen (2 texts)
- Consistency. settings_display_title: not → nada; settings_vibrate_in_tune_summary: tepat → pas (glossary)
- Store listing: senar terbuka → senar lepas. Glossary: Play it back = Tirukan nada.

For a native speaker to look at:
- "pasak" for the tuning peg is formal.
- tuner_lowered_none "Tidak": "Tidak ada" is more exact but longer.
- Chord order "mayor septim" vs interval "septim mayor".
- practice_capo_sounds_in "Berbunyi dalam %1$s" is literal.
- "Akor" (glossary) vs the casual "kunci".

## Russian (ru): 18 of 553 changed

- Meaning. tuner_strum_spoken_less_sure: менее точно → не наверняка
- Meaning. practice_capo_sounds_in → Тональность: %1$s; tuner_chord_unclear → Аккорд не распознан
- Wording. tuner_pitch_trail, tuner_trail_spoken: След высоты → График высоты (a calque)
- Wording. four *_short_string texts: не знают → не поддерживают
- Wording. settings_in_tune_precision_relaxed/precise: Свободно/Точно → Нестрого/Строго
- Wording. mic_permission_intro, tuner_lower_less, tour_readout, tour_strings, tuner_chord_next, settings_display_numbers
- Grammar. settings_about_website_summary: comma before «и» removed
- Store listing: След высоты → График высоты. Glossary: Pitch trail = График высоты.

For a native speaker to look at:
- tuner_intonation_fine «Всё в порядке» is three words for one short word (glossary).
- Root «Тоника»: right for scales; «Основной тон» is the chord term.
- sus/add stay in Latin letters; TalkBack may read them oddly.
- «График высоты» (new); alternative «Линия высоты».
- Drop H / Drop B: many Russian rock players mean си by "Drop B".

## Turkish (tr): 15 of 553 changed

- Meaning. tuner_strum_not_all_heard: Tüm teller duyulmadı → Bazı teller duyulmadı (read as "none heard")
- Meaning. error_mic_permission: Akorda devam → Akort etmeye devam; tuner_string_locked(_sounding): Sabit → Sabitlendi
- Consistency. pitch is no longer "perde" (that is the fret): tuner_pitch_trail, tuner_trail_spoken: Perde izi → Ses izi;
  settings_group_pitch → Ses ve notalar; settings_vibrate_in_tune_summary → Nota akortlu olunca
- Consistency. metronome_listening: Dinleniyor → Dinliyor ("is resting")
- Wording. tuner_intonation_let_ring, tuner_chord_let_ring → Bırak çınlasın; tuner_chord_spoken_recent → öncesinde
- Wording. settings_left_handed_summary → gam klavyesi
- Grammar. practice_drone_flute_organ → Flüt orgu; ear_notes_in_row → Art arda notalar
- Store listing: Perde izi → Ses izi. Glossary: Pitch trail = Ses izi.

For a native speaker to look at:
- TalkBack action labels are bare verb stems; infinitives may read better after "… için iki kez dokunun".
- tuner_lower_by "Düşür" reads like a button.
- "Ses izi" (new); "Ses eğrisi" is another option.
- Interval signs m2, M2, P4 vs the Turkish k2, B2, T4.
- metronome_sound_cowbell "İnek çanı": drummers say "cowbell".
- Strum = "vur" gives a rather physical "Bir akora vur".

## Polish (pl): 20 of 553 changed

- Meaning. tuner_hint_playing, tuner_string_sounding, tuner_string_locked_sounding: Gra → Brzmi
- Meaning. tuner_lowered_none: Wcale → Brak; practice_capo_sounds_in → Tonacja: %1$s
- Wording. ear_choices → Zakres pytań; metronome_beats_in_bar → Metrum; practice_drone_play: Graj → Włącz burdon
- Wording. settings_theme_system → Jak w systemie; settings_show_tune_hint_summary: Mówi → Pokazuje
- Wording. tuner_chord_unclear → Niewyraźny akord; trail bending up/down; mic_permission_settings_hint (Android's wording)
- Consistency. practice_chord_none, ear_chord_none: strojeniu → stroju (glossary); practice_drone_notes: Nuty → Dźwięki
- Consistency. metronome_sound_rim_click: Rimclick → Rim click
- Grammar. practice_spoken_muted → niegrana; tuner_strum_spoken_less_sure → odczyt niepewny

For a native speaker to look at:
- ear_played "Jak grane" is clumsy; "Sposób grania" is twice as long.
- Tritone "TT" vs the textbook "4zw".
- tuner_pitch_trail "Ślad wysokości" is literal; "Przebieg wysokości" may be more natural.
- Root "Pryma" vs "Podstawa".
- Chord names are textbook-heavy but consistent.
- "odczyt niepewny" is stronger than "less sure".

## Arabic (ar): 16 of 553 changed

- Meaning. ear_hold: أمسك → أطِل (sustain, not grab); settings_in_tune_effect_bold: جريء → قوي
- Meaning. practice_capo_sounds_column: الصوت → يُسمَع; mic_permission_intro → "to tune your instrument"
- Wording. tuning_editor_title_edit → تعديل الدوزان (a heading); practice_capo_lower/higher: verbs added
- Consistency. ear_chord_none, practice_chord_none: الضبط → الدوزان (glossary)
- Consistency. tuner_chord_strum, tuner_chord_next: اعزف → اضرب (strum, glossary)
- Consistency. النوتات/النوتة → النغمات/النغمة (4 texts); metronome_listening → جارٍ الاستماع…

For a native speaker to look at:
- TalkBack action labels are imperatives; verbal nouns may read better after "انقر مرّتين لـ…".
- wear_cents "two" form keeps "سنت" after the number (the signed number must stay).
- Chord types "ماجور سابعة" vs the textbook "سابعة كبيرة".
- practice_capo_sounds_column "يُسمَع" (a verb, like the Dutch "Klinkt"); "النتيجة" is an alternative.
- Tuning act mostly ضبط, once الدوزنة.
