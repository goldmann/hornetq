# HornetQ

If you need information about the HornetQ project please go to

http://community.jboss.org/wiki/HornetQ

http://www.jboss.org/hornetq/

This file describes some minimum 'stuff one needs to know' to get
started coding in this project.

## Source

The project's source code is hosted at:

https://github.com/hornetq

### Git usage:

Pull requests should be merged without fast forwards '--no-ff'. An easy way to achieve that is to use

```% git config branch.master.mergeoptions --no-ff```

## Maven

The minimum required Maven version is 3.0.0.

Do note that there are some compatibility issues with Maven 3.X still
unsolved [1]. This is specially true for the 'site' plugin [2].

[1]: <https://cwiki.apache.org/MAVEN/maven-3x-compatibility-notes.html>
[2]: <https://cwiki.apache.org/MAVEN/maven-3x-and-site-plugin.html>

## Tests

To run the unit tests:

```% mvn -Phudson-tests test```

Generating reports from unit tests:

```% mvn install site```

## To build a release artifact

```% mvn install -Prelease```

## Eclipse

We recommend you to use Eclipse 3.7 "Indigo". As it improved Maven and
Git support considerably. Note that there are still some Maven plugins
used by sub-projects (e.g. documentation) which are not supported even
in Eclipse 3.7.

Eclipse code formatting and (basic) project configuration files can be
found at the ```etc/``` folder. You need to manually copy them or use
a plugin.

### Annotation Pre-Processing

HornetQ uses [JBoss Logging] and that requires source code to be
generated from Java annotations. Currently M2E doesn't 'just work'
when Maven is configured to do that, although there is work in
progress to achieve this [3].

[JBoss Logging]: <https://community.jboss.org/wiki/JBossLoggingTooling>

[3]: <https://bugs.eclipse.org/bugs/show_bug.cgi?id=335036>

While waiting for M2E to solve this once and for all, there are 2 alternatives to work around it:

1. One is to [configure Eclipse to run annotation processors], but that
requires a direct reference to the JBoss Logging processor (apparently
Eclipse can't take this from the Maven path).

[configure Eclipse to run annotation processors]: <http://help.eclipse.org/indigo/index.jsp?topic=/org.eclipse.jdt.doc.isv/guide/jdt_apt_getting_started.htm>

2. Compiling the classes through Maven in the command line, and then
adding the source folder to the project's classpath.
