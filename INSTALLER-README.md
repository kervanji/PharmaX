# دليل إنشاء Installer لبرنامج PharmaX

## المتطلبات

### 1. تثبيت Inno Setup

- قم بتحميل Inno Setup من: https://jrsoftware.org/isdl.php
- اختر النسخة: **Inno Setup 6** (مع QuickStart Pack)
- قم بتثبيته بالإعدادات الافتراضية

### 2. التأكد من وجود Java

- تأكد من تثبيت Java 17 أو أحدث
- تأكد من إعداد متغير البيئة `JAVA_HOME`

## خطوات إنشاء Installer

### الطريقة الأولى: تلقائي (موصى به)

قم بتشغيل الأمر التالي في PowerShell:

```powershell
.\build-installer.ps1
```

هذا الأمر سيقوم بـ:

1. بناء البرنامج باستخدام Maven
2. إنشاء custom runtime باستخدام jlink
3. إنشاء installer باستخدام Inno Setup

### الطريقة الثانية: يدوي

#### الخطوة 1: بناء Distribution

```powershell
.\build-distribution.ps1
```

#### الخطوة 2: إنشاء Installer

```powershell
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" installer-setup.iss
```

## الملفات المهمة

- **installer-setup.iss** - ملف إعدادات Inno Setup
- **build-installer.ps1** - سكريبت بناء الـ installer تلقائياً
- **build-distribution.ps1** - سكريبت بناء حزمة التوزيع

## الناتج النهائي

بعد اكتمال البناء، ستجد الـ installer في:

```
installer-output\PharmaX-Setup-1.2.5.exe
```

## مميزات الـ Installer

✅ **تثبيت احترافي**

- واجهة تثبيت حديثة وجميلة
- دعم اللغة العربية والإنجليزية
- اختيار مجلد التثبيت

✅ **اختصارات تلقائية**

- اختصار في قائمة ابدأ
- اختصار على سطح المكتب (اختياري)
- اختصار في شريط المهام السريع (اختياري)

✅ **إدارة الملفات**

- إنشاء مجلدات البيانات تلقائياً
- الحفاظ على قاعدة البيانات عند إعادة التثبيت
- خيار حذف البيانات عند إلغاء التثبيت

✅ **أمان**

- يتطلب صلاحيات المسؤول
- معرف فريد للبرنامج (GUID)
- توقيع رقمي (يمكن إضافته لاحقاً)

## تخصيص الـ Installer

يمكنك تعديل ملف `installer-setup.iss` لتخصيص:

### معلومات البرنامج

```pascal
#define MyAppName "PharmaX"
#define MyAppVersion "1.2.5"
#define MyAppPublisher "PharmaX"
```

### الأيقونة

```pascal
#define MyAppIcon "src\main\resources\templates\PharmaX.ico"
```

### اللغات

```pascal
[Languages]
Name: "arabic"; MessagesFile: "compiler:Languages\Arabic.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"
```

## التوزيع للعملاء

### الحجم المتوقع

- Installer: حوالي 50-60 MB (مضغوط)
- بعد التثبيت: حوالي 150 MB

### المتطلبات على جهاز العميل

- Windows 7 أو أحدث (64-bit)
- لا يحتاج Java (مدمج في البرنامج)
- مساحة حرة: 200 MB على الأقل

### طريقة التثبيت للعميل

1. تحميل ملف `PharmaX-Setup-1.2.5.exe`
2. النقر مرتين على الملف
3. اتباع خطوات التثبيت
4. تشغيل البرنامج من قائمة ابدأ أو سطح المكتب

## استكشاف الأخطاء

### خطأ: "Inno Setup not found"

- تأكد من تثبيت Inno Setup
- أعد تشغيل PowerShell بعد التثبيت

### خطأ: "Maven build failed"

- تأكد من تثبيت Maven
- تأكد من الاتصال بالإنترنت لتحميل المكتبات

### خطأ: "JavaFX jmods not found"

- قم بتشغيل `build-distribution.ps1` أولاً
- سيقوم بتحميل JavaFX تلقائياً

## إضافة توقيع رقمي (اختياري)

لإضافة توقيع رقمي للـ installer، أضف في ملف `installer-setup.iss`:

```pascal
[Setup]
SignTool=signtool
SignedUninstaller=yes
```

ثم قم بإعداد أداة التوقيع في Inno Setup.

## الدعم

للمزيد من المعلومات عن Inno Setup:

- الموقع الرسمي: https://jrsoftware.org/isinfo.php
- التوثيق: https://jrsoftware.org/ishelp/

---

**ملاحظة**: تأكد من اختبار الـ installer على أجهزة مختلفة قبل التوزيع للعملاء.
