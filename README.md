<div align="center">

<p align="center">
  <!-- Drag gamenothing-launch-arc-16x9.gif (or gamenothing-launch-eye-16x9.gif) into GitHub and replace the src URL below -->
  <img width="720" alt="GameNothing Launch Animation" src="PASTE_16X9_LAUNCH_GIF_URL_HERE" />
</p>

# GameNothing

**PC gaming on Android, stripped down to what matters.**  
Nothing-inspired glass UI, high refresh rate support, built-in offline audio, Hands-Free Scrolling, and the Capri companion — merged from **GameNative**, **DroidDeck**, and my own projects.

[![Download Latest APK](https://img.shields.io/badge/DOWNLOAD_LATEST_APK-D71921?style=for-the-badge&logo=android&logoColor=white)](https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases/latest/download/GameNothing.apk)
[![All Releases](https://img.shields.io/badge/RELEASES-111111?style=for-the-badge&logo=github&logoColor=white)](https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases/latest)
[![Discord](https://img.shields.io/badge/DISCORD-111111?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/CCtN2UUECc)
[![TikTok](https://img.shields.io/badge/@LSXNOTHING-111111?style=for-the-badge&logo=tiktok&logoColor=white)](https://www.tiktok.com/@lsxnothing)
[![Gmail](https://img.shields.io/badge/EMAIL-111111?style=for-the-badge&logo=gmail&logoColor=white)](mailto:gabrielmartinslascau@gmail.com)

100% free. Zero telemetry. No paywalls. Your device, your library.

</div>

---

> **Important:** GameNothing is an independent fork — **not** classic Winlator (there is no Vortek or VirGL here). **Do not** report GameNothing bugs to the upstream GameNative repository. Open an issue here or post in the [Discord](https://discord.gg/CCtN2UUECc).

---

## Preview

<!-- Optional: drag your second 16:9 GIF (gamenothing-launch-eye-16x9.gif) or promo MP4 here -->

- **Interactive Promo (HTML):** [GameNothing 2.0 Promo](https://github.com/user-attachments/files/35552695/GameNothing.2.0.Promo.1.html)

---

## Quick Install & First-Time Setup

Never ran PC games on Android before? Follow these 4 steps:

1. **Install the APK:** Tap the big red **DOWNLOAD LATEST APK** button at the top, open `GameNothing.apk`, and allow installs from your browser/files app if Android asks.
2. **Add a Game:** Sign in to **Steam, GOG, Epic, or Amazon** — or point GameNothing to a local folder or USB drive.
3. **Copy These Starter Settings First:** Nobody knows what half these emulation settings do at first (honestly, neither do I completely), so start with the combo that works best so far:

| Setting | Recommended Starter Value |
| :--- | :--- |
| **Proton** | `Proton 10-4` |
| **VRAM** | `2 GB` |
| **DXVK** | `2.4.1 gplasync` |
| **Graphics Driver** | **Custom drivers recommended** over the built-in ones *(also test custom faux drivers if a game fights you)* |

4. **Dial In Your Phone (Pen & Paper Method):** First-time setup and troubleshooting *is* hard. Install a small game (**under 1 GB**), test combinations of **Proton 10** and **Proton 11** with different **DXVK** and **RAM** settings, and write down what works on pen and paper. Once you find your phone's sweet spot, bigger games become way easier.

---

## Performance Expectations

PS2 and 3DS are for the weak. It gets tiring seeing the internet say *"a 7 Gen 4 can only run modest 2010 games"* — surprise: it runs 2018 AAA titles like **No Man's Sky** (Ultra), **Halo: MCC**, **Subnautica: Below Zero**, **Ori and the Will of the Wisps**, and yes, **Crysis at max graphics**.

### Real Benchmark: No Man's Sky

| Device | Chip / Power | Settings & Notes | Result |
| :--- | :--- | :--- | :--- |
| **Nothing Phone (4a) Pro** | Snapdragon 7 Gen 4 (~10W) | Standard playable config (same FPS-per-watt ballpark as a 30W handheld) | **~35 FPS** |
| **Nothing Phone (4a) Pro** | Snapdragon 7 Gen 4 (~10W) | **1080p, everything on max Ultra + max FSR**, system driver, running off a **USB pen drive capped at 480 Mbps** | **10 FPS** *(insane given USB 2.0 + system driver)* |
| **Lenovo Legion Go** | Z1 Extreme (30W nominal / 41W peak) | 800p, Ultra settings, **no LSFG** | **80–90 FPS** |

### Hardware Tier Guide

| Chipset Tier | What to Expect |
| :--- | :--- |
| **Snapdragon 6s Gen 4** | Light indie PC titles (*Silksong* if lucky) |
| **Snapdragon 6 Gen 4** *(Min. Recommended)* | Lighter modern games like *Moonlighter* or *Hollow Knight* |
| **Snapdragon 7 Gen 4** | ~1/3 of a Z1 Extreme |
| **Snapdragon 8 Elite Gen 5** | Beats the Steam Deck at 720p |
| **Snapdragon 8 Gen 6** | Obliterates the Steam Deck |

> **GPU & Engine Notes:** **Adreno** gives the best experience, with **MediaTek** and **Kirin** support also included. **UE5** games are the line (pretty much the only case where the Steam Deck wins), and **DX12** support is still limited.

---

## What It Does

### Interface & Visuals
- Full Nothing-inspired dark/light design system with custom glass UI and minimal monochrome iconography.
- High refresh rate control and performance-first navigation.
- Customizable app icons, including the `GameNothing x Capri` logo suite.

### PC Gaming Engine
- Steam, GOG, Epic, Amazon, and custom local or external games.
- USB and external folder storage support for saving internal space.
- Custom graphics driver support, cloud saves, and local / Nexus mod support.
- Merged with **DroidDeck** on top of the **GameNative** core.

### Capri Companion (`"Yams"`)
- Optional AI companion running either on-device (**Local Gemma E2B** via LiteRT-LM) or via **Cloud Gemini**.
- Interactive **Capri Brain** node graph for memories, custom actions, and session logs.
- Fast 0-token local command routing for quick device actions, messaging, and music playback.
- Press **Volume Up + Volume Down** together at any time as a hardware panic cancel.
- *Real talk:* Capri is still buggy, constantly moving forward, and yes — the code is literally called `"yams"`.

<p align="center">
  <img height="300" alt="capri-all-animations-montage" src="https://github.com/user-attachments/assets/2be49974-4c8b-4c25-9224-e8964f93f735" />
  &nbsp;&nbsp;
  <img height="300" alt="capri-companion-v2-3-preview" src="https://github.com/user-attachments/assets/276b4d69-93cb-4497-8b31-6c23ee7acc2c" />
</p>

### Offline Audio & Extras
- Built-in offline **MP3 / FLAC** music player with recursive folder scanning and floating playback bubble.
- **Discord Rich Presence** integration.
- **Hands-Free Scrolling** built right in.

<p align="center">
  <img width="640" alt="Discord Rich Presence — Playing GameNothing" src="https://github.com/user-attachments/assets/5703ffbe-56ef-431c-9d8c-a70a5ae848a6" />
</p>

---

## Community & Updates

I keep this heavily integrated with the community — live updates drop in both the **Discord** (`#updates`) and our **TikTok group**. Got a feature request? Ask there: **all requests are added for free in under a week.**

- **Discord:** [discord.gg/CCtN2UUECc](https://discord.gg/CCtN2UUECc)
- **TikTok & Group:** [@lsxnothing](https://www.tiktok.com/@lsxnothing)
- **Email:** [gabrielmartinslascau@gmail.com](mailto:gabrielmartinslascau@gmail.com)

---

## Star History

<a href="https://www.star-history.com/#gabrielmartinslascau-lsxnya/GameNothing&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=gabrielmartinslascau-lsxnya/GameNothing&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=gabrielmartinslascau-lsxnya/GameNothing&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=gabrielmartinslascau-lsxnya/GameNothing&type=Date" />
  </picture>
</a>

---

## Credits & License

- Built on top of **GameNative** (`utkarshdalal` and contributors), **DroidDeck**, and the open-source emulation ecosystem behind them.
- Licensed under **GPL-3.0**. See `LICENSE` for details.

<p align="center">
  <img width="260" height="260" alt="capri-05-napping" src="https://github.com/user-attachments/assets/eb5d1bbf-7ded-40ca-81d7-f0fb7ca529c2" />
</p>
