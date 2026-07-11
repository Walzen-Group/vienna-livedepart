{
  description = "Vienna LiveDepart — Wear OS dev shell (OpenJDK 17 + Android debug tools)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };

        jdk = pkgs.zulu17;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk               # Azul Zulu OpenJDK 17
            pkgs.android-tools # adb, fastboot
            pkgs.gradle
          ];

          JAVA_HOME = "${jdk}";

          shellHook = ''
            echo "Vienna LiveDepart dev shell"
            echo "  JDK:  $(java -version 2>&1 | head -n1)"
            echo "  adb:  $(adb --version 2>/dev/null | head -n1)"
          '';
        };
      });
}
