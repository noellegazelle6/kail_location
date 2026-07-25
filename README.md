# Kail Location

> 调试者的空间坐标实验舱：路线模拟、位置模拟、导航模拟、步频模拟、虚拟定位与 NFC 模拟。

<p align="center">
  <a href="https://github.com/noellegazelle6/kail_location/releases">App 下载</a>
  ·
  <a href="README_EN.md">English README</a>
</p>

<p align="center">
  <img alt="Language" src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img alt="UI" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img alt="Architecture" src="https://img.shields.io/badge/MVVM-111827?style=for-the-badge" />
  <img alt="License" src="https://img.shields.io/badge/GPL--3.0-0F172A?style=for-the-badge" />
</p>

---

## 星舰核心能力

| 模块 | 能力 |
| --- | --- |
| 路线模拟 | 构建并回放调试路线，用于验证位置相关业务流程。 |
| 位置模拟 | 在授权环境下模拟设备位置，辅助开发与专业测试。 |
| 导航模拟 | 模拟导航过程，观察路线、方向与状态变化。 |
| 步频模拟 | 模拟步频数据，用于传感器相关场景调试。 |
| 虚拟定位 | 提供虚拟坐标能力，便于定位逻辑验证。 |
| NFC 模拟 | 面向调试场景的 NFC 模拟能力。 |

---

## 下载

[app下载](https://github.com/noellegazelle6/kail_location/releases)

---

## 文档导航

- [中文说明](README.md)
- [English README](README_EN.md)
- [开发者文档](docs/README.md) — 架构总览、开发指南
  - [项目技术文档](docs/项目技术文档.md) — 完整技术文档（小白快速上手版）：架构、运行模式、调用链、Native 注入、Xposed
  - [百度地图 API Key 申请](docs/baiduApiKey.md)

---


## 技术栈

| 层级 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 架构 | MVVM |

更多功能正在开发中。

---

## 使用说明

本软件仅供开发人员或专业人士在合法、授权、可控的环境中进行调试与测试。

如果你有功能建议或问题反馈，欢迎在 issues 中提出。

---

## 使用条例

1. 本软件仅供开发人员或专业人士调试使用，严禁用于一切侵权、侵害他人利益、违法违禁等不当行为和目的，包括但不限于用于校园跑、打卡、签到、网约车、配送服务等。因使用本软件造成的一切后果和损失须由您（用户）自行承担，本软件及本软件作者不承担由此引起的直接和间接损害责任；且本软件有权对已知存在侵权、侵害他人利益、违法违禁等不当行为和目的的使用场景做相应功能限制。

2. 本软件功能实现所依托的各项权限，都需经由您手动同意授权后才允许使用。您需提前了解设备系统各项权限的作用以及利弊，并自行决定是否授予本软件相应权限。

3. 您（用户）不得对本软件进行反向工程（Reverse engineer）、反向编译（Decompile）、反汇编（Disassemble）、二次打包发布，违者属于侵权行为，本软件作者有权追究其责任。

4. 您（用户）不得使用来源于网络的 DIY、破解等修改版本，也不得使用针对于本软件的注入型破解修改插件。修改版本和被插件注入后的程序或将不具可控性、安全性和合法性，由此行为引起的一切后果和损失须由您（用户）自行承担，本软件及本软件作者不承担由此引起的直接和间接损害责任。

5. 本软件本身保证不含任何病毒、木马等破坏用户数据的恶意代码。但是由于本软件可以通过网络等途径下载、传播，对于从非本软件作者指定站点下载的本软件，本软件作者无法保证其是否感染计算机病毒、是否隐藏有伪装的木马程序或者黑客软件等恶意代码，本软件及本软件作者不承担由此引起的直接和间接损害责任。

6. 由于计算机软硬件环境的差异性和复杂性，本软件所提供的各项功能不能保证在任何情况下都能正常执行或达到您所期望的结果，本软件及本软件作者不承担由此引起的直接和间接损害责任。

---

## 常见问题

### 1.搜索功能 / 模拟导航不能用

请检查是否已配置 **百度地图 API Key**。搜索和导航依赖百度地图 SDK，需申请并填入 AK。

参考申请教程：[docs/baiduApiKey.md](docs/baiduApiKey.md)

### 2.如果root模式提示获取root类似的

解决办法：可以安装1.6.5的kaillocation与 KailLocationXposed,然后root模式下开始模拟就可以了

---

## 联系作者

- Telegram: https://t.me/kaillocation
- Email: kailkali23143@gmail.com

---

## 参考与感谢

- https://github.com/ZCShou/GoGoGo
- https://github.com/Lerist/FakeLocation
- https://github.com/ella8192/Portal
- https://github.com/Hilaver/MockGPS
- https://github.com/Acooldog/fuckschoolrun
- https://github.com/ALEX5402/NewBlackbox

---

## 赞赏作者

<img width="538" height="397" alt="赞助二维码" src="./assets/1767779394823.png" />

```text
0x549928ea1ab2407bcba7bdde7b6a62a6e5a68e08f9bbe798ed151cef086da883
```

---

## License

本项目基于 GNU 通用公共许可证 v3.0（GPL-3.0）开源。

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fnoellegazelle6%2Fkail_location.svg?type=large&issueType=license)](https://app.fossa.com/projects/git%2Bgithub.com%2Fnoellegazelle6%2Fkail_location?ref=badge_large&issueType=license)
