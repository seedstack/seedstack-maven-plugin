# Version 2.3.0 (2016-?-?)

* [new] Goal `tool` executes any Seed tool present in the application by name (starting from Seed 3.0.0+).
* [new] Goal `config` is a shortcut to the config Seed tool.

# Version 2.2.4 (2016-06-03)

* [new] Add the ability to resolve `*` wildcards in capsule external classpath entries (to load all JARs from the specified path)

# Version 2.2.3 (2016-05-20)

* [new] Add the ability to specify an external classpath to the capsule either at runtime or build time.
* [brk] Drop light capsule (which was downloading dependencies from Maven on runtime) as non-reliable.

# Version 2.2.2 (2016-04-08)

* [fix] Fix project dependencies resolution in package goal which now strictly follows Maven rules
