{
  description = "Android development environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-25.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.android_sdk.accept_license = true;
	  config.allowUnfree = true;
        };
        
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "35.0.0" ];
          platformVersions = [ "36" ];
          abiVersions = [ "x86_64" "arm64-v8a" ];
          includeNDK = true;
          ndkVersions = [ "25.2.9519653" ];
          includeSystemImages = false;
          includeEmulator = true;
          includeExtras = [
            "extras;google;google_play_services"
            "extras;android;m2repository"
            "extras;google;m2repository"
          ];
        };

        androidSdk = androidComposition.androidsdk;
        
        buildScript = pkgs.writeShellScriptBin "build-app" ''
          set -e
          export ANDROID_HOME=${androidSdk}/libexec/android-sdk
          export ANDROID_SDK_ROOT=$ANDROID_HOME
          export JAVA_HOME=${pkgs.jdk17}
          export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
          
          echo "Building Android APK..."
          ./gradlew assembleRelease --no-daemon
          
          echo "APK built successfully!"
          echo "Location: $(realpath app/build/outputs/apk/release/*.apk)"
        '';
      in
      {
        packages.default = buildScript;
        packages.build-app = buildScript;
        
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            # Android SDK
            androidSdk
            
            # Java development
            jdk17
            gradle
            maven
            
            # Kotlin
            kotlin
            kotlin-language-server
            
            # Build tools
            cmake
            ninja
            pkg-config
            
            # Version control
            git
            
            # Optional: Android Studio (GUI)
            # Uncomment if you want the full IDE
            # android-studio
            
            # Useful command-line tools
            # adb
            scrcpy  # Screen mirroring
            
            # Code formatting
            ktlint
          ];

          shellHook = ''
            export ANDROID_HOME=${androidSdk}/libexec/android-sdk
            export ANDROID_SDK_ROOT=$ANDROID_HOME
            export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
            
            # Java configuration
            export JAVA_HOME=${pkgs.jdk17}
            
            echo "Android development environment loaded!"
            echo "Android SDK: $ANDROID_HOME"
            echo "Java: $JAVA_HOME"
            echo ""
            echo "Available commands:"
            echo "  - gradle: Build tool for Android projects"
            echo "  - adb: Android Debug Bridge"
            echo "  - emulator: Android emulator"
            echo "  - sdkmanager: SDK package manager"
            echo "  - avdmanager: Android Virtual Device manager"
            echo ""
          '';
        };
      });
}
