# Eclipse OSGi Technology Build Tools

Build tools used by the Eclipse OSGi Technology project. The repository is a
Maven reactor containing Maven plugins and their supporting build configuration.

## Contents

The repository currently provides:

- `osgi-docbook-maven-plugin`: generates an OSGi specification PDF from a
	DocBook chapter and the Java API documentation in the project.

## Prerequisites

- Git
- A full JDK, not only a JRE. The PDF plugin invokes the JDK Javadoc tool.
- Apache Maven 3.9 or newer
- Network access to Maven Central on the first build, so Maven can download
	dependencies and plugins

Use `java -version` and `mvn -version` to check the selected Java runtime and
Maven installation. Use a JDK that supports the Java language level configured
by the parent build.

## Build the repository

Run the complete reactor build from the repository root:

```sh
mvn verify
```

This compiles the parent and Maven plugin projects, runs the plugin tests, and
packages the plugin. The integration-style test project under
`maven-plugins/osgi-docbook-maven-plugin/src/test/resources/test-projects`
exercises PDF generation.

Useful build commands:

```sh
# Compile without running tests
mvn -DskipTests package

# Run the tests and show Maven's detailed logging
mvn test -X

# Install the plugin into the local Maven repository for use by another build
mvn install
```

The root POM is a convenience reactor and is not deployed. The plugin artifact
is built at
`maven-plugins/osgi-docbook-maven-plugin/target/osgi-docbook-maven-plugin-0.0.1-SNAPSHOT.jar`.

## Use the DocBook plugin

After installing this repository locally, add the plugin to a specification
project. The default goal is `pdf`, and it runs in the `compile` phase.

```xml
<build>
	<plugins>
		<plugin>
			<groupId>org.eclipse.osgi-technology.build.tools.maven</groupId>
			<artifactId>osgi-docbook-maven-plugin</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</plugin>
	</plugins>
</build>
```

The plugin expects the specification chapter at
`src/main/resources/spec/spec.xml` by default. It also scans the project's
compile source roots for Java sources and uses their Javadoc and OSGi
annotations when creating the specification document. A project should
therefore have its API sources and any referenced annotation dependencies on
its compile class path.

Generate the document with:

```sh
mvn compile
```

The plugin creates the following files in `target/spec/pdf`:

- `<final-name>.fo`: the generated XSL-FO document, attached with classifier
	`specification` and type `fo`
- `<final-name>.pdf`: the generated PDF, attached with classifier
	`specification` and type `pdf`
- intermediate Javadoc and DocBook transformation files under
	`target/spec/pdf/javadoc`

The output base name normally comes from Maven's `project.build.finalName`.
If that value is unavailable, the plugin uses `<artifactId>.<version>`.

### Plugin properties

Override the defaults with Maven properties or plugin configuration:

| Property | Default | Purpose |
| --- | --- | --- |
| `osgi.docbook.spec.source` | `src/main/resources/spec/spec.xml` | Path to the DocBook specification chapter, relative to the project base directory |
| `osgi.docbook.spec.title` | `${project.name}` | Title inserted into the generated specification book |

For example:

```xml
<plugin>
	<groupId>org.eclipse.osgi-technology.build.tools.maven</groupId>
	<artifactId>osgi-docbook-maven-plugin</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<configuration>
		<chapterSource>src/main/resources/spec/custom-spec.xml</chapterSource>
		<specTitle>My OSGi Specification</specTitle>
	</configuration>
</plugin>
```

The corresponding command-line properties are:

```sh
mvn compile \
	-Dosgi.docbook.spec.source=src/main/resources/spec/custom-spec.xml \
	-Dosgi.docbook.spec.title="My OSGi Specification"
```

## Development

The source for the plugin is in
`maven-plugins/osgi-docbook-maven-plugin/src/main/java`. Its bundled DocBook
stylesheets, fonts, FOP configuration, and book resources are in
`src/main/resources`. Tests and the sample consumer project are in
`src/test`.

For a focused development loop:

```sh
# Run only the plugin module and its required reactor projects
mvn -pl maven-plugins/osgi-docbook-maven-plugin -am test

# Rebuild and run all verification checks
mvn clean verify
```

When changing PDF generation, inspect the generated files under
`maven-plugins/osgi-docbook-maven-plugin/target/spec/pdf` and add or update the
sample project resources and `PDFGenerationTest` as appropriate. Keep generated
`target` directories out of commits.

## License

This project is distributed under the Apache License 2.0. See the source files
and bundled resource notices for the licensing terms of included components.
