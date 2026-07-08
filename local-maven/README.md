# Local Maven Repository

This directory contains Maven-layout artifacts for binary-only libraries that were previously wired with Gradle `files(...)` dependencies.

Gradle resolves them through:

```groovy
maven { url = uri('local-maven') }
```

Do not reference jars from `lib/` with `files(...)`; add or update artifacts here using normal Maven coordinates instead.
