# Changelog

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
