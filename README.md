# fugue-cicd
Fugue Jenkins pipeline library

## Change Log

## 2018-10-17
A v2 pipeline is now included with a renaming of almost all entities. The v1 pipeline is now deprecated and users of it
should move to the v2 pipeline.

## 2018-09-28
The Jenkins BUILD_NUMBER is only unique within a single job so to deal with situations in which there are multiple jobs
on the same server or multiple Jenkins servers with build jobs for the same projects, the pipeline now uses a 
build qualifier rather than a build number.

The build qualifier is a timestamp in the format YYYYMMDD-hhmmss-XXXX where XXXX is a random number to try to avoid the 
slight possibility of two builds for the same project being started on different servers in the same second.

The pipeline initializes the build qualifier on instantiation, the withBuildNumber() method has been replaced with
withBuildQualifier(String qualifier) and this method should only be called if you wish to re-use an existing build.

Existing Jenkinsfiles will break as a result of this change, calls to FuguePipeline.withBuildNumber() should be removed.