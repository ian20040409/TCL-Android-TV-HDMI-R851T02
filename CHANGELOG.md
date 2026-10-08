# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Privacy & Permissions documentation detailing `READ_LOGS`, Accessibility Service, and Shizuku integration.
- Separate in-depth troubleshooting documentation (`docs/troubleshooting.md` and `docs/troubleshooting_zh.md`).
- Tested / Untested hardware compatibility matrix.
- GitHub Bug Report issue template.

### Changed
- Revised README performance claims to reflect actual runtime behavior (clarified foreground service lifecycle when Wake Guard / CEC Log Reader is active).
- Updated animation scale guidance with global system side effects and restoration commands.
- Updated Shizuku setup instructions to remove hardcoded device paths.

---

## [1.0.0] - 2026-10-07
### Added
- Native TvView hardware passthrough HDMI viewer (`HdmiViewerActivity`) bypassing `com.tcl.tv`.
- Fast numeric key (1/2/3) input switching.
- Auto-switch boot countdown with configurable delay.
- Leanback OOBE setup wizard and settings activities.
- Shizuku API integration for APK installation and package management.
- Foreground CEC log reader service (`CecLogReaderService`) for automatic HDMI wake-up routing.
- Standby wake guard and Home button redirection accessibility service (`WakeAccessibilityService`).
