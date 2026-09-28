# Upstream Attribution / Üst Proje Atfı

This repository preserves the complete Git history of a GitHub fork. GitLab cannot represent an external GitHub repository as a native GitLab fork, so the lineage is documented explicitly here.

Bu depo, bir GitHub fork'unun eksiksiz Git geçmişini korur. GitLab harici bir GitHub deposunu yerel GitLab fork ilişkisiyle gösteremediğinden proje soyu burada açıkça belgelenmiştir.

Ouranos and its BedrockData resources are merged into the source tree of this repository; the package is relocated to `oxy.geyser.reversion.ouranos`, upstream history is preserved, and the licenses are retained under `src/main/resources/META-INF/licenses`. Their history is preserved from oryxel1/Ouranos commit `e927ea4` and BedrockData commit `5b9a844`, with local compatibility changes applied in-tree.

Ouranos ve BedrockData kaynakları bu deponun kaynak ağacına birleştirilmiştir; paket `oxy.geyser.reversion.ouranos` altına taşınmıştır, üst proje geçmişi korunmuştur ve lisanslar `src/main/resources/META-INF/licenses` altında muhafaza edilmektedir. Geçmişleri oryxel1/Ouranos `e927ea4` ve BedrockData `5b9a844` commit'lerinden korunmuştur; yerel uyumluluk değişiklikleri ağaç içinde uygulanmıştır.

 - GitHub fork / GitHub çatalı: [siberanka/GeyserReversion-AIRemake](https://github.com/siberanka/GeyserReversion-AIRemake)
 - Immediate parent / Doğrudan üst depo: [AnarchadiaMC/GeyserReversion](https://github.com/AnarchadiaMC/GeyserReversion)
 - Original root upstream / Özgün kök proje: [oryxel1/GeyserReversion](https://github.com/oryxel1/GeyserReversion)
 - Upstream owner or organization / Üst proje sahibi veya kuruluşu: [oxy (@oryxel1)](https://github.com/oryxel1)
 - Detected upstream license / Algılanan üst proje lisansı: [GPL-3.0](https://github.com/oryxel1/GeyserReversion/blob/ouranos/LICENSE)
 - Original upstream default branch / Özgün varsayılan dal: ouranos

All existing copyright, license, notice, author, and contributor records remain in the repository and its Git history. This mirror does not claim authorship of upstream work. Later modifications remain attributable to their respective commit authors.

Depodaki ve Git geçmişindeki tüm telif, lisans, bildirim, yazar ve katkıcı kayıtları korunmuştur. Bu ayna üst projenin yazarlığını sahiplenmez; sonraki değişiklikler ilgili commit yazarlarına aittir.

## Pull request #4 attribution / 4 numaralı birleştirme isteği atfı

The modernization pull request #4 ("Modernize for Geyser 2.9.5", branch `modernize-geyser-2.9.5`) was authored by GitHub user [siberanka](https://github.com/siberanka). Its commits remain in this repository's history unchanged. Three of those commits (`382c0dd`, `bb41ac2`, `581417f`) carry the generic `BuildTools <unconfigured@null.spigotmc.org>` git identity from the contributor's local tooling; the work in them is the contributor's and is attributed to siberanka here and on the original pull request page.

4 numaralı modernizasyon birleştirme isteği ("Modernize for Geyser 2.9.5", `modernize-geyser-2.9.5` dalı) GitHub kullanıcısı [siberanka](https://github.com/siberanka) tarafından hazırlanmıştır. Commit'leri bu deponun geçmişinde değiştirilmeden durmaktadır. Bu commit'lerden üçü (`382c0dd`, `bb41ac2`, `581417f`) katkıcının yerel araçlarından gelen genel `BuildTools <unconfigured@null.spigotmc.org>` kimliğini taşımaktadır; içerdikleri çalışma katkıcıya aittir ve burada ve özgün birleştirme isteği sayfasında siberanka'ya atfedilir.

## Integrated data references / Entegre edilmiş veri referansları

These upstream projects are not part of the fork lineage; their data files are
vendored or generated into `src/main/resources/`, and their licenses ship beside
the data. Pinned revisions and checksums are in
[docs/LEGACY-DATA.md](docs/LEGACY-DATA.md); the per-component license table is
in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

| Upstream / Üst proje | Used for / Kullanım | License / Lisans |
|---|---|---|
| [pmmp/BedrockData](https://github.com/pmmp/BedrockData) (tag `bedrock-1.26.30`, commit `bdb44a48`) | `vanilla/v1001` (protocol 1001, Bedrock 1.26.30) mapping data; also the 1.9.0/1.10.0/1.11.0 inputs to the legacy converter | LGPL-3.0 repository; the per-version data directories carry their own CC0-1.0 `LICENSE` |
| [pmmp/BedrockBlockPaletteArchive](https://github.com/pmmp/BedrockBlockPaletteArchive) (now opencollab-incubator) | block palettes for 1.9/1.10/1.12, the basis of the generated `vanilla/v332`, `v340` and `v354` directories | CC0-1.0 |
| [pmmp/BedrockItemUpgradeSchema](https://github.com/pmmp/BedrockItemUpgradeSchema) (now opencollab-incubator/BedrockItemUpgradeSchema) | `schema/id_meta_upgrade_schema/`, including the vendored `0251`, `0261` and `0271` item id/meta transitions | CC0-1.0 |
| [pmmp/BedrockBlockUpgradeSchema](https://github.com/pmmp/BedrockBlockUpgradeSchema) (now opencollab-incubator/BedrockBlockUpgradeSchema) | `block_schema/` NBT upgrade schemas | CC0-1.0 |

Bu üst projeler çatal soyuna ait değildir; veri dosyaları
`src/main/resources/` altına alınmış veya üretilmiştir ve lisansları verinin
yanında bulunur. Sabitlenmiş revizyonlar ve sağlama toplamları
[docs/LEGACY-DATA.md](docs/LEGACY-DATA.md) içinde, bileşen bazlı lisans tablosu
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) içindedir.

## Upstream contributors / Üst proje katkıcıları

GitHub contributor data snapshot: 2026-08-25. [View the live contributor graph](https://github.com/oryxel1/GeyserReversion/graphs/contributors).

| Contributor / Katkıcı | GitHub profile / profili | Contributions / Katkı |
|---|---|---:|
| oryxel1 | [github.com/oryxel1](https://github.com/oryxel1) | 63 |

## License and provenance / Lisans ve kaynak

Reuse and redistribution remain governed by the upstream license linked above and by any third-party licenses already present in the repository. Fork provenance: [https://github.com/siberanka/GeyserReversion-AIRemake/forks](https://github.com/siberanka/GeyserReversion-AIRemake/forks).
