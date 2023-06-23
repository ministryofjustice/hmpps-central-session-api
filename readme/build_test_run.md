[< Back](../README.md)
---

## Building

To build the project (without tests):
```
./gradlew clean build -x test
```

To rebuild the docker image locally after building the project (perhaps after some new changes), run:
```
docker build -t quay.io/hmpps/hmpps-central-session-api:latest .
```

## Testing 
```
./gradlew test 
```

## Running
```
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The service can be run similarly within IntelliJ by editing the run configuration and adding `dev` as the Active Profile

## Common gradle tasks 

To list project dependencies, run:

```
./gradlew dependencies
```

To check for dependency updates, run:
```
./gradlew dependencyUpdates --warning-mode all
```

To run an OWASP dependency check, run:
```
./gradlew clean dependencyCheckAnalyze --info
```

To upgrade the gradle wrapper version, run:
```
./gradlew wrapper --gradle-version=<VERSION>
```

To automatically update project dependencies, run:
```
./gradlew useLatestVersions
```

#### Ktlint Gradle Tasks

To run Ktlint check:
```
./gradlew ktlintCheck
```

To run Ktlint format:
```
./gradlew ktlintFormat
```

To register pre-commit check to run Ktlint format:
```
./gradlew addKtlintFormatGitPreCommitHook 
```

...or to register pre-commit check to only run Ktlint check:
```
./gradlew addKtlintCheckGitPreCommitHook
```
