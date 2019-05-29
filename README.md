# javappackager

Java wrapper for maven and winrun4j to help to create Windows executable packages.

Used in command line interface. In alpha.

Functionalities:
- embed icon in executable
- embed JVM
- copy "config" dir, and add it in class path
- during copying configuration files, ignore the ignored git files.
- copy all jar dependencies (and add those to classpath)
- copy licences for jar dependencies, with app licence and winrun4j licence.
- copy executables declared as dependencies (and add those to classpath)

Actually it just works on Windows, tested on my `jYTdl` java code. Don't manage winrun4j Windows services. 

Please use Maven and Java 11 for start it.
