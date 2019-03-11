# Version 2.7.4 (2019-03-11)

* [fix] Preserve classpath order in packaged capsules (CLI having precedence over POM having precedence over application manifest).

# Version 2.7.3 (2018-12-04)

* [chg] Built and tested with OpenJDK 11 (minimum Java version still being 8).
* [fix] Properly handle resource deletion.

# Version 2.7.2 (2018-10-18)

* [fix] In watch goal, resources (including static resources under `META-INF/resources`) are now properly updated on change.

# Version 2.7.1 (2018-05-18)

* [new] In watch goal, trigger a LiveReload (without app refresh) when a resource changes.
* [chg] Java 9 compatibility: avoid using JDK internal classes for hot-reloading.
* [fix] More reliable check for Cygwin environment. 

# Version 2.7.0 (2017-11-31)

* [new] Add watch goal which automatically refreshes the application when a source file change.
* [new] Support for [LiveReload](http://livereload.com/) on the watch goal.

# Version 2.6.4 (2017-10-19)

* [fix] Uninstall ansi console before exiting.
* [new] Automatically switch to basic prompt under CYGWIN.

# Version 2.6.3 (2017-10-19)

* [new] Add basic prompt mode (-DbasicPrompt) on generate goal, for the case where ConsoleUI doesn't work properly.
* [chg] If an archetype named "web" exists, set it as the default project type.

# Version 2.6.2 (2017-08-10)

* [chg] Add a fallback to hard-coded archetype list for project generation.
* [chg] In the case of project generation with a custom archetype, ensures that archetype id is not blank.
* [chg] If the user cancels (ctrl+c) project generation during questions, still render the template with fallback values. 

# Version 2.6.1 (2017-08-04)

* [chg] Search custom archetype catalog first (`http://seedstack.org/maven/` by default), then central, then local, then manual coordinates.

# Version 2.6.0 (2017-08-03)

* [new] Add the ability to template project file names in project archetypes.
* [new] Add the ability to generate class configuration in templates with `yamlClassConfig` function. 

# Version 2.5.0 (2017-08-02)

* [new] Colorized, interactive prompter thanks to [ConsoleUI](https://github.com/awegmann/consoleui).
* [new] Supports [Pebble](http://www.mitchellbosecke.com/pebble/home) template language in archetypes.
* [new] Ability to ask questions if a JSON question file is present at the root of the generated project (answers can be used in templates). 
* [new] Distribution information can now be specified on the generate goal allowing to generate projects based on a custom distribution.
* [new] Resolve distribution highest version before listing project types when generating (avoid listing obsolete types).

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
