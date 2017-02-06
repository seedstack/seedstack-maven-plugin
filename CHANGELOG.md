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
