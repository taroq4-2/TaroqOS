# 🛡️ Taroq OS — حاجب الإعلانات الشامل

<div align="center">

**مفتوح المصدر | بدون root | يحجب كل الإعلانات**

[![Build APK](https://github.com/Taro0q/TaroqOS/actions/workflows/build.yml/badge.svg)](https://github.com/Taro0q/TaroqOS/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-teal.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://android.com)

🌐 [os73.com](https://os73.com) &nbsp;|&nbsp; 📷 [@Taro0q](https://instagram.com/Taro0q)

</div>

---

## كيفية الحصول على APK

### الطريقة الأولى: GitHub Actions (تلقائية)
1. ارفع هذا المشروع على GitHub
2. اذهب لـ `Actions` ← `Build TaroqOS APK`
3. اضغط `Run workflow`
4. بعد انتهاء البناء، حمّل الـ APK من `Artifacts`

### الطريقة الثانية: Android Studio
1. افتح Android Studio ← `File → Open` ← اختر مجلد `TaroqOS`
2. انتظر مزامنة Gradle
3. `Build → Build APK(s)`
4. APK في: `app/build/outputs/apk/debug/`

### الطريقة الثالثة: Command Line
```bash
cd TaroqOS
chmod +x gradlew
./gradlew assembleDebug
```

---

## آلية العمل

```
┌─────────────────────────────────────────────────────────────┐
│                    Taroq OS Shield                          │
│                                                             │
│  1. فلتر DNS  — يحجب 500+ نطاق إعلاني بالاسم              │
│  2. فلتر SNI  — يقرأ وجهة HTTPS قبل التشفير               │
│  3. VPN محلي  — لا يحتاج root                              │
│                                                             │
│  جميع العمليات محلية — لا بيانات تغادر جهازك              │
└─────────────────────────────────────────────────────────────┘
```

---

## المتطلبات
- Android 8.0 (API 26) أو أعلى
- لا يحتاج root

---

## المطور
- 🌐 [os73.com](https://os73.com)
- 📷 [@Taro0q](https://instagram.com/Taro0q)

---

## الرخصة
MIT License
