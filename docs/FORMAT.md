# Veilix image format

This describes what a decoder on any platform must do. The Android
implementation is in
[`app/src/main/java/cattivole/veilix/core`](../app/src/main/java/cattivole/veilix/core)
(start with `stego/StealthFormat.kt`), and [`test-vectors/`](../test-vectors)
holds images written by the separate iOS implementation (see the end of this
document). Both apps read each other's output.

## Carrier

An image is treated as 8-bit RGBA, top row first, left to right. Alpha is never
used. Every R, G and B byte is one carrier position, numbered from 0:

```
offset → byte index:  (offset / 3) * 4 + (offset % 3)
rgbCount = width * height * 3
```

Only the least significant bit of each position carries data. The file must be
lossless: recompressing the image destroys the payload.

## Choosing a format

Read the first 344 positions in order, as 43 bytes, most significant bit first.

- If they start with `VLX1` and the CRC32 of the first 39 bytes matches the
  last 4 (big-endian), the image is **format v1–v3**.
- Otherwise, if the user gave a password, try **format v4**. Otherwise report
  that nothing was found.

An image with no readable header and an image holding v4 data look the same.
The password is the only way to tell them apart, and a decoder must report both
cases with the same message.

## Transport in a ZIP

Messengers recompress images but deliver other files untouched, so the app can
share the PNG inside a ZIP: one stored entry, with a fixed timestamp of
1980-01-01 so the file doesn't record when it was made. Exported files get
camera-style names (`IMG_1234.png`) because a name travels with the file.

A decoder should accept a ZIP as input and use the first `.png` entry whose
signature and CRC32 are correct, in either stored or DEFLATE form. Names inside
an archive are untrusted and must never be used as paths.

## Format v4

Used for everything protected by a password.

### Positions

Taken positions are remembered across all steps below. To draw a position, take
the next 64-bit value `v` and compute `v mod rgbCount`; if that position is
already taken, draw again.

Bits are read and written most significant bit of each byte first.

### 1. Salt

16 bytes, at positions drawn from SplitMix64 with the public seed
`0x5645494C49585634` (`"VEILIXV4"`). If the seed is 0, use `0xDEADBEEFCAFEBABE`
(never the case for this seed).

```
state += 0x9E3779B97F4A7C15            (wrapping)
z = state
z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9  (wrapping)
z = (z ^ (z >> 27)) * 0x94D049BB133111EB  (wrapping)
output = z ^ (z >> 31)
```

### 2. Keys

```
password bytes = UTF-8 of the password after Unicode NFC normalization
master         = PBKDF2-HMAC-SHA256(password bytes, salt, 600000 rounds, 32 bytes)
meta key       = HKDF-SHA256(master, salt = empty, info = "veilix.v4.meta",      32 bytes)
body key       = HKDF-SHA256(master, salt = empty, info = "veilix.v4.body",      32 bytes)
positions key  = HKDF-SHA256(master, salt = empty, info = "veilix.v4.positions", 32 bytes)
```

An empty password is not valid for v4.

### 3. Position stream

All further positions come from an AES-256-CTR keystream, produced by sealing
1 MiB chunks of zeros with AES-256-GCM under the positions key and using the
ciphertext (not the tag). Chunk `i` (from 0) uses the 12-byte nonce of four zero
bytes followed by `i` as a big-endian 64-bit integer. Each position uses the next
8 keystream bytes as a big-endian `v`.

### 4. Sealed meta

23 bytes from the position stream: 7 bytes of ciphertext and a 16-byte tag, in
that order. AES-256-GCM under the meta key, with a 12-byte zero nonce and the
salt as associated data. Plaintext:

| Offset | Size | Field |
|---|---|---|
| 0 | 1 | Version, currently `4` |
| 1 | 1 | Body cipher: `1` AES-256-GCM, `2` ChaCha20-Poly1305 |
| 2 | 1 | Compression: `0` none, `1` raw DEFLATE, `2` LZFSE, `3` LZMA (`2` and `3` are written only by the iOS app) |
| 3 | 4 | Body size in bytes, big-endian, including the 16-byte tag |

If the tag doesn't verify, report a wrong password.

The nonce can be fixed because every salt produces new keys and each key seals
one message.

### 5. Body

`body size` bytes from the position stream continuing where meta ended: ciphertext
then a 16-byte tag. Sealed with the body cipher under the body key, with a 12-byte
zero nonce and `salt || meta plaintext` as associated data.

