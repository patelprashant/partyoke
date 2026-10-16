# 🚀 Releasing PartyOke

PartyOke uses **GitHub Actions** for CI/CD. There are two workflows:

| Workflow | Trigger | What it does |
|---|---|---|
| **CI** (`.github/workflows/ci.yml`) | every PR + push to `main` | ktlint, Android lint, builds the debug APK, uploads it as a run artifact |
| **Release** (`.github/workflows/release.yml`) | pushing a tag `vX.Y.Z` | builds a **signed** release APK + AAB and publishes a GitHub Release with auto-generated notes |

Versioning is **tag-driven**: the tag `v1.2.0` becomes `versionName = "1.2.0"`, and
`versionCode` is the workflow run number. You never edit `build.gradle.kts` for a release.

---

## One-time setup: signing secrets

The release workflow needs a signing keystore. You create it **once** and store it
as encrypted GitHub Secrets. CI decodes it at build time; it is never committed.

### 1. Generate a keystore (locally, once)

```bash
keytool -genkeypair -v \
  -keystore partyoke-release.keystore \
  -alias partyoke \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a **keystore password**, your details, and a **key password**
(you can reuse the same password for both). **Keep this file and the passwords safe
and backed up** — losing them means you can't ship updates that the device accepts
as the same app.

### 2. Base64-encode the keystore

```bash
# macOS
base64 -i partyoke-release.keystore | pbcopy
# Linux
base64 -w0 partyoke-release.keystore
```

### 3. Add the GitHub Secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 string from step 2 |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `partyoke` (the alias from step 1) |
| `KEY_PASSWORD` | the key password |

---

## Cutting a release

Once secrets are set, releasing is a single tag push:

```bash
git checkout main && git pull
git tag v1.0.0
git push origin v1.0.0
```

The Release workflow then:
1. Derives the version from the tag.
2. Builds the signed `partyoke-1.0.0.apk` and `partyoke-1.0.0.aab`.
3. Creates a **GitHub Release** named "PartyOke 1.0.0" with auto notes + both files attached.

Find it under the repo's **Releases** tab.

> **Versioning scheme:** [SemVer](https://semver.org/) — `vMAJOR.MINOR.PATCH`.
> Bump PATCH for fixes, MINOR for new features, MAJOR for breaking changes.

---

## Local builds (no secrets needed)

Local debug builds are unaffected and need no keystore:

```bash
./gradlew :app:assembleDebug
./gradlew ktlintCheck         # run the same lint CI runs
./gradlew ktlintFormat        # auto-fix style issues
```

A local **release** build without a keystore stays unsigned (the signing config is
only attached when the keystore properties are present), so configuration never
fails on a dev machine or a fork.
