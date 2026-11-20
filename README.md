# K3 KR 무선 안드로이드 오토 (USB Wi-Fi) — ev6kr-wireless-carplay 포크

> [!IMPORTANT]
> 이 포크는 [hwjang1/ev6kr-wireless-carplay](https://github.com/hwjang1/ev6kr-wireless-carplay)(국내형 EV6 무선 **카플레이**)를
> 무선 모듈(Wi-Fi 칩)이 없는 **국내형 기아 K3**(`BDPE.KOR`, 펌웨어 260902)에서 **무선 안드로이드 오토**로 쓰도록 바꾼 것입니다.
> **카플레이는 끕니다**(기능 플래그를 안드로이드 오토 무선 하나로 바꿈). EV6 카플레이를 쓰려면 원본 저장소를 쓰세요.
> 원본 README는 이 문서 [아래](#ev6-kr-무선-카플레이-usb-wi-fi)에 그대로 있습니다.

> [!WARNING]
> **모든 사용은 본인 책임입니다.** 원본의 [면책](#면책)이 그대로 적용됩니다. 한 대(K3 BD PE, 260902)에서만 시험했고,
> 아래 [상태](#상태)의 "차에서 확인 전" 항목은 PC에서 빌드·시험만 했습니다.

## 대상

| 항목 | 값 |
|---|---|
| 차 | 기아 K3 BD PE, 국내형 |
| 내비 소프트웨어 | `BDPE.KOR.0000.V039.001.260902`, onebin `bdpe_kr` |
| 플랫폼 | Telechips `tcc803x`, Android 4.4.2, `stepofspec` 110 |
| 순정 Wi-Fi 칩 | **없음** (`[Feature] Hardware(not supported), HW Variant(3)`, SDIO 장치 없음, `wlan0` 없음) |
| 데이터 USB 포트 | 1개(`4-1`, EV6와 같은 위치). 동글이 차지합니다 |
| 동글 | ipTIME **AX900MINI**(RTL8831BU, `0bda:b831`, 지금 사용), ipTIME A3000mini(RTL8812BU, `0bda:b812`, 끊김이 잦았음) |
| 필요한 것 | [NEMESIS](https://github.com/xwtk/hmg_nemesis) 1.16.7, [HMG-API](https://github.com/Open-HMG/HMG-API) 1.0.2, [usbwifi.hotspot](https://github.com/GRT47/HKG_5W_USB_Wlan) v2.0.9 |
| 폰 | 무선 Android Auto를 지원하는 안드로이드 폰 |

K3 순정 파일을 EV6 국내형 260902와 비교한 결과(차량 정보 수집 USB, `usb/carinfo/`):

- **카플레이 네이티브 라이브러리 6개는 EV6와 똑같습니다.** 레시피가 그대로 맞습니다(이 포크에서는 카플레이를 끄므로 쓰지 않음).
- DiPO·GPM·BtSettings·프레임워크 등 앱과 프레임워크 odex는 다릅니다. 그래도 EV6 훅 맵의 283개 지점 중 275개가 K3 파일에 같은 이름·시그니처로 있고, **모드가 쓰는 지점은 모두 있습니다**(나머지 8개: 모드가 쓰지 않는 `btsettings.projection_helper` 4개, 수집하지 않은 파일의 4개).
- AA 라이브러리 `libautoreceiver_infobank.so`(md5 `48ee793a…`)는 니로 하이브리드(Korea Projection 확인 차)와 같은 파일이라, AA 길안내 레시피 `aa-tbt-48ee793a`가 맞습니다.

## 상태

| 기능 | 모드 판 | 상태 |
|---|---|---|
| 무선 안드로이드 오토 연결 (화면·소리) | 0.3.1 | **차에서 확인** |
| 계기판·HUD 길안내 | 0.3.2 | **차에서 확인**(길안내 정보가 계기판으로 들어옴) |
| 한글 도로 이름 끝 글자 깨짐 고침("학의로" → "학의뀀") | 0.3.3 | 차에서 확인 전 |
| 길안내 취소 때 안드로이드 오토 강제 종료 | 0.3.3 | 원인 추정 후 수정, 차에서 확인 전 |
| 계기판 차선 표시 | 0.3.4 | 차에서 확인 전 |
| ★ 버튼 "Android Auto 알림" | 0.3.5 | 차에서 확인 전(폰의 AA가 이 키를 받는지는 폰이 정함) |
| 버벅임 요인 하나 줄임 | 0.3.6 | 차에서 확인 전 |
| ★ 알림 키 segfault 가능성 제거, DiPO USB 전원 리셋 막기 | 0.3.7 | 차에서 확인 전 |
| 간헐적 끊김 후 즉시 복구 | — | **원인 미확인.** AX900MINI로 바꾼 뒤 한 번 있었고 그 뒤 5분 주행은 문제없음 |

## 동작 원리

| 단계 | 내용 | 담당 |
|---|---|---|
| 1 | USB 동글 핫스팟(5GHz, `rtwlanN`)을 순정 무선 프로젝션 SSID·비밀번호로 띄움 | usbwifi.hotspot + `ev6kr.usbap.sync`(SSID·비밀번호를 순정 `softap.conf`에 맞춤) |
| 2 | 폰 ↔ 헤드유닛 블루투스 RFCOMM으로 무선 AA 협상, 폰에 Wi-Fi 정보(SSID·비밀번호·BSSID·호스트 IP) 전달 | 순정 GPM(`com.google.android.projection.sink`)·폰 프로젝션 앱 + **모드의 `AaWifi`** |
| 3 | 폰이 동글 핫스팟에 접속, TCP로 AA 세션 | 순정 GPM |
| 4 | 길안내를 계기판·HUD로 | AA 라이브러리 메모리 패치(`aa-tbt-48ee793a`) + 모드의 `AaTurns` |

순정이 동글 AP를 모르는 데서 생기는 두 문제를 모드가 메웁니다.

1. **AP 상태**: GPM과 폰 프로젝션 앱은 `WifiManager.isWifiApEnabled()`가 참이고 `getWifiApMode()`가 2(프로젝션 AP)일 때만 폰에 Wi-Fi 정보를 보냅니다. 순정 Wi-Fi 서비스는 동글 AP를 모르므로, 동글 인터페이스에 사설 IPv4가 있는 동안 이 두 앱 안에서만 그렇게 답합니다(Korea Projection의 `AaWifi`와 같은 방식, 신호는 동글 주소).
2. **호스트 IP·BSSID**: GPM의 `sink.wireless.a.dS()`·`dT()`는 네트워크 인터페이스를 돌며 이름이 `wlan0`인 것만 씁니다. **이름 비교는 `getName()`이 아니라 `getDisplayName()`**(odex의 vtable 11번, K3 `core.odex`로 계산)입니다. 두 메서드가 도는 동안 그 스레드에서만, AP 주소를 가진 `rtwlanN`이 두 이름 모두 `wlan0`으로 답하게 합니다. 진짜 `wlan0`이 있으면 바꾸지 않습니다.

## 설치

1. **Korea Projection을 지웁니다**(설치되어 있으면). 그 패키지의 무선 모드가 같은 지점을 훅합니다. NEMESIS Manager → My Packages → Uninstall → 재부팅.
2. **HMG-API 1.0.2 설치**, HMG-API 앱 → Settings → Hook map → *Map this car from its files* → *Check the map on this car* → 재부팅.
3. **패키지 설치**: 직접 빌드한 `dist/ev6kr-wireless-carplay-0.2.0-mod0.3.7-nemesis.zip`의 내용물과 usbwifi.hotspot의 `packages/usbwifi.hotspot.zip`을 USB 최상위에 둡니다. 하나의 목록으로 묶은 `NEMESIS.data` 예:
   ```
   [nemesis]
   version=1.1
   type=list

   [package.0]
   path=packages/usbwifi.hotspot.zip
   name=USB WiFi Hotspot Enabler

   [package.1]
   path=packages/ev6kr.usbap.sync.zip
   name=EV6 KR CarPlay Hotspot Sync

   [package.2]
   path=packages/hmgapi.wireless-carplay-kr.zip
   name=Wireless Android Auto (K3 KR, USB Wi-Fi)
   ```
   Package Store에서 **USB WiFi Hotspot Enabler → ① EV6 KR CarPlay Hotspot Sync → ② Wireless Android Auto (K3 KR)** 순서로 설치합니다.
   이전 판이 있으면 ②는 업데이트로 나옵니다(같은 모드 id, 같은 서명 키일 때).
4. USB를 빼고 **동글을 꽂은 채 재부팅**. 켜고 **3분 넘게** 둡니다(짧게 여러 번 재부팅하면 HMG-API 부팅 보호가 훅을 끕니다).
5. `[MAP]` 3초 → usbwifi 대시보드에서 **AP 모드, 5GHz**, SSID가 `Kia_…`(동기화 패키지가 맞춘 순정 값)인지 확인. usbwifi의 자동 업데이트는 끄는 것을 권합니다.

## 폰 연결

1. 폰의 Android Auto 설정에서 **무선 Android Auto**를 켜고 Wi-Fi를 켭니다.
2. **처음 한 번은** 내비와 폰 양쪽에서 블루투스 등록을 지우고 새로 페어링합니다.
3. 내비에 "무선 Android Auto 사용" 안내가 뜨면 **사용**. 30초~1분 뒤 AA 화면이 뜹니다. 핫스팟 비밀번호는 직접 입력하지 않습니다.
4. 시동 뒤 저절로 붙지 않으면 폰 블루투스를 껐다 켭니다(Korea Projection의 자동 연결 보정 `AaReady`는 K3 GPM 구조가 달라 넣지 않았습니다: K3 GPM에는 `mWLDeviceMap`이 없음).

★ 버튼: 차량 설정 → 버튼 설정 → ★ 버튼 → **"Android Auto 알림"**. 고른 뒤 AA를 다시 연결해야 합니다(알림 키는 연결할 때 폰에 알림).

## 모드 설정 (HMG-API 앱)

이 포크에서 추가했거나 기본값을 바꾼 것만 적습니다. 나머지는 원본과 같습니다.

| 키 | 기본 | 내용 |
|---|---|---|
| `aa_wifi` | 켬 | 무선 AA가 동글 AP를 쓰게 함(`AaWifi`) |
| `aa_keys` | 켬 | ★ 버튼 "Android Auto 알림"(`AaKeys`) |
| `tbt` | 켬 | 계기판·HUD 길안내(AA 라이브러리 패치 포함) |
| `usb_drives` | 켬 | USB 메모리·허브, **그리고 Wi-Fi 동글**이 유선 AA를 시작하지 않게 |
| `bridge`, `audio`, `now_playing`, `early_start`, `tbt_standard`, `cluster_artist`, `call_state`, `call_key_phone`, `siri_key`, `audio_resume` | **끔** | 카플레이 전용(원본은 켬) |

## 바꾼 내용 (모드 0.2.9 → 0.3.7)

| 판 | 바꾼 것 | 파일 |
|---|---|---|
| — | 차종 추가: `BDPE.KOR.`(모드·동기화 패키지·점검 USB), K3 패치 세트 `bdpe_kr`(순정 파일 14개 md5) | `mod/mod.json`, `mod/patches.json`, `mod/patches/bdpe_kr/`, `apsync/NEMESIS.data`, `usb/preflight/NEMESIS.sh` |
| — | 차량 정보 수집 USB가 패치 세트·훅 맵 파일 17개의 md5도 적고, `CARINFO-FILES`가 있으면 EV6와 다른 것을 복사 | `usb/carinfo/NEMESIS.sh` |
| 0.3.0 | 기능 플래그를 `androidauto_wireless`로 바꿈(카플레이 끔), 카플레이 설정 기본 끔, `AaWifi` 추가, AA 길안내 레시피 추가, Wi-Fi 동글 USB id를 유선 AA 제외 목록에 | `mod/mod.json`, `mod/settings.json`, `AaWifi.java`, `Wireless.java`, `mod/recipes/aa-tbt-48ee793a.recipe` |
| 0.3.1 | GPM이 `getDisplayName()`으로 `wlan0`을 비교하는 것을 찾아 그 메서드도 훅("Failed to find host IP" 해결) | `AaWifi.java` |
| 0.3.2 | AA 라이브러리 패치를 실제로 요청(`mod.nativePatch(app, "libautoreceiver_infobank.so")`). 원본은 EV6 국내형에 맞는 레시피가 없어 이 호출을 뺐고, `mod.json`에 레시피를 적는 것만으로는 적용되지 않음 | `Wireless.java` |
| 0.3.3 | Korea Projection의 고친 레시피로 교체: UTF-8 자르기 함수가 이름 끝의 이어지는 바이트만 지워 마지막 한글이 깨지고(EB A1 9C → EB = "뀀"), 빈 이름이면 문자열 앞 메모리를 읽음. 로그 수집 USB가 tombstone·DropBox 앱 크래시 기록을 `EV6KR-TRYLOG/crashes/`에 모음 | `mod/recipes/aa-tbt-48ee793a.recipe`, `usb/trylog/NEMESIS.sh` |
| 0.3.4 | 폰이 안내하는 동안 순정 내비의 `update_lane_info`도 막음(빈 차로 프레임이 폰의 차로를 지우던 것, Korea Projection 2.3.0과 같음). 차로 수신 로그 `Android Auto: lanes to the cluster: N` | `Wireless.java`, `mod/mod.json`, `AaTurns.java` |
| 0.3.5 | ★ 버튼 "Android Auto 알림": 연결 때 키 목록(`InputSource.registerKeyCodes`)에 `KEYCODE_NOTIFICATION`(83) 추가, ★ 방송을 받으면 `sendKeyEvent(83)`. ★ 목록의 카플레이 항목 제거 | `AaKeys.java`, `mod/mod.json`, `mod/settings.json` |
| 0.3.6 | `AaWifi`의 동글 인터페이스 확인을 모드 스레드로 옮김(앱이 AP 상태를 물을 때 기다리지 않게) | `AaWifi.java` |
| 0.3.7 | `InputSource.shutdown()` 전에 기억한 연결을 지우고 키 보내기와 같은 잠금으로 묶음(끊긴 연결의 네이티브 객체에 키를 보내 GPM이 죽을 수 있던 것). DiPO의 USB 전원 리셋 막기를 카플레이 설정과 상관없이 켬 | `AaKeys.java`, `dipo/Dipo.java` |

## 로그

- HMG-API 로그(앱의 로그 화면 또는 `/data/hmgapi/logs/hmgapi/log.log`)의 `[wireless-carplay-kr] Android Auto:` 줄
  - `AP hooks in … 5 of 5`, `host IP and BSSID from the dongle's AP interface in …`: 훅이 걸림
  - `getWifiApMode answered for the dongle's AP on rtwlanN`: 무선 AA가 동글 AP를 씀
  - `host interface lookup failed on …`: 호스트 IP·BSSID를 못 찾음(0.3.0의 증상)
  - `native patch libautoreceiver_infobank.so: …`, `turn-by-turn to the cluster for this connection`: 길안내
  - `lanes to the cluster: N`(logcat): 폰이 차로를 보냄
  - `notification key offered to the phone`, `★ key: notification key sent`: ★ 알림 키
  - `USB power reset …`: DiPO가 USB 포트 전원을 껐다 켜려 함(동글이 있으면 막음)
- 문제가 생기면 동글을 빼고 로그 수집 USB(`dist/ev6kr-trylog-usb.zip`)를 꽂습니다. 간헐적인 문제는 먼저 `EV6KR-DEBUG-ON`으로 진단 로그를 켜 두고 다시 겪은 뒤 수집합니다(로그는 내비 `/data`에 쌓이므로 USB는 켤 때와 수집할 때만 꽂으면 됨). 수집본에는 휴대폰 이름·주소가 들어갈 수 있으니 공개하지 마세요.

## 알려진 제약

- **간헐적 끊김**: 원인 미확인. 후보는 Wi-Fi 링크(usbwifi.hotspot의 hostapd는 5GHz HT20 최소 설정), 핫스팟 재시작(usbwifi 데몬·동기화 패키지·자동 업데이트), AA 세션 재연결. 진단 로그로 가립니다.
- **버벅임**: 폰의 AA 개발자 설정에서 동영상 해상도를 낮춰 보면 영상 대역폭·디코딩 문제인지 가릴 수 있습니다. GPM은 800x480·1280x720·1920x1080, 30·60fps 중에서 폰과 협상합니다(`protocol.VideoSink`).
- **길안내 정보는 폰의 지도 앱이 보낼 때만** 나옵니다. 국내에서는 구글 지도가 자동차 길안내를 하지 않습니다.
- **AA 화면의 개발자 디버그 표시 "permits: disable"**: 폰 쪽 표시라 뜻은 확인하지 못했습니다. GPM이 폰에 보내는 주행 상태(`DrivingStatus`)와 관련된 것으로 보입니다.
- **카플레이**: 꺼져 있습니다. 같은 모드로 카플레이를 다시 켜려면 기능 플래그를 `carplay_wireless`로 되돌리고 카플레이 설정을 켜야 합니다(K3에서 카플레이는 시험하지 않음).
- **훅 맵**: 저장소에 K3 맵은 없습니다. 차에서 HMG-API 앱으로 만듭니다.
- **EV6 국내형**: 이 포크의 모드는 EV6에서도 설치되지만 카플레이가 꺼지고, EV6의 AA 라이브러리(`c4fbccdf…`)에는 맞는 길안내 레시피가 없습니다.

## 빌드

원본 [빌드](#빌드-1)와 같습니다. macOS에서 쓴 환경:

```sh
brew install openjdk@17                       # keg-only: PATH 앞에 /opt/homebrew/opt/openjdk@17/bin
brew install --cask android-commandlinetools  # SDK: /opt/homebrew/share/android-commandlinetools
sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools "platforms;android-34" "build-tools;34.0.0"
git clone https://github.com/Open-HMG/HMG-API.git ../HMG-API && git -C ../HMG-API checkout v1.0.2
sh tools/build.sh keygen   # 처음 한 번: ~/hmg-private/keys/ev6kr-mods.key (저장소 밖, 백업 필수)
sh tools/build.sh          # dist/
```

서명 키가 원본과 다르므로, 원본 릴리스의 모드가 설치된 차에서는 그 모드를 먼저 지워야 이 포크의 모드가 설치됩니다.

## 참고와 출처

- 무선 AA를 국내 Gen5W에서 띄운 방식과 AA 길안내 수정은 [Korea Projection](https://github.com/StopDragon/korea-projection)과 그 HMG-API 포크([StopDragon/HMG-API](https://github.com/StopDragon/HMG-API) `korea-build`, 커밋 `b573ac6`, AGPL-3.0)를 참고·옮겼습니다: `AaWifi`의 WifiManager 훅, 고친 `aa-tbt-48ee793a` 레시피, `update_lane_info` 막기. 자세한 출처는 [NOTICE.md](NOTICE.md).
- 라이선스는 원본과 같이 AGPL-3.0입니다. 제조사 앱·라이브러리는 포함하지 않습니다(md5 목록과 레시피만).

---

# EV6 KR 무선 카플레이 (USB Wi-Fi)

무선 모듈(Wi-Fi 칩)이 없는 **국내형 기아 EV6**(`CVEV.KOR`, 펌웨어 260902, Gen5W 표준형 내비)에서
**USB 무선랜카드 핫스팟**으로 **무선 애플 카플레이**를 쓰기 위한 패키지 모음입니다.
핫스팟은 두 가지 구성 중 하나로 띄웁니다.
- **기존 구성**: NEMESIS 패키지 `usbwifi.hotspot` + 동기화 패키지 `ev6kr.usbap.sync`
- **독립 구성**: 이 저장소의 HMG-API 모드 `usb-hotspot-kr` 하나(usbwifi.hotspot 불필요, [hotspot/README.md](hotspot/README.md))
HMG-API의 `wireless-carplay-aa` 모드(미국·캐나다 EV6/아이오닉 5용)를 국내형 EV6와 USB 동글 핫스팟에 맞게 옮겼습니다.
다른 차종은 [차량 정보 수집 안내](docs/CARINFO.md)대로 정보를 모아 이슈로 올려 주시면, 대상이 되는지 검토해 답변합니다.

> [!WARNING]
> **모든 사용은 본인 책임입니다.** 이 저장소는 현대자동차·기아·현대모비스·Telechips·Apple과 관계없는 비공식 개인 프로젝트이며, 어떤 보증도 하지 않습니다.
> 설치하면 헤드유닛의 시스템 설정을 바꾸고 순정 라이브러리를 메모리에서 패치합니다. 함께 설치하는 HMG-API는 `/system/bin/app_process`를 교체합니다.
> 이로 인해 생기는 **헤드유닛·차량의 고장, 부팅 불가, 순정 기능 오작동, 데이터 손실, 보증·무상수리 거부, 법규 위반 등 모든 결과는 설치한 사람이 책임집니다.** 작성자와 기여자는 책임지지 않습니다.
> 설치 전에 [면책](#면책)과 [의존 프로젝트](#의존-프로젝트와-제약)를 읽어 주세요.

> **상태 (2026-11-15): v0.2.9 정식 릴리스. 기존 구성(`usbwifi.hotspot` + `ev6kr.usbap.sync` + 카플레이 모드)과 ipTIME AX900UA·AX900MINI(RTL8831BU), A3000mini(RTL8812BU) 동글로 무선 카플레이가 동작합니다.**
> - **차에서 확인:** 시동 뒤 자동 연결, 24분·39분 주행 중 끊김 없음, 화면과 소리, 차 LTE를 공유하지 않는 순정 방식(iPhone은 자기 셀룰러 사용)([31](docs/ANALYSIS.md)·[35](docs/ANALYSIS.md)·[42절](docs/ANALYSIS.md)).
> - **0.2.9 (카플레이 모드만, [48절](docs/ANALYSIS.md)):** 통화가 없을 때 통화 버튼이 카플레이 전화를 열지 않던 세션이 있던 문제를 고쳤습니다.
>   - 아이폰이 카플레이 연결 뒤에도 한동안(길게는 2분) 블루투스로 통신하는 동안에는 모드가 아이폰의 통화 상태를 받지 못합니다. 0.2.8까지는 그동안 버튼을 순정 처리에 넘겼고, 순정은 통화가 없으면 아무것도 하지 않았습니다.
>   - 이제 카플레이 자체의 통화 모드(벨이 울릴 때부터 켜짐)로 통화 여부를 판단해, 통화가 없으면 언제든 카플레이 전화를 엽니다. 벨이 울릴 때와 통화 중에는 지금처럼 순정이 받기·끊기를 처리합니다.
>   - 차에서 확인: 0.2.9에서 통화 버튼으로 카플레이 전화가 열림. 통화 상태를 받기 전 구간에서 여는 경로는 로그로 아직 확인하지 못했습니다.
> - **0.2.8 (카플레이 모드만, [47절](docs/ANALYSIS.md)):** 카플레이 길안내로 **AR HUD 화살표**가 움직입니다.
>   - 교차로 약 450 m 안에서 회전을 순정 내비 형식(회전 종류·각도·거리 막대)으로 보냅니다. 설정 `tbt_standard`, 기본 켬.
>   - 차에서 확인: 우회전, 좌회전, 약간 좌회전, 유턴(네이버지도, T맵). 갈림길, 고속도로 출구, 회전교차로, 도착은 아직 확인하지 못했습니다.
>   - 형식은 HMG-API 무선 모드의 안드로이드 오토용(AwsomeFox, 미국 아이오닉 5)을 따랐고, 카플레이용 변환을 새로 만들었습니다. 0.2.7은 시험판이라 배포하지 않았습니다.
> - **0.2.6 (카플레이 모드만, [46절](docs/ANALYSIS.md)):** HUD 곡 정보에 아티스트가 두 번 나오던 문제 수정(0.2.1부터, 오디오를 껐다 켤 때 등). HUD에는 카플레이 곡의 제목·아티스트가 나오지만, HUD의 그림 칸은 카플레이일 때 비어 있습니다(순정이 카플레이용 그림을 두지 않음).
> - **0.2.3 (카플레이 모드만, [45절](docs/ANALYSIS.md)):**
>   - ★ 버튼 설정에 **"Apple CarPlay 지도"** 추가. 카플레이 안이든 밖이든 누르면 카플레이 지도 화면으로 바로 갑니다(어느 지도 앱을 보여줄지는 iPhone이 정함)
>   - 다른 폰에 블루투스 전화가 와 있으면 통화 버튼을 순정 처리에 맡김(카플레이 전화를 열지 않음)
>   - 아이폰이 두 대일 때 카플레이 중인 폰을 다른 폰이 밀어내지 않고, 카플레이 폰이 내리면 다른 아이폰으로 이어 연결(차에서 아직 확인 전)
> - **0.2.1 (카플레이 모드만):** 핸들 버튼과 계기판을 카플레이에 맞췄습니다([43절](docs/ANALYSIS.md)). 모두 HMG-API 앱의 모드 설정에서 하나씩 끌 수 있습니다.
>   - 계기판 곡 팝업: "아티스트 - 제목"
>   - 통화 버튼: 벨이 울릴 때 짧게 받기·길게 거절, 통화 중 짧게 끊기(전에는 보류가 됨), 통화가 없으면 카플레이 전화 열기. 무선에서도 통화 상태를 받아 순정 통화 팝업이 뜹니다
>   - 음성 버튼: 카플레이 연결 중에는 짧게 눌러도 시리(길게 누르면 누르는 동안 말하기)
>   - 시동을 다시 켰을 때 카플레이 음악이 이어서 재생되게(차에서 아직 확인 전)
> - **0.2.0:** 차에서 확인한 0.1.13과 코드가 같고, 진단 로그만 기본으로 꺼 두었습니다([42절](docs/ANALYSIS.md)). 로그 파일은 크기와 개수가 제한되고, `/data` 공간이 부족하면 진단 로그가 저절로 멈춥니다([41절](docs/ANALYSIS.md)).
> - **모드 기능도 0.1.13까지 차에서 확인:**
>   - 클러스터·HUD 길안내: T맵(0.1.11의 아이콘 보강 포함), 네이버지도(앱 CarPlay 설정의 HUD 스위치를 켠 뒤, [36절](docs/ANALYSIS.md))
>   - 블루투스가 준비되면 바로 카플레이 시작(0.1.12, [37절](docs/ANALYSIS.md))
>   - 무선에서도 곡 정보 표시, 다음·이전 곡 포함(0.1.13, [39절](docs/ANALYSIS.md))
>   - ★ 키로 카플레이 열기
> - RTL8812BU 동글 한 개는 송신부 불량으로 링크가 계속 끊겼습니다([29절](docs/ANALYSIS.md)). 같은 칩의 ipTIME A3000mini는 정상 동작합니다([49절](docs/ANALYSIS.md)). 독립 구성(`usb-hotspot-kr`)은 보류 중입니다.
>
> 아래 [알려진 제약](#알려진-제약과-미확인-사항)을 먼저 읽어 주세요.

## 다운로드

[릴리스 페이지](https://github.com/hwjang1/ev6kr-wireless-carplay/releases)에서 받습니다. 지금 판은 **v0.2.9**입니다. 아래 링크는 늘 최신 릴리스를 받습니다.

| 파일 | 용도 |
|---|---|
| [`ev6kr-wireless-carplay-nemesis.zip`](https://github.com/hwjang1/ev6kr-wireless-carplay/releases/latest/download/ev6kr-wireless-carplay-nemesis.zip) | **설치용.** USB 최상위에 풀고 꽂으면 NEMESIS에 두 패키지가 나옵니다([설치 순서](#설치-순서) 5단계). 같은 파일이 버전이 붙은 이름(`ev6kr-wireless-carplay-0.2.0-mod0.2.9-nemesis.zip`)으로도 있습니다 |
| [`ev6kr-preflight-usb.zip`](https://github.com/hwjang1/ev6kr-wireless-carplay/releases/latest/download/ev6kr-preflight-usb.zip) | 설치 전 점검 USB(1단계) |
| [`ev6kr-trylog-usb.zip`](https://github.com/hwjang1/ev6kr-wireless-carplay/releases/latest/download/ev6kr-trylog-usb.zip) | 문제가 생겼을 때 로그 수집 USB(8단계) |
| [`ev6kr-carinfo-usb.zip`](https://github.com/hwjang1/ev6kr-wireless-carplay/releases/latest/download/ev6kr-carinfo-usb.zip) | 다른 차종 지원 요청용 차량 정보 수집 USB([안내](docs/CARINFO.md)) |
| [`SHA256SUMS.txt`](https://github.com/hwjang1/ev6kr-wireless-carplay/releases/latest/download/SHA256SUMS.txt) | 받은 파일 확인용: `shasum -a 256 -c SHA256SUMS.txt` |

USB 메모리는 FAT32나 exFAT로 포맷하고, zip은 **안의 내용물**을 USB 최상위에 풉니다. `packages/` 안의 zip은 풀지 마세요.

이 저장소 밖에서 따로 받아 설치할 것: [NEMESIS](https://github.com/xwtk/hmg_nemesis)(1.16.7에서 확인), [HMG-API](https://github.com/Open-HMG/HMG-API) 1.0.2, [usbwifi.hotspot](https://github.com/GRT47/HKG_5W_USB_Wlan), 그리고 USB 무선랜 동글(확인된 것은 ipTIME AX900UA, AX900MINI, A3000mini). 조건과 제약은 [의존 프로젝트와 제약](#의존-프로젝트와-제약)을 보세요.

## 동작 원리

무선 카플레이 연결 순서와 각 단계를 맡는 부분:

| 단계 | 내용 | 담당 |
|---|---|---|
| 1 | iPhone ↔ 헤드유닛 블루투스, iAP2(SPP) 세션 | **브리지**(이 모드, DiPO 안에서 실행): 순정에는 블루투스 iAP2 데이터를 iAP2 서버로 넘기는 부분이 없음 |
| 2 | iAP2로 iPhone에 Wi-Fi 정보(SSID·비밀번호) 전달 | 순정 iAP2 서버. 순정 설정 `ConnectivitySSID`/`ConnectivityPSK`를 보냄. `persist.sys.iap2.wifi=t` 필요(이 모드가 설정) |
| 3 | iPhone이 헤드유닛 핫스팟 접속 | **USB 동글 핫스팟**. **`ev6kr.usbap.sync`**가 순정 카플레이 SSID·비밀번호·5GHz 채널로 맞춰 둠 |
| 4 | Wi-Fi로 카플레이 세션 | 순정 카플레이 라이브러리(국내 V039는 Wi-Fi 세션 자체 지원). 인터페이스 이름 판별 1곳만 1바이트 메모리 패치(이름표에 없는 인터페이스 = 동글의 `rtwlanN`을 Wi-Fi로 판정) |

자세한 분석 근거는 [docs/ANALYSIS.md](docs/ANALYSIS.md)에 있습니다.

## 구성

| 경로 | 내용 |
|---|---|
| [apsync/](apsync/) | NEMESIS 패키지 **`ev6kr.usbap.sync`**: USB 핫스팟(`usbwifi.hotspot`의 `config/hotspot.conf`)의 `AP_SSID`·`AP_PASS`·`AP_HW_MODE`·`AP_CHANNEL`을 순정 카플레이 핫스팟 설정(`/data/misc/wifi/softap.conf`)에 맞춤. 30초마다 확인, 바뀌면 USB 핫스팟 재시작. 제거 시 원래 값 복원 |
| [hotspot/](hotspot/) | HMG-API 모드 **`usb-hotspot-kr`**(독립 구성): 우리 드라이버를 올리고, 순정 hostapd·dnsmasq로 순정 카플레이 SSID·비밀번호의 핫스팟을 띄움. 동글 분리·재연결 감시, 진단·튜닝 포함. usbwifi.hotspot이 설치돼 있으면 꺼져 있음 |
| [driver/](driver/) | 동글 드라이버(RTL8812BU/8822BU, morrownr v5.13.1) 빌드와 Gen5W 수정(**GPL-2.0**). 차량의 `/proc/config.gz`로 빌드 |
| [shared/rtwlan.sh](shared/rtwlan.sh) | 두 구성이 함께 쓰는 동글 쪽 코드: 수신 backlog, netsample, 디버그 logcat, 송신 경로·전력 조정 |
| [mod/](mod/) | HMG-API 모드 **`wireless-carplay-kr`**(NEMESIS 패키지 `hmgapi.wireless-carplay-kr`): 블루투스 브리지, 카플레이 기능 플래그·설정값, 클러스터/HUD 길안내, 무선 오디오 포맷, 인터페이스 이름 패치, 계기판 곡 정보, 핸들 통화·음성 버튼 |
| [maps/kia/ev6/kor/260902/](maps/kia/ev6/kor/260902/) | 국내형 EV6 260902의 HMG-API 훅 맵(이름만, 앱 코드 없음). 모드가 쓰는 지점 전부 매핑됨(0.2.1에서 system_server의 `framework.hkmc_loader` 두 지점 추가) |
| [usb/preflight/](usb/preflight/) | 설치 전 점검 USB(읽기 전용): `EV6KR-PREFLIGHT.txt` |
| [usb/driver-reset/](usb/driver-reset/) | usbwifi.hotspot을 원래 드라이버(v5.8.7.1)로 되돌리는 USB(덮어쓰기 파일 제거 + 지금 드라이버 보고), 실행 뒤 재부팅 |
| [usb/wifi-check/](usb/wifi-check/) | 순정 Wi-Fi를 한 번 켜서 내장 Wi-Fi 모듈이 있는지 확인하는 USB(시험용, 결과는 토스트와 `EV6KR-WIFICHECK.txt`, [ANALYSIS 38절](docs/ANALYSIS.md)). 블루투스·USB가 잠시 끊길 수 있어 끝나면 헤드유닛 재시작 |
| [usb/trylog/](usb/trylog/) | 연결 시도 뒤 로그 수집 USB: `EV6KR-TRYLOG/`(HMG-API 로그, 동기화·진단 로그, USB 핫스팟 로그, 엔진 상태). 휴대폰 이름·주소가 들어갈 수 있어 공개하지 마세요 |
| [usb/carinfo/](usb/carinfo/) | 다른 차종으로 옮길 수 있는지 보는 정보 수집 USB(읽기 전용, NEMESIS만 필요): `CARINFO-<차종>-<지역>.txt`에 플랫폼, 순정 Wi-Fi 모듈 유무, 카플레이 관련 파일이 EV6 국내형 260902와 같은지(md5)를 적음. 빈 파일 `CARINFO-FILES`를 두면 다른 파일을 분석용으로 복사(제조사 파일이라 공개 금지). 사용법은 [docs/CARINFO.md](docs/CARINFO.md) |
| [tools/build.sh](tools/build.sh) | 빌드: `dist/`에 NEMESIS 패키지·서명된 모드 생성(`build/`·`dist/`는 저장소에 올리지 않음, 아래 [빌드](#빌드)) |
| [tests/apsync-test.sh](tests/apsync-test.sh) | `ev6kr.usbap.sync` 차량 밖 시험 |
| [tests/hotspot-test.sh](tests/hotspot-test.sh) | `usb-hotspot-kr` 서비스 차량 밖 시험 |
| [tests/nowplaying-test.sh](tests/nowplaying-test.sh), [tests/clustertext-test.sh](tests/clustertext-test.sh) | 카플레이 모드의 곡 정보·통화 상태 시작 시점, 계기판 곡 문구 시험(JDK 필요) |

## 의존 프로젝트와 제약

이 저장소만으로는 동작하지 않습니다. 아래 프로젝트가 필요하며, 각 프로젝트의 라이선스·동작·업데이트가 이 저장소의 배포 조건과 동작을 제약합니다.

| 프로젝트 | 쓰는 곳 | 필요한 때 | 라이선스·제약 |
|---|---|---|---|
| [NEMESIS](https://github.com/xwtk/hmg_nemesis) (xwtk) 1.16.7에서 확인 | 모든 패키지의 설치, 백그라운드 서비스 실행 | 차에 먼저 설치 | AGPL-3.0. 이 저장소는 NEMESIS를 설치하지 않습니다 |
| [HMG-API](https://github.com/Open-HMG/HMG-API) 1.0.2 | 카플레이 모드와 `usb-hotspot-kr`의 엔진(훅, 기능 플래그, 네이티브 패치). 빌드할 때 옆 폴더의 소스(`HMGAPI_DIR`)를 읽음 | 차에 설치, 빌드 | AGPL-3.0. 이 저장소는 HMG-API의 파생물이라 **AGPL-3.0을 따릅니다**(배포하거나 네트워크로 제공하면 소스 공개). 국내형은 HMG-API 공식 지원 차종이 아니며, 설치 시 `app_process`를 교체합니다 |
| HMG-API `wireless-carplay-aa` 2.1.0 (VysseCodes) | [mod/](mod/)의 바탕 코드 | 빌드 | AGPL-3.0. 바꾼 부분은 [NOTICE.md](NOTICE.md) |
| [usbwifi.hotspot](https://github.com/GRT47/HKG_5W_USB_Wlan) (GRT47, HKG_5W_USB_Wlan) | 기존 구성의 동글 드라이버와 핫스팟 | 기존 구성에서 차에 설치 | **라이선스 파일이 없어** 코드를 복사하지 않습니다. `ev6kr.usbap.sync`는 실행 중에 그 설정 파일의 키 몇 개만 바꿉니다. `AUTO_UPDATE=1`이면 LTE로 스스로 업데이트되어 동작이 바뀔 수 있습니다(2026-11-08 하루에 세 번 바뀜) |
| [morrownr/88x2bu-20210702](https://github.com/morrownr/88x2bu-20210702) (Realtek v5.13.1) | 독립 구성의 동글 드라이버([driver/](driver/)) | 빌드 | GPL-2.0. [driver/](driver/)와 빌드된 `.ko`는 GPL-2.0이며, `.ko`를 배포할 때는 소스와 패치를 함께 둡니다 |
| AOSP `arm-eabi-4.7`, Linux 3.18.24(kernel.org) | 드라이버 빌드 툴체인과 커널 소스 | 빌드(Docker) | 첫 빌드 때 내려받음 |
| JDK, Android SDK(`android.jar`, `d8`), python3, zip | 패키지 빌드 | 빌드 | — |
| 순정 펌웨어 `CVEV.KOR.0000.V039.001.260902` | 모드·레시피·훅 맵의 대상 | 차 | 이 펌웨어 전용입니다. 레시피는 순정 바이트의 해시를 확인하므로, 서비스센터 업데이트 등으로 펌웨어가 바뀌면 패치가 적용되지 않습니다. 제조사 파일은 저장소에 없습니다 |
| USB 무선랜 동글(5GHz AP 지원 Realtek) | 핫스팟 | 차 | 기존 구성: usbwifi.hotspot이 지원하는 RTL8812BU/8822BU/8831BU/8851BU. 독립 구성: RTL8812BU/8822BU만. 동작을 확인한 동글: ipTIME AX900UA·AX900MINI(RTL8831BU), ipTIME A3000mini(RTL8812BU) ([49절](docs/ANALYSIS.md)) |
| HMG Leafia Plus(`wireless.carplay.aa`) | — | **함께 설치 불가** | HMG-API와 같이 설치되지 않으므로 먼저 제거합니다 |

## 설치 순서

준비: NEMESIS 설치됨, `usbwifi.hotspot` 설치·**AP(핫스팟) 모드**·동글 연결, **5GHz**.
동글은 동작을 확인한 **ipTIME AX900UA**·**AX900MINI**(RTL8831BU)나 **ipTIME A3000mini**(RTL8812BU)를 권장합니다. 처음에 CD-ROM으로 잡히는 동글(AX900UA 등)은 ①이 CD를 꺼내 무선랜으로 바꿉니다(약 3초). AX900MINI와 A3000mini는 이 단계가 없습니다.

1. **점검**: 릴리스의 `ev6kr-preflight-usb.zip`을 USB 최상위에 풀고(또는 [usb/preflight/](usb/preflight/)의 두 파일을 복사) → 꽂고 "예" → `EV6KR-PREFLIGHT.txt`의 `[STOP]` 항목을 먼저 해결.
2. **Leafia 제거**: NEMESIS Manager → My Packages → *HMG Leafia Plus - Wireless CarPlay/AA*(`wireless.carplay.aa`) → Uninstall → 헤드유닛 재시작.
   HMG-API는 Leafia와 함께 설치되지 않습니다.
3. **HMG-API 설치**: [Open-HMG/HMG-API](https://github.com/Open-HMG/HMG-API) 1.0.2의 NEMESIS용 zip을 USB에 풀어 설치 → 재시작.
   - 점검에서 `app_process`가 "모르는 빌드"로 나오면, 설치 프로그램이 거부합니다. 진행하려면 USB에 빈 파일 `HMGAPI/FORCE`를 두어야 합니다(**본인 책임**: `/system/bin/app_process`를 HMG-API의 래퍼로 바꾸며, HMG-API 제거 시 원래 파일로 복원됨).
4. **훅 맵 만들기**: HMG-API 앱 → Settings → Hook map → *Map this car from its files* → *Check the map on this car* → 재시작.
   (이 저장소의 [maps/](maps/)는 같은 방법으로 Mac에서 만든 것이라 결과 대조용입니다. adb로 `/data/hmgapi/maps/kia/ev6/kor/260902/`에 넣고 `hmgapid engine verify-map`을 실행해도 됩니다.)
5. **두 패키지 설치**: [릴리스](https://github.com/hwjang1/ev6kr-wireless-carplay/releases)의 `ev6kr-wireless-carplay-nemesis.zip`(직접 빌드했다면 `dist/ev6kr-wireless-carplay-0.2.0-mod0.2.9-nemesis.zip`)을 USB 루트에 풀고 꽂으면 NEMESIS 패키지 스토어에 두 패키지가 나옵니다.
   **① EV6 KR CarPlay Hotspot Sync**를 먼저, 이어서 **② Wireless CarPlay (EV6 KR, USB Wi-Fi)**를 설치합니다.
   - ①은 USB 핫스팟의 SSID·비밀번호를 순정 카플레이 값(`Kia_` + 4글자)으로 바꿉니다. NEMESIS Manager에서 Launch를 누르면 즉시 동기화하고 결과를 알림으로 보여줍니다.
   - ②는 HMG-API 엔진으로 설치·적용됩니다. HMG-API에 등록되지 않은 서명 키라 경고가 나오지만 설치는 됩니다.
   - 하나씩 설치하려면 `ev6kr.usbap.sync-0.2.0-nemesis.zip`, `wireless-carplay-kr-0.2.9-nemesis.zip`을 각각 써도 됩니다. 이전 버전이 깔려 있으면 스토어에서 업데이트로 나옵니다.
6. USB를 빼고 **동글을 꽂은 뒤 헤드유닛 재시작**. 기능 플래그와 훅은 재시작 뒤에 적용됩니다.
7. **연결**: iPhone에서 예전 핫스팟(`HKG_USB_Hotspot`)과 **`Kia_…` 네트워크를 "이 네트워크 설정 지우기"** 하고, 설정 → 일반 → CarPlay에서 이 차를 지웁니다(손으로 입력한 설정이 남아 있으면 카플레이가 받은 설정과 엇갈립니다). iCloud 키체인을 쓰는 다른 Apple 기기에도 같은 네트워크가 남아 있으면 지웁니다. 이어서 블루투스로 연결하고 카플레이 안내에 따라 진행합니다. **핫스팟 비밀번호를 직접 입력하지 마세요.**
8. **안 되면**: 동글을 빼고 로그 수집 USB(릴리스의 `ev6kr-trylog-usb.zip`, 또는 [usb/trylog/](usb/trylog/))를 꽂아 실행 → 화면에 "EV6KR 로그 수집 완료" 토스트가 뜰 때까지(약 1분) 기다렸다가 빼고 `EV6KR-TRYLOG/` 폴더를 보내주세요. 일찍 빼면 파일이 비어 있습니다(설정 파일은 먼저 적용됨).
   ①의 진단 로그(카플레이·iAP2·블루투스·핫스팟 관련 태그 전부 + 그 밖의 경고·오류, 네트워크 샘플)는 0.2.0부터 기본으로 꺼져 있습니다. 원인을 찾아야 하면 이 USB 루트에 빈 파일 `EV6KR-DEBUG-ON`을 두고 한 번 실행해 켜고, 문제가 생기는 상황을 다시 겪은 뒤 로그를 수집하세요. 켜 두어도 1MB 파일 5개(약 5MB)까지만 남고, `/data` 남은 공간이 100MB보다 적으면 저절로 멈추고 지워집니다. 다 쓰면 `EV6KR-DEBUG-OFF`를 두고 실행해 끕니다(꺼지면 다음 시동 때 진단 파일도 지워짐).

### 되돌리기

설치의 역순입니다: 모드(`hmgapi.wireless-carplay-kr`) 제거 → `ev6kr.usbap.sync` 제거(USB 핫스팟의 원래 SSID·비밀번호 복원) → 필요하면 HMG-API 제거(`app_process` 원본 복원).
모드의 변경(기능 플래그 1줄, 권한 파일 1개, 설정값 3개)은 HMG-API 엔진이 원본 그대로 되돌립니다. 라이브러리 패치는 메모리에서만 적용되므로 `/system`의 파일은 바뀌지 않습니다.

### 로그

- 핫스팟 동기화: `/data/nemesis/ev6kr.usbap.sync.log`(256KB를 넘으면 `.old`로 넘김), 진단 logcat `/data/nemesis/ev6kr.usbap.sync.logcat.txt*`(진단 로그를 켰을 때만)
- 모드: `adb logcat -v time -s DiPO:V GPM:V Iap2:V HMG-API:V`(브리지 줄은 `HMG-API: bridge: ...`), 또는 HMG-API 앱의 Car Report
- USB 핫스팟: `/data/local/tmp/usbwifi_hotspot/daemon.log`, `hostapd.log`

### 설정

- 동기화 일시 중지: 빈 파일 `/data/nemesis/ev6kr.usbap.sync.disabled` (앱에서 Launch를 누르면 해제되고 즉시 동기화)
- 진단 로그 켜기: 빈 파일 `/data/nemesis/ev6kr.usbap.sync.debug` (로그 수집 USB의 `EV6KR-DEBUG-ON`이 만들고 `EV6KR-DEBUG-OFF`가 지움)
- 순정 카플레이 비밀번호에 영문·숫자 외 문자가 있으면 영문·숫자 12자리로 바꿉니다(iAP2와 같은 `WifiManager` API, 원래 값은 백업 후 제거 시 복원). 그대로 두려면 빈 파일 `/data/nemesis/ev6kr.usbap.sync.keeppsk`
- USB 핫스팟이 STA(클라이언트) 모드일 때는 동기화하지 않습니다.
- 차 LTE 공유: 0.1.12부터 끕니다(순정처럼 iPhone은 자기 셀룰러 사용). 다시 켜려면 `/data/nemesis/ev6kr.usbap.sync.tune`에 `lte_share=1`(trylog USB의 `EV6KR-TUNE.txt`로도 설정)
- 모드 설정(HMG-API 앱): 길안내(tbt), 블루투스 브리지(bridge), 무선 오디오(audio), 무선 카플레이 곡 정보(now_playing, 0.1.13, 기본 켬, 재시작 후 적용: 계기판·미디어 위젯·분할 화면의 곡 제목), 블루투스 준비 즉시 카플레이 시작(early_start, 0.1.12, 기본 켬, 재시작 후 적용), USB 메모리·허브가 유선 안드로이드 오토를 시작하지 않게(usb_drives).
  0.2.1에서 추가(모두 기본 켬, 재시작 후 적용): 계기판 곡 팝업에 아티스트(cluster_artist), 무선 통화 상태(call_state), 통화가 없을 때 통화 버튼으로 카플레이 전화(call_key_phone, call_state 필요; 0.2.9부터 통화 상태를 받기 전에도 동작), 음성 버튼 짧게 눌러도 시리(siri_key), 시동을 끌 때 카플레이 음악을 마지막 소리로 유지(audio_resume).
  0.2.8에서 추가(기본 켬, 재시작 후 적용): 교차로 근처에서 카플레이 회전을 순정 내비 형식으로(tbt_standard, AR HUD 화살표). 계기판·HUD 방향 표시가 이상하면 끄면 이전처럼 아이콘만 보냅니다.
  ★ 버튼(차량 설정의 버튼 설정): "Apple CarPlay"(카플레이 열기), "Apple CarPlay 지도"(0.2.3, 카플레이 지도 화면으로 바로)

## 빌드

```sh
tools/build.sh keygen     # 처음 한 번: 모드 서명 키 ~/hmg-private/keys/ev6kr-mods.key (저장소 밖, 백업 필수)
tools/build.sh            # 기존 구성: dist/ 에 zip 6개 (두 패키지를 담은 USB 설치본 포함)
driver/build.sh <차량 /proc/config.gz> # 독립 구성의 드라이버 (Docker)
tools/build.sh release    # 릴리스용 파일: dist/release/ (점검·로그 USB zip, SHA256SUMS.txt 포함)
tools/build.sh standalone # 독립 구성: usb-hotspot-kr, 카플레이 모드, 둘을 담은 USB 설치본
sh tests/apsync-test.sh   # 동기화 패키지 시험 (MOCK_SH=mksh 로 차량과 같은 셸 사용 가능)
sh tests/hotspot-test.sh  # 독립 핫스팟 서비스 시험
sh tests/nowplaying-test.sh; sh tests/clustertext-test.sh; sh tests/carturns-test.sh  # 카플레이 모드 시험 (JDK)
```

필요: JDK(javac/java), Android SDK(`platforms/android-*/android.jar`, build-tools `d8`), python3, zip, 그리고 옆 폴더의 HMG-API 소스(`HMGAPI_DIR`, 기본 `../HMG-API`, 읽기만 함).
**서명 키는 잃어버리면 같은 모드의 업데이트를 만들 수 없습니다**(차에 설치된 모드가 키에 묶임).

## 알려진 제약과 미확인 사항

- **차량 확인 범위**: 한 대(국내형 EV6, 260902)에서만 확인했습니다. 확인 항목은 맨 위 상태를 보세요. 다른 차종은 [차량 정보 수집 안내](docs/CARINFO.md)를 보세요.
- **동글**: 정상 동작을 확인한 동글은 ipTIME AX900UA·AX900MINI(RTL8831BU, `8851bu` 드라이버)와 ipTIME A3000mini(RTL8812BU, `88x2bu` 드라이버)입니다([49절](docs/ANALYSIS.md)). A3000mini는 짧은 세션(162초, 103초)으로 확인했고, 긴 주행은 아직 시험하지 않았습니다. 다른 RTL8812BU 동글 한 개는 송신 체인 불량으로 링크가 무너졌고, 송신 경로·전력·1T2R 같은 소프트웨어 조정으로도 해결되지 않았습니다([29절](docs/ANALYSIS.md)). A3000mini는 같은 칩으로 송신 실패 0%였으므로, 그 동글 개체의 하드웨어 문제로 봅니다. 5GHz를 지원하지 않는 동글(예: 2.4GHz 전용 AX300급)은 쓸 수 없습니다.
- **길안내**: 내비 앱이 iAP2 길안내 정보를 보내야 클러스터·HUD에 표시됩니다. T맵과 네이버지도는 확인했습니다. 네이버지도는 앱의 CarPlay 설정(톱니바퀴 → 네비게이션 탭)에서 HUD 스위치를 켜야 보냅니다. 다른 앱은 시험하지 않았습니다.
- **usbwifi.hotspot 자동 업데이트**: `AUTO_UPDATE=1`(기본)이면 LTE로 GitHub에서 스스로 업데이트합니다. 업데이트 중 핫스팟이 몇 분간 반복해서 다시 만들어진 적이 있습니다([26절](docs/ANALYSIS.md)).
- **USB 포트**: 이 차는 데이터 USB 포트가 하나라서, 동글을 꽂아 두면 유선 카플레이·USB 메모리는 허브 없이 쓸 수 없습니다.
- **DiPO의 USB 리셋**: DiPO는 카플레이 세션이 약 6초 안에 열리지 않으면 USB 포트 전원을 껐다 켭니다(유선용 복구). 모드가 동글이 꽂혀 있는 동안(Realtek USB 드라이버에 USB 인터페이스가 묶여 있을 때) DiPO 프로세스에서 이 리셋을 막고, 막든 안 막든 HMG-API 로그에 `USB power reset …`을 남깁니다.
- **USB 핫스팟 복구**: `usbwifi.hotspot` 데몬은 USB 버스 리셋이나 빠른 재연결을 알아채지 못해 핫스팟을 다시 띄우지 않습니다(사라진 `rtwlan0`에 주소가 남고 새 `rtwlanN`은 미설정). 동기화 서비스가 이 상태를 약 1분 30초 뒤 감지해 재시작을 요청합니다. 근본적으로는 USB 패키지 쪽 수정이 필요합니다.
- **카플레이 IE**: 순정은 핫스팟 비콘용 카플레이 정보 요소를 설정에 저장하지만 순정 핫스팟 시작 경로에서 쓰이는 곳은 찾지 못했고, USB 핫스팟에도 넣지 않았습니다. 넣지 않아도 iPhone은 iAP2로 받은 SSID·비밀번호로 접속합니다(iAP2는 채널을 보내지 않으므로 iPhone이 SSID로 찾아갑니다).
- **핸들 버튼·음악 자동 재생(0.2.1)**: DiPO의 난독화된 메서드를 이름과 시그니처로 찾습니다. 펌웨어가 바뀌어 찾지 못하면 그 기능만 꺼지고 HMG-API 로그에 남습니다. 시동을 다시 켰을 때 음악이 이어지는 부분(audio_resume)과 아이폰 두 대일 때의 동작(0.2.3)은 차에서 아직 확인하지 못했습니다. 아이폰이 블루투스로 통신하는 동안(통화 상태를 받기 전)에는 통화 중에 통화 버튼을 누르면 끊기지 않고 보류가 됩니다(순정과 같음, [48절](docs/ANALYSIS.md)).
- **AR HUD 화살표(0.2.8)**: 교차로 약 457 m 안에서만 순정 형식으로 보내고, 그보다 멀 때와 HUD의 다음 회전 목록은 아이콘입니다. 계기판의 채워지는 거리 막대는 제한속도와 같은 프레임이라 비워 둡니다(HMG-API에 연결 지점이 더 필요). T맵의 회전교차로는 회전 종류 없이(0) 와서 지금도 표시되지 않습니다. 갈림길·고속도로 출구·회전교차로·도착은 차에서 아직 확인하지 못했습니다([47절](docs/ANALYSIS.md)).
- **HUD 그림 칸**: HUD는 음원 종류마다 곡 정보 옆에 그림을 그리는데, 카플레이용 그림은 없어 비어 있습니다. 카플레이를 다른 음원으로 알리면 계기판 팝업과 HUD 곡 정보가 아예 표시되지 않아(0.2.5 시험) 순정 그대로 둡니다. 계기판·HUD로 앨범아트 이미지를 보내는 순정 경로는 없습니다([46절](docs/ANALYSIS.md)).
- **무선 안드로이드 오토**: 켜지 않았습니다(카플레이 무선 플래그만). 안드로이드 오토 길안내 네이티브 패치는 국내형 라이브러리가 달라 적용되지 않습니다.
- **HMG-API 차량 탐색기**: 국내형 BtSettings에서 `btsettings.projection_helper`를 엉뚱한 클래스로 잡아 그 지점들이 빠집니다(Mac의 `map-car.py`는 찾음). 모드의 지점이 하나라도 빠지면 모드 훅 전체가 꺼지므로, 이 지점을 쓰는 미국·캐나다 전용 메뉴 기능을 뺐습니다.
- **HMG-API 코어**: 국내형은 공식 지원 차종이 아닙니다. `app_process` 빌드 확인이 필요하고, 코어의 일부 부가 기능(버튼 설정 등 68개 지점)은 국내형 맵에 없어 꺼집니다.
- **USB 대시보드와의 관계**: 대시보드에서 AP SSID·비밀번호를 바꾸면 30초 안에 순정 카플레이 값으로 되돌아갑니다(카플레이가 그 값을 iPhone에 주기 때문). 대시보드는 설정값을 따옴표 없이 저장하므로, 순정 비밀번호에 특수문자가 있으면 대시보드 화면에는 따옴표가 붙어 보일 수 있습니다(데몬은 정확히 읽음).
- **셸**: 스크립트는 sh·dash·mksh로 시험합니다(차량은 mksh).
- **독립 구성**: 드라이버의 핫언플러그, dnsmasq의 67번 포트, 순정 방화벽을 차에서 확인하지 못했습니다([hotspot/README.md](hotspot/README.md)).

## 면책

- **비공식 프로젝트입니다.** 현대자동차·기아·현대모비스·Telechips·Apple 등 어떤 제조사의 승인이나 지원도 받지 않았습니다. 상표는 각 소유자의 것입니다.
- **보증하지 않습니다.** 소프트웨어는 "있는 그대로" 제공되며, 법이 허용하는 범위에서 어떤 명시적·묵시적 보증도 하지 않습니다(AGPL-3.0 제15·16조).
- **결과는 설치한 사람이 책임집니다.** 다음을 포함한 모든 손해의 책임은 사용자에게 있습니다.
  - 헤드유닛 부팅 불가나 고장. 복구에 서비스센터 입고가 필요할 수 있습니다.
  - 블루투스 통화, 내비게이션, 카메라 화면 등 순정 기능의 오작동
  - 펌웨어 업데이트 뒤 동작 중단이나 충돌, 설정·데이터 손실
  - 제조사·판매점의 보증이나 무상수리 거부
- **이 저장소가 하지 않는 일:** 차량 CAN 메시지를 직접 보내지 않고, MICOM·부트로더·모뎀 펌웨어를 바꾸지 않습니다. 이것이 안전을 보장한다는 뜻은 아닙니다.
- **전파 관련:** USB 무선랜 동글은 국내 전파 인증(KC)을 받은 제품을 쓰세요. 송신 경로·전력 조정 기능은 시험용이며, 인증 범위를 벗어나는 설정은 사용자 책임입니다.
- **운전 중에는 조작하지 마세요.** 설치·설정·로그 수집은 정차 상태에서 하고, 카플레이는 도로교통법에 맞게 사용하세요.
- **개인정보:** [usb/trylog/](usb/trylog/) 수집본에는 휴대폰 이름, 블루투스·Wi-Fi 주소, 헤드유닛 정보가 들어갈 수 있습니다. 공개 이슈에 그대로 올리지 마세요.

## 라이선스

AGPL-3.0 ([LICENSE](LICENSE)). 의존 프로젝트의 라이선스는 [의존 프로젝트와 제약](#의존-프로젝트와-제약)에 있습니다. HMG-API의 코드를 바탕으로 하며, 출처는 [NOTICE.md](NOTICE.md)에 있습니다. [driver/](driver/)와 `usb-hotspot-kr`에 들어가는 드라이버(`.ko`)는 GPL-2.0입니다.
제조사 앱·라이브러리는 포함하지 않습니다(해시 기반 레시피, md5 목록, 훅 이름만).