### 6. Payload

Decompress the plaintext, refusing to produce more than 256 MiB, then read the
envelope:

- `0x00`, then UTF-8 text.
- `0x01`, then 1 byte of name length, the UTF-8 file name, then the file bytes.

The file name is untrusted input. Reduce it to a single plain path component
before using it: no path separators, control characters, text direction
overrides, or leading dots.

### Writing

- Reject the payload unless `(23 + body size) * 8 <= (rgbCount - 128) * 9 / 10`.
- Generate the salt with a cryptographic random generator for every image.
- Flatten any transparency first, so that all alpha values are 255. Premultiplied
  colour doesn't survive PNG encoding when alpha is below 255.
- Write with **LSB matching**: if a position's LSB already matches the bit, leave
  it. Otherwise add or subtract 1, choosing the direction at random. A value of 0
  always goes up and 255 always goes down. Don't overwrite the LSB directly: that
  is what classic histogram detectors look for.
- Export as PNG and check every carrier bit against the re-read file before
  offering the image to the user.

## Formats v1–v3

v3 is still written when there is no password. It offers no secrecy and is meant
to be found. v1 and v2, and encrypted v3, are read-only, for images that already
exist.

### Header

43 bytes in the first 344 positions, in order:

| Offset | Size | Field |
|---|---|---|
| 0 | 4 | Magic `VLX1` |
| 4 | 1 | Version |
| 5 | 1 | Encryption: `0` none, `1` AES-256-GCM, `2` ChaCha20-Poly1305 |
| 6 | 1 | Compression, as in v4 |
| 7 | 16 | Salt |
| 23 | 12 | Nonce |
| 35 | 4 | Body length, big-endian |
| 39 | 4 | CRC32 of bytes 0–38, big-endian |

### Keys

```
v1:      key source = password bytes, used directly with salt
v2, v3:  master = PBKDF2-HMAC-SHA256(password bytes, salt, 200000 rounds, 32 bytes)

v1 key  = HKDF-SHA256(password bytes, salt, info = "veilix.enc.v1", 32 bytes)
v1 seed = HKDF-SHA256(password bytes, salt, info = "veilix.seed.v1", 8 bytes)
v2, v3 key  = HKDF-SHA256(master, salt = empty, info = "veilix.enc.v1", 32 bytes)
v2, v3 seed = HKDF-SHA256(master, salt = empty, info = "veilix.seed.v1", 8 bytes)
```

The seed bytes are read as a little-endian 64-bit integer. Without encryption the
password is empty. Images from early builds may be keyed to a password that was
left in a hidden field, so after a failure with the empty password, a decoder
should retry with any password the user typed.

### Body

Positions are drawn as in v4 but from SplitMix64 with the seed above, over the
positions from 344 to `rgbCount - 1` (draw `v mod (rgbCount - 344)` and add 344).
The body is `ciphertext || tag`, with the header's nonce and no associated data.

After decompressing, v1 and v2 bodies are raw UTF-8 text. v3 bodies use the
envelope above.

## Test vectors

[`test-vectors/`](../test-vectors). All were written by the iOS app, and the
Android unit tests run against every one of them. Android can't yet decompress
LZFSE or LZMA, so for `v3-aes.png` and `v3-chacha.png` the tests only check that
it reports the compression as unsupported.

| File | Password | Content |
|---|---|---|
| `v4-aes.png` | `пароль` | Text `v4 golden AES` (AES-256-GCM, raw DEFLATE) |
| `v4-chacha.png` | `pw2` | File `доклад.txt` containing `v4 golden file` (ChaCha20-Poly1305, no compression) |
| `v4-aes-zip-cli.zip` | `пароль` | `v4-aes.png` in a DEFLATE ZIP made with `zip -9` |
| `v4-aes-zip-ditto.zip` | `пароль` | `v4-aes.png` in a ZIP made with `ditto`, as Finder does, with `__MACOSX` entries |
| `v3-aes.png` | `пароль` | Text `legacy-aes` (AES-256-GCM, LZFSE) |
| `v3-chacha.png` | `pw2` | File `доклад.txt` containing `legacy-file` (ChaCha20-Poly1305, LZMA) |
| `v3-plain.png` | none | Text `legacy-plain` |
| `v3-stale.png` | `stale` | Text `legacy-stale`, keyed to a leftover password |
| `v3-plain-cat.png` | none | Text `Просто проверочный текст` |
