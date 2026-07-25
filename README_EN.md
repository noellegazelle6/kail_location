# Kail Location

> A spatial control deck for developers and professionals — route simulation, location simulation, navigation simulation, cadence simulation, virtual location, and NFC simulation.

<p align="center">
  <a href="https://github.com/noellegazelle6/kail_location/releases">Download app</a>
  ·
  <a href="README.md">中文说明</a>
</p>

<p align="center">
  <img alt="Language" src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img alt="UI" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img alt="Architecture" src="https://img.shields.io/badge/MVVM-111827?style=for-the-badge" />
  <img alt="License" src="https://img.shields.io/badge/GPL--3.0-0F172A?style=for-the-badge" />
</p>

---

## Core Modules

| Module | Capability |
| --- | --- |
| Route Simulation | Build and replay routes for location-related debugging. |
| Location Simulation | Simulate device location in authorized environments for testing. |
| Navigation Simulation | Emulate navigation flows to inspect route, heading, and state changes. |
| Cadence Simulation | Generate cadence data for sensor-related debugging. |
| Virtual Location | Provide virtual coordinates for location logic validation. |
| NFC Simulation | NFC simulation for controlled debugging scenarios. |

---

## Download

[app](https://github.com/noellegazelle6/kail_location/releases)

---

## Documentation

- [中文说明](README.md)
- [English README](README_EN.md)
- [Developer Docs](docs/README.md) — architecture & development guides (Chinese)
  - [Full Technical Doc](docs/项目技术文档.md) — architecture, run modes, call chains, native injection, Xposed
  - [Baidu Map API Key](docs/baiduApiKey.md)

---


## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM |

More features are under active development.

---

## Usage Notice

This software is intended only for developers or professionals performing lawful, authorized, and controlled debugging or testing.

Feature requests and feedback are welcome via issues.

---

## Usage Terms

1. This software is only for developers or professionals to debug and test. It is strictly forbidden to use it for any infringing, illegal, or improper purposes, including but not limited to campus running, check-in/sign-in, ride-hailing, delivery services, or any other scenarios that may violate others' rights or break the law. You, the user, must bear all consequences and losses caused by such usage. This software and its author shall not be liable for any direct or indirect damages arising therefrom. The software reserves the right to restrict functionality for usage scenarios that are known to be infringing, harmful, or illegal.

2. All permissions required by this software must be explicitly granted by you. You should fully understand the purpose and potential risks of each system permission, and decide by yourself whether to grant them.

3. You, the user, must not perform reverse engineering, decompilation, disassembly, or repackaging and redistribution of this software. Such behavior constitutes infringement, and the author reserves the right to pursue legal responsibility.

4. You, the user, must not use DIY, cracked, or modified versions of this software downloaded from the internet, nor use injection-based cracking or patching plugins targeting this software. Modified versions or plugin-injected versions may no longer be controllable, secure, or legal. All consequences and losses arising from such actions shall be borne solely by you, the user. This software and its author shall not be liable for any direct or indirect damages arising therefrom.

5. This software itself does not contain viruses, trojans, or other malicious code that may damage user data. However, because this software can be downloaded and distributed over the network, the author cannot guarantee that copies obtained from non-official channels are free from infection, trojans, hacking tools, or other malicious code. This software and its author shall not be liable for any direct or indirect damages caused by such issues.

6. Due to the diversity and complexity of hardware and software environments, the functions provided by this software cannot be guaranteed to work correctly in all circumstances or always meet your expectations. This software and its author shall not be liable for any direct or indirect damages arising therefrom.

---

## FAQ

### 1.Search / Navigation Simulation not working

Make sure a **Baidu Map API Key** has been configured. The search and navigation features depend on the Baidu Maps SDK — you need to apply for an Access Key (AK) and fill it in the app.

See the guide: [docs/baiduApiKey.md](docs/baiduApiKey.md)

### 2.Root mode prompts for root access but fails

Solution: Install KailLocation 1.6.5 and KailLocationXposed, then start simulation in root mode.

---

## Contact

- Telegram: https://t.me/kaillocation
- Email: kailkali23143@gmail.com

---

## References and Thanks

- https://github.com/ZCShou/GoGoGo
- https://github.com/Lerist/FakeLocation
- https://github.com/ella8192/Portal
- https://github.com/Hilaver/MockGPS
- https://github.com/Acooldog/fuckschoolrun
- https://github.com/ALEX5402/NewBlackbox

---

## Support the Author

<img width="538" height="397" alt="Donate QR" src="./assets/1767779394823.png" />

```text
0x549928ea1ab2407bcba7bdde7b6a62a6e5a68e08f9bbe798ed151cef086da883
```

---

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fnoellegazelle6%2Fkail_location.svg?type=large&issueType=license)](https://app.fossa.com/projects/git%2Bgithub.com%2Fnoellegazelle6%2Fkail_location?ref=badge_large&issueType=license)
