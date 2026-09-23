# Eclipse OSGi Technology Build Tools

Build tools used by the Eclipse OSGi Technology project. The repository is a
Maven reactor containing Maven plugins and their supporting build configuration.

## Contents

The repository currently provides:

- `osgi-docbook-maven-plugin`: generates an OSGi specification PDF and HTML
	site from a DocBook chapter and the Java API documentation in the project.

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
exercises PDF and HTML generation.

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
project. It has two goals, `pdf` and `html`, which both run in the `compile`
phase. Bind the goals for the formats you need:

```xml
<build>
	<plugins>
		<plugin>
			<groupId>org.eclipse.osgi-technology.build.tools.maven</groupId>
			<artifactId>osgi-docbook-maven-plugin</artifactId>
			<version>0.0.1-SNAPSHOT</version>
			<executions>
				<execution>
					<id>spec-pdf</id>
					<goals>
						<goal>pdf</goal>
					</goals>
				</execution>
				<execution>
					<id>spec-html</id>
					<goals>
						<goal>html</goal>
					</goals>
				</execution>
			</executions>
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

Both goals first convert the Javadoc of the project into DocBook, under
`target/spec/javadoc`. This step is skipped when its output is newer than
every source file, so a build that runs both goals only generates it once.

The `pdf` goal creates the following files in `target/spec/pdf`:

- `<final-name>.fo`: the generated XSL-FO document, attached with classifier
	`specification` and type `fo`
- `<final-name>.pdf`: the generated PDF, attached with classifier
	`specification` and type `pdf`

The `html` goal creates a chunked XHTML site in `target/spec/html`, with the
same layout as the specification pages on docs.osgi.org:

- `index.html`: the book title page and table of contents
- `<chapter id>.html`: one page per chapter, named after its `xml:id`, plus
	`LICENSE.html` and `preface.html`
- `css/`, `js/` and `images/`: the style sheets, scripts (including
	highlight.js for code listings), logo, draft watermark and the images of the
	chapter. SVG images are used as they are, and are referred to by file name,
	so every image in a chapter needs a distinct file name.

It also creates `target/<final-name>-html.zip` from the site, attached with
classifier `specification-html` and type `zip`.

The output base name normally comes from Maven's `project.build.finalName`.
If that value is unavailable, the plugin uses `<artifactId>.<version>`.

### Plugin properties

Override the defaults with Maven properties or plugin configuration:

| Property | Default | Purpose |
| --- | --- | --- |
| `osgi.docbook.spec.source` | `src/main/resources/spec/spec.xml` | Path to the DocBook specification chapter, relative to the project base directory |
| `osgi.docbook.spec.title` | `${project.name}` | Title inserted into the generated specification book |
| `osgi.docbook.html.skip` | `false` | Skip the `html` goal |
| `osgi.docbook.html.attach` | `true` | Create and attach the zip of the HTML site |

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

When changing PDF or HTML generation, inspect the generated files under
`maven-plugins/osgi-docbook-maven-plugin/target/test-classes/test-projects/simple/target/spec`
and add or update the sample project resources, `PDFGenerationTest` and
`HTMLGenerationTest` as appropriate. Keep generated `target` directories out of
commits.

The DocBook 1.78.1 chunker cannot write files with Saxon-HE, so
`custom-html-chunker.xsl` replaces it. Chunks are first collected as marker
elements and then written with `xsl:result-document`; the stylesheet explains
why.

## License

This project is distributed under the Apache License 2.0. See the source files
and bundled resource notices for the licensing terms of included components.
