# DJAEGER WORK APK Signing

Source of truth APK berada di repository `Djaeger1/DJAEGER-WORK`.

## Aturan

- Jangan commit file `.jks`, `.keystore`, password signing, atau base64 keystore ke repository public.
- Build tanpa secret tetap berjalan sebagai validasi unsigned.
- Build signed menggunakan GitHub Actions secrets berikut:
  - `HERMES_WORK_KEYSTORE_B64`
  - `HERMES_WORK_STORE_PASSWORD`
  - `HERMES_WORK_KEY_ALIAS`
  - `HERMES_WORK_KEY_PASSWORD`
- Package harus tetap `com.hermes.workdashboard`.
- Jalur migrasi v1.2.8 memakai signature legacy agar bisa meng-update instalasi v1.2.7.
- Legacy signer di `DJAEGER-Control-Center` hanya manual fallback; source APK tetap dibaca dari `DJAEGER-WORK/main`.

## Status migrasi

- Source v1.2.7 dipindahkan tanpa folder signing.
- Versi migrasi: v1.2.8 / versionCode 128.
- Workflow isolated APK: aktif.
- Unsigned validation build: aktif.
- Legacy signed migration build: terverifikasi.
