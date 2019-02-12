Release Process
===============

 1.  Ensure `version` in `build.gradle` is set to the version you want to release.
 2.  Update `README.md` with the version about to be released.
 3.  Commit: `git commit -am "Prepare version X.Y.X"`
 4.  Tag: `git tag -a X.Y.Z -m "Version X.Y.Z"`
 5.  Push: `git push && git push --tags`
 6.  Add the new release with notes (https://github.com/dsrees/JavaPhoenixClient/releases).
