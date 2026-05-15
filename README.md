# gcsee-jma

Java Memory Analyzer — a Quarkus web app that ingests a JVM GC log
and renders an interactive dashboard of summary, analytics, and
per-feature charts. Supports G1, Parallel, Serial, CMS, and
generational ZGC logs. Built on top of the GCSee parser.

## Install and run

Two paths, pick whichever fits your environment.

### Run with JBang

Requires [JBang](https://www.jbang.dev/) (which will fetch the right
JDK automatically — no manual Java install needed).

```sh
jbang gcsee-jma@kcpeppe/gcsee-jma
```

Open <http://localhost:8080> once you see
`Listening on: http://0.0.0.0:8080`. `Ctrl-C` to stop.

To avoid typing the full alias every time, install it as a local
command:

```sh
jbang app install gcsee-jma@kcpeppe/gcsee-jma
```

After that, just run:

```sh
gcsee-jma
```

To override the port or bind address, pass JVM properties before the
alias (or command name):

```sh
jbang -Dquarkus.http.port=9000 -Dquarkus.http.host=127.0.0.1 \
  gcsee-jma@kcpeppe/gcsee-jma
```

### Run the JAR directly

Requires **Java 25** or newer (any vendor — Temurin, Oracle, Azul, …).

1. Grab the latest `gcsee-jma-<version>.jar` from the
   [Releases page](../../releases/latest).
2. `java -jar gcsee-jma-<version>.jar`
3. Wait ~3 seconds for `Listening on: http://0.0.0.0:8080`, then open
   <http://localhost:8080>.
4. Upload a GC log via the page's "Analyze" button.
5. `Ctrl-C` to stop.

To run on a different port, pass `-Dquarkus.http.port=9000` (or any
other port) before `-jar`. To bind only to localhost (the default
binding listens on all interfaces), pass
`-Dquarkus.http.host=127.0.0.1`.

### Run as a container

No JDK needed locally — the image bundles a JRE.

```sh
docker run --rm -p 8080:8080 ghcr.io/<owner>/gcsee-jma:latest
# then open http://localhost:8080
```

Replace `<owner>` with the GitHub user / org that hosts this repo
(check the URL in your browser). Images are published as multi-arch
manifests for `linux/amd64` and `linux/arm64`, so the same tag works
on Intel and Apple-Silicon machines.

To pin to a specific release, use the version tag instead of
`latest`:

```sh
docker run --rm -p 8080:8080 ghcr.io/<owner>/gcsee-jma:v0.1.0
```

To pass JVM tuning options into the container (heap size, GC
flags, etc.), use the `JAVA_OPTS` environment variable:

```sh
docker run --rm -e JAVA_OPTS="-Xmx2g" -p 8080:8080 ghcr.io/<owner>/gcsee-jma:latest
```

## Build from source

Requires **JDK 25** and **Maven 3.9+**.

```sh
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

For an uber-jar (single self-contained file, what releases ship):

```sh
mvn -Dquarkus.package.jar.type=uber-jar package
java -jar target/*-runner.jar
```

For dev mode with hot reload:

```sh
mvn quarkus:dev
```

## Releasing

Releases are cut by pushing a Git tag matching `v*`. The release
workflow (`.github/workflows/release.yml`) takes it from there:

```sh
# from a clean main with the commit you want to release
git tag v0.1.0
git push origin v0.1.0
```

That single push triggers a workflow run that:

1. Builds the uber-jar with `mvn -Dquarkus.package.jar.type=uber-jar package`.
2. Creates (or updates) a GitHub Release at the tag, with
   auto-generated notes from commits since the previous tag.
3. Attaches `gcsee-jma-<version>.jar` as a release asset.
4. Builds a multi-arch container image
   (`linux/amd64` + `linux/arm64`) and pushes it to
   `ghcr.io/<owner>/gcsee-jma` tagged with both the version
   (`v0.1.0`) and `latest`.

Re-running a failed release is supported via the workflow's
`workflow_dispatch` trigger — go to the Actions tab, pick the
**Release** workflow, click **Run workflow**, and supply the tag
name.

## License

See [LICENSE](LICENSE).
