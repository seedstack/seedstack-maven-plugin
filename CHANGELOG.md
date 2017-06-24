# Version 2.4.3 (2017-05-02)

* [new] Colorized, interactive prompter thanks to [ConsoleUI](https://github.com/awegmann/consoleui).
* [new] Supports [Pebble](http://www.mitchellbosecke.com/pebble/home) template language in archetypes.

# Version 2.4.3 (2017-05-02)

* [fix] Correctly sets `java.class.path` system property before running application with `run` goal.

# Version 2.4.2 (2017-04-24)

* [fix] Don't automatically run package phase before package goal.

# Version 2.4.1 (2017-02-17)

* [fix] Display the cause of the error when launching the application or a tool.
* [fix] Fix asking for the generated project properties twice.

# Version 2.4.0 (2017-02-09)

* [new] New goal `effective-config` to dump effective configuration of the project as YAML.
* [new] New goal `effective-test-config` to dump effective test configuration of the project as YAML.
* [new] New goal `crypt` to crypt password using the configured master key store of the application.

# Version 2.3.2 (2017-02-06)

* [new] Detect and use the latest version of Maven archetype plugin when generating a project.
* [new] Abort release if parent is still a SNAPSHOT.

# Version 2.3.1 (2017-01-09)

* [fix] Fix auto-detection of possible project archetypes.

# Version 2.3.0 (2016-12-13)

* [new] Goal `tool` executes any Seed tool present in the application by name (starting from Seed 3.0.0+).
* [new] Goal `config` is a shortcut to the config Seed tool.
* [new] Dynamic fetches the list of archetypes from Maven repository.

# Version 2.2.4 (2016-06-03)

* [new] Add the ability to resolve `*` wildcards in capsule external classpath entries (to load all JARs from the specified path)

# Version 2.2.3 (2016-05-20)

* [new] Add the ability to specify an external classpath to the capsule either at runtime or build time.
* [brk] Drop light capsule (which was downloading dependencies from Maven on runtime) as non-reliable.

# Version 2.2.2 (2016-04-08)

* [fix] Fix project dependencies resolution in package goal which now strictly follows Maven rules
