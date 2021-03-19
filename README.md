# Fugue CICD
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

## Contributing

1. Fork it (<https://github.com/symphonyoss/fugue-cicd/fork>)
2. Create your feature branch (`git checkout -b feature/fooBar`)
3. Read our [contribution guidelines](.github/CONTRIBUTING.md) and [Community Code of Conduct](https://www.finos.org/code-of-conduct)
4. Commit your changes (`git commit -am 'Add some fooBar'`)
5. Push to the branch (`git push origin feature/fooBar`)
6. Create a new Pull Request

## License

The code in this repository is distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright 2018-2019 Symphony Communication Services, LLC.