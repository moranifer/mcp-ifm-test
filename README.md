# MCP-ifm · Feasibility test (Android)

Small test app that answers one question before full development:
does our processing reach the car through Android Auto, with Spotify, YouTube Music and Apple Music?

- **Test A · System EQ:** Android's built-in DynamicsProcessing effect attached to other apps' audio sessions
  (and optionally to the global output). Modes: OFF, BASS TEST (+12 dB lows / -12 dB highs), LOUD (+6 dB with limiter).
- **Test B · Capture:** checks whether each music app lets us capture its audio for our own full DSP. Measures level only.
- **Report:** one tap copies device info, status and log.

## Build
Push this folder to a GitHub repository. The workflow in `.github/workflows/build.yml` builds the APK and
publishes it under **Releases** as `MCP-ifm-Test.apk`. Or open the folder in Android Studio and press Run.
