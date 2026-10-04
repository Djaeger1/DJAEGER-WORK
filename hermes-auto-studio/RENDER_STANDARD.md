# DJAEGER WORK — Standar Render v2 (2026-10-04)

Disetujui Schatz berdasarkan video "belajar warna anak 2 tahun" FINAL-v2.

## Render Engine
**Muse** adalah mesin render utama. Bukan GitHub Actions, bukan WAN2.2.

## Konsep Wajib
**"Orang menjelaskan sambil bermain"** — presenter (anak kecil anime) aktif bermain
dengan objek pembelajaran sambil narasi menjelaskan. Bukan objek diam, bukan
karakter pajangan.

## Spesifikasi Teknis

### Video
- 6 klip AI video, masing-masing 10 detik, trim ke durasi scene (5/8/10/10/10/7)
- Concat langsung — **DILARANG stretch, ping-pong, slow-motion, frame beku**
- Resolusi 1280x720, H.264 + AAC, 50 detik total
- Gaya: anime (clean line art, warna vibrant, background indah) — referensi boy gitar

### Presenter
- SATU karakter konsisten di semua scene (deskripsi identik tiap generate)
- Framing: **medium-wide shot** — badan utuh terlihat, ruang lega di sekitar karakter
- DILARANG close-up sampai wajah kepotong
- DILARANG portrait lalu crop ke 16:9 — generate langsung landscape

### Suara
- TTS voice: `avocado_v2:vd2_r82_rep5k_2063_v068_28k_g5k` (dipilih Schatz)
- Bahasa: Indonesia (`--language id`), speed 80
- Generate PER SCENE (bukan satu file lalu dipotong) — hindari kata kepotong
- Tiap narasi mulai tepat di awal scene-nya (offset 0/5/13/23/33/43)
- Verifikasi: volumedetect di awal tiap scene sebelum kirim

### Teks Overlay
- Font: DejaVu Sans Bold, styling bersih (bukan font meme)
- Tampil SEPANJANG scene (bukan cuma 3 detik)
- Contoh: "belajar warna anak 2 tahun", "Giliran kamu!", "Hebat!"

## Aturan Kerja (dari Schatz)
1. **Audit dulu sebelum kirim** — cek 1 frame per scene + verifikasi audio
2. **Kekurangan yang ditemukan langsung diperbaiki** — jangan tunggu perintah
3. **Boleh kirim versi sebelum revisi** — kirim keduanya (sebelum & sesudah)
4. **Tiap generasi nama beda** — jangan timpa file lama (v1, v2, FINAL-v2, dst)
5. **Jangan paksa elemen** — ikuti script; boy gitar hanya contoh gaya

## Pipeline Lama (Deprecated)
`ai_video_scene.py` (WAN2.2, klip 2 detik + stretch) dan `render.sh` versi lama
tidak lagi dipakai sebagai primary. Disimpan sebagai fallback darurat saja.
