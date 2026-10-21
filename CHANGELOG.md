# Changelog

## [20.3.2](https://github.com/gimportexportdevs/gexporter/compare/v20.3.1...v20.3.2) (2026-10-21)


### Bug Fixes

* a shared Google Maps route navigates to its destination ([a6f990e](https://github.com/gimportexportdevs/gexporter/commit/a6f990e954be7beeadb663bd65d9f5984f4fc9e5)), closes [#12](https://github.com/gimportexportdevs/gexporter/issues/12)
* read the destination of routes shared from the Google Maps app ([8d45bbb](https://github.com/gimportexportdevs/gexporter/commit/8d45bbbbd5c964d2c6810d635cc74e3281d0acd4))

## [20.3.1](https://github.com/gimportexportdevs/gexporter/compare/v20.3.0...v20.3.1) (2026-10-21)


### Bug Fixes

* a second Garmin device without gimporter no longer hides the one that has it ([60a83e0](https://github.com/gimportexportdevs/gexporter/commit/60a83e0f86b421e7793a0d9285fbec1135651059))
* **convert:** valid timestamps for Locus routes, distances for waypoints ([8fb4c83](https://github.com/gimportexportdevs/gexporter/commit/8fb4c83de4c7c5a3dacee5a16758b1c680b94c85)), closes [#36](https://github.com/gimportexportdevs/gexporter/issues/36) [#14](https://github.com/gimportexportdevs/gexporter/issues/14)

## [20.3.0](https://github.com/gimportexportdevs/gexporter/compare/v20.2.0...v20.3.0) (2026-10-21)


### Bug Fixes

* Refinements to sending tracks to the watch (see 20.2.0 for the new features)

## [20.2.0](https://github.com/gimportexportdevs/gexporter/compare/v20.1.0...v20.2.0) (2026-10-20)


### Features

* add UI translations for 28 major languages ([18583fa](https://github.com/gimportexportdevs/gexporter/commit/18583fa8b05747c91a713f0c9d64052bb34ab7aa))
* geocode Google place links without coordinates via Nominatim ([c58ea56](https://github.com/gimportexportdevs/gexporter/commit/c58ea56431134e4e3c26ca6e127a024b363d3c22))
* list and delete gimporter content on the watch from the phone ([cf671df](https://github.com/gimportexportdevs/gexporter/commit/cf671dfa75c93bc7bef507a0b9696c227a77a927))
* long-press menu to send, delete from the watch or remove an entry ([e426574](https://github.com/gimportexportdevs/gexporter/commit/e4265744bbec25c85ec4c8c71f7dbd5fa0c90234))
* mark entries on the watch with a watch icon ([b5c10c4](https://github.com/gimportexportdevs/gexporter/commit/b5c10c42d03e39e22d7c8c0fcf148cdda0ce734d))
* open an entry's menu from a ⋮ button ([c223cac](https://github.com/gimportexportdevs/gexporter/commit/c223cacf3825cdec2afd620d04e31d24065c7215))
* push shared locations straight to the watch ([bda6992](https://github.com/gimportexportdevs/gexporter/commit/bda6992d8c4b29f343f1de8a8fda413257d36b55))
* redesign the main screen and move settings to their own screen ([1de67cd](https://github.com/gimportexportdevs/gexporter/commit/1de67cd66ec54cfad7462d8c23f419cc45dc8442))
* send a track to the watch with one tap and mark what is on it ([8166f2b](https://github.com/gimportexportdevs/gexporter/commit/8166f2bb0d6da589060bbf48586b9439ad5980f7))
* share locations from map apps to the watch ([f08d823](https://github.com/gimportexportdevs/gexporter/commit/f08d8239be1daf6f895c12702985189fe1695b22))
* show what gimporter is doing and do not send while it is busy ([ad5a058](https://github.com/gimportexportdevs/gexporter/commit/ad5a0581ce03cf43b2a681f7062fa42f89eb1bdd))
* show when a sent track is waiting on the watch to be started ([d0c594e](https://github.com/gimportexportdevs/gexporter/commit/d0c594e96aaca996d92c485916cc525ad4fe5bed))
* swipe an entry to delete it from the watch ([d1d40e5](https://github.com/gimportexportdevs/gexporter/commit/d1d40e5ce791f50eee2b9d5faf41f1be02c89260))
* target Android SDK 37, upgrade AGP 9.2.1 / Gradle 9.6, update deps ([fea0412](https://github.com/gimportexportdevs/gexporter/commit/fea0412c1b24a4484f4999a203aee48a4a2e151a))


### Bug Fixes

* ask an unknown running gimporter only once and show the whole hint ([255149e](https://github.com/gimportexportdevs/gexporter/commit/255149eafef7a31da15b1426bcfc2d43ddac5e18))
* do not send requests to a gimporter that does not know them ([3565d56](https://github.com/gimportexportdevs/gexporter/commit/3565d56a41560d9be81cf874c1afa795770ba048))
* do not warn when a converted file is already deleted ([2f23655](https://github.com/gimportexportdevs/gexporter/commit/2f23655329a52bbef8fd24528ce0f0429367d2ea))
* harden the share intake for track files and locations ([c296eec](https://github.com/gimportexportdevs/gexporter/commit/c296eec95d9c8271d5cdcb72067e7e068f83d168))
* list shared locations only for gimporter versions that handle them ([82c2670](https://github.com/gimportexportdevs/gexporter/commit/82c2670f2a51c5e16f29256d5e646dfdfa39f4ac))
* manage the gimporter widget's tracks too ([21b88eb](https://github.com/gimportexportdevs/gexporter/commit/21b88eb0f8190a459e4430f498b4b3f293173ff5))
* narrow the VIEW intent filter to supported file types ([dd17ec7](https://github.com/gimportexportdevs/gexporter/commit/dd17ec7cab5022179db2ab56c4e18761c2c87669))
* never guess a Google place position from the page ([9322a76](https://github.com/gimportexportdevs/gexporter/commit/9322a76f520a672560d3b0e5402151fc098c7fc8))
* serve from one shared directory and stop leaking cache files ([e287bd7](https://github.com/gimportexportdevs/gexporter/commit/e287bd7f24a6504d05b93adfc1eb8eee8622ed34))
* write the byte-order mark check as an escape ([79ac2c2](https://github.com/gimportexportdevs/gexporter/commit/79ac2c22a2eba5179fad0c62c160040297f13d11))

## [20.1.0](https://github.com/gimportexportdevs/gexporter/compare/v20.0.0...v20.1.0) (2026-07-06)


### Features

* Add release-please integration for automated releases ([383e41f](https://github.com/gimportexportdevs/gexporter/commit/383e41f52ebd37e53c33c8a84ab07c74cd320187))
* Enable edge-to-edge display and update dependencies ([2289cf9](https://github.com/gimportexportdevs/gexporter/commit/2289cf9c2ad0ac4e1ae5b5e4c9f761ab4e3ddb3d))
* enhance Garmin app integration and improve server initialization ([7f580de](https://github.com/gimportexportdevs/gexporter/commit/7f580dedcfa255860c3a2d0e265d8af896cd2518))
* Implement modern edge-to-edge UI using WindowCompat and Material Design 3 ([0190c2f](https://github.com/gimportexportdevs/gexporter/commit/0190c2f23996a3b0f9debe78fb278fd89965b013))
* Increment app version ([9f92b21](https://github.com/gimportexportdevs/gexporter/commit/9f92b21fcceadddc7c448cf2895f067a4e6831b2))
* update app version and refine intent filter for file types ([c1d221e](https://github.com/gimportexportdevs/gexporter/commit/c1d221edb4fcff8c631f0af7c2f9c1f6bdd26d4e))


### Bug Fixes

* add logback configuration for logcat output ([b044d81](https://github.com/gimportexportdevs/gexporter/commit/b044d812158d1dddad1820e91a3d0f04c1aa4eae))
* **convert:** robust timestamps and input validation in GPX-&gt;FIT ([73ea8c5](https://github.com/gimportexportdevs/gexporter/commit/73ea8c5355118b08c429916e6133a36dba330d0c)), closes [#26](https://github.com/gimportexportdevs/gexporter/issues/26) [#27](https://github.com/gimportexportdevs/gexporter/issues/27) [#28](https://github.com/gimportexportdevs/gexporter/issues/28)
* Correct edge-to-edge implementation to prevent ActionBar overlap with status bar ([d0702c0](https://github.com/gimportexportdevs/gexporter/commit/d0702c080e13f491d0e270b76df007eee67963c3))
* Demote routine web server logs from error/warn to debug ([295ad83](https://github.com/gimportexportdevs/gexporter/commit/295ad8375cc76cb5717ba2eac87058ad3da19177))
* **lifecycle:** server/listener lifecycle races and re-entrancy ([2b860b9](https://github.com/gimportexportdevs/gexporter/commit/2b860b9ad7bbda43c26845554a63bbfda38ec1a8)), closes [#30](https://github.com/gimportexportdevs/gexporter/issues/30) [#29](https://github.com/gimportexportdevs/gexporter/issues/29)
* Migrate to new storage permissions for Android 13 ([6803449](https://github.com/gimportexportdevs/gexporter/commit/680344996ffa4adb2755064d94c0bc498d4127c3))
* Narrow share intent filter to supported file types ([8833439](https://github.com/gimportexportdevs/gexporter/commit/88334399e519636b064c89ebc3c1acf17ebb6418))
* Prevent ClassCastException on message receive ([934b0d2](https://github.com/gimportexportdevs/gexporter/commit/934b0d27a2a84e81c012232c0344f72d8f6f0db7))
* Register message listener when only widget variant is installed ([50ace76](https://github.com/gimportexportdevs/gexporter/commit/50ace76db297c2fec6be4ca21a03e94d65217df1))
* rte and wpt handling for GPX files ([b893575](https://github.com/gimportexportdevs/gexporter/commit/b893575398d4b0e9b1a3bee730af6a5664c0d8ee))
* single options instance across threads; namespace-agnostic GPX fallback ([3e70dfd](https://github.com/gimportexportdevs/gexporter/commit/3e70dfdabe6cd472f71c523fd38f725e3796a5f4)), closes [#31](https://github.com/gimportexportdevs/gexporter/issues/31) [#34](https://github.com/gimportexportdevs/gexporter/issues/34)
* use logback-android for logging ([b750ebd](https://github.com/gimportexportdevs/gexporter/commit/b750ebd86de21689a35b62ff96a5ead9ef4f4e76))
* **webserver:** path traversal, LAN exposure, JSON injection, conversion race ([945463a](https://github.com/gimportexportdevs/gexporter/commit/945463aabbc0c25eefe4404c88bb506607e60c95)), closes [#24](https://github.com/gimportexportdevs/gexporter/issues/24) [#25](https://github.com/gimportexportdevs/gexporter/issues/25) [#33](https://github.com/gimportexportdevs/gexporter/issues/33)
