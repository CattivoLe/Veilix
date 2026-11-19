# Security

## Reporting a vulnerability

Please don't open a public issue. Use GitHub's
[private vulnerability reporting](../../security/advisories/new) for this
repository instead.

Useful reports include:

- a way to recover a payload, its length, or the fact that one is present
  without the password, faster than guessing passwords;
- an image that Veilix encodes but can't decode, or that decodes to the wrong
  data;
- a crafted image or ZIP that crashes the app, uses unbounded memory, or writes
  a file outside the place you chose.

## Scope

[README → What it does not protect against](README.md#what-it-does-not-protect-against)
lists the known limits. Statistical detection of LSB embedding is expected, so
it isn't a vulnerability on its own, but a detector that works at small payload
sizes is interesting and welcome as an issue.

The cryptography comes from the platform (`javax.crypto` with AES-GCM and
ChaCha20-Poly1305). Veilix's own code covers key derivation, position
selection, the container format and its parsers. That is where review is most
useful.
