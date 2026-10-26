# Sourced by the classify-changes action and release-notes.sh, so a build and its release notes see the same files.
# Usage: in_stream <plugin|mod|site> <path>, succeeding when the path belongs to that stream.
in_stream() {
  case "$2" in
    '' | *.md) return 1 ;;
    site/* | .github/actions/build-site/* | .github/workflows/site.yml | branding/ingame/* | branding/images/youtube/*)
      [ "$1" = 'site' ] ;;
    branding/* | .github/*) return 1 ;;
    plugin/src/main/resources/web/fonts/QuinqueFive-Regular.woff2 | plugin/src/main/resources/web/favicon.ico \
      | plugin/src/main/resources/web/images/*)
      [ "$1" != 'mod' ] ;;
    mod/*) [ "$1" = 'mod' ] ;;
    gradle/* | build-logic/* | settings.gradle.kts | build.gradle.kts | gradle.properties | gradlew | gradlew.bat)
      [ "$1" != 'site' ] ;;
    *) [ "$1" = 'plugin' ] ;;
  esac
}
