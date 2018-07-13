Release Process
===============

 1.  Ensure `version` in `build.gradle` is set to the version you want to release.
 2.  Update `README.md` with the version about to be released.
 3.  Commit: `git commit -am "Prepare version X.Y.X"`
 4.  Tag: `git tag -a X.Y.Z -m "Version X.Y.Z"`
 5.  Update `version` in `build.grade` to the next development version. For example, if
     you just tagged version 1.0.4 you would set this value to 1.0.5. Do NOT append "-SNAPSHOT" to
     this value, it will be added automatically.
 6.  Commit: `git commit -am "Prepare next development version."`
 7.  Push: `git push && git push --tags`
 8.  Add the new release with notes (https://github.com/dsrees/JavaPhoenixClient/releases).