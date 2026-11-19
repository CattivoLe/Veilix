# Changelog

## 1.1

First open-source release.

- **Format v4.** Password-protected images carry no readable marker. The salt
  is the only thing at public positions; the header is sealed and everything
  else sits at positions derived from the password.
- Key derivation raised to PBKDF2-HMAC-SHA256 with 600,000 rounds, followed by
  separate HKDF keys for the header, the body and the positions.
- LSB matching (±1) instead of overwriting the low bit.
- Share as ZIP, so messengers deliver the PNG without recompressing it. ZIP
  files open directly.
- Every export is read back and checked before it's offered.
- Images with transparency are flattened first, so they no longer lose data.
- Password generator and a stricter strength meter. A new password must be
  typed twice.
- Exports get camera-style names (`IMG_1234.png`).
- The screen is hidden in recent apps, and copied secrets clear from the
  clipboard after a minute.
- Saving to Downloads/Veilix.
- No cloud backup or device-to-device transfer of app data.

Images from 1.0 (formats v1–v3) still open. Images protected with 1.1 need
Veilix 1.1 or later on the other side.

## 1.0

- Hide text or files in images with AES-256-GCM or ChaCha20-Poly1305.
- Compatible with the iOS app.
