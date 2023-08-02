Release Process
===============

 1.  Ensure `version` in `build.gradle` is set to the version you want to release.
 2.  Update `README.md` with the version about to be released.
 3.  Commit: `git commit -am "Prepare version X.Y.X"`
 4.  Tag: `git tag -a X.Y.Z -m "Version X.Y.Z"`
 5.  Push: `git push && git push --tags`
 6.  Add the new release with notes (https://github.com/dsrees/JavaPhoenixClient/releases).
 7.  Publish to Maven Central by running `./gradlew clean publish`. Can only be done by dsrees until CI setup
 8.  Close the staging repo here: https://s01.oss.sonatype.org/#stagingRepositories
 9.  Release the closed repo
