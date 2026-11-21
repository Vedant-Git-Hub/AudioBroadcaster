# Google Play release preparation

This repository prepares the Android receiver for Google Play. Public publication still requires a verified Play Console developer account and an approved Play release.

## Prepared artifacts

- Signed Android App Bundle: `android/app/build/outputs/bundle/release/app-release.aab`
- Store listing copy: [STORE_LISTING.md](STORE_LISTING.md)
- Store icon: [assets/play-icon-512.png](assets/play-icon-512.png)
- Feature graphic: [assets/feature-graphic-1024x500.png](assets/feature-graphic-1024x500.png)
- Privacy policy: [../PRIVACY_POLICY.md](../PRIVACY_POLICY.md)

The app targets API 36, supports Android 10+, and uses package `com.a1saudio.receiver`. The unsigned debug builds use a different signing key. An installed debug build may need to be removed before installing the Play-signed version, which clears that app's local data.

## Upload key

The local release bundle is signed with an upload key stored outside the repository at `~/.config/audiobridge/upload.jks`. Its Gradle signing properties are at `~/.config/audiobridge/upload.properties`; both files have owner-only permissions. They are **not committed**. Back up both securely before the first Play upload and keep them available for future updates. Play App Signing will manage the key used to sign copies delivered to users; this local key signs bundles uploaded to Play.

To rebuild the signed bundle on this computer:

```sh
cd android
./gradlew bundleRelease
```

## Account and submission steps

1. Create and verify a Google Play Console developer account. Google currently charges a one-time registration fee.
2. Create the AudioBridge app in Play Console. Check whether Google asks for proof of ownership of `com.a1saudio.receiver`, which has previously been installed outside Play using a debug key.
3. Fill the main store listing using the prepared copy and graphics. Add at least the required phone screenshots. Enter a public developer contact email and the privacy-policy URL from the listing draft.
4. Complete the App content, target audience, ads, Data safety, content rating, and foreground-service declarations accurately for the final app. Review the draft privacy statement against the final binary.
5. Upload the signed `.aab` and configure Play App Signing. Provide the hardware access explanation and a demonstration video if review requests them.
6. If the new account is a personal account created after 13 November 2023, run a closed test with at least 12 opted-in testers for 14 consecutive days, then apply for production access.
7. After Play grants production access, review the final listing and release, then submit for production review.

The release must be reviewed in Play Console before submitting it. Do not put Play account credentials or signing secrets into this repository.
